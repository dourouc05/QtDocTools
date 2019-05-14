package be.tcuvelier.qdoctools.utils.io;

import be.tcuvelier.qdoctools.cli.MainCommand;
import org.apache.poi.xwpf.usermodel.*;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class DocxInput {
    public static void main(String[] args) throws IOException, XMLStreamException {
//        String test = "basic";
//        String test = "sections";
//        String test = "sections_bogus";
        String test = "images";

        String docBook = new DocxInput(MainCommand.fromDocxTests + "synthetic/" + test + ".docx").toDocBook();

        System.out.println(docBook);
//        Files.write(Paths.get(MainCommand.fromDocxTests + "synthetic/" + test + ".xml"), docBook.getBytes());
    }

    private XWPFDocument doc;
    private XMLStreamWriter xmlStream;

    private int currentDepth;
    private int currentSectionLevel;

    private static String indentation = "  ";
    private static String docbookNS = "http://docbook.org/ns/docbook";
    private static String xlinkNS = "http://www.w3.org/1999/xlink";

    public DocxInput(String filename) throws IOException {
        doc = new XWPFDocument(new FileInputStream(filename));
    }

    private void writeIndent() throws XMLStreamException {
        xmlStream.writeCharacters(indentation.repeat(currentDepth));
    }

    private void increaseIndent() {
        currentDepth += 1;
    }

    private void decreaseIndent() {
        currentDepth -= 1;
    }

    private void writeNewLine() throws XMLStreamException {
        xmlStream.writeCharacters("\n");
    }

    public void toDocBook(String output) throws IOException, XMLStreamException {
        Files.write(Paths.get(output), toDocBook().getBytes());
    }

    @SuppressWarnings("WeakerAccess")
    public String toDocBook() throws IOException, XMLStreamException {
        // Initialise the XML stream.
        OutputStream writer = new ByteArrayOutputStream();
        xmlStream = XMLOutputFactory.newInstance().createXMLStreamWriter(writer, "UTF-8");

        // Initialise counters.
        currentDepth = 0;
        currentSectionLevel = 0;

        // Generate the document: root, prefixes, content, then close the sections that should be.
        xmlStream.writeStartDocument("UTF-8", "1.0");

        writeNewLine(); writeIndent();
        xmlStream.writeStartElement("db", "article", docbookNS);
        xmlStream.setPrefix("db", docbookNS);
        xmlStream.writeNamespace("db", docbookNS);
        xmlStream.setPrefix("xlink", xlinkNS);
        xmlStream.writeNamespace("xlink", xlinkNS);
        increaseIndent();

        writeNewLine();

        for (IBodyElement b: doc.getBodyElements()) {
            visit(b);
        }

        while (0 < currentSectionLevel) {
            decreaseIndent();
            writeIndent();
            xmlStream.writeEndElement(); // </db:section>
            writeNewLine();
            currentSectionLevel -= 1;
        }

        decreaseIndent(); // For consistency: this has no impact on the produced XML.
        xmlStream.writeEndElement();
        xmlStream.writeEndDocument();

        if (currentDepth != 0) {
            System.err.println("Reached the end of the document, but the indentation depth is not zero: " +
                    "there is a bug! Current depth: " + currentDepth);
        }

        // Generate the string from the stream.
        String xmlString = writer.toString();
        writer.close();
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
            throw new RuntimeException("An element has not been caught by a visit() method.");
        }
    }

    private void visitParagraph(XWPFParagraph p) throws XMLStreamException {
        if (p.getRuns().size() > 0) {
            if (p.getStyleID() == null) {
                visitNormalParagraph(p);
            } else {
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
                        visitNormalParagraph(p);
                        break;
                }
            }
        }
    }

    private void visitNormalParagraph(XWPFParagraph p) throws XMLStreamException {
        if (p.getRuns().size() == 1 && p.getRuns().get(0).getEmbeddedPictures().size() == 1) { // TODO: Several pictures per run? Seems unlikely.
            // This paragraph only contains an image, no need for a <db:para>, but rather a <db:mediaobject>.
            writeIndent();
            xmlStream.writeStartElement(docbookNS, "mediaobject");
            visitRuns(p.getRuns());
            // TODO: What about the caption? It may even be in another paragraph!
            xmlStream.writeEndElement(); // </db:mediaobject>
            writeNewLine();
        } else {
            // Normal case for a paragraph.
            writeIndent();
            xmlStream.writeStartElement(docbookNS, "para");
            visitRuns(p.getRuns());
            xmlStream.writeEndElement(); // </db:para>
            writeNewLine();
        }
    }

    private void visitDocumentTitle(XWPFParagraph p) throws XMLStreamException {
        // Called only once, at tbe beginning of the document. This function is thus also responsible for the main
        // <db:info> tag.
        currentSectionLevel = 0;

        writeIndent();
        xmlStream.writeStartElement(docbookNS, "info");
        increaseIndent();

        writeNewLine();
        writeIndent();

        xmlStream.writeStartElement(docbookNS, "title");
        visitRuns(p.getRuns());
        xmlStream.writeEndElement(); // </db:title>
        writeNewLine();

        // TODO: What about the abstract?

        decreaseIndent();
        writeIndent();
        xmlStream.writeEndElement(); // </db:info>
        writeNewLine();
    }

    private void visitSectionTitle(XWPFParagraph p) throws XMLStreamException {
        // Pop sections until the current level is reached.
        int level = Integer.parseInt(p.getStyleID().replace("Heading", ""));
        if (level > currentSectionLevel + 1) {
            System.err.println("A section of level " + level + " was found within a section of level " +
                    currentSectionLevel + " (for instance, a subsubsection within a section): it seems there is " +
                    "a bad of use section levels in the input document. " +
                    "You could get a bad output (invalid XML and/or exceptions) in some cases.");
        }
        while (level <= currentSectionLevel) {
            decreaseIndent();
            writeIndent();
            xmlStream.writeEndElement(); // </db:section>
            writeNewLine();
            currentSectionLevel -= 1;
        }

        // Deal with this section.
        currentSectionLevel += 1;

        writeIndent();
        xmlStream.writeStartElement(docbookNS, "section");
        increaseIndent();
        writeNewLine();

        writeIndent();
        xmlStream.writeStartElement(docbookNS, "title");
        visitRuns(p.getRuns());
        xmlStream.writeEndElement(); // </db:title>
        writeNewLine();

        // TODO: Implement a check on the currentSectionLevel and the level (in case someone missed a level in the headings).
    }

    private void visitRuns(List<XWPFRun> runs) throws XMLStreamException {
        // TODO: maybe implement simplifications if two runs have the same set of formattings.

        // Copied from STVerticalAlignRun.Enum. TODO: Better way?
        int INT_SUPERSCRIPT = 2;
        int INT_SUBSCRIPT = 3;

        for (XWPFRun r: runs) {
            // Maybe this run contains an inline image. Output a <db:inlinemediaobject> (whose beginning is on the same
            // line as the rest of the text; the inside part is indented normally; the closing tag is directly followed
            // by the rest of the text, if any).
            xmlStream.writeStartElement(docbookNS, "inlinemediaobject");
            writeNewLine();
            increaseIndent();
            visitPictureRun(r);
            decreaseIndent();
            writeNewLine();
            xmlStream.writeEndElement(); // </inlinemediaobject>

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

    private void visitPictureRun(XWPFRun r) throws XMLStreamException {
        writeIndent();
        xmlStream.writeStartElement(docbookNS, "imageobject");
        writeNewLine();
        increaseIndent();

        writeIndent();
        xmlStream.writeEmptyElement(docbookNS, "imagedata");
        xmlStream.writeAttribute(docbookNS, "fileref", "http://shit.com/image/not/found");
        writeNewLine();

        decreaseIndent();

        xmlStream.writeEndElement(); // </db:imageobject>
        writeNewLine();
    }

    private void visitTable(XWPFTable t) throws XMLStreamException {
        writeIndent();
        xmlStream.writeStartElement(docbookNS, "informaltable");
        increaseIndent();
        writeNewLine();

        // Output the table row per row, in HTML format.
        for (XWPFTableRow row: t.getRows()) {
            writeIndent();
            xmlStream.writeStartElement(docbookNS, "tr");
            increaseIndent();
            writeNewLine();

            for (XWPFTableCell cell: row.getTableCells()) {
                // Special case: an empty cell. One paragraph with zero runs.
                if (cell.getParagraphs().size() == 0 ||
                        (cell.getParagraphs().size() == 1 && cell.getParagraphs().get(0).getRuns().size() == 0)) {
                    writeIndent();
                    xmlStream.writeEmptyElement(docbookNS, "td");
                    writeNewLine();

                    continue;
                }

                // Normal case.
                writeIndent();
                xmlStream.writeStartElement(docbookNS, "td");
                increaseIndent();
                writeNewLine();

                for (XWPFParagraph p: cell.getParagraphs()){
                    visitParagraph(p);
                }

                decreaseIndent();
                writeIndent();
                xmlStream.writeEndElement(); // </db:td>
                writeNewLine();
            }

            decreaseIndent();
            writeIndent();
            xmlStream.writeEndElement(); // </db:tr>
            writeNewLine();
        }

        decreaseIndent();
        writeIndent();
        xmlStream.writeEndElement(); // </db:informaltable>
        writeNewLine();
    }
}