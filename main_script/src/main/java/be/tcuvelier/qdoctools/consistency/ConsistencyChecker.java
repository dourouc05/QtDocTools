package be.tcuvelier.qdoctools.consistency;

import net.sf.saxon.s9api.*;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class ConsistencyChecker {
    final ConsistencyResults cr;
    final String logPrefix;
    final Processor processor;
    final Path docbookFile;
    final Path htmlFile;
    final XdmNode xdm;
    final Document html;
    final XPathCompiler compiler;

    public ConsistencyChecker(String logPrefix, Processor processor, Path docbookFile,
            Path htmlFile, XdmNode xdm, Document html, XPathCompiler compiler) {
        this.cr = ConsistencyResults.fromNoError();
        this.logPrefix = logPrefix;
        this.processor = processor;
        this.docbookFile = docbookFile;
        this.htmlFile = htmlFile;
        this.xdm = xdm;
        this.html = html;
        this.compiler = compiler;
    }

    XdmValue xpath(final String expression, final XdmNode xdm) {
        try {
            return compiler.evaluate(expression, xdm);
        } catch (SaxonApiException e) {
            throw new RuntimeException(e);
        }
    }

    Set<String> xpathToSet(String expression, final XdmNode xdm) {
        XdmValue xml = xpath(expression, xdm);
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

    Set<String> htmlToSet(String expression, String tag, String anchor, Document html) {
        return htmlToSet(expression, tag, anchor, false, html);
    }

    Set<String> htmlToSet(String expression, String tag, String anchor, boolean enumMode, Document html) {
        return htmlToSet(expression, tag, anchor, enumMode, (s) -> true, html);
    }

    Set<String> htmlToSet(String expression, String tag, String anchor, boolean enumMode,
            Predicate<String> firstColumn, Document html) {
        Elements selectedPart = html.getElementsContainingText(expression);
        assert selectedPart != null;
        Elements elements = selectedPart.select(tag + (anchor.isEmpty() ? "" : ("#" + anchor)));
        assert elements != null;

        Set<String> set = new HashSet<>();
        if (elements.size() > 0) {
            // For flags: one enumeration is followed by flags (same name, just in the plural;
            // more robust test:
            // same links), but only one documentation entry for the two.
            String previousLink = "";
            Element nextSibling = elements.get(0).nextElementSibling();
            assert nextSibling != null;
            Elements propertiesListHTML = nextSibling.getElementsByTag("a");
            assert propertiesListHTML != null;

            for (Element e : propertiesListHTML) {
                boolean toConsider = true;

                // If there is a previous element and this one just adds an s (enum then flags),
                // skip; otherwise, keep.
                if (enumMode) {
                    if (previousLink != null && !previousLink.isEmpty() &&
                            Objects.equals(e.attr("href"), previousLink)) {
                        toConsider = false;
                    }
                    previousLink = e.attr("href");
                }

                // Imposed first column content.
                if (toConsider && e.parent() != null && e.parent().parent() != null &&
                        e.parent().parent().previousElementSibling() != null &&
                        e.parent().parent().previousElementSibling().text() != null) {
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

    boolean isClass(XdmNode xdm) {
        return xpath("//db:classsynopsis", xdm).size() > 0;
    }

    public void perform(Function<ConsistencyChecker, ConsistencyCheckResults> checkerMethod) {
        ConsistencyCheckResults cr = checkerMethod.apply(this);
        if (cr.hasErrors()) {
            System.out.println(cr.describeSetDifferences(logPrefix, docbookFile.toString()));
        }
    }
}
