package be.tcuvelier.qdoctools.utils.consistency;

import be.tcuvelier.qdoctools.helpers.SetHelpers;
import net.sf.saxon.s9api.SaxonApiException;

public class Items {
    public static ItemsResult checkItems(CheckRequest request) throws SaxonApiException {
        // If the local file is not a class, the test is passed (only for classes).
        if (! request.isClass()) {
            return new ItemsResult();
        }

        ItemsResult ir = new ItemsResult();
        ir.addComparison("Types",
                request.xpathToSet("//(db:enumsynopsis/db:enumname union db:typedefsynopsis[not(preceding-sibling::*[1][self::db:enumsynopsis])]/db:typedefname)/text()"),
                SetHelpers.union(
                        request.htmlToSet("Public Types", "h2", "public-types", true),
                        request.htmlToSet("Related Non-Members", "h2", "related-non-members", false, s -> s.equals("typedef"))
                )
        );
        ir.addComparison("Properties",
                request.xpathToSet("//db:fieldsynopsis/db:varname/text()"),
                request.htmlToSet("Properties", "h2", "properties")
        );
        ir.addComparison("Public functions",
                request.xpathToSet("//(db:methodsynopsis[not(db:modifier[text() = 'signal'])] union db:constructorsynopsis union db:destructorsynopsis)/db:methodname/text()"), // Methods, constructors, destructors.
                SetHelpers.union(
                        request.htmlToSet("Public Functions", "h2", "public-functions"),
                        request.htmlToSet("Public Slots", "h2", "public-slots"),
                        request.htmlToSet("Signals", "h2", "signals"),
                        request.htmlToSet("Reimplemented Public Functions", "h2", "reimplemented-public-functions"), // Example: http://doc.qt.io/qt-5/q3dcamera.html
                        request.htmlToSet("Reimplemented Protected Functions", "h2", "reimplemented-protected-functions"), // Example: http://doc.qt.io/qt-5/qabstractanimation.html#event
                        request.htmlToSet("Static Public Members", "h2", "static-public-members"), // Example: https://doc.qt.io/qt-5.11/q3dscene.html
                        request.htmlToSet("Protected Functions", "h2", "protected-functions"), // Example: https://doc.qt.io/qt-5.11/q3dobject.html
                        request.htmlToSet("Related Non-Members", "h2", "related-non-members", false, s -> ! s.equals("typedef")) // Example: http://doc.qt.io/qt-5/qopengldebugmessage.html http://doc.qt.io/qt-5/qxmlstreamnotationdeclaration.html
                )
        );
        ir.addComparison("Signals", // TODO: What the heck, signals are also in the public functions?
                request.xpathToSet("//db:methodsynopsis[db:modifier[text() = 'signal']]/db:methodname/text()"),
                request.htmlToSet("Signals", "h2", "signals")
        );
//        ir.addComparison("Public variables",
//                request.xpathToSet("//db:fieldsynopsis/db:varname/text()"), // Example: http://doc.qt.io/qt-5/qstyleoptionrubberband.html
//                request.htmlToSet("Public Types", "h2", "public-variables")
//        );
        // TODO: Public functions, like https://doc.qt.io/qt-5.11/qopenglfunctions-1-0.html
        // TODO: Static public members, like http://doc.qt.io/qt-5/qxmlinputsource.html#static-public-members
        // TODO: Protected slots, like http://doc.qt.io/qt-5/qmdiarea.html#protected-slots

        return ir;
    }
}
