package io.github.dourouc05.qtdoctools.from.qdoc;

import net.sf.saxon.s9api.*;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.xml.transform.stream.StreamSource;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ConsistencyChecks {
    private static <T> Set<T> difference(Set<T> a, Set<T> b) {
        Set<T> difference = new HashSet<>(a);
        difference.removeAll(b);
        return difference;
    }

    private static <T> boolean compareSets(Set<T> a, Set<T> b) {
        return a.size() == b.size() && difference(a, b).size() == 0;
    }

    public static void check(Path fileName, String prefix) throws IOException, SaxonApiException {
        // Performs all checks and prints the results.

        CheckRequest r;
        try {
            r = new CheckRequest(fileName);
        } catch (HttpStatusException e) {
            System.out.println(prefix + " Error while performing the inherited-by class check: 404 when downloading the original class.");
            // For instance, QAbstractXMLReceiver: https://doc-snapshots.qt.io/qt5-5.9/qabstractxmlreceiver.html exists,
            // but not http://doc.qt.io/qt-5/qabstractxmlreceiver.html.
            return; // Cannot continue.
        } catch (IOException | SaxonApiException e) {
            System.out.println(prefix + " Error while performing the inherited-by class check: ");
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
            ItemsResult items = checkItems(r);
            if (! items.result) {
                System.out.println(prefix + " File: " + fileName.toString());

                if (! items.resultPublicTypes) {
                    System.out.println(prefix + " Public types mismatch: ");
                    System.out.println(prefix + "     - DocBook has: " + Arrays.toString(items.publicTypesXML.toArray()));
                    System.out.println(prefix + "     - HTML has: " + Arrays.toString(items.publicTypesHTML.toArray()));
                }
            }
        } catch (SaxonApiException e) {
            System.out.println(prefix + " Error while performing the items class check: ");
            e.printStackTrace();
        }
    }

    private static class CheckRequest {
        Processor processor;
        XdmNode xdm;
        XPathCompiler compiler;
        Document html;

        CheckRequest(Path fileName) throws IOException, SaxonApiException {
            processor = new Processor(false);
            xdm = processor.newDocumentBuilder().build(new StreamSource(new FileReader(fileName.toFile())));
            compiler = processor.newXPathCompiler();
            compiler.declareNamespace("db", "http://docbook.org/ns/docbook");
            compiler.declareNamespace("xlink", "http://www.w3.org/1999/xlink");

            String otherFile = fileName.getFileName().toString().replace(".qdt", "") + ".html";
            html = Jsoup.connect("http://doc.qt.io/qt-5/" + otherFile).get();
        }

        XdmValue xpath(String expression) throws SaxonApiException {
            return compiler.evaluate(expression, xdm);
        }

        boolean isClass() throws SaxonApiException {
            return xpath("//db:classsynopsis").size() > 0;
        }
    }

    public static class InheritedByResult {
        public final boolean result;
        public final Set<String> xml;
        public final Set<String> html;

        InheritedByResult(boolean result) {
            xml = new HashSet<>();
            html = new HashSet<>();
            this.result = result;
        }

        InheritedByResult(Set<String> xml, Set<String> html) {
            this.xml = xml;
            this.html = html;
            result = compareSets(xml, html);
        }
    }

    public static InheritedByResult checkInheritedBy(CheckRequest request) throws SaxonApiException {
        // If the local file is not a class, the test is passed (only for classes).
        if (! request.isClass()) {
            return new InheritedByResult(true);
        }

        // Find the inherited-by classes.
        XdmValue inheritedByListXML = request.xpath("//db:classsynopsisinfo[@role='inheritedBy']/text()");

        Set<String> inheritedBySetXML = new HashSet<>();
        for (int i = 0; i < inheritedByListXML.size(); ++i) {
            inheritedBySetXML.add(inheritedByListXML.itemAt(i).getStringValue());
        }

        // Load the remote HTML.
        Elements inheritedByTagHTML = request.html.getElementsContainingText("Inherited By:");
        Set<String> inheritedBySetHTML = new HashSet<>();

        if (inheritedByTagHTML.size() > 0) {
            // inheritedByTagHTML contains all tags that contain a tag that has "Inherited by": take the last one,
            // the most precise of this collection.
            Elements inheritedByListHTML = inheritedByTagHTML.get(inheritedByTagHTML.size() - 1).siblingElements().get(0).getElementsByTag("a");
            for (Element e : inheritedByListHTML) {
                inheritedBySetHTML.add(e.text());
            }
        }

        // Compare.
        return new InheritedByResult(inheritedBySetXML, inheritedBySetHTML);
    }

    public static class ItemsResult {
        public final boolean result;
        public final boolean resultPublicTypes;
        public final Set<String> publicTypesXML;
        public final Set<String> publicTypesHTML;

        ItemsResult() {
            result = true;
            resultPublicTypes = true;
            publicTypesXML = new HashSet<>();
            publicTypesHTML = new HashSet<>();
        }

        ItemsResult(Set<String> publicTypesXML, Set<String> publicTypesHTML) {
            this.publicTypesXML = publicTypesXML;
            this.publicTypesHTML = publicTypesHTML;
            resultPublicTypes = compareSets(publicTypesXML, publicTypesHTML);

            result = resultPublicTypes;
        }
    }

    public static ItemsResult checkItems(CheckRequest request) throws SaxonApiException {
        // If the local file is not a class, the test is passed (only for classes).
        if (! request.isClass()) {
            return new ItemsResult();
        }

        // Count the public types.
        XdmValue publicTypesXML = request.xpath("//db:enumsynopsis/db:enumname/text()");

        Set<String> publicTypesSetXML = new HashSet<>();
        for (int i = 0; i < publicTypesXML.size(); ++i) {
            publicTypesSetXML.add(publicTypesXML.itemAt(i).getStringValue());
        }

        Elements publicTypesHTML = request.html.getElementsContainingText("Public Types");
        Set<String> publicTypesSetHTML = new HashSet<>();
        if (publicTypesHTML.size() > 0) {
            Elements publicTypesListHTML = publicTypesHTML.get(publicTypesHTML.size() - 1).nextElementSibling().getElementsByTag("a");
            for (Element e : publicTypesListHTML) {
                publicTypesSetHTML.add(e.text());
            }
        }

        // Count the properties.

        // Count the public functions.

        // Count the signals.

        return new ItemsResult(publicTypesSetXML, publicTypesSetHTML);
    }
}
