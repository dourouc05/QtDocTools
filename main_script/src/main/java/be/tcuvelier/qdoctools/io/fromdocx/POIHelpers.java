package be.tcuvelier.qdoctools.io.fromdocx;

import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTString;

public class POIHelpers {
    public static String getStyle(XWPFRun r) {
        // https://github.com/apache/poi/pull/151
        CTRPr pr = r.getCTR().getRPr();
        if (pr == null) {
            return "";
        }

        CTString style = pr.getRStyle();
        if (style == null) {
            return "";
        }

        return style.getVal();
    }
}
