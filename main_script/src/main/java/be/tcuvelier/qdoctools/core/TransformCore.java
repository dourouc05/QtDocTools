package be.tcuvelier.qdoctools.core;

import be.tcuvelier.qdoctools.core.config.Configuration;
import be.tcuvelier.qdoctools.core.handlers.DocBookSanityCheckHandler;
import be.tcuvelier.qdoctools.core.helpers.FileHelpers;
import be.tcuvelier.qdoctools.core.helpers.TransformHelpers;
import be.tcuvelier.qdoctools.core.helpers.ValidationHelper;
import net.sf.saxon.s9api.SaxonApiException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class TransformCore {
    public enum Format {
        // All pairs with DocBook are supported.
        Default, DocBook, DOCX, DvpML, ODT
    }

    public static String sanityCheckFailedMessage = "SANITY CHECK: one or more sanity checks did not pass. It is " +
            "better if you correct these problems now; otherwise, you may use the option --disable-sanity-checks to " +
            "ignore them. In the latter case, you may face errors while producing the DOCX file or, " +
            "more likely, you will not get a comparable DocBook file when round-tripping.";

    public static String validationFailedMessage = "There were validation errors. See the above exception for details.";

    private static String generateOutputFilename(String input, Format outputFormat) {
        // Specific handling for collisions between DocBook and DvpML: add a suffix (just before the extension).
        if (input.endsWith("_dvp.xml")) {
            input = input.replace("_dvp.xml", ".xml");
        } else if (input.endsWith("_db.xml")) {
            input = input.replace("_db.xml", ".xml");
        }

        // Change the extension based on the target format.
        if (outputFormat == Format.DOCX) {
            return FileHelpers.changeExtension(input, ".docx");
        } else if (outputFormat == Format.ODT) {
            return FileHelpers.changeExtension(input, ".odt");
        } else if (outputFormat == Format.DvpML) {
            String output = FileHelpers.changeExtension(input, ".xml");

            if (input.equals(output)) {
                output = output.substring(0, output.length() - 4) + "_dvp.xml";
            }

            return output;
        } else if (outputFormat == Format.DocBook) {
            String output = FileHelpers.changeExtension(input, ".xml");

            if (input.equals(output)) {
                output = output.substring(0, output.length() - 4) + "_db.xml";
            }

            return output;
        }

        // Format not found. This is mostly a Java requirement...
        throw new IllegalArgumentException("Format not recognised when generating a new file name.");
    }

    public static void call(String input, Format inputFormat,
                            String output, Format outputFormat,
                            Configuration config, boolean validate, boolean disableSanityChecks)
            throws SaxonApiException, IOException, SAXException, ParserConfigurationException, InvalidFormatException,
            XMLStreamException {
        if (! new File(input).exists()) {
            throw new IOException("Input file " + input + " does not exist!");
        }

        // Detect the formats.
        if (inputFormat == Format.Default) {
            if (FileHelpers.isDocBook(input)) {
                inputFormat = Format.DocBook;
            } else if (FileHelpers.isDOCX(input)) {
                inputFormat = Format.DOCX;
            } else if (FileHelpers.isDvpML(input)) {
                inputFormat = Format.DvpML;
            } else if (FileHelpers.isODT(input)) {
                inputFormat = Format.ODT;
            } else {
                throw new RuntimeException("File format not recognised for input file!");
            }
        }

        if (outputFormat == Format.Default) {
            if (inputFormat == Format.DocBook) {
                outputFormat = Format.DOCX;
            } else {
                outputFormat = Format.DocBook;
            }
        }

        assert inputFormat != outputFormat;

        // Generate an output file name if necessary.
        if (output == null || output.isBlank()) {
            output = generateOutputFilename(input, outputFormat);
        }

        // Dispatch to the helper methods.
        switch (inputFormat) {
            case DocBook:
                // Perform the sanity checks in all cases, don't interrupt only if allowed not to.
                if (!checkSanity(input) && !disableSanityChecks) {
                    throw new IOException("Input DocBook file does not pass the sanity checks!",
                            new RuntimeException(sanityCheckFailedMessage));
                }

                if (outputFormat == Format.DOCX) {
                    TransformHelpers.fromDocBookToDOCX(input, output, config);
                } else if (outputFormat == Format.ODT) {
                    TransformHelpers.fromDocBookToODT(input, output);
                } else if (outputFormat == Format.DvpML) {
                    TransformHelpers.fromDocBookToDvpML(input, output, config);
                }
                break;
            case DOCX:
                assert outputFormat == Format.DocBook;

                TransformHelpers.fromDOCXToDocBook(input, output);
                break;
            case ODT:
                assert outputFormat == Format.DocBook;

                TransformHelpers.fromODTToDocBook(input, output);
                break;
            case DvpML:
                assert outputFormat == Format.DocBook;

                TransformHelpers.fromDvpMLToDocBook(input, output, config);
                break;
        }

        // If required, validate the document.
        if (validate) {
            boolean isValid;
            if (outputFormat == Format.DocBook) {
                isValid = ValidationHelper.validateDocBook(output, config);
            } else if (outputFormat == Format.DvpML) {
                isValid = ValidationHelper.validateDvpML(output, config);
            } else {
                // No easy check to perform. Could use OpenXML SDK for DOCX, but that's C#.
                isValid = true;
            }

            if (!isValid) {
                System.err.println(validationFailedMessage);
            }
        }
    }

    public static boolean checkSanity(String input) throws SaxonApiException, FileNotFoundException {
        return new DocBookSanityCheckHandler(input).performSanityCheck();
    }
}
