package be.tcuvelier.qdoctools.io.docx.fromdocx;

import be.tcuvelier.qdoctools.core.constants.ContributorType;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MetaDataParagraphs {
    // Store metadata about a document (except its title) as a list of raw paragraphs.

    public XWPFParagraph datePara; // TODO: useless?
    public Date date;
    public XWPFParagraph pubdatePara; // TODO: useless?
    public Date pubdate;
    public List<XWPFParagraph> contributorParas; // TODO: useless?
    public List<Contributor> contributors;
    public List<XWPFParagraph> abstractParas;

    public MetaDataParagraphs() {
        contributorParas = new ArrayList<>();
        abstractParas = new ArrayList<>();
    }

    public boolean hasMetaData() {
        return hasMetaDataExceptAbstract() || abstractParas.size() > 0;
    }

    public boolean hasMetaDataExceptAbstract() {
        return !contributorParas.isEmpty() && datePara != null && pubdatePara != null;
    }

    public static class Contributor {
        public ContributorType type;
        public String pseudonym;

        public String name; // Only if no specific information is available (i.e. no firstName
        // and no familyName).
        public String firstName;
        public String familyName;

        public String mainUri;
        public String websiteUri;
        public String blogUri;
        public String googlePlusgUri;
        public String linkedInUri;
    }
}
