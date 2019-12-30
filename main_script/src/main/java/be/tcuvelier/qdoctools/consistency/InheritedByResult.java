package be.tcuvelier.qdoctools.consistency;

import be.tcuvelier.qdoctools.core.helpers.SetHelpers;

import java.util.HashSet;
import java.util.Set;

public class InheritedByResult {
    public final boolean result;
    public final Set<String> xml;
    public final Set<String> html;

    public InheritedByResult(boolean result) {
        xml = new HashSet<>();
        html = new HashSet<>();
        this.result = result;
    }

    public InheritedByResult(Set<String> xml, Set<String> html) {
        this.xml = xml;
        this.html = html;
        result = SetHelpers.compareSets(xml, html);
    }
}
