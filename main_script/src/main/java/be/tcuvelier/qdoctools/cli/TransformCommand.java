package be.tcuvelier.qdoctools.cli;

/*
 * TODO: How to encode/retrieve block <screen> vs. <programlisting>? screen/prompt?
 * TODO: How to encode/retrieve inline <filename>? filename/replaceable?
 */

import be.tcuvelier.qdoctools.io.DocxInput;
import be.tcuvelier.qdoctools.io.DocxOutput;
import be.tcuvelier.qdoctools.utils.handlers.SanityCheckHandler;
import be.tcuvelier.qdoctools.utils.helpers.FileHelpers;
import be.tcuvelier.qdoctools.utils.helpers.ValidationHelper;
import be.tcuvelier.qdoctools.utils.handlers.XsltHandler;
import net.sf.saxon.s9api.SaxonApiException;
import org.xml.sax.SAXException;
import picocli.CommandLine.Option;
import picocli.CommandLine.Command;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

@Command(name = "transform", description = "Perform transformations to and from DocBook for publishing")
public class TransformCommand implements Callable<Void> {
    public enum Format {
        DocBook, DOCX, DvpML, ODT
    }

    @Option(names = { "-i", "--input-file", "--input-folder" },
            description = "File or folder to process", required = true)
    private String input;

    @Option(names = { "-if", "--input-format" },
            description = "Format of the input to transform. Default value: detected from extension. " +
                    "Valid values: ${COMPLETION-CANDIDATES}")
    private Format inputFormat; // TODO: use me.

    @Option(names = { "-o", "--output-file", "--output-folder" },
            description = "Output file or folder", required = true)
    private String output;

    @Option(names = { "-of", "--output-format" },
            description = "Format of the output to generate. Default value: DOCX if the input is DocBook, " +
                    "DocBook otherwise. Valid values: ${COMPLETION-CANDIDATES}")
    private Format outputFormat; // TODO: use me.

    @Option(names = { "-c", "--configuration-file" },
            description = "Configuration file (default: ${DEFAULT-VALUE})")
    private String configurationFile = "config.json";

    @Option(names = "--no-validation",
            description = "Disables the validation of the output against a known XSD or RNG")
    private boolean validate = true;

    @Option(names = { "--disable-sanity-checks" },
            description = "Perform the sanity checks, but continue with generation even in case of failure")
    private boolean disableSanityChecks = false;

    @Override
    public Void call() throws SaxonApiException, IOException, SAXException {
        // TODO: old.

        // Just one conversion to perform.

        // Create a Saxon object based on the sheet to use.
        XsltHandler h;
        if (FileHelpers.isDvpML(input) && FileHelpers.isDocBook(output)) {
            h = new XsltHandler(MainCommand.xsltDvpMLToDocBookPath);
        } else if (FileHelpers.isDocBook(input) && FileHelpers.isDvpML(output)) {
            h = new XsltHandler(MainCommand.xsltDocBookToDvpMLPath);
        } else {
            System.err.println("The input-output pair was not recognised! This mode only allows DvpML <> DocBook.");
            return null;
        }

        // Run the transformation.
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

        return null;

        // TODO: ex-Proofread.
        if (! new File(input).exists()) {
            throw new RuntimeException("Input file " + input + " does not exist!");
        }

        if (FileHelpers.isDOCX(input)) {
            String output = FileHelpers.changeExtension(input, ".xml");
            fromDOCXToDocBook(input, output);
        } else if (FileHelpers.isODT(input)) {
            System.out.println("NOT YET IMPLEMENTED");
        } else if (FileHelpers.isDocBook(input)) {
            if (! checkSanity(input)) {
                System.out.println("SANITY CHECK: one or more sanity checks did not pass. It is better if you " +
                        "correct these problems now; otherwise, you may use the option --disable-sanity-checks to " +
                        "ignore them. In the latter case, you may face errors while producing the DOCX file or, " +
                        "more likely, you will not get a comparable DocBook file when round-tripping.");
                throw new RuntimeException("Input DocBook file does not pass the sanity checks! ");
            }

            if (outputFormat == UploadCommand.OutputFormat.DOCX) {
                String output = FileHelpers.changeExtension(input, ".docx");
                fromDocBookToDOCX(input, output);
            } else if (outputFormat == UploadCommand.OutputFormat.ODT) {
                System.out.println("ODT NOT YET IMPLEMENTED");
            }
        } else {
            throw new RuntimeException("File format not recognised for input file!");
        }

        return null;
    }

    private boolean checkSanity(String input) throws SaxonApiException, FileNotFoundException {
        if (! disableSanityChecks) {
            return new SanityCheckHandler(input).performSanityCheck();
        } else {
            return true;
        }
    }

    private static void fromDOCXToDocBook(String input, String output) throws Exception {
        new DocxInput(input).toDocBook(output);
    }

    private static void fromDocBookToDOCX(String input, String output) throws Exception {
        new DocxOutput(input).toDocx(output);
    }
}
