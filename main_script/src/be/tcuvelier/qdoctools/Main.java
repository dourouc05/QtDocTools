package be.tcuvelier.qdoctools;

import be.tcuvelier.qdoctools.helpers.FileHelpers;
import be.tcuvelier.qdoctools.helpers.ValidationHelper;
import be.tcuvelier.qdoctools.utils.*;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XsltTransformer;
import org.xml.sax.SAXException;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Goals of this package?
 *  - Transform documents, either one by one OR by batch (for Qt's documentation only)
 *      - DvpML <> DocBook (configuration: a JSON along the document)
 *    File types are guessed from extensions (.xml for DvpML, .db, .dbk, or .qdt for DocBook, .webxml for WebXML).
 *    Single-shot transformations from WebXML are not available outside qdoc mode, due to the requirements of the
 *    transformation (the utilities sheet must be run before).
 *  - Run qdoc and the associated transformations (for Qt's documentation only)
 *      - Only qdoc to WebXML
 *      - From qdoc to DocBook
 *  - Later on, more documentation-oriented things (like seeing what has changed between two versions and applying
 *    the same changes to a translated copy).
 *
 *  All options to find qdoc and related tools are contained in a configuration file.
 */

@SuppressWarnings("WeakerAccess")
public class Main implements Callable<Void> {
    public enum Mode { qdoc, normal }
    @Option(names = { "-m", "--mode" },
            description = "Working modes: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE})")
    private Mode mode = Mode.normal;

    @Option(names = { "-i", "--input-file", "--input-folder" },
            description = "File (normal mode) or folder (qdoc mode) to process", required = true)
    private String input;

    @Option(names = { "-o", "--output-file", "--output-folder" },
            description = "Output file (normal mode) or folder (qdoc mode)", required = true)
    private String output;

    @Option(names = { "-v", "--validate" },
            description = "Whether the output shall be validated against a known XSD or RNG")
    private boolean validate = true;

    @Option(names = "--no-rewrite-qdocconf", description = "Disables the rewriting of the .qdocconf files " +
            "(the new ones have already been generated)")
    private boolean rewriteQdocconf = true;

    @Option(names = "--no-convert-webxml", description = "Disables the generation of the WebXML files. " +
            "This operation is time-consuming, as it relies on qdoc, and requires the prior generation of the qdocconf files")
    private boolean convertToWebXML = true;

    @Option(names = "--no-convert-docbook", description = "Disables the generation of the DocBook files. " +
            "This operation requires the prior generation of the WebXML files")
    private boolean convertToDocBook = true;

    @Option(names = "--no-convert-dvpml", description = "Disables the generation of the DvpML files. " +
            "This operation requires the prior generation of the DocBook files")
    private boolean convertToDvpML = true;

    @Option(names = "--no-consistency-checks", description = "Disables advanced consistency checks. " +
            "They require an Internet connection")
    private boolean consistencyChecks = true;

    @Option(names = { "-c", "--configuration-file" },
            description = "Configuration file, mostly useful in qdoc mode (default: ${DEFAULT-VALUE})")
    private String configurationFile = "config.json";

    @Option(names = "--qt-version",
            description = "Version of Qt that is being processed; only useful in qdoc mode")
    private String qtVersion = "1.0";

    public final static String xsltWebXMLToDocBookPath = "../import/from_qdoc_v2/xslt/webxml_to_docbook.xslt"; // Path to the XSLT sheet WebXML to DocBook.
    public final static String xsltWebXMLToDocBookUtilPath = "../import/from_qdoc_v2/xslt/class_parser.xslt"; // Path to the XSLT sheet that contains utilities for the WebXML to DocBook transformation.
    public final static String xsltDvpMLToDocBookPath = "../import/from_dvpml/xslt/dvpml_to_docbook.xslt"; // Path to the XSLT sheet DvpML to DocBook.
    public final static String xsltDocBookToDvpMLPath = "../export/to_dvpml/xslt/docbook_to_dvpml.xslt"; // Path to the XSLT sheet DocBook to DvpML.
    public final static String docBookRNGPath = "../import/from_qdoc_v2/schema/docbook52qdt/custom.rnc";
    public final static String dvpMLXSDPath = "../export/to_dvpml/schema/article.xsd";

    public static void main(String[] args) {
        String[] argv = {"-i", "/", "-o", "/"};
        CommandLine.call(new Main(), argv);
    }

    @Override
    public Void call() throws SaxonApiException, IOException, SAXException, InterruptedException {
        switch (mode) {
            case normal:
                callNormal();
                return null;
            case qdoc:
                callQdoc();
                return null;
            default:
                // This is strictly impossible.
                return null;
        }
    }

    private String prefix(int i, List list) {
        String iFormat = "%0" + Integer.toString(list.size()).length() + "d";
        return "[" + String.format(iFormat, i + 1) + "/" + list.size() + "]";
    }

    private void callQdoc() throws SaxonApiException, IOException, InterruptedException {
        // Perform the conversion cycle, as complete as required.

        // First, initialise global objects.
        Configuration config;
        try {
            config = new Configuration(configurationFile);
        } catch (FileNotFoundException e) {
            System.out.println("!!> Configuration file not found! " + configurationFile);
            return;
        }

        QdocHandler q = new QdocHandler(input, output, config.getQdocLocation());
        q.ensureOutputFolderExists();

        // Explore the source directory for the qdocconf files.
        List<Pair<String, Path>> modules = q.findModules();
        System.out.println("++> " + modules.size() + " modules found");

        // Rewrite the needed qdocconf files (one per module, may be multiple times per folder).
        if (rewriteQdocconf) {
            for (Pair<String, Path> module : modules) {
                q.rewriteQdocconf(module.first, module.second);
                System.out.println("++> Module qdocconf rewritten: " + module.first);
            }

            Path mainQdocconfPath = q.makeMainQdocconf(modules);
            System.out.println("++> Main qdocconf rewritten: " + mainQdocconfPath);
        }

        // Run qdoc to get the WebXML output.
        if (convertToWebXML) {
            System.out.println("++> Running qdoc.");
            q.runQdoc();
            System.out.println("++> Qdoc done.");
        }

        // Run Saxon to get the DocBook output.
        if (convertToDocBook) {
            Path root = q.getOutputFolder();

            // First, generate the list of classes (may take a bit of time).
            XsltTransformer utilities = new XsltHandler(xsltWebXMLToDocBookUtilPath)
                    .createTransformer(root.resolve("qdt_classes.xml"), "main");
            utilities.setParameter(new QName("local-folder"), new XdmAtomicValue(q.getOutputFolder().toUri()));
            utilities.transform();

            // Second, iterate through the files.
            List<Path> webxml = q.findWebXML();
            XsltHandler h = new XsltHandler(xsltWebXMLToDocBookPath);

            if (webxml.size() == 0) {
                System.out.println("??> Has qdoc been launched before in " + q.getOutputFolder() + "? There are " +
                        "no WebXML files there.");
            }

            int i = 0;
            for (Path file : webxml) {
//                        if (! file.getFileName().toString().startsWith("q"))
//                            continue;
//                        if (! file.getFileName().toString().endsWith("qxmlnodemodelindex.webxml"))
//                            continue;

                // Output the result in the same folder as before, with the same file name, just replace
                // the extension (.webxml becomes .qdt).
                Path destination = root.resolve(FileHelpers.changeExtension(file, ".qdt"));

                // Print the name of the file to process to ease debugging.
                System.out.println(prefix(i, webxml) + " " + file.toString());

                // Actually convert the WebXML into DocBook. This may print errors directly to stderr.
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                XsltTransformer trans = h.createTransformer(file, destination, os);
                trans.setParameter(new QName("qt-version"), new XdmAtomicValue(qtVersion));
                trans.setParameter(new QName("local-folder"), new XdmAtomicValue(q.getOutputFolder().toUri()));
                trans.transform();

                String errors = new String(os.toByteArray(), StandardCharsets.UTF_8);
                if (errors.length() > 0) {
                    System.out.println(errors);
                }

                // Handle validation.
                if (validate) {
                    try {
                        boolean isValid = ValidationHelper.validateDocBook(destination);
                        if (! isValid) {
                            System.err.println(prefix(i, webxml) + " There were validation errors. See the above exception for details.");
                        }
                    } catch (SAXException e) {
                        System.out.println(prefix(i, webxml) + " Validation error!");
                        e.printStackTrace();
                    }
                }

                // Perform advanced consistency checks (requires Internet connectivity).
                if (consistencyChecks) {
                    QdocConsistencyChecks qc = new QdocConsistencyChecks(destination, prefix(i, webxml));
                    boolean result = qc.checkInheritedBy();
                    result &= qc.checkItems();

                    if (! result) {
                        System.out.println(prefix(i, webxml) + " Check error!");
                    }
                }

                // Go to the next file.
                ++i;
            }
        }

        // Run Saxon to get the DvpML output.
        if (convertToDvpML) {
            Path root = q.getOutputFolder();

            // Iterate through all the files.
            List<Path> qdt = q.findDocBook();
            XsltHandler h = new XsltHandler(xsltDocBookToDvpMLPath);

            if (qdt.size() == 0) {
                System.out.println("??> Have WebXML files been converted into DocBook before in " +
                        q.getOutputFolder() + "? There are no DocBook files there.");
            }

            int i = 0;
            for (Path file : qdt) {
                // Output the result in the same folder as before, with the same file name, just replace
                // the extension (.qdt becomes .xml).
                Path destination = root.resolve(FileHelpers.changeExtension(file, ".xml"));

                // Print the name of the file to process to ease debugging.
                System.out.println(prefix(i, qdt) + " " + file.toString());

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
                            System.err.println(prefix(i, qdt) + "There were validation errors. See the above exception for details.");
                        }
                    } catch (SAXException e) {
                        System.out.println(prefix(i, qdt) + " Validation error!");
                        e.printStackTrace();
                    }
                }

                // Go to the next file.
                ++i;
            }
        }
    }

    private void callNormal() throws SaxonApiException, IOException, SAXException {
        // Just one conversion to perform.

        // Create a Saxon object based on the sheet to use.
        XsltHandler h;
        if (FileHelpers.isDvpML(input) && FileHelpers.isDocBook(output)) {
            h = new XsltHandler(xsltDvpMLToDocBookPath);
        } else if (FileHelpers.isDocBook(input) && FileHelpers.isDvpML(output)) {
            h = new XsltHandler(xsltDocBookToDvpMLPath);
        } else {
            System.err.println("The input-output pair was not recognised! This mode only allows DvpML <> DocBook.");
            return;
        }

        // Run the transformation (including some variables that are required for qdoc).
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        h.createTransformer(input, output, os).transform();

        // If there were errors, print them out.
        String errors = new String(os.toByteArray(), StandardCharsets.UTF_8);
        if (errors.length() > 0) {
            System.err.println(errors);
        }

        // If required, validate the document.
        if (validate) {
            boolean isValid;
            if (FileHelpers.isDocBook(output)) {
                isValid = ValidationHelper.validateDocBook(output);
            } else if (FileHelpers.isDvpML(output)) {
                isValid = ValidationHelper.validateDvpML(output);
            } else {
                System.err.println("The output format has no validation step defined!");
                isValid = true;
            }

            if (!isValid) {
                System.err.println("There were validation errors. See the above exception for details.");
            }
        }
    }
}
