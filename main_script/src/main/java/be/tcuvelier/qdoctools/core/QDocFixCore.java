package be.tcuvelier.qdoctools.core;

import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.handlers.QDocFixHandler;
import net.sf.saxon.s9api.SaxonApiException;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;

public class QDocFixCore {
    // Perform the fixing part of the conversion cycle to get rid of QDoc quirks.
    public static void call(String outputFolder, GlobalConfiguration config)
            throws SaxonApiException, IOException, InterruptedException,
            ParserConfigurationException, SAXException {
        System.out.println("++> Fixing some qdoc quirks.");
        QDocFixHandler qfh = new QDocFixHandler(outputFolder, config, true);
//        qfh.fixQDocBugs();
//        qfh.addDates();
//        qfh.addAuthors();
        qfh.fixLinks();
        qfh.removeBackupsIfNeeded();
        System.out.println("++> QDoc quirks fixed."); // At least, the ones I know about right now.
    }
}
