package be.tcuvelier.qdoctools.utils;

import be.tcuvelier.qdoctools.utils.consistency.*;
import net.sf.saxon.s9api.SaxonApiException;
import org.jsoup.HttpStatusException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;

public class QdocConsistencyChecks {
    private final Path fileName;
    private final String prefix;
    private final CheckRequest r;

    public QdocConsistencyChecks(Path fileName, String prefix, QtVersion qtVersion) throws IOException, SaxonApiException {
        this.fileName = fileName;
        this.prefix = prefix;

        try {
            r = new CheckRequest(fileName, qtVersion);
        } catch (HttpStatusException e) {
            System.out.println(prefix + " Error while performing consistency checks: 404 when downloading the original file.");
            // For instance, QAbstractXMLReceiver: https://doc-snapshots.qt.io/qt5-5.9/qabstractxmlreceiver.html exists,
            // but not http://doc.qt.io/qt-5/qabstractxmlreceiver.html.
            throw e; // Cannot continue for this file.
        } catch (IOException | SaxonApiException e) {
            System.out.println(prefix + " Error while performing consistency checks: ");
            e.printStackTrace();
            throw e; // Cannot continue.
        }
    }

    public boolean checkInheritedBy() {
        try {
            InheritedByResult inheritedBy = InheritedBy.checkInheritedBy(r);
            if (! inheritedBy.result) {
                System.out.println(prefix + " File: " + fileName.toString());
                System.out.println(prefix + " Inherited-by classes mismatch: ");
                System.out.println(prefix + "     - DocBook has: " + Arrays.toString(inheritedBy.xml.toArray()));
                System.out.println(prefix + "     - HTML has: " + Arrays.toString(inheritedBy.html.toArray()));
            }
            return inheritedBy.result;
        } catch (SaxonApiException e) {
            System.out.println(prefix + " Error while performing the inherited-by class check: ");
            e.printStackTrace();
            return false;
        }
    }

    public boolean checkItems() {
        try {
            ItemsResult items = Items.checkItems(r);
            if (! items.result()) {
                for (String name: items.tests()) {
                    if (! items.getResult(name)) {
                        System.out.println(prefix + " " + name + " mismatch: ");

                        Object[] docbook = items.getXML(name).toArray();
                        Arrays.sort(docbook);
                        System.out.println(prefix + "     - DocBook has: " + Arrays.toString(docbook));

                        Object[] html = items.getHTML(name).toArray();
                        Arrays.sort(html);
                        System.out.println(prefix + "     - HTML has:    " + Arrays.toString(html));

                        // Compute XML \ HTML and HTML \ XML (i.e. all differences, items in one set but not the other),
                        // their union (XML \ HTML) u (HTML \ XML), and output it.
                        Set<String> docbookMinusHTMLSet = items.getXML(name);
                        docbookMinusHTMLSet.removeAll(items.getHTML(name));
                        Set<String> htmlMinusDocBookSet = items.getHTML(name);
                        htmlMinusDocBookSet.removeAll(items.getXML(name));
                        htmlMinusDocBookSet.addAll(docbookMinusHTMLSet);
                        Object[] differences = htmlMinusDocBookSet.toArray();

                        Arrays.sort(differences);
                        System.out.println(prefix + "     > Differences between the sets: " + Arrays.toString(differences));
                    }
                }
            }
            return items.result();
        } catch (SaxonApiException e) {
            System.out.println(prefix + " Error while performing the items class check: ");
            e.printStackTrace();
            return false;
        }
    }
}
