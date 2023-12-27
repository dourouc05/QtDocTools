package be.tcuvelier.qdoctools.core.handlers;

import be.tcuvelier.qdoctools.consistency.ConsistencyChecks;
import be.tcuvelier.qdoctools.consistency.ConsistencyResults;
import be.tcuvelier.qdoctools.core.QtModules;
import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.exceptions.WriteQdocconfException;
import be.tcuvelier.qdoctools.core.helpers.ValidationHelper;
import be.tcuvelier.qdoctools.core.utils.Pair;
import be.tcuvelier.qdoctools.core.utils.QtVersion;
import be.tcuvelier.qdoctools.core.utils.StreamGobbler;
import net.sf.saxon.s9api.SaxonApiException;
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

public class QDocHandler {
    private final Path sourceFolder; // Containing Qt's sources.
    private final Path installedFolder; // Containing a compiled and installed version of Qt.
    private final Path outputFolder; // Where all the generated files should be put (QDoc may
    // also output in a
    // subfolder, in which case the files are automatically moved to a flatter hierarchy).
    private final Path htmlFolder; // A preexisting copy of the HTML docs.
    private final Path mainQdocconfPath; // The qdocconf that lists all the other ones.
    private final String qtAttributionsScannerPath;
    private final String qdocPath;
    private final QtVersion qtVersion;
    private final boolean qdocDebug;
    private final List<String> cppCompilerIncludes;
    private final GlobalConfiguration config;

    public QDocHandler(String source, String installed, String output, String htmlVersion, String qdocPath,
            QtVersion qtVersion,
            boolean qdocDebug, List<String> cppCompilerIncludes, GlobalConfiguration config) throws IOException {
        sourceFolder = Paths.get(source);
        installedFolder = Paths.get(installed);
        outputFolder = Paths.get(output);
        htmlFolder = Paths.get(htmlVersion);
        mainQdocconfPath = outputFolder.resolve("qtdoctools-main.qdocconf");

        this.config = config;
        Path qdocContainingFolder = Paths.get(qdocPath).getParent();
        this.qtAttributionsScannerPath = Arrays.stream(
                    Objects.requireNonNull(qdocContainingFolder.toFile().list()))
                .filter((String path) -> path.contains("qtattributionsscanner"))
                .map((String fileName) -> qdocContainingFolder.resolve(fileName).toString())
                .findFirst()
                .orElse("");
        this.qdocPath = qdocPath; // TODO: either read this from `config` or from `installed`.
        this.qtVersion = qtVersion;
        this.qdocDebug = qdocDebug;
        this.cppCompilerIncludes = cppCompilerIncludes;

        // TODO: for qtAttributionsScannerPath, qdocPath, test whether you can run these binaries (i.e. they don't
        // merely exist, they have their required shared library accessible)?

        ensureOutputFolderExists();
        ensureIncludesExist();
    }

    private void ensureOutputFolderExists() throws IOException {
        if (!outputFolder.toFile().isDirectory()) {
            Files.createDirectories(outputFolder);
        }
        if (!outputFolder.resolve("images").toFile().isDirectory()) {
            Files.createDirectories(outputFolder.resolve("images"));
        }
    }

    private void ensureIncludesExist() throws IOException {
        for (String path : cppCompilerIncludes) {
            if (!Files.exists(Paths.get(path))) {
                throw new IOException("Include folder " + path + " does not exist.");
            }
        }
    }

    public Path getOutputFolder() {
        return outputFolder;
    }

    /**
     * @return list of modules, in the form Pair[module name, .qdocconf file path]
     */
    public Pair<List<Pair<String, Path>>, List<String>> findModules() {
        // List all folders within Qt's sources that correspond to modules.
        String[] directories = sourceFolder.toFile().list((current, name) ->
                name.startsWith("q")
                        && new File(current, name).isDirectory()
                        && QtModules.ignoredModules.stream().noneMatch(name::equals)
        );

        if (directories == null || directories.length == 0) {
            throw new RuntimeException("No modules found in the given source directory (" + sourceFolder + ")");
        }

        // Loop over all these folders and identify the modules (and their associated qdocconf
        // file).
        // Process based on https://github.com/pyside/pyside2-setup/blob/5.11/sources/pyside2/doc/CMakeLists.txt
        // Find the qdocconf files, skip if it does not exist at known places.
        // This system cannot be replaced by a simple enumeration of all qdocconf files: many of
        // them just define
        // global configuration options or example URLs. For instance, for Qt 6.3, qtbase has 70
        // qdocconf files, but
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

                    Optional<Path> qdocconfOptionalPath =
                            potentialQdocconfPaths.stream().filter(path -> path.toFile().isFile()).findAny();

                    if (qdocconfOptionalPath.isEmpty()) {
                        System.out.println("Skipped module \"qttools / " + entry.getKey() + "\": " +
                                "no .qdocconf file");
                        continue;
                    }

                    System.out.println("--> Found submodule: qttools / " + entry.getKey() + "; " +
                            "qdocconf: " + qdocconfOptionalPath.get());
                    modules.add(new Pair<>(directory, qdocconfOptionalPath.get()));
                }
            } else if (QtModules.submodulesSpecificNames.containsKey(directory)) {
                // Find the path to the documentation folders for each of the submodule.
                for (Pair<String, String> submodule :
                        QtModules.submodulesSpecificNames.get(directory)) {
                    Path importsDirectoryPath = srcDirectoryPath.resolve("imports");
                    Path docDirectoryPath = srcDirectoryPath.resolve(submodule.first).resolve(
                            "doc");
                    Path docImportsDirectoryPath =
                            importsDirectoryPath.resolve(submodule.first).resolve("doc");

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
                            docImportsDirectoryPath.resolve(submodule.second.substring(0,
                                    submodule.second.length() - 1) + ".qdocconf"),
                            // Qt Quick modules.
                            srcDirectoryPath.resolve("imports").resolve(submodule.second +
                                    ".qdocconf"),
                            // Qt Quick Dialogs 2 (Qt 6)
                            srcDirectoryPath.resolve(submodule.first).resolve(submodule.first).resolve("doc").resolve("qt" + submodule.second + ".qdocconf"),
                            // Qt Quick Controls 1.
                            docDirectoryPath.resolve(submodule.second + "1.qdocconf"),
                            // Base case.
                            docDirectoryPath.resolve(submodule.second + ".qdocconf"),
                            docDirectoryPath.resolve("qt" + submodule.second + ".qdocconf")
                    );

                    Optional<Path> qdocconfOptionalPath =
                            potentialQdocconfPaths.stream().filter(path -> path.toFile().isFile()).findAny();

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
                Path docDirectoryPath =
                        srcDirectoryPath.resolve(directory.replaceFirst("qt", "")).resolve("doc");

                // Find the exact qdocconf file.
                List<Path> potentialQdocconfPaths = Arrays.asList(
                        docDirectoryPath.resolve(directory + ".qdocconf"),
                        docDirectoryPath.resolve(directory.replaceFirst("qt", "") + ".qdocconf"),
                        // ActiveQt. E.g.: doc\activeqt.qdocconf
                        modulePath.resolve("doc").resolve("config").resolve(directory +
                                ".qdocconf"), // Qt Doc.
                        srcDirectoryPath.resolve("doc").resolve(directory + ".qdocconf"), // Qt
                        // Speech.
                        srcDirectoryPath.resolve("imports").resolve(directory).resolve("doc").resolve(directory + ".qdocconf"), // Qt Quick modules.
                        modulePath.resolve("Source").resolve(directory + ".qdocconf"), // Qt WebKit.
                        modulePath.resolve("doc").resolve(directory.replace("-", "") + ".qdocconf"
                                ), // Qt WebKit Examples (5.3-).
                        modulePath.resolve("doc").resolve(directory.replaceAll("-a(.*)", "").replace("-", "") + ".qdocconf"), // Qt WebKit Examples and Demos (5.0).
                        docDirectoryPath.resolve(directory + ".qdocconf") // Base case. E.g.:
                        // doc\qtdeclarative.qdocconf
                );

                Optional<Path> qdocconfOptionalPath =
                        potentialQdocconfPaths.stream().filter(path -> path.toFile().isFile()).findAny();

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

                if (!qdocconfPath.toFile().isFile()) {
                    if (modules.stream().noneMatch(stringPathPair -> stringPathPair.second.toFile().getName().contains(entry.getKey() + ".qdocconf"))) {
                        System.out.println("Skipped module \"qtbase / " + entry.getKey() + "\": " +
                                "no .qdocconf file");
                    }
                    continue;
                }

                System.out.println("--> Found submodule: qtbase / " + entry.getKey() + "; " +
                        "qdocconf: " + qdocconfPath);
                modules.add(new Pair<>("qtbase", qdocconfPath));
            }
        }

        // Qt 5.0 has qmake in an awkward place.
        if (modules.stream().noneMatch(stringPathPair -> stringPathPair.second.toFile().getName().contains("qmake.qdocconf"))) {
            Path qtDocPath = sourceFolder.resolve("qtdoc");
            Path docDirectoryPath = qtDocPath.resolve("doc").resolve("config");
            Path qdocconfPath = docDirectoryPath.resolve("qmake.qdocconf");

            if (!qdocconfPath.toFile().isFile()) {
                System.out.println("Skipped submodule: qtdoc / qmake (old Qt 5 only)");
            } else {
                System.out.println("--> Found submodule: qtbase / qmake (old Qt 5 only); " +
                        "qdocconf: " + qdocconfPath);
                modules.add(new Pair<>("qtbase", qdocconfPath));
            }
        }

        return new Pair<>(modules, Arrays.asList(directories));
    }

    private List<String> findIncludes() {
        // Accumulate the `include` folders. Base case: just the `include` folder of an installed
        // Qt.
        List<String> includeDirs =
                new ArrayList<>(List.of(installedFolder.resolve("include").toString()));

        // Special cases.
        includeDirs.add(sourceFolder.resolve("qtbase").resolve("qmake").toString());
        includeDirs.add(sourceFolder.resolve("qtandroidextras").resolve("src").resolve(
                "androidextras").resolve("doc").resolve("QtAndroidExtras").toString());

        // Find all modules within the `include` folder, to capture all private folders.
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
        // and Qt/5.13.0/include/MODULE/VERSION/MODULE and Qt/5.13
        // .0/include/MODULE/VERSION/MODULE/private.
        for (Path directory : directories) {
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
            for (Path sd : subDirectories) {
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
        // Write a list of the .qdocconf files that must be generated in the qdoc run.
        modules.sort(Comparator.comparing(a -> a.first));
        try {
            Files.write(mainQdocconfPath,
                    modules.stream().map(m -> m.second.toString()).collect(Collectors.joining("\n"
                    )).getBytes());
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

    private void printCommand(List<String> commands) {
        System.out.println("        " + commands.get(0));
        for (int i = 1; i < commands.size(); i++) {
            String command = commands.get(i);
            System.out.print("            " + command);

            // If the parameter takes an argument, show it on the same line.
            if (i + 1 < commands.size() && !commands.get(i + 1).startsWith("-")) {
                System.out.print(" " + commands.get(i + 1));
                i += 1;
            }

            System.out.println();
        }
    }

    private Pair<Integer, String> runCommandAndReturnCodeAndLogs(ProcessBuilder pb, String logFileName) throws IOException, InterruptedException {
        Process process = pb.start();
        StringBuffer sb = new StringBuffer(); // Will be written to from multiple threads, hence
        // StringBuffer instead of StringBuilder.
        Consumer<String> errOutput = s -> {
            if (qdocDebug || (!s.contains("warning: ") && !s.contains("note: "))) {
                System.err.println(s);
            }
        };
        Consumer<String> errAppend = s -> sb.append(s).append("\n");
        StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream(), List.of(errOutput,
                errAppend));
        StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream(), List.of(errOutput,
                errAppend));
        new Thread(outputGobbler).start();
        new Thread(errorGobbler).start();
        int code = process.waitFor();

        // Write down the log.
        String errors = sb.toString();
        if (outputFolder.resolve(logFileName).toFile().exists()) {
            //noinspection ResultOfMethodCallIgnored
            outputFolder.resolve(logFileName).toFile().delete();
        }
        Files.write(outputFolder.resolve(logFileName), errors.getBytes());

        return new Pair<>(code, sb.toString());
    }

    public void runQtAttributionsScanner(@NotNull List<Pair<String, Path>> modules)
            throws IOException, InterruptedException {
        if (!new File(qtAttributionsScannerPath).exists()) {
            throw new IOException("Path to QtAttributionsScanner wrong: file " +
                    qtAttributionsScannerPath + " does not exist!");
        }

        // The target directory is that of the Git repository containing the module(s). Compute it as the only shared
        // part in all the paths. Typically, this will end in Src/ if the sources are installed using Qt's installer.
        List<Path> module_paths = modules.stream().map(pair -> pair.second).toList();
        int min_path_length = module_paths.stream().map(Path::getNameCount).min(Comparator.comparingInt(i -> i)).orElse(0);
        assert min_path_length > 0;

        Path common_path = module_paths.get(0).getRoot();
        final Path final_common_path = common_path;
        assert module_paths.stream().map(Path::getRoot).allMatch(path -> path.equals(final_common_path));
        for (int i = 0; i < min_path_length; i++) {
            final int final_i = i;
            Path first_path = module_paths.get(0).getName(final_i);
            boolean all_equal = module_paths.stream().map(path -> path.getName(final_i)).allMatch(name -> name.equals(first_path));
            if (all_equal) {
                common_path = common_path.resolve(first_path);
            } else {
                break;
            }
        }

        // Run the scanner once per Git repository, i.e. directory.
        // Based on https://github.com/qt/qtbase/blob/dev/cmake/QtDocsHelpers.cmake,
        // `add_custom_target(qattributionsscanner_${target}`.
        for (Pair<String, Path> module : modules) {
            // The output file is one level above Qt's .qdocconf file (after analysing the generated build scripts, i.e.
            // after CMake). This .qdocconf is typically in a doc folder.
            Path destination_path = module.second.getParent();
            assert destination_path != null;
            if (destination_path.getFileName().toString().equals("doc")) {
                destination_path = destination_path.getParent();
            }
            destination_path = destination_path.resolve("codeattributions.qdoc");

            Path module_path = common_path.resolve(module.second.getName(common_path.getNameCount()));

            List<String> params = new ArrayList<>(Arrays.asList(qtAttributionsScannerPath,
                    module_path.toString(),
                    "--basedir", common_path.toString(),
                     "--filter", "QDocModule=" + module.second.getFileName().toString().split("\\.")[0], // Use
                    // the name of the .qddocconf file as module.
                    "-o", destination_path.toString()));
            ProcessBuilder pb = new ProcessBuilder(params);

            System.out.println("::> Running QtAttributionsScanner for module " + module.first + " with the following arguments: ");
            printCommand(pb.command());

            Pair<Integer, String> qdocResult = runCommandAndReturnCodeAndLogs(pb, "qtdoctools-qtattributionsscanner-log");
            int code = qdocResult.first;
            String errors = qdocResult.second;

            System.out.println("::> QtAttributionsScanner ran into issues: ");
            System.out.println("::>   - Return code: " + code);
            System.out.println(errors);
        }

        throw new IOException("DONE?");
    }

    public void runQDoc() throws IOException, InterruptedException {
        if (!new File(qdocPath).exists()) {
            throw new IOException("Path to QDoc wrong: file " + qdocPath + " does not exist!");
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
        for (String includePath : cppCompilerIncludes) {
            // https://bugreports.qt.io/browse/QTCREATORBUG-20903
            // Clang includes must come before GCC includes.
            params.add("-I");
            params.add(includePath);
        }
        for (String includePath : findIncludes()) {
            params.add("-I");
            params.add(includePath);
        }
        ProcessBuilder pb = new ProcessBuilder(params);

        System.out.println("::> Running QDoc with the following arguments: ");
        printCommand(pb.command());

        // QDoc requires a series of environment variables.
        Map<String, String> env = pb.environment();
        env.put("QT_INSTALL_DOCS", sourceFolder.resolve("qtbase").resolve("doc").toString());
        env.put("BUILDDIR", sourceFolder.resolve("qtbase").resolve("doc").toString());
        env.put("QT_VERSION_TAG", qtVersion.QT_VERSION_TAG());
        env.put("QT_VER", qtVersion.QT_VER());
        env.put("QT_VERSION", qtVersion.QT_VERSION());

        // Run qdoc and wait until it is done.
        // TODO: the check for errors fails when qdoc.exe exists but loads the wrong DLLs (running it directly shows an
        //  error message): this function ends with "::> QDoc ended with no errors." after showing the full call to
        //  qdoc.
        Pair<Integer, String> qdocResult = runCommandAndReturnCodeAndLogs(pb, "qtdoctools-qdoc-log");
        int qdocCode = qdocResult.first;
        String errors = qdocResult.second;

        // Parse the results from qdoc to find errors.
        int nErrors = countString(errors, "error:");
        int nFatalErrors = countString(errors, "fatal error:");
//        int nMissingDepends = countString(errors, "fatal error: '[a-zA-Z]+/[a-zA-Z]+Depends'
//        file not found"); // Takes too long to compute.

        if (nErrors > 0) {
            System.out.println("::> QDoc ran into issues: ");
            System.out.println("::>   - Return code: " + qdocCode);
            System.out.println("::>   - " + nErrors + " errors");
            System.out.println("::>   - " + nFatalErrors + " fatal errors");
//            System.out.println("::>   - " + nMissingDepends + " missing QtModuleDepends files");
            if (qdocCode != 0) {
                throw new IOException("qdoc ran into errors");
            }
        } else {
            System.out.println("::> QDoc ended with no errors.");
        }
    }

    public void copyGeneratedFiles() throws IOException {
        // Only copy, no move, because consistency checks requires the same folder structure.

        // Maybe everything is under the `html` folder.
        Path abnormalPath = outputFolder.resolve("html");
        if (Files.exists(abnormalPath)) {
            String[] files = abnormalPath.toFile().list((dir, name) -> name.endsWith(".xml"));
            if (files != null && files.length > 0) {
                System.out.println("++> Moving qdoc's result to the expected folder");

                if (Arrays.stream(files)
                        .map(abnormalPath::resolve)
                        .map(Path::toFile)
                        .map(file -> file.renameTo(outputFolder.resolve(file.getName()).toFile()))
                        .anyMatch(val -> !val)) {
                    System.out.println("!!> Moving some files was not possible!");
                }

                if (!abnormalPath.resolve("images").toFile().renameTo(outputFolder.resolve(
                        "images").toFile())) {
                    System.out.println("!!> Moving the `images` folder was not possible!");
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
        for (File subfolder : subfolders) { // For each module...
            // First, deal with the DocBook files.
            File[] files = subfolder.listFiles((dir, name) -> name.endsWith(".xml"));
            if (files != null && files.length > 0) {
                System.out.println("++> Moving qdoc's result from " + subfolder + " to the " +
                        "expected folder");
                for (File f : files) { // For each DocBook file...
                    String name = f.getName();

                    if (name.equals("search-results.xml")) {
                        continue;
                    }

                    Path destination = outputFolder.resolve(name);
                    try {
                        Files.copy(f.toPath(), destination);
                    } catch (FileAlreadyExistsException e) {
                        System.out.println("!!> File already exists: " + destination + ". Tried " +
                                "to copy from: " + f);
                    }
                }
            }

            // Maybe there is an `images` folder to move one level up.
            // Sometimes, the folder has a stranger name, like "images".
            File[] folders = subfolder.listFiles((f, name) -> f.isDirectory());
            if (folders != null) {
                for (File f : folders) {
                    if (f.getName().endsWith("images")) {
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

        if (!destination.toFile().exists()) {
            if (!destination.toFile().mkdirs()) {
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

    public void fixQDocBugs() throws IOException {
        // Only the files in the root folder are considered.
        // TODO: links to xml files
        int nFiles = 0;
        int nFilesRewritten = 0;
        int nFilesIgnored = 0;

        for (Path filePath : findDocBook()) {
            boolean abandon = false;
            boolean hasMatched = false;
            String file = Files.readString(filePath);

            nFiles += 1;

            if (file.length() == 0) {
                abandon = true;
            }

            // if (! abandon) {
            //     ;
            // }

            if (abandon) {
                nFilesIgnored += 1;
                continue;
            }
            if (!hasMatched) {
                // This file has not changed: no need to have a back-up file or to spend time
                // writing on disk.
                continue;
            }
            nFilesRewritten += 1;

            Path fileBackUp = filePath.getParent().resolve(filePath.getFileName() + ".bak");
            if (!fileBackUp.toFile().exists()) {
                Files.move(filePath, fileBackUp);
            }
            Files.write(filePath, file.getBytes());
        }

        System.out.println("++> " + nFiles + " postprocessed, " +
                nFilesRewritten + " rewritten, " + nFilesIgnored + " ignored.");
    }

    public void validateDocBook() throws IOException, SAXException {
        int nFiles = 0;
        int nEmptyFiles = 0;
        int nValidFiles = 0;
        for (Path file : findDocBook()) {
            nFiles += 1;
            if (Files.size(file) == 0) {
                // Validation can only fail for empty files.
                nEmptyFiles += 1;
                continue;
            }

            if (ValidationHelper.validateDocBook(file, config)) {
                nValidFiles += 1;
            } else {
                System.out.println("!!> Invalid file: " + file);
            }
        }
        System.out.println("++> " + nFiles + " validated, " +
                nValidFiles + " valid, " + (nFiles - nValidFiles) + " invalid, " + nEmptyFiles +
                " empty.");
    }

    public void checkDocBookConsistency() throws IOException, SaxonApiException {
        ConsistencyResults cr = new ConsistencyChecks(outputFolder, htmlFolder, ">>> ").checkAll();
        if (! cr.hasErrors()) {
            System.out.println("++> Consistency checks revealed no discrepancy.");
        } else {
            System.out.println("++> Consistency checks revealed differences between DocBook and HTML.");
            System.out.println(cr.describe("++> "));
        }
    }

    private List<Path> findWithExtension(@SuppressWarnings("SameParameterValue") String extension) {
        String[] fileNames =
                outputFolder.toFile().list((current, name) -> name.endsWith(extension));
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
