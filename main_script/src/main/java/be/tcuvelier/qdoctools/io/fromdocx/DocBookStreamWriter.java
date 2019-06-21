package be.tcuvelier.qdoctools.io.fromdocx;

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
    public XMLStreamWriter xmlStream; // TODO: !
    private int currentDepth;

    @SuppressWarnings("FieldCanBeLocal")
    private static String indentation = "  ";
    public static String docbookNS = "http://docbook.org/ns/docbook"; // TODO: !
    @SuppressWarnings("FieldCanBeLocal")
    private static String xlinkNS = "http://www.w3.org/1999/xlink"; // TODO: !

    public DocBookStreamWriter() throws XMLStreamException {
        writer = new ByteArrayOutputStream();
        xmlStream = XMLOutputFactory.newInstance().createXMLStreamWriter(writer, "UTF-8");
        currentDepth = 0;
    }

    public int getCurrentDepth() {
        return currentDepth;
    }

    public void startDocument(String root, @SuppressWarnings("SameParameterValue") String version) throws XMLStreamException {
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
        xmlStream.writeEndElement();
        xmlStream.writeEndDocument();
    }

    public String write() throws IOException {
        String xmlString = writer.toString();
        writer.close();
        return xmlString;
    }

    public void writeIndent() throws XMLStreamException { // TODO: !
        xmlStream.writeCharacters(indentation.repeat(currentDepth));
    }

    public void increaseIndent() { // TODO: !
        currentDepth += 1;
    }

    public void decreaseIndent() throws XMLStreamException { // TODO: !
        if (currentDepth == 0) {
            throw new XMLStreamException("Cannot decrease indent when it is already zero.");
        }

        currentDepth -= 1;
    }

    public void writeNewLine() throws XMLStreamException { // TODO: !
        xmlStream.writeCharacters("\n");
    }

    public void openParagraphTag(String tag) throws XMLStreamException { // Paragraphs start on a new line, but contain inline elements on the same line. Examples: paragraphs, titles.
        openParagraphTag(tag, Collections.emptyMap());
    }

    public void openParagraphTag(String tag, Map<String, String> attributes) throws XMLStreamException {
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

    public void openInlineTag(String tag) throws XMLStreamException { // Inline elements are on the same line.
        openInlineTag(tag, Collections.emptyMap());
    }

    public void openInlineTag(String tag, Map<String, String> attributes) throws XMLStreamException {
        xmlStream.writeStartElement(docbookNS, tag);

        for (Map.Entry<String, String> attribute: attributes.entrySet()) {
            xmlStream.writeAttribute(attribute.getKey(), attribute.getValue());
        }
    }

    public void closeInlineTag() throws XMLStreamException {
        xmlStream.writeEndElement();
    }

    public void openBlockTag(String tag) throws XMLStreamException { // Blocks start on a new line, and have nothing else on the same line.
        openBlockTag(tag, Collections.emptyMap());
    }

    public void openBlockTag(String tag, Map<String, String> attributes) throws XMLStreamException {
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

    public void emptyBlockTag(String tag) throws XMLStreamException {
        emptyBlockTag(tag, Collections.emptyMap());
    }

    public void emptyBlockTag(String tag, Map<String, String> attributes) throws XMLStreamException {
        writeIndent();
        xmlStream.writeEmptyElement(docbookNS, tag);

        for (Map.Entry<String, String> attribute: attributes.entrySet()) {
            xmlStream.writeAttribute(attribute.getKey(), attribute.getValue());
        }

        writeNewLine();
    }

    public void writeCharacters(String text) throws XMLStreamException { // Characters.
        xmlStream.writeCharacters(text);
    }
}
