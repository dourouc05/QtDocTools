package io.github.dourouc05.qtdoctools.from.qdoc;

import com.thaiopensource.relaxng.jaxp.CompactSyntaxSchemaFactory;
import net.sf.saxon.lib.StandardErrorListener;
import net.sf.saxon.lib.StandardLogger;
import net.sf.saxon.s9api.*;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws IOException, InterruptedException, SaxonApiException {
        // 1. Index all modules.
        // 2. Create the new qdocconf files.
        // 3. Generate the attributions. TODO!
        //    C:\Qt\Qt-5.11.1\bin\qtattributionsscanner.exe C:/Qt/5.11.1/Src/qtbase --filter QDocModule=qtcore -o C:/Qt/5.11.1_build/qtbase/src/corelib/codeattributions.qdoc
        // 4. Run QDoc to get the WebXML files.
        // 5. Run Saxon to retrieve the DocBook files.
        // 6. Run Saxon to retrieve the DvpML files.

        // TODO: Parse inputs!

        Main m = new Main();

        // Ensure the output folder exists.
//        if (! m.outputFolder.toFile().isDirectory()) {
//            Files.createDirectories(m.outputFolder);
//        }

        // Explore the source directory for the qdocconf files.
        List<Pair<String, Path>> modules = m.findModules();
        System.out.println("::> " + modules.size() + " modules found");

        // Rewrite the needed qdocconf files (one per module, may be multiple times per folder).
        for (Pair<String, Path> module : modules) {
            m.rewriteQdocconf(module.first, module.second);
            System.out.println("++> Module qdocconf rewritten: " + module.first);
        }

        // Generate the main qdocconf file.
//        Path mainQdocconfPath = m.outputFolder.resolve("qtdoctools-main.qdocconf"); // TODO: Get m.outputFolder from the command-line parameters.
//        m.makeMainQdocconf(modules, mainQdocconfPath);
//        System.out.println("++> Main qdocconf rewritten: " + mainQdocconfPath);

        // Run qdoc.
//        System.out.println("++> Running qdoc.");
//        m.runQdoc(mainQdocconfPath);
//        System.out.println("++> Qdoc done.");

        // Gather all WebXML files and transform them into DocBook.
        List<Path> webxml = m.findWebXML();

        int i = 0;
        String iFormat = "%0" + Integer.toString(webxml.size()).length() + "d";
        for (Path file : webxml) {
            Path destination = file.getParent().resolve(file.getFileName().toString().replaceFirst("[.][^.]+$", "") + ".qdt");

            // Print the name of the file to process to ease debugging.
            System.out.println("[" + String.format(iFormat, i + 1) + "/" + webxml.size() + "]" + file.toString());
            System.out.flush();

            m.runXSLTWebXMLToDocBook(file.toFile(), destination);
            try {
                m.validateDocBook(destination.toFile());
            } catch (SAXException e) {
                System.out.println("[" + String.format(iFormat, i + 1) + "/" + webxml.size() + "] Validation error!");
                e.printStackTrace();
            }
            ++i;
        }

        // Gather all DocBook files and transform them into DvpML.
        List<Path> qdt = m.findDocBook();

        if (webxml.size() != qdt.size()) {
            System.out.println("Warning: not the same number of WebXML and DocBook files (" + webxml.size() + " vs. " + qdt.size() + ". ");
        }

        i = 0;
        iFormat = "%0" + Integer.toString(qdt.size()).length() + "d";
        for (Path file : qdt) {
//            Path destination = file.getParent().resolve(file.getFileName().toString().replaceFirst("[.][^.]+$", "") + ".xml");
//
//            // Print the name of the file to process to ease debugging.
//            System.out.println("[" + String.format(iFormat, i + 1) + "/" + qdt.size() + "]" + file.toString());
//            System.out.flush();
//
//            m.runXSLTDocBookToDvpML(file.toFile(), destination);
//            ++i;
        }
    }

    private Path sourceFolder; // Containing Qt's sources.
    private Path outputFolder; // Where all the generated files should be put.

    private final String qtattributionsscannerPath; // Path to qtattributionsscanner.
    private final String qdocPath; // Path to qdoc.
    private final String xsltWebXMLToDocBookPath; // Path to the XSLT sheet WebXML to DocBook.
    private final String xsltDocBookToDvpMLPath; // Path to the XSLT sheet DocBook to DvpML.
    private final String docBookRNGPath;

    private List<String> ignoredModules; // A list of modules that have no documentation, and should thus be ignored.
//    private Map<String, List<String>> submodules; // First-level folders in the source code that have multiple
    // modules in them (like qtconnectivity: bluetooth and nfc).
    private Map<String, List<Pair<String, String>>> submodulesSpecificNames; // First-level folders in the source code
    // that have multiple modules in them, but the qdocconf files have nonstandard names (like qtquickcontrols:
    // controls->qtquickcontrols, dialogs->qtquickdialogs, extras->qtquickextras).
//    private Map<String, String> renamedSubfolder; // Modules that have a strange subfolder (like qtdatavis3d: datavisualization).
    private Map<String, Pair<Path, String>> qtTools; // Qt Tools follows no other pattern.
    private Map<String, Pair<Path, String>> qtBaseTools; // Qt Tools follows no other pattern, even within Qt Base.

    private Processor saxonProcessor;
    private XsltCompiler saxonCompiler;
    private XsltExecutable saxonExecutableWebXMLToDocBook;
    private XsltExecutable saxonExecutableDocBookToDvpML;

    private Main() {
        qdocPath = "C:\\Qt\\5.11.1\\msvc2017_64\\bin\\qdoc.exe";
        qtattributionsscannerPath = "C:\\Qt\\5.11.1\\msvc2017_64\\bin\\qtattributionsscanner.exe"; // TODO!
        xsltWebXMLToDocBookPath = "D:\\Dvp\\QtDoc\\QtDocTools\\import\\from_qdoc_v2\\xslt\\webxml_to_docbook.xslt";
        xsltDocBookToDvpMLPath = "D:\\Dvp\\QtDoc\\QtDocTools\\export\\to_dvpml\\xslt\\docbook_to_dvpml.xslt";
        docBookRNGPath = "D:\\Dvp\\QtDoc\\QtDocTools\\import\\from_qdoc_v2\\schema\\docbook52qdt\\custom.rnc";

        sourceFolder = Paths.get("C:\\Qt\\5.11.1\\Src");
        outputFolder = Paths.get("C:\\Qt\\Doc");

        generateFileMappings();
    }

    private void generateFileMappings() {
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

    public List<Pair<String, Path>> findModules() {
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
        List<Pair<String, Path>> modules = new ArrayList<>(directories.length);
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
                    modules.add(new Pair<>(directory, qdocconfPath));
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
            for (Map.Entry<String, Pair<Path, String>> entry : qtBaseTools.entrySet()) {
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

        // Rewrite a more suitable qdocconf file.
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

    public void makeMainQdocconf(List<Pair<String, Path>> modules, Path mainQdocconfPath) throws WriteQdocconfException {
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
    }

    public void runQdoc(Path mainQdocconfPath) throws IOException, InterruptedException {
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

        Map<String, String> env = pb.environment();
        env.put("QT_INSTALL_DOCS", sourceFolder.resolve("qtbase").resolve("doc").toString());
        env.put("BUILDDIR", sourceFolder.resolve("qtbase").resolve("doc").toString());
        env.put("QT_VERSION_TAG", "100");
        env.put("QT_VER", "1.0");
        env.put("QT_VERSION", "1.0.0");

        pb.inheritIO();

        pb.start().waitFor();
    }

    private List<Path> findWithExtension(String extension, String typeName) throws IOException {
        String[] fileNames = outputFolder.resolve("webxml").toFile().list((current, name) -> name.endsWith(extension));
        if (fileNames == null || fileNames.length == 0) {
            throw new IOException("No " + typeName + " files found!");
        }

        return Arrays.stream(fileNames).map(s -> outputFolder.resolve("webxml").resolve(s)).collect(Collectors.toList());
    }

    public List<Path> findWebXML() throws IOException {
        return findWithExtension(".webxml", "WebXML");
    }

    private void initialiseSaxon() throws SaxonApiException {
        saxonProcessor = new Processor(false);
        saxonCompiler = saxonProcessor.newXsltCompiler();
    }

    private StandardErrorListener createLogger(ByteArrayOutputStream os) {
        PrintStream ps = new PrintStream(os, true, StandardCharsets.UTF_8);
        StandardErrorListener sel = new StandardErrorListener();
        sel.setLogger(new StandardLogger(ps));
        return sel;
    }

    private XsltTransformer createTransformer(File file, Path destination, ByteArrayOutputStream os, String format) throws SaxonApiException {
        XdmNode source = saxonProcessor.newDocumentBuilder().build(new StreamSource(file));
        Serializer out = saxonProcessor.newSerializer();
        out.setOutputFile(destination.toFile());
        saxonCompiler.setErrorListener(createLogger(os));

        XsltTransformer trans = (format.equals("WebXML") ? saxonExecutableWebXMLToDocBook : saxonExecutableDocBookToDvpML).load();
        trans.setInitialContextNode(source);
        trans.setDestination(out);
        return trans;
    }

    public void runXSLTWebXMLToDocBook(File file, Path destination) throws SaxonApiException {
        if (saxonProcessor == null) {
            initialiseSaxon();
        }
        if (saxonExecutableWebXMLToDocBook == null) {
            saxonExecutableWebXMLToDocBook = saxonCompiler.compile(new StreamSource(new File(xsltWebXMLToDocBookPath)));
        }

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        XsltTransformer trans = createTransformer(file, destination, os, "WebXML");
        trans.setParameter(new QName("qt-version"), new XdmAtomicValue("5.11"));
        trans.setParameter(new QName("local-folder"), new XdmAtomicValue(file.getParentFile().toPath().toUri()));
        trans.transform();

        String errors = new String(os.toByteArray(), StandardCharsets.UTF_8);
        if (errors.length() > 0) {
            System.out.println(errors);
        }
    }

    private boolean validateDocBook(File file) throws SAXException, IOException {
        // Required according to https://pages.lip6.fr/Jean-Francois.Perrot/XML-Int/Session3/RNG/.
        System.setProperty(
                SchemaFactory.class.getName() + ":" + XMLConstants.RELAXNG_NS_URI,
                "com.thaiopensource.relaxng.jaxp.CompactSyntaxSchemaFactory"
        );
        SchemaFactory factory = CompactSyntaxSchemaFactory.newInstance(XMLConstants.RELAXNG_NS_URI);
        Schema schema = factory.newSchema(new StreamSource(new File(docBookRNGPath)));

        Validator validator = schema.newValidator();
        try {
            validator.validate(new StreamSource(file)); // SAXException when validation error.
            return true;
        } catch (SAXException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<Path> findDocBook() throws IOException {
        return findWithExtension(".qdt", "DocBook");
    }

    public void runXSLTDocBookToDvpML(File file, Path destination) throws SaxonApiException {
        if (saxonProcessor == null) {
            initialiseSaxon();
        }
        if (saxonExecutableDocBookToDvpML == null) {
            saxonExecutableDocBookToDvpML = saxonCompiler.compile(new StreamSource(new File(xsltDocBookToDvpMLPath)));
        }

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        XsltTransformer trans = createTransformer(file, destination, os, "DvpML");
        trans.transform();

        String errors = new String(os.toByteArray(), StandardCharsets.UTF_8);
        if (errors.length() > 0) {
            System.out.println(errors);
        }
    }
}
