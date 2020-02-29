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

    // <db:programlisting>.
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

    // <db:author>, <db:authorgroup>, and family.
    public static final Map<Language, String> author =
            Map.of(Language.FRENCH, "Auteur", Language.ENGLISH, "Author");
    public static final Map<Language, String> proofreader =
            Map.of(Language.FRENCH, "Correcteur", Language.ENGLISH, "Proofreader");
    public static final Map<Language, String> converter =
            Map.of(Language.FRENCH, "Mise au gabarit", Language.ENGLISH, "Converter"); // TODO:
    public static final Map<Language, String> reviewer =
            Map.of(Language.FRENCH, "Relecteur technique", Language.ENGLISH, "Technical reviewer");
    public static final Map<Language, String> translator =
            Map.of(Language.FRENCH, "Traducteur", Language.ENGLISH, "Translator");
    public static final Map<Language, String> firstName =
            Map.of(Language.FRENCH, "Prénom", Language.ENGLISH, "First name");
    public static final Map<Language, String> surname =
            Map.of(Language.FRENCH, "Nom de famille", Language.ENGLISH, "Family name");
    public static final Map<Language, String> pseudonym =
            Map.of(Language.FRENCH, "Pseudonyme", Language.ENGLISH, "Pseudonym");
}
