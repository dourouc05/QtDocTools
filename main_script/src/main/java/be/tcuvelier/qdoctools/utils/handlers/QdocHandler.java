package be.tcuvelier.qdoctools.utils.handlers;

import be.tcuvelier.qdoctools.exceptions.ReadQdocconfException;
import be.tcuvelier.qdoctools.exceptions.WriteQdocconfException;
import be.tcuvelier.qdoctools.utils.StreamGobbler;
import be.tcuvelier.qdoctools.utils.helpers.QtModules;
import be.tcuvelier.qdoctools.utils.Pair;
import be.tcuvelier.qdoctools.utils.QtVersion;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
    private Path outputFolder; // Where all the generated files should be put. Not final, can be updated when looking
    // for WebXML files (qdoc may also output in a subfolder).
    private final Path mainQdocconfPath; // The qdocconf that lists all the other ones.
    private final String qdocPath;
    private final QtVersion qtVersion;
    private final List<String> cppCompilerIncludes;

    public QdocHandler(String input, String output, String qdocPath, QtVersion qtVersion, List<String> cppCompilerIncludes) {
        sourceFolder = Paths.get(input);
        outputFolder = Paths.get(output);
        mainQdocconfPath = outputFolder.resolve("qtdoctools-main.qdocconf");

        this.qdocPath = qdocPath;
        this.qtVersion = qtVersion;
        this.cppCompilerIncludes = cppCompilerIncludes;
    }

    public void ensureOutputFolderExists() throws IOException {
        if (! outputFolder.toFile().isDirectory()) {
            Files.createDirectories(outputFolder);
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
        List<String> includeDirs = new ArrayList<>();

        String[] directories = sourceFolder.toFile().list((current, name) ->
                name.startsWith("q")
                        && new File(current, name).isDirectory()
                        && QtModules.ignoredModules.stream().noneMatch(name::equals)
        );
        if (directories == null || directories.length == 0) {
            return includeDirs;
        }

        for (String directory: directories) {
            Path modulePath = sourceFolder.resolve(directory);
            Path tentative = modulePath.resolve("include");
            if (tentative.toFile().exists()) {
                includeDirs.add(tentative.toString());
            }
        }

        return includeDirs;
    }

    public void rewriteQdocconf(String module, Path originalFile) throws ReadQdocconfException, WriteQdocconfException {
        // Read the existing qdocconf file.
        String qdocconf;
        try {
            qdocconf = new String(Files.readAllBytes(originalFile));
        } catch (IOException e) {
            throw new ReadQdocconfException(module, originalFile, e);
        }

        // Rewrite a more suitable qdocconf file (i.e. generate WebXML output instead of HTML).
        qdocconf += "\n\n";
        qdocconf += "outputdir                 = " + outputFolder.toString() + "\n";
        qdocconf += "outputformats             = WebXML\n";
        qdocconf += "WebXML.quotinginformation = true\n";
        qdocconf += "WebXML.nosubdirs          = true\n";

        Path destinationFile = originalFile.getParent().resolve("qtdoctools-" + module + ".qdocconf");
        try {
            Files.write(originalFile.getParent().resolve("qtdoctools-" + module + ".qdocconf"), qdocconf.getBytes());
        } catch (IOException e) {
            throw new WriteQdocconfException(module, originalFile, destinationFile, e);
        }
    }

    public Path makeMainQdocconf(List<Pair<String, Path>> modules) throws WriteQdocconfException {
        modules.sort(Comparator.comparing(a -> a.first));

        StringBuilder b = new StringBuilder();
        for (Pair<String, Path> module : modules) {
            b.append(module.second.getParent().resolve("qtdoctools-" + module.first + ".qdocconf").toString());
            b.append('\n');
        }

        try {
            Files.write(mainQdocconfPath, b.toString().getBytes());
        } catch (IOException e) {
            throw new WriteQdocconfException(mainQdocconfPath, e);
        }

        return mainQdocconfPath;
    }

    private int countString(String haystack, String needle) {
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
                "--single-exec",
                "--log-progress"));
        for (String includePath: cppCompilerIncludes) {
            params.add("-I");
            params.add(includePath);
        }
        for (String includePath: findIncludes()) {
            params.add("-I");
            params.add(includePath);
        }
        // TODO: --outputformat to get rid of qdocconf rewriting?
        ProcessBuilder pb = new ProcessBuilder(params);

        System.out.println("::> Running qdoc with the following arguments: ");
        boolean firstLine = true;
        for (String command : pb.command()) {
            if (firstLine) {
                System.out.println("        " + command);
            } else {
                System.out.println("            " + command);
            }
            firstLine = false;
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

        // Parse the results from qdoc to find errors.
        String errors = sb.toString();
        int nErrors = countString(errors, "error:");
        int nFatalErrors = countString(errors, "fatal error:");
//        int nMissingDepends = countString(errors, "fatal error: '[a-zA-Z]+/[a-zA-Z]+Depends' file not found"); // Takes too long to compute.

        if (nErrors > 0) {
            System.out.println("::> Qdoc ran into issues: ");
            System.out.println("::>   - " + nErrors + " errors");
            System.out.println("::>   - " + nFatalErrors + " fatal errors");
//            System.out.println("::>   - " + nMissingDepends + " missing QtModuleDepends files");
            System.out.println("::>   Did you forget to configure and build Qt in the source folder? (Linking Qt libraries should not be needed, though.)");
            System.out.println("::>   Are the C and C++ standard libraries configured properly within config.json (cpp_compiler_includes)?");
            System.out.println("::>   If on Windows, install LLVM including llvm-config when building Qt, available for instance from https://github.com/CRogers/LLVM-Windows-Binaries (set LLVM_INSTALL_DIR=C:\\Program Files\\LLVM)");
            System.out.println("::>   Is OpenGL ES used for building (EGL/egl.h should be available) or explicitly disabled (-opengl desktop)? ");
            System.out.println("::>   If on Windows, did you include DBus (configure -dbus-runtime)? ");
        } else {
            System.out.println("::> Qdoc ended with no errors.");
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

    public List<Path> findWebXML() {
        return findWithExtension(".webxml");
    }

    public List<Path> findDocBook() {
        return findWithExtension(".qdt");
    }
}
