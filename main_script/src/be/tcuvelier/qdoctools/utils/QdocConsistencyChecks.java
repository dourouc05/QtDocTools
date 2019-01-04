package be.tcuvelier.qdoctools.utils;

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
import java.util.*;
import java.util.function.Predicate;

public class QdocConsistencyChecks {
    private static <T> Set<T> setDifference(Set<T> a, Set<T> b) {
        Set<T> difference = new HashSet<>(a);
        difference.removeAll(b);
        return difference;
    }

    private static <T> Set<T> difference(Set<T> a, Set<T> b) {
        Set<T> forward = setDifference(a, b);
        forward.addAll(setDifference(b, a));
        return forward;
    }

    @SafeVarargs
    private static <T> Set<T> union(Set<T> a, Set<T>... lb) {
        Set<T> result = new HashSet<>(a);
        for (Set<T> b: lb) {
            result.addAll(b);
        }
        return result;
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

        Set<String> xpathToSet(String expression) throws SaxonApiException {
            XdmValue xml = xpath(expression);
            Set<String> set = new HashSet<>();
            for (int i = 0; i < xml.size(); ++i) {
                String element = xml.itemAt(i).getStringValue();
                if (! element.contains("::")) {
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

        Set<String> htmlToSet(String expression, String tag, String anchor, boolean enumMode, Predicate<String> firstColumn) {
            Elements html = this.html.getElementsContainingText(expression).select(tag + (anchor.isEmpty()? "" : ("#" + anchor)));
            Set<String> set = new HashSet<>();
            if (html.size() > 0) {
                // For flags: one enumeration is followed by flags (same name, just in the plural; more robust test:
                // same links), but only one documentation entry for the two.
                String previousLink = "";
                Elements propertiesListHTML = html.get(0).nextElementSibling().getElementsByTag("a");
                for (Element e : propertiesListHTML) {
                    boolean toConsider = true;

                    // If there is a previous element and this one just adds an s (enum then flags), skip; otherwise, keep.
                    if (enumMode) {
                        if (!previousLink.isEmpty() && e.attr("href").equals(previousLink)) {
                            toConsider = false;
                        }
                        previousLink = e.attr("href");
                    }

                    // Imposed first column content.
                    if (toConsider && e.parent().parent().previousElementSibling() != null) {
                        toConsider = firstColumn.test(e.parent().parent().previousElementSibling().text());
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
            //                                                                                   ^^^^^^^^^^^^^^^^^^^^^^^^
            //                                                                                   Differences with htmlToSet()
            for (Element e : inheritedByListHTML) {
                inheritedBySetHTML.add(e.text());
            }
        }

        // Compare.
        return new InheritedByResult(inheritedBySetXML, inheritedBySetHTML);
    }

    public static class ItemsResult {
        private final Map<String, Set<String>> xmls;
        private final Map<String, Set<String>> htmls;
        private final Map<String, Boolean> results;

        ItemsResult() {
            xmls = new HashMap<>();
            htmls = new HashMap<>();
            results = new HashMap<>();
        }

        void addComparison(String name, Set<String> xml, Set<String> html) {
            xmls.put(name, xml);
            htmls.put(name, html);
            results.put(name,compareSets(xml, html));
        }

        public boolean result() {
            for (Boolean v: results.values()) {
                if (! v) {
                    return false;
                }
            }
            return true;
        }

        public Set<String> tests() {
            return results.keySet();
        }

        public Set<String> getXML(String name) {
            return xmls.get(name);
        }

        public Set<String> getHTML(String name) {
            return htmls.get(name);
        }

        public boolean getResult(String name) {
            return results.get(name);
        }
    }

    public static ItemsResult checkItems(CheckRequest request) throws SaxonApiException {
        // If the local file is not a class, the test is passed (only for classes).
        if (! request.isClass()) {
            return new ItemsResult();
        }

        ItemsResult ir = new ItemsResult();
        ir.addComparison("Types",
                request.xpathToSet("//(db:enumsynopsis/db:enumname union db:typedefsynopsis[not(preceding-sibling::*[1][self::db:enumsynopsis])]/db:typedefname)/text()"),
                union(
                        request.htmlToSet("Public Types", "h2", "public-types", true),
                        request.htmlToSet("Related Non-Members", "h2", "related-non-members", false, s -> s.equals("typedef"))
                )
        );
        ir.addComparison("Properties",
                request.xpathToSet("//db:fieldsynopsis/db:varname/text()"),
                request.htmlToSet("Properties", "h2", "properties")
        );
        ir.addComparison("Public functions",
                request.xpathToSet("//(db:methodsynopsis[not(db:modifier[text() = 'signal'])] union db:constructorsynopsis union db:destructorsynopsis)/db:methodname/text()"), // Methods, constructors, destructors.
                union(
                        request.htmlToSet("Public Functions", "h2", "public-functions"),
                        request.htmlToSet("Public Slots", "h2", "public-slots"),
                        request.htmlToSet("Signals", "h2", "signals"),
                        request.htmlToSet("Reimplemented Public Functions", "h2", "reimplemented-public-functions"), // Example: http://doc.qt.io/qt-5/q3dcamera.html
                        request.htmlToSet("Reimplemented Protected Functions", "h2", "reimplemented-protected-functions"), // Example: http://doc.qt.io/qt-5/qabstractanimation.html#event
                        request.htmlToSet("Static Public Members", "h2", "static-public-members"), // Example: https://doc.qt.io/qt-5.11/q3dscene.html
                        request.htmlToSet("Protected Functions", "h2", "protected-functions"), // Example: https://doc.qt.io/qt-5.11/q3dobject.html
                        request.htmlToSet("Related Non-Members", "h2", "related-non-members", false, s -> ! s.equals("typedef")) // Example: http://doc.qt.io/qt-5/qopengldebugmessage.html http://doc.qt.io/qt-5/qxmlstreamnotationdeclaration.html
                )
        );
        ir.addComparison("Signals", // TODO: What the heck, signals are also in the public functions?
                request.xpathToSet("//db:methodsynopsis[db:modifier[text() = 'signal']]/db:methodname/text()"),
                request.htmlToSet("Signals", "h2", "signals")
        );
//        ir.addComparison("Public variables",
//                request.xpathToSet("//db:fieldsynopsis/db:varname/text()"), // Example: http://doc.qt.io/qt-5/qstyleoptionrubberband.html
//                request.htmlToSet("Public Types", "h2", "public-variables")
//        );
        // TODO: Public functions, like https://doc.qt.io/qt-5.11/qopenglfunctions-1-0.html
        // TODO: Static public members, like http://doc.qt.io/qt-5/qxmlinputsource.html#static-public-members
        // TODO: Protected slots, like http://doc.qt.io/qt-5/qmdiarea.html#protected-slots

        return ir;
    }
}
