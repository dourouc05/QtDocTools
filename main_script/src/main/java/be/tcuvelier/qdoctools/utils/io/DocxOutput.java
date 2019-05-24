package be.tcuvelier.qdoctools.utils.io;

import be.tcuvelier.qdoctools.cli.MainCommand;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTHyperlink;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.imageio.ImageIO;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DocxOutput {
    public static void main(String[] args) throws Exception {
//        String test = "basic";
//        String test = "sections";
//        String test = "images";
        String test = "lists";

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
        TABLE,
        ITEMIZED_LIST, ORDERED_LIST
    }

    private static class LevelStack {
        // Slight interface on top of a stack (internally, a Deque) to provide some facilities when peeking, based on
        // the values of Level.

        private Deque<Level> levels = new ArrayDeque<>();

        void push(Level l) {
            levels.push(l);
        }

        void pop() {
            levels.pop();
        }

        boolean pop(Level l) {
            return pop(Stream.of(l));
        }

        boolean pop(Stream<Level> ls) {
            return pop(ls.collect(Collectors.toSet()));
        }

        boolean pop(Set<Level> ls) {
            if (! ls.contains(levels.peek())) {
                return false;
            }

            pop();
            return true;
        }

        void pop(Level l, SAXException t) throws SAXException {
            if (! pop(l)) {
                throw t;
            }
        }

        void pop(Stream<Level> l, SAXException t) throws SAXException {
            if (! pop(l)) {
                throw t;
            }
        }

        Level peek() {
            return levels.peek();
        }

        boolean peekRoot() {
            return peek() == Level.ROOT;
        }

        boolean peekTable() {
            return peek() == Level.TABLE;
        }

        boolean peekList() {
            return peek() == Level.ITEMIZED_LIST || peek() == Level.ORDERED_LIST;
        }
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
            case "emf":  return XWPFDocument.PICTURE_TYPE_EMF;
            case "wmf":  return XWPFDocument.PICTURE_TYPE_WMF;
            case "pict": return XWPFDocument.PICTURE_TYPE_PICT;
            case "jpg":
            case "jpeg": return XWPFDocument.PICTURE_TYPE_JPEG;
            case "png":  return XWPFDocument.PICTURE_TYPE_PNG;
            case "dib":  return XWPFDocument.PICTURE_TYPE_DIB;
            case "gif":  return XWPFDocument.PICTURE_TYPE_GIF;
            case "tif":
            case "tiff": return XWPFDocument.PICTURE_TYPE_TIFF;
            case "eps":  return XWPFDocument.PICTURE_TYPE_EPS;
            case "bmp":  return XWPFDocument.PICTURE_TYPE_BMP;
            case "wpg":  return XWPFDocument.PICTURE_TYPE_WPG;
        }
        return -1;
    }

    private static class SAXHelpers {
        private static Map<String, String> attributes(Attributes attributes) {
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

        private static boolean isLinkTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("link");
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

        private static boolean isItemizedListTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("itemizedlist");
        }

        private static boolean isOrderedListTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("orderedlist");
        }

        private static boolean isListItemTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("listitem");
        }
    }

    private static class SAXHandler extends DefaultHandler {
        private Locator locator;
        private Path folder;

        private XWPFDocument doc;
        private XWPFParagraph paragraph;
        private int paragraphNumber = -1;
        private XWPFRun run;
        private BigInteger numbering;
        private BigInteger lastFilledNumbering = BigInteger.ZERO;
        private int numberingItemNumber = -1;
        private int numberingItemParagraphNumber = -1;
        private XWPFTable table; // TODO: What about nested tables? Really care about this case? Would need to stack them...
        private XWPFTableRow tableRow;
        private int tableRowNumber = -1;
        private XWPFTableCell tableColumn;
        private int tableColumnNumber = -1;

        private LevelStack currentLevel = new LevelStack();
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

        /** Error and warning management **/

        @Override
        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }

        private String getLocationString() {
            return locator.getSystemId() + " line " + locator.getLineNumber() + ", " +
                    "column " + locator.getColumnNumber() + ": ";
        }

        private class DocxException extends SAXException {
            DocxException(String message) {
                super(getLocationString() + message);
            }
            DocxException (String message, Exception e) {
                super(getLocationString() + message, e);
            }
        }

        private void warnUnknownAttributes(Map<String, String> attr) {
            for (String key: attr.keySet()) {
                System.out.println(getLocationString() + "unknown attribute " + key + ".");
            }
        }

        private void warnUnknownAttributes(Attributes attr) {
            warnUnknownAttributes(SAXHelpers.attributes(attr));
        }

        private void warnUnknownAttributes(Map<String, String> attr, Stream<String> recognised) {
            Map<String, String> unknown = new HashMap<>(attr);
            for (String s: recognised.collect(Collectors.toCollection(Vector::new))) {
                unknown.remove(s);
            }
            warnUnknownAttributes(unknown);
        }

        /** Miscellaneous helpers that must reside within SAXHandler **/

        private int parseMeasurementAsEMU(String m) throws SAXException {
            if (m == null) {
                throw new DocxException("invalid measured quantity.");
            }

            if (m.endsWith("in")) {
                return Integer.parseInt(m.replace("in", "")) * 914_400;
            } else if (m.endsWith("pt")) {
                return Units.toEMU(Integer.parseInt(m.replace("pt", "")));
            } else if (m.endsWith("cm")) {
                return Integer.parseInt(m.replace("cm", "")) * Units.EMU_PER_CENTIMETER;
            } else if (m.endsWith("mm")) {
                return Integer.parseInt(m.replace("mm", "")) * Units.EMU_PER_CENTIMETER / 10;
            } else if (m.endsWith("px")) {
                return Integer.parseInt(m.replace("px", "")) * Units.EMU_PER_PIXEL;
            } else {
                throw new DocxException("unknown measurement unit in " + m + ".");
            }
        }

        /** POI helpers **/

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
                        throw new DocxException("formatting not recognised by setRunFormatting: " + f);
                }
            }
        }

        private void ensureNoTextAllowed() {
            paragraph = null;
            run = null;
        }

        private XWPFHyperlinkRun createHyperlinkRun(String uri) {
            // https://stackoverflow.com/questions/55275241/how-to-add-a-hyperlink-to-the-footer-of-a-xwpfdocument-using-apache-poi
            String rId = paragraph.getPart().getPackagePart().addExternalRelationship(
                    uri, XWPFRelation.HYPERLINK.getRelation()
            ).getId();

            CTHyperlink ctHyperLink = paragraph.getCTP().addNewHyperlink();
            ctHyperLink.setId(rId);
            ctHyperLink.addNewR();

            return new XWPFHyperlinkRun(ctHyperLink, ctHyperLink.getRArray(0), paragraph);
        }

        private void createImage(Attributes attributes) throws SAXException {
            Map<String, String> attr = SAXHelpers.attributes(attributes);

            if (! attr.containsKey("fileref")) {
                throw new DocxException("the image tag has no fileref attribute.");
            }

            String filename = attr.get("fileref");
            Path filePath = folder.resolve(filename);
            int width;
            int height;

            // Get the image width and height: either from the XML or from the image itself.
            // Avoid loading the image if both dimensions are known from the XML.
            {
                String imageWidth = null;
                String imageHeight = null;
                if (! attr.containsKey("width") && ! attr.containsKey("height")) {
                    try {
                        BufferedImage img = ImageIO.read(filePath.toFile());
                        imageWidth = img.getWidth() + "px";
                        imageHeight = img.getHeight() + "px";
                    } catch (IOException e) {
                        throw new DocxException("there was a problem reading the image", e);
                    }
                }

                width = parseMeasurementAsEMU(attr.getOrDefault("width", imageWidth));
                height = parseMeasurementAsEMU(attr.getOrDefault("height", imageHeight));
            }

            warnUnknownAttributes(attr, Stream.of("fileref", "width", "height"));

            int format = filenameToWordFormat(filename);
            if (format < 0) {
                throw new DocxException("unknown image extension " + filename + ".");
            }

            try {
                run.addPicture(new FileInputStream(filePath.toFile()), format,
                        filePath.getFileName().toString(), width, height);
            } catch (IOException | InvalidFormatException e) {
                throw new DocxException("there was a problem reading the image", e);
            }
        }

        private BigInteger createNumbering() {
            // Based on https://github.com/apache/poi/blob/trunk/src/ooxml/testcases/org/apache/poi/xwpf/usermodel/TestXWPFNumbering.java
            // A bit of inspiration from https://coderanch.com/t/649584/java/create-Bullet-Square-Word-POI

            // Create a numbering identifier (find the first empty).
            // lastFilledNumbering is only used to speed up the computations.
            XWPFNumbering numbering = doc.createNumbering();
            BigInteger abstractNumId = lastFilledNumbering;
            {
                // First, test id == 1 (start at zero, increase, check if 1 exists: if not, loop).
                Object o;
                do {
                    abstractNumId = abstractNumId.add(BigInteger.ONE);
                    o = numbering.getAbstractNum(abstractNumId);
                } while (o != null);
            }
            lastFilledNumbering = abstractNumId;

            return numbering.addNum(abstractNumId);

            // TODO: distinction between bullets and numbers? For now, just numbers...
            // https://github.com/apache/poi/blob/trunk/src/ooxml/testcases/org/apache/poi/xwpf/usermodel/TestXWPFNumbering.java
            // https://coderanch.com/t/649584/java/create-Bullet-Square-Word-POI
            // https://stackoverflow.com/questions/43155172/how-can-i-add-list-in-poi-word-ordered-number-or-other-symbol-for-list-symbol
            // http://mail-archives.apache.org/mod_mbox/poi-user/201209.mbox/%3C1346490025472-5710829.post@n5.nabble.com%3E
        }

        /** Actual SAX handler **/

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            if (SAXHelpers.isRootTag(qName)) {
                currentLevel.push(Level.ROOT);
                ensureNoTextAllowed();
                // Don't warn about unknown attributes, as it will most likely just be version and name spaces.
            } else if (SAXHelpers.isInfoTag(qName)) {
                currentLevel.push(currentLevel.peekRoot() ? Level.ROOT_INFO : Level.SECTION_INFO);
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
                if (currentLevel.peekTable() && paragraphNumber == 0) {
                    return;
                }

                if (paragraph != null || run != null) {
                    throw new DocxException("tried to create a new paragraph, but one was already being filled.");
                }

                if (currentLevel.peekTable()) {
                    paragraph = tableColumn.addParagraph();
                } else {
                    paragraph = doc.createParagraph();

                    if (currentLevel.peekList()) {
                        paragraph.setNumID(numbering);

                        if (numberingItemParagraphNumber > 0) {
                            throw new DocxException("more than one paragraph in a list item, this is not supported.");
                            // How to make the difference when decoding this back? For implementation, maybe hack
                            // something based on https://stackoverflow.com/a/43164999/1066843.
                        }
                    }
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
            } else if (SAXHelpers.isLinkTag(qName)) {
                Map<String, String> attr = SAXHelpers.attributes(attributes);
                warnUnknownAttributes(attr, Stream.of("href"));

                // Always create a new run, as it is much easier than to replace a run within the paragraph.
                run = createHyperlinkRun(attr.get("href"));

                // Set formatting for the link.
                run.setUnderline(UnderlinePatterns.SINGLE);
                run.setColor("0563c1");
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
                    throw new DocxException("unexpected table row.");
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
                    throw new DocxException("unexpected table column.");
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
                throw new DocxException("table headers/footers are not handled.");
            } else if (SAXHelpers.isCALSTag(qName)) {
                throw new DocxException("CALS tables are not handled.");
            } else if (SAXHelpers.isInlineMediaObjectTag(qName)) {
//                run.addPicture(new FileInputStream(imgFile), XWPFDocument.PICTURE_TYPE_PNG, imgFile, Units.toEMU(50), Units.toEMU(50));
                warnUnknownAttributes(attributes);
            } else if (SAXHelpers.isMediaObjectTag(qName)) {
                paragraph = doc.createParagraph();
                Map<String, String> attr = SAXHelpers.attributes(attributes);
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
            } else if (SAXHelpers.isItemizedListTag(qName) || SAXHelpers.isOrderedListTag(qName)) {
                if (currentLevel.peekList()) {
                    throw new DocxException("list within list not yet implemented.");
                }

                numbering = createNumbering();
                if (SAXHelpers.isItemizedListTag(qName)) {
                    currentLevel.push(Level.ITEMIZED_LIST);
                } else if (SAXHelpers.isOrderedListTag(qName)) {
                    currentLevel.push(Level.ORDERED_LIST);
                }

                numberingItemNumber = 0;
                numberingItemParagraphNumber = -1;

//                paragraph = doc.createParagraph();
//                paragraph.setNumID(numbering);
//
//                run = paragraph.createRun();

                ensureNoTextAllowed();
                warnUnknownAttributes(attributes);
            } else if (SAXHelpers.isListItemTag(qName)) {
                if (! currentLevel.peekList()) {
                    throw new DocxException("unexpected listitem.");
                }
                ensureNoTextAllowed();
                warnUnknownAttributes(attributes);
                // listitem is just a container for para, so barely nothing to do here.
                numberingItemNumber = -1;
                numberingItemParagraphNumber = 0;
            } else {
                throw new DocxException("unknown tag " + qName + ".");
            }

            // There might be return instructions in the long switch.
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            if (SAXHelpers.isRootTag(qName)) {
                ensureNoTextAllowed();
            } else if (SAXHelpers.isInfoTag(qName)) {
                currentLevel.pop(Stream.of(Level.ROOT_INFO, Level.SECTION_INFO), new DocxException("unexpected end of info"));
                ensureNoTextAllowed();
            } else if (SAXHelpers.isSectionTag(qName)) {
                currentLevel.pop(Level.SECTION, new DocxException("unexpected end of section"));
                currentSectionDepth -= 1;
                ensureNoTextAllowed();
            } else if (SAXHelpers.isTitleTag(qName)) {
                ensureNoTextAllowed();
            } else if (SAXHelpers.isParagraphTag(qName)) {
                paragraphNumber += 1;

                if (currentLevel.peekList()) {
                    numberingItemParagraphNumber += 1;
                }

                ensureNoTextAllowed();
            } else if (SAXHelpers.isFormatting(qName)) {
                // Remove the last formatting tag found. Throw an exception if it should not have been added by emphasis.
                Formatting f = currentFormatting.get(currentFormatting.size() - 1);
                if (f != Formatting.EMPHASIS && f != Formatting.EMPHASIS_BOLD && f != Formatting.EMPHASIS_UNDERLINE &&
                        f != Formatting.EMPHASIS_STRIKETHROUGH) {
                    throw new DocxException("formatting " + f + " should not have been added by an emphasis tag.");
                }

                currentFormatting.remove(currentFormatting.size() - 1);
            } else if (SAXHelpers.isLinkTag(qName)) {
                // Create a new run, so that the new text is not within the same run.
                run = paragraph.createRun();
            } else if (SAXHelpers.isTableTag(qName)) {
                currentLevel.pop(Level.TABLE, new DocxException("unexpected end of table"));
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
                throw new DocxException("table headers/footers are not handled.");
            } else if (SAXHelpers.isCALSTag(qName)) {
                throw new DocxException("CALS tables are not handled.");
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
            } else if (SAXHelpers.isItemizedListTag(qName) || SAXHelpers.isOrderedListTag(qName)) {
                currentLevel.pop(Stream.of(Level.ORDERED_LIST, Level.ITEMIZED_LIST), new DocxException("unexpected end of list"));

                if (SAXHelpers.isItemizedListTag(qName)) {
                    currentLevel.push(Level.ITEMIZED_LIST);
                } else if (SAXHelpers.isOrderedListTag(qName)) {
                    currentLevel.push(Level.ORDERED_LIST);
                }

                numberingItemNumber = -1;
                numberingItemParagraphNumber = -1;

                ensureNoTextAllowed();
            } else if (SAXHelpers.isListItemTag(qName)) {
                if (currentLevel.peek() != Level.ORDERED_LIST && currentLevel.peek() != Level.ITEMIZED_LIST) {
                    throw new DocxException("unexpected end of listitem.");
                }
                ensureNoTextAllowed();
                numberingItemNumber += 1;
                numberingItemParagraphNumber = -1;
            } else {
                throw new DocxException("unknown tag " + qName + ".");
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
                throw new DocxException("invalid document, text not expected here.");
            }

            run.setText(content);
        }
    }
}
