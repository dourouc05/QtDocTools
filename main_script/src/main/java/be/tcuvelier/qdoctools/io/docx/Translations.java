package be.tcuvelier.qdoctools.io.docx;

import java.util.Map;

public class Translations {
    public enum Language {
        FRENCH, ENGLISH;

        public static Language fromXmlLang(String xmlLang) {
            switch (xmlLang) {
                case "fr":
                    return FRENCH;
                case "en":
                default:
                    return ENGLISH;
            }
        }
    }

    // <db:programlisting>
    public static final Map<Language, String> programListing =
            Map.of(Language.FRENCH, "Code source", Language.ENGLISH, "Program listing");
    public static final Map<Language, String> programListingLanguage =
            Map.of(Language.FRENCH, "Langage", Language.ENGLISH, "Language");
    public static final Map<Language, String> programListingContinuation =
            Map.of(Language.FRENCH, "Continuation", Language.ENGLISH, "Continuation");
    public static final Map<Language, String> programListingContinuationValueContinues =
            Map.of(Language.FRENCH, "continue", Language.ENGLISH, "continues");
    public static final Map<Language, String> programListingContinuationValueRestarts =
            Map.of(Language.FRENCH, "redémarre", Language.ENGLISH, "restarts");
    public static final Map<Language, String> programListingLineNumbering =
            Map.of(Language.FRENCH, "Numérotation des lignes", Language.ENGLISH, "Line numbering");
    public static final Map<Language, String> programListingLineNumberingValueNumbered =
            Map.of(Language.FRENCH, "numéroté", Language.ENGLISH, "numbered");
    public static final Map<Language, String> programListingLineNumberingValueUnnumbered =
            Map.of(Language.FRENCH, "non numéroté", Language.ENGLISH, "unnumbered");
    public static final Map<Language, String> programListingStartingLineNumber =
            Map.of(Language.FRENCH, "Numéro de la première ligne", Language.ENGLISH, "Starting line number");
}
