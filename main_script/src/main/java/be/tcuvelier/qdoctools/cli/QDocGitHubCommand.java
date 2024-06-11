package be.tcuvelier.qdoctools.cli;

import be.tcuvelier.qdoctools.core.QDocCore;
import be.tcuvelier.qdoctools.core.QDocGitHubCore;
import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.utils.QtVersion;
import net.sf.saxon.s9api.SaxonApiException;
import org.xml.sax.SAXException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.concurrent.Callable;

@Command(name = "qdoc", description = "Transform the result from QDoc into a publication-ready version")
public class QDocGitHubCommand implements Callable<Void> {
    @Option(names = {"-c", "--configuration-file"},
            description = "Configuration file, mostly useful in QDoc mode (default: " +
                    "${DEFAULT-VALUE})")
    private String configurationFile = "config.json";
    @Option(names = "--qt-version",
            description = "Version of Qt that is being processed")
    private QtVersion qtVersion = new QtVersion("1.0");
    @Option(names = "--no-validation",
            description = "Disables the validation of the output against a known XSD or RNG at " +
                    "all steps")
    private boolean validate = true;
    @Option(names = "--no-convert-dvpml",
            description = "Disables the generation of the DvpML files")
    private boolean convertToDvpML = true;
    @Option(names = {"-i", "--input-folder"},
            description = "Input folder (DocBook files)", required = true)
    private String input;
    @Option(names = {"-d", "--dvpml-output-folder"},
            description = "Output folder (DvpML files)", required = true)
    private String dvpmlOutput;

    @Override
    public Void call() throws SaxonApiException, IOException, InterruptedException,
            ParserConfigurationException, SAXException {
        GlobalConfiguration config = new GlobalConfiguration(configurationFile);
        QDocGitHubCore.call(input, dvpmlOutput, qtVersion, validate, convertToDvpML, config);
        return null;
    }
}
