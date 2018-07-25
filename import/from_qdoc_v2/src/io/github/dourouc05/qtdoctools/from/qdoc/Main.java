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
        // 3. Generate the attributions.
        //    C:\Qt\Qt-5.11.1\bin\qtattributionsscanner.exe C:/Qt/5.11.1/Src/qtbase --filter QDocModule=qtcore -o C:/Qt/5.11.1_build/qtbase/src/corelib/codeattributions.qdoc
        // 4. Generate indices.
        // 5. Run QDoc for good to get the WebXML files.
        // 6. Run Saxon to retrieve DocBook files.
        Main m = new Main();
        m.findModules();
    }

    private Path sourceFolder; // Containing Qt's sources.
    private Path outputFolder; // Where all the generated files should be put.

    private String qtattributionsscannerPath; // Path to qtattributionsscanner.
    private String qdocPath; // Path to qdoc.

    private List<String> ignoredModules; // A list of modules that have no documentation, and should thus be ignored.
//    private Map<String, List<String>> submodules; // First-level folders in the source code that have multiple
    // modules in them (like qtconnectivity: bluetooth and nfc).
    private Map<String, List<Pair<String, String>>> submodulesSpecificNames; // First-level folders in the source code
    // that have multiple modules in them, but the qdocconf files have nonstandard names (like qtquickcontrols:
    // controls->qtquickcontrols, dialogs->qtquickdialogs, extras->qtquickextras).
//    private Map<String, String> renamedSubfolder; // Modules that have a strange subfolder (like qtdatavis3d: datavisualization).
    private Map<String, Pair<Path, String>> qtTools; // Qt Tools follows no other pattern.
    private Map<String, Pair<Path, String>> qtBaseTools; // Qt Tools follows no other pattern, even within Qt Base.

    private Main() {
        qdocPath = "";

        sourceFolder = Paths.get("C:\\Qt\\5.11.1\\Src");
        outputFolder = Paths.get("C:\\Qt\\Doc");

        ignoredModules = Arrays.asList("qttranslations", "qtwebglplugin");
        Map<String, List<String>> submodules = Map.of(
                "qtconnectivity", Arrays.asList("bluetooth", "nfc"),
                "qtdeclarative", Arrays.asList("qml", "qmltest", "quick"),
                "qtscript", Arrays.asList("script", "scripttools"),
                "qtlocation", Arrays.asList("location", "positioning")
        );
        submodulesSpecificNames = Map.of(
                "qtquickcontrols",
                Arrays.asList(new Pair<>("controls", "qtquickcontrols"),
                        new Pair<>("dialogs", "qtquickdialogs"),
                        new Pair<>("extras", "qtquickextras")),
                "qtwayland", Arrays.asList(new Pair<>("compositor", "qtwaylandcompositor")),
                "qtbase",
                Arrays.asList(new Pair<>("concurrent", "qtconcurrent"),
                        new Pair<>("corelib", "qtcore"), // Reason why qtbase cannot be in submodules.
                        new Pair<>("dbus", "qtdbus"),
                        new Pair<>("gui", "qtgui"),
                        new Pair<>("network", "qtnetwork"),
                        new Pair<>("opengl", "qtopengl"),
                        new Pair<>("platformheaders", "qtplatformheaders"),
                        new Pair<>("printsupport", "qtprintsupport"),
                        new Pair<>("sql", "qtsql"),
                        new Pair<>("testlib", "qttestlib"),
                        new Pair<>("widgets", "qtwidgets"),
                        new Pair<>("xml", "qtxml")),
                "qtquickcontrols2",
                Arrays.asList(new Pair<>("calendar", "qtlabscalendar"),
                        new Pair<>("controls", "qtquickcontrols2"),
                        new Pair<>("platform", "qtlabsplatform"))
        );
        Map<String, String> renamedSubfolder = Map.of(
                "qtdatavis3d", "datavisualization",
                "qtgraphicaleffects", "effects",
                "qtnetworkauth", "oauth"
        );
        qtTools = Map.of(
                "assistant", new Pair<>(Paths.get("src/assistant/assistant/doc/"), "qtassistant"),
                // TODO: What to do about Src\qttools\src\assistant\assistant\doc\internal\assistant.qdocconf?
                "help", new Pair<>(Paths.get("src/assistant/help/doc/"), "qthelp"),
                "designer", new Pair<>(Paths.get("src/designer/src/designer/doc/"), "qtdesigner"),
                "uitools", new Pair<>(Paths.get("src/designer/src/uitools/doc/"), "qtuitools"),
                "linguist", new Pair<>(Paths.get("src/linguist/linguist/doc/"), "qtlinguist"),
                "qdoc", new Pair<>(Paths.get("src/qdoc/doc/config/"), "qdoc")
        );
        qtBaseTools = Map.of(
                "qlalr", new Pair<>(Paths.get("src/tools/qlalr/doc/"), "qlalr"),
                "qmake", new Pair<>(Paths.get("qmake/doc/"), "qmake")
        );

        // Rewrite submodules and renamedSubfolder into submodulesSpecificNames.
        Map<String, List<Pair<String, String>>> tmp = new HashMap<>(submodulesSpecificNames);
        for (Map.Entry<String, List<String>> entry : submodules.entrySet()) {
            tmp.put(entry.getKey(), entry.getValue().stream().map(s -> new Pair<>(s, s)).collect(Collectors.toList()));
        }
        for (Map.Entry<String, String> entry : renamedSubfolder.entrySet()) {
            tmp.put(entry.getKey(), Collections.singletonList(new Pair<>(entry.getValue(), entry.getKey())));
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
        // Find the qdocconf files, skip if it does not exist at known places.
        List<Triple<String, Path, Path>> modules = new ArrayList<>(directories.length);
        for (String directory : directories) {
            Path modulePath = sourceFolder.resolve(directory);
            Path srcDirectoryPath = modulePath.resolve("src");

            if (directory.equals("qttools")) {
                // The most annoying case: Qt Tools, everything seems ad-hoc.
                for (Map.Entry<String, Pair<Path, String>> entry : qtTools.entrySet()) {
                    Path docDirectoryPath = modulePath.resolve(entry.getValue().first);
                    Path qdocconfPath = docDirectoryPath.resolve(entry.getValue().second + ".qdocconf");

                    if (! qdocconfPath.toFile().isFile()) {
                        System.out.println("Skipped module: qttools / " + entry.getKey());
                        continue;
                    }

                    System.out.println("--> Found submodule: qttools / " + entry.getKey() + "; qdocconf: " + qdocconfPath.toString());
                    Path qdocconfRewrittenPath = docDirectoryPath.resolve("qtdoctools-" + entry.getValue().second + ".qdocconf");
                    modules.add(new Triple<>(directory, qdocconfPath, qdocconfRewrittenPath));
                }
            } else if (submodulesSpecificNames.containsKey(directory)) {
                // Find the path to the documentation folders for each of the submodule.
                for (Pair<String, String> submodule : submodulesSpecificNames.get(directory)) {
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
                    if (directory.equals("qtdoc")) {
                        docDirectoryPath = modulePath.resolve("doc").resolve("config");
                    }

                    Optional<Path> qdocconfOptionalPath = potentialQdocconfPaths.stream().filter(path -> path.toFile().isFile()).findAny();

                    if (! qdocconfOptionalPath.isPresent()) {
                        System.out.println("Skipped module: " + directory + " / " + submodule.first);
                        continue;
                    }

                    // Everything seems OK: push this module so that it will be handled later on.
                    Path qdocconfPath = qdocconfOptionalPath.get();
                    System.out.println("--> Found submodule: " + directory + " / " + submodule.first + "; qdocconf: " + qdocconfPath.toString());
                    Path qdocconfRewrittenPath = docDirectoryPath.resolve("qtdoctools-" + directory + ".qdocconf");
                    modules.add(new Triple<>(directory, qdocconfPath, qdocconfRewrittenPath));
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
                        docDirectoryPath.resolve(directory + ".qdocconf") // Base case. E.g.: doc\qtdeclarative.qdocconf
                );
                if (directory.equals("qtdoc")) {
                    docDirectoryPath = modulePath.resolve("doc").resolve("config");
                }

                Optional<Path> qdocconfOptionalPath = potentialQdocconfPaths.stream().filter(path -> path.toFile().isFile()).findAny();

                if (! qdocconfOptionalPath.isPresent()) {
                    System.out.println("Skipped module: " + directory);
                    continue;
                }

                // Everything seems OK: push this module so that it will be handled later on.
                Path qdocconfPath = qdocconfOptionalPath.get();
                System.out.println("--> Found module: " + directory + "; qdocconf: " + qdocconfPath.toString());
                Path qdocconfRewrittenPath = docDirectoryPath.resolve("qtdoctools-" + directory + ".qdocconf");
                modules.add(new Triple<>(directory, qdocconfPath, qdocconfRewrittenPath));
            }
        }

        // Tools within Qt Base are another source of headaches...
        Path qtBasePath = sourceFolder.resolve("qtbase");
        for (Map.Entry<String, Pair<Path, String>> entry : qtBaseTools.entrySet()) {
            Path docDirectoryPath = qtBasePath.resolve(entry.getValue().first);
            Path qdocconfPath = docDirectoryPath.resolve(entry.getValue().second + ".qdocconf");

            if (! qdocconfPath.toFile().isFile()) {
                System.out.println("Skipped module: qtbase / " + entry.getKey());
                continue;
            }

            System.out.println("--> Found submodule: qtbase / " + entry.getKey() + "; qdocconf: " + qdocconfPath.toString());
            Path qdocconfRewrittenPath = docDirectoryPath.resolve("qtdoctools-" + entry.getValue().second + ".qdocconf");
            modules.add(new Triple<>("qtbase", qdocconfPath, qdocconfRewrittenPath));
        }

        System.out.println("::: " + modules.size() + " modules found");

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
}
