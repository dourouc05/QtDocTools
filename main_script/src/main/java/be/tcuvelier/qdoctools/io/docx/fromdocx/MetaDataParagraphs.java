package be.tcuvelier.qdoctools.io.docx.fromdocx;

import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.util.ArrayList;
import java.util.List;

public class MetaDataParagraphs {
    // Store metadata about a document (except its title) as a list of raw paragraphs.

    public XWPFParagraph author;
    public List<XWPFParagraph> abstracts;

    public MetaDataParagraphs() {
        abstracts = new ArrayList<>();
    }

    public boolean hasMetaData() {
        return hasMetaDataExceptAbstract() || abstracts.size() > 0;
    }

    public boolean hasMetaDataExceptAbstract() {
        return author != null;
    }
}
