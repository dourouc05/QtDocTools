package be.tcuvelier.qdoctools.consistency;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ConsistencyCheckResults {
    public String majorError;

    // Mapping (check type) -> (list of elements). Only set for an object corresponding to one file,
    // they are reset for aggregates.
    public Map<String, Set<String>> docbook;
    public Map<String, Set<String>> html;

    private ConsistencyCheckResults(String majorError, Map<String, Set<String>> docbook,
            Map<String, Set<String>> html) {
        this.majorError = majorError;
        this.docbook = docbook;
        this.html = html;
    }

    private ConsistencyCheckResults() {
        this("", new HashMap<>(), new HashMap<>());
    }

    public static ConsistencyCheckResults fromNoError() {
        return new ConsistencyCheckResults();
    }
    public static ConsistencyCheckResults fromMajorError(String error) {
        ConsistencyCheckResults cr = new ConsistencyCheckResults();
        cr.majorError = error;
        return cr;
    }
    public static ConsistencyCheckResults fromXmlHtmlSets(String checkName, Set<String> docbook, Set<String> html) {
        if (docbook.equals(html)) {
            return fromNoError();
        }
        ConsistencyCheckResults cr = new ConsistencyCheckResults();
        cr.docbook.put(checkName, docbook);
        cr.html.put(checkName, html);
        return cr;
    }
    public void addXmlHtmlSets(String checkName, Set<String> docbook, Set<String> html) {
        this.docbook.put(checkName, docbook);
        this.html.put(checkName, html);
    }

    public boolean hasErrors() {
        return ! hasNoErrors();
    }

    public boolean hasNoErrors() {
        return majorError.isEmpty() && docbook.equals(html);
    }

    public String describeSetDifferences(String prefix, String fileName) {
        final StringBuilder sb = new StringBuilder();
        sb.append(prefix).append("File: ").append(fileName).append("\n");
        for (String checkName : docbook.keySet()) {
            assert html.containsKey(checkName);

            if (! docbook.get(checkName).equals(html.get(checkName))) {
                sb.append(prefix).append(checkName).append(" mismatch: ").append("\n");
                sb.append(prefix).append("    - DocBook has: ").append(docbook.get(checkName)).append("\n");
                sb.append(prefix).append("    - HTML has: ").append(html.get(checkName)).append("\n");
            }
        }
        return sb.toString();
    }
}
