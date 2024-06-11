package be.tcuvelier.qdoctools.core;

import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.handlers.QDocPostProcessingHandler;
import be.tcuvelier.qdoctools.core.handlers.QDocRunningHandler;
import be.tcuvelier.qdoctools.core.handlers.QDocToDvpMLHandler;
import be.tcuvelier.qdoctools.core.helpers.FormattingHelpers;
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
        // First, initialise global objects.
        List<String> includes = config.getCppCompilerIncludes();
        includes.addAll(config.getNdkIncludes());

        QDocRunningHandler qrh = new QDocRunningHandler(source, installed, output,
                config.getQDocLocation(), qtVersion, qdocDebug, reduceIncludeListSize,
                includes, config);
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

            System.out.println("++> Fixing some qdoc quirks.");
            qrh.copyGeneratedFiles(); // Sometimes, qdoc outputs things in a strange folder. Ahoy!
            qpph.fixQDocBugs();
            qpph.addDates();
            qpph.addAuthors();
            qpph.fixLinks();
            System.out.println("++> QDoc quirks fixed."); // At least, the ones I know about right now.

            System.out.println("++> Validating DocBook output.");
            qpph.validateDocBook();
            System.out.println("++> DocBook output validated.");
        }

        // Perform some consistency checks on the contents to ensure that there is no hidden major
        // qdoc bug.
        if (checkConsistency) {
            if (htmlVersion.isEmpty()) {
                System.out.println("!!> Cannot check consistency without an existing HTML version" +
                        " (--html-version).");
            } else {
                // As of Qt 5.15-6.5, the docs installed at the same time as Qt with the official
                // installer have the same folder structure as output by QDoc: the copies done in
                // copyGeneratedFiles() cannot yet be removed for this check to be performed!
                System.out.println("++> Checking consistency of the DocBook output.");
                qpph.checkDocBookConsistency();
                System.out.println("++> DocBook consistency checked.");
            }
        }

        // TODO: rather call QDocGitHubCore.call instead of duplicating the code? Is there any value to the duplication?
        // Moving more code to the handler would make them output logs.

        // Run Saxon to get the DvpML output.
        if (convertToDvpML) {
            System.out.println("++> Starting DocBook-to-DvpML transformation.");

            if (dvpmlOutput.isEmpty()) {
                throw new RuntimeException("Argument --dvpml-output missing when generating DvpML files for Qt docs; " +
                        "in which folder should the DvpML output be located?");
            }

            QDocToDvpMLHandler qdh = new QDocToDvpMLHandler(output, dvpmlOutput, qtVersion, config);
            List<Path> xml = qdh.findDocBook();
            if (xml.isEmpty()) {
                System.out.println("??> Have DocBook files been generated in " +
                        qrh.getOutputFolder() + "? There are no DocBook files there.");
            }

            // Iterate through all the files.
            // Not using TransformHelpers.fromDocBookToDvpML to avoid building one XsltHandler per file. As Qt's doc is
            // roughly 4,000 pages, that would mean loading the XSLT 4,000 times. Plus, there is some specific
            // postprocessing for Qt's doc.
            int i = 0;
            for (Path dbFile : xml) {
                System.out.println(FormattingHelpers.prefix(i, xml) + " " + dbFile);

                // Actually convert the DocBook into DvpML. This may print errors directly to stderr.
                Path dvpmlFile = qdh.rewritePath(dbFile);
                qdh.transformDocBookToDvpML(dbFile, dvpmlFile);

                // Do a few manual transformations.
                qdh.fixURLs(dvpmlFile);
                qdh.copyImages(dbFile, dvpmlFile);

                // Handle validation.
                if (validate) {
                    try {
                        if (!qdh.isValidDvpML(dvpmlFile)) {
                            System.err.println(FormattingHelpers.prefix(i, xml) + "There were " +
                                    "validation errors. See the above exception for details.");
                        }
                    } catch (SAXException e) {
                        System.out.println(FormattingHelpers.prefix(i, xml) + " Validation error!");
                        //noinspection CallToPrintStackTrace
                        e.printStackTrace();
                    }
                }

                ++i;
            }
            System.out.println("++> DocBook-to-DvpML transformation done.");

            // Final touch: move the index/ page to the root. After all, it's the index.
            qdh.moveIndex();
            System.out.println("++> DvpML index moved.");
        }
    }
}
