package be.tcuvelier.qdoctools.core;

import be.tcuvelier.qdoctools.core.utils.Pair;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class QtModules {
    // Private attributes are rewritten in terms of another (package-private) attribute.
    // At some point, could be moved to configuration file, but I guess there is little to gain
    // in terms of maintenance.

    public static final List<String> ignoredModules; // A list of modules that have no
    // documentation, and should thus
    // qtdatavis3d: datavisualization).
    public static final Map<String, List<Pair<Path, String>>> qtTools; // Qt Tools follows no other
    // pattern.
    public static final Map<String, Pair<Path, String>> qtBaseTools; // Qt Tools follows no other
    // pattern, even within
    // be ignored.
    private static final Map<String, List<String>> submodules; // First-level folders in the
    // source code that have
    // code that have multiple modules in them, but the qdocconf files have nonstandard names
    // (like qtquickcontrols:
    // controls->qtquickcontrols, dialogs->qtquickdialogs, extras->qtquickextras).
    private static final Map<String, String> renamedSubfolder; // Modules that have a strange
    // subfolder (like
    // multiple modules in them (like qtconnectivity: bluetooth and nfc).
    public static Map<String, List<Pair<String, String>>> submodulesSpecificNames; // First-level
    // folders in the source
    // Qt Base.

    static {
        ignoredModules = Arrays.asList("qttranslations", "qtwebglplugin");
        submodules = Map.of(
                "qtconnectivity", Arrays.asList("bluetooth", "nfc"),
                "qtscript", Arrays.asList("script", "scripttools"),
                "qtlocation", Arrays.asList("location", "positioning"),
                "qtlanguageserver", List.of("jsonrpc")
        );
        submodulesSpecificNames = Map.ofEntries(
                Map.entry(
                        "qtquickcontrols",
                        Arrays.asList(new Pair<>("controls", "qtquickcontrols"),
                                new Pair<>("dialogs", "qtquickdialogs"),
                                new Pair<>("extras", "qtquickextras"))),
                Map.entry("qtlottie", Collections.singletonList(new Pair<>("", "qtlottieanimation"
                ))),
                Map.entry("qtwayland", Collections.singletonList(new Pair<>("compositor",
                        "qtwaylandcompositor"))),
                Map.entry(
                        "qtdeclarative",
                        Arrays.asList(
                                // Qt 5.
                                new Pair<>("qml", "qml"),
                                new Pair<>("qmltest", "qmltest"),
                                new Pair<>("quick", "quick"),
                                // Qt 6.
                                new Pair<>("core", "qmlcore"),
                                new Pair<>("qmlworkerscript", "qmlworkerscript"),
                                new Pair<>("qmlxmllistmodel", "qmlxmllistmodel"),
                                new Pair<>("qmlmodels", "qmlmodels"),
                                new Pair<>("quickcontrols2", "quickcontrols"),
                                new Pair<>("quickdialogs2", "quickdialogs"),
                                new Pair<>("labs/platform", "labsplatform"))),
                Map.entry("qtbase",
                        Arrays.asList(new Pair<>("concurrent", "qtconcurrent"),
                                new Pair<>("corelib", "qtcore"), // Reason why qtbase cannot be
                                // in submodules (specific
                                // qtdocconf file name, cannot be guessed from submodule name).
                                new Pair<>("dbus", "qtdbus"),
                                new Pair<>("gui", "qtgui"),
                                new Pair<>("network", "qtnetwork"),
                                new Pair<>("opengl", "qtopengl"),
                                new Pair<>("platformheaders", "qtplatformheaders"),
                                new Pair<>("printsupport", "qtprintsupport"),
                                new Pair<>("sql", "qtsql"),
                                new Pair<>("testlib", "qttestlib"),
                                new Pair<>("widgets", "qtwidgets"),
                                new Pair<>("xml", "qtxml"))),
                Map.entry("qtquickcontrols2",
                        Arrays.asList(new Pair<>("calendar", "qtlabscalendar"),
                                new Pair<>("controls", "qtquickcontrols2"),
                                new Pair<>("platform", "qtlabsplatform"))),
                Map.entry("qt3d", Collections.singletonList(new Pair<>("core", "qt3d"))),
                Map.entry("qt5compat",
                        Arrays.asList(new Pair<>("core5", "core5compat"),
                                new Pair<>("graphicaleffects5", "graphicaleffects5compat"))),
                Map.entry("qtdoc",
                        Arrays.asList(new Pair<>("qtdoc", "qtdoc"),
                                new Pair<>("cmake", "qtcmake"),
                                new Pair<>("platformintegration", "qtplatformintegration"))),
                Map.entry("qtquicktimeline", Collections.singletonList(new Pair<>("timeline",
                        "qtquicktimeline"))),
                Map.entry("qtscxml",
                        Arrays.asList(new Pair<>("scxml", "qtscxml"),
                                new Pair<>("statemachine", "qtstatemachine"))),
                Map.entry("qtwebengine",
                        Arrays.asList(new Pair<>("core", "qtwebengine"),
                                new Pair<>("pdf", "qtpdf"))),

                // Below: Qt 5.3 and previous only.
                Map.entry("qtquick1", Collections.singletonList(new Pair<>("", "qtdeclarative"))),
                Map.entry("qtenginio",
                        Arrays.asList(new Pair<>("enginio_client", "qtenginio"),
                                new Pair<>("enginio_plugin", "qtenginioqml")))
        );
        renamedSubfolder = Map.of(
                "qtdatavis3d", "datavisualization",
                "qtgraphicaleffects", "effects",
                "qtnetworkauth", "oauth"
        );
        qtTools = Map.of(
                "assistant", List.of(new Pair<>(Paths.get("src/assistant/assistant/doc/"), "qtassistant")),
                "help", List.of(new Pair<>(Paths.get("src/assistant/help/doc/"), "qthelp")),
                "designer", List.of(new Pair<>(Paths.get("src/designer/src/designer/doc/"), "qtdesigner")),
                "uitools", List.of(
                        new Pair<>(Paths.get("src/designer/src/uitools/doc/"), "qtuitools"),
                        new Pair<>(Paths.get("src/uitools/doc/"), "qtuitools")
                ),
                "linguist", List.of(new Pair<>(Paths.get("src/linguist/linguist/doc/"), "qtlinguist")),
                "qdoc", List.of(new Pair<>(Paths.get("src/qdoc/doc/config/"), "qdoc")),
                "qtdistancefieldgenerator", List.of(new Pair<>(Paths.get("src/distancefieldgenerator/doc" +
                        "/"), "distancefieldgenerator"))
        );
        qtBaseTools = Map.of(
                "qlalr", new Pair<>(Paths.get("src/tools/qlalr/doc/"), "qlalr"),
                "qmake", new Pair<>(Paths.get("qmake/doc/"), "qmake"),
                "qdoc", new Pair<>(Paths.get("src/tools/qdoc/doc/config"), "qdoc") // Needed for
                // Qt 5.3 and previous.
        );

        // Rewrite submodules and renamedSubfolder into submodulesSpecificNames.
        Map<String, List<Pair<String, String>>> tmp = new HashMap<>(submodulesSpecificNames);
        for (Map.Entry<String, List<String>> entry : submodules.entrySet()) {
            tmp.put(entry.getKey(),
                    entry.getValue().stream().map(s -> new Pair<>(s, s)).collect(Collectors.toList()));
        }
        for (Map.Entry<String, String> entry : renamedSubfolder.entrySet()) {
            tmp.put(entry.getKey(), Collections.singletonList(new Pair<>(entry.getValue(),
                    entry.getKey())));
        }
        submodulesSpecificNames = Collections.unmodifiableMap(tmp);
    }
}
