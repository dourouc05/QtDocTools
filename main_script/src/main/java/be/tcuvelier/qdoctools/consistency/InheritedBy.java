package be.tcuvelier.qdoctools.consistency;

import be.tcuvelier.qdoctools.core.helpers.SetHelpers;
import net.sf.saxon.s9api.XdmValue;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.util.HashSet;
import java.util.Set;

public class InheritedBy {
    public static ConsistencyCheckResults checkInheritedBy(ConsistencyChecker cc) {
        // If the local file is not a class, the test is passed (only for classes).
        if (! cc.isClass(cc.xdm)) {
            return ConsistencyCheckResults.fromNoError();
        }

        // Find the inherited-by classes.
        XdmValue inheritedByListXML = cc.xpath("//db:classsynopsisinfo[@role='inheritedBy" +
                "']/text()", cc.xdm);

        Set<String> inheritedBySetXML = new HashSet<>();
        for (int i = 0; i < inheritedByListXML.size(); ++i) {
            inheritedBySetXML.add(inheritedByListXML.itemAt(i).getStringValue());
        }

        // Load the remote HTML.
        Elements inheritedByTagHTML = cc.html.getElementsContainingText("Inherited By:");
        Set<String> inheritedBySetHTML = new HashSet<>();

        if (inheritedByTagHTML != null && inheritedByTagHTML.size() > 0) {
            // inheritedByTagHTML contains all tags that contain a tag that has "Inherited by":
            // take the last one, the most precise of this collection.
            Elements inheritedByListHTML =
                    inheritedByTagHTML.get(inheritedByTagHTML.size() - 1).siblingElements().get(0).getElementsByTag("a");
            //                                                                                   ^^^^^^^^^^^^^^^^^^^^^^^^
            //                                                                                   Differences with htmlToSet()
            for (Element e : inheritedByListHTML) {
                inheritedBySetHTML.add(e.text());
            }
        }

        // Compare.
        return ConsistencyCheckResults.fromXmlHtmlSets(
                "Inherited-by classes", inheritedBySetXML, inheritedBySetHTML);
    }
}
