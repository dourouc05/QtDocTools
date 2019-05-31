package be.tcuvelier.qdoctools.io.helpers;

import org.xml.sax.Attributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public enum DocBookBlock {
    PROGRAM_LISTING, SCREEN, SYNOPSIS, LITERAL_LAYOUT;

    private static final List<Triple<DocBookBlock, String, String>> preformatted = List.of(
            new Triple<>(PROGRAM_LISTING, "programlisting", "ProgramListing"),
            new Triple<>(SCREEN, "screen", "Screen"),
            new Triple<>(SYNOPSIS, "synopsis", "Synopsis"),
            new Triple<>(LITERAL_LAYOUT, "literallayout", "LiteralLayout")
    );

    public static Map<DocBookBlock, Predicate<String>> blockToPredicate = Map.ofEntries();

    public static Map<Predicate<String>, String> predicateToStyleID = Map.ofEntries();

    public static Map<String, String> styleIDToDocBookTag = Map.ofEntries();

    static {
        // Make the fields mutable temporarily.
        blockToPredicate = new HashMap<>(blockToPredicate);
        predicateToStyleID = new HashMap<>(predicateToStyleID);
        styleIDToDocBookTag = new HashMap<>(styleIDToDocBookTag);

        // Fill them.
        for (Triple<DocBookBlock, String, String> t: preformatted) {
            blockToPredicate.put(t.first, DocBook.tagRecogniser(t.second));
            predicateToStyleID.put(DocBook.tagRecogniser(t.second), t.third);
            styleIDToDocBookTag.put(t.third, t.second);
        }

        // Make them immutable again.
        blockToPredicate = Map.copyOf(blockToPredicate);
        predicateToStyleID = Map.copyOf(predicateToStyleID);
        styleIDToDocBookTag = Map.copyOf(styleIDToDocBookTag);
    }

    public static String tagToStyleID(String localName, Attributes attributes) {
        // Attributes are ignored.

        // Many cases are really simple: just a function to call to decide which formatting it is.
        for (Map.Entry<Predicate<String>, String> e: predicateToStyleID.entrySet()) {
            if (e.getKey().test(localName)) {
                return e.getValue();
            }
        }

        // Catch-all.
        throw new IllegalArgumentException("Unknown block tag for tagToStyleID, " +
                "but recognised as block: " + localName);
    }

    public static boolean isPreformatted(String qName) {
        return blockToPredicate.get(PROGRAM_LISTING).test(qName) || blockToPredicate.get(SCREEN).test(qName)
                || blockToPredicate.get(SYNOPSIS).test(qName) || blockToPredicate.get(LITERAL_LAYOUT).test(qName);
    }
}
