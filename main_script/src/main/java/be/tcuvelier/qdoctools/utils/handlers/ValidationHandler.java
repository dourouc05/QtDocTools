package be.tcuvelier.qdoctools.utils.handlers;

import com.thaiopensource.relaxng.jaxp.CompactSyntaxSchemaFactory;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
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
            validator.validate(new StreamSource(file)); // SAXException when validation error.
            return true;
        } catch (SAXException e) {
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
            return unsafeLoadRNGSchema(rng);
        } catch (SAXException e) {
            // Try a second time, forcing to load Jing.
            loadJing();
            return unsafeLoadRNGSchema(rng);
        }
    }

    private static Schema unsafeLoadRNGSchema(String rng) throws SAXException {
        return loadSchema(rng, XMLConstants.RELAXNG_NS_URI);
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
