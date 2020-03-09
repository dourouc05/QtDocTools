package be.tcuvelier.qdoctools.cli;

/*
 * TODO: How to encode/retrieve block <screen> vs. <programlisting>? screen/prompt?
 * TODO: How to encode/retrieve inline <filename>? filename/replaceable?
 */

import be.tcuvelier.qdoctools.core.TransformCore;
import be.tcuvelier.qdoctools.core.TransformCore.Format;
import be.tcuvelier.qdoctools.core.UploadCore;
import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.helpers.FileHelpers;
import be.tcuvelier.qdoctools.core.helpers.TransformHelpers;
import net.sf.saxon.s9api.SaxonApiException;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.xml.sax.SAXException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(name = "transform", description = "Perform transformations to and from DocBook for publishing")
// No other formats are handled here. In particular, DvpML tools are wrapped by UploadCore.
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
            description = "Disables the validation of the output against a known XSD or RNG (default: ${DEFAULT-VALUE})")
    private boolean validate = true;

    @Option(names = { "--disable-sanity-checks" },
            description = "Perform the sanity checks, but continue with generation even in case of failure (default: ${DEFAULT-VALUE})")
    private boolean disableSanityChecks = false;

    @Option(names = { "--generate" },
            description = "Starts the standard DvpML tools to generate PHP/HTML files (default: ${DEFAULT-VALUE})")
    private boolean generate = false;

    @Option(names = { "--upload" },
            description = "Uploads the generated (with the --generate option) files (default: ${DEFAULT-VALUE})")
    private boolean upload = false;

    @Option(names = { "--clean" },
            description = "Cleans the generated file. This option is mostly useful with --generate and --upload (default: ${DEFAULT-VALUE})")
    private boolean clean = false;

    @Option(names = "--follow-links",
            description = "Follows the links in the files (default: ${DEFAULT-VALUE}). " +
                          "If true, for reference files, all linked files will be generated")
    private boolean followLinks = true;

    @Override
    public Void call() throws SaxonApiException, IOException, SAXException, InvalidFormatException,
            XMLStreamException, ParserConfigurationException, InterruptedException {
        // Shared part.
        GlobalConfiguration config = new GlobalConfiguration(configurationFile);

        // Handle related files.
        if (FileHelpers.isRelated(input)) {
            if (output == null || output.isBlank()) {
                output = FileHelpers.generateOutputFilename(input, Format.DvpML);
            }

            // Start the transformation into DvpML.
            TransformCore.callRelated(input, output, config);

            Map<Path, String> linkedFiles = null;
            if (followLinks) {
                List<Path> linkedFilesList = TransformHelpers.fromRelatedJSONToListOfRelatedFiles(input, config);
                linkedFiles = new HashMap<>(linkedFilesList.size());

                for (Path linked : linkedFilesList) {
                    linkedFiles.put(linked, FileHelpers.generateOutputFilename(linked, Format.DvpML));
                    TransformCore.call(linked.toString(), Format.DocBook, linkedFiles.get(linked), Format.DvpML, config, false, false);
                }
            }

            // Perform the upload if needed.
            if (generate) {
                System.out.println("Generating related: " + input);
                String outputRelated = UploadCore.generateRelated(input, config);

                Map<String, String> outputs = null;
                if (followLinks) {
                    assert linkedFiles != null;
                    outputs = new HashMap<>();
                    for (String linked : linkedFiles.values()) {
                        System.out.println("Generating linked article: " + linked);
                        outputs.put(linked, UploadCore.generateArticle(linked, config));
                    }
                }

                if (upload) {
                    System.out.println("Uploading related: " + input);
                    UploadCore.uploadRelated(input, outputRelated);

                    if (followLinks) {
                        assert linkedFiles != null;
                        assert outputs != null;

                        for (String linked : linkedFiles.values()) {
                            System.out.println("Uploading linked article: " + linked);
                            UploadCore.uploadArticle(linked, outputs.get(linked));
                        }
                    }
                }
            }
        }

        // Handle articles. No recursive operation makes sense here.
        else {
            // Replace default values.
            inputFormat = FileHelpers.parseFileFormat(inputFormat, input);
            outputFormat = FileHelpers.parseOutputFileFormat(inputFormat, outputFormat);
            if (output == null || output.isBlank()) {
                output = FileHelpers.generateOutputFilename(input, outputFormat);
            }

            boolean isOutputDvpML = outputFormat == Format.DvpML;
            if (outputFormat == Format.Default) {
                isOutputDvpML = FileHelpers.isDvpML(output);
            }

            // Start the transformation into DvpML.
            TransformCore.call(input, inputFormat, output, outputFormat, config, validate, disableSanityChecks);

            // Perform the upload if needed.
            if (isOutputDvpML && generate) {
                UploadCore.call(Paths.get(output), upload, config);
            }
        }

        // Clean-up operations.
        if (clean) {
            Paths.get(output).toFile().delete();
        }

        return null;
    }
}
