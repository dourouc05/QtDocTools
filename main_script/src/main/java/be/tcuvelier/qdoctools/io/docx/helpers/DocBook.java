package be.tcuvelier.qdoctools.io.docx.helpers;

import java.util.Arrays;
import java.util.function.Predicate;

class DocBook {
    static Predicate<String> tagRecogniser(String... tag) {
        return qName -> Arrays.stream(tag).anyMatch(t -> recogniseTag(t, qName));
    }

    static Predicate<String> tagRecogniser(String tag) {
        return qName -> recogniseTag(tag, qName);
    }

    static boolean recogniseTag(String tag, String qName) {
        // SAX returns a localName that is zero-length... Hence this function: go from db:article to article.
        // But maybe a specific DocBook document has no defined namespace, or DocBook is the default namespace.
        String localName;
        if (! qName.contains(":")) {
            localName = qName;
        } else {
            localName = qName.split(":")[1];
        }

        return localName.equalsIgnoreCase(tag);
    }
}
