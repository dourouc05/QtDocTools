package be.tcuvelier.qdoctools.io.todocx;

import be.tcuvelier.qdoctools.io.helpers.DocBookFormatting;
import org.xml.sax.Attributes;

import java.util.HashMap;
import java.util.Map;

public class SAXHelpers {
    public static Map<String, String> attributes(Attributes attributes) {
        Map<String, String> d = new HashMap<>();
        for (int i = 0; i < attributes.getLength(); ++i) {
            d.put(SAXHelpers.qNameToTagName(attributes.getLocalName(i)), attributes.getValue(i));
        }
        return d;
    }

    private static String qNameToTagName(String qName) {
        // SAX returns a localName that is zero-length... Hence this function: go from db:article to article.
        // But maybe a specific DocBook document has no defined namespace, or DocBook is the default namespace.
        if (! qName.contains(":")) {
            return qName;
        } else {
            return qName.split(":")[1];
        }
    }

    private static boolean compare(String qName, String reference) {
        String localName = qNameToTagName(qName);
        return localName.equalsIgnoreCase(reference);
    }

    public static boolean isInfoTag(String qName) {
        return compare(qName, "info");
    }

    public static boolean isAuthorTag(String qName) {
        return compare(qName, "author");
    }

    public static boolean isEditorTag(String qName) {
        return compare(qName, "editor");
    }

    public static boolean isAuthorGroupTag(String qName) {
        return compare(qName, "authorgroup");
    }

    public static boolean isPersonNameTag(String qName) {
        return compare(qName, "personname");
    }

    public static boolean isFirstNameTag(String qName) {
        return compare(qName, "firstname");
    }

    public static boolean isHonorificTag(String qName) {
        return compare(qName, "honorific");
    }

    public static boolean isLineageTag(String qName) {
        return compare(qName, "lineage");
    }

    public static boolean isOtherNameTag(String qName) {
        return compare(qName, "othername");
    }

    public static boolean isSurNameTag(String qName) {
        return compare(qName, "surname");
    }

    public static boolean isBelowAuthor(String qName) {
        return isPersonNameTag(qName) || isFirstNameTag(qName) || isHonorificTag(qName) || isLineageTag(qName)
                || isOtherNameTag(qName) || isSurNameTag(qName);
    }

    public static boolean isAbstractTag(String qName) {
        return compare(qName, "abstract");
    }

    public static boolean isPartIntroTag(String qName) {
        return compare(qName, "partintro");
    }

    public static boolean isTitleTag(String qName) {
        return compare(qName, "title");
    }

    public static boolean isSectionTag(String qName) {
        String localName = qNameToTagName(qName);
        return localName.equalsIgnoreCase("section")
                || localName.equalsIgnoreCase("sect1")
                || localName.equalsIgnoreCase("sect2")
                || localName.equalsIgnoreCase("sect3")
                || localName.equalsIgnoreCase("sect4")
                || localName.equalsIgnoreCase("sect5")
                || localName.equalsIgnoreCase("sect6");
    }

    public static boolean isParagraphTag(String qName) {
        String localName = qNameToTagName(qName);
        return localName.equalsIgnoreCase("para")
                || localName.equalsIgnoreCase("simpara");
    }

    public static boolean isFormatting(String qName) {
        return DocBookFormatting.isRunFormatting(qName) || DocBookFormatting.isInlineFormatting(qName);
    }

    public static boolean isLinkTag(String qName) {
        return compare(qName, "link");
    }

    public static boolean isFootnoteTag(String qName) {
        return compare(qName, "footnote");
    }

    public static boolean isTableTag(String qName) {
        String localName = qNameToTagName(qName);
        return localName.equalsIgnoreCase("informaltable")
                || localName.equalsIgnoreCase("table");
    }

    public static boolean isTableHeaderTag(String qName) {
        return compare(qName, "thead");
    }

    public static boolean isTableBodyTag(String qName) {
        return compare(qName, "tbody");
    }

    public static boolean isTableFooterTag(String qName) {
        return compare(qName, "tfoot");
    }

    public static boolean isTableRowTag(String qName) {
        return compare(qName, "tr");
    }

    public static boolean isTableColumnTag(String qName) {
        return compare(qName, "td");
    }

    public static boolean isCALSTag(String qName) {
        String localName = qNameToTagName(qName);
        return localName.equalsIgnoreCase("tgroup")
                || localName.equalsIgnoreCase("colspec")
                || localName.equalsIgnoreCase("row")
                || localName.equalsIgnoreCase("entry");
    }

    public static boolean isProgramListingTag(String qName) {
        return compare(qName, "programlisting");
    }

    public static boolean isInlineMediaObjectTag(String qName) {
        return compare(qName, "inlinemediaobject");
    }

    public static boolean isMediaObjectTag(String qName) {
        return compare(qName, "mediaobject");
    }

    public static boolean isImageDataTag(String qName) {
        return compare(qName, "imagedata");
    }

    public static boolean isImageObjectTag(String qName) {
        return compare(qName, "imageobject");
    }

    public static boolean isCaptionTag(String qName) {
        return compare(qName, "caption");
    }

    public static boolean isItemizedListTag(String qName) {
        return compare(qName, "itemizedlist");
    }

    public static boolean isOrderedListTag(String qName) {
        return compare(qName, "orderedlist");
    }

    public static boolean isListItemTag(String qName) {
        return compare(qName, "listitem");
    }

    public static boolean isSegmentedListTag(String qName) {
        return compare(qName, "segmentedlist");
    }

    public static boolean isSegmentedListTitleTag(String qName) {
        return compare(qName, "segtitle");
    }

    public static boolean isSegmentedListItemTag(String qName) {
        return compare(qName, "seglistitem");
    }

    public static boolean isSegmentedListItemValueTag(String qName) {
        return compare(qName, "seg");
    }

    public static boolean isVariableListTag(String qName) {
        return compare(qName, "variablelist");
    }

    public static boolean isVariableListItemTag(String qName) {
        return compare(qName, "varlistentry");
    }

    public static boolean isVariableListItemDefinitionTag(String qName) {
        return compare(qName, "term");
    }
}
