package be.tcuvelier.qdoctools.core;

import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.handlers.QDocMovingHandler;
import be.tcuvelier.qdoctools.core.handlers.QDocPostProcessingHandler;
import be.tcuvelier.qdoctools.core.handlers.QDocRunningHandler;
import be.tcuvelier.qdoctools.core.utils.QtVersion;
import net.sf.saxon.s9api.SaxonApiException;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.List;

public class QDocPostProcessCore {
    // Perform the postprocessing part of the conversion cycle.
    public static void call(String outputFolder, String htmlFolder,
                            GlobalConfiguration config)
            throws SaxonApiException, IOException, InterruptedException,
            ParserConfigurationException, SAXException {
        // First, initialise global objects.
        List<String> includes = config.getCppCompilerIncludes();
        includes.addAll(config.getNdkIncludes());

        QDocMovingHandler qrh = new QDocMovingHandler(outputFolder);
        QDocPostProcessingHandler qpph = new QDocPostProcessingHandler(outputFolder, htmlFolder, config);

        System.out.println("++> Copying QDoc output.");
        qrh.copyGeneratedFiles(); // Sometimes, qdoc outputs things in a strange folder. Ahoy!
        System.out.println("++> QDoc output copied.");

        System.out.println("++> Fixing some qdoc quirks.");
        qpph.fixQDocBugs();
        qpph.addDates();
        qpph.addAuthors();
        qpph.fixLinks();
        System.out.println("++> QDoc quirks fixed."); // At least, the ones I know about right now.
    }
}
