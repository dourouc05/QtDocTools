package be.tcuvelier.qdoctools.io.docx.helpers;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;

public class DocBookAlignment {
    public static String paragraphAlignmentToDocBookAttribute(ParagraphAlignment align) {
        switch (align.name()) {
            case "LEFT":
                return "left";
            case "CENTER":
                return "center";
            case "RIGHT":
                return "right";
            case "BOTH":
                return "justified";
            default:
                return "";
        }
    }

    public static ParagraphAlignment docbookAttributeToParagraphAlignment(String attribute) {
        switch (attribute) {
            case "center":
                return ParagraphAlignment.CENTER;
            case "right":
                return ParagraphAlignment.RIGHT;
            case "justified":
                return ParagraphAlignment.BOTH;
            case "left":
            default:
                return ParagraphAlignment.LEFT;
        }
    }
}
