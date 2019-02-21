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
    private static boolean subValidate(File file, Validator validator) throws IOException {
        try {
            validator.validate(new StreamSource(file)); // SAXException when validation error.
            return true;
        } catch (SAXException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean validateRNG(File file, String rng) throws SAXException, IOException {
        // Required according to https://pages.lip6.fr/Jean-Francois.Perrot/XML-Int/Session3/RNG/.
        System.setProperty(
                SchemaFactory.class.getName() + ":" + XMLConstants.RELAXNG_NS_URI,
                "com.thaiopensource.relaxng.jaxp.CompactSyntaxSchemaFactory"
        );
        SchemaFactory factory = CompactSyntaxSchemaFactory.newInstance(XMLConstants.RELAXNG_NS_URI);
        Schema schema = factory.newSchema(new StreamSource(new File(rng)));
        Validator validator = schema.newValidator();
        return subValidate(file, validator);
    }

    public static boolean validateXSD(File file, String xsd) throws SAXException, IOException {
        SchemaFactory factory = SchemaFactory.newInstance("http://www.w3.org/2001/XMLSchema");
        Schema schema = factory.newSchema(new StreamSource(new File(xsd)));

        Validator validator = schema.newValidator();
        return subValidate(file, validator);
    }
}
