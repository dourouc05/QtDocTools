package be.tcuvelier.qdoctools.core;

import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.config.QdtPaths;
import be.tcuvelier.qdoctools.core.handlers.QDocHandler;
import be.tcuvelier.qdoctools.core.handlers.XsltHandler;
import be.tcuvelier.qdoctools.core.helpers.FileHelpers;
import be.tcuvelier.qdoctools.core.helpers.FormattingHelpers;
import be.tcuvelier.qdoctools.core.helpers.ValidationHelper;
import be.tcuvelier.qdoctools.core.utils.Pair;
import be.tcuvelier.qdoctools.core.utils.QtVersion;
import net.sf.saxon.s9api.SaxonApiException;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class QDocCore {
    public static void call(String source, String installed, String output, String htmlVersion,
                            QtVersion qtVersion, boolean qdocDebug, boolean reduceIncludeListSize,
                            boolean validate, boolean convertToDocBook,
                            boolean convertToDvpML, boolean checkConsistency,
                            GlobalConfiguration config)
            throws SaxonApiException, IOException, InterruptedException,
            ParserConfigurationException, SAXException {
        // Perform the conversion cycle, as complete as required.

        // First, initialise global objects.
        List<String> includes = config.getCppCompilerIncludes();
        includes.addAll(config.getNdkIncludes());
        QDocHandler q = new QDocHandler(source, installed, output, htmlVersion,
                config.getQDocLocation(), qtVersion, qdocDebug, reduceIncludeListSize,
                includes, config);

        // Explore the source directory for the qdocconf files.
        System.out.println("++> Looking for qdocconf files");
        List<Pair<String, Path>> modules = q.findModules().first;
        System.out.println("++> " + modules.size() + " modules found");

        // Disable Qt for Education: qdoc fails with that one. Error message:
        //     qdoc can't run; no project set in qdocconf file
        System.out.println("++> Filtering problematic modules");
        modules = modules.stream().filter(pair -> !pair.second.toString().contains("qtforeducation.qdocconf")).toList();
        System.out.println("++> " + modules.size() + " modules kept");

        // Run qdoc to get the DocBook output.
        if (convertToDocBook) {
            // Write the list of qdocconf files.
            Path mainQdocconfPath = q.makeMainQdocconf(modules);
            System.out.println("++> Main qdocconf written: " + mainQdocconfPath);

            // Run QtAttributionScanner to generate some files.
            System.out.println("++> Running QtAttributionScanner.");
            q.runQtAttributionsScanner(modules);
            System.out.println("++> QtAttributionScanner done.");

            // Actually run qdoc on this new file.
            System.out.println("++> Running QDoc.");
            q.runQDoc(); // TODO: think about running moc to avoid too many errors while reading
            // the code.
            System.out.println("++> QDoc done.");

            System.out.println("++> Fixing some qdoc quirks.");
            q.copyGeneratedFiles(); // Sometimes, qdoc outputs things in a strange folder. Ahoy!
            // TODO: fix paths when moving files from one folder to the other. (xref: .
            //  ./qtwidgets/...)
            q.fixQDocBugs();
            System.out.println("++> QDoc quirks fixed."); // At least, the ones I know about
            // right now.

            System.out.println("++> Validating DocBook output.");
            q.validateDocBook();
            System.out.println("++> DocBook output validated.");
        }

        // Perform some consistency checks on the contents to ensure that there is no hidden major
        // qdoc bug.
        if (checkConsistency && htmlVersion.isEmpty()) {
            System.out.println("!!> Cannot check consistency without an existing HTML version" +
                    " (--html-version).");
        } else if (checkConsistency) {
            // As of Qt 5.15-6.4, the docs installed at the same time as Qt with the official
            // installer have the same folder structure as output by QDoc: the copies done in
            // copyGeneratedFiles() cannot yet be removed for this check to be performed!
            System.out.println("++> Checking consistency of the DocBook output.");
            q.checkDocBookConsistency();
            System.out.println("++> DocBook consistency checked.");
        }

        // Run Saxon to get the DvpML output.
        if (convertToDvpML) {
            Path root = q.getOutputFolder();

            // Iterate through all the files.
            System.out.println("++> Starting DocBook-to-DvpML transformation.");
            List<Path> xml = q.findDocBook();

            if (xml.isEmpty()) {
                System.out.println("??> Have DocBook files been generated in " +
                        q.getOutputFolder() + "? There are no DocBook files there.");
            }

            XsltHandler h = new XsltHandler(new QdtPaths(config).getXsltToDvpMLPath());
            int i = 0;
            for (Path file : xml) {
                System.out.println(FormattingHelpers.prefix(i, xml) + " " + file);

                // Output the result in the same folder as before, with the same file name, just add a "_dvp" suffix
                // (the same as in the XSLT sheets).
                String baseFileName = FileHelpers.removeExtension(file);
                Path destination = root.resolve(baseFileName + "_dvp.xml");

                // Actually convert the DocBook into DvpML. This may print errors directly to stderr.
                h.transform(file.toFile(), destination.toFile(), Map.of(
                        "doc-qt", true,
                        "qt-version", qtVersion.QT_VER(),
                        "document-file-name", baseFileName
                ));

                // Handle validation.
                if (validate) {
                    try {
                        boolean isValid = ValidationHelper.validateDvpML(destination, config);
                        if (!isValid) {
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
        }
    }
}
