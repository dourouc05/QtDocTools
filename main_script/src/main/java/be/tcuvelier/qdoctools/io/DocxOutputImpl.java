package be.tcuvelier.qdoctools.io;

import be.tcuvelier.qdoctools.cli.MainCommand;
import be.tcuvelier.qdoctools.io.helpers.DocBookAlignment;
import be.tcuvelier.qdoctools.io.helpers.DocBookBlock;
import be.tcuvelier.qdoctools.io.helpers.DocBookFormatting;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.imageio.ImageIO;
import javax.xml.stream.XMLStreamException;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DocxOutputImpl extends DefaultHandler {
    private enum Level {
        ROOT_ARTICLE, ROOT_ARTICLE_INFO, ROOT_BOOK, ROOT_BOOK_INFO, PART, PART_INFO, CHAPTER, CHAPTER_INFO,
        SECTION, SECTION_INFO,
        TABLE,
        ITEMIZED_LIST, ORDERED_LIST, SEGMENTED_LIST, SEGMENTED_LIST_TITLE, VARIABLE_LIST,
        BLOCK_PREFORMATTED
    }

    /**
     * A few design notes.
     * - Avoid SDTs. Two reasons: they are not really supported within POI (but you can work around it); they are not
     *   really supported by LibreOffice, at least with 6.1.3.2 (not shown on screen as different from the rest of
     *   the code; many bug reports related to loss of information when saving as DOCX).
     */

    private static class LevelStack {
        // Slight interface on top of a stack (internally, a Deque) to provide some facilities when peeking, based on
        // the values of Level.

        private Deque<Level> levels = new ArrayDeque<>();
        private int listDepth = 0;

        void push(Level l) {
            levels.push(l);

            if (l == Level.ITEMIZED_LIST || l == Level.ORDERED_LIST) {
                listDepth += 1;
            }
        }

        private void pop() {
            Level l = levels.pop();

            if (l == Level.ITEMIZED_LIST || l == Level.ORDERED_LIST) {
                listDepth -= 1;
            }
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

        boolean peekRootArticle() {
            return peek() == Level.ROOT_ARTICLE;
        }

        boolean peekRootArticleInfo() {
            return peek() == Level.ROOT_ARTICLE_INFO;
        }

        boolean peekRootBook() {
            return peek() == Level.ROOT_BOOK;
        }

        boolean peekRootBookInfo() {
            return peek() == Level.ROOT_BOOK_INFO;
        }

        boolean peekPart() {
            return peek() == Level.PART;
        }

        boolean peekChapter() {
            return peek() == Level.CHAPTER;
        }

        boolean peekSection() {
            return peek() == Level.SECTION;
        }

        boolean peekSectionInfo() {
            return peek() == Level.SECTION_INFO;
        }

        boolean peekBlockPreformatted() {
            return peek() == Level.BLOCK_PREFORMATTED;
        }

        boolean peekTable() {
            return peek() == Level.TABLE;
        }

        boolean peekList() {
            return peekItemizedList() || peekOrderedList();
        }

        boolean peekItemizedList() {
            return peek() == Level.ITEMIZED_LIST;
        }

        boolean peekOrderedList() {
            return peek() == Level.ORDERED_LIST;
        }

        int countListDepth() {
            return listDepth;
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        boolean peekSegmentedList() {
            return peek() == Level.SEGMENTED_LIST;
        }

        boolean peekSegmentedListTitle() {
            return peek() == Level.SEGMENTED_LIST_TITLE;
        }

        boolean peekVariableList() {
            return peek() == Level.VARIABLE_LIST;
        }
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

        private static boolean compare(String qName, String reference) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase(reference);
        }

        private static boolean isInfoTag(String qName) {
            return compare(qName, "info");
        }

        private static boolean isAbstractTag(String qName) {
            return compare(qName, "abstract");
        }

        private static boolean isPartIntroTag(String qName) {
            return compare(qName, "partintro");
        }

        private static boolean isTitleTag(String qName) {
            return compare(qName, "title");
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

        private static boolean isFormatting(String qName) {
            return DocBookFormatting.isRunFormatting(qName) || DocBookFormatting.isInlineFormatting(qName);
        }

        private static boolean isLinkTag(String qName) {
            return compare(qName, "link");
        }

        private static boolean isTableTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("informaltable")
                    || localName.equalsIgnoreCase("table");
        }

        private static boolean isTableHeaderTag(String qName) {
            return compare(qName, "thead");
        }

        private static boolean isTableBodyTag(String qName) {
            return compare(qName, "tbody");
        }

        private static boolean isTableFooterTag(String qName) {
            return compare(qName, "tfoot");
        }

        private static boolean isTableRowTag(String qName) {
            return compare(qName, "tr");
        }

        private static boolean isTableColumnTag(String qName) {
            return compare(qName, "td");
        }

        private static boolean isCALSTag(String qName) {
            String localName = qNameToTagName(qName);
            return localName.equalsIgnoreCase("tgroup")
                    || localName.equalsIgnoreCase("colspec")
                    || localName.equalsIgnoreCase("row")
                    || localName.equalsIgnoreCase("entry");
        }

        private static boolean isProgramListingTag(String qName) {
            return compare(qName, "programlisting");
        }

        private static boolean isScreenTag(String qName) {
            return compare(qName, "screen");
        }

        private static boolean isSynopsisTag(String qName) {
            return compare(qName, "synopsis");
        }

        private static boolean isLiteralLayoutTag(String qName) {
            return compare(qName, "literallayout");
        }

        private static boolean isInlineMediaObjectTag(String qName) {
            return compare(qName, "inlinemediaobject");
        }

        private static boolean isMediaObjectTag(String qName) {
            return compare(qName, "mediaobject");
        }

        private static boolean isImageDataTag(String qName) {
            return compare(qName, "imagedata");
        }

        private static boolean isImageObjectTag(String qName) {
            return compare(qName, "imageobject");
        }

        private static boolean isCaptionTag(String qName) {
            return compare(qName, "caption");
        }

        private static boolean isItemizedListTag(String qName) {
            return compare(qName, "itemizedlist");
        }

        private static boolean isOrderedListTag(String qName) {
            return compare(qName, "orderedlist");
        }

        private static boolean isListItemTag(String qName) {
            return compare(qName, "listitem");
        }

        private static boolean isSegmentedListTag(String qName) {
            return compare(qName, "segmentedlist");
        }

        private static boolean isSegmentedListTitleTag(String qName) {
            return compare(qName, "segtitle");
        }

        private static boolean isSegmentedListItemTag(String qName) {
            return compare(qName, "seglistitem");
        }

        private static boolean isSegmentedListItemValueTag(String qName) {
            return compare(qName, "seg");
        }

        private static boolean isVariableListTag(String qName) {
            return compare(qName, "variablelist");
        }

        private static boolean isVariableListItemTag(String qName) {
            return compare(qName, "varlistentry");
        }

        private static boolean isVariableListItemDefinitionTag(String qName) {
            return compare(qName, "term");
        }
    }

    /** Internal variables. **/

    private Locator locator;
    private Path folder;
    XWPFDocument doc;
    private POIHelpers h = new POIHelpers();

    private XWPFParagraph paragraph;
    private int paragraphNumber = -1;
    private XWPFRun run;
    private String paragraphStyle = "Normal";
    private int runNumber = -1;
    private int runCharactersNumber = -1;

    private BigInteger numbering;
    private BigInteger lastFilledNumbering = BigInteger.ZERO;
    private int numberingItemParagraphNumber = -1;
    private List<String> segmentedListHeaders; // TODO: Limitation for ease of implementation: no styling stored in this list, just raw headers.
    private int segmentNumber = -1;

    private XWPFTable table; // TODO: What about nested tables? Really care about this case? Would need to stack them...
    private XWPFTableRow tableRow;
    private int tableRowNumber = -1;
    private XWPFTableCell tableCell;
    private int tableColumnNumber = -1;

    private LevelStack currentLevel = new LevelStack();
    private int currentSectionDepth = 0; // 0: root; >0: sections.
    private List<DocBookFormatting> currentFormatting = new ArrayList<>(); // Order: FIFO, i.e. first tag met in
    // the document is the first one in the vector. TODO: migrate to Deque?

    DocxOutputImpl(Path folder) throws IOException {
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

    /** POI-level helpers. These functions are not really specific to this project and could be upstreamed. **/
    // Cannot be a static class, as some methods require access to the current document.

    private class POIHelpers {
        private int filenameToWordFormat(String filename) {
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

        private CTLevelText createText(String t) {
            CTLevelText ct = CTLevelText.Factory.newInstance();
            ct.setVal(t);
            return ct;
        }

        private CTRPr createFont(String name) {
            CTFonts fonts = CTFonts.Factory.newInstance();
            fonts.setAscii(name);
            fonts.setHAnsi(name);
            fonts.setCs(name);
            fonts.setHint(STHint.DEFAULT);

            CTRPr font = CTRPr.Factory.newInstance();
            font.setRFonts(fonts);
            return font;
        }

        private XWPFHyperlinkRun createHyperlinkRun(String uri) {
            // https://stackoverflow.com/questions/55275241/how-to-add-a-hyperlink-to-the-footer-of-a-xwpfdocument-using-apache-poi
            // https://github.com/apache/poi/pull/153
            // Create a relationship ID for this link.
            String rId = paragraph.getPart().getPackagePart().addExternalRelationship(
                    uri, XWPFRelation.HYPERLINK.getRelation()
            ).getId();

            // Create the run.
            CTHyperlink ctHyperLink = paragraph.getCTP().addNewHyperlink();
            ctHyperLink.setId(rId);
            ctHyperLink.addNewR();

            // Append this run to the paragraph.
            XWPFHyperlinkRun link = new XWPFHyperlinkRun(ctHyperLink, ctHyperLink.getRArray(0), paragraph);
            paragraph.addRun(link);
            return link;
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

        private BigInteger createNumbering(boolean isOrdered) {
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

            // Create the abstract numbering from scratch.
            // Inspired by
            // https://stackoverflow.com/questions/1940911/openxml-2-sdk-word-document-create-bulleted-list-programmatically
            // http://officeopenxml.com/WPnumbering.php
            CTAbstractNum ctAbstractNum = CTAbstractNum.Factory.newInstance();
            ctAbstractNum.setAbstractNumId(abstractNumId);
            {
                CTMultiLevelType ctMultiLevelType = CTMultiLevelType.Factory.newInstance();
                ctMultiLevelType.setVal(STMultiLevelType.HYBRID_MULTILEVEL);
                ctAbstractNum.setMultiLevelType(ctMultiLevelType);
            }
            {
                // Define many useful values that will be used several times later on (only in this scope).
                CTDecimalNumber one = CTDecimalNumber.Factory.newInstance();
                one.setVal(BigInteger.ONE);

                CTNumFmt bullet = CTNumFmt.Factory.newInstance();
                bullet.setVal(STNumberFormat.BULLET);
                CTNumFmt decimal = CTNumFmt.Factory.newInstance();
                decimal.setVal(STNumberFormat.DECIMAL);
                CTNumFmt lowerLetter = CTNumFmt.Factory.newInstance();
                lowerLetter.setVal(STNumberFormat.LOWER_LETTER);
                CTNumFmt lowerRoman = CTNumFmt.Factory.newInstance();
                lowerRoman.setVal(STNumberFormat.LOWER_ROMAN);

                CTLevelText charFullBullet = createText("\uF0B7"); // Not working in all fonts!
                CTLevelText charEmptyBullet = createText("o"); // Just an o.
                CTLevelText charFullSquare = createText("\uF0A7"); // Not working in all fonts!

                CTJc left = CTJc.Factory.newInstance();
                left.setVal(STJc.LEFT);

                CTRPr symbolFont = createFont("Symbol");
                CTRPr courierNewFont = createFont("Courier New");
                CTRPr wingdingsFont = createFont("Wingdings");

                // Create the various levels of this abstract numbering.
                for (int i = 0; i < 9; ++i) {
                    CTLvl lvl = ctAbstractNum.addNewLvl();
                    lvl.setIlvl(BigInteger.valueOf(i));
                    lvl.setStart(one);
                    lvl.setLvlJc(left);

                    // Actually define the bullets: either a real bullet, or a number (following what Word 2019 does).
                    if (! isOrdered) {
                        lvl.setNumFmt(bullet);
                        if (i % 3 == 0) {
                            lvl.setLvlText(charFullBullet);
                            lvl.setRPr(symbolFont);
                        } else if (i % 3 == 1) {
                            lvl.setLvlText(charEmptyBullet);
                            lvl.setRPr(courierNewFont);
                        } else {
                            lvl.setLvlText(charFullSquare);
                            lvl.setRPr(wingdingsFont);
                        }
                    } else {
                        if (i % 3 == 0) {
                            lvl.setNumFmt(decimal);
                        } else if (i % 3 == 1) {
                            lvl.setNumFmt(lowerLetter);
                        } else {
                            lvl.setNumFmt(lowerRoman);
                        }
                        lvl.setLvlText(createText("%" + (i + 1) + "."));
                    }

                    // Indentation values taken from Word 2019 (major difference with that reference: left vs start,
                    // see http://officeopenxml.com/WPindentation.php).
                    CTInd levelIndent = CTInd.Factory.newInstance();
                    levelIndent.setLeft(BigInteger.valueOf(720 * (i + 1)));
                    levelIndent.setHanging(BigInteger.valueOf(360));
                    CTPPr indent = CTPPr.Factory.newInstance();
                    indent.setInd(levelIndent);
                    lvl.setPPr(indent);
                }
            }

            // Actually create the (concrete) numbering.
            BigInteger numId = numbering.addNum(abstractNumId);
            XWPFAbstractNum abstractNum = new XWPFAbstractNum(ctAbstractNum, numbering);
            numbering.addAbstractNum(abstractNum);

            return numId;
        }
    }

    /** Error and warning management. **/

    @Override
    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
    }

    private String getLocationString() {
        return locator.getSystemId() + ", line " + locator.getLineNumber() + ", " +
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
            // Ignore XInclude attributes.
            if (key.equals("base")) {
                continue;
            }

            System.err.println(getLocationString() + "unknown attribute " + key + ".");
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

    /** Remaining POI helpers, really specific to this class. **/

    private void setRunFormatting() throws SAXException {
        for (DocBookFormatting f: currentFormatting) {
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
                case SUBSCRIPT:
                    run.setSubscript(VerticalAlign.SUBSCRIPT);
                    break;
                case SUPERSCRIPT:
                    run.setSubscript(VerticalAlign.SUPERSCRIPT);
                    break;
                default:
                    // Special cases.
                    // Monospaced tag with a replaceable inside: just output the replaceable as italic.
                    if (currentFormatting.size() > 1 && f == DocBookFormatting.REPLACEABLE
                            && currentFormatting.stream().anyMatch(DocBookFormatting::isMonospacedFormatting)) {
                        run.setItalic(true);
                        break;
                    }

                    // General case.
                    if (DocBookFormatting.formattingToStyleID.containsKey(f)) {
                        run.setStyle(DocBookFormatting.formattingToStyleID.get(f));
                    } else {
                        throw new DocxException("formatting not recognised by setRunFormatting: " + f);
                    }
                    break;
            }
        }
    }

    private void ensureNoTextAllowed() {
        paragraph = null;
        run = null;
        runNumber = -1;
        runCharactersNumber = -1;
    }

    private void restoreParagraphStyle() {
        paragraphStyle = "Normal";
    }

    /** Actual SAX handler. **/

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        // Note: XInclude not supported right now. Could easily be done to export to DOCX, but the reverse would be
        // much harder. TODO at a later point.

        if (DocBookBlock.blockToPredicate.get(DocBookBlock.ARTICLE).test(qName)) {
            if (currentLevel.peekRootBook()) {
                throw new DocxException("unexpected article within a book (use articles).");
                // TODO: Maybe this is too conservative? Articles in books are allowed by the standard (equivalent to chapters).
            }

            currentLevel.push(Level.ROOT_ARTICLE);
            ensureNoTextAllowed();
            // Don't warn about unknown attributes, as it will most likely just be version and name spaces.
        } else if (DocBookBlock.blockToPredicate.get(DocBookBlock.BOOK).test(qName)) {
            if (currentLevel.peekRootArticle()) {
                throw new DocxException("unexpected book within an article (the other direction works).");
            }

            currentLevel.push(Level.ROOT_BOOK);
            ensureNoTextAllowed();
            // Don't warn about unknown attributes, as it will most likely just be version and name spaces.
        } else if (DocBookBlock.blockToPredicate.get(DocBookBlock.PART).test(qName)) {
            if (! currentLevel.peekRootBook()) {
                throw new DocxException("unexpected part outside a book.");
            }

            currentLevel.push(Level.PART);
            ensureNoTextAllowed();
            warnUnknownAttributes(attributes);
            // Don't increase section depth: it is only meant to be used with real sections (direct mapping to
            // the style to be used).
        } else if (DocBookBlock.blockToPredicate.get(DocBookBlock.CHAPTER).test(qName)) {
            if (! currentLevel.peekRootBook() && ! currentLevel.peekPart()) {
                throw new DocxException("unexpected chapter outside a book or a part.");
            }

            currentLevel.push(Level.CHAPTER);
            ensureNoTextAllowed();
            warnUnknownAttributes(attributes);
            // Don't increase section depth: it is only meant to be used with real sections (direct mapping to
            // the style to be used).
        } else if (SAXHelpers.isInfoTag(qName)) {
            switch (currentLevel.peek()) {
                case ROOT_ARTICLE:
                    currentLevel.push(Level.ROOT_ARTICLE_INFO);
                    break;
                case ROOT_BOOK:
                    currentLevel.push(Level.ROOT_BOOK_INFO);
                    break;
                case PART:
                    currentLevel.push(Level.PART_INFO);
                    break;
                case CHAPTER:
                    currentLevel.push(Level.CHAPTER_INFO);
                    break;
                case SECTION:
                    currentLevel.push(Level.SECTION_INFO);
                    break;
                default:
                    throw new DocxException("unexpected info tag in " + localName);
            }

            ensureNoTextAllowed();
            warnUnknownAttributes(attributes);
        }

        else if (SAXHelpers.isSectionTag(qName)) {
            currentLevel.push(Level.SECTION);
            currentSectionDepth += 1;
            paragraphNumber = 0;
            ensureNoTextAllowed();
            warnUnknownAttributes(attributes);
        }

        else if (SAXHelpers.isTitleTag(qName)) {
            paragraph = doc.createParagraph();
            runNumber = 0;

            if (currentSectionDepth == 0) {
                if (currentLevel.peekRootArticle() || currentLevel.peekRootArticleInfo()) {
                    paragraph.setStyle(DocBookBlock.tagToStyleID("article", attributes));
                } else if (currentLevel.peekRootBook() || currentLevel.peekRootBookInfo()) {
                    paragraph.setStyle(DocBookBlock.tagToStyleID("book", attributes));
                } else if (currentLevel.peekPart()) {
                    paragraph.setStyle(DocBookBlock.tagToStyleID("part", attributes));
                } else if (currentLevel.peekChapter()) {
                    paragraph.setStyle(DocBookBlock.tagToStyleID("chapter", attributes));
                } else {
                    throw new DocxException("unexpected root title");
                }
            } else if (currentLevel.peekPart()) {
                paragraph.setStyle(DocBookBlock.tagToStyleID("part", attributes));
            } else if (currentLevel.peekChapter()) {
                paragraph.setStyle(DocBookBlock.tagToStyleID("chapter", attributes));
            } else if (currentLevel.peekSection() || currentLevel.peekSectionInfo()) {
                paragraph.setStyle("Heading" + currentSectionDepth);
            } else {
                throw new DocxException("title not expected");
            }

            run = paragraph.createRun();
            runCharactersNumber = 0;
            warnUnknownAttributes(attributes);
        }

        else if (SAXHelpers.isAbstractTag(qName) || SAXHelpers.isPartIntroTag(qName)) {
            paragraphStyle = "Abstract";
            warnUnknownAttributes(attributes);
        }

        // Admonitions.
        else if (DocBookBlock.isAdmonition(qName)) {
            paragraphStyle = DocBookBlock.docbookTagToStyleID.get(qName);
            if (paragraphStyle == null) {
                throw new DocxException("admonition " + qName + " not found in docbookTagToStyleID.");
            }

            warnUnknownAttributes(attributes);
        }

        // Paragraphs: lots of special cases...
        else if (SAXHelpers.isParagraphTag(qName)) {
            // For tables, the paragraph is automatically created within a table cell.
            if (currentLevel.peekTable() && paragraphNumber == 0) {
                return;
            }

            if (paragraph != null || run != null) {
                throw new DocxException("tried to create a new paragraph, but one was already being filled.");
            }

            if (currentLevel.peekTable()) {
                // In tables, add the new paragraph to the current cell.
                paragraph = tableCell.addParagraph();
                runNumber = 0;
                runCharactersNumber = 0;
            } else {
                // Otherwise, create a new paragraph at the end of the document.
                paragraph = doc.createParagraph();
                runNumber = 0;

                if (currentLevel.peekList()) {
                    paragraph.setNumID(numbering);

                    CTDecimalNumber depth = CTDecimalNumber.Factory.newInstance();
                    depth.setVal(BigInteger.valueOf(currentLevel.countListDepth() - 1));
                    paragraph.getCTP().getPPr().getNumPr().setIlvl(depth);
                    // https://bz.apache.org/bugzilla/show_bug.cgi?id=63465 -- at some point on GitHub too?

                    // If within a list, this must be a new item (only one paragraph allowed per item).
                    // This is just allowed for variable lists.
                    if (numberingItemParagraphNumber > 0) {
                        throw new DocxException("more than one paragraph in a list item, this is not supported.");
                        // How to make the difference when decoding this back? For implementation, maybe hack
                        // something based on https://stackoverflow.com/a/43164999/1066843.
                        // However, easy to do for variable list: new items are indicated with a title.
                    }
                }
            }

            paragraph.setStyle(paragraphStyle);

            run = paragraph.createRun();
            runCharactersNumber += 1;
            warnUnknownAttributes(attributes);
        }

        // Inline tags.
        else if (SAXHelpers.isFormatting(qName)) {
            if (currentLevel.peekBlockPreformatted()) {
                System.err.println(getLocationString() + "formatting (" + qName + ") within a preformatted block: " +
                        "the document is probably too complex for this kind of tool to ever success at round-tripping; " +
                        "it will do its best, but don't complain if some content is lost during round-tripping.");
            }
            if (currentFormatting.size() > 0) {
                DocBookFormatting topFormatting = currentFormatting.get(currentFormatting.size() - 1);
                if (! DocBookFormatting.isRunFormatting(topFormatting)
                        && ! (topFormatting.equals(DocBookFormatting.FILE_NAME) // Exception: filename + replaceable.
                            && DocBookFormatting.formattingToPredicate.get(DocBookFormatting.REPLACEABLE).test(qName))
                ) {
                    System.err.println(getLocationString() + "style-based formatting (" + qName + ") " +
                            "within a formatting (" + DocBookFormatting.formattingToDocBookTag.get(topFormatting) + "): " +
                            "the document is probably too complex for this kind of tool to ever success at round-tripping; " +
                            "it will do its best, but don't complain if some content is lost during round-tripping.");
                }
            }

            try {
                currentFormatting.add(DocBookFormatting.tagToFormatting(qName, attributes));
            } catch (IllegalArgumentException e) {
                throw new DocxException("formatting not recognised", e);
            }

            // Create a new run if this one is already started.
            if (run.text().length() > 0) {
                run = paragraph.createRun();
                runNumber += 1;
            }

            setRunFormatting();
            warnUnknownAttributes(attributes);
        } else if (SAXHelpers.isLinkTag(qName)) {
            Map<String, String> attr = SAXHelpers.attributes(attributes);
            warnUnknownAttributes(attr, Stream.of("href"));

            // Always create a new run, as it is much easier than to replace a run within the paragraph.
            run = h.createHyperlinkRun(attr.get("href"));
            runNumber += 1;

            // Set formatting for the link.
            run.setUnderline(UnderlinePatterns.SINGLE);
            run.setColor("0563c1");
        }

        // Tables: only HTML implemented.
        else if (SAXHelpers.isTableTag(qName)) {
            currentLevel.push(Level.TABLE);

            table = doc.createTable();
            tableRowNumber = 0;tableColumnNumber = 0;
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
                tableCell = tableRow.getCell(0);
            } else if (tableRowNumber > 0 && tableRow.getTableCells().size() >= tableColumnNumber) {
                tableCell = tableRow.getCell(tableRowNumber);
            } else {
                tableCell = tableRow.createCell();
            }
            paragraph = tableCell.getParagraphs().get(0);
            runNumber = 0;
            run = paragraph.createRun();
            runCharactersNumber = 0;
            warnUnknownAttributes(attributes);
        } else if (SAXHelpers.isTableHeaderTag(qName) || SAXHelpers.isTableFooterTag(qName)) {
            throw new DocxException("table headers/footers are not handled.");
        } else if (SAXHelpers.isCALSTag(qName)) {
            throw new DocxException("CALS tables are not handled.");
        }

        // Preformatted areas.
        else if (DocBookBlock.isPreformatted(qName)) {
            if (currentLevel.peekBlockPreformatted()) {
                System.err.println(getLocationString() + "preformatted block (" + qName + ") within a preformatted block: " +
                        "the document is probably too complex for this kind of tool to ever success at round-tripping; " +
                        "it will do its best, but don't complain if some content is lost during round-tripping.");
            }

            currentLevel.push(Level.BLOCK_PREFORMATTED);

            Map<String, String> attr = SAXHelpers.attributes(attributes);

            paragraph = doc.createParagraph();
            runNumber = 0;
            paragraph.setStyle(DocBookBlock.tagToStyleID(qName, attributes));

            // For program listings, add the language as a first paragraph.
            if (SAXHelpers.isProgramListingTag(qName)) {
                // https://github.com/apache/poi/pull/152
                CTOnOff on = CTOnOff.Factory.newInstance();
                on.setVal(STOnOff.ON);
                paragraph.getCTP().getPPr().setKeepNext(on);

                String text = "Program listing. ";
                if (attr.containsKey("language")) {
                    text += "Language: " + attr.get("language") + ". ";
                }
                if (attr.containsKey("continuation")) {
                    text += "Continuation: " + attr.get("continuation") + ". ";
                }
                if (attr.containsKey("linenumbering")) {
                    text += "Line numbering: " + attr.get("linenumbering") + ". ";
                }
                if (attr.containsKey("startinglinenumber")) {
                    text += "Starting line number: " + attr.get("startinglinenumber") + ". ";
                }

                run = paragraph.createRun();
                run.setBold(true);
                run.setText(text);

                paragraph = doc.createParagraph();
                runNumber = 0;
                runCharactersNumber = 0;
                paragraph.setStyle(DocBookBlock.tagToStyleID(qName, attributes));

                warnUnknownAttributes(attr, Stream.of("language", "continuation", "linenumbering", "startinglinenumber"));
                return;
            } else {
                warnUnknownAttributes(attr);
            }

            run = paragraph.createRun();
            runNumber = 0;
            runCharactersNumber = 0;
        }

        // Media tags: for now, only images are implemented.
        else if (SAXHelpers.isInlineMediaObjectTag(qName)) {
            warnUnknownAttributes(attributes);
        } else if (SAXHelpers.isMediaObjectTag(qName)) {
            paragraph = doc.createParagraph();
            runNumber = 0;

            Map<String, String> attr = SAXHelpers.attributes(attributes);
            if (attr.containsKey("align")) {
                paragraph.setAlignment(DocBookAlignment.docbookAttributeToParagraphAlignment(attr.get("align").toLowerCase()));
            }
            warnUnknownAttributes(attr, Stream.of("align"));

            run = paragraph.createRun();
            runNumber += 1;
            runCharactersNumber = 0;
        } else if (SAXHelpers.isImageDataTag(qName)) {
            h.createImage(attributes); // Already warns about unknown attributes.
        } else if (SAXHelpers.isImageObjectTag(qName)) {
            // Nothing to do, as everything is under <db:imagedata>.
            // TODO: check if there is only one imagedata per imageobject?
            warnUnknownAttributes(attributes);
        } else if (SAXHelpers.isCaptionTag(qName)) { // TODO: <db:caption> may also appear in tables and other items; use currentLevel or is this implementation good enough?
            paragraph = doc.createParagraph();
            runNumber = 0;

            paragraph.setStyle("Caption");

            run = paragraph.createRun();
            runNumber += 1;
            runCharactersNumber = 0;

            warnUnknownAttributes(attributes);
        }

        // Standard lists: same treatment, except for creating the abstract numbering.
        else if (SAXHelpers.isItemizedListTag(qName) || SAXHelpers.isOrderedListTag(qName)) {
            if (currentLevel.peekList()) {
                if ((currentLevel.peekOrderedList() && SAXHelpers.isItemizedListTag(qName))
                        || (currentLevel.peekItemizedList() && SAXHelpers.isOrderedListTag(qName))) {
                    throw new DocxException("mixing list types not yet implemented (itemized only within itemized, " +
                            "ordered only within ordered).");
                }
            }

            // Create a numbering only if one does not already exists.
            if (! currentLevel.peekList()) {
                numbering = h.createNumbering(SAXHelpers.isOrderedListTag(qName));
                // For now, only able to create homogeneous numberings: only bullets OR only numbers.
                // To do better, would need to look ahead in the SAX stream, or store information along the way
                // before creating the numbering. To be done in a later iteration.
            }

            // Push the new list onto the stack.
            if (SAXHelpers.isItemizedListTag(qName)) {
                currentLevel.push(Level.ITEMIZED_LIST);
            } else if (SAXHelpers.isOrderedListTag(qName)) {
                currentLevel.push(Level.ORDERED_LIST);
            }

            if (currentLevel.countListDepth() > 9) {
                // No numbering is created with more than 9 levels (Word does not allow it either).
                throw new DocxException("list depth of more than 9 is not supported.");
            }

            numberingItemParagraphNumber = -1;

            ensureNoTextAllowed();
            warnUnknownAttributes(attributes);
        } else if (SAXHelpers.isListItemTag(qName) && currentLevel.peekList()) {
            ensureNoTextAllowed();
            warnUnknownAttributes(attributes);

            // listitem is just a container for para, so barely nothing to do here.
            numberingItemParagraphNumber = 0;
        }

        // Principles for handling segmented lists: buffer the titles in segmentedListHeaders, then spit them
        // out as necessary for each seglistitem.
        // Thus, characters() will need a mode to write to the buffer only if SAX is within a title right now;
        // afterwards, it should just print text normally to the current run.
        else if (SAXHelpers.isSegmentedListTag(qName)) {
            currentLevel.push(Level.SEGMENTED_LIST);

            numberingItemParagraphNumber = -1;
            segmentedListHeaders = new ArrayList<>();
            segmentNumber = -1;

            ensureNoTextAllowed();
            warnUnknownAttributes(attributes);
        } else if (SAXHelpers.isSegmentedListTitleTag(qName)) {
            if (! currentLevel.peekSegmentedList()) {
                throw new DocxException("unexpected segmented list header");
            }

            currentLevel.push(Level.SEGMENTED_LIST_TITLE);

            ensureNoTextAllowed(); // No text written to the document right now, it must be buffered
            // (done in characters).
            warnUnknownAttributes(attributes);
        } else if (SAXHelpers.isSegmentedListItemTag(qName)) {
            if (! currentLevel.peekSegmentedList()) {
                throw new DocxException("unexpected segmented list content");
            }

            if (segmentedListHeaders.size() == 0) {
                throw new DocxException("segmented list has no header");
            }

            segmentNumber = 0;
        } else if (SAXHelpers.isSegmentedListItemValueTag(qName)) {
            if (segmentNumber < 0 || ! currentLevel.peekSegmentedList()) {
                throw new DocxException("unexpected segmented list content");
            }

            if (segmentNumber >= segmentedListHeaders.size()) {
                throw new DocxException("more values than allowed by the header; " +
                        "did you use any formatting for the header?");
            }

            // Print the header for this segment, then prepare for the value.
            paragraph = doc.createParagraph();
            paragraph.setStyle("DefinitionListTitle");
            run = paragraph.createRun();
            run.setText(segmentedListHeaders.get(segmentNumber));

            paragraph = doc.createParagraph();
            runNumber = 0;
            paragraph.setStyle("DefinitionListItem");
            run = paragraph.createRun();
            runCharactersNumber = 0;
        }

        // Variable lists: quite similar to segmented lists, but a completely different content model.
        else if (SAXHelpers.isVariableListTag(qName)) {
            currentLevel.push(Level.VARIABLE_LIST);

            numberingItemParagraphNumber = -1;

            ensureNoTextAllowed();
            warnUnknownAttributes(attributes);
        } else if (SAXHelpers.isVariableListItemTag(qName)) {
            if (! currentLevel.peekVariableList()) {
                throw new DocxException("unexpected segmented list content");
            }

            ensureNoTextAllowed();
            warnUnknownAttributes(attributes);
        } else if (SAXHelpers.isVariableListItemDefinitionTag(qName)) {
            if (! currentLevel.peekVariableList()) {
                throw new DocxException("unexpected segmented list content");
            }

            paragraph = doc.createParagraph();
            runNumber = 0;
            paragraph.setStyle("VariableListTitle");
            run = paragraph.createRun();
            runNumber += 1;
            runCharactersNumber = 0;
            // Then directly inline content.

            warnUnknownAttributes(attributes);
        } else if (SAXHelpers.isListItemTag(qName) && currentLevel.peekVariableList()) {
            ensureNoTextAllowed();
            warnUnknownAttributes(attributes);

            // listitem is just a container for para, so barely nothing to do here.
            numberingItemParagraphNumber = 0;

            paragraphStyle = "VariableListItem";
        }

        // Catch-all for the remaining tags.
        else {
            throw new DocxException("unknown tag " + qName + ". Stack head: " + currentLevel.peek());
        }

        // There might be return instructions in the long switch.
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (DocBookBlock.blockToPredicate.get(DocBookBlock.ARTICLE).test(qName) || DocBookBlock.blockToPredicate.get(DocBookBlock.BOOK).test(qName)) {
            ensureNoTextAllowed();
        } else if (DocBookBlock.blockToPredicate.get(DocBookBlock.PART).test(qName)) {
            currentLevel.pop(Level.PART, new DocxException("unexpected end of part"));
            ensureNoTextAllowed();
        } else if (DocBookBlock.blockToPredicate.get(DocBookBlock.CHAPTER).test(qName)) {
            currentLevel.pop(Level.CHAPTER, new DocxException("unexpected end of chapter"));
            ensureNoTextAllowed();
        } else if (SAXHelpers.isInfoTag(qName)) {
            currentLevel.pop(
                    Stream.of(
                            Level.ROOT_ARTICLE_INFO, Level.ROOT_BOOK_INFO, Level.PART_INFO, Level.CHAPTER_INFO,
                            Level.SECTION_INFO
                    ),
                    new DocxException("unexpected end of info")
            );
            ensureNoTextAllowed();
        } else if (SAXHelpers.isSectionTag(qName)) {
            currentLevel.pop(Level.SECTION, new DocxException("unexpected end of section"));
            currentSectionDepth -= 1;
            ensureNoTextAllowed();
        } else if (SAXHelpers.isTitleTag(qName)) {
            ensureNoTextAllowed();
            restoreParagraphStyle();
        }

        else if (SAXHelpers.isAbstractTag(qName) || SAXHelpers.isPartIntroTag(qName)) {
            ensureNoTextAllowed();
            restoreParagraphStyle();
        }

        // Admonitions.
        else if (DocBookBlock.isAdmonition(qName)) {
            ensureNoTextAllowed();
            restoreParagraphStyle();
        }

        // Paragraph: mostly, update counters.
        else if (SAXHelpers.isParagraphTag(qName)) {
            paragraphNumber += 1;

            if (currentLevel.peekList()) {
                numberingItemParagraphNumber += 1;
            }

            ensureNoTextAllowed();
        }

        // Inline tags: ensure the formatting is no more included in the next runs.
        else if (DocBookFormatting.isRunFormatting(qName)) {
            currentFormatting.remove(currentFormatting.size() - 1);
            run = paragraph.createRun();
            runNumber += 1;
            runCharactersNumber = 0;
            setRunFormatting();
        } else if (SAXHelpers.isLinkTag(qName)) {
            // Create a new run, so that the new text is not within the same run.
            run = paragraph.createRun();
            runNumber += 1;
            runCharactersNumber = 0;
        }

        // Tables: update counters.
        else if (SAXHelpers.isTableTag(qName)) {
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
            tableCell = null;
            tableRowNumber += 1;
            ensureNoTextAllowed();
        } else if (SAXHelpers.isTableColumnTag(qName)) {
            tableCell = null;
            tableColumnNumber += 1;
            ensureNoTextAllowed();
        } else if (SAXHelpers.isTableHeaderTag(qName) || SAXHelpers.isTableFooterTag(qName)) {
            throw new DocxException("table headers/footers are not handled.");
        } else if (SAXHelpers.isCALSTag(qName)) {
            throw new DocxException("CALS tables are not handled.");
        }

        // Preformatted areas.
        else if (DocBookBlock.isPreformatted(qName)) {
            restoreParagraphStyle();
            ensureNoTextAllowed();

            currentLevel.pop(Level.BLOCK_PREFORMATTED);
        }

        // Media: nothing to do.
        else if (SAXHelpers.isInlineMediaObjectTag(qName) || SAXHelpers.isMediaObjectTag(qName)
                || SAXHelpers.isImageDataTag(qName) || SAXHelpers.isImageObjectTag(qName)) {
            ensureNoTextAllowed();
        } else if (SAXHelpers.isCaptionTag(qName)) {
            restoreParagraphStyle();
            ensureNoTextAllowed();
        }

        // Standard lists: update counters.
        else if (SAXHelpers.isItemizedListTag(qName) || SAXHelpers.isOrderedListTag(qName)) {
            currentLevel.pop(Stream.of(Level.ORDERED_LIST, Level.ITEMIZED_LIST),
                    new DocxException("unexpected end of list"));

            numberingItemParagraphNumber = -1;

            ensureNoTextAllowed();
        } else if (SAXHelpers.isListItemTag(qName) && currentLevel.peekList()) {
            numberingItemParagraphNumber = -1;

            ensureNoTextAllowed();
        }

        // Segmented lists: update counters.
        else if (SAXHelpers.isSegmentedListTag(qName)) {
            currentLevel.pop(Level.SEGMENTED_LIST, new DocxException("unexpected end of segmented list"));

            numberingItemParagraphNumber = -1;
            segmentedListHeaders = null;

            ensureNoTextAllowed();
        } else if (SAXHelpers.isSegmentedListTitleTag(qName)) {
            currentLevel.pop(Level.SEGMENTED_LIST_TITLE,
                    new DocxException("unexpected end of segmented list header"));

            ensureNoTextAllowed();
        } else if (SAXHelpers.isSegmentedListItemTag(qName)) {
            // End of a list item: no more value is expected.
            segmentNumber = -1;

            ensureNoTextAllowed();
        } else if (SAXHelpers.isSegmentedListItemValueTag(qName)) {
            // End of a value: go to the next one.
            segmentNumber += 1;

            restoreParagraphStyle();
            ensureNoTextAllowed();
        }

        // Variable lists: update counters.
        else if (SAXHelpers.isVariableListTag(qName)) {
            currentLevel.pop(Level.VARIABLE_LIST, new DocxException("unexpected end of variable list"));

            numberingItemParagraphNumber = -1;

            ensureNoTextAllowed();
        } else if (SAXHelpers.isVariableListItemTag(qName)) {
            if (! currentLevel.peekVariableList()) {
                throw new DocxException("unexpected variable list content");
            }

            ensureNoTextAllowed();
        } else if (SAXHelpers.isVariableListItemDefinitionTag(qName)) {
            if (! currentLevel.peekVariableList()) {
                throw new DocxException("unexpected variable list content");
            }
            restoreParagraphStyle();
        } else if (SAXHelpers.isListItemTag(qName) && currentLevel.peekVariableList()) {
            ensureNoTextAllowed();
            restoreParagraphStyle();

            // listitem is just a container for para, so barely nothing to do here.
            numberingItemParagraphNumber = 0;
        }

        // Catch-all.
        else {
            throw new DocxException("unknown tag " + qName + ". Stack head: " + currentLevel.peek() + ".");
        }

        // There might be return instructions in the long switch.
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        // How to deal with white space? This is a tricky question...
        // Don't trim this string, as the series of runs might require those spaces to be kept. Real-life example
        // (a sequence of runs):
        // - "La méthode " -- useful space at the end
        // - "addMIPStart()" -- DocBook tag
        // - " n'est utile que" -- useful space at the beginning
        // However, when having line feeds,

        // Some consistency checks.
        if (run != null && currentFormatting.size() > 0 && run.getCTR().getRPr() == null) {
            throw new DocxException("assertion failed: this run should have some formatting, but has no RPr.");
        }

        // Start processing the characters.
        String content = new String(ch, start, length);

        // This function is called for anything that is not a tag, including whitespace (nothing to do on it).
        if (content.length() == 0 || content.replaceAll("(\\s|\r|\n)+", "").length() == 0) {
            return;
        }

        // Special cases: the text should not be written in the current run.
        // Just segmented lists, for now.
        if (currentLevel.peekSegmentedListTitle()) {
            // Store the run in segmentedListTitles, as it should be rewritten for each item in the segmented list.
            // Formatting is ignored; this is a known limitation.
            segmentedListHeaders.add(content);
            return;
        }

        // Generic case: try to write inside the current run. If there is none open, it means text is not expected
        // here (also see ensureNoTextAllowed method).
        if (run == null) {
            throw new DocxException("invalid document, text not expected here. A likely cause is that you used " +
                    "block tags within paragraphs. For this tool, paragraphs are not supposed to contain block elements " +
                    "(such as lists or blocks of code): round-tripping would be very hard to implement in this case.");
        }

        // Line feeds are not well understood by setText: they should be replaced by a series of runs.
        // This is only done in environments where line feeds must be reflected in DocBook.
        if (currentLevel.peekBlockPreformatted()) {
            if (content.contains("\n") || content.contains("\r")) {
                String[] lines = content.split("(\n|\r\n)");
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i];
                    run.setText(line);
                    if (i != lines.length - 1) {
                        run.addBreak();
                    }
                }
            } else {
                run.setText(content);
            }
        } else {
            // White space is not important, get rid of (most of) it.
            content = content.replaceAll("[\r\n\t]", " ");
            while (content.contains("  ")) {
                content = content.replace("  ", " ");
            }

            // If this is the first run of the paragraph, white space at the beginning is not important.
            if (runCharactersNumber == 0) {
                content = content.replaceAll("^\\s*", ""); // Trim left.
            }

            // Do not test if this is the last run of the paragraph: as it is still being built, it will obviously
            // always be the last one. Caveat: might leave one white space character at the end of a paragraph.
            // That's not really a problem, as these spaces do not change the aspect of the document (unlike the first
            // character of a line).

            // Line feeds may create problems: if a tag starts on a new line, all the space between the last word and
            // the new tag is considered as "ignorable white space". Create a run with only white space in this case,
            // but only if the previous run does not end with white space. If validation worked, then
            // ignorableWhitespace could be used to deal with this case, but unfortunately it does not.
//            if (! content.endsWith(" ")) {
//                // Heuristic to decide when to append a space and when to avoid doing this processing.
//                if (! content.endsWith("(")) {
//                    content += " ";
//                    justAddedWhiteSpace = true;
//                }
//            }

            // If the previous run ends with white space, as it is not relevant in this run, remove it from
            // the beginning of this run (i.e. trim left).
            if (runNumber > 0) {
                XWPFRun previous = paragraph.getRuns().get(runNumber - 1);
                String prevText = previous.text();

                if (prevText.endsWith(" ")) {
                    content = content.replaceAll("^\\s+", ""); // Trim left.
                }

//                // Sometimes undo what the previous step did (justAddedWhiteSpace == true).
//                if (justAddedWhiteSpace && prevText.endsWith(")")) {
//                    prevText = prevText.substring(0, prevText.length() - 2);
//                    previous.setText(prevText);
//                }
            }

            run.setText(content);
            runCharactersNumber += 1;
        }
    }

    public void processingInstruction (String target, String data) {
        System.err.println("Processing instructions are not taken into account and will not be round-tripped.");
    }
}
