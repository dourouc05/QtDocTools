package be.tcuvelier.qdoctools.utils.consistency;

import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmValue;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.HashSet;
import java.util.Set;

public class InheritedBy {
    public static InheritedByResult checkInheritedBy(CheckRequest request) throws SaxonApiException {
        // If the local file is not a class, the test is passed (only for classes).
        if (! request.isClass()) {
            return new InheritedByResult(true);
        }

        // Find the inherited-by classes.
        XdmValue inheritedByListXML = request.xpath("//db:classsynopsisinfo[@role='inheritedBy']/text()");

        Set<String> inheritedBySetXML = new HashSet<>();
        for (int i = 0; i < inheritedByListXML.size(); ++i) {
            inheritedBySetXML.add(inheritedByListXML.itemAt(i).getStringValue());
        }

        // Load the remote HTML.
        Elements inheritedByTagHTML = request.html.getElementsContainingText("Inherited By:");
        Set<String> inheritedBySetHTML = new HashSet<>();

        if (inheritedByTagHTML.size() > 0) {
            // inheritedByTagHTML contains all tags that contain a tag that has "Inherited by": take the last one,
            // the most precise of this collection.
            Elements inheritedByListHTML = inheritedByTagHTML.get(inheritedByTagHTML.size() - 1).siblingElements().get(0).getElementsByTag("a");
            //                                                                                   ^^^^^^^^^^^^^^^^^^^^^^^^
            //                                                                                   Differences with htmlToSet()
            for (Element e : inheritedByListHTML) {
                inheritedBySetHTML.add(e.text());
            }
        }

        // Compare.
        return new InheritedByResult(inheritedBySetXML, inheritedBySetHTML);
    }
}
