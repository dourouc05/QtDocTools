package io.github.dourouc05.qtdoctools.from.qdoc;

import net.sf.saxon.s9api.*;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.xml.transform.stream.StreamSource;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ConsistencyChecks {
    public static void check(Path fileName, String prefix) {
        // Performs all checks and prints the results.

        try {
            InheritedByResult inheritedBy = checkInheritedBy(fileName);
            if (! inheritedBy.result) {
                System.out.println(prefix + " File: " + fileName.toString());
                System.out.println(prefix + " Inherited-by classes mismatch: ");
                System.out.println(prefix + "     - DocBook has: " + Arrays.toString(inheritedBy.xml.toArray()));
                System.out.println(prefix + "     - HTML has: " + Arrays.toString(inheritedBy.html.toArray()));
            }
        } catch (HttpStatusException e) {
            System.out.println(prefix + " Error while performing the inherited-by class check: 404 when downloading the original class.");
            // For instance, QAbstractXMLReceiver: https://doc-snapshots.qt.io/qt5-5.9/qabstractxmlreceiver.html exists,
            // but not http://doc.qt.io/qt-5/qabstractxmlreceiver.html.
        } catch (IOException | SaxonApiException e) {
            System.out.println(prefix + " Error while performing the inherited-by class check: ");
            e.printStackTrace();
        }
    }

    private static class SaxonState {
        Processor processor;
        XdmNode xdm;
        XPathCompiler compiler;

        SaxonState(Path fileName) throws FileNotFoundException, SaxonApiException {
            processor = new Processor(false);
            xdm = processor.newDocumentBuilder().build(new StreamSource(new FileReader(fileName.toFile())));
            compiler = processor.newXPathCompiler();
            compiler.declareNamespace("db", "http://docbook.org/ns/docbook");
            compiler.declareNamespace("xlink", "http://www.w3.org/1999/xlink");
        }

        XdmValue xpath(String expression) throws SaxonApiException {
            return compiler.evaluate(expression, xdm);
        }

        boolean isClass() throws SaxonApiException {
            return xpath("//db:classsynopsis").size() > 0;
        }
    }

    private static Document readOnline(Path fileName) throws IOException {
        return Jsoup.connect("http://doc.qt.io/qt-5/" + fileName.getFileName().toString().replace(".qdt", "") + ".html").get();
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

            Set<String> difference = new HashSet<>(xml);
            difference.removeAll(html);
            result = xml.size() == html.size() && difference.size() == 0;
        }
    }

    public static InheritedByResult checkInheritedBy(Path fileName) throws IOException, SaxonApiException {
        SaxonState saxon = new SaxonState(fileName);

        // If the local file is not a class, the test is passed (only for classes).
        if (! saxon.isClass()) {
            return new InheritedByResult(true);
        }

        // Find the inherited-by classes.
        XdmValue inheritedByListXML = saxon.xpath("//db:classsynopsisinfo[@role='inheritedBy']/text()");

        Set<String> inheritedBySetXML = new HashSet<>();
        for (int i = 0; i < inheritedByListXML.size(); ++i) {
            inheritedBySetXML.add(inheritedByListXML.itemAt(i).getStringValue());
        }

        // Load the remote HTML.
        Document html = readOnline(fileName);
        Elements inheritedByTagHTML = html.getElementsContainingText("Inherited By:");
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

        ItemsResult() {
            result = true;
        }
    }

    public static ItemsResult checkItems(Path fileName) throws IOException, SaxonApiException {
        SaxonState saxon = new SaxonState(fileName);

        // If the local file is not a class, the test is passed (only for classes).
        if (! saxon.isClass()) {
            return new ItemsResult();
        }

        return new ItemsResult();
    }
}
