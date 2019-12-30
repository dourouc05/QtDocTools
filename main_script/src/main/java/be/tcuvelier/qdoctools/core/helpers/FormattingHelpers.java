package be.tcuvelier.qdoctools.core.helpers;

import java.util.List;

public class FormattingHelpers {
    public static String prefix(int i, List<?> list) {
        String iFormat = "%0" + Integer.toString(list.size()).length() + "d";
        return "[" + String.format(iFormat, i + 1) + "/" + list.size() + "]";
    }
}
