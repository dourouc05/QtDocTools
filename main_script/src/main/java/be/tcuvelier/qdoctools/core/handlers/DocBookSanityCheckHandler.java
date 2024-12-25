package be.tcuvelier.qdoctools.core.handlers;

import net.sf.saxon.s9api.*;

import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DocBookSanityCheckHandler {
    private final static Set<String> codeSynopsisNames =
            Stream.of("classsynopsis", "b").collect(Collectors.toSet());
    private final XdmNode xdm;
    private final XPathCompiler compiler;

    public DocBookSanityCheckHandler(String fileName) throws FileNotFoundException,
            SaxonApiException {
        Processor processor = new Processor(false);
        DocumentBuilder db = processor.newDocumentBuilder();
        db.setLineNumbering(true);
        xdm = db.build(new StreamSource(new FileReader(fileName)));
        compiler = processor.newXPathCompiler();
        compiler.declareNamespace("db", "http://docbook.org/ns/docbook");
        compiler.declareNamespace("xlink", "http://www.w3.org/1999/xlink");
    }

    private static void signalElement(XdmValue v) {
        System.out.println("- at line " + ((XdmNode) v).getLineNumber() + ", " +
                "column " + ((XdmNode) v).getColumnNumber() + ": " +
                "the culprit is a " + ((XdmNode) v).getNodeName() + " tag");
    }

    private static void signalElements(XdmValue lv) {
        for (XdmValue v : lv) {
            signalElement(v);
        }
    }

    private XdmValue xpath(String expression) throws SaxonApiException {
        return compiler.evaluate(expression, xdm);
    }

    public boolean performSanityCheck() throws SaxonApiException {
        // Accumulate the negative results. Along the way, show as many things as possible.
        // Not all checks update this value: only if there is a high risk of content loss. If
        // something may be
        // represented in a similar but different way, just warn and don't update it.
        boolean result = true;
        boolean isBook = false; // Some checks depend on this state.

        // Articles and books are supported.
        {
            XdmNode rootElement = (XdmNode) xpath("/*").itemAt(0);
            String rootName = rootElement.getNodeName().toString();
            if (rootName.contains("book")) {
                isBook = true;
                System.out.println("SANITY WARNING: books have a very different DvpML output, " +
                        "incompatible with " +
                        "DocBook round-tripping. Other tools should still maintain semantics, " +
                        "though.");
                // No change in result: this is just a warning.
            } else if (!rootName.contains("article")) {
                System.out.println("SANITY CHECK: unknown root tag: " + rootName + ". Is this " +
                        "file DocBook?");
                result = false;
            }
        }

        // No list within a paragraph (Word-to-XML outputs them outside the paragraph).
        {
            XdmValue listInPara = xpath("//db:para/db:itemizedlist union //para/itemizedlist");
            if (!listInPara.isEmpty()) {
                System.out.println("SANITY WARNING: found " + listInPara.size() + " list(s) " +
                        "within paragraphs: ");
                signalElements(listInPara);
                // No change in result: this is just a warning.
            }
        }

        // No block of code within a paragraph (Word-to-XML outputs them outside the paragraph).
        {
            XdmValue codeInPara = xpath(
                    "//db:para/db:programlisting union //para/programlisting " +
                            "union //db:para/db:screen union //para/screen"
            );
            if (!codeInPara.isEmpty()) {
                System.out.println("SANITY WARNING: found " + codeInPara.size() + " code blocks" +
                        "(s) within paragraphs: ");
                signalElements(codeInPara);
                // No change in result: this is just a warning.
            }
        }

        // No text before the first section (would be included in the abstract).
        // Code synopses will be lost, but can easily be retrieved.
        {
            String forbiddingChildren = "not(self::section)";
            String forbiddingChildrenNS = "not(self::db:section)";
            if (isBook) {
                forbiddingChildren += " and not(self::chapter)";
                forbiddingChildrenNS += " and not(self::db:chapter)";
            }
            XdmValue textAfterInfo = xpath(
                    "/*/info[1]/following-sibling::*[" + forbiddingChildren + "] " +
                            "union /*/db:info[1]/following-sibling::*[" + forbiddingChildrenNS + "]"
            );

            // Split the list into two parts: the elements that are recoverable with
            // post-processing (not really a
            // problem, as long as you are aware of it), and those that should definitely not be
            // here.
            Predicate<XdmValue> isCode =
                    (XdmValue n) -> codeSynopsisNames.contains(((XdmNode) n).getNodeName().getLocalName());
            List<XdmValue> warnings =
                    textAfterInfo.stream().filter(isCode).collect(Collectors.toList());
            List<XdmValue> errors =
                    textAfterInfo.stream().filter(Predicate.not(isCode)).collect(Collectors.toList());

            if (!warnings.isEmpty()) {
                System.out.println("SANITY WARNING: found " + textAfterInfo.size() + " block " +
                        "elements between " +
                        "the title and the main content (all content should be either in the " +
                        "abstract or " +
                        "in a section), but these may be recovered by post-processing (merge " +
                        "subcommand, " +
                        "AFTER_PROOFREADING mode): ");
                signalElements(textAfterInfo);
                // No change in result: this is just a warning.
            }

            if (!errors.isEmpty()) {
                System.out.println("SANITY CHECK: found " + textAfterInfo.size() + " block " +
                        "elements between the title " +
                        "and the main content (all content should be either in the abstract or in" +
                        " a section): ");
                signalElements(textAfterInfo);
                result = false;
            }
        }

        // CALS tables will be converted to HTML tables after the round trip.
        {
            XdmValue calsTable = xpath(
                    "//informaltable/(row, entry) union //table/(row, entry)" +
                            "union //db:informaltable/(db:row, db:entry) union //db:table/" +
                            "(db:row, db:entry)"
            );
            if (!calsTable.isEmpty()) {
                System.out.println("SANITY WARNING: found " + calsTable.size() + " CALS tables, " +
                        "which will be converted to HTML tables after" +
                        "round tripping: ");
                signalElements(calsTable);
                // No change in result: this is just a warning.
            }
        }

        // Tables with just one column are understood as <simplelist>s.
        {
            XdmValue nColumns = xpath(
                    "//informaltable[not(/tbody/tr/count(td) > 1)] " +
                            "union //db:informaltable[not(/db:tbody/db:tr/count(db:td) > 1)]"
            );
            if (!nColumns.isEmpty()) {
                System.out.println("SANITY WARNING: found " + nColumns.size() + " tables with " +
                        "just one column, which will be " +
                        "converted to simple lists after round tripping: ");
                signalElements(nColumns);
                // No change in result: this is just a warning.
            }
        }

        return result;
    }
}
