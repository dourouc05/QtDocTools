package be.tcuvelier.qdoctools.io.docx.todocx;

import org.xml.sax.SAXException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A few design notes.
 * - Avoid SDTs. Two reasons: they are not really supported within POI (but you can work around it); they are not
 *   really supported by LibreOffice, at least with 6.1.3.2 (not shown on screen as different from the rest of
 *   the code; many bug reports related to loss of information when saving as DOCX).
 */

public class LevelStack {
    // Slight interface on top of a stack (internally, a Deque) to provide some facilities when peeking, based on
    // the values of Level.

    private Deque<Level> levels = new ArrayDeque<>();
    private int listDepth = 0;

    public void push(Level l) {
        levels.push(l);

        if (l == Level.ITEMIZED_LIST || l == Level.ORDERED_LIST) {
            listDepth += 1;
        }
    }

    private void pop() {
        Level l = levels.pop();

        if (l == Level.ITEMIZED_LIST || l == Level.ORDERED_LIST) {
            listDepth -= 1;
        }
    }

    public boolean pop(Level l) {
        return pop(Stream.of(l));
    }

    private boolean pop(Stream<Level> ls) {
        return pop(ls.collect(Collectors.toSet()));
    }

    private boolean pop(Set<Level> ls) {
        if (! ls.contains(levels.peek())) {
            return false;
        }

        pop();
        return true;
    }

    public void pop(Level l, SAXException t) throws SAXException {
        if (! pop(l)) {
            throw t;
        }
    }

    public void pop(Stream<Level> l, SAXException t) throws SAXException {
        if (! pop(l)) {
            throw t;
        }
    }

    public Level peek() {
        return levels.peek();
    }

    public Level peekSecond() {
        if (levels.size() <= 1) {
            return null;
        }

        Level first = levels.pop();
        Level second = levels.peek();
        levels.push(first);
        return second;
    }

    public boolean peekRootArticle() {
        return peek() == Level.ROOT_ARTICLE;
    }

    public boolean peekRootArticleInfo() {
        return peek() == Level.ROOT_ARTICLE_INFO;
    }

    public boolean peekRootBook() {
        return peek() == Level.ROOT_BOOK;
    }

    public boolean peekRootBookInfo() {
        return peek() == Level.ROOT_BOOK_INFO;
    }

    public boolean peekPart() {
        return peek() == Level.PART;
    }

    public boolean peekChapter() {
        return peek() == Level.CHAPTER;
    }

    public boolean peekSection() {
        return peek() == Level.SECTION;
    }

    public boolean peekSectionInfo() {
        return peek() == Level.SECTION_INFO;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean peekInfo() {
        return peekSectionInfo() || peekRootArticleInfo() || peekRootBookInfo();
    }

    public boolean peekBlockPreformatted() {
        return peek() == Level.BLOCK_PREFORMATTED;
    }

    public boolean peekTable() {
        return peek() == Level.TABLE;
    }

    public boolean peekFigure() {
        return peek() == Level.FIGURE;
    }

    public boolean peekList() {
        return peekItemizedList() || peekOrderedList();
    }

    public boolean peekItemizedList() {
        return peek() == Level.ITEMIZED_LIST;
    }

    public boolean peekOrderedList() {
        return peek() == Level.ORDERED_LIST;
    }

    public int countListDepth() {
        return listDepth;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean peekSegmentedList() {
        return peek() == Level.SEGMENTED_LIST;
    }

    public boolean peekSegmentedListTitle() {
        return peek() == Level.SEGMENTED_LIST_TITLE;
    }

    public boolean peekVariableList() {
        return peek() == Level.VARIABLE_LIST;
    }

    public boolean peekSecondAdmonition() {
        Level second = peekSecond();
        return second == Level.CAUTION || second == Level.IMPORTANT || second == Level.NOTE || second == Level.TIP || second == Level.WARNING;
    }
}
