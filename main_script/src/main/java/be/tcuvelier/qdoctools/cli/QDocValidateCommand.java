package be.tcuvelier.qdoctools.cli;

import be.tcuvelier.qdoctools.core.QDocValidateCore;
import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import net.sf.saxon.s9api.SaxonApiException;
import org.xml.sax.SAXException;
import picocli.CommandLine;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.concurrent.Callable;

public class QDocValidateCommand implements Callable<Void> {
    @CommandLine.Option(names = {"-c", "--configuration-file"},
            description = "Configuration file, mostly useful in QDoc mode (default: " +
                    "${DEFAULT-VALUE})")
    private String configurationFile = "config.json";
    @CommandLine.Option(names = {"-o", "--output-folder"},
            description = "QDoc output folder (DocBook files)", required = true)
    private String output;

    @Override
    public Void call() throws SaxonApiException, IOException, InterruptedException,
            ParserConfigurationException, SAXException {
        GlobalConfiguration config = new GlobalConfiguration(configurationFile);
        QDocValidateCore.call(output, config);
        return null;
    }
}
