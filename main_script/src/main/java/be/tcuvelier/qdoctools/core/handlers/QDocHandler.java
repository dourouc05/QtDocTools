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
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Backup extensions:
// - fixQDocBugs: .bak
// - addDates: .bak2
// - fixLinks: .bak3
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
    private final boolean reduceIncludeListSize;
    private final List<String> cppCompilerIncludes;
    private final GlobalConfiguration config;

    public QDocHandler(String source, String installed, String output, String htmlVersion, String qdocPath,
            QtVersion qtVersion, boolean qdocDebug, boolean reduceIncludeListSize, List<String> cppCompilerIncludes, GlobalConfiguration config)
            throws IOException {
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
        this.reduceIncludeListSize = reduceIncludeListSize;
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

        if (directories.isEmpty()) {
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
        env.put("QT_VERSION_TAG", qtVersion.QT_VERSION_TAG());
        env.put("QT_VER", qtVersion.QT_VER());
        env.put("QT_VERSION", qtVersion.QT_VERSION());

        System.out.println("::> Running QDoc with the following environment variables: ");
        System.out.println("            QT_INSTALL_DOCS: " + env.get("QT_INSTALL_DOCS"));
        System.out.println("            BUILDDIR: " + env.get("BUILDDIR"));
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
                        // TODO: add a CLI option to control overwriting.
                        System.out.println("!!> File already exists: " + destination + ". Tried " +
                                "to copy from: " + f + ". Retrying.");
                        Files.copy(f.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
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
                // TODO: add a CLI option to control overwriting.
                System.out.println("!!> File already exists: " + d + ". Tried to copy from: " + f + ". Retrying.");
                Files.copy(f.toPath(), d, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public void fixQDocBugs() throws IOException {
        // Only the files in the root folder are considered.
        int nFiles = 0;
        int nFilesRewritten = 0;
        int nFilesIgnored = 0;

        for (Path filePath : findDocBook()) {
            boolean hasMatched = false;
            String fileContents = Files.readString(filePath);

            nFiles += 1;

            if (fileContents.isEmpty()) {
                nFilesIgnored += 1;
                continue;
            }

            // <db:para><db:para>QXmlStreamReader is part of <db:simplelist><db:member>xml-tools</db:member>
            // <db:member>qtserialization</db:member></db:simplelist></db:para>
            // </db:para>
            // ->
            // <db:para>QXmlStreamReader is part of <db:simplelist><db:member>xml-tools</db:member>
            // <db:member>qtserialization</db:member></db:simplelist></db:para>
            // More generic! Before https://codereview.qt-project.org/c/qt/qttools/+/527899.
            {
                Pattern regex = Pattern.compile(
                        "<db:para><db:para>(.*) is part of <db:simplelist>(.*)</db:simplelist></db:para>\n" +
                        "</db:para>");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;

                    fileContents = matches.replaceAll("<db:para>$1 is part of <db:simplelist>$2</db:simplelist></db:para>");
                }
            }

            //  xml:id=""
            // Nothing.
            {
                Pattern regex = Pattern.compile(" xml:id=\"\"");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll("");
                }
            }

            // <db:img src="images/happy.gif"/>
            // \inlineimage happy.gif
            {
                Pattern regex = Pattern.compile("<db:img src=\"images/happy\\.gif\"/>");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll("\\\\inlineimage happy.gif");
                }
            }

            // <db:emphasis&#246;/>
            // <db:emphasis>&#246;</db:emphasis>
            {
                Pattern regex = Pattern.compile("<db:emphasis&#246;/>");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll("<db:emphasis>&#246;</db:emphasis>");
                }
            }

            // xlink:to="Qt for QNX"
            // xlink:to="qnx.xml"
            // And family.
            {
                Pattern regex = Pattern.compile("xlink:to=\"Qt for QNX\"");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll("xlink:to=\"qnx.xml\"");
                }
            }
            {
                Pattern regex = Pattern.compile("xlink:to=\"Desktop Integration\"");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll("xlink:to=\"desktop-integration.xml\"");
                }
            }

            // <db:section><db:title>Universal.accent : color</db:title><db:fieldsynopsis><db:type>color</db:type>
            // <db:varname>Universal.accent</db:varname></db:fieldsynopsis>
            // <db:anchor xml:id="universal-accent-attached-prop"/>
            // ->
            // <db:section xml:id="universal-accent-attached-prop"><db:title>Universal.accent : color</db:title>
            // <db:fieldsynopsis><db:type>color</db:type><db:varname>Universal.accent</db:varname></db:fieldsynopsis>
            // More generic! Once https://codereview.qt-project.org/c/qt/qtdeclarative/+/528728 is in.
            {
                Pattern regex = Pattern.compile(
                        "<db:section><db:title>(.*) : (.*)</db:title><db:fieldsynopsis><db:type>(.*)</db:type><db:varname>(.*)</db:varname></db:fieldsynopsis><db:anchor xml:id=\"(.*)\"/>");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    // TODO: assert that $1 == $4 and $1 == $2.
                    fileContents = matches.replaceAll(
                            "<db:section xml:id=\"$5\"><db:title>$1 : $2</db:title><db:fieldsynopsis><db:type>$3</db:type><db:varname>$4</db:varname></db:fieldsynopsis>");
                }
            }

            // <db:section xml:id="universal-accent-attached-prop"><db:title>Universal.accent : color</db:title><db:fieldsynopsis><db:type>color</db:type>
            // <db:varname>Universal.accent</db:varname></db:fieldsynopsis>
            // <db:anchor xml:id="universal-accent-attached-prop"/>
            // ->
            // <db:section xml:id="universal-accent-attached-prop"><db:title>Universal.accent : color</db:title>
            // <db:fieldsynopsis><db:type>color</db:type><db:varname>Universal.accent</db:varname></db:fieldsynopsis>
            // More generic! Before https://codereview.qt-project.org/c/qt/qtdeclarative/+/528728.
            {
                Pattern regex = Pattern.compile(
                        "<db:section xml:id=\"(.*)\"><db:title>(.*) : (.*)</db:title><db:fieldsynopsis><db:type>(.*)</db:type><db:varname>(.*)</db:varname></db:fieldsynopsis><db:anchor xml:id=\"(.*)\"/>");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    // TODO: assert that $1 == $6 and $2 == $5 and $2 == $3.
                    fileContents = matches.replaceAll(
                            "<db:section xml:id=\"$1\"><db:title>$2 : $3</db:title><db:fieldsynopsis><db:type>$4</db:type><db:varname>$5</db:varname></db:fieldsynopsis>");
                }
            }

            // </db:abstract>
            // </db:info>
            // </db:article>
            // ->
            // </db:abstract>
            // </db:info>
            // <db:para/>
            // </db:article>
            {
                Pattern regex = Pattern.compile(
                        "</db:abstract>\n</db:info>\n</db:article>");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll(
                            "</db:abstract>\n</db:info>\n<db:para/>\n</db:article>");
                }
            }

            // QAbstract3DGraph only, two parts.
            // ----------------------------
            // </db:itemizedlist>
            // </db:td>
            // <db:tr>
            // <db:td>
            // <db:para>
            // Add a </db:tr>.
            // ----------------------------
            // </db:tr>
            // </db:tr>
            // <db:para>The <db:code>SelectionFlags</db:code> type is a typedef for
            // <db:code><db:link xlink:href="qflags.xml">QFlags</db:link>&lt;SelectionFlag&gt;. </db:code>
            // It stores an OR combination of <db:code>SelectionFlag</db:code> values.</db:para>
            // </db:informaltable>
            // Replace a double </db:tr>, move </db:informaltable> before the paragraph.
            // ----------------------------
            // https://bugreports.qt.io/browse/QTBUG-120457
            {
                Pattern regex = Pattern.compile(
                        "</db:itemizedlist>\n</db:td>\n<db:tr>\n<db:td>\n<db:para>");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll(
                            "</db:itemizedlist>\n</db:td>\n</db:tr>\n<db:tr>\n<db:td>\n<db:para>");
                }
            }
            {
                Pattern regex = Pattern.compile(
                        "</db:tr>\n</db:tr>\n<db:para>(.*)</db:para>\n</db:informaltable>\n<db:section");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll(
                            "</db:tr>\n</db:informaltable>\n<db:para>$1</db:para>\n<db:section");
                }
            }

            // overviews.xml and licenses-used-in-qt.xml and cmake-command-reference.xml and activeqt-tools.xml.
            // <db:variablelist role="explanations-positioning">
            // <db:listitem>
            // Replace by a <db:itemizedlist>, but also the closing tag
            // Hence, a simple regex doesn't capture enough.
            // Hopefully, in overviews.xml and activeqt-tools.xml, there are no true <db:variablelist>s.
            // However, this pattern also appears in licenses-used-in-qt.xml and cmake-command-reference.xml, where true <db:variablelist>s also appear.
            // The only occurrence is in the middle of licenses-used-in-qt.xml (or at the end for cmake-command-reference.xml), with true <db:variablelist>s both before and after.
            // https://codereview.qt-project.org/c/qt/qttools/+/527900
            if (filePath.toString().contains("overviews.xml") || filePath.toString().contains("activeqt-tools.xml")) {
                Pattern regex = Pattern.compile(
                        "<db:variablelist role=\"(.*)\">\n<db:listitem>");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = fileContents.replaceAll("db:variablelist", "db:itemizedlist");
                }
            }
            if (filePath.toString().contains("licenses-used-in-qt.xml")) { // Qt 6.5.
                Pattern regex = Pattern.compile(
                        """
                                <db:variablelist role="annotatedattributions">
                                <db:listitem>
                                <db:para><db:link xlink:href="qtsvg-svggenerator-example.xml" xlink:role="page">SVG Generator Example</db:link></db:para>
                                </db:listitem>
                                <db:listitem>
                                <db:para><db:link xlink:href="qtsvg-svgviewer-example.xml" xlink:role="page">SVG Viewer Example</db:link></db:para>
                                </db:listitem>
                                <db:listitem>
                                <db:para><db:link xlink:href="qtsvg-richtext-textobject-example.xml" xlink:role="page">Text Object Example</db:link></db:para>
                                </db:listitem>
                                </db:variablelist>""");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll(
                            """
                            <db:itemizedlist role="annotatedattributions">
                            <db:listitem>
                            <db:para><db:link xlink:href="qtsvg-svggenerator-example.xml" xlink:role="page">SVG Generator Example</db:link></db:para>
                            </db:listitem>
                            <db:listitem>
                            <db:para><db:link xlink:href="qtsvg-svgviewer-example.xml" xlink:role="page">SVG Viewer Example</db:link></db:para>
                            </db:listitem>
                            <db:listitem>
                            <db:para><db:link xlink:href="qtsvg-richtext-textobject-example.xml" xlink:role="page">Text Object Example</db:link></db:para>
                            </db:listitem>
                            </db:itemizedlist>""");
                }
            }
            if (filePath.toString().contains("licenses-used-in-qt.xml")) { // Qt 6.4.
                Pattern regex = Pattern.compile(
                        """
                                <db:variablelist role="annotatedattributions">
                                <db:listitem>
                                <db:para><db:link xlink:href="qtremoteobjects-modelviewclient-example\\.xml" xlink:role="page">Model-View Client</db:link></db:para>
                                </db:listitem>
                                <db:listitem>
                                <db:para><db:link xlink:href="qtremoteobjects-modelviewserver-example\\.xml" xlink:role="page">Model-View Server</db:link></db:para>
                                </db:listitem>
                                <db:listitem>
                                <db:para><db:link xlink:href="qtremoteobjects-qmlmodelviewclient-example\\.xml" xlink:role="page">QML Model-View Client</db:link></db:para>
                                </db:listitem>
                                <db:listitem>
                                <db:para><db:link xlink:href="qtremoteobjects-ssl-example\\.xml" xlink:role="page">QtRemoteObjects SSL Example</db:link></db:para>
                                </db:listitem>
                                <db:listitem>
                                <db:para><db:link xlink:href="qtremoteobjects-websockets-example\\.xml" xlink:role="page">QtRemoteObjects WebSockets Example</db:link></db:para>
                                </db:listitem>
                                </db:variablelist>""");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll(
                            """
                                    <db:itemizedlist role="annotatedattributions">
                                    <db:listitem>
                                    <db:para><db:link xlink:href="qtremoteobjects-modelviewclient-example.xml" xlink:role="page">Model-View Client</db:link></db:para>
                                    </db:listitem>
                                    <db:listitem>
                                    <db:para><db:link xlink:href="qtremoteobjects-modelviewserver-example.xml" xlink:role="page">Model-View Server</db:link></db:para>
                                    </db:listitem>
                                    <db:listitem>
                                    <db:para><db:link xlink:href="qtremoteobjects-qmlmodelviewclient-example.xml" xlink:role="page">QML Model-View Client</db:link></db:para>
                                    </db:listitem>
                                    <db:listitem>
                                    <db:para><db:link xlink:href="qtremoteobjects-ssl-example.xml" xlink:role="page">QtRemoteObjects SSL Example</db:link></db:para>
                                    </db:listitem>
                                    <db:listitem>
                                    <db:para><db:link xlink:href="qtremoteobjects-websockets-example.xml" xlink:role="page">QtRemoteObjects WebSockets Example</db:link></db:para>
                                    </db:listitem>
                                    </db:itemizedlist>""");
                }
            }
            if (filePath.toString().contains("cmake-command-reference.xml")) {
                Pattern regex = Pattern.compile("""
                        <db:variablelist role="cmake-macros-qtscxml">
                        <db:listitem>
                        <db:para><db:link xlink:href="qtscxml-cmake-qt-add-statecharts.xml" xlink:role="page">qt_add_statecharts</db:link></db:para>
                        </db:listitem>
                        </db:variablelist>""");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll("""
                            <db:itemizedlist role="cmake-macros-qtscxml">
                            <db:listitem>
                            <db:para><db:link xlink:href="qtscxml-cmake-qt-add-statecharts.xml" xlink:role="page">qt_add_statecharts</db:link></db:para>
                            </db:listitem>
                            </db:itemizedlist>""");
                }
            }

            // qml-qt5compat-graphicaleffects-gaussianblur.xml
            // https://codereview.qt-project.org/c/qt/qt5compat/+/527903
            if (filePath.toString().contains("qml-qt5compat-graphicaleffects-gaussianblur.xml")) {
                Pattern regex = Pattern.compile(
                        """
                    <db:para><db:inlinemediaobject>
                    <db:imageobject>
                    <db:imagedata fileref="images/GaussianBlur_deviation_graph\\.png"/>
                    </db:imageobject>
                    </db:inlinemediaobject></db:para>
                    <db:title>The image above shows the Gaussian function with two different deviation values, yellow \\(1\\) and cyan \\(2\\.7\\)\\. The y-axis shows the weights, the x-axis shows the pixel distance.</db:title>""");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll("""
                            <db:figure>
                            <db:title>The image above shows the Gaussian function with two different deviation values, yellow (1) and cyan (2.7). The y-axis shows the weights, the x-axis shows the pixel distance.</db:title>
                            <db:mediaobject>
                            <db:imageobject>
                            <db:imagedata fileref="images/GaussianBlur_deviation_graph.png"/>
                            </db:imageobject>
                            </db:mediaobject>
                            </db:figure>""");
                }
            }

            // qml-color.xml, qcolorconstants.xml
            // <div style="padding:10px;color:#fff;background:#000000;"></div>
            // <db:phrase role="color:#000000">&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;</db:phrase>
            // https://codereview.qt-project.org/c/qt/qtdeclarative/+/421106
            if (filePath.toString().contains("qml-color.xml") || filePath.toString().contains("qcolorconstants.xml")) {
                Pattern regex = Pattern.compile("<div style=\"padding:10px;color:#fff;background:#(.*);\"></div>");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll("<db:phrase role=\"color:#$1\">&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;&#160;</db:phrase>");
                }
            }

            // licenses-used-in-qt.xml
            // <db:bridgehead renderas="sect2" xml:id="additional-information">Additional Information</db:bridgehead><db:para>
            // Transform to a real section, close it at the end of the document. Fixed at Qt 6.5.
            if (filePath.toString().contains("licenses-used-in-qt.xml")) {
                Pattern regex = Pattern.compile("<db:bridgehead renderas=\"sect2\" xml:id=\"additional-information\">Additional Information</db:bridgehead><db:para>");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll("<db:section xml:id=\"additional-information\"><db:title>Additional Information</db:title><db:para>");
                    fileContents = fileContents.replaceAll("</db:variablelist>\n</db:article>", "</db:variablelist>\n</db:section>\n</db:article>");
                }
            }

            // 12-0-qdoc-commands-miscellaneous.xml
            // The original text has indentation (eight spaces):
            //        <blockquote>
            //        <h1 class="title">Foo Namespace</h1>
            //        <p>A namespace. <a>More...</a></p>
            //        <div class="table"><table class="alignedsummary">
            //        <tr><td class="memItemLeft rightAlign topAlign"> Header:</td><td class="memItemRight bottomAlign"> <span class="preprocessor">#include &lt;Bar&gt;</span></td></tr>
            //        <tr><td class="memItemLeft rightAlign topAlign"> CMake:</td><td class="memItemRight bottomAlign"> find_package(Qt6 REQUIRED COMPONENTS Baz)</td></tr>
            //        </table></div>
            //        </blockquote>
            // -> CDATA:
            // <db:blockquote><db:programlisting role="raw-html"><![CDATA[<h1 class="title">Foo Namespace</h1>
            //            <p>A namespace. <a>More...</a></p>
            //            <div class="table"><table class="alignedsummary">
            //            <tr><td class="memItemLeft rightAlign topAlign"> Header:</td><td class="memItemRight bottomAlign"> <span class="preprocessor">#include &lt;Bar&gt;</span></td></tr>
            //            <tr><td class="memItemLeft rightAlign topAlign"> CMake:</td><td class="memItemRight bottomAlign"> find_package(Qt6 REQUIRED COMPONENTS Baz)</td></tr>
            //            </table></div>]]></db:programlisting>
            // </db:blockquote>
            if (filePath.toString().contains("12-0-qdoc-commands-miscellaneous.xml")) {
                Pattern regex = Pattern.compile("        <blockquote>\n" +
                        "        <h1 class=\"title\">Foo Namespace</h1>\n" +
                        "        <p>A namespace. <a>More...</a></p>\n" +
                        "        <div class=\"table\"><table class=\"alignedsummary\">\n" +
                        "        <tr><td class=\"memItemLeft rightAlign topAlign\"> Header:</td><td class=\"memItemRight bottomAlign\"> <span class=\"preprocessor\">#include &lt;Bar&gt;</span></td></tr>\n" +
                        "        <tr><td class=\"memItemLeft rightAlign topAlign\"> CMake:</td><td class=\"memItemRight bottomAlign\"> find_package\\(Qt6 REQUIRED COMPONENTS Baz\\)</td></tr>\n" +
                        "        </table></div>\n" +
                        "        </blockquote>");
                Matcher matches = regex.matcher(fileContents);
                if (matches.find()) {
                    hasMatched = true;
                    fileContents = matches.replaceAll("<db:blockquote><db:programlisting role=\"raw-html\"><![CDATA[<h1 class=\"title\">Foo Namespace</h1>\n            <p>A namespace. <a>More...</a></p>\n            <div class=\"table\"><table class=\"alignedsummary\">\n            <tr><td class=\"memItemLeft rightAlign topAlign\"> Header:</td><td class=\"memItemRight bottomAlign\"> <span class=\"preprocessor\">#include &lt;Bar&gt;</span></td></tr>\n            <tr><td class=\"memItemLeft rightAlign topAlign\"> CMake:</td><td class=\"memItemRight bottomAlign\"> find_package(Qt6 REQUIRED COMPONENTS Baz)</td></tr>\n            </table></div>]]></db:programlisting>\n</db:blockquote>");
                }
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
            Files.write(filePath, fileContents.getBytes());
        }

        System.out.println("++> " + nFiles + " postprocessed, " +
                nFilesRewritten + " rewritten, " + nFilesIgnored + " ignored.");
    }

    public void addDates() throws IOException {
        // The following patterns appears only once per file:
        // </db:abstract>
        // </db:info>
        // Insert the dates just there.
        Pattern regex = Pattern.compile("</db:abstract>\n</db:info>");
        String replacement = "</db:abstract>\n<db:pubdate>" + java.time.LocalDate.now() + "</db:pubdate>\n<db:date>" + java.time.LocalDate.now() + "</db:date>\n</db:info>";

        for (Path filePath : findDocBook()) {
            String fileContents = Files.readString(filePath);

            fileContents = regex.matcher(fileContents).replaceAll(replacement);

            Path fileBackUp = filePath.getParent().resolve(filePath.getFileName() + ".bak2");
            if (!fileBackUp.toFile().exists()) {
                Files.move(filePath, fileBackUp);
            }
            Files.write(filePath, fileContents.getBytes());
        }
    }

    public void fixLinks() throws IOException {
        // Update the links (but not the anchors):
        //    xlink:href="../qtcore/qobject.xml"
        //    xlink:href="../qdoc/22-qdoc-configuration-generalvariables.xml#headers-variable"
        Pattern regex = Pattern.compile("xlink:href=\"\\.\\./[a-z]*/(.*)\\.xml");

        for (Path filePath : findDocBook()) {
            String fileContents = Files.readString(filePath);

            fileContents = regex.matcher(fileContents).replaceAll("xlink:href=\"$1.xml");

            Path fileBackUp = filePath.getParent().resolve(filePath.getFileName() + ".bak3");
            if (!fileBackUp.toFile().exists()) {
                Files.move(filePath, fileBackUp);
            }
            Files.write(filePath, fileContents.getBytes());
        }
    }

    public void validateDocBook() throws IOException, SAXException {
        int nFiles = 0;
        int nEmptyFiles = 0;
        int nValidFiles = 0;
        for (Path filePath : findDocBook()) {
            nFiles += 1;
            if (Files.size(filePath) == 0) {
                // Validation can only fail for empty files.
                nEmptyFiles += 1;
                continue;
            }

            if (ValidationHelper.validateDocBook(filePath, config)) {
                nValidFiles += 1;
            } else {
                System.out.println("!!> Invalid file: " + filePath);
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
