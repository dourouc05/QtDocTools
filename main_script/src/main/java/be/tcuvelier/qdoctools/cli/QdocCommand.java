package be.tcuvelier.qdoctools.cli;

import be.tcuvelier.qdoctools.core.QdocCore;
import be.tcuvelier.qdoctools.core.helpers.FormattingHelpers;
import be.tcuvelier.qdoctools.core.utils.Configuration;
import be.tcuvelier.qdoctools.core.utils.Pair;
import be.tcuvelier.qdoctools.core.utils.QtVersion;
import be.tcuvelier.qdoctools.core.handlers.QdocHandler;
import be.tcuvelier.qdoctools.core.handlers.XsltHandler;
import be.tcuvelier.qdoctools.core.helpers.FileHelpers;
import be.tcuvelier.qdoctools.core.helpers.ValidationHelper;
import net.sf.saxon.s9api.SaxonApiException;
import org.xml.sax.SAXException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "qdoc", description = "Run qdoc and the associated transformations")
public class QdocCommand implements Callable<Void> {
    @Option(names = { "-i", "--source-folder" },
            description = "Folder to process (source code of Qt)", required = true)
    private String source;

    @Option(names = { "-s", "--installed-folder" },
            description = "Folder with a complete Qt installation " +
                    "(either precompiled or built from scratch and installed)", required = true)
    private String installed;

    @Option(names = { "-o", "--output-folder" },
            description = "Output folder", required = true)
    private String output;

    @Option(names = { "-c", "--configuration-file" },
            description = "Configuration file, mostly useful in qdoc mode (default: ${DEFAULT-VALUE})")
    private String configurationFile = "config.json";

    @Option(names = "--qt-version",
            description = "Version of Qt that is being processed")
    private QtVersion qtVersion = new QtVersion("1.0");

    @Option(names = "--qdoc-debug",
            description = "Run qdoc in debug mode")
    private boolean qdocDebug = false;

    @Option(names = "--no-validation",
            description = "Disables the validation of the output against a known XSD or RNG at all steps")
    private boolean validate = true;

    @Option(names = "--no-convert-docbook", description = "Disables the generation of the DocBook files " +
            "(i.e. do not run qdoc)")
    private boolean convertToDocBook = true;

    @Option(names = "--no-convert-dvpml", description = "Disables the generation of the DvpML files. " +
            "This operation requires the prior generation of the DocBook files")
    private boolean convertToDvpML = true;

    @Option(names = "--no-consistency-checks", description = "Disables advanced consistency checks. " +
            "They require an Internet connection")
    private boolean consistencyChecks = true;
    // TODO: Still required with the qdoc machinery? Probably not that much, but it would really not hurt to have it to check qdoc works properly...

    @Override
    public Void call() throws SaxonApiException, IOException, InterruptedException, ParserConfigurationException, SAXException {
        QdocCore.call(source, installed, output, configurationFile, qtVersion, qdocDebug, validate, convertToDocBook,
                convertToDvpML, consistencyChecks);
        return null;
    }
}
