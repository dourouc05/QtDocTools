package be.tcuvelier.qdoctools.cli;

import be.tcuvelier.qdoctools.core.*;
import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.utils.QtVersion;
import net.sf.saxon.s9api.SaxonApiException;
import org.xml.sax.SAXException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.concurrent.Callable;

@Command(name = "qdoc", description = "Run QDoc and the associated transformations")
public class QDocCommand implements Callable<Void> {
    @Option(names = {"-c", "--configuration-file"},
            description = "Configuration file, mostly useful in QDoc mode (default: " +
                    "${DEFAULT-VALUE})",
            scope = CommandLine.ScopeType.INHERIT)
    private String configurationFile = "config.json";
    @Option(names = "--qt-version",
            description = "Version of Qt that is being processed",
            scope = CommandLine.ScopeType.INHERIT)
    private QtVersion qtVersion = new QtVersion();
    @Option(names = "--qdoc-debug",
            description = "Run QDoc in debug mode",
            scope = CommandLine.ScopeType.INHERIT)
    private boolean qdocDebug = false;
    @Option(names = "--xslt-debug",
            description = "Run XSLT conversions in debug mode (show logs)",
            scope = CommandLine.ScopeType.INHERIT)
    private boolean xsltDebug = false;
    @Option(names = "--reduce-include-list-size",
            description = "Reduce the size of the include list. This is useful when the list of " +
                    "arguments to QDoc starts getting out of control, as some platforms will " +
                    "limit the size of the argument list",
            scope = CommandLine.ScopeType.INHERIT)
    private boolean reduceIncludeListSize = true;
    @Option(names = "--no-validation",
            description = "Disables the validation of the output against a known XSD or RNG at " +
                    "all steps",
            scope = CommandLine.ScopeType.INHERIT)
    private boolean validate = true;
    @Option(names = "--no-convert-docbook",
            description = "Disables the generation of the DocBook files (i.e. do not run QDoc)",
            scope = CommandLine.ScopeType.INHERIT)
    private boolean convertToDocBook = true;
    @Option(names = "--no-consistency-check",
            description = "Disables the consistency checks between the DocBook version and a" +
                    " given HTML version of the docs (passed with --html-folder)",
            scope = CommandLine.ScopeType.INHERIT)
    private boolean checkConsistency = true;
    @Option(names = "--no-convert-dvpml",
            description = "Disables the generation of the DvpML files. " +
                    "This operation requires the prior generation of the DocBook files",
            scope = CommandLine.ScopeType.INHERIT)
    private boolean convertToDvpML = true;
    @Option(names = {"-s", "--source-folder"},
            description = "Folder to process (source code of Qt)",
            scope = CommandLine.ScopeType.INHERIT)
    private String source;
    @Option(names = {"-i", "--installed-folder"},
            description = "Folder with a complete Qt installation " +
                    "(either precompiled or built from scratch and installed)",
            scope = CommandLine.ScopeType.INHERIT)
    private String installed;
    @Option(names = {"-o", "--output-folder"},
            description = "QDoc output folder (DocBook files); " +
                    "for the publish command, where the DocBook files reside",
            scope = CommandLine.ScopeType.INHERIT)
    private String output; // Only parameter required by all subcommands.
    @Option(names = {"-d", "--dvpml-output-folder"},
            description = "QtDocTools output folder (DvpML files)",
            scope = CommandLine.ScopeType.INHERIT)
    private String dvpmlOutput = output;
    @Option(names = {"-h", "--html-folder"},
            description = "HTML-version folder (already generated documentation; it will not be" +
                    " created by this tool), typically found near your Qt installation",
            scope = CommandLine.ScopeType.INHERIT)
    private String htmlFolder;

    @Override
    public Void call() throws SaxonApiException, IOException, InterruptedException,
            ParserConfigurationException, SAXException {
        if (source.isEmpty()) {
            throw new RuntimeException("Argument --source missing when generating Qt docs; " +
                    "what are Qt's sources located?");
        }
        if (output.isEmpty()) {
            throw new RuntimeException("Argument --output-folder missing when generating Qt docs; " +
                    "where should the DocBook version of the Qt docs be output?");
        }
        if (installed.isEmpty()) {
            throw new RuntimeException("Argument --installed-folder missing when generating Qt docs; " +
                    "what is the path of an installed version of Qt?");
        }
        if (checkConsistency && htmlFolder.isEmpty()) {
            System.out.println("!!> Cannot check consistency without an existing HTML version" +
                    " (--html-version).");
            checkConsistency = false;
        }
        if (convertToDvpML && dvpmlOutput.isEmpty()) {
            throw new RuntimeException("Argument --dvpml-output missing when generating DvpML files for Qt docs; " +
                    "in which folder should the DvpML output be located?");
        }

        GlobalConfiguration config = new GlobalConfiguration(configurationFile);
        QDocCore.call(source, installed, output, dvpmlOutput, htmlFolder, qtVersion, qdocDebug,
                reduceIncludeListSize, validate, convertToDocBook, checkConsistency,
                convertToDvpML, xsltDebug, config);
        return null;
    }

    @Command(name = "post")
    public void post() throws IOException {
        if (output.isEmpty()) {
            throw new RuntimeException("Argument --output-folder missing when generating Qt docs; " +
                    "where is located the DocBook version of the Qt docs (for instance, a Git repository)?");
        }
        QDocCore.postprocess(output);
    }

    @Command(name = "validate")
    public void validate() throws IOException, SAXException {
        if (output.isEmpty()) {
            throw new RuntimeException("Argument --output-folder missing when generating Qt docs; " +
                    "where is located the DocBook version of the Qt docs (for instance, a Git repository)?");
        }
        GlobalConfiguration config = new GlobalConfiguration(configurationFile);
        QDocCore.validate(output, config);
    }

    @Command(name = "fix")
    public void fix() throws IOException {
        if (output.isEmpty()) {
            throw new RuntimeException("Argument --output-folder missing when generating Qt docs; " +
                    "where is located the DocBook version of the Qt docs (for instance, a Git repository)?");
        }
        QDocCore.fix(output);
    }

    @Command(name = "publish")
    public void publish() throws SaxonApiException, IOException {
        if (output == null || output.isEmpty()) {
            throw new RuntimeException("Argument --output-folder missing when generating Qt docs; " +
                    "where is located the DocBook version of the Qt docs (for instance, a Git repository)?");
        }
        if (dvpmlOutput == null || dvpmlOutput.isEmpty()) {
            throw new RuntimeException("Argument --dvpml-output missing when generating DvpML files for Qt docs; " +
                    "in which folder should the DvpML output be located?");
        }

        GlobalConfiguration config = new GlobalConfiguration(configurationFile);
        QDocCore.publish(output, dvpmlOutput, qtVersion, validate, convertToDvpML, xsltDebug, config);
    }
}
