package be.tcuvelier.qdoctools;

import be.tcuvelier.qdoctools.utils.ValidationHandler;
import be.tcuvelier.qdoctools.utils.XsltHandler;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XsltTransformer;
import org.xml.sax.SAXException;
import picocli.CommandLine;
import picocli.CommandLine.Option;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * Goals of téhis package?
 *  - Transform documents, either one by one OR by batch (for Qt's documentation only)
 *      - DvpML <> DocBook (configuration: a JSON along the document)
 *    File types are guessed from extensions (.xml for DvpML, .db, .dbk, or .qdt for DocBook, .webxml for WebXML).
 *    Single-shot transformations from WebXML are not available outside qdoc mode, due to the requirements of the
 *    transformation (the utilities sheet must be run before).
 *  - Run qdoc and the associated transformations (for Qt's documentation only)
 *      - Only qdoc to WebXML
 *      - From qdoc to DocBook
 *  - Later on, more documentation-oriented things.
 *
 *  All options to find qdoc and related tools are contained in a configuration file.
 */

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


    @Option(names = { "-V", "--consistency-checks" },
            description = "In qdoc mode, run deeper validation tests (consistency checks)")
    private boolean consistencyCheck = false;

    @Option(names = { "-c", "--configuration-file" },
            description = "Configuration file, only useful in qdoc mode (default: ${DEFAULT-VALUE})")
    private String configurationFile = "config.json";

    private final String xsltWebXMLToDocBookPath = "../import/from_qdoc_v2/xslt/webxml_to_docbook.xslt"; // Path to the XSLT sheet WebXML to DocBook.
    private final String xsltWebXMLToDocBookUtilPath = "../import/from_qdoc_v2/xslt/class_parser.xslt"; // Path to the XSLT sheet that contains utilities for the WebXML to DocBook transformation.
    private final String xsltDvpMLToDocBookPath = "../import/from_dvpml/xslt/dvpml_to_docbook.xslt"; // Path to the XSLT sheet DvpML to DocBook.
    private final String xsltDocBookToDvpMLPath = "../export/to_dvpml/xslt/docbook_to_dvpml.xslt"; // Path to the XSLT sheet DocBook to DvpML.
    private final String docBookRNGPath = "../import/from_qdoc_v2/schema/docbook52qdt/custom.rnc";
    private final String dvpMLXSDPath = "../export/to_dvpml/schema/article.xsd";

    public static void main(String[] args) {
        String[] argv = {"-i", "/", "-o", "/"};
        CommandLine.call(new Main(), argv);
    }

    private boolean isDvpML(String path) {
        return path.endsWith(".xml");
    }

    private boolean isDocBook(String path) {
        return path.endsWith(".db") || path.endsWith(".dbk") || path.endsWith(".qdt");
    }

    private boolean isWebXML(String path) {
        return path.endsWith(".webxml");
    }

    @Override
    public Void call() throws SaxonApiException, IOException, SAXException {
        switch (mode) {
            case normal:
                // Just one conversion to perform.

                // Create a Saxon object based on the sheet to use.
                XsltHandler h;
                if (isDvpML(input) && isDocBook(output)) {
                    h = new XsltHandler(xsltDvpMLToDocBookPath);
                } else if (isDocBook(input) && isDvpML(output)) {
                    h = new XsltHandler(xsltDocBookToDvpMLPath);
                } else {
                    System.err.println("The input-output pair was not recognised! This mode only allows DvpML <> DocBook.");
                    return null;
                }

                // Run the transformation (including some variables that are required for qdoc).
                File file = new File(input);
                Path destination = Paths.get(output);
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                XsltTransformer trans = h.createTransformer(file, destination, os);
                trans.setParameter(new QName("qt-version"), new XdmAtomicValue("5.11"));
                trans.setParameter(new QName("local-folder"), new XdmAtomicValue(file.getParentFile().toPath().toUri()));
                trans.transform();

                // If there were errors, print them out.
                String errors = new String(os.toByteArray(), StandardCharsets.UTF_8);
                if (errors.length() > 0) {
                    System.err.println(errors);
                }

                // If required, validate the document.
                if (validate) {
                    boolean isValid;
                    if (isDocBook(output)) {
                        isValid = ValidationHandler.validateRNG(destination.toFile(), docBookRNGPath);
                    } else if (isDvpML(output)) {
                        isValid = ValidationHandler.validateXSD(destination.toFile(), dvpMLXSDPath);
                    } else {
                        System.err.println("The output format has no validation step defined!");
                        isValid = true;
                    }

                    if (! isValid) {
                        System.err.println("There were validation errors. See the above exception for details.");
                    }
                }

                // Done!
                return null;
            case qdoc:
                // Perform the complete conversion cycle.
                return null;
            default:
                // This is strictly impossible.
                return null;
        }
    }
}
