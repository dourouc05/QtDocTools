package be.tcuvelier.qdoctools.io.fromdocx;

import org.jetbrains.annotations.NotNull;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;

public class DocBookStreamWriter {
    private final OutputStream writer;
    private XMLStreamWriter xmlStream;
    private int currentDepth;

    @SuppressWarnings("FieldCanBeLocal")
    private static String indentation = "  ";
    private static String docbookNS = "http://docbook.org/ns/docbook";
    @SuppressWarnings("FieldCanBeLocal")
    private static String xlinkNS = "http://www.w3.org/1999/xlink";

    public DocBookStreamWriter() throws XMLStreamException {
        writer = new ByteArrayOutputStream();
        xmlStream = XMLOutputFactory.newInstance().createXMLStreamWriter(writer, "UTF-8");
        currentDepth = 0;
    }

    public int getCurrentDepth() {
        return currentDepth;
    }

    public void startDocument(@NotNull String root, @SuppressWarnings("SameParameterValue") @NotNull String version) throws XMLStreamException {
        xmlStream.writeStartDocument("UTF-8", "1.0");

        writeNewLine();
        writeIndent();
        xmlStream.writeStartElement("db", root, docbookNS);
        xmlStream.setPrefix("db", docbookNS);
        xmlStream.writeNamespace("db", docbookNS);
        xmlStream.setPrefix("xlink", xlinkNS);
        xmlStream.writeNamespace("xlink", xlinkNS);
        xmlStream.writeAttribute("version", version);
        increaseIndent();

        writeNewLine();
    }

    public void endDocument() throws XMLStreamException {
        decreaseIndent();
        xmlStream.writeEndElement();
        xmlStream.writeEndDocument();
    }

    public String write() throws IOException {
        String xmlString = writer.toString();
        writer.close();
        return xmlString;
    }

    private void writeIndent() throws XMLStreamException {
        xmlStream.writeCharacters(indentation.repeat(currentDepth));
    }

    private void increaseIndent() {
        currentDepth += 1;
    }

    private void decreaseIndent() throws XMLStreamException {
        if (currentDepth == 0) {
            throw new XMLStreamException("Cannot decrease indent when it is already zero.");
        }

        currentDepth -= 1;
    }

    private void writeNewLine() throws XMLStreamException {
        xmlStream.writeCharacters("\n");
    }

    public void openParagraphTag(@NotNull String tag) throws XMLStreamException { // Paragraphs start on a new line, but contain inline elements on the same line. Examples: paragraphs, titles.
        openParagraphTag(tag, Collections.emptyMap());
    }

    public void openParagraphTag(@NotNull String tag, @NotNull Map<String, String> attributes) throws XMLStreamException {
        writeIndent();
        xmlStream.writeStartElement(docbookNS, tag);

        for (Map.Entry<String, String> attribute: attributes.entrySet()) {
            xmlStream.writeAttribute(attribute.getKey(), attribute.getValue());
        }
    }

    public void closeParagraphTag() throws XMLStreamException {
        xmlStream.writeEndElement();
        writeNewLine();
    }

    public void openInlineTag(@NotNull String tag) throws XMLStreamException { // Inline elements are on the same line.
        openInlineTag(tag, Collections.emptyMap());
    }

    public void openInlineTag(@NotNull String tag, @NotNull Map<String, String> attributes) throws XMLStreamException {
        xmlStream.writeStartElement(docbookNS, tag);

        for (Map.Entry<String, String> attribute: attributes.entrySet()) {
            xmlStream.writeAttribute(attribute.getKey(), attribute.getValue());
        }
    }

    public void closeInlineTag() throws XMLStreamException {
        xmlStream.writeEndElement();
    }

    public void openBlockInlineTag(@NotNull String tag) throws XMLStreamException { // Inline block elements start on the same line, but have nothing else to their right.
        openInlineTag(tag);
        writeNewLine();
        increaseIndent();
    }

    public void closeBlockInlineTag() throws XMLStreamException {
        writeIndent(); // Write it indented, as inline.
        closeInlineTag();
        decreaseIndent();
    }

    public void openBlockTag(@NotNull String tag) throws XMLStreamException { // Blocks start on a new line, and have nothing else on the same line.
        openBlockTag(tag, Collections.emptyMap());
    }

    @SuppressWarnings("WeakerAccess")
    public void openBlockTag(@NotNull String tag, @NotNull Map<String, String> attributes) throws XMLStreamException {
        writeIndent();
        xmlStream.writeStartElement(docbookNS, tag);

        for (Map.Entry<String, String> attribute: attributes.entrySet()) {
            xmlStream.writeAttribute(attribute.getKey(), attribute.getValue());
        }

        increaseIndent();
        writeNewLine();
    }

    public void closeBlockTag() throws XMLStreamException {
        decreaseIndent();
        writeIndent();
        xmlStream.writeEndElement();
        writeNewLine();
    }

    public void emptyBlockTag(@NotNull String tag) throws XMLStreamException {
        emptyBlockTag(tag, Collections.emptyMap());
    }

    public void emptyBlockTag(@NotNull String tag, @NotNull Map<String, String> attributes) throws XMLStreamException {
        writeIndent();
        xmlStream.writeEmptyElement(docbookNS, tag);

        for (Map.Entry<String, String> attribute: attributes.entrySet()) {
            xmlStream.writeAttribute(attribute.getKey(), attribute.getValue());
        }

        writeNewLine();
    }

    public void writeCharacters(@NotNull String text) throws XMLStreamException { // Characters.
        xmlStream.writeCharacters(text);
    }
}
