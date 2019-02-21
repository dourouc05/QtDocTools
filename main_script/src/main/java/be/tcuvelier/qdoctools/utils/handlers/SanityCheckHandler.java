package be.tcuvelier.qdoctools.utils.handlers;

import net.sf.saxon.s9api.*;

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

    public boolean performSanityCheck() throws SaxonApiException {
        // No list within a paragraph (Word-to-XML outputs them outside the paragraph).
        XdmValue listInPara = xpath("//db:para/db:itemizedlist union //para/itemizedlist");
        if (listInPara.size() > 0) {
            System.out.println("SANITY CHECK: found " + listInPara.size() + " list(s) within paragraphs: ");
            for (XdmValue v: listInPara) {
                System.out.println("- at line " + ((XdmNode) v).getLineNumber() + ", " +
                        "column " + ((XdmNode) v).getColumnNumber());
            }
            return false;
        }

        return true;
    }
}
