package be.tcuvelier.qdoctools.io.helpers;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.xml.sax.Attributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public enum DocBookFormatting {
    EMPHASIS, EMPHASIS_BOLD, EMPHASIS_UNDERLINE, EMPHASIS_STRIKETHROUGH, SUPERSCRIPT, SUBSCRIPT,
    // Monospaced.
    CLASS_NAME, EXCEPTION_NAME, INTERFACE_NAME, METHOD_NAME, COMPUTER_OUTPUT, CONSTANT, ENVIRONMENT_VARIABLE,
    FILE_NAME, LITERAL, CODE, OPTION, PROMPT, SYSTEM_ITEM, VARIABLE_NAME, EMAIL, URI,
    // No specific formatting.
    AUTHOR_INITIALS, ACCEL, ACTION, APPLICATION, DATABASE, DATE, ERROR_CODE, ERROR_NAME, ERROR_TYPE, ERROR_TEXT,
    GUI_BUTTON, GUI_ICON, GUI_LABEL, GUI_MENU, GUI_MENU_ITEM, GUI_SUBMENU, HARDWARE, INTERFACE, INTERFACE_DEFINITION,
    KEY_CODE, KEY_SYMBOL, MOUSE_BUTTON, PACKAGE, PROPERTY, RETURN_VALUE, STRUCTURE_NAME, SYMBOL, TOKEN, TYPE,
    ABBREVIATION, ACRONYM, MARKUP, PRODUCT_NUMBER, POB, STREET, CITY, STATE, POST_CODE, COUNTRY, OTHER_ADDRESS,
    PHONE, FAX, HONORIFIC, FIRST_NAME, GIVEN_NAME, SURNAME, LINEAGE, OTHER_NAME,
    // Bold.
    COMMAND, SHORTCUT
    ;

    // For code readability, store in a list all elements that are interesting for formattings:
    // how to recognise one, its Word style ID, etc. Of course, not all of this makes sense for all formattings.
    // Thus, the maps predicateToFormatting and docbookTagToStyleID (directly useful) are filled with two sources:
    // the list formattings, and special cases.

    public static final List<Triple<DocBookFormatting, String, String>> formattings = List.of(
            // https://github.com/docbook/xslt10-stylesheets/blob/master/xsl/html/inline.xsl: monospaced style.
            new Triple<>(CLASS_NAME, "classname", "ClassName"),
            new Triple<>(EXCEPTION_NAME, "exceptionname", "ExceptionName"),
            new Triple<>(INTERFACE_NAME, "interfacename", "InterfaceName"),
            new Triple<>(METHOD_NAME, "methodname", "MethodName"),
            new Triple<>(COMPUTER_OUTPUT, "computeroutput", "ComputerOutput"),
            new Triple<>(CONSTANT, "constant", "Constant"),
            new Triple<>(ENVIRONMENT_VARIABLE, "envar", "EnvironmentVariable"),
            new Triple<>(FILE_NAME, "filename", "FileName"),
            new Triple<>(LITERAL, "literal", "Literal"),
            new Triple<>(CODE, "code", "Code"),
            new Triple<>(OPTION, "option", "Option"),
            new Triple<>(PROMPT, "option", "Prompt"),
            new Triple<>(SYSTEM_ITEM, "systemitem", "SystemItem"),
            new Triple<>(VARIABLE_NAME, "varname", "VariableName"),
            new Triple<>(EMAIL, "email", "Email"),
            new Triple<>(URI, "uri", "URI"),
            // TODO: What to do with function?
            // https://github.com/docbook/xslt10-stylesheets/blob/master/xsl/html/inline.xsl: normal style.
            new Triple<>(AUTHOR_INITIALS, "authorinitials", "Author Initials"),
            new Triple<>(ACCEL, "accel", "Accel"),
            new Triple<>(ACTION, "action", "Action"),
            new Triple<>(APPLICATION, "application", "Application"),
            new Triple<>(DATABASE, "database", "Database"),
            new Triple<>(DATE, "date", "Date"),
            new Triple<>(ERROR_CODE, "errorcode", "ErrorCode"),
            new Triple<>(ERROR_NAME, "errorname", "ErrorName"),
            new Triple<>(ERROR_TYPE, "errortype", "ErrorType"),
            new Triple<>(ERROR_TEXT, "errortext", "ErrorText"),
            new Triple<>(GUI_BUTTON, "guibutton", "GUIButton"),
            new Triple<>(GUI_ICON, "guiicon", "GUIIcon"),
            new Triple<>(GUI_LABEL, "guilabel", "GUILabel"),
            new Triple<>(GUI_MENU, "guimenu", "GUIMenu"),
            new Triple<>(GUI_MENU_ITEM, "guimenuitem", "GUIMenuItem"),
            new Triple<>(GUI_SUBMENU, "guisubmenu", "GUISubmenu"),
            new Triple<>(HARDWARE, "hardware", "Hardware"),
            new Triple<>(INTERFACE, "interface", "Interface"),
            new Triple<>(INTERFACE_DEFINITION, "interfacedefinition", "InterfaceDefinition"),
            new Triple<>(KEY_CODE, "keycode", "KeyCode"),
            new Triple<>(KEY_SYMBOL, "keysym", "KeySymbol"),
            new Triple<>(MOUSE_BUTTON, "mousebutton", "MouseButton"),
            new Triple<>(PACKAGE, "package", "Package"),
            new Triple<>(PROPERTY, "property", "Property"),
            new Triple<>(RETURN_VALUE, "returnvalue", "ReturnValue"),
            new Triple<>(STRUCTURE_NAME, "structname", "StructureName"),
            new Triple<>(SYMBOL, "symbol", "Symbol"),
            new Triple<>(TOKEN, "token", "Token"),
            new Triple<>(TYPE, "type", "Type"),
            new Triple<>(ABBREVIATION, "abbrev", "Abbreviation"),
            new Triple<>(ACRONYM, "acronym", "Acronym"),
            new Triple<>(MARKUP, "markup", "Markup"),
            new Triple<>(PRODUCT_NUMBER, "productnumber", "ProductNumber"),
            new Triple<>(POB, "pob", "POB"),
            new Triple<>(STREET, "street", "Street"),
            new Triple<>(CITY, "city", "City"),
            new Triple<>(STATE, "state", "State"),
            new Triple<>(POST_CODE, "postcode", "PostCode"),
            new Triple<>(COUNTRY, "country", "Country"),
            new Triple<>(OTHER_ADDRESS, "otheraddr", "OtherAddress"),
            new Triple<>(PHONE, "phone", "Phone"),
            new Triple<>(FAX, "fax", "Fax"),
            new Triple<>(HONORIFIC, "honorific", "Honorific"),
            new Triple<>(FIRST_NAME, "firstname", "FirstName"),
            new Triple<>(GIVEN_NAME, "givenname", "GivenName"),
            new Triple<>(SURNAME, "surname", "Surname"),
            new Triple<>(LINEAGE, "lineage", "Lineage"),
            new Triple<>(OTHER_NAME, "othername", "OtherName"),
            // TODO: What to do with citerefentry, citetitle, quote, lineannotation, trademark, optional, citation, citebiblioid, comment, remark, productname?
            // https://github.com/docbook/xslt10-stylesheets/blob/master/xsl/html/inline.xsl: bold style.
            new Triple<>(COMMAND, "command", "Command"),
            new Triple<>(SHORTCUT, "shortcut", "Shortcut")
    );

    public static Map<Predicate<String>, DocBookFormatting> predicateToFormatting = Map.ofEntries(
            // https://github.com/docbook/xslt10-stylesheets/blob/c50f1cd7afc9a5b8ecee25dc1a46d62cdcd4917c/xsl/fo/inline.xsl#L745
            Map.entry(DocBook.tagRecogniser("superscript"), DocBookFormatting.SUPERSCRIPT),
            Map.entry(DocBook.tagRecogniser("subscript"), DocBookFormatting.SUBSCRIPT)
    );

    public static Map<DocBookFormatting, Predicate<String>> formattingToPredicate; // Filled in the static block.

    public static Map<DocBookFormatting, String> docbookTagToStyleID = Map.ofEntries();

    public static Map<String, String> styleIDToDocBookTag = Map.ofEntries();

    static {
        // Make the fields mutable temporarily.
        predicateToFormatting = new HashMap<>(predicateToFormatting);
        docbookTagToStyleID = new HashMap<>(docbookTagToStyleID);
        styleIDToDocBookTag = new HashMap<>(styleIDToDocBookTag);

        // Fill them.
        for (Triple<DocBookFormatting, String, String> t: formattings) {
            predicateToFormatting.put(DocBook.tagRecogniser(t.second), t.first);
            docbookTagToStyleID.put(t.first, t.third);
            styleIDToDocBookTag.put(t.third, t.second);
        }

        // Make them immutable again.
        predicateToFormatting = Map.copyOf(predicateToFormatting);
        docbookTagToStyleID = Map.copyOf(docbookTagToStyleID);
        styleIDToDocBookTag = Map.copyOf(styleIDToDocBookTag);

        // Compute the derived maps.
        formattingToPredicate = predicateToFormatting.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
    }

    public static DocBookFormatting tagToFormatting(String localName, Attributes attributes) {
        String role = "";
        for (int i = 0; i < attributes.getLength(); ++i) {
            if (attributes.getLocalName(i).equalsIgnoreCase("role")) {
                role = attributes.getValue(i).toLowerCase();
                break;
            }
        }

        // Based on current XSLT sheets.
        // https://github.com/docbook/xslt10-stylesheets/blob/c50f1cd7afc9a5b8ecee25dc1a46d62cdcd4917c/xsl/fo/inline.xsl#L745
        if (DocBook.recogniseTag("emphasis", localName)) {
            switch (role) {
                case "strong":
                    System.out.println("Warning: an emphasis tag has a 'strong' role, which will be replaced " +
                            "by 'bold' after round-tripping back to DocBook.");
                    // Slip through.
                case "bold":
                    return DocBookFormatting.EMPHASIS_BOLD;
                case "underline":
                    return DocBookFormatting.EMPHASIS_UNDERLINE;
                case "strikethrough":
                    return DocBookFormatting.EMPHASIS_STRIKETHROUGH;
                case "":
                case "italics":
                default:
                    return DocBookFormatting.EMPHASIS;
            }
        } else {
            // Many cases are really simple: just a function to call to decide which formatting it is.
            for (Map.Entry<Predicate<String>, DocBookFormatting> e: predicateToFormatting.entrySet()) {
                if (e.getKey().test(localName)) {
                    return e.getValue();
                }
            }

            // Catch-all.
            throw new IllegalArgumentException("Unknown formatting tag for tagToFormatting, " +
                    "but recognised as formatting: " + localName);
        }
    }

    public static boolean isRunFormatting(String qName) {
        // Formattings that are set through run parameters.
        return DocBook.recogniseTag("emphasis", qName)
                || formattingToPredicate.get(SUBSCRIPT).test(qName)
                || formattingToPredicate.get(SUPERSCRIPT).test(qName)
                || isInlineFormatting(qName);
    }

    public static boolean isInlineFormatting(String qName) {
        // Formattings that require a style.
        // TODO: What to do with function? To be studied further...
        for (Triple<DocBookFormatting, String, String> t: formattings) {
            if (formattingToPredicate.get(t.first).test(qName)) {
                return true;
            }
        }
        return false;
    }
}
