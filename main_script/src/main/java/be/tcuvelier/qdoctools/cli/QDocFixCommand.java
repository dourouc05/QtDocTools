package be.tcuvelier.qdoctools.cli;

import be.tcuvelier.qdoctools.core.QDocFixCore;
import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import net.sf.saxon.s9api.SaxonApiException;
import org.xml.sax.SAXException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.concurrent.Callable;

// TODO: make this a subcommand of QDocCommand?
@Command(name = "qdocfix", description = "Fix QDoc quirks in a set of DocBook files")
public class QDocFixCommand implements Callable<Void> {
    @Option(names = {"-o", "--output-folder"},
            description = "QDoc output folder (DocBook files)", required = true)
    private String output;

    @Override
    public Void call() throws SaxonApiException, IOException, InterruptedException,
            ParserConfigurationException, SAXException {
        QDocFixCore.call(output);
        return null;
    }
}
