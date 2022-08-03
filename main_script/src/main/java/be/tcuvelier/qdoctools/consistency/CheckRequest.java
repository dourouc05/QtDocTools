package be.tcuvelier.qdoctools.consistency;

import be.tcuvelier.qdoctools.core.utils.QtVersion;
import net.sf.saxon.s9api.*;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.xml.transform.stream.StreamSource;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class CheckRequest {
    final Document html;
    private final XdmNode xdm;
    private final XPathCompiler compiler;

    public CheckRequest(Path fileName, QtVersion qtVersion) throws IOException, SaxonApiException {
        Processor processor = new Processor(false);
        xdm = processor.newDocumentBuilder().build(new StreamSource(new FileReader(fileName.toFile())));
        compiler = processor.newXPathCompiler();
        compiler.declareNamespace("db", "http://docbook.org/ns/docbook");
        compiler.declareNamespace("xlink", "http://www.w3.org/1999/xlink");

        String otherFile = fileName.getFileName().toString().replace(".xml", ".html");
        html = Jsoup.connect("https://doc.qt.io/qt-" + qtVersion.QT_VER() + "/" + otherFile).get();
    }

    XdmValue xpath(String expression) throws SaxonApiException {
        return compiler.evaluate(expression, xdm);
    }

    Set<String> xpathToSet(String expression) throws SaxonApiException {
        XdmValue xml = xpath(expression);
        Set<String> set = new HashSet<>();
        for (int i = 0; i < xml.size(); ++i) {
            String element = xml.itemAt(i).getStringValue();
            if (!element.contains("::")) {
                set.add(element);
            } else {
                set.add(element.split("::")[1]);
            }
        }
        return set;
    }

    Set<String> htmlToSet(String expression, String tag, String anchor) {
        return htmlToSet(expression, tag, anchor, false);
    }

    Set<String> htmlToSet(String expression, String tag, String anchor, boolean enumMode) {
        return htmlToSet(expression, tag, anchor, enumMode, (s) -> true);
    }

    Set<String> htmlToSet(String expression, String tag, String anchor, boolean enumMode,
            Predicate<String> firstColumn) {
        Elements html =
                this.html.getElementsContainingText(expression).select(tag + (anchor.isEmpty() ?
                        "" : ("#" + anchor)));
        Set<String> set = new HashSet<>();
        if (html.size() > 0) {
            // For flags: one enumeration is followed by flags (same name, just in the plural;
            // more robust test:
            // same links), but only one documentation entry for the two.
            String previousLink = "";
            Elements propertiesListHTML = html.get(0).nextElementSibling().getElementsByTag("a");
            for (Element e : propertiesListHTML) {
                boolean toConsider = true;

                // If there is a previous element and this one just adds an s (enum then flags),
                // skip; otherwise, keep.
                if (enumMode) {
                    if (!previousLink.isEmpty() && e.attr("href").equals(previousLink)) {
                        toConsider = false;
                    }
                    previousLink = e.attr("href");
                }

                // Imposed first column content.
                if (toConsider && e.parent().parent().previousElementSibling() != null) {
                    toConsider =
                            firstColumn.test(e.parent().parent().previousElementSibling().text());
                }

                // Once all tests are performed, consider to add this element to the set.
                if (toConsider) {
                    set.add(e.text());
                }
            }
        }
        return set;
    }

    boolean isClass() throws SaxonApiException {
        return xpath("//db:classsynopsis").size() > 0;
    }
}
