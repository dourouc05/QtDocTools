package be.tcuvelier.qdoctools.core;

import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.handlers.QDocToDvpMLHandler;
import be.tcuvelier.qdoctools.core.helpers.FormattingHelpers;
import be.tcuvelier.qdoctools.core.utils.QtVersion;
import net.sf.saxon.s9api.SaxonApiException;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class QDocPublishCore {
    public static void call(String input, String output, QtVersion qtVersion, boolean validateDvpML,
                            boolean convertToDvpML, GlobalConfiguration config) throws SaxonApiException, IOException {
        assert !input.isEmpty();
        assert !output.isEmpty();

        if (!convertToDvpML && !validateDvpML) {
            // Nothing to do.
            return;
        }

        QDocToDvpMLHandler qdh = new QDocToDvpMLHandler(input, output, qtVersion, config);

        // Run Saxon to get the DvpML output.
        if (convertToDvpML) {
            System.out.println("++> Starting DocBook-to-DvpML transformation.");

            // Gather the list of DocBook files.
            List<Path> xml = qdh.findDocBook();
            if (xml.isEmpty()) {
                System.out.println("??> There are no DocBook files in " + input);
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
            List<Path> dvpml = qdh.findDvpML();
            if (dvpml.isEmpty()) {
                System.out.println("??> There are no DvpML files in " + output);
            }

            int i = 0;
            for (Path dvpmlFile : dvpml) {
                System.out.println(FormattingHelpers.prefix(i, dvpml) + " " + dvpmlFile);

                try {
                    if (!qdh.isValidDvpML(dvpmlFile)) {
                        System.err.println(FormattingHelpers.prefix(i, dvpml) + "There were " +
                                "validation errors. See the above exception for details.");
                    }
                } catch (SAXException e) {
                    System.out.println(FormattingHelpers.prefix(i, dvpml) + " Validation error!");
                    //noinspection CallToPrintStackTrace
                    e.printStackTrace();
                }

                ++i;
            }
        }
    }
}
