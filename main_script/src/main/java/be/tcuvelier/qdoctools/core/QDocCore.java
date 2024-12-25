package be.tcuvelier.qdoctools.core;

import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.handlers.*;
import be.tcuvelier.qdoctools.core.utils.Pair;
import be.tcuvelier.qdoctools.core.utils.QtVersion;
import net.sf.saxon.s9api.SaxonApiException;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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

            QDocCore.postprocess(output);
            QDocCore.validate(output, config);
        }

        // Perform some consistency checks on the contents to ensure that there is no hidden major
        // qdoc bug.
        if (checkConsistency) {
            QDocCore.checkConsistency(output, htmlVersion);
        }

        QDocCore.publish(output, dvpmlOutput, qtVersion, validate, convertToDvpML, config);
    }

    public static void checkConsistency(String output, String htmlVersion) throws IOException, SaxonApiException {
        System.out.println("++> Checking consistency of the DocBook output.");
        assert !output.isEmpty();
        assert !htmlVersion.isEmpty();

        // As of Qt 5.15-6.5, the docs installed at the same time as Qt with the official
        // installer have the same folder structure as output by QDoc: the copies done in
        // copyGeneratedFiles() cannot yet be removed for this check to be performed!
        QDocConsistencyCheckHandler qpph = new QDocConsistencyCheckHandler(output, htmlVersion);
        qpph.checkDocBookConsistency();
        System.out.println("++> DocBook consistency checked.");
    }

    // Performs a formal DocBook validation step
    public static void validate(String output, GlobalConfiguration config)
            throws IOException, SAXException {
        System.out.println("++> Validating DocBook output.");
        QDocValidateHandler qvh = new QDocValidateHandler(output, config);
        qvh.validateDocBook();
        System.out.println("++> DocBook output validated.");
    }

    // Perform the fixing part of the conversion cycle to get rid of QDoc quirks.
    public static void fix(String outputFolder)
            throws IOException {
        System.out.println("++> Fixing some QDoc quirks.");
        QDocFixHandler qfh = new QDocFixHandler(outputFolder, true);
        System.out.println("++>   Step 1: bugs.");
        qfh.fixQDocBugs();
        System.out.println("++>   Step 2: dates.");
        qfh.addDates();
        System.out.println("++>   Step 3: authors.");
        qfh.addAuthors();
        System.out.println("++>   Step 4: links.");
        qfh.fixLinks();
        System.out.println("++>   Final step: removing backups if needed.");
        qfh.removeBackupsIfNeeded();
        System.out.println("++> QDoc quirks fixed."); // At least, the ones I know about right now.
    }

    // Perform the postprocessing part of the conversion cycle.
    public static void postprocess(String outputFolder)
            throws IOException {
        System.out.println("++> Copying QDoc output.");
        QDocMovingHandler qrh = new QDocMovingHandler(outputFolder);
        qrh.copyGeneratedFiles(); // Sometimes, qdoc outputs things in a strange folder. Ahoy!
        System.out.println("++> QDoc output copied.");

        QDocCore.fix(outputFolder);
    }

    public static void publish(String input, String output, QtVersion qtVersion, boolean validateDvpML,
                            boolean convertToDvpML, GlobalConfiguration config) throws SaxonApiException, IOException {
        assert !input.isEmpty();
        assert !output.isEmpty();

        if (!convertToDvpML && !validateDvpML) {
            // Nothing to do.
            return;
        }

        Path inputFolder = Paths.get(input);
        Path outputFolder = Paths.get(output);
        QDocToDvpMLHandler qdh = new QDocToDvpMLHandler(inputFolder, outputFolder, qtVersion, config);

        // Run Saxon to get the DvpML output.
        if (convertToDvpML) {
            System.out.println("++> Starting DocBook-to-DvpML transformation.");

            // Gather the list of DocBook files.
            List<Path> xml = qdh.findWithExtension(inputFolder, ".xml");
            if (xml.isEmpty()) {
                System.out.println("??> There are no DocBook files in " + input);
            }

            // Iterate through all the files.
            // Not using TransformHelpers.fromDocBookToDvpML to avoid building one XsltHandler per file. As Qt's doc is
            // roughly 4,000 pages, that would mean loading the XSLT 4,000 times. Plus, there is some specific
            // postprocessing for Qt's doc.
            int i = 0;
            for (Path dbFile : xml) {
                System.out.println(prefix(i, xml) + " " + dbFile);

                // Actually convert the DocBook into DvpML. This may print errors directly to stderr.
                Path dvpmlFile = qdh.rewritePath(dbFile);
                qdh.transformDocBookToDvpML(dbFile, dvpmlFile);

                // Do a few manual transformations.
                qdh.fixURLs(dvpmlFile);
                qdh.copyImages(dbFile, dvpmlFile);

                ++i;
            }
            System.out.println("++> DocBook-to-DvpML transformation done.");

            // Final touch: move the index/ page to the root. After all, it's the index.
            qdh.moveIndex();
            System.out.println("++> DvpML index moved.");
        }

        // Handle validation.
        if (validateDvpML) {
            // Gather the list of DvpML files.
            List<Path> dvpml = qdh.findWithExtension(outputFolder, ".xml");
            if (dvpml.isEmpty()) {
                System.out.println("??> There are no DvpML files in " + output);
            }

            int i = 0;
            for (Path dvpmlFile : dvpml) {
                System.out.println(prefix(i, dvpml) + " " + dvpmlFile);

                try {
                    if (!qdh.isValidDvpML(dvpmlFile)) {
                        System.err.println(prefix(i, dvpml) + " There were " +
                                "validation errors. See the above exception for details.");
                    }
                } catch (SAXException e) {
                    System.out.println(prefix(i, dvpml) + " Validation error!");
                    //noinspection CallToPrintStackTrace
                    e.printStackTrace();
                }

                ++i;
            }
        }
    }

    private static String prefix(int i, List<?> list) {
        String iFormat = "%0" + Integer.toString(list.size()).length() + "d";
        return "[" + String.format(iFormat, i + 1) + "/" + list.size() + "]";
    }
}
