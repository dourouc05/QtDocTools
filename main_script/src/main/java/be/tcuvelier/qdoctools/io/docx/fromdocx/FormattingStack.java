package be.tcuvelier.qdoctools.io.docx.fromdocx;

import be.tcuvelier.qdoctools.io.docx.helpers.DocBookFormatting;
import be.tcuvelier.qdoctools.io.docx.helpers.Tuple;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.xml.stream.XMLStreamException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

public class FormattingStack {
    private final Deque<DocBookFormatting> stack = new ArrayDeque<>();
    private Deque<DocBookFormatting> addedInRun;
    private Deque<DocBookFormatting> removedInRun;

    private void unstackUntilAndRemove(@Nullable DocBookFormatting f) throws XMLStreamException {
        // Example stack: BOLD, EMPHASIS, STRIKE.
        // Close EMPHASIS. Should unstack both STRIKE and EMPHASIS, then stack again STRIKE.
        // (Could do something better by storing the full paragraph before outputting the formattings, but the
        // added complexity is not worth it, as this case will probably not happen often.)

        // Unstack the tags until you reach the required formatting (or no formatting at all).
        Deque<DocBookFormatting> removed = new ArrayDeque<>();
        if (f != null) {
            while (stack.getLast() != f) {
                DocBookFormatting current = stack.removeLast();
                removed.push(current);
            }
        } else {
            while (stack.size() > 0) {
                DocBookFormatting current = stack.removeLast();
                removed.push(current);
            }
        }

        // Pop the formatting you're looking for.
        if (f != null) {
            removedInRun.add(f);
            stack.removeLast();
            if (stack.size() > 0 && stack.getLast().equals(f)) {
                throw new XMLStreamException("Assertion failed.");
            }
        }

        // Push the untouched formattings. This destroys removed.
        Iterator<DocBookFormatting> itr = removed.descendingIterator();
        while (itr.hasNext()) {
            DocBookFormatting elt = itr.next();
            removedInRun.push(elt);
        }
    }

    private void dealWith(boolean isFormattingEnabled, DocBookFormatting f) throws XMLStreamException {
        if (isFormattingEnabled && ! stack.contains(f)) { // If this formatting is new, add it.
            addedInRun.add(f);
            stack.add(f);
        } else if (! isFormattingEnabled && stack.contains(f)) { // If this formatting is not enabled but was there
            // before, remove it.
            unstackUntilAndRemove(f);
        } // Otherwise, nothing going on (two cases: not enabled and not pending; enabled and opened previously).
    }

    private void unrecognisedStyle(@NotNull XWPFRun run) throws XMLStreamException {
        String styleID = run.getStyle();
        if (styleID.equals("CommentReference")) {
            System.out.println("There is still a comment in the document. Have proofs been checked?");
        } else if (! isStyleIDIgnored(styleID)) {
            throw new XMLStreamException("Unrecognised run style: " + styleID);
        } else {
            // No style, but maybe the user wants to tell the software something.

            if (run.getFontName() != null) {
                // Cannot make a test on the font family, as it does not support monospaced information:
                // https://docs.microsoft.com/en-us/dotnet/api/documentformat.openxml.spreadsheet.fontfamily?view=openxml-2.8.1
                if (run.getFontName().equals("Consolas") || run.getFontName().equals("Courier New")) {
                    System.out.println("Error: text in a monospaced font (" + run.getFontName() + ") but not marked " +
                            "with a style to indicate its meaning.");
                    // TODO: default to <code>.
                }
            }
        }
    }

    @Contract("null -> false")
    private boolean isStyleIDNormal(String styleID) {
        if (styleID == null) {
            return false;
        }
        return styleID.equals("Normal") || styleID.equals("FootnoteText") || styleID.equals("");
    }

    @Contract("null -> false")
    private boolean isStyleIDIgnored(String styleID) {
        if (styleID == null) {
            return false;
        }
        return styleID.equals("Hyperlink") || styleID.equals("FootnoteReference") || styleID.equals("");
    }

    public Tuple<Deque<DocBookFormatting>, Deque<DocBookFormatting>> processRun(@NotNull XWPFRun run, @Nullable XWPFRun prevRun)
            throws XMLStreamException {
        // Copied from STVerticalAlignRun.Enum. TODO: Better way to have these constants?
        int INT_SUPERSCRIPT = 2;
        int INT_SUBSCRIPT = 3;

        // Start dealing with this run: iterate through the formattings, translate them into DocBookFormatting
        // instances, and call dealWith.
        addedInRun = new ArrayDeque<>();
        removedInRun = new ArrayDeque<>();

        // Formattings encoded as run attributes.
        boolean isLink = run instanceof XWPFHyperlinkRun;
        dealWith(run.isBold(), DocBookFormatting.EMPHASIS_BOLD);
        dealWith(run.isItalic(), DocBookFormatting.EMPHASIS);
        dealWith(! isLink && run.getUnderline() != UnderlinePatterns.NONE, DocBookFormatting.EMPHASIS_UNDERLINE);
        dealWith(run.isStrikeThrough() || run.isDoubleStrikeThrough(), DocBookFormatting.EMPHASIS_STRIKETHROUGH);
        dealWith(run.getVerticalAlignment().intValue() == INT_SUPERSCRIPT, DocBookFormatting.SUPERSCRIPT);
        dealWith(run.getVerticalAlignment().intValue() == INT_SUBSCRIPT, DocBookFormatting.SUPERSCRIPT);

        // Formattings encoded as styles.
        String styleID = run.getStyle();
        String prevStyleID = prevRun == null? "" : prevRun.getStyle();
        if ((DocBookFormatting.styleIDToDocBookTag.containsKey(styleID) || styleID.equals(""))
                && (prevRun == null || prevStyleID.equals("") || DocBookFormatting.styleIDToDocBookTag.containsKey(prevStyleID))) {
            // If both styles are equal, nothing to do. Otherwise...
            if (! prevStyleID.equals(styleID)) {
                if (isStyleIDNormal(prevStyleID)) {
                    DocBookFormatting f = DocBookFormatting.styleIDToFormatting.get(styleID);
                    addedInRun.add(f);
                    stack.add(f);
                } else if (isStyleIDNormal(styleID)) {
                    unstackUntilAndRemove(DocBookFormatting.styleIDToFormatting.get(styleID));
                } else {
                    DocBookFormatting f = DocBookFormatting.styleIDToFormatting.get(prevStyleID);
                    unstackUntilAndRemove(f);
                    addedInRun.add(f);
                    stack.add(f);
                }
            }
        } else {
            // It's not because the previous condition was not met that an error should be shown.
            // Ignore the style Hyperlink, used for links: this is properly handled elsewhere, not using the
            // standard style mechanism (links have a special run type: XWPFHyperlinkRun).
            if (! isStyleIDIgnored(styleID) && ! DocBookFormatting.styleIDToDocBookTag.containsKey(styleID)) {
                unrecognisedStyle(run);
            }
            if (prevRun != null && ! isStyleIDIgnored(styleID) && ! DocBookFormatting.styleIDToDocBookTag.containsKey(prevStyleID)) {
                unrecognisedStyle(prevRun);
            }
        }

        return new Tuple<>(addedInRun, removedInRun);
    }

    public Deque<DocBookFormatting> formattings() {
        return stack;
    }
}
