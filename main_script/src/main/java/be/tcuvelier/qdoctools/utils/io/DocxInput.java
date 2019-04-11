package be.tcuvelier.qdoctools.utils.io;

import org.apache.poi.xwpf.usermodel.*;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.*;
import java.util.List;

public class DocxInput {
    public static void main(String[] args) throws IOException, XMLStreamException {
        System.out.println(new DocxInput("test.docx").toDocBook());
//        XWPFDocument doc = new XWPFDocument(new FileInputStream("test.docx"));
//
//        for (IBodyElement b: doc.getBodyElements()) {
//            System.out.println(b.getPartType());
//        }
    }

    private XWPFDocument doc;
    private XMLStreamWriter xmlStream;

    private int currentLevel;

    private static String docbookNS = "http://docbook.org/ns/docbook";
    private static String xlinkNS = "http://www.w3.org/1999/xlink";

    public DocxInput(String filename) throws IOException {
        doc = new XWPFDocument(new FileInputStream(filename));
    }

    public String toDocBook() throws IOException, XMLStreamException {
        // Initialise the XML stream.
        StringWriter stringWriter = new StringWriter();
        xmlStream = XMLOutputFactory.newInstance().createXMLStreamWriter(stringWriter);

        // Generate the document.
        xmlStream.writeStartDocument();
        xmlStream.writeStartElement("db", "article", docbookNS);

        xmlStream.setPrefix("db", docbookNS);
        xmlStream.writeNamespace("db", docbookNS);
        xmlStream.setPrefix("xlink", xlinkNS);
        xmlStream.writeNamespace("xlink", xlinkNS);

        currentLevel = 0;
        for (IBodyElement b: doc.getBodyElements()) {
            visit(b);
        }

        xmlStream.writeEndElement();
        xmlStream.writeEndDocument();

        // Generate the string from the stream.
        String xmlString = stringWriter.getBuffer().toString();
        stringWriter.close();
        return xmlString;
    }

    private void visit(IBodyElement b) throws XMLStreamException {
        // As the XWPF hierarchy is not made for visitors, and as it is not possible to alter it, use reflection...
        if (b instanceof XWPFParagraph) {
            visitParagraph((XWPFParagraph) b);
        } else if (b instanceof XWPFTable) {
            visitTable((XWPFTable) b);
        } else {
            System.out.println(b.getElementType());
//        throw new RuntimeException("An element has not been caught by a visit() method.");
        }
    }

    private void visitParagraph(XWPFParagraph p) throws XMLStreamException {
        if (p.getRuns().size() > 0) {
            switch (p.getStyleID()) {
                case "Title":
                    visitDocumentTitle(p);
                    break;
                case "Heading1":
                case "Heading2":
                case "Heading3":
                case "Heading4":
                case "Heading5":
                case "Heading6":
                case "Heading7":
                case "Heading8":
                case "Heading9":
                // TODO: Part, Chapter?
                    visitSectionTitle(p);
                    break;
                // TODO: Note, etc.?
                default:
                    xmlStream.writeStartElement(docbookNS, "para");
                    visitRuns(p.getRuns());
                    xmlStream.writeEndElement(); // </db:para>
                    break;
            }
        }
    }

    private void visitDocumentTitle(XWPFParagraph p) throws XMLStreamException {
        currentLevel = 0;
        xmlStream.writeStartElement(docbookNS, "info");
        xmlStream.writeStartElement(docbookNS, "title");
        visitRuns(p.getRuns());
        xmlStream.writeEndElement(); // </db:title>
        xmlStream.writeEndElement(); // </db:info>
    }

    private void visitSectionTitle(XWPFParagraph p) throws XMLStreamException {
        int level = Integer.parseInt(p.getStyleID().replace("Heading", ""));
        while (level <= currentLevel) {
            xmlStream.writeEndElement(); // </db:section>
            currentLevel -= 1;
        }

        currentLevel += 1;
        xmlStream.writeStartElement(docbookNS, "section");
        xmlStream.writeStartElement(docbookNS, "title");
        visitRuns(p.getRuns());
        xmlStream.writeEndElement(); // </db:title>
    }

    private void visitRuns(List<XWPFRun> runs) throws XMLStreamException {
        for (XWPFRun r: runs) {
            xmlStream.writeCharacters(r.text());
        }
    }

    private void visitTable(XWPFTable t) throws XMLStreamException {
        xmlStream.writeStartElement(docbookNS, "table");
        xmlStream.writeEndElement(); // </db:table>
    }
}
