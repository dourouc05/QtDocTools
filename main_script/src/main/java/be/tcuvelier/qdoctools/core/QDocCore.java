package be.tcuvelier.qdoctools.core;

import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.handlers.QDocMovingHandler;
import be.tcuvelier.qdoctools.core.handlers.QDocPostProcessingHandler;
import be.tcuvelier.qdoctools.core.handlers.QDocRunningHandler;
import be.tcuvelier.qdoctools.core.utils.Pair;
import be.tcuvelier.qdoctools.core.utils.QtVersion;
import net.sf.saxon.s9api.SaxonApiException;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class QDocCore {
    // Perform the conversion cycle, as complete as required.
    public static void call(String source, String installed, String output, String dvpmlOutput, String htmlVersion,
                            QtVersion qtVersion, boolean qdocDebug, boolean reduceIncludeListSize,
                            boolean validate, boolean convertToDocBook,
                            boolean convertToDvpML, boolean checkConsistency,
                            GlobalConfiguration config)
            throws SaxonApiException, IOException, InterruptedException,
            ParserConfigurationException, SAXException {
        // TODO: move the first part of this method to a new command that only calls QDoc and converts into DocBook?

        // First, initialise global objects.
        List<String> includes = config.getCppCompilerIncludes();
        includes.addAll(config.getNdkIncludes());

        QDocRunningHandler qrh = new QDocRunningHandler(source, installed, output,
                config.getQDocLocation(), qtVersion, qdocDebug, reduceIncludeListSize,
                includes, config);
        QDocMovingHandler qmh = new QDocMovingHandler(output);
        QDocPostProcessingHandler qpph = new QDocPostProcessingHandler(output, htmlVersion, config);
        // TODO: think of a way to avoid too many arguments to the QDoc*Handler constructors. config is only read from
        // a file.

        // Explore the source directory for the qdocconf files.
        System.out.println("++> Looking for qdocconf files");
        List<Pair<String, Path>> modules = qrh.findModules().first;
        System.out.println("++> " + modules.size() + " modules found");

        // Disable Qt for Education: qdoc fails with that one, around Qt 6.4 and 6.5. Error message:
        //     qdoc can't run; no project set in qdocconf file
        System.out.println("++> Filtering problematic modules");
        modules = modules.stream().filter(pair -> !pair.second.toString().contains("qtforeducation.qdocconf")).toList();
        System.out.println("++> " + modules.size() + " modules kept");

        // Run qdoc to get the DocBook output.
        if (convertToDocBook) {
            // Write the list of qdocconf files.
            Path mainQdocconfPath = qrh.makeMainQdocconf(modules);
            System.out.println("++> Main qdocconf written: " + mainQdocconfPath);

            // Run QtAttributionScanner to generate some files.
            System.out.println("++> Running QtAttributionScanner.");
            qrh.runQtAttributionsScanner(modules);
            System.out.println("++> QtAttributionScanner done.");

            // Actually run qdoc on this new file.
            System.out.println("++> Running QDoc.");
            qrh.runQDoc(); // TODO: think about running moc to avoid too many errors while reading
            // the code.
            System.out.println("++> QDoc done.");

            QDocPostProcessCore.call(output, htmlVersion, config);

            System.out.println("++> Validating DocBook output.");
            qpph.validateDocBook();
            System.out.println("++> DocBook output validated.");
        }

        // Perform some consistency checks on the contents to ensure that there is no hidden major
        // qdoc bug.
        if (checkConsistency) {
            System.out.println("++> Checking consistency of the DocBook output.");
            assert !htmlVersion.isEmpty();

            // As of Qt 5.15-6.5, the docs installed at the same time as Qt with the official
            // installer have the same folder structure as output by QDoc: the copies done in
            // copyGeneratedFiles() cannot yet be removed for this check to be performed!
            qpph.checkDocBookConsistency();
            System.out.println("++> DocBook consistency checked.");
        }

        QDocPublishCore.call(output, dvpmlOutput, qtVersion, validate, convertToDvpML, config);
    }
}
