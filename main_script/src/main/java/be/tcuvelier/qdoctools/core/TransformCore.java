package be.tcuvelier.qdoctools.core;

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

    public static void call(String input, Format inputFormat,
                            String output, Format outputFormat,
                            /*String configurationFile, */boolean validate, boolean disableSanityChecks)
            throws SaxonApiException, IOException, SAXException, ParserConfigurationException, InvalidFormatException,
            XMLStreamException {
        if (!new File(input).exists()) {
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

        // Dispatch to the helper methods.
        switch (inputFormat) {
            case DocBook:
                // Perform the sanity checks in all cases, don't interrupt only if allowed not to.
                if (!checkSanity(input) && !disableSanityChecks) {
                    throw new IOException("Input DocBook file does not pass the sanity checks!",
                            new RuntimeException(sanityCheckFailedMessage));
                }

                if (outputFormat == Format.DOCX) {
                    if (output == null || output.isBlank()) {
                        output = FileHelpers.changeExtension(input, ".docx");
                    }

                    TransformHelpers.fromDocBookToDOCX(input, output);
                } else if (outputFormat == Format.ODT) {
                    if (output == null || output.isBlank()) {
                        output = FileHelpers.changeExtension(input, ".odt");
                    }

                    TransformHelpers.fromDocBookToODT(input, output);
                } else if (outputFormat == Format.DvpML) {
                    if (output == null || output.isBlank()) {
                        output = FileHelpers.changeExtension(input, ".xml");
                    }

                    TransformHelpers.fromDocBookToDvpML(input, output);
                }
                break;
            case DOCX:
                assert outputFormat == Format.DocBook;

                if (output == null || output.isBlank()) {
                    output = FileHelpers.changeExtension(input, ".xml");
                }

                TransformHelpers.fromDOCXToDocBook(input, output);
                break;
            case ODT:
                assert outputFormat == Format.DocBook;

                if (output == null || output.isBlank()) {
                    output = FileHelpers.changeExtension(input, ".xml");
                }

                TransformHelpers.fromODTToDocBook(input, output);
                break;
            case DvpML:
                assert outputFormat == Format.DocBook;

                if (output == null || output.isBlank()) {
                    output = FileHelpers.changeExtension(input, ".xml");
                }

                TransformHelpers.fromDvpMLToDocBook(input, output);
                break;
        }

        // If required, validate the document.
        if (validate) {
            boolean isValid;
            if (FileHelpers.isDocBook(output)) {
                isValid = ValidationHelper.validateDocBook(output);
            } else if (FileHelpers.isDvpML(output)) {
                isValid = ValidationHelper.validateDvpML(output);
            } else {
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
