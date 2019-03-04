package be.tcuvelier.qdoctools.utils.handlers;

import net.sf.saxon.s9api.*;
import net.sf.saxon.trans.XPathException;

import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;

public class SanityCheckHandler {
    private final XdmNode xdm;
    private final XPathCompiler compiler;

    public SanityCheckHandler(String fileName) throws FileNotFoundException, SaxonApiException {
        Processor processor = new Processor(false);
        DocumentBuilder db = processor.newDocumentBuilder();
        db.setLineNumbering(true);
        xdm = db.build(new StreamSource(new FileReader(new File(fileName))));
        compiler = processor.newXPathCompiler();
        compiler.declareNamespace("db", "http://docbook.org/ns/docbook");
        compiler.declareNamespace("xlink", "http://www.w3.org/1999/xlink");
    }

    private XdmValue xpath(String expression) throws SaxonApiException {
        return compiler.evaluate(expression, xdm);
    }

    private static void signalElement(XdmValue v) {
        System.out.println("- at line " + ((XdmNode) v).getLineNumber() + ", " +
                "column " + ((XdmNode) v).getColumnNumber() + ": " +
                "the culprit is a " + ((XdmNode) v).getNodeName() + " tag");
    }

    public boolean performSanityCheck() throws SaxonApiException {
        // Accumulate the results and show as many things as possible.
        boolean result = true;

        // Only articles are implemented for now.
        // TODO: Allow books at some point. Very unlikely that other root elements will ever be supported.
        {
            XdmNode rootElement = (XdmNode) xpath("/*").itemAt(0);
            String rootName = rootElement.getNodeName().toString();
            if (rootName.contains("book")) {
                System.out.println("SANITY CHECK: books are not yet implemented");
                result = false;
            } else if (! rootName.contains("article")) {
                System.out.println("SANITY CHECK: unknown root tag: " + rootName);
                result = false;
            }
        }

        // No list within a paragraph (Word-to-XML outputs them outside the paragraph).
        {
            XdmValue listInPara = xpath("//db:para/db:itemizedlist union //para/itemizedlist");
            if (listInPara.size() > 0) {
                System.out.println("SANITY CHECK: found " + listInPara.size() + " list(s) within paragraphs: ");
                for (XdmValue v : listInPara) {
                    signalElement(v);
                }
                result = false;
            }
        }

        // No block of code within a paragraph (Word-to-XML outputs them outside the paragraph).
        {
            XdmValue codeInPara = xpath(
                    "//db:para/db:programlisting union //para/programlisting " +
                            "union //db:para/db:screen union //para/screen"
            );
            if (codeInPara.size() > 0) {
                System.out.println("SANITY CHECK: found " + codeInPara.size() + " code blocks(s) within paragraphs: ");
                for (XdmValue v : codeInPara) {
                    signalElement(v);
                }
                result = false;
            }
        }

        // No text before the first section (would be included in the abstract).
        {
            XdmValue textAfterInfo = xpath(
                    "/*/info[1]/following-sibling::*[not(self::section)] " +
                            "union /*/db:info[1]/following-sibling::*[not(self::db:section)]"
            );
            if (textAfterInfo.size() > 0) {
                System.out.println("SANITY CHECK: found " + textAfterInfo.size() + " block elements between the title " +
                        "and the main content (all content should be either in the abstract or in a section): ");
                for (XdmValue v : textAfterInfo) {
                    signalElement(v);
                }
                result = false;
            }
        }

        // CALS tables will be converted to HTML tables after the round trip.
        {
            XdmValue calsTable = xpath(
                    "//informaltable/(row, entry) union //table/(row, entry)" +
                            "union //db:informaltable/(db:row, db:entry) union //db:table/(db:row, db:entry)"
            );
            if (calsTable.size() > 0) {
                System.out.println("SANITY CHECK: found CALS tables, which will be converted to HTML tables after" +
                        "round tripping: ");
                for (XdmValue v : calsTable) {
                    signalElement(v);
                }
                result = false;
            }
        }

        // Tables with just one column are understood as <simplelist>s.
        {
            XdmValue nColumns = xpath("//informaltable[not(/tbody/tr/count(td) > 1)]");
            XdmValue nColumnsNS = xpath("//db:informaltable[not(/db:tbody/db:tr/count(db:td) > 1)]");

            try {
                boolean printedIntro = false;

                for (XdmValue sequences : new XdmValue[]{nColumns, nColumnsNS}) {
                    for (XdmValue v : sequences) {
                        if (v.getUnderlyingValue().effectiveBooleanValue()) {
                            if (! printedIntro) {
                                System.out.println("SANITY CHECK: found tables with just one column, which will be " +
                                        "converted to simple lists after round tripping: ");
                                printedIntro = true;
                            }

                            signalElement(v);
                        }
                    }
                }
            } catch (XPathException e) {
                // Due to the design of the XPath query, this exception shall never be thrown.
                e.printStackTrace();
            }
        }

        return result;
    }
}