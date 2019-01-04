package be.tcuvelier.qdoctools.utils.consistency;

import be.tcuvelier.qdoctools.utils.SetUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ItemsResult {
    private final Map<String, Set<String>> xmls;
    private final Map<String, Set<String>> htmls;
    private final Map<String, Boolean> results;

    public ItemsResult() {
        xmls = new HashMap<>();
        htmls = new HashMap<>();
        results = new HashMap<>();
    }

    public void addComparison(String name, Set<String> xml, Set<String> html) {
        xmls.put(name, xml);
        htmls.put(name, html);
        results.put(name, SetUtils.compareSets(xml, html));
    }

    public boolean result() {
        for (Boolean v: results.values()) {
            if (! v) {
                return false;
            }
        }
        return true;
    }

    public Set<String> tests() {
        return results.keySet();
    }

    public Set<String> getXML(String name) {
        return xmls.get(name);
    }

    public Set<String> getHTML(String name) {
        return htmls.get(name);
    }

    public boolean getResult(String name) {
        return results.get(name);
    }
}
