package be.tcuvelier.qdoctools.utils.io;

import be.tcuvelier.qdoctools.cli.MainCommand;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DocxOutput {
    public static void main(String[] args) throws Exception {
//        String test = "basic";
//        String test = "sections";
        String test = "images";

        new DocxOutput(MainCommand.toDocxTests + "synthetic/" + test + ".xml")
                .toDocx(MainCommand.toDocxTests + "synthetic/" + test + ".docx");
    }

    private String input;

    public DocxOutput(String input) {
        this.input = input;
    }

    public void toDocx(String output) throws IOException, ParserConfigurationException, SAXException {
        try (FileOutputStream out = new FileOutputStream(output)) {
            toDocx().write(out);
        }
    }

    @SuppressWarnings("WeakerAccess")
    public XWPFDocument toDocx() throws IOException, ParserConfigurationException, SAXException {
        SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
        SAXHandler handler = new SAXHandler(Paths.get(input).getParent());
        saxParser.parse(new File(input), handler);
        return handler.doc;
    }

    private enum Level {
        ROOT, ROOT_INFO,
        SECTION, SECTION_INFO,
        TABLE
    }

    private enum Formatting {
        EMPHASIS, EMPHASIS_BOLD, EMPHASIS_UNDERLINE, EMPHASIS_STRIKETHROUGH, SUPERSCRIPT, SUBSCRIPT;

        static Formatting tagToFormatting(String localName, Attributes attributes) {
            String role = "";
            for (int i = 0; i < attributes.getLength(); ++i) {
                if (attributes.getLocalName(i).equalsIgnoreCase("role")) {
                    role = attributes.getValue(i).toLowerCase();
                    break;
                }
            }

            // Based on current XSLT sheets.
            // https://github.com/docbook/xslt10-stylesheets/blob/c50f1cd7afc9a5b8ecee25dc1a46d62cdcd4917c/xsl/fo/inline.xsl#L745
            if (SAXHelpers.isEmphasisTag(localName) && (role.equals("") || role.equals("italics"))) {
                return Formatting.EMPHASIS;
            } else if (SAXHelpers.isEmphasisTag(localName) && (role.equals("bold") || role.equals("strong"))) {
                if (role.equals("strong")) {
                    System.out.println("Warning: an emphasis tag has a 'strong' role, which will be replaced by 'bold' " +
                            "after round-tripping back to DocBook.");
                }

                return Formatting.EMPHASIS_BOLD;
            } else if (SAXHelpers.isEmphasisTag(localName) && role.equals("underline")) {
                return Formatting.EMPHASIS_UNDERLINE;
            } else if (SAXHelpers.isEmphasisTag(localName) && role.equals("strikethrough")) {
                return Formatting.EMPHASIS_STRIKETHROUGH;
            } else if (SAXHelpers.isSuperscriptTag(localName)) {
                return Formatting.SUPERSCRIPT;
            } else if (SAXHelpers.isSubscriptTag(localName)) {
                return Formatting.SUBSCRIPT;
            } else {
                return Formatting.EMPHASIS;
            }
        }
    }

    private static ParagraphAlignment attributeToAlignment(String attribute) {
        // See also DocxInput.paragraphAlignmentToDocBookAttribute.
        switch (attribute) {
            case "center":
                return ParagraphAlignment.CENTER;
            case "right":
                return ParagraphAlignment.RIGHT;
            case "justified":
                return ParagraphAlignment.BOTH;
            case "left":
            default:
                return ParagraphAlignment.LEFT;
        }
    }

    private static int filenameToWordFormat(String filename) {
        // See org.apache.poi.xwpf.usermodel.Document.
        String[] parts = filename.split("\\.");
        String extension = parts[parts.length - 1].toLowerCase();

        switch (extension) {
            case "emf":  return 2;
            case "wmf":  return 3;
            case "pict": return 4;
            case "jpg":
            case "jpeg": return 5;
            case "png":  return 6;
            case "dib":  return 7;
            case "gif":  return 8;
            case "tif":
            case "tiff": return 9;
            case "eps":  return 10;
            case "bmp":  return 11;
            case "wpg":  return 12;
        }
        return -1;
    }

    private static class SAXHelpers {
        private static String qNameToTagName(String qName) {
            // SAX returns a localName that is zero-length... Hence this function: go from db:article to article.
            // But maybe a specific DocBook document has no defined namespace, or DocBook is the default namespace.
            if (! qName.contains(":")) {
                return qName;
            } else {
                return qName.split(":")[1];
            }
        }

        private static boolean isRootTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("article") || localName.equalsIgnoreCase("book");
        }

        private static boolean isInfoTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("info");
        }

        private static boolean isTitleTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("title");
        }

        private static boolean isSectionTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("section")
                    || localName.equalsIgnoreCase("sect1")
                    || localName.equalsIgnoreCase("sect2")
                    || localName.equalsIgnoreCase("sect3")
                    || localName.equalsIgnoreCase("sect4")
                    || localName.equalsIgnoreCase("sect5")
                    || localName.equalsIgnoreCase("sect6");
        }

        private static boolean isParagraphTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("para")
                    || localName.equalsIgnoreCase("simpara");
        }

        private static boolean isEmphasisTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("emphasis");
        }

        private static boolean isSuperscriptTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("superscript");
        }

        private static boolean isSubscriptTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("subscript");
        }

        private static boolean isFormatting(String qName) {
            return isEmphasisTag(qName) || isSubscriptTag(qName) || isSuperscriptTag(qName);
        }

        private static boolean isTableTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("informaltable")
                    || localName.equalsIgnoreCase("table");
        }

        private static boolean isTableHeaderTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("thead");
        }

        private static boolean isTableBodyTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("tbody");
        }

        private static boolean isTableFooterTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("tfoot");
        }

        private static boolean isTableRowTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("tr");
        }

        private static boolean isTableColumnTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("td");
        }

        private static boolean isCALSTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("tgroup")
                    || localName.equalsIgnoreCase("colspec")
                    || localName.equalsIgnoreCase("row")
                    || localName.equalsIgnoreCase("entry");
        }

        private static boolean isInlineMediaObjectTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("inlinemediaobject");
        }

        private static boolean isMediaObjectTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("mediaobject");
        }

        private static boolean isImageDataTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("imagedata");
        }

        private static boolean isImageObjectTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("imageobject");
        }

        private static boolean isCaptionTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("caption");
        }
    }

    private static class SAXHandler extends DefaultHandler {
        private Locator locator;
        private Path folder;

        private XWPFDocument doc;
        private XWPFParagraph paragraph;
        private int paragraphNumber = -1;
        private XWPFRun run;
        private XWPFTable table; // TODO: What about nested tables? Really care about this case? Would need to stack them...
        private XWPFTableRow tableRow;
        private int tableRowNumber = -1;
        private XWPFTableCell tableColumn;
        private int tableColumnNumber = -1;

        private Deque<Level> currentLevel = new ArrayDeque<>();
        private int currentSectionDepth = 0; // 0: root; >0: sections.
        private List<Formatting> currentFormatting = new ArrayList<>(); // Order: FIFO, i.e. first tag met in
        // the document is the first one in the vector. TODO: migrate to Deque?

        SAXHandler(Path folder) throws IOException {
            this.folder = folder;

            // Start a document with the template that defines all needed styles.
            doc = new XWPFDocument(new FileInputStream(MainCommand.toDocxTemplate));

            // The template always contains empty paragraphs, remove them (just to have a clean output).
            // Either it is just an empty document, or these paragraphs are used so that Word has no latent style.
            int nParagraphsToRemove = doc.getBodyElements().size();
            while (nParagraphsToRemove >= 0) {
                doc.removeBodyElement(0);
                nParagraphsToRemove -= 1;
            }
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }

        private void ensureNoTextAllowed() {
            paragraph = null;
            run = null;
        }

        private String getLocationString() {
            return locator.getSystemId() + " line " + locator.getLineNumber() + ", " +
                    "column " + locator.getColumnNumber() + ": ";
        }

        private void setRunFormatting() throws SAXException {
            for (Formatting f: currentFormatting) {
                switch (f) {
                    case EMPHASIS:
                        run.setItalic(true);
                        break;
                    case EMPHASIS_BOLD:
                        run.setBold(true);
                        break;
                    case EMPHASIS_UNDERLINE:
                        run.setUnderline(UnderlinePatterns.SINGLE);
                        break;
                    case EMPHASIS_STRIKETHROUGH:
                        run.setStrikeThrough(true);
                        break;
                    default:
                        throw new SAXException("Formatting not recognised by setRunFormatting: " + f);
                }
            }
        }

        @SuppressWarnings("StatementWithEmptyBody")
        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if (SAXHelpers.isRootTag(qName)) {
                currentLevel.push(Level.ROOT);
                ensureNoTextAllowed();
                warnUnknownAttributes(attributes);
            } else if (SAXHelpers.isInfoTag(qName)) {
                currentLevel.push((currentLevel.peek() == Level.ROOT) ? Level.ROOT_INFO : Level.SECTION_INFO);
                ensureNoTextAllowed();
                warnUnknownAttributes(attributes);
            } else if (SAXHelpers.isSectionTag(qName)) {
                currentLevel.push(Level.SECTION);
                currentSectionDepth += 1;
                paragraphNumber = 0;
                ensureNoTextAllowed();
                warnUnknownAttributes(attributes);
            } else if (SAXHelpers.isTitleTag(qName)) {
                paragraph = doc.createParagraph();
                if (currentSectionDepth == 0) {
                    paragraph.setStyle("Title");
                } else {
                    paragraph.setStyle("Heading" + currentSectionDepth);
                }
                run = paragraph.createRun();
                warnUnknownAttributes(attributes);
            } else if (SAXHelpers.isParagraphTag(qName)) {
                // For tables, the paragraph is automatically created within a table cell.
                if (currentLevel.peek() == Level.TABLE && paragraphNumber == 0) {
                    return;
                }

                if (paragraph != null || run != null) {
                    throw new SAXException(getLocationString() + "tried to create a new paragraph, but one was " +
                            "already being filled.");
                }

                if (currentLevel.peek() == Level.TABLE) {
                    paragraph = tableColumn.addParagraph();
                } else {
                    paragraph = doc.createParagraph();
                }
                run = paragraph.createRun();
                warnUnknownAttributes(attributes);
            } else if (SAXHelpers.isFormatting(qName)) {
                currentFormatting.add(Formatting.tagToFormatting(qName, attributes));

                // Create a new run if this one is already started.
                if (run.text().length() > 0) {
                    run = paragraph.createRun();
                }

                paragraph = doc.createParagraph();
                run = paragraph.createRun();
                setRunFormatting();
                warnUnknownAttributes(attributes);
            } else if (SAXHelpers.isTableTag(qName)) {
                currentLevel.push(Level.TABLE);

                table = doc.createTable();
                tableRowNumber = 0;
                tableColumnNumber = 0;
                warnUnknownAttributes(attributes);
            } else if (SAXHelpers.isTableBodyTag(qName)) {
                // Nothing to do, just go inside the table.
                warnUnknownAttributes(attributes);
            } else if (SAXHelpers.isTableRowTag(qName)) {
                if (table == null || tableRowNumber < 0) {
                    throw new SAXException(getLocationString() + "unexpected table row.");
                }

                // If this is the first row, exploit the one automatically created by the table.
                if (tableRowNumber == 0) {
                    tableRow = table.getRow(0);
                } else {
                    tableRow = table.createRow();
                }
                tableColumnNumber = 0;
                warnUnknownAttributes(attributes);
            } else if (SAXHelpers.isTableColumnTag(qName)) {
                if (table == null || tableRowNumber < 0 || tableColumnNumber < 0) {
                    throw new SAXException(getLocationString() + "unexpected table column.");
                }

                // If this is the first column of the row, exploit the one automatically created by the row.
                // If there has already been a complete row, several columns have already been generated.
                if (tableColumnNumber == 0) {
                    tableColumn = tableRow.getCell(0);
                } else if (tableRowNumber > 0 && tableRow.getTableCells().size() >= tableColumnNumber) {
                    tableColumn = tableRow.getCell(tableRowNumber);
                } else {
                    tableColumn = tableRow.createCell();
                }
                paragraph = tableColumn.getParagraphs().get(0);
                run = paragraph.createRun();
                warnUnknownAttributes(attributes);
            } else if (SAXHelpers.isTableHeaderTag(qName) || SAXHelpers.isTableFooterTag(qName)) {
                throw new SAXException(getLocationString() + "table headers/footers are not handled.");
            } else if (SAXHelpers.isCALSTag(qName)) {
                throw new SAXException(getLocationString() + "CALS tables are not handled.");
            } else if (SAXHelpers.isInlineMediaObjectTag(qName)) {
//                run.addPicture(new FileInputStream(imgFile), XWPFDocument.PICTURE_TYPE_PNG, imgFile, Units.toEMU(50), Units.toEMU(50));
                warnUnknownAttributes(attributes);
            } else if (SAXHelpers.isMediaObjectTag(qName)) {
                paragraph = doc.createParagraph();
                Map<String, String> attr = attributes(attributes);
                if (attr.containsKey("align")) {
                    paragraph.setAlignment(attributeToAlignment(attr.get("align").toLowerCase()));
                }
                warnUnknownAttributes(attr, Stream.of("align"));
                run = paragraph.createRun();
            } else if (SAXHelpers.isImageDataTag(qName)) {
                createImage(attributes); // Already warns about unknown attributes.
            } else if (SAXHelpers.isImageObjectTag(qName)) {
                // Nothing to do, as everything is under <db:imagedata>.
                // TODO: check if there is only one imagedata per imageobject?
                warnUnknownAttributes(attributes);
            } else if (SAXHelpers.isCaptionTag(qName)) {
                paragraph = doc.createParagraph();
                paragraph.setStyle("Caption");
                run = paragraph.createRun();
                warnUnknownAttributes(attributes);
            } else {
                throw new SAXException(getLocationString() + "unknown tag " + qName + ".");
            }

            // There might be return instructions in the long switch.
        }

        private Map<String, String> attributes(Attributes attributes) {
            Map<String, String> d = new HashMap<>();
            for (int i = 0; i < attributes.getLength(); ++i) {
                d.put(SAXHelpers.qNameToTagName(attributes.getLocalName(i)), attributes.getValue(i));
            }
            return d;
        }

        private void warnUnknownAttributes(Map<String, String> attr) {
            for (String key: attr.keySet()) {
                System.out.println(getLocationString() + "unknown attribute " + key + ".");
            }
        }

        private void warnUnknownAttributes(Attributes attr) {
            warnUnknownAttributes(attributes(attr));
        }

        private void warnUnknownAttributes(Map<String, String> attr, Stream<String> recognised) {
            Map<String, String> unknown = new HashMap<>(attr);
            for (String s: recognised.collect(Collectors.toCollection(Vector::new))) {
                unknown.remove(s);
            }
            warnUnknownAttributes(unknown);
        }

        private int parseMeasurementAsEMU(String m) throws SAXException {
            if (m == null) {
                throw new SAXException(getLocationString() + "invalid measured quantity.");
            }

            if (m.endsWith("in")) {
                return Integer.parseInt(m.replace("in", "")) * 914_400;
            } else if (m.endsWith("pt")) {
                return Units.toEMU(Integer.parseInt(m.replace("pt", "")));
            } else if (m.endsWith("cm")) {
                return Integer.parseInt(m.replace("cm", "")) * Units.EMU_PER_CENTIMETER;
            } else if (m.endsWith("mm")) {
                return Integer.parseInt(m.replace("mm", "")) * Units.EMU_PER_CENTIMETER / 10;
            } else {
                throw new SAXException(getLocationString() + "unknown measurement unit in " + m + ".");
            }
        }

        private void createImage(Attributes attributes) throws SAXException {
            Map<String, String> attr = attributes(attributes);
            String filename = attr.get("fileref");
            Path filePath = Paths.get(filename);
            int width = 1;//parseMeasurementAsEMU(attr.get("width"));
            int height = 1;//parseMeasurementAsEMU(attr.get("height"));
            warnUnknownAttributes(attr, Stream.of("fileref", "width", "height"));

            int format = filenameToWordFormat(filename);
            if (format < 0) {
                throw new SAXException(getLocationString() + "unknown image extension " + filename + ".");
            }

            try {
                run.addPicture(new FileInputStream(folder.resolve(filePath).toFile()), format,
                        filePath.getFileName().toString(), width, height);
            } catch (Exception e) {
                throw new SAXException(e);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (SAXHelpers.isRootTag(qName)) {
                ensureNoTextAllowed();
            } else if (SAXHelpers.isInfoTag(qName)) {
                currentLevel.pop();
                ensureNoTextAllowed();
            } else if (SAXHelpers.isSectionTag(qName)) {
                currentLevel.pop();
                currentSectionDepth -= 1;
                ensureNoTextAllowed();
            } else if (SAXHelpers.isTitleTag(qName)) {
                ensureNoTextAllowed();
            } else if (SAXHelpers.isParagraphTag(qName)) {
                paragraphNumber += 1;
                ensureNoTextAllowed();
            } else if (SAXHelpers.isFormatting(qName)) {
                // Remove the last formatting tag found. Throw an exception if it should not have been added by emphasis.
                Formatting f = currentFormatting.get(currentFormatting.size() - 1);
                if (f != Formatting.EMPHASIS && f != Formatting.EMPHASIS_BOLD && f != Formatting.EMPHASIS_UNDERLINE &&
                        f != Formatting.EMPHASIS_STRIKETHROUGH) {
                    throw new SAXException("Formatting " + f + " should not have been added by an emphasis tag.");
                }

                currentFormatting.remove(currentFormatting.size() - 1);
            } else if (SAXHelpers.isTableTag(qName)) {
                currentLevel.pop();
                tableRowNumber = -1;
                tableColumnNumber = -1;
                paragraphNumber = 0;
                ensureNoTextAllowed();
            } else if (SAXHelpers.isTableBodyTag(qName)) {
                // Nothing to do, just go outside the table.
                ensureNoTextAllowed();
            } else if (SAXHelpers.isTableRowTag(qName)) {
                tableRow = null;
                tableColumn = null;
                tableRowNumber += 1;
                ensureNoTextAllowed();
            } else if (SAXHelpers.isTableColumnTag(qName)) {
                tableColumn = null;
                tableColumnNumber += 1;
                ensureNoTextAllowed();
            } else if (SAXHelpers.isTableHeaderTag(qName) || SAXHelpers.isTableFooterTag(qName)) {
                throw new SAXException(getLocationString() + "table headers/footers are not handled.");
            } else if (SAXHelpers.isCALSTag(qName)) {
                throw new SAXException(getLocationString() + "CALS tables are not handled.");
            } else if (SAXHelpers.isInlineMediaObjectTag(qName)) {
                ensureNoTextAllowed();
            } else if (SAXHelpers.isMediaObjectTag(qName)) {
                ensureNoTextAllowed();
            } else if (SAXHelpers.isImageDataTag(qName)) {
                ensureNoTextAllowed();
            } else if (SAXHelpers.isImageObjectTag(qName)) {
                ensureNoTextAllowed();
            } else if (SAXHelpers.isCaptionTag(qName)) {
                ensureNoTextAllowed();
            } else {
                throw new SAXException(getLocationString() + "unknown tag " + qName + ".");
            }

            // There might be return instructions in the long switch.
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            String content = new String(ch, start, length);

            if (content.replaceAll("(\\s|\n)+", "").length() == 0) {
                return;
            }

            if (run == null) {
                throw new SAXException(getLocationString() + "document invalid, text not expected here.");
            }

            run.setText(content);
        }
    }
}
