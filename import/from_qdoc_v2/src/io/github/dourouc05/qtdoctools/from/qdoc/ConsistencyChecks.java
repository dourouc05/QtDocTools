package io.github.dourouc05.qtdoctools.from.qdoc;

import net.sf.saxon.s9api.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
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
        } catch (IOException | ParserConfigurationException | SAXException | XPathExpressionException | SaxonApiException e) {
            System.out.println(prefix + " Error while performing the inherited-by class check: ");
            e.printStackTrace();
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

            Set<String> difference = new HashSet<>(xml);
            difference.removeAll(html);
            result = xml.size() == html.size() && difference.size() == 0;
        }
    }

    public static InheritedByResult checkInheritedBy(Path fileName) throws IOException, ParserConfigurationException, SAXException, XPathExpressionException, SaxonApiException {
        System.out.println(fileName);

        // Initialise Saxon's S9 API and load the local XML file.
        Processor processor = new Processor(false);
        XdmNode xdm = processor.newDocumentBuilder().build(new StreamSource(new FileReader(fileName.toFile())));
        XPathCompiler compiler = processor.newXPathCompiler();
        compiler.declareNamespace("db", "http://docbook.org/ns/docbook");
        compiler.declareNamespace("xlink", "http://www.w3.org/1999/xlink");

        // If the local file is not a class, the test is passed (only for classes).
        boolean isClass = compiler.evaluate("//db:classsynopsis", xdm).size() > 0;
        if (! isClass) {
            return new InheritedByResult(true);
        }

        // Find the inherited-by classes.
        XdmValue inheritedByListXML = compiler.evaluate("//db:classsynopsisinfo[@role='inheritedBy']/text()", xdm);

        Set<String> inheritedBySetXML = new HashSet<>();
        for (int i = 0; i < inheritedByListXML.size(); ++i) {
            inheritedBySetXML.add(inheritedByListXML.itemAt(i).getStringValue());
        }

        // Load the remote HTML.
        Document html = Jsoup.connect("http://doc.qt.io/qt-5/" + fileName.getFileName().toString().replace(".qdt", "") + ".html").get();
        Elements inheritedByTagHTML = html.getElementsContainingText("Inherited By:");
        Set<String> inheritedBySetHTML = new HashSet<>();

        if (inheritedByTagHTML.size() > 0) {
            Elements inheritedByListHTML = html.getElementsContainingText("Inherited By:").get(0).firstElementSibling().getElementsByTag("a");
            for (Element e : inheritedByListHTML) {
                inheritedBySetHTML.add(e.text());
            }
        }

        // Compare.
        return new InheritedByResult(inheritedBySetXML, inheritedBySetHTML);
    }
}
