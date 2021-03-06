package be.tcuvelier.qdoctools.core.handlers;

import be.tcuvelier.qdoctools.core.exceptions.WriteQdocconfException;
import be.tcuvelier.qdoctools.core.utils.Pair;
import be.tcuvelier.qdoctools.core.utils.QtVersion;
import be.tcuvelier.qdoctools.core.utils.StreamGobbler;
import be.tcuvelier.qdoctools.core.QtModules;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class QdocHandler {
    private final Path sourceFolder; // Containing Qt's sources.
    private final Path installedFolder; // Containing a compiled and installed version of Qt.
    private Path outputFolder; // Where all the generated files should be put (qdoc may also output in a subfolder,
    // in which case the files are automatically moved to a flatter hierarchy).
    private final Path mainQdocconfPath; // The qdocconf that lists all the other ones.
    private final String qdocPath;
    private final QtVersion qtVersion;
    private final boolean qdocDebug;
    private final List<String> cppCompilerIncludes;

    public QdocHandler(String source, String installed, String output, String qdocPath, QtVersion qtVersion,
                       boolean qdocDebug, List<String> cppCompilerIncludes) {
        sourceFolder = Paths.get(source);
        installedFolder = Paths.get(installed);
        outputFolder = Paths.get(output);
        mainQdocconfPath = outputFolder.resolve("qtdoctools-main.qdocconf");

        this.qdocPath = qdocPath;
        this.qtVersion = qtVersion;
        this.qdocDebug = qdocDebug;
        this.cppCompilerIncludes = cppCompilerIncludes;
    }

    public void ensureOutputFolderExists() throws IOException {
        if (! outputFolder.toFile().isDirectory()) {
            Files.createDirectories(outputFolder);
        }
        if (! outputFolder.resolve("images").toFile().isDirectory()) {
            Files.createDirectories(outputFolder.resolve("images"));
        }
    }

    public Path getOutputFolder() {
        return outputFolder;
    }

    /**
     * @return list of modules, in the form Pair[module name, .qdocconf file path]
     */
    public List<Pair<String, Path>> findModules() {
        // List all folders within Qt's sources that correspond to modules.
        String[] directories = sourceFolder.toFile().list((current, name) ->
                name.startsWith("q")
                        && new File(current, name).isDirectory()
                        && QtModules.ignoredModules.stream().noneMatch(name::equals)
        );

        if (directories == null || directories.length == 0) {
            throw new RuntimeException("No modules found in the given source directory (" + sourceFolder + ")");
        }

        // Loop over all these folders and identify the modules (and their associated qdocconf file).
        // Process based on https://github.com/pyside/pyside2-setup/blob/5.11/sources/pyside2/doc/CMakeLists.txt
        // Find the qdocconf files, skip if it does not exist at known places.
        List<Pair<String, Path>> modules = new ArrayList<>(directories.length);
        for (String directory : directories) {
            Path modulePath = sourceFolder.resolve(directory);
            Path srcDirectoryPath = modulePath.resolve("src");

            if (directory.equals("qttools")) {
                // The most annoying case: Qt Tools, everything seems ad-hoc.
                for (Map.Entry<String, Pair<Path, String>> entry : QtModules.qtTools.entrySet()) {
                    Path docDirectoryPath = modulePath.resolve(entry.getValue().first);
                    Path qdocconfPath = docDirectoryPath.resolve(entry.getValue().second + ".qdocconf");

                    if (! qdocconfPath.toFile().isFile()) {
                        System.out.println("Skipped module: qttools / " + entry.getKey());
                        continue;
                    }

                    System.out.println("--> Found submodule: qttools / " + entry.getKey() + "; qdocconf: " + qdocconfPath.toString());
                    modules.add(new Pair<>(directory, qdocconfPath));
                }
            } else if (QtModules.submodulesSpecificNames.containsKey(directory)) {
                // Find the path to the documentation folders for each of the submodule.
                for (Pair<String, String> submodule : QtModules.submodulesSpecificNames.get(directory)) {
                    Path importsDirectoryPath = srcDirectoryPath.resolve("imports");
                    Path docDirectoryPath = srcDirectoryPath.resolve(submodule.first).resolve("doc");
                    Path docImportsDirectoryPath = importsDirectoryPath.resolve(submodule.first).resolve("doc");

                    // Find the exact qdocconf file.
                    List<Path> potentialQdocconfPaths = Arrays.asList(
                            docDirectoryPath.resolve(submodule.second + ".qdocconf"), // ActiveQt.
                            modulePath.resolve("doc").resolve("config").resolve(submodule.second + ".qdocconf"), // Qt Doc.
                            srcDirectoryPath.resolve("doc").resolve(submodule.second + ".qdocconf"), // Qt Speech.
                            docImportsDirectoryPath.resolve(submodule.second + ".qdocconf"), // Qt Quick modules like Controls 2.
                            docImportsDirectoryPath.resolve(submodule.second.substring(0, submodule.second.length() - 1) + ".qdocconf"),
                            srcDirectoryPath.resolve("imports").resolve(submodule.second + ".qdocconf"), // Qt Quick modules.
                            docDirectoryPath.resolve(submodule.second + "1.qdocconf"), // Qt Quick Controls 1.
                            docDirectoryPath.resolve(submodule.second + ".qdocconf"), // Base case.
                            docDirectoryPath.resolve("qt" + submodule.second + ".qdocconf")
                    );

                    Optional<Path> qdocconfOptionalPath = potentialQdocconfPaths.stream().filter(path -> path.toFile().isFile()).findAny();

                    if (qdocconfOptionalPath.isEmpty()) {
                        System.out.println("Skipped module: " + directory + " / " + submodule.first);
                        continue;
                    }

                    // Everything seems OK: push this module so that it will be handled later on.
                    Path qdocconfPath = qdocconfOptionalPath.get();
                    System.out.println("--> Found submodule: " + directory + " / " + submodule.first + "; qdocconf: " + qdocconfPath.toString());
                    modules.add(new Pair<>(directory, qdocconfPath));
                }
            } else {
                // Find the path to the documentation folder.
                Path docDirectoryPath = srcDirectoryPath.resolve(directory.replaceFirst("qt", "")).resolve("doc");

                // Find the exact qdocconf file.
                List<Path> potentialQdocconfPaths = Arrays.asList(
                        docDirectoryPath.resolve(directory + ".qdocconf"),
                        docDirectoryPath.resolve(directory.replaceFirst("qt", "") + ".qdocconf"), // ActiveQt. E.g.: doc\activeqt.qdocconf
                        modulePath.resolve("doc").resolve("config").resolve(directory + ".qdocconf"), // Qt Doc.
                        srcDirectoryPath.resolve("doc").resolve(directory + ".qdocconf"), // Qt Speech.
                        srcDirectoryPath.resolve("imports").resolve(directory).resolve("doc").resolve(directory + ".qdocconf"), // Qt Quick modules.
                        modulePath.resolve("Source").resolve(directory + ".qdocconf"), // Qt WebKit.
                        modulePath.resolve("doc").resolve(directory.replace("-", "") + ".qdocconf"), // Qt WebKit Examples (5.3-).
                        modulePath.resolve("doc").resolve(directory.replaceAll("-a(.*)", "").replace("-", "") + ".qdocconf"), // Qt WebKit Examples and Demos (5.0).
                        docDirectoryPath.resolve(directory + ".qdocconf") // Base case. E.g.: doc\qtdeclarative.qdocconf
                );

                Optional<Path> qdocconfOptionalPath = potentialQdocconfPaths.stream().filter(path -> path.toFile().isFile()).findAny();

                if (qdocconfOptionalPath.isEmpty()) {
                    System.out.println("Skipped module: " + directory);
                    continue;
                }

                // Everything seems OK: push this module so that it will be handled later on.
                Path qdocconfPath = qdocconfOptionalPath.get();
                System.out.println("--> Found module: " + directory + "; qdocconf: " + qdocconfPath.toString());
                modules.add(new Pair<>(directory, qdocconfPath));
            }
        }

        // Tools within Qt Base are another source of headaches...
        {
            Path qtBasePath = sourceFolder.resolve("qtbase");
            for (Map.Entry<String, Pair<Path, String>> entry : QtModules.qtBaseTools.entrySet()) {
                Path docDirectoryPath = qtBasePath.resolve(entry.getValue().first);
                Path qdocconfPath = docDirectoryPath.resolve(entry.getValue().second + ".qdocconf");

                if (! qdocconfPath.toFile().isFile()) {
                    if (modules.stream().noneMatch(stringPathPair -> stringPathPair.second.toFile().getName().contains(entry.getKey() + ".qdocconf"))) {
                        System.out.println("Skipped module: qtbase / " + entry.getKey());
                    }
                    continue;
                }

                System.out.println("--> Found submodule: qtbase / " + entry.getKey() + "; qdocconf: " + qdocconfPath.toString());
                modules.add(new Pair<>("qtbase", qdocconfPath));
            }
        }

        // Qt 5.0 has qmake in an awkward place.
        if (modules.stream().noneMatch(stringPathPair -> stringPathPair.second.toFile().getName().contains("qmake.qdocconf"))) {
            Path qtDocPath = sourceFolder.resolve("qtdoc");
            Path docDirectoryPath = qtDocPath.resolve("doc").resolve("config");
            Path qdocconfPath = docDirectoryPath.resolve("qmake.qdocconf");

            if (! qdocconfPath.toFile().isFile()) {
                System.out.println("Skipped submodule: qtdoc / qmake (old Qt 5 only)");
            } else {
                System.out.println("--> Found submodule: qtbase / qmake (old Qt 5 only); qdocconf: " + qdocconfPath.toString());
                modules.add(new Pair<>("qtbase", qdocconfPath));
            }
        }

        return modules;
    }

    private List<String> findIncludes() {
        // Accumulate the include folders. Base case: just the include folder of an installed Qt.
        List<String> includeDirs = new ArrayList<>(List.of(installedFolder.resolve("include").toString()));

        // Special cases.
        includeDirs.add(sourceFolder.resolve("qtbase").resolve("qmake").toString());
        includeDirs.add(sourceFolder.resolve("qtandroidextras").resolve("src").resolve("androidextras").resolve("doc").resolve("QtAndroidExtras").toString());

        // Find all modules within the include folder, to capture all private folders.
        File[] containedFiles = installedFolder.resolve("include").toFile().listFiles();
        if (containedFiles == null || containedFiles.length == 0) {
            return includeDirs;
        }
        List<Path> directories = Arrays.stream(containedFiles)
                .filter(f -> f.isDirectory() && QtModules.ignoredModules.stream().noneMatch(i -> f.toString().equals(i)))
                .map(File::toPath)
                .collect(Collectors.toList());

        if (directories.size() == 0) {
            return includeDirs;
        }

        // Up to now: Qt/5.13.0/include.
        // Add paths: Qt/5.13.0/include/MODULE and Qt/5.13.0/include/MODULE/VERSION
        // and Qt/5.13.0/include/MODULE/VERSION/MODULE and Qt/5.13.0/include/MODULE/VERSION/MODULE/private.
        for (Path directory: directories) {
            File[] subDirs = directory.toFile().listFiles();
            if (subDirs == null || subDirs.length == 0) {
                continue;
            }

            if (directory.toFile().exists()) {
                includeDirs.add(directory.toString());
            } else {
                continue;
            }

            List<Path> subDirectories = Arrays.stream(subDirs)
                    .filter(File::isDirectory)
                    .filter(f -> f.getName().split("\\.").length == 3)
                    .map(File::toPath)
                    .collect(Collectors.toList());
            for (Path sd: subDirectories) {
                includeDirs.add(sd.toString());

                String moduleName = sd.getParent().toFile().getName();
                Path ssd = sd.resolve(moduleName);
                if (ssd.toFile().exists()) {
                    includeDirs.add(ssd.toString());
                } else {
                    continue;
                }

                if (ssd.resolve("private").toFile().exists()) {
                    includeDirs.add(ssd.resolve("private").toString());
                }
            }
        }

        return includeDirs;
    }

    public Path makeMainQdocconf(@NotNull List<Pair<String, Path>> modules) throws WriteQdocconfException {
        modules.sort(Comparator.comparing(a -> a.first));
        try {
            Files.write(mainQdocconfPath, modules.stream().map(m -> m.second.toString()).collect(Collectors.joining("\n")).getBytes());
        } catch (IOException e) {
            throw new WriteQdocconfException(mainQdocconfPath, e);
        }

        return mainQdocconfPath;
    }

    private int countString(@NotNull String haystack, @NotNull String needle) {
        int count = 0;

        Matcher m = Pattern.compile(needle).matcher(haystack);
        int startIndex = 0;
        while (m.find(startIndex)) {
            count++;
            startIndex = m.start() + 1;
        }

        return count;
    }

    public void runQdoc() throws IOException, InterruptedException {
        if (! new File(qdocPath).exists()){
            throw new IOException("Path to qdoc wrong: file " + qdocPath + " does not exist!");
        }

        List<String> params = new ArrayList<>(Arrays.asList(qdocPath,
                "--outputdir", outputFolder.toString(),
                "--installdir", outputFolder.toString(),
                mainQdocconfPath.toString(),
                "--outputformat", "DocBook",
                "--single-exec",
                "--log-progress",
                "--timestamps"));
        if (qdocDebug) { // TODO: isn't this required to get all the needed information for later stages (like which file should be used within included examples)?
            params.add("--debug");
        }
        for (String includePath: cppCompilerIncludes) {
            // https://bugreports.qt.io/browse/QTCREATORBUG-20903
            // Clang includes must come before GCC includes.
            params.add("-I");
            params.add(includePath);
        }
        for (String includePath: findIncludes()) {
            params.add("-I");
            params.add(includePath);
        }
        ProcessBuilder pb = new ProcessBuilder(params);

        System.out.println("::> Running qdoc with the following arguments: ");
        List<String> commands = pb.command();
        System.out.println("        " + commands.get(0));
        List<String> strings = pb.command();
        for (int i = 1; i < strings.size(); i++) {
            String command = strings.get(i);
            System.out.print("            " + command);

            if (i + 1 < strings.size() && ! commands.get(i + 1).startsWith("-")) {
                System.out.print(" " + commands.get(i + 1));
                i += 1;
            }

            System.out.println();
        }

        // Qdoc requires a series of environment variables.
        Map<String, String> env = pb.environment();
        env.put("QT_INSTALL_DOCS", sourceFolder.resolve("qtbase").resolve("doc").toString());
        env.put("BUILDDIR", sourceFolder.resolve("qtbase").resolve("doc").toString());
        env.put("QT_VERSION_TAG", qtVersion.QT_VERSION_TAG());
        env.put("QT_VER", qtVersion.QT_VER());
        env.put("QT_VERSION", qtVersion.QT_VERSION());

        // Run qdoc and wait until it is done.
        Process qdoc = pb.start();
        @SuppressWarnings("StringBufferMayBeStringBuilder") StringBuffer sb = new StringBuffer(); // Will be written to from multiple threads, hence StringBuffer instead of StringBuilder.
        Consumer<String> errOutput = s -> {
            if (! s.contains("warning: ") && ! s.contains("note: ")) {
                System.err.println(s);
            }
        };
        StreamGobbler outputGobbler = new StreamGobbler(qdoc.getInputStream(), List.of(errOutput, sb::append));
        StreamGobbler errorGobbler = new StreamGobbler(qdoc.getErrorStream(), List.of(errOutput, sb::append));
        new Thread(outputGobbler).start();
        new Thread(errorGobbler).start();
        qdoc.waitFor();

        // Write down the log.
        String errors = sb.toString();

        if (outputFolder.resolve("qtdoctools-qdoc-log").toFile().exists()) {
            //noinspection ResultOfMethodCallIgnored
            outputFolder.resolve("qtdoctools-qdoc-log").toFile().delete();
        }
        Files.write(outputFolder.resolve("qtdoctools-qdoc-log"), errors.getBytes());

        // Parse the results from qdoc to find errors.
        int nErrors = countString(errors, "error:");
        int nFatalErrors = countString(errors, "fatal error:");
//        int nMissingDepends = countString(errors, "fatal error: '[a-zA-Z]+/[a-zA-Z]+Depends' file not found"); // Takes too long to compute.

        if (nErrors > 0) {
            System.out.println("::> Qdoc ran into issues: ");
            System.out.println("::>   - " + nErrors + " errors");
            System.out.println("::>   - " + nFatalErrors + " fatal errors");
//            System.out.println("::>   - " + nMissingDepends + " missing QtModuleDepends files");
        } else {
            System.out.println("::> Qdoc ended with no errors.");
        }
    }

    public void checkUngeneratedFiles() throws ParserConfigurationException, IOException, SAXException {
        // TODO: update to DocBook, based on the same files.
//        // Not always working, just catching some errors, that's already a good improvement on top of not using this function.
//        File[] fs = outputFolder.toFile().listFiles();
//        if (fs == null || fs.length == 0) {
//            return;
//        }
//        List<File> subfolders = Arrays.stream(fs).filter(File::isDirectory).collect(Collectors.toList());
//        for (File subfolder: subfolders) { // For each module...
//            // Find the index file.
//            File[] potentialIndices = subfolder.listFiles((dir, name) -> name.endsWith(".index"));
//            if (potentialIndices == null || potentialIndices.length != 1) {
//                continue;
//            }
//
//            File index = potentialIndices[0];
//            if (! index.exists()) {
//                continue;
//            }
//
//            // Find all WebXML files in this folder.
//            File[] webxmlFiles = subfolder.listFiles((dir, name) -> name.endsWith(".webxml"));
//            if (webxmlFiles == null) {
//                continue;
//            }
//            Set<String> webxml = Arrays.stream(webxmlFiles).map(File::getName).collect(Collectors.toSet());
//
//            // Iterate through the folder and find missing files.
//            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(index);
//            Node root = doc.getDocumentElement().getElementsByTagName("namespace").item(0);
//            NodeList children = root.getChildNodes();
//            for (int i = 0; i < children.getLength(); ++i) {
//                Node page = children.item(i);
//                if (! page.getNodeName().equals("page")) {
//                    continue;
//                }
//
//                String pageName = page.getAttributes().getNamedItem("href").getNodeValue().replace(".html", ".webxml");
//                if (! webxml.contains(pageName) && ! pageName.equals("nolink") && !
//                        (pageName.startsWith("http://") || pageName.startsWith("https://") || pageName.startsWith("ftp://"))
//                ) {
//                    System.out.println("Missing file: " + pageName + "; module: " + subfolder);
//                }
//            }
//        }
    }

    public void moveGeneratedFiles() throws IOException {
        // Maybe everything under the html folder.
        Path abnormalPath = outputFolder.resolve("html");
        if (Files.exists(abnormalPath)) {
            String[] files = abnormalPath.toFile().list((dir, name) -> name.endsWith(".xml"));
            if (files != null && files.length > 0) {
                System.out.println("++> Moving qdoc's result to the expected folder");

                if (Arrays.stream(files)
                        .map(abnormalPath::resolve)
                        .map(Path::toFile)
                        .map(file -> file.renameTo(outputFolder.resolve(file.getName()).toFile()))
                        .anyMatch(val -> ! val)) {
                    System.out.println("!!> Moving some files was not possible!");
                }

                if (! abnormalPath.resolve("images").toFile().renameTo(outputFolder.resolve("images").toFile())) {
                    System.out.println("!!> Moving the images folder was not possible!");
                }
            }
        }

        // Or even in one folder per module.
        File[] fs = outputFolder.toFile().listFiles();
        if (fs == null || fs.length == 0) {
            System.out.println("!!> No generated file or folder!");
            System.exit(0);
        }
        List<File> subfolders = Arrays.stream(fs).filter(File::isDirectory).collect(Collectors.toList());
        for (File subfolder: subfolders) { // For each module...
            File[] files = subfolder.listFiles((dir, name) -> name.endsWith(".xml"));
            if (files == null || files.length == 0) { // If there are no DocBook files: maybe everything already at the right place.
                continue;
            }

            System.out.println("++> Moving qdoc's result from " + subfolder + " to the expected folder");
            for (File f: files) { // For each DocBook file...
                String name = f.getName();
                try {
                    Files.copy(f.toPath(), outputFolder.resolve(name));
                } catch (FileAlreadyExistsException e) {
                    System.out.println("!!> File already exists: " + outputFolder.resolve(name) + ". Tried to copy from: " + f.toString());
                }
            }

            // Maybe there is an images folder to move one level up.
            File[] folders = subfolder.listFiles((f, name) -> f.isDirectory());
            if (folders == null || folders.length == 0) {
                continue;
            }
            for (File f: folders) {
                if (f.getName().equals("images")) {
                    File[] images = f.listFiles();
                    if (images == null || images.length == 0) {
                        continue;
                    }

                    for (File i: images) {
                        String name = i.getName();
                        try {
                            Files.move(i.toPath(), outputFolder.resolve("images").resolve(name));
                        } catch (FileAlreadyExistsException e) {
                            System.out.println("!!> File already exists: " + outputFolder.resolve("images").resolve(name) + ". Tried to copy from: " + i.toString());
                        }
                    }
                }
            }
        }
    }

    private List<Path> findWithExtension(String extension) {
        String[] fileNames = outputFolder.toFile().list((current, name) -> name.endsWith(extension));
        if (fileNames == null || fileNames.length == 0) {
            return Collections.emptyList();
        } else {
            return Arrays.stream(fileNames).map(outputFolder::resolve).collect(Collectors.toList());
        }
    }

    public List<Path> findDocBook() {
        return findWithExtension(".xml");
    }
}
