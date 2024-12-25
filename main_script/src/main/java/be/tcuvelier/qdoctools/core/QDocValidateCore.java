package be.tcuvelier.qdoctools.core;

import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.handlers.QDocValidateHandler;
import net.sf.saxon.s9api.SaxonApiException;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

public class QDocValidateCore {
    // Performs a formal DocBook validation step
    public static void call(String output, GlobalConfiguration config)
            throws SaxonApiException, IOException, InterruptedException,
            ParserConfigurationException, SAXException {
        System.out.println("++> Validating DocBook output.");
        QDocValidateHandler qvh = new QDocValidateHandler(output, config);
        qvh.validateDocBook();
        System.out.println("++> DocBook output validated.");
    }
}
