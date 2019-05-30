package be.tcuvelier.qdoctools.io.helpers;

import org.xml.sax.Attributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public enum DocBookFormatting {
    EMPHASIS, EMPHASIS_BOLD, EMPHASIS_UNDERLINE, EMPHASIS_STRIKETHROUGH, SUPERSCRIPT, SUBSCRIPT,
    CLASS_NAME, EXCEPTION_NAME, INTERFACE_NAME, METHOD_NAME, COMPUTER_OUTPUT, CONSTANT, ENVIRONMENT_VARIABLE,
    FILE_NAME, LITERAL, CODE, OPTION, PROMPT, SYSTEM_ITEM, VARIABLE_NAME, EMAIL, URI;

    // For code readability, store in a list all elements that are interesting for formattings:
    // how to recognise one, its Word style ID, etc. Of course, not all of this makes sense for all formattings.
    // Thus, the maps predicateToFormatting and docbookTagToStyleID (directly useful) are filled with two sources:
    // the list formattings, and special cases.

    public static List<Triplet<DocBookFormatting, String, String>> formattings = List.of(
            // https://github.com/docbook/xslt10-stylesheets/blob/master/xsl/html/inline.xsl: inline style
            new Triplet<>(DocBookFormatting.CLASS_NAME, "classname", "ClassName"),
            new Triplet<>(DocBookFormatting.EXCEPTION_NAME, "exceptionname", "ExceptionName"),
            new Triplet<>(DocBookFormatting.INTERFACE_NAME, "interfacename", "InterfaceName"),
            new Triplet<>(DocBookFormatting.METHOD_NAME, "methodname", "MethodName"),
            new Triplet<>(DocBookFormatting.COMPUTER_OUTPUT, "computeroutput", "ComputerOutput"),
            new Triplet<>(DocBookFormatting.CONSTANT, "constant", "Constant"),
            new Triplet<>(DocBookFormatting.ENVIRONMENT_VARIABLE, "envar", "EnvironmentVariable"),
            new Triplet<>(DocBookFormatting.FILE_NAME, "filename", "FileName"),
            new Triplet<>(DocBookFormatting.LITERAL, "literal", "Literal"),
            new Triplet<>(DocBookFormatting.CODE, "code", "Code"),
            new Triplet<>(DocBookFormatting.OPTION, "option", "Option"),
            new Triplet<>(DocBookFormatting.PROMPT, "option", "Prompt"),
            new Triplet<>(DocBookFormatting.SYSTEM_ITEM, "systemitem", "SystemItem"),
            new Triplet<>(DocBookFormatting.VARIABLE_NAME, "varname", "VariableName"),
            new Triplet<>(DocBookFormatting.EMAIL, "email", "Email"),
            new Triplet<>(DocBookFormatting.URI, "uri", "URI")
    );

    public static Map<Predicate<String>, DocBookFormatting> predicateToFormatting = Map.ofEntries(
            // https://github.com/docbook/xslt10-stylesheets/blob/c50f1cd7afc9a5b8ecee25dc1a46d62cdcd4917c/xsl/fo/inline.xsl#L745
            Map.entry(tagRecogniser("superscript"), DocBookFormatting.SUPERSCRIPT),
            Map.entry(tagRecogniser("subscript"), DocBookFormatting.SUBSCRIPT)
    );

    public static Map<DocBookFormatting, Predicate<String>> formattingToPredicate =
            predicateToFormatting.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    public static Map<DocBookFormatting, String> docbookTagToStyleID = Map.ofEntries();

    public static Map<String, String> styleIDToDocBookTag = Map.ofEntries();

    static {
        // Make the fields mutable temporarily.
        predicateToFormatting = new HashMap<>(predicateToFormatting);
        docbookTagToStyleID = new HashMap<>(docbookTagToStyleID);
        styleIDToDocBookTag = new HashMap<>(styleIDToDocBookTag);

        // Fill them.
        for (Triplet<DocBookFormatting, String, String> t: formattings) {
            predicateToFormatting.put(tagRecogniser(t.second), t.first);
            docbookTagToStyleID.put(t.first, t.third);
            styleIDToDocBookTag.put(t.third, t.second);
        }

        // Make them immutable again.
        predicateToFormatting = Map.copyOf(predicateToFormatting);
        docbookTagToStyleID = Map.copyOf(docbookTagToStyleID);
        styleIDToDocBookTag = Map.copyOf(styleIDToDocBookTag);
    }

    private static Predicate<String> tagRecogniser(String tag) {
        return qName -> recogniseTag(tag, qName);
    }

    public static boolean recogniseTag(String tag, String qName) {
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
        if (recogniseTag("emphasis", localName)) {
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
        return recogniseTag("emphasis", qName)
                || formattingToPredicate.get(SUBSCRIPT).test(qName)
                || formattingToPredicate.get(SUPERSCRIPT).test(qName)
                || isInlineFormatting(qName);
    }

    public static boolean isInlineFormatting(String qName) {
        // Formattings that require a style.
        // TODO: What to do with function? To be studied further...
        for (Triplet<DocBookFormatting, String, String> t: formattings) {
            if (formattingToPredicate.get(t.first).test(qName)) {
                return true;
            }
        }
        return false;
    }
}