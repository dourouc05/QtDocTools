package be.tcuvelier.qdoctools.core.handlers;

import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.config.QdtPaths;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.File;
import java.io.IOException;

public class ValidationHandler {
    // Low-level API.
    private static boolean validate(File file, Schema schema) throws IOException {
        Validator validator = schema.newValidator();
        try {
            // First, check if the document is not empty.
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file);
            if (doc.getDocumentElement().getChildNodes().item(0) == null) {
                System.err.println("Document is empty!");
                return false;
            }

            // If there is real content, do a proper validation.
            validator.validate(new StreamSource(file)); // SAXException when validation error.
            return true;
        } catch (ParserConfigurationException | SAXException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void loadJing() {
        // Required according to https://pages.lip6.fr/Jean-Francois.Perrot/XML-Int/Session3/RNG/.
        System.setProperty(
                SchemaFactory.class.getName() + ":" + XMLConstants.RELAXNG_NS_URI,
                "com.thaiopensource.relaxng.jaxp.CompactSyntaxSchemaFactory"
        );
    }

    private static Schema loadSchema(String schema, String format) throws SAXException {
        SchemaFactory factory = SchemaFactory.newInstance(format);
        return factory.newSchema(new StreamSource(new File(schema)));
    }

    public static Schema loadRNGSchema(String rng) throws SAXException {
        try {
            return loadSchema(rng, XMLConstants.RELAXNG_NS_URI);
        } catch (IllegalArgumentException e) {
            // Try a second time, forcing to load Jing. In the case it is not possible to load a
            // schema of the asked format, an IllegalArgumentException is thrown:
            // No SchemaFactory that implements the schema language specified by: http://relaxng.org/ns/structure/1.0
            // could be loaded
            loadJing();
            return loadSchema(rng, XMLConstants.RELAXNG_NS_URI);
        }
    }

    public static Schema loadXSDSchema(String xsd) throws SAXException {
        return loadSchema(xsd, XMLConstants.W3C_XML_SCHEMA_NS_URI);
    }

    // Medium-level API: validate a file against a schema.
    private static boolean validateRNG(File file, String rng) throws SAXException, IOException {
        return validate(file, loadRNGSchema(rng));
    }

    private static boolean validateXSD(File file, String xsd) throws SAXException, IOException {
        return validate(file, loadXSDSchema(xsd));
    }

    // Higher-level API: validate a file against a configuration-defined schema.
    // Only this API is visible from outside the class.
    public static boolean validateDvpML(File file, GlobalConfiguration config) throws IOException, SAXException {
        return ValidationHandler.validateXSD(file,
                new QdtPaths(config).getDvpMLXSDPath());
    }

    public static boolean validateDocBook(File file, GlobalConfiguration config) throws IOException, SAXException {
        return ValidationHandler.validateRNG(file, new QdtPaths(config).getDocBookRNGPath());
    }
}
