package be.tcuvelier.qdoctools.core;

import be.tcuvelier.qdoctools.core.utils.Pair;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("FieldCanBeLocal")
public class QtModules {
    // Private attributes are rewritten in terms of another (package-private) attribute.
    // At some point, could be moved to configuration file, but I guess there is little to gain in terms of maintenance.

    public static final List<String> ignoredModules; // A list of modules that have no documentation, and should thus
    // be ignored.
    private static final Map<String, List<String>> submodules; // First-level folders in the source code that have
    // multiple modules in them (like qtconnectivity: bluetooth and nfc).
    public static Map<String, List<Pair<String, String>>> submodulesSpecificNames; // First-level folders in the source
    // code that have multiple modules in them, but the qdocconf files have nonstandard names (like qtquickcontrols:
    // controls->qtquickcontrols, dialogs->qtquickdialogs, extras->qtquickextras).
    private static final Map<String, String> renamedSubfolder; // Modules that have a strange subfolder (like
    // qtdatavis3d: datavisualization).
    public static final Map<String, Pair<Path, String>> qtTools; // Qt Tools follows no other pattern.
    public static final Map<String, Pair<Path, String>> qtBaseTools; // Qt Tools follows no other pattern, even within
    // Qt Base.

    static {
        ignoredModules = Arrays.asList("qttranslations", "qtwebglplugin");
        submodules = Map.of(
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
                "qtlottie",
                Collections.singletonList(new Pair<>("", "qtlottieanimation")),
                "qtwayland", Collections.singletonList(new Pair<>("compositor", "qtwaylandcompositor")),
                "qtbase",
                Arrays.asList(new Pair<>("concurrent", "qtconcurrent"),
                        new Pair<>("corelib", "qtcore"), // Reason why qtbase cannot be in submodules (specific conf file name).
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
                        new Pair<>("platform", "qtlabsplatform")),
                "qtquick1", Collections.singletonList(new Pair<>("", "qtdeclarative")), // Needed for Qt 5.3 and previous.
                "qtenginio", Arrays.asList(new Pair<>("enginio_client", "qtenginio"),
                        new Pair<>("enginio_plugin", "qtenginioqml")) // Needed for Qt 5.3 and previous.
        );
        renamedSubfolder = Map.of(
                "qtdatavis3d", "datavisualization",
                "qtgraphicaleffects", "effects",
                "qtnetworkauth", "oauth"
        );
        qtTools = Map.of(
                "assistant", new Pair<>(Paths.get("src/assistant/assistant/doc/"), "qtassistant"),
                "help", new Pair<>(Paths.get("src/assistant/help/doc/"), "qthelp"),
                "designer", new Pair<>(Paths.get("src/designer/src/designer/doc/"), "qtdesigner"),
                "uitools", new Pair<>(Paths.get("src/designer/src/uitools/doc/"), "qtuitools"),
                "linguist", new Pair<>(Paths.get("src/linguist/linguist/doc/"), "qtlinguist"),
                "qdoc", new Pair<>(Paths.get("src/qdoc/doc/config/"), "qdoc"),
                "qtdistancefieldgenerator", new Pair<>(Paths.get("src/distancefieldgenerator/doc/"), "distancefieldgenerator")
        );
        qtBaseTools = Map.of(
                "qlalr", new Pair<>(Paths.get("src/tools/qlalr/doc/"), "qlalr"),
                "qmake", new Pair<>(Paths.get("qmake/doc/"), "qmake"),
                "qdoc", new Pair<>(Paths.get("src/tools/qdoc/doc/config"), "qdoc") // Needed for Qt 5.3 and previous.
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
}
