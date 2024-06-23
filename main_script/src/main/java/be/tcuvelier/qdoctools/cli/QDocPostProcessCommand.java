package be.tcuvelier.qdoctools.cli;

import be.tcuvelier.qdoctools.core.QDocPostProcessCore;
import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import net.sf.saxon.s9api.SaxonApiException;
import org.xml.sax.SAXException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.concurrent.Callable;

@Command(name = "qdoc", description = "Run QDoc and the associated transformations")
public class QDocPostProcessCommand implements Callable<Void> {
    @Option(names = {"-c", "--configuration-file"},
            description = "Configuration file, mostly useful in QDoc mode (default: " +
                    "${DEFAULT-VALUE})")
    private String configurationFile = "config.json";
    @Option(names = {"-s", "--source-folder"},
            description = "Folder to process (source code of Qt)", required = true)
    private String source;
    @Option(names = {"-i", "--installed-folder"},
            description = "Folder with a complete Qt installation " +
                    "(either precompiled or built from scratch and installed)", required = true)
    private String installed;
    @Option(names = {"-o", "--output-folder"},
            description = "Output folder (DocBook files)", required = true)
    private String output;
    @Option(names = {"-d", "--dvpml-output-folder"},
            description = "Output folder (DvpML files)")
    private String dvpmlOutput = output;
    @Option(names = {"-h", "--html-folder"},
            description = "HTML-version folder (already generated documentation; it will not be" +
                    " created by this tool), typically found near your Qt installation")
    private String htmlVersion;

    @Override
    public Void call() throws SaxonApiException, IOException, InterruptedException,
            ParserConfigurationException, SAXException {
        GlobalConfiguration config = new GlobalConfiguration(configurationFile);
        QDocPostProcessCore.call(output, htmlVersion, config);
        return null;
    }
}
