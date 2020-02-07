package be.tcuvelier.qdoctools.io.fromdocx;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;

public class DelayedSimplifyingDocBookStreamWriter implements DocBookStreamWriter {
    // Simplification implemented here: </tag><tag>, merge them if it's just formatting and if all attributes match.

    private final DocBookStreamWriter db;
    private final Stack<Tag> tags;
    private boolean justClosedInlineTag = false;

    private static class Tag {
        @NotNull final String tag;
        @Nullable Map<String, String> attributes;

        Tag(@NotNull String tag) {
            this.tag = tag;
        }

        Tag(@NotNull String tag, @NotNull Map<String, String> attributes) {
            this.tag = tag;
            this.attributes = attributes;
        }
    }

    public DelayedSimplifyingDocBookStreamWriter(DocBookStreamWriter db) {
        this.db = db;
        tags = new Stack<>();
    }

    private void writeDelayedTag() throws XMLStreamException {
        if (tags.empty()) {
            return;
        }

        tags.pop();
        db.closeInlineTag();
    }

    private boolean doesDelayedTagMatch(@NotNull String tag) {
        return ! tags.empty() && tags.peek().tag.equals(tag) && tags.peek().attributes == null;
    }

    private boolean doesDelayedTagMatch(@NotNull String tag, @NotNull Map<String, String> attributes) {
        return ! tags.empty() && tags.peek().tag.equals(tag) && Objects.equals(tags.peek().attributes, attributes);
    }

    private void addTag(Tag tag) {
        tags.push(tag);
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
        // This line must be before the return: otherwise, the tag may be closed too soon.
        justClosedInlineTag = false;

        if (doesDelayedTagMatch(tag)) {
            return;
        }

        writeDelayedTag();
        db.openInlineTag(tag);
        addTag(new Tag(tag));
    }

    @Override
    public void openInlineTag(@NotNull String tag, @NotNull Map<String, String> attributes) throws XMLStreamException {
        // This line must be before the return: otherwise, the tag may be closed too soon.
        justClosedInlineTag = false;

        if (doesDelayedTagMatch(tag, attributes)) {
            return;
        }

        writeDelayedTag();
        db.openInlineTag(tag, attributes);
        addTag(new Tag(tag, attributes));
    }

    @Override
    public void closeInlineTag() {
        justClosedInlineTag = true;
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
        db.openBlockTag(tag);
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
        if (justClosedInlineTag) {
            writeDelayedTag();
            justClosedInlineTag = false;
        }

        db.writeCharacters(text);
    }
}
