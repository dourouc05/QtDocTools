package be.tcuvelier.qdoctools.core.constants;

import be.tcuvelier.qdoctools.io.docx.fromdocx.MetaDataParagraphs;

public enum ContributorType {
    AUTHOR, CONVERTER, PROOFREADER, REVIEWER, TRANSLATOR;

    public static String typeToDocBookOtherCreditClass(ContributorType type) {
        switch (type) {
            case AUTHOR:
                return null; // Should use a <db:author> tag, not a <db:othercredit> one.
            case CONVERTER:
                return "conversion";
            case PROOFREADER:
                return "proofreader";
            case REVIEWER:
                return "reviewer";
            case TRANSLATOR:
                return "translator";
        }
        return ""; // Unknown. Should not happen.
    }
}
