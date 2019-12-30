package be.tcuvelier.qdoctools.cli;

import be.tcuvelier.qdoctools.utils.Configuration;
import be.tcuvelier.qdoctools.utils.Pair;
import be.tcuvelier.qdoctools.utils.QtVersion;
import be.tcuvelier.qdoctools.utils.handlers.QdocHandler;
import be.tcuvelier.qdoctools.utils.handlers.XsltHandler;
import be.tcuvelier.qdoctools.core.helpers.FileHelpers;
import be.tcuvelier.qdoctools.utils.helpers.ValidationHelper;
import net.sf.saxon.s9api.SaxonApiException;
import org.xml.sax.SAXException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "qdoc", description = "Run qdoc and the associated transformations")
public class QdocCommand implements Callable<Void> {
    @Option(names = { "-i", "--source-folder" },
            description = "Folder to process (source code of Qt)", required = true)
    private String source;

    @Option(names = { "-s", "--installed-folder" },
            description = "Folder with a complete Qt installation " +
                    "(either precompiled or built from scratch and installed)", required = true)
    private String installed;

    @Option(names = { "-o", "--output-folder" },
            description = "Output folder", required = true)
    private String output;

    @Option(names = { "-c", "--configuration-file" },
            description = "Configuration file, mostly useful in qdoc mode (default: ${DEFAULT-VALUE})")
    private String configurationFile = "config.json";

    @Option(names = "--qt-version",
            description = "Version of Qt that is being processed")
    private QtVersion qtVersion = new QtVersion("1.0");

    @Option(names = "--qdoc-debug",
            description = "Run qdoc in debug mode")
    private boolean qdocDebug = false;

    @Option(names = "--no-validation",
            description = "Disables the validation of the output against a known XSD or RNG")
    private boolean validate = true;

    @Option(names = "--no-convert-docbook", description = "Disables the generation of the DocBook files " +
            "(i.e. do not run qdoc)")
    private boolean convertToDocBook = true;

    @Option(names = "--no-convert-dvpml", description = "Disables the generation of the DvpML files. " +
            "This operation requires the prior generation of the DocBook files")
    private boolean convertToDvpML = true;

    @Option(names = "--no-consistency-checks", description = "Disables advanced consistency checks. " +
            "They require an Internet connection")
    private boolean consistencyChecks = true;

    @Override
    public Void call() throws SaxonApiException, IOException, InterruptedException, ParserConfigurationException, SAXException {
        // Perform the conversion cycle, as complete as required.

        // First, initialise global objects.
        Configuration config;
        try {
            config = new Configuration(configurationFile);
        } catch (FileNotFoundException e) {
            System.out.println("!!> Configuration file not found! " + configurationFile);
            return null;
        }

        List<String> includes = config.getCppCompilerIncludes();
        includes.addAll(config.getNdkIncludes());
        QdocHandler q = new QdocHandler(source, installed, output, config.getQdocLocation(), qtVersion, qdocDebug, includes);
        q.ensureOutputFolderExists();

        // Explore the source directory for the qdocconf files.
        List<Pair<String, Path>> modules = q.findModules();
        System.out.println("++> " + modules.size() + " modules found");

        // Run qdoc to get the DocBook output.
        if (convertToDocBook) {
            // Rewrite the list of qdocconf files (one per module, may be multiple times per folder).
            Path mainQdocconfPath = q.makeMainQdocconf(modules);
            System.out.println("++> Main qdocconf rewritten: " + mainQdocconfPath);

            // Actually run qdoc on this new file.
            System.out.println("++> Running qdoc.");
            q.runQdoc();
            System.out.println("++> Qdoc done.");

            // Sometimes, qdoc outputs things in a strange folder. Ahoy!
            q.moveGeneratedFiles();

            System.out.println("++> Checking whether all indexed files are present.");
            q.checkUngeneratedFiles();
            System.out.println("++> Checked!");

            // TODO: validate the files.
        }

        // Run Saxon to get the DocBook output.
//        if (convertToDocBook) {
//            Path root = q.getOutputFolder();
//
//            // First, generate the list of classes (may take a bit of time).
//            System.out.println("++> Generating utilities for WebXML-to-DocBook transformation.");
//            XsltTransformer utilities = new XsltHandler(MainCommand.xsltWebXMLToDocBookUtilPath)
//                    .createTransformer(root.resolve("qdt_classes.xml"), "main");
//            utilities.setParameter(new QName("local-folder"), new XdmAtomicValue(q.getOutputFolder().toUri()));
//            utilities.transform();
//
//            // Second, iterate through the files.
//            System.out.println("++> Starting WebXML-to-DocBook transformation.");
//            List<Path> webxml = q.findWebXML();
//            XsltHandler h = new XsltHandler(MainCommand.xsltWebXMLToDocBookPath);
//
//            if (webxml.size() == 0) {
//                System.out.println("??> Has qdoc been launched before in " + q.getOutputFolder() + "? There are " +
//                        "no WebXML files there.");
//            }
//
//            int i = 0;
//            for (Path file : webxml) {
////                if (file.getFileName().toString().charAt(0) < 'r')
////                    continue;
////                if (! file.getFileName().toString().startsWith("q"))
////                    continue;
////                if (! file.getFileName().toString().endsWith("qxmlnodemodelindex.webxml"))
////                    continue;
//
//                // Output the result in the same folder as before, with the same file name, just replace
//                // the extension (.webxml becomes .qdt).
//                Path destination = root.resolve(FileHelpers.changeExtension(file, ".qdt"));
//
//                // Print the name of the file to process to ease debugging.
//                System.out.println(Helpers.prefix(i, webxml) + " " + file.toString());
//
//                // Actually convert the WebXML into DocBook. This may print errors directly to stderr.
//                ByteArrayOutputStream os = new ByteArrayOutputStream();
//                XsltTransformer trans = h.createTransformer(file, destination, os);
//                trans.setParameter(new QName("qt-version"), new XdmAtomicValue(qtVersion.QT_VER()));
//                trans.setParameter(new QName("local-folder"), new XdmAtomicValue(q.getOutputFolder().toUri()));
//                trans.transform();
//
//                String errors = new String(os.toByteArray(), StandardCharsets.UTF_8);
//                if (errors.length() > 0) {
//                    System.out.println(errors);
//                }
//
//                // Handle validation.
//                if (validate) {
//                    try {
//                        boolean isValid = ValidationHelper.validateDocBook(destination);
//                        if (! isValid) {
//                            System.err.println(Helpers.prefix(i, webxml) + " There were validation errors. See the above exception for details.");
//                        }
//                    } catch (SAXException e) {
//                        System.out.println(Helpers.prefix(i, webxml) + " Validation error!");
//                        e.printStackTrace();
//                    }
//                }
//
//                // Perform advanced consistency checks (requires Internet connectivity).
//                if (consistencyChecks) {
//                    boolean result;
//
//                    try {
//                        QdocConsistencyChecks qc = new QdocConsistencyChecks(destination, Helpers.prefix(i, webxml), qtVersion);
//                        result = qc.checkInheritedBy();
//                        result &= qc.checkItems();
//                    } catch (Exception e) {
//                        result = false;
//                    }
//
//                    if (! result) {
//                        System.out.println(Helpers.prefix(i, webxml) + " Check error!");
//                    }
//                }
//
//                // Go to the next file.
//                ++i;
//            }
//            System.out.println("++> WebXML-to-DocBook transformation done.");
//        }

        // Run Saxon to get the DvpML output.
        if (convertToDvpML) {
            Path root = q.getOutputFolder();

            // Iterate through all the files.
            System.out.println("++> Starting DocBook-to-DvpML transformation.");
            List<Path> xml = q.findDocBook();
            XsltHandler h = new XsltHandler(MainCommand.xsltDocBookToDvpMLPath);

            if (xml.size() == 0) {
                System.out.println("??> Have DocBook files been generated in " +
                        q.getOutputFolder() + "? There are no DocBook files there.");
            }

            int i = 0;
            for (Path file : xml) {
                // Output the result in the same folder as before, with the same file name, just replace
                // the extension (.xml becomes .xml).
                // TODO: problem, qdoc generates XML files, same extension as DvpML docs...
                Path destination = root.resolve(FileHelpers.changeExtension(file, ".xml"));

                // Print the name of the file to process to ease debugging.
                System.out.println(Helpers.prefix(i, xml) + " " + file.toString());

                // Actually convert the DocBook into DvpML. This may print errors directly to stderr.
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                h.createTransformer(file, destination, os).transform();

                String errors = new String(os.toByteArray(), StandardCharsets.UTF_8);
                if (errors.length() > 0) {
                    System.out.println(errors);
                }

                // Handle validation.
                if (validate) {
                    try {
                        boolean isValid = ValidationHelper.validateDvpML(destination);
                        if (! isValid) {
                            System.err.println(Helpers.prefix(i, xml) + "There were validation errors. See the above exception for details.");
                        }
                    } catch (SAXException e) {
                        System.out.println(Helpers.prefix(i, xml) + " Validation error!");
                        e.printStackTrace();
                    }
                }

                // Go to the next file.
                ++i;
            }
            System.out.println("++> DocBook-to-DvpML transformation done.");
        }

        return null;
    }
}
