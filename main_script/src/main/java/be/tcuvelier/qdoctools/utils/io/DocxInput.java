package be.tcuvelier.qdoctools.utils.io;

import org.apache.poi.xwpf.usermodel.*;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DocxInput {
    public static void main(String[] args) throws IOException, XMLStreamException {
        System.out.println(new DocxInput("test.docx").toDocBook());
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

        // Wrap the writer inside an indenting proxy.
        // Copied from https://ewernli.wordpress.com/2009/06/18/stax-pretty-printer/.
        xmlStream = (XMLStreamWriter) Proxy.newProxyInstance(
                XMLStreamWriter.class.getClassLoader(),
                new Class[] {XMLStreamWriter.class},
                new PrettyPrintHandler(xmlStream)
        );

        // Generate the document: root, prefixes, content, then close the sections that should be.
        xmlStream.writeStartDocument();
        xmlStream.writeCharacters("\n");
        xmlStream.writeStartElement("db", "article", docbookNS);

        xmlStream.setPrefix("db", docbookNS);
        xmlStream.writeNamespace("db", docbookNS);
        xmlStream.setPrefix("xlink", xlinkNS);
        xmlStream.writeNamespace("xlink", xlinkNS);
        xmlStream.writeCharacters("\n");

        currentLevel = 0;
        for (IBodyElement b: doc.getBodyElements()) {
            visit(b);
        }

//        while (1 >= currentLevel) {
//            xmlStream.writeEndElement(); // </db:section>
//            currentLevel -= 1;
//        }

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
        // Called only once, at tbe beginning of the document. This function is thus also responsible for the main
        // <db:info> tag.
        currentLevel = 0;
        xmlStream.writeStartElement(docbookNS, "info");
        xmlStream.writeStartElement(docbookNS, "title");
        visitRuns(p.getRuns());
//        xmlStream.writeEndElement(); // </db:title>
        // TODO: What about the abstract?


        xmlStream.writeStartElement(docbookNS, "emphasis");
        xmlStream.writeCharacters("bold");
        xmlStream.writeEndElement();
        xmlStream.writeEndElement();

        xmlStream.writeEndElement(); // </db:info>
    }

    private void visitSectionTitle(XWPFParagraph p) throws XMLStreamException {
        // Pop sections until the current level is reached.
        int level = Integer.parseInt(p.getStyleID().replace("Heading", ""));
        while (level >= currentLevel) {
            xmlStream.writeEndElement(); // </db:section>
            currentLevel -= 1;
        }

        // Deal with this section.
        currentLevel += 1;
        xmlStream.writeStartElement(docbookNS, "section");
        xmlStream.writeStartElement(docbookNS, "title");
        visitRuns(p.getRuns());
        xmlStream.writeEndElement(); // </db:title>
        xmlStream.writeCharacters("\n");

        // TODO: Implement a check on the currentLevel and the level (in case someone missed a level in the headings).
    }

    private void visitRuns(List<XWPFRun> runs) throws XMLStreamException {
        // TODO: maybe implement simplifications if two runs have the same set of formattings.

        // Copied from STVerticalAlignRun.Enum. TODO: Better way?
        int INT_SUPERSCRIPT = 2;
        int INT_SUBSCRIPT = 3;

        for (XWPFRun r: runs) {
            // Formatting tags (maybe several ones to add!).
            if (r.isBold()) {
                xmlStream.writeStartElement(docbookNS, "emphasis");
                xmlStream.writeAttribute(docbookNS, "role", "bold"); // TODO: Check if bold is used everywhere else
            }
            if (r.isItalic()) {
                xmlStream.writeStartElement(docbookNS, "emphasis");
            }
            if (r.getUnderline() != UnderlinePatterns.NONE) {
                xmlStream.writeStartElement(docbookNS, "emphasis");
                xmlStream.writeAttribute(docbookNS, "role", "underline"); // TODO: Check if underline is used everywhere else
            }
            if (r.isStrikeThrough() || r.isDoubleStrikeThrough()) {
                xmlStream.writeStartElement(docbookNS, "emphasis");
                xmlStream.writeAttribute(docbookNS, "role", "strikethrough");
            }
            if (r.getVerticalAlignment().intValue() == INT_SUPERSCRIPT) {
                xmlStream.writeStartElement(docbookNS, "superscript");
            }
            if (r.getVerticalAlignment().intValue() == INT_SUBSCRIPT) {
                xmlStream.writeStartElement(docbookNS, "subscript");
            }
            if (r.getFontFamily() != null) {
                // TODO: Font family (if code).
            }

            // Actual text for this run.
            xmlStream.writeCharacters(r.text());

            // Close the tags if needed (strictly the reverse order from opening tags).
            if (r.getUnderline() != UnderlinePatterns.NONE) {
                xmlStream.writeEndElement(); // </db:emphasis> for underline
            }
            if (r.isItalic()) {
                xmlStream.writeEndElement(); // </db:emphasis> for italics
            }
            if (r.isBold()) {
                xmlStream.writeEndElement(); // </db:emphasis> for bold
            }
            if (r.isStrikeThrough() || r.isDoubleStrikeThrough()) {
                xmlStream.writeEndElement(); // </db:emphasis> for strikethrough
            }
            if (r.getVerticalAlignment().intValue() == INT_SUPERSCRIPT) {
                xmlStream.writeEndElement(); // </db:superscript>
            }
            if (r.getVerticalAlignment().intValue() == INT_SUBSCRIPT) {
                xmlStream.writeEndElement(); // </db:subscript>
            }
            if (r.getFontFamily() != null) {
                // TODO: Font family (if code).
            }
        }
    }

    private void visitTable(XWPFTable t) throws XMLStreamException {
        xmlStream.writeStartElement(docbookNS, "informaltable");

        // Output the table row per row, in HTML format.
        for (XWPFTableRow row: t.getRows()) {
            xmlStream.writeStartElement(docbookNS, "tr");

            for (XWPFTableCell cell: row.getTableCells()) {
                xmlStream.writeStartElement(docbookNS, "td");
                xmlStream.writeEndElement(); // </db:td>
            }

            xmlStream.writeEndElement(); // </db:tr>
        }

        xmlStream.writeEndElement(); // </db:informaltable>
    }

    // Copied from https://ewernli.wordpress.com/2009/06/18/stax-pretty-printer/
    private static class PrettyPrintHandler implements InvocationHandler {
        private final XMLStreamWriter target;
        private int depth = 0;
        private final Map<Integer, Boolean> hasChildElement = new HashMap<>();
        private static final String INDENT = " ";
        private static final String LINEFEED = "\n";

        public PrettyPrintHandler(XMLStreamWriter t) {
            target = t;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String m = method.getName();

            // Needs to be BEFORE the actual event, so that for instance the
            // sequence writeStartElem, writeAttr, writeStartElem, writeEndElem, writeEndElem
            // is correctly handled
            if ("writeStartElement".equals(m)) {
                // update state of parent node
                if (depth > 0) {
                    hasChildElement.put(depth - 1, true);
                }
                // reset state of current node
                hasChildElement.put(depth, false);
                // indent for current depth
                target.writeCharacters(LINEFEED);
                target.writeCharacters(repeat(depth));
                depth++;
            }
            else if ("writeEndElement".equals(m)) {
                depth--;
                if (hasChildElement.get(depth)) {
                    target.writeCharacters(LINEFEED);
                    target.writeCharacters(repeat(depth));
                }
            }
            else if ("writeEmptyElement".equals(m)) {
                // update state of parent node
                if (depth > 0) {
                    hasChildElement.put(depth - 1, true);
                }
                // indent for current depth
                target.writeCharacters(LINEFEED);
                target.writeCharacters(repeat(depth));
            }
            method.invoke(target, args);
            return null;
        }

        private static String repeat(int d) {
            return PrettyPrintHandler.INDENT.repeat(d);
        }
    }
}
