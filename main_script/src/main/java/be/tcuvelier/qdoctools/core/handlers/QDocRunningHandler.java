package be.tcuvelier.qdoctools.core.handlers;

import be.tcuvelier.qdoctools.core.QtModules;
import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.exceptions.WriteQdocconfException;
import be.tcuvelier.qdoctools.core.utils.Pair;
import be.tcuvelier.qdoctools.core.utils.QtVersion;
import be.tcuvelier.qdoctools.core.utils.StreamGobbler;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Handler for running QDoc and closely related tools (for now, only the attribution scanner).
public class QDocRunningHandler {
    private final Path sourceFolder; // Containing Qt's sources.
    private final Path installedFolder; // Containing a compiled and installed version of Qt.
    private final Path outputFolder; // Where all the generated files should be put (QDoc may
    // also output in a subfolder, in which case the files are automatically moved to a flatter
    // hierarchy).
    private final Path mainQdocconfPath; // The qdocconf that lists all the other ones.
    private final String qtAttributionsScannerPath;
    private final String qdocPath;
    private final QtVersion qtVersion;
    private final boolean qdocDebug;
    private final boolean reduceIncludeListSize;
    private final List<String> cppCompilerIncludes;
    private final GlobalConfiguration config;

    public QDocRunningHandler(String source, String installed, String output, String qdocPath,
                              QtVersion qtVersion, boolean qdocDebug, boolean reduceIncludeListSize,
                              List<String> cppCompilerIncludes, GlobalConfiguration config)
            throws IOException {
        sourceFolder = Paths.get(source);
        installedFolder = Paths.get(installed);
        outputFolder = Paths.get(output);
        mainQdocconfPath = outputFolder.resolve("qtdoctools-main.qdocconf");

        this.config = config; // TODO: remove or use! (e.g., to determine qdocPath)
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
        this.reduceIncludeListSize = reduceIncludeListSize; // TODO: implement when really needed.
        this.cppCompilerIncludes = cppCompilerIncludes;

        if (qdocPath.isEmpty()) {
            throw new IOException("Path to QDoc empty!");
        }
        if (!Paths.get(qdocPath).toFile().exists()) {
            throw new IOException("Path to QDoc wrong: file " + qdocPath + " does not exist!");
        }

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
        Set<Path> module_paths = new HashSet<>(directories.length);
        for (String directory : directories) {
            Path modulePath = sourceFolder.resolve(directory);
            Path srcDirectoryPath = modulePath.resolve("src");

            boolean hasFoundQdocconfModule = false;

            if (directory.equals("qttools")) {
                // The most annoying case: Qt Tools, everything seems ad-hoc, hence the huge list
                // qtTools of hard-coded paths.
                for (Map.Entry<String, List<Pair<Path, String>>> entry : QtModules.qtTools.entrySet()) {
                    List<Path> potentialQdocconfPaths = new ArrayList<>();
                    for (Pair<Path, String> possible_entry : entry.getValue()) {
                        Path docDirectoryPath = modulePath.resolve(possible_entry.first);
                        potentialQdocconfPaths.add(docDirectoryPath.resolve(possible_entry.second + ".qdocconf"));
                        potentialQdocconfPaths.add(docDirectoryPath.resolve("qt" + possible_entry.second + ".qdocconf"));
                    }

                    Optional<Path> qdocconfOptionalPath =
                            potentialQdocconfPaths.stream().filter(path -> path.toFile().isFile()).findAny();

                    if (qdocconfOptionalPath.isEmpty()) {
                        System.out.println("Skipped module \"qttools / " + entry.getKey() + "\": " +
                                "no .qdocconf file");
                        continue;
                    }

                    if (!module_paths.contains(qdocconfOptionalPath.get())) {
                        System.out.println("--> Found submodule: qttools / " + entry.getKey() + "; " +
                                "qdocconf: " + qdocconfOptionalPath.get());
                        modules.add(new Pair<>(directory, qdocconfOptionalPath.get()));
                        module_paths.add(qdocconfOptionalPath.get());
                    }
                }

                hasFoundQdocconfModule = true;
            }

            if (QtModules.submodulesSpecificNames.containsKey(directory)) {
                // Find the path to the documentation folders for each of the submodule.
                for (Pair<String, String> submodule :
                        QtModules.submodulesSpecificNames.get(directory)) {
                    Path importsDirectoryPath = srcDirectoryPath.resolve("imports");
                    Path docDirectoryPath = srcDirectoryPath.resolve(submodule.first).resolve(
                            "doc");
                    Path docImportsDirectoryPath =
                            importsDirectoryPath.resolve(submodule.first).resolve("doc");
                    Path directDocDirectoryPath = modulePath.resolve("doc");

                    // Find the exact qdocconf file.
                    List<Path> potentialQdocconfPaths = Arrays.asList(
                            // ActiveQt.
                            docDirectoryPath.resolve(submodule.second + ".qdocconf"),
                            // Qt Doc.
                            modulePath.resolve("doc").resolve("config").resolve(submodule.second + ".qdocconf"),
                            modulePath.resolve("doc").resolve("src").resolve(submodule.first).resolve(submodule.second + ".qdocconf"),
                            // Qt for Education.
                            directDocDirectoryPath.resolve(submodule.first).resolve("config").resolve(submodule.second + ".qdocconf"),
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
                            // Qt Quick Dialogs 2 (Qt 6).
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
                        if (!hasFoundQdocconfModule) {
                            System.out.println("Skipped module \"" + directory + " / " + submodule.first + "\": no .qdocconf file");
                        }
                        continue;
                    }

                    // Everything seems OK: push this module so that it will be handled later on.
                    if (!module_paths.contains(qdocconfOptionalPath.get())) {
                        System.out.println("--> Found submodule: " + directory + " / " + submodule.first + "; qdocconf: " + qdocconfOptionalPath.get());
                        modules.add(new Pair<>(directory, qdocconfOptionalPath.get()));
                        module_paths.add(qdocconfOptionalPath.get());
                    }

                    hasFoundQdocconfModule = true;
                }
            }

            {
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
                    if (!hasFoundQdocconfModule) {
                        System.out.println("Skipped module \"" + directory + "\": no .qdocconf file");
                    }
                    continue;
                }

                // Everything seems OK: push this module so that it will be handled later on.
                if (!module_paths.contains(qdocconfOptionalPath.get())) {
                    System.out.println("--> Found module: " + directory + "; qdocconf: " + qdocconfOptionalPath.get());
                    modules.add(new Pair<>(directory, qdocconfOptionalPath.get()));
                    module_paths.add(qdocconfOptionalPath.get());
                }
                //noinspection UnusedAssignment
                hasFoundQdocconfModule = true;
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

                if (!module_paths.contains(qdocconfPath)) {
                    System.out.println("--> Found submodule: qtbase / " + entry.getKey() + "; " +
                            "qdocconf: " + qdocconfPath);
                    modules.add(new Pair<>("qtbase", qdocconfPath));
                    module_paths.add(qdocconfPath);
                }
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
                Pair<String, Path> moduleEntry = new Pair<>("qtbase", qdocconfPath);
                if (!modules.contains(moduleEntry)) {
                    System.out.println("--> Found submodule: qtbase / qmake (old Qt 5 only); " +
                            "qdocconf: " + qdocconfPath);
                    modules.add(moduleEntry);
                }
            }
        }

        return new Pair<>(modules, Arrays.asList(directories));
    }

    private List<String> findIncludes() {
        // Accumulate the `include` folders. Base case: just the `include` folder of an installed
        // Qt (which is a much easier way to find all the includes in one place, instead of
        // scattered across all modules).
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

        if (directories.isEmpty()) {
            return includeDirs;
        }

        // Up to now: <Qt>/VERSION/include, such as <Qt>/5.13.0/include.
        // Add paths:
        // - <Qt>/VERSION/include/MODULE
        // - <Qt>/VERSION/include/MODULE/VERSION/MODULE
        // - <Qt>/VERSION/include/MODULE/VERSION/MODULE/private
        // However, do not add <Qt>/VERSION/include/MODULE/VERSION: it is not used
        // (But only if reduceIncludeListSize, just in case.)
        for (Path directory : directories) {
            if (!directory.toFile().exists()) {
                continue;
            }
            File[] subDirs = directory.toFile().listFiles();
            if (subDirs == null || subDirs.length == 0) {
                continue;
            }

            includeDirs.add(directory.toString());

            List<Path> subDirectories = Arrays.stream(subDirs)
                    .filter(File::isDirectory)
                    .filter(f -> f.getName().split("\\.").length == 3)
                    .map(File::toPath).toList();
            for (Path sd : subDirectories) {
                if (!reduceIncludeListSize) {
                    includeDirs.add(sd.toString());
                }

                String moduleName = sd.getParent().toFile().getName();
                Path ssd = sd.resolve(moduleName);
                if (!ssd.toFile().exists()) {
                    continue;
                }

                includeDirs.add(ssd.toString());
                if (ssd.resolve("private").toFile().exists()) {
                    includeDirs.add(ssd.resolve("private").toString());
                }
            }
        }

        return includeDirs;
    }

    // Write a list of the .qdocconf files that must be generated in the qdoc run.
    public Path makeMainQdocconf(@NotNull List<Pair<String, Path>> modules) throws WriteQdocconfException {
        // modules may be immutable, no way to tell, hence always make a copy.
        modules = modules.stream().sorted(Comparator.comparing(a -> a.first)).toList();
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
        int commandLength = 0;
        for (int i = 0; i < pb.command().size(); i++) {
            // Arguments and quotes.
            commandLength += pb.command().get(i).length() + 2;
            // Space between arguments.
            if (i > 0) {
                commandLength += 1;
            }
        }
        if (commandLength >= 32768) {
            System.out.println("!!> Command and arguments are too long for some platforms! Number of characters: " + commandLength);
            System.out.println("!!> Think about --reduce-include-list-size");
        }

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
        if (qtAttributionsScannerPath.isEmpty()) {
            throw new IOException("Path to QtAttributionsScanner empty!");
        }
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

            Pair<Integer, String> qdocResult = runCommandAndReturnCodeAndLogs(pb, "qtdoctools-qtattributionsscanner-" + module.first + "-log");
            int code = qdocResult.first;
            String errors = qdocResult.second;

            if (code != 0) {
                System.out.println("::> QtAttributionsScanner ran into issues: ");
                System.out.println("::>   - Return code: " + code);
                System.out.println(errors);
            }
        }
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
            params.add("-I" + includePath);
        }
        for (String includePath : findIncludes()) {
            params.add("-I" + includePath);
        }
        ProcessBuilder pb = new ProcessBuilder(params);

        System.out.println("::> Running QDoc with the following arguments: ");
        printCommand(pb.command());

        // QDoc requires a series of environment variables.
        Map<String, String> env = pb.environment();
        env.put("QT_INSTALL_DOCS", sourceFolder.resolve("qtbase").resolve("doc").toString());
        env.put("BUILDDIR", sourceFolder.resolve("qtbase").resolve("doc").toString());
        env.put("QT_INSTALL_HEADERS", installedFolder.resolve("include").toString());
        env.put("QT_VERSION_TAG", qtVersion.QT_VERSION_TAG());
        env.put("QT_VER", qtVersion.QT_VER());
        env.put("QT_VERSION", qtVersion.QT_VERSION());

        System.out.println("::> Running QDoc with the following environment variables: ");
        System.out.println("            QT_INSTALL_DOCS: " + env.get("QT_INSTALL_DOCS"));
        System.out.println("            BUILDDIR: " + env.get("BUILDDIR"));
        System.out.println("            QT_INSTALL_HEADERS: " + env.get("QT_INSTALL_HEADERS"));
        System.out.println("            QT_VERSION_TAG: " + env.get("QT_VERSION_TAG"));
        System.out.println("            QT_VER: " + env.get("QT_VER"));
        System.out.println("            QT_VERSION: " + env.get("QT_VERSION"));

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

            // Not all qdoc errors are a reason to stop the process. Determining a good heuristic
            // would be hard, just using the number of errors is not always meaningful. So, don't
            // do it for now.
//            if (qdocCode != 0) {
//                throw new IOException("qdoc ran into errors");
//            }
        } else {
            System.out.println("::> QDoc ended with no errors.");
        }
    }
}
