package be.tcuvelier.qdoctools.io.fromdocx;

import org.jetbrains.annotations.NotNull;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.util.Map;

public interface DocBookStreamWriter {
    @SuppressWarnings("FieldCanBeLocal")
    String indentation = "  ";
    String docbookNS = "http://docbook.org/ns/docbook";
    @SuppressWarnings("FieldCanBeLocal")
    String xlinkNS = "http://www.w3.org/1999/xlink";

    int getCurrentDepth(); // TODO: not that clean...
    String write() throws IOException;

    void startDocument(@NotNull String root, @SuppressWarnings("SameParameterValue") @NotNull String version) throws XMLStreamException;
    void endDocument() throws XMLStreamException;

    void openParagraphTag(@NotNull String tag) throws XMLStreamException;
    void openParagraphTag(@NotNull String tag, @NotNull Map<String, String> attributes) throws XMLStreamException;
    void closeParagraphTag() throws XMLStreamException;

    void openInlineTag(@NotNull String tag) throws XMLStreamException;
    void openInlineTag(@NotNull String tag, @NotNull Map<String, String> attributes) throws XMLStreamException;
    void closeInlineTag() throws XMLStreamException;

    void openBlockInlineTag(@NotNull String tag) throws XMLStreamException;
    void closeBlockInlineTag() throws XMLStreamException;

    void openBlockTag(@NotNull String tag) throws XMLStreamException;
    void openBlockTag(@NotNull String tag, @NotNull Map<String, String> attributes) throws XMLStreamException;
    void closeBlockTag() throws XMLStreamException;

    void emptyBlockTag(@NotNull String tag) throws XMLStreamException;
    void emptyBlockTag(@NotNull String tag, @NotNull Map<String, String> attributes) throws XMLStreamException;

    void writeCharacters(@NotNull String text) throws XMLStreamException;
}
