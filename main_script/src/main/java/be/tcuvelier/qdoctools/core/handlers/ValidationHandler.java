package be.tcuvelier.qdoctools.core.handlers;

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
    private static boolean subValidate(File file, Schema schema) throws IOException {
        return subValidate(file, schema.newValidator());
    }

    private static boolean subValidate(File file, Validator validator) throws IOException {
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
            // Try a second time, forcing to load Jing. In the case it is not possible to load a schema of the asked
            // format, an IllegalArgumentException is thrown:
            // No SchemaFactory that implements the schema language specified by: http://relaxng.org/ns/structure/1.0 could be loaded
            loadJing();
            return loadSchema(rng, XMLConstants.RELAXNG_NS_URI);
        }
    }

    public static Schema loadXSDSchema(String xsd) throws SAXException {
        return loadSchema(xsd, XMLConstants.W3C_XML_SCHEMA_NS_URI);
    }

    public static boolean validateRNG(File file, String rng) throws SAXException, IOException {
        return subValidate(file, loadRNGSchema(rng));
    }

    public static boolean validateXSD(File file, String xsd) throws SAXException, IOException {
        return subValidate(file, loadXSDSchema(xsd));
    }
}
