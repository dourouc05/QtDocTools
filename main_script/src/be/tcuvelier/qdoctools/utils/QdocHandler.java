package be.tcuvelier.qdoctools.utils;

import be.tcuvelier.qdoctools.ReadQdocconfException;
import be.tcuvelier.qdoctools.WriteQdocconfException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class QdocHandler {
    private final Path sourceFolder; // Containing Qt's sources.
    private final Path outputFolder; // Where all the generated files should be put.
    private final Path mainQdocconfPath; // The qdocconf that lists all the other ones.
    private final String qdocPath = "C:\\Qt\\5.12.0\\msvc2017_64\\bin\\qdoc.exe"; // TODO: Read this from configuration.

    public QdocHandler(String input, String output) {
        sourceFolder = Paths.get(input);
        outputFolder = Paths.get(output);
        mainQdocconfPath = outputFolder.resolve("qtdoctools-main.qdocconf");
    }

    public void ensureOutputFolderExists() throws IOException {
        if (! outputFolder.toFile().isDirectory()) {
            Files.createDirectories(outputFolder);
        }
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
                            srcDirectoryPath.resolve("imports").resolve(submodule.second + ".qdocconf"), // Qt Quick modules.
                            docDirectoryPath.resolve(submodule.second + ".qdocconf"), // Base case.
                            docDirectoryPath.resolve("qt" + submodule.second + ".qdocconf")
                    );

                    Optional<Path> qdocconfOptionalPath = potentialQdocconfPaths.stream().filter(path -> path.toFile().isFile()).findAny();

                    if (! qdocconfOptionalPath.isPresent()) {
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

                if (! qdocconfOptionalPath.isPresent()) {
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

                if (!qdocconfPath.toFile().isFile()) {
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

            if (!qdocconfPath.toFile().isFile()) {
                System.out.println("Skipped submodule: qtdoc / qmake (old Qt 5 only)");
            } else {
                System.out.println("--> Found submodule: qtbase / qmake (old Qt 5 only); qdocconf: " + qdocconfPath.toString());
                modules.add(new Pair<>("qtbase", qdocconfPath));
            }
        }

        return modules;
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
        qdocconf += "WebXML.outputsubdir       = webxml\n";

        Path destinationFile = originalFile.getParent().resolve("qtdoctools-" + module + ".qdocconf");
        try {
            Files.write(originalFile.getParent().resolve("qtdoctools-" + module + ".qdocconf"), qdocconf.getBytes());
        } catch (IOException e) {
            throw new WriteQdocconfException(module, originalFile, destinationFile, e);
        }
    }

    public Path makeMainQdocconf(List<Pair<String, Path>> modules) throws WriteQdocconfException {
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

    public void runQdoc() throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(qdocPath,
                "--outputdir", outputFolder.toString(),
                "--installdir", outputFolder.toString(),
                mainQdocconfPath.toString(),
                "--single-exec",
                "--log-progress");

        System.out.println("::> Running qdoc with the following arguments: ");
        for (String command : pb.command()) {
            System.out.println("        " + command);
        }

        // Qdoc requires a series of environment variables, with no real impact on the WebXML files that get generated.
        Map<String, String> env = pb.environment();
        env.put("QT_INSTALL_DOCS", sourceFolder.resolve("qtbase").resolve("doc").toString());
        env.put("BUILDDIR", sourceFolder.resolve("qtbase").resolve("doc").toString());
        env.put("QT_VERSION_TAG", "100");
        env.put("QT_VER", "1.0");
        env.put("QT_VERSION", "1.0.0");

        // Let qdoc write to this process' stdout.
        pb.inheritIO();

        // Run qdoc and wait until it is done.
        pb.start().waitFor();
    }
}