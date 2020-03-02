package be.tcuvelier.qdoctools.core.constants;

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

    public static String toXmlLang(Language lang) {
        switch (lang) {
            case FRENCH:
                return "fr";
            case ENGLISH:
            default:
                return "en";
        }
    }

    public static Language fromWordLang(String wordLang) {
        if (wordLang.startsWith("fr")) {
            return FRENCH;
        } else {
            return ENGLISH;
        }
    }

    public static String toWordLang(Language lang) {
        switch (lang) {
            case FRENCH:
                return "fr-FR";
            case ENGLISH:
            default:
                return "en-GB";
        }
    }
}
