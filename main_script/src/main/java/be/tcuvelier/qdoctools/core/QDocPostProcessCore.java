package be.tcuvelier.qdoctools.core;

import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.handlers.QDocMovingHandler;
import net.sf.saxon.s9api.SaxonApiException;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

public class QDocPostProcessCore {
    // Perform the postprocessing part of the conversion cycle.
    public static void call(String outputFolder, GlobalConfiguration config)
            throws SaxonApiException, IOException, InterruptedException,
            ParserConfigurationException, SAXException {
        System.out.println("++> Copying QDoc output.");
        QDocMovingHandler qrh = new QDocMovingHandler(outputFolder);
        qrh.copyGeneratedFiles(); // Sometimes, qdoc outputs things in a strange folder. Ahoy!
        System.out.println("++> QDoc output copied.");

        QDocFixCore.call(outputFolder, config);
    }
}
