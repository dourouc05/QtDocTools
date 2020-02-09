package be.tcuvelier.qdoctools.cli;

/*
 * TODO: How to encode/retrieve block <screen> vs. <programlisting>? screen/prompt?
 * TODO: How to encode/retrieve inline <filename>? filename/replaceable?
 */

import be.tcuvelier.qdoctools.core.TransformCore;
import be.tcuvelier.qdoctools.core.TransformCore.Format;
import be.tcuvelier.qdoctools.core.config.Configuration;
import net.sf.saxon.s9api.SaxonApiException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.xml.sax.SAXException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.util.concurrent.Callable;

@Command(name = "transform", description = "Perform transformations to and from DocBook for publishing")
public class TransformCommand implements Callable<Void> {
    @Option(names = { "-i", "--input-file", "--input-folder" },
            description = "File or folder to process", required = true)
    private String input;

    @Option(names = { "-if", "--input-format" },
            description = "Format of the input to transform. Default value: detected from extension. " +
                    "Valid values: ${COMPLETION-CANDIDATES}")
    private Format inputFormat = Format.Default;

    @Option(names = { "-o", "--output-file", "--output-folder" },
            description = "Output file or folder")
    private String output;

    @Option(names = { "-of", "--output-format" },
            description = "Format of the output to generate. Default value: DOCX if the input is DocBook, " +
                    "DocBook otherwise. Valid values: ${COMPLETION-CANDIDATES}")
    private Format outputFormat = Format.Default;

    @Option(names = { "-c", "--configuration-file" },
            description = "Configuration file (default: ${DEFAULT-VALUE})")
    private String configurationFile = "config.json";

    @Option(names = "--no-validation",
            description = "Disables the validation of the output against a known XSD or RNG")
    private boolean validate = true;

    @Option(names = { "--disable-sanity-checks" },
            description = "Perform the sanity checks, but continue with generation even in case of failure")
    private boolean disableSanityChecks = false;

    @Override
    public Void call() throws SaxonApiException, IOException, SAXException, InvalidFormatException,
            XMLStreamException, ParserConfigurationException {
        Configuration config = new Configuration(configurationFile);
        TransformCore.call(input, inputFormat, output, outputFormat, config, validate, disableSanityChecks);
        return null;
    }
}
