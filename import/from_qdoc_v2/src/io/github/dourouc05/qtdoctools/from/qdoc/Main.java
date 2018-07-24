package io.github.dourouc05.qtdoctools.from.qdoc;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        // 1. Index all modules.
        // 2. Create the new qdocconf files.
        // 3. Generate indices.
        // 4. Run QDoc for good to get the WebXML files.
        // 5. Run Saxon to retrieve DocBook files.
        Main m = new Main();
        m.findModules();
    }

    private Path sourceFolder; // Containing Qt's sources.
    private Path outputFolder; // Where all the generated files should be put.

    private String qdocPath; // Path to QDoc.

    private List<String> ignoredModules; // A list of modules that have no documentation, and should thus be ignored.
    private List<String> pureQtQuickModules; // A list of modules that only have a Qt Quick plugin.
//    private Map<String, List<String>> submodules; // First-level folders in the source code that have multiple
    // modules in them (like qtconnectivity: bluetooth and nfc).
    private Map<String, List<Pair<String, String>>> submodulesSpecificNames; // First-level folders in the source code
    // that have multiple modules in them, but the qdocconf files have nonstandard names (like qtquickcontrols:
    // controls->qtquickcontrols, dialogs->qtquickdialogs, extras->qtquickextras).
    private Map<String, String> renamedSubfolder; // Modules that have a strange subfolder (like qtdatavis3d: datavisualization).

    private Main() {
        qdocPath = "";

        sourceFolder = Paths.get("C:\\Qt\\5.11.1\\Src");
        outputFolder = Paths.get("C:\\Qt\\Doc");

        ignoredModules = Arrays.asList("qttranslations", "qtwayland", "qlalr");
        pureQtQuickModules = Arrays.asList("qtcanvas3d");
        Map<String, List<String>> submodules = Map.of(
                "qtconnectivity", Arrays.asList("bluetooth", "nfc"),
                "qtdeclarative", Arrays.asList("qml", "qmltest", "quick")
        );
        submodulesSpecificNames = Map.of(
                "qtquickcontrols",
                Arrays.asList(new Pair<>("controls", "qtquickcontrols"),
                        new Pair<>("dialogs", "qtquickdialogs"),
                        new Pair<>("extras", "qtquickextras"))
        );
        renamedSubfolder = Map.of(
                "qtdatavis3d", "datavisualization",
                "qtgraphicaleffects", "effects",
                "qtnetworkauth", "oauth"
        );

        // Rewrite submodules into submodulesSpecificNames.
        Map<String, List<Pair<String, String>>> tmp = new HashMap<>(submodulesSpecificNames);
        for (Map.Entry<String, List<String>> entry : submodules.entrySet()) {
            tmp.put(entry.getKey(), entry.getValue().stream().map(s -> new Pair<>(s, "qt" + s)).collect(Collectors.toList()));
        }
        submodulesSpecificNames = Collections.unmodifiableMap(tmp);
    }

    private void findModules() {
        // List all folders within Qt's sources that correspond to modules.
        String[] directories = sourceFolder.toFile().list((current, name) ->
                name.startsWith("q")
                && new File(current, name).isDirectory()
                && ignoredModules.stream().noneMatch(name::equals)
        );

        if (directories == null || directories.length == 0) {
            throw new RuntimeException("No modules found in the given source directory (" + sourceFolder + ")");
        }

        // Loop over all these folders and identify the modules (and their associated qdocconf file).
        // Process based on https://github.com/pyside/pyside2-setup/blob/5.11/sources/pyside2/doc/CMakeLists.txt
        List<Triple<String, Path, Path>> modules = new ArrayList<>(directories.length);
        for (String directory : directories) {
            if (directory.equals("qtbase")) {
                // Qt Core is a really special case.
            } else if (submodulesSpecificNames.containsKey(directory)) {
                // Find the qdocconf file, skip if it does not exist at known places.
                Path modulePath = sourceFolder.resolve(directory);
                String moduleName = directory.replaceFirst("qt", "");

                // TODO: Any kind of Qt Quick handling? Not for Qt Connectivity.

                // Find the path to the documentation folders for each of the submodule.
                for (Pair<String, String> submodule : submodulesSpecificNames.get(directory)) {
                    Path docDirectoryPath = modulePath.resolve("src");
                    docDirectoryPath = docDirectoryPath.resolve(submodule.first);
                    docDirectoryPath = docDirectoryPath.resolve("doc");

                    if (!docDirectoryPath.toFile().isDirectory()) {
                        System.out.println("Skipped submodule: " + directory + " / " + submodule.first + "; expected a doc folder at: " + docDirectoryPath.toString());
                        continue;
                    }

                    // Find the exact qdocconf file.
                    Path qdocconfPath = docDirectoryPath.resolve(submodule.second + ".qdocconf");

                    if (! qdocconfPath.toFile().isFile()) {
                        System.out.println("Skipped submodule: " + directory + " / " + submodule.first + "; expected a qdocconf at: " + qdocconfPath.toString());
                        continue;
                    }

                    // Everything seems OK: push this module so that it will be handled later on.
                    System.out.println("--> Found submodule: " + directory + " / " + submodule.first + "; qdocconf: " + qdocconfPath.toString());
                    Path qdocconfRewrittenPath = docDirectoryPath.resolve("qtdoctools-" + directory + ".qdocconf");
                    modules.add(new Triple<>(directory, qdocconfPath, qdocconfRewrittenPath));
                }
            } else {
                // Find the qdocconf file, skip if it does not exist at known places.
                Path modulePath = sourceFolder.resolve(directory);
                String moduleName = directory.replaceFirst("qt", "");
                boolean isPureQtQuick = pureQtQuickModules.stream().anyMatch(directory::equals);

                // Find the path to the documentation folder.
                Path docDirectoryPath = modulePath.resolve("src");
                if (isPureQtQuick) {
                    docDirectoryPath = docDirectoryPath.resolve("imports").resolve(directory);
                } else if (renamedSubfolder.containsKey(directory)) {
                    docDirectoryPath = docDirectoryPath.resolve(renamedSubfolder.get(directory));
                } else {
                    docDirectoryPath = docDirectoryPath.resolve(moduleName);
                }
                docDirectoryPath = docDirectoryPath.resolve("doc");

                if (! docDirectoryPath.toFile().isDirectory() && ! directory.equals("qtdoc")) {
                    System.out.println("Skipped module: " + directory + "; expected a doc folder at: " + docDirectoryPath.toString());
                    continue;
                }

                // Find the exact qdocconf file.
                Path qdocconfPath;
                if (directory.equals("qtactiveqt")) { // ActiveQt is a real exception: only module to have Qt at the end of the name.
                    qdocconfPath = docDirectoryPath.resolve(moduleName + ".qdocconf"); // E.g.: doc\activeqt.qdocconf
                } else if (directory.equals("qtdoc")) { // Qt Doc is the only real exception, as it does not contain code, only doc.
                    docDirectoryPath = modulePath.resolve("doc").resolve("config");
                    qdocconfPath = docDirectoryPath.resolve(directory + ".qdocconf");
                } else {
                    qdocconfPath = docDirectoryPath.resolve(directory + ".qdocconf"); // E.g.: doc\qtdeclarative.qdocconf
                }

                if (! qdocconfPath.toFile().isFile()) {
                    System.out.println("Skipped module: " + directory + "; expected a qdocconf at: " + qdocconfPath.toString());
                    continue;
                }

                // Everything seems OK: push this module so that it will be handled later on.
                System.out.println("--> Found module: " + directory + "; qdocconf: " + qdocconfPath.toString());
                Path qdocconfRewrittenPath = docDirectoryPath.resolve("qtdoctools-" + directory + ".qdocconf");
                modules.add(new Triple<>(directory, qdocconfPath, qdocconfRewrittenPath));
            }
        }

        // Based on the previous loop, rewrite the needed qdocconf files (one per module, may be multiple times per folder).
//        for (Triple<String, Path, Path> module : modules) {
//            try {
//                rewriteQdocconf(module.first, module.second, module.third);
//                System.out.println("++> Module qdocconf rewritten: " + module.first);
//            } catch (IOException e) {
//                System.out.println(e.getMessage());
//            }
//        }
    }

    private void rewriteQdocconf(String module, Path originalFile, Path destinationFile) throws ReadQdocconfException, WriteQdocconfException {
        // Read the existing qdocconf file.
        String qdocconf;
        try {
            qdocconf = new String(Files.readAllBytes(originalFile));
        } catch (IOException e) {
            throw new ReadQdocconfException(module, originalFile, e);
        }

        // Rewrite a more suitable qdocconf file.
        qdocconf += "outputdir                 = " + outputFolder.toString() + "\n";
        qdocconf += "outputformats             = WebXML\n";
        qdocconf += "WebXML.quotinginformation = true\n";
        qdocconf += "WebXML.nosubdirs          = true\n";
        qdocconf += "WebXML.outputsubdir       = webxml\n";

        try {
            Files.write(destinationFile, qdocconf.getBytes());
        } catch (IOException e) {
            throw new WriteQdocconfException(module, originalFile, destinationFile, e);
        }
    }

    private class ReadQdocconfException extends IOException {
        ReadQdocconfException(String module, Path originalFile, Throwable cause) {
            super("Problem while reading module's qdocconf: " + module + "; " +
                    "reading from: " + originalFile.toString(), cause);
        }
    }

    private class WriteQdocconfException extends IOException {
        WriteQdocconfException(String module, Path originalFile, Path destinationFile, Throwable cause) {
            super("Problem while writing module's qdocconf: " + module + "; " +
                    "reading from: " + originalFile.toString() + "; " +
                    "writing to: " + destinationFile.toString(), cause);
        }
    }

    private class Pair<T, U> {
        private final T first;
        private final U second;

        Pair(T first, U second) {
            this.first = first;
            this.second = second;
        }
    }

    private class Triple<T, U, V> {
        private final T first;
        private final U second;
        private final V third;

        Triple(T first, U second, V third) {
            this.first = first;
            this.second = second;
            this.third = third;
        }
    }
}
