package be.tcuvelier.qdoctools.core.handlers;

import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.exceptions.WriteQdocconfException;
import be.tcuvelier.qdoctools.core.helpers.ValidationHelper;
import be.tcuvelier.qdoctools.core.utils.Pair;
import be.tcuvelier.qdoctools.core.utils.QtVersion;
import be.tcuvelier.qdoctools.core.utils.StreamGobbler;
import be.tcuvelier.qdoctools.core.QtModules;
import org.jetbrains.annotations.NotNull;
import org.xml.sax.SAXException;

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
    private final Path outputFolder; // Where all the generated files should be put (qdoc may also output in a subfolder,
    // in which case the files are automatically moved to a flatter hierarchy).
    private final Path mainQdocconfPath; // The qdocconf that lists all the other ones.
    private final String qdocPath;
    private final QtVersion qtVersion;
    private final boolean qdocDebug;
    private final List<String> cppCompilerIncludes;
    private final GlobalConfiguration config;

    public QdocHandler(String source, String installed, String output, String qdocPath, QtVersion qtVersion,
                       boolean qdocDebug, List<String> cppCompilerIncludes, GlobalConfiguration config) {
        sourceFolder = Paths.get(source);
        installedFolder = Paths.get(installed);
        outputFolder = Paths.get(output);
        mainQdocconfPath = outputFolder.resolve("qtdoctools-main.qdocconf");

        this.config = config;
        this.qdocPath = qdocPath; // TODO: either read this from `config` or from `installed`.
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
        // This system cannot be replaced by a simple enumeration of all qdocconf files: many of them just define
        // global configuration options or example URLs. For instance, for Qt 6.3, qtbase has 70 qdocconf files, but
        // only 12 of them are relevant.
        List<Pair<String, Path>> modules = new ArrayList<>(directories.length);
        for (String directory : directories) {
            Path modulePath = sourceFolder.resolve(directory);
            Path srcDirectoryPath = modulePath.resolve("src");

            if (directory.equals("qttools")) {
                // The most annoying case: Qt Tools, everything seems ad-hoc.
                for (Map.Entry<String, Pair<Path, String>> entry : QtModules.qtTools.entrySet()) {
                    Path docDirectoryPath = modulePath.resolve(entry.getValue().first);

                    List<Path> potentialQdocconfPaths = Arrays.asList(
                            docDirectoryPath.resolve(entry.getValue().second + ".qdocconf"),
                            docDirectoryPath.resolve("qt" + entry.getValue().second + ".qdocconf")
                    );

                    Optional<Path> qdocconfOptionalPath = potentialQdocconfPaths.stream().filter(path -> path.toFile().isFile()).findAny();

                    if (qdocconfOptionalPath.isEmpty()) {
                        System.out.println("Skipped module \"qttools / " + entry.getKey() + "\": no .qdocconf file");
                        continue;
                    }

                    System.out.println("--> Found submodule: qttools / " + entry.getKey() + "; qdocconf: " + qdocconfOptionalPath.get());
                    modules.add(new Pair<>(directory, qdocconfOptionalPath.get()));
                }
            } else if (QtModules.submodulesSpecificNames.containsKey(directory)) {
                // Find the path to the documentation folders for each of the submodule.
                for (Pair<String, String> submodule : QtModules.submodulesSpecificNames.get(directory)) {
                    Path importsDirectoryPath = srcDirectoryPath.resolve("imports");
                    Path docDirectoryPath = srcDirectoryPath.resolve(submodule.first).resolve("doc");
                    Path docImportsDirectoryPath = importsDirectoryPath.resolve(submodule.first).resolve("doc");

                    // Find the exact qdocconf file.
                    List<Path> potentialQdocconfPaths = Arrays.asList(
                            // ActiveQt.
                            docDirectoryPath.resolve(submodule.second + ".qdocconf"),
                            // Qt Doc.
                            modulePath.resolve("doc").resolve("config").resolve(submodule.second + ".qdocconf"),
                            modulePath.resolve("doc").resolve("src").resolve(submodule.first).resolve(submodule.second + ".qdocconf"),
                            // Qt Speech.
                            srcDirectoryPath.resolve("doc").resolve(submodule.second + ".qdocconf"),
                            // Qt Quick modules like Controls 2.
                            docImportsDirectoryPath.resolve(submodule.second + ".qdocconf"),
                            docImportsDirectoryPath.resolve("qt" + submodule.second + ".qdocconf"),
                            docImportsDirectoryPath.resolve(submodule.second.substring(0, submodule.second.length() - 1) + ".qdocconf"),
                            // Qt Quick modules.
                            srcDirectoryPath.resolve("imports").resolve(submodule.second + ".qdocconf"),
                            // Qt Quick Dialogs 2 (Qt 6)
                            srcDirectoryPath.resolve(submodule.first).resolve(submodule.first).resolve("doc").resolve("qt" + submodule.second + ".qdocconf"),
                            // Qt Quick Controls 1.
                            docDirectoryPath.resolve(submodule.second + "1.qdocconf"),
                            // Base case.
                            docDirectoryPath.resolve(submodule.second + ".qdocconf"),
                            docDirectoryPath.resolve("qt" + submodule.second + ".qdocconf")
                    );

                    Optional<Path> qdocconfOptionalPath = potentialQdocconfPaths.stream().filter(path -> path.toFile().isFile()).findAny();

                    if (qdocconfOptionalPath.isEmpty()) {
                        System.out.println("Skipped module \"" + directory + " / " + submodule.first + "\": no .qdocconf file");
                        continue;
                    }

                    // Everything seems OK: push this module so that it will be handled later on.
                    Path qdocconfPath = qdocconfOptionalPath.get();
                    System.out.println("--> Found submodule: " + directory + " / " + submodule.first + "; qdocconf: " + qdocconfPath);
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
                    System.out.println("Skipped module \"" + directory + "\": no .qdocconf file");
                    continue;
                }

                // Everything seems OK: push this module so that it will be handled later on.
                Path qdocconfPath = qdocconfOptionalPath.get();
                System.out.println("--> Found module: " + directory + "; qdocconf: " + qdocconfPath);
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
                        System.out.println("Skipped module \"qtbase / " + entry.getKey() + "\": no .qdocconf file");
                    }
                    continue;
                }

                System.out.println("--> Found submodule: qtbase / " + entry.getKey() + "; qdocconf: " + qdocconfPath);
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
                System.out.println("--> Found submodule: qtbase / qmake (old Qt 5 only); qdocconf: " + qdocconfPath);
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
                .map(File::toPath).toList();

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
                    .map(File::toPath).toList();
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
                "--timestamps",
                "--docbook-extensions"));
        if (qdocDebug) {
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
            if (qdocDebug || (! s.contains("warning: ") && ! s.contains("note: "))) {
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

    public void moveGeneratedFiles() throws IOException {
        // Maybe everything is under the html folder.
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
        List<File> subfolders = Arrays.stream(fs).filter(File::isDirectory).toList();
        for (File subfolder: subfolders) { // For each module...
            // First, deal with the DocBook files.
            File[] files = subfolder.listFiles((dir, name) -> name.endsWith(".xml"));
            if (files != null && files.length > 0) {
                System.out.println("++> Moving qdoc's result from " + subfolder + " to the expected folder");
                for (File f : files) { // For each DocBook file...
                    String name = f.getName();

                    if (name.equals("search-results.xml")) {
                        continue;
                    }

                    Path destination = outputFolder.resolve(name);
                    try {
                        Files.copy(f.toPath(), destination);
                    } catch (FileAlreadyExistsException e) {
                        System.out.println("!!> File already exists: " + destination + ". Tried to copy from: " + f);
                    }
                }
            }

            // Maybe there is an images folder to move one level up.
            File[] folders = subfolder.listFiles((f, name) -> f.isDirectory());
            if (folders != null && folders.length != 0) {
                for (File f : folders) {
                    if (f.getName().equals("images")) {
                        moveGeneratedImagesRecursively(f, outputFolder.resolve("images"));
                    }
                }
            }
        }
    }

    private void moveGeneratedImagesRecursively(File folder, Path destination) throws IOException {
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            return;
        }

        if (! destination.toFile().exists()) {
            if (! destination.toFile().mkdirs()) {
                throw new IOException("Could not create directories: " + destination);
            }
        }

        for (File f : files) {
            String name = f.getName();

            if (f.isDirectory()) {
                moveGeneratedImagesRecursively(f, destination.resolve(name));
                continue;
            }

            Path d = destination.resolve(name);
            try {
                Files.copy(f.toPath(), d);
            } catch (FileAlreadyExistsException e) {
                System.out.println("!!> File already exists: " + d + ". Tried to copy from: " + f);
            }
        }
    }

    public void fixQdocBugs() throws IOException {
        // Only the files in the root folder are considered.
        // List of bugs fixed here:
        // - in the DocBook output, code has markers for syntax highlighting (e.g., around keywords).
        //   For instance:
        //       &lt;@keyword&gt;class&lt;/@keyword&gt; &lt;@type&gt;QObject&lt;/@type&gt;
        //   This example should be mapped to:
        //       class QObject;
        //   -> regex: (&lt;@[^&]*&gt;)|(&lt;/@[^&]*&gt;)
        // - in the DocBook output, extendedlinks are not valid: https://bugreports.qt.io/browse/QTBUG-103747
        //   For instance:
        //       <db:extendedlink><db:link xlink:to="xml-namespaces.xml" xlink:title="prev" xlink:label="An
        //       Introduction to Namespaces"/></db:extendedlink>
        //   This example should be mapped to:
        //       <db:extendedlink xlink:type="extended"><db:link xlink:to="xml-namespaces.xml" xlink:title="An
        //       Introduction to Namespaces" xlink:type="arc" xlink:arcrole="prev"/></db:extendedlink>
        //   -> base regex:
        //          <db:extendedlink><db:link xlink:to="(.*)" xlink:title="(.*)" xlink:label="(.*)"/></db:extendedlink>
        // - in the DocBook output, for examples, links to projects are output outside sections:
        //   https://bugreports.qt.io/browse/QTBUG-103749
        //   For instance, at the end of the file:
        //        </db:section>
        //        <db:para><db:link
        //        xlink:href="https://code.qt.io/cgit/qt/qtbase.git/tree/examples/gui/hellovulkanwidget?h=6.3">Example
        //        project @ code.qt.io</db:link></db:para>
        //        </db:article>
        //   This example should be mapped to:
        //        </db:section>
        //        <db:section>
        //        <db:title>Example project</db:title>
        //        <db:para><db:link
        //        xlink:href="https://code.qt.io/cgit/qt/qtbase.git/tree/examples/gui/hellovulkanwidget?h=6.3">Example
        //        project @ code.qt.io</db:link></db:para>
        //        </db:section>
        //        </db:article>

        // Build a regex pattern for the strings to remove.
        final Pattern patternMarker = Pattern.compile("(&lt;@[^&]*&gt;)|(&lt;/@[^&]*&gt;)");
        final Pattern patternExtended = Pattern.compile(
                "<db:extendedlink>" +
                      "<db:link xlink:to=\"([a-zA-Z\\-]*)\\.xml\" xlink:title=\"([^\"]*)\" xlink:label=\"([^\"]*)\"/>" +
                      "</db:extendedlink>");
        final Pattern patternExampleLink = Pattern.compile(
                "</db:section>\\R" +
                        "<db:para><db:link xlink:href=\"(.*)\">Example project @ (.*)</db:link></db:para>\\R"+
                      "</db:article>");

        for (Path filePath : findDocBook()) {
            boolean hasMatched = false;
            String file = Files.readString(filePath);

            {
                Matcher matcher = patternMarker.matcher(file);
                if (matcher.results().findAny().isPresent()) {
                    hasMatched = true;
                    file = matcher.replaceAll("");
                }
            }
            if (file.contains("</db:extendedlink><db:extendedlink>")) {
                hasMatched = true;
                file = file.replaceAll("</db:extendedlink><db:extendedlink>", "</db:extendedlink>\n<db:extendedlink>");
            }
            if (file.contains("</db:extendedlink><db:abstract>")) {
                hasMatched = true;
                file = file.replaceAll("</db:extendedlink><db:abstract>", "</db:extendedlink>\n<db:abstract>");
            }
            {
                Matcher matcher = patternExtended.matcher(file);
                if (matcher.results().findAny().isPresent()) {
                    hasMatched = true;
                    file = matcher.replaceAll("<db:extendedlink xlink:type=\"extended\"><db:link xlink:to=\"$1.xml\" xlink:title=\"$3\" xlink:type=\"arc\" xlink:arcrole=\"$2\"/></db:extendedlink>");
                }
            }
            {
                Matcher matcher = patternExampleLink.matcher(file);
                if (matcher.results().findAny().isPresent()) {
                    hasMatched = true;
                    file = matcher.replaceAll("""
                            </db:section>
                            <db:section>
                            <db:title>Example project</db:title>
                            <db:para><db:link xlink:href="$1">Example project @ $2</db:link></db:para>
                            </db:section>
                            </db:article>""");
                }
            }

            if (! hasMatched) {
                // This file has not changed: no need to have a back-up file or to spend time writing on disk.
                continue;
            }

            Path fileBackUp = filePath.getParent().resolve(filePath.getFileName() + ".bak");
            if (! fileBackUp.toFile().exists()) {
                Files.move(filePath, fileBackUp);
            }
            Files.write(filePath, file.getBytes());
        }
    }

    public void validateDocBook() throws IOException, SAXException {
        int nFiles = 0;
        int nValidFiles = 0;
        for (Path file : findDocBook()) {
            nFiles += 1;
            if (ValidationHelper.validateDocBook(file, config)) {
                nValidFiles += 1;
            } else {
                System.out.println("!!> Invalid file: " + file);
            }
        }
        System.out.println("++> " + nFiles + " validated, " + nValidFiles + " valid, " + (nFiles - nValidFiles) + " invalid.");
    }

    private List<Path> findWithExtension(@SuppressWarnings("SameParameterValue") String extension) {
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
