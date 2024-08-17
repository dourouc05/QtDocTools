package be.tcuvelier.qdoctools.cli;

import be.tcuvelier.qdoctools.core.QDocCore;
import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.utils.QtVersion;
import net.sf.saxon.s9api.SaxonApiException;
import org.xml.sax.SAXException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.concurrent.Callable;

@Command(name = "qdoc", description = "Run QDoc and the associated transformations")
public class QDocCommand implements Callable<Void> {
    @Option(names = {"-c", "--configuration-file"},
            description = "Configuration file, mostly useful in QDoc mode (default: " +
                    "${DEFAULT-VALUE})")
    private String configurationFile = "config.json";
    @Option(names = "--qt-version",
            description = "Version of Qt that is being processed")
    private QtVersion qtVersion = new QtVersion("1.0");
    @Option(names = "--qdoc-debug",
            description = "Run QDoc in debug mode")
    private boolean qdocDebug = false;
    @Option(names = "--reduce-include-list-size",
            description = "Reduce the size of the include list. This is useful when the list of " +
                    "arguments to QDoc starts getting out of control, as some platforms will " +
                    "limit the size of the argument list")
    private boolean reduceIncludeListSize = true;
    @Option(names = "--no-validation",
            description = "Disables the validation of the output against a known XSD or RNG at " +
                    "all steps")
    private boolean validate = true;
    @Option(names = "--no-convert-docbook",
            description = "Disables the generation of the DocBook files (i.e. do not run QDoc)")
    private boolean convertToDocBook = true;
    @Option(names = "--no-consistency-check",
            description = "Disables the consistency checks between the DocBook version and a" +
                    " given HTML version of the docs (passed with --html-folder)")
    private boolean checkConsistency = true;
    @Option(names = "--no-convert-dvpml",
            description = "Disables the generation of the DvpML files. " +
                    "This operation requires the prior generation of the DocBook files")
    private boolean convertToDvpML = true;
    @Option(names = {"-s", "--source-folder"},
            description = "Folder to process (source code of Qt)", required = true)
    private String source;
    @Option(names = {"-i", "--installed-folder"},
            description = "Folder with a complete Qt installation " +
                    "(either precompiled or built from scratch and installed)", required = true)
    private String installed;
    @Option(names = {"-o", "--output-folder"},
            description = "QDoc output folder (DocBook files)", required = true)
    private String output;
    @Option(names = {"-d", "--dvpml-output-folder"},
            description = "QtDocTools output folder (DvpML files)")
    private String dvpmlOutput = output;
    @Option(names = {"-h", "--html-folder"},
            description = "HTML-version folder (already generated documentation; it will not be" +
                    " created by this tool), typically found near your Qt installation")
    private String htmlFolder;

    @Override
    public Void call() throws SaxonApiException, IOException, InterruptedException,
            ParserConfigurationException, SAXException {
        if (checkConsistency && htmlFolder.isEmpty()) {
            System.out.println("!!> Cannot check consistency without an existing HTML version" +
                    " (--html-version).");
        }
        if (convertToDvpML && dvpmlOutput.isEmpty()) {
            throw new RuntimeException("Argument --dvpml-output missing when generating DvpML files for Qt docs; " +
                    "in which folder should the DvpML output be located?");
        }

        GlobalConfiguration config = new GlobalConfiguration(configurationFile);
        QDocCore.call(source, installed, output, dvpmlOutput, htmlFolder, qtVersion, qdocDebug,
                reduceIncludeListSize, validate, convertToDocBook, checkConsistency,
                convertToDvpML, config);
        return null;
    }
}
