package be.tcuvelier.qdoctools.utils;

import be.tcuvelier.qdoctools.utils.consistency.CheckRequest;
import be.tcuvelier.qdoctools.utils.consistency.Items;
import be.tcuvelier.qdoctools.utils.consistency.ItemsResult;
import net.sf.saxon.s9api.SaxonApiException;
import org.jsoup.HttpStatusException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;

public class QdocConsistencyChecks {
    public static void check(Path fileName, String prefix) {
        // Performs all checks and prints the results.

        CheckRequest r;
        try {
            r = new CheckRequest(fileName);
        } catch (HttpStatusException e) {
            System.out.println(prefix + " Error while performing consistency checks: 404 when downloading the original class.");
            // For instance, QAbstractXMLReceiver: https://doc-snapshots.qt.io/qt5-5.9/qabstractxmlreceiver.html exists,
            // but not http://doc.qt.io/qt-5/qabstractxmlreceiver.html.
            return; // Cannot continue for this file.
        } catch (IOException | SaxonApiException e) {
            System.out.println(prefix + " Error while performing consistency checks: ");
            e.printStackTrace();
            return; // Cannot continue.
        }

//        try {
//            InheritedByResult inheritedBy = checkInheritedBy(r);
//            if (! inheritedBy.result) {
//                System.out.println(prefix + " File: " + fileName.toString());
//                System.out.println(prefix + " Inherited-by classes mismatch: ");
//                System.out.println(prefix + "     - DocBook has: " + Arrays.toString(inheritedBy.xml.toArray()));
//                System.out.println(prefix + "     - HTML has: " + Arrays.toString(inheritedBy.html.toArray()));
//            }
//        } catch (SaxonApiException e) {
//            System.out.println(prefix + " Error while performing the inherited-by class check: ");
//            e.printStackTrace();
//        }

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
        } catch (SaxonApiException e) {
            System.out.println(prefix + " Error while performing the items class check: ");
            e.printStackTrace();
        }
    }
}
