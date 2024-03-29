package be.tcuvelier.qdoctools.io.docx.todocx;

public enum Level {
    ROOT_ARTICLE, ROOT_ARTICLE_INFO, ROOT_BOOK, ROOT_BOOK_INFO, PART, PART_INFO, CHAPTER,
    CHAPTER_INFO,
    SECTION, SECTION_INFO, AUTHORGROUP,
    TABLE, FIGURE,
    CAUTION, IMPORTANT, NOTE, TIP, WARNING, // Admonitions (see helpers.DocBookBlock);
    ITEMIZED_LIST, ORDERED_LIST, SEGMENTED_LIST, SEGMENTED_LIST_TITLE, VARIABLE_LIST,
    BLOCK_PREFORMATTED;

    public static Level fromAdmonitionQname(String qname) {
        // See helpers.DocBookBlock.admonitions.
        switch (qname) {
            case "caution":
            case "db:caution":
                return CAUTION;
            case "important":
            case "db:important":
                return IMPORTANT;
            case "note":
            case "db:note":
                return NOTE;
            case "tip":
            case "db:tip":
                return TIP;
            case "warning":
            case "db:warning":
                return WARNING;
        }
        throw new IllegalArgumentException("Qname not recognised as an admonition: " + qname);
    }

    public static String qnameFromAdmonition(Level level) {
        switch (level) {
            case CAUTION:
                return "caution";
            case IMPORTANT:
                return "important";
            case NOTE:
                return "note";
            case TIP:
                return "tip";
            case WARNING:
                return "warning";
        }
        throw new IllegalArgumentException("Not recognised as an admonition: " + level);
    }
}
