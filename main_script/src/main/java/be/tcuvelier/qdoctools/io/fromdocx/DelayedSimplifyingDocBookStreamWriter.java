package be.tcuvelier.qdoctools.io.fromdocx;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.util.Map;

public class DelayedSimplifyingDocBookStreamWriter implements DocBookStreamWriter {
    private final DocBookStreamWriter db;
    private DelayedTag delayed;

    private enum DelayedTagType {
        PARAGRAPH, INLINE, BLOCKINLINE, BLOCK;
    }

    private static class DelayedTag {
        @NotNull final DelayedTagType type;
        @NotNull final String tag;
        @Nullable Map<String, String> attributes;

        DelayedTag(@NotNull DelayedTagType type, @NotNull String tag) {
            this.type = type;
            this.tag = tag;
        }

        DelayedTag(@NotNull DelayedTagType type, @NotNull String tag, @NotNull Map<String, String> attributes) {
            this.type = type;
            this.tag = tag;
            this.attributes = attributes;
        }
    }

    public DelayedSimplifyingDocBookStreamWriter(DocBookStreamWriter db) {
        this.db = db;
        delayed = null;
    }

    private void writeDelayedTag() throws XMLStreamException {
        if (delayed == null) {
            return;
        }

        switch (delayed.type) {
            case PARAGRAPH:
                db.closeParagraphTag();
                break;
            case INLINE:
                db.closeInlineTag();
                break;
            case BLOCKINLINE:
                db.closeBlockInlineTag();
                break;
            case BLOCK:
                db.closeBlockTag();
                break;
        }

        delayed = null;
    }

    @Override
    public int getCurrentDepth() {
        return db.getCurrentDepth();
    }

    @Override
    public String write() throws IOException {
        return db.write();
    }

    @Override
    public void startDocument(@NotNull String root, @NotNull String version) throws XMLStreamException {
        db.startDocument(root, version);
    }

    @Override
    public void endDocument() throws XMLStreamException {
        db.endDocument();
    }

    @Override
    public void openParagraphTag(@NotNull String tag) throws XMLStreamException {
        db.openParagraphTag(tag);
    }

    @Override
    public void openParagraphTag(@NotNull String tag, @NotNull Map<String, String> attributes) throws XMLStreamException {
        db.openParagraphTag(tag, attributes);
    }

    @Override
    public void closeParagraphTag() throws XMLStreamException {
        db.closeParagraphTag();
    }

    @Override
    public void openInlineTag(@NotNull String tag) throws XMLStreamException {
        db.openInlineTag(tag);
    }

    @Override
    public void openInlineTag(@NotNull String tag, @NotNull Map<String, String> attributes) throws XMLStreamException {
        db.openInlineTag(tag, attributes);
    }

    @Override
    public void closeInlineTag() throws XMLStreamException {
        db.closeInlineTag();
    }

    @Override
    public void openBlockInlineTag(@NotNull String tag) throws XMLStreamException {
        db.openBlockInlineTag(tag);
    }

    @Override
    public void closeBlockInlineTag() throws XMLStreamException {
        db.closeBlockInlineTag();
    }

    @Override
    public void openBlockTag(@NotNull String tag) throws XMLStreamException {
        db.openBlockInlineTag(tag);
    }

    @Override
    public void openBlockTag(@NotNull String tag, @NotNull Map<String, String> attributes) throws XMLStreamException {
        db.openBlockTag(tag, attributes);
    }

    @Override
    public void closeBlockTag() throws XMLStreamException {
        db.closeBlockTag();
    }

    @Override
    public void emptyBlockTag(@NotNull String tag) throws XMLStreamException {
        db.emptyBlockTag(tag);
    }

    @Override
    public void emptyBlockTag(@NotNull String tag, @NotNull Map<String, String> attributes) throws XMLStreamException {
        db.emptyBlockTag(tag, attributes);
    }

    @Override
    public void writeCharacters(@NotNull String text) throws XMLStreamException {
        db.writeCharacters(text);
    }
}
