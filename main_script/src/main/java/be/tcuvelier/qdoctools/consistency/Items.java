package be.tcuvelier.qdoctools.consistency;

import be.tcuvelier.qdoctools.core.helpers.SetHelpers;

public class Items {
    public static ConsistencyCheckResults checkItems(ConsistencyChecker cc) {
        // If the local file is not a class, the test is passed (only for classes).
        if (! cc.isClass(cc.xdm)) {
            return ConsistencyCheckResults.fromNoError();
        }

        ConsistencyCheckResults cr = ConsistencyCheckResults.fromNoError();
        cr.addXmlHtmlSets("Types",
                cc.xpathToSet("//(db:enumsynopsis/db:enumname union db:typedefsynopsis[not" +
                        "(preceding-sibling::*[1][self::db:enumsynopsis])]/db:typedefname)/text()", cc.xdm),
                SetHelpers.union(
                        cc.htmlToSet("Public Types", "h2", "public-types", true, cc.html),
                        cc.htmlToSet("Related Non-Members", "h2", "related-non-members",
                                false, s -> s.equals("typedef"), cc.html)
                )
        );
        cr.addXmlHtmlSets("Properties",
                cc.xpathToSet("//db:fieldsynopsis/db:varname/text()", cc.xdm),
                cc.htmlToSet("Properties", "h2", "properties", cc.html)
        );
        cr.addXmlHtmlSets("Public functions",
                cc.xpathToSet("//(db:methodsynopsis[not(db:modifier[text() = 'signal'])] " +
                        "union db:constructorsynopsis union db:destructorsynopsis)" +
                        "/db:methodname/text()", cc.xdm), // Methods, constructors, destructors.
                SetHelpers.union(
                        cc.htmlToSet("Public Functions", "h2", "public-functions", cc.html),
                        cc.htmlToSet("Public Slots", "h2", "public-slots", cc.html),
                        cc.htmlToSet("Reimplemented Public Functions", "h2", "reimplemented" +
                                "-public-functions", cc.html), // Example: http://doc.qt.io/qt-5/q3dcamera.html
                        cc.htmlToSet("Reimplemented Protected Functions", "h2",
                                "reimplemented-protected-functions", cc.html), // Example: http://doc.qt.io/qt-5/qabstractanimation.html#event
                        cc.htmlToSet("Static Public Members", "h2", "static-public-members", cc.html)
                        , // Example: https://doc.qt.io/qt-5.11/q3dscene.html
                        cc.htmlToSet("Protected Functions", "h2", "protected-functions", cc.html), // Example: https://doc.qt.io/qt-5.11/q3dobject.html
                        cc.htmlToSet("Related Non-Members", "h2", "related-non-members",
                                false, s -> !s.equals("typedef"), cc.html) // Example: http://doc.qt.io/qt-5/qopengldebugmessage.html
                        // http://doc.qt.io/qt-5/qxmlstreamnotationdeclaration.html
                )
        );
        cr.addXmlHtmlSets("Signals",
                cc.xpathToSet("//db:methodsynopsis[db:modifier[text() = " +
                        "'signal']]/db:methodname/text()", cc.xdm),
                cc.htmlToSet("Signals", "h2", "signals", cc.html)
        );
//        cr.addXmlHtmlSets("Public variables",
//                cc.xpathToSet("//db:fieldsynopsis/db:varname/text()"), // Example: http://doc.qt.io/qt-5/qstyleoptionrubberband.html
//                cc.htmlToSet("Public Types", "h2", "public-variables", cc.html)
//        );
        // TODO: Public functions, like https://doc.qt.io/qt-5.11/qopenglfunctions-1-0.html
        // TODO: Static public members, like http://doc.qt.io/qt-5/qxmlinputsource.html#static-public-members
        // TODO: Protected slots, like http://doc.qt.io/qt-5/qmdiarea.html#protected-slots

        return cr;
    }
}
