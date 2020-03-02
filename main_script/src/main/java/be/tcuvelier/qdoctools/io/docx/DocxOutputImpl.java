package be.tcuvelier.qdoctools.io.docx;

import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.config.QdtPaths;
import be.tcuvelier.qdoctools.core.helpers.Language;
import be.tcuvelier.qdoctools.io.docx.helpers.DocBookAlignment;
import be.tcuvelier.qdoctools.io.docx.helpers.DocBookBlock;
import be.tcuvelier.qdoctools.io.docx.helpers.DocBookFormatting;
import be.tcuvelier.qdoctools.io.docx.todocx.Level;
import be.tcuvelier.qdoctools.io.docx.todocx.LevelStack;
import be.tcuvelier.qdoctools.io.docx.todocx.SAXHelpers;
import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.openxmlformats.schemas.officeDocument.x2006.extendedProperties.CTProperties;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DocxOutputImpl extends DefaultHandler {
    /** Internal variables. **/

    private Locator locator; // Tracks the state within the XML document, useful to display useful error messages.
    private Path folder; // Folder of the document. Used to determine where to store images.
    XWPFDocument doc; // DOCX document being written.
    private POIHelpers h = new POIHelpers();

    private Language language;

    // Paragraph being filled.
    private Deque<XWPFParagraph> paragraph; // Paragraph being written while another paragraph is not finished yet,
    // e.g. within footnotes. In normal cases, this deque only stores one paragraph. At any rate, most operations
    // are performed only on the last element of the deque.
    private XWPFRun run; // Belongs to the last paragraph in the queue.

    // Current state.
    private int paragraphNumber = -1;
    private int runNumber = -1;
    private int runCharactersNumber = -1;
    private String currentLink = null; // When != null: all new runs must point to that link.

    private boolean footnotesInitialised = false; // Whether initialiseFootnotes() should be called.
    private XWPFFootnote footnote; // Footnote being filled.
    private String paragraphStyle = "Normal"; // Current style for the current paragraph. Paragraphs created when
    // opening a <para> tag will have the same style.

    private BigInteger numbering; // != null when filling a list. Used when opening a <para> tag.
    private BigInteger lastFilledNumbering = BigInteger.ZERO; // Ease the look-up for a new numbering to fill.
    private int numberingItemParagraphNumber = -1; // Check whether multiple paragraphs are used for a single item.
    private List<String> segmentedListHeaders; // Stores the header of a segmented list (Word requires that they are
    // repeated). TODO: Limitation for ease of implementation: no styling stored in this list, just raw headers.
    private int segmentNumber = -1;

    private XWPFTable table; // Table being currently filled TODO: What about nested tables? Really care about this case? Would need to stack them...
    private XWPFTableRow tableRow; // Table row being currently filled. null outside a table row.
    private int tableRowNumber = -1; // Number of the current row (starts at 0).
    private XWPFTableCell tableCell; // Table cell being currently filled (i.e. the intersection of a row and a column).
    // null when outside a table cell.
    private int tableColumnNumber = -1; // Number of the current column within the row (starts at 0).

    private LevelStack currentLevel = new LevelStack(); // Records some tags are are currently open.
    private int currentSectionDepth = 0; // 0: root; >0: sections.
    private List<DocBookFormatting> currentFormatting = new ArrayList<>(); // Order: FIFO, i.e. first tag met in
    // the document is the first one in the vector. TODO: migrate to Deque?

    DocxOutputImpl(Path folder, GlobalConfiguration config) throws IOException {
        this.folder = folder;

        // Start a document with the template that defines all needed styles.
        doc = new XWPFDocument(new FileInputStream(new QdtPaths(config).getToDocxTemplate()));

        // A Word document always contains empty paragraphs, remove them (just to have a clean output).
        // Either it is just an empty document, or these paragraphs are used so that Word has no latent style.
        int nParagraphs = doc.getBodyElements().size();
        while (nParagraphs > 0) {
            doc.removeBodyElement(0);
            nParagraphs -= 1;
        }

        // Remove the footnotes of the template, and directly create the ones Word requires.
        int nFootnotes = doc.getFootnotes().size();
        while (nFootnotes > 0) {
            doc.removeFootnote(0);
            nFootnotes -= 1;
        }
        initialiseFootnotes();

        // Reset the properties.
        POIXMLProperties props = doc.getProperties();
        POIXMLProperties.CoreProperties coreProps = props.getCoreProperties();
        coreProps.setCreated(Optional.of(new Date()));
        coreProps.setCreator("");
        coreProps.setModified(Optional.of(new Date()));
        coreProps.setLastModifiedByUser("QtDocTools");
        coreProps.setLastPrinted(Optional.empty());
        coreProps.setTitle("");
        coreProps.getUnderlyingProperties().setCreatorProperty(Optional.empty());

        // https://github.com/apache/poi/pull/157
        CTProperties extendedProps = props.getExtendedProperties().getUnderlyingProperties();
        extendedProps.unsetApplication();
        extendedProps.unsetAppVersion();
        extendedProps.unsetCharacters();
        extendedProps.unsetCharactersWithSpaces();
        extendedProps.unsetPages();
        extendedProps.unsetParagraphs();
        extendedProps.unsetLines();
        extendedProps.unsetWords();
        extendedProps.unsetTotalTime();
        props.getExtendedProperties().setTemplate("QtDocTools.docm");
    }

    /** POI-level helpers. These functions are not really specific to this project and could be upstreamed. **/
    // Cannot be a static class, as some methods require access to the current document. (A static class would be
    // less clean to use.)

    private class POIHelpers {
        private ParagraphAlignment alignmentToWordAligment(@NotNull String align) {
            switch (align) {
                case "center":
                    return ParagraphAlignment.CENTER;
                case "char":
                    // No easy translation to Word.
                    // DocBook definition (https://tdg.docbook.org/tdg/5.2/imagedata.html):
                    //      Aligned horizontally on the specified character
                    // Available to Word:
                    //      http://officeopenxml.com/WPalignment.php
                    //      https://docs.microsoft.com/en-us/dotnet/api/documentformat.openxml.spreadsheet.alignment?view=openxml-2.8.1
                    throw new IllegalArgumentException("DocBook char alignment is not supported");
                case "justify":
                    return ParagraphAlignment.DISTRIBUTE;
                case "left":
                    return ParagraphAlignment.LEFT;
                case "right":
                    return ParagraphAlignment.RIGHT;
            }
            return null;
        }

        private int filenameToWordFormat(@NotNull String filename) {
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

        @Contract("null -> fail")
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

        private CTLevelText createText(@NotNull String t) {
            CTLevelText ct = CTLevelText.Factory.newInstance();
            ct.setVal(t);
            return ct;
        }

        private CTRPr createFont(@NotNull String name) {
            CTFonts fonts = CTFonts.Factory.newInstance();
            fonts.setAscii(name);
            fonts.setHAnsi(name);
            fonts.setCs(name);
            fonts.setHint(STHint.DEFAULT);

            CTRPr font = CTRPr.Factory.newInstance();
            font.setRFonts(fonts);
            return font;
        }

        private String createHyperlinkReference(@NotNull String uri) {
            return paragraph.getLast().getPart().getPackagePart().addExternalRelationship(
                    uri, XWPFRelation.HYPERLINK.getRelation()
            ).getId();
        }

        private XWPFHyperlinkRun createHyperlinkRun(@NotNull String rId) {
            // https://stackoverflow.com/questions/55275241/how-to-add-a-hyperlink-to-the-footer-of-a-xwpfdocument-using-apache-poi
            // https://github.com/apache/poi/pull/153
            // https://bz.apache.org/bugzilla/show_bug.cgi?id=64038
            assert paragraph.size() > 0;

            // Create the run.
            CTHyperlink ctHyperLink = paragraph.getLast().getCTP().addNewHyperlink();
            ctHyperLink.setId(rId);
            ctHyperLink.addNewR();

            // Append this run to the paragraph.
            XWPFHyperlinkRun link = new XWPFHyperlinkRun(ctHyperLink, ctHyperLink.getRArray(0), paragraph.getLast());
            paragraph.getLast().addRun(link);
            return link;
        }

        private void createImage(Attributes attributes) throws SAXException {
            assert paragraph.size() > 0;
            assert run != null;

            Map<String, String> attr = SAXHelpers.attributes(attributes);

            if (! attr.containsKey("fileref")) {
                throw new DocxException("the image tag has no fileref attribute.");
            }

            String filename = attr.get("fileref");
            Path filePath = folder.resolve(filename);
            int width;
            int height;
            String align = attr.getOrDefault("width", null);

            // Get the image width and height: either from the XML or from the image itself.
            // Avoid loading the image if both dimensions are known from the XML.
            {
                String imageWidth = null;
                String imageHeight = null;
                if (! attr.containsKey("width") && ! attr.containsKey("depth")) {
                    try {
                        BufferedImage img = ImageIO.read(filePath.toFile());
                        imageWidth = img.getWidth() + "px";
                        imageHeight = img.getHeight() + "px";
                    } catch (IOException e) {
                        throw new DocxException("there was a problem reading the image", e);
                    }
                }

                width = parseMeasurementAsEMU(attr.getOrDefault("width", imageWidth));
                height = parseMeasurementAsEMU(attr.getOrDefault("depth", imageHeight));
            }

            warnUnknownAttributes(attr, Stream.of("fileref", "width", "depth", "align"));

            // Detect the format of the image.
            int format = filenameToWordFormat(filename);
            if (format < 0) {
                throw new DocxException("unknown image extension " + filename + ".");
            }

            // Prepare the current paragraph to only receive this image.
            if (run.text().length() > 0) {
                paragraph.getLast().removeRun(0);
                createNewRun();
                runNumber -= 1;
            }

            // To allow images in admonitions, apply the admonition style to the image.
            if (currentLevel.peekSecondAdmonition()) {
                String style = DocBookBlock.docbookTagToStyleID.get(Level.qnameFromAdmonition(currentLevel.peekSecond()));
                if (style != null) {
                    paragraphStyle = style;
                    paragraph.getLast().setStyle(style);
                }
            }

            // Use the last attributes.
            if (align != null && ! align.isBlank()) {
                ParagraphAlignment a = h.alignmentToWordAligment(align);
                if (a != null) {
                    paragraph.getLast().setAlignment(a);
                }
            }

            // Actually add the image.
            try {
                run.addPicture(new FileInputStream(filePath.toFile()), format,
                        filePath.getFileName().toString(), width, height);
            } catch (IOException | InvalidFormatException e) {
                throw new DocxException("there was a problem adding the image to the output file", e);
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

            // Create the abstract numbering from scratch. Inspired by:
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

        private void iterateOverStyleHierarchy(XWPFStyle s, Consumer<XWPFStyle> f) {
            XWPFStyle style = s;
            XWPFStyles styles = s.getStyles();
            while (style != null) {
                f.accept(style);
                style = styles.getStyle(style.getBasisStyleID());
            }
        }

        private void copyStyleToParagraph(XWPFStyle parentStyle, XWPFParagraph p) {
            // Copy groups of properties from the given style (or any parent style).
            // Properties may come from any parent! However, the child styles always override a parent: once a property
            // has been set, it should not get overwritten.
            // Should be called once the paragraph is empty, so that the properties can be properly updated for all
            // its runs.

            iterateOverStyleHierarchy(parentStyle, (XWPFStyle s) -> {
                /* Paragraph style */
                CTPPr ppr = s.getCTStyle().getPPr();
                if (ppr != null) {
                    // Borders.
                    if (ppr.getPBdr() != null) {
                        CTPBdr pbdr = ppr.getPBdr();

                        if (pbdr.getBetween() != null && p.getBorderBetween().equals(Borders.NONE)) {
                            p.setBorderBetween(Borders.valueOf(pbdr.getBetween().getVal().intValue()));
                        }
                        if (pbdr.getBottom() != null && p.getBorderBottom().equals(Borders.NONE)) {
                            p.setBorderBottom(Borders.valueOf(pbdr.getBottom().getVal().intValue()));
                        }
                        if (pbdr.getLeft() != null && p.getBorderLeft().equals(Borders.NONE)) {
                            p.setBorderLeft(Borders.valueOf(pbdr.getLeft().getVal().intValue()));
                        }
                        if (pbdr.getRight() != null && p.getBorderRight().equals(Borders.NONE)) {
                            p.setBorderRight(Borders.valueOf(pbdr.getRight().getVal().intValue()));
                        }
                        if (pbdr.getTop() != null && p.getBorderTop().equals(Borders.NONE)) {
                            p.setBorderTop(Borders.valueOf(pbdr.getTop().getVal().intValue()));
                        }
                    }

                    // Indentation.
                    if (ppr.getInd() != null) {
                        CTInd ind = ppr.getInd();

                        if (ind.getFirstLine() != null && p.getIndentationFirstLine() == -1) {
                            p.setIndentationFirstLine(ind.getFirstLine().intValue());
                        }
                        if (ind.getHanging() != null && p.getIndentationHanging() == -1) {
                            p.setIndentationHanging(ind.getHanging().intValue());
                        }
                        if (ind.getLeft() != null && p.getIndentationLeft() == -1) {
                            p.setIndentationLeft(ind.getLeft().intValue());
                        }
                        if (ind.getRight() != null && p.getIndentationRight() == -1) {
                            p.setIndentationRight(ind.getRight().intValue());
                        }
                    }

                    // Shades.
                    if (ppr.getShd() != null) {
                        CTPPr p_ppr = p.getCTP().getPPr();
                        if (p_ppr == null) {
                            p_ppr = p.getCTP().addNewPPr();
                        }

                        p_ppr.setShd(ppr.getShd());
                    }
                }

                /* Run style */
                CTRPr rpr = s.getCTStyle().getRPr();
                if (rpr != null) {
                    // Iterate over the runs.
                    for (CTR r : p.getCTP().getRList()) {
                        // Text outline. Apparently not yet supported by POI. (Albeit it's from Word 2010: CT_TextOutlineEffect,
                        // https://docs.microsoft.com/en-us/openspecs/office_standards/ms-docx/9704b59f-bc49-4618-ac66-41beb82a0d7f)
                        // rpr.getTextOutline();

                        // Shades.
                        if (rpr.getShd() != null) {
                            CTRPr p_rpr = r.getRPr();
                            if (p_rpr == null) {
                                p_rpr = r.addNewRPr();
                            }

                            p_rpr.setShd(rpr.getShd());
                        }
                    }
                }
            });
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

            // Special message for ubiquitous linking attributes.
            if (key.equals("href") && currentLink == null) {
                System.err.println(getLocationString() + "ubiquitous linking attributes like " + key + " are not implemented in this context.");
                // Otherwise, the currentLink variable would be filled with an ID.
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
        for (String s : recognised.collect(Collectors.toCollection(Vector::new))) {
            unknown.remove(s);
        }
        if (unknown.size() > 0) {
            warnUnknownAttributes(unknown);
        }
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
        if (paragraph != null && paragraph.size() > 0) {
            paragraph.removeLast();
        }
        run = null;
        runNumber = -1;
        runCharactersNumber = -1;
    }

    private void restoreParagraphStyle() {
        paragraphStyle = "Normal";
    }

    private void allocateNewParagraph() {
        if (paragraph == null) {
            paragraph = new ArrayDeque<>();
        } else if (paragraph.size() > 0) {
            paragraph.removeLast();
        }

        if (currentLevel.peekTable()) {
            // In tables, add the new paragraph to the current cell.
            paragraph.addLast(tableCell.addParagraph());
        } else if (footnote != null) {
            // In footnotes, must trick the system...
            CTP ctp = footnote.getCTFtnEdn().addNewP();
            paragraph.addLast(new XWPFParagraph(ctp, footnote));
        } else {
            // Otherwise, create a new paragraph at the end of the document.
            paragraph.addLast(doc.createParagraph());
        }

        createNewRun();
        runNumber = 0;
    }

    /** Actual SAX handler. **/

    private void initialiseFootnotes() {
        if (! footnotesInitialised) {
            footnotesInitialised = true;
            doc.createFootnotes();

            // Create the first two "dummy" footnotes. Without them, the document cannot be considered valid by Word
            // (even though LibreOffice has no problem with it).

            // <w:footnote w:type="separator" w:id="-1"><w:p><w:r><w:separator/></w:r></w:p></w:footnote>
            footnote = doc.createFootnote();
            footnote.getCTFtnEdn().setId(BigInteger.ZERO.subtract(BigInteger.ONE)); // -1
            footnote.getCTFtnEdn().setType(STFtnEdn.SEPARATOR);
            footnote.getCTFtnEdn().addNewP();
            footnote.getCTFtnEdn().getPArray(0).addNewR();
            footnote.getCTFtnEdn().getPArray(0).getRArray(0).addNewSeparator();

            // <w:footnote w:type="continuationSeparator" w:id="0"><w:p><w:r><w:continuationSeparator/></w:r></w:p></w:footnote>
            footnote = doc.createFootnote();
            footnote.getCTFtnEdn().setId(BigInteger.ZERO);
            footnote.getCTFtnEdn().setType(STFtnEdn.CONTINUATION_SEPARATOR);
            footnote.getCTFtnEdn().addNewP();
            footnote.getCTFtnEdn().getPArray(0).addNewR();
            footnote.getCTFtnEdn().getPArray(0).getRArray(0).addNewContinuationSeparator();

            footnote = null;
        }
    }

    private boolean isParagraphEmpty(XWPFParagraph p) {
        assert p != null;
        // Either no runs or just whitespace, without images.
        if (p.getRuns().size() == 0) {
            return true;
        }

        boolean hasText = ! p.getText().isBlank();
        boolean hasImage = p.getRuns().stream().anyMatch(r -> r.getCTR().sizeOfDrawingArray() > 0);
        return ! hasText && ! hasImage;
    }

    private void createNewParagraph() {
        // If there is already a paragraph in the deque, maybe it's empty and still useable.
        // Remember: this code only creates a document, it does not modify one. Moreover, for footnotes, paragraph
        // creation does not use this mechanism, so that footnote paragraphs do not end up in doc.paragraphs.
        XWPFParagraph pendingP = (paragraph.size() > 0) ? paragraph.getLast() : null;
        List<XWPFParagraph> lp = doc.getParagraphs();
        XWPFParagraph lastP = (lp.size() > 0) ? lp.get(lp.size() - 1) : null;
        XWPFParagraph p = (pendingP != null) ? pendingP : lastP; // May still be null!

        if (p != null && isParagraphEmpty(p)) {
            if (paragraph.size() == 0 || paragraph.getLast() != p) {
                paragraph.addLast(p);
            }
        }

        // If it's not empty, remove it from the deque and create a new from scratch.
        else {
            if (paragraph.size() > 0) {
                paragraph.removeLast();
            }
            paragraph.addLast(doc.createParagraph());
        }

        paragraph.getLast().setStyle("Normal");
        for (int i = 0; i < paragraph.getLast().getRuns().size(); ++i) {
            paragraph.getLast().removeRun(0);
        }

        createNewRun();
    }

    private void setCurrentLink(String uri) {
        currentLink = h.createHyperlinkReference(uri);
    }

    private void setCurrentLink() {
        currentLink = null;
    }

    private void createNewRun() {
        if (currentLink == null) {
            run = paragraph.getLast().createRun();
        } else {
            run = h.createHyperlinkRun(currentLink);
        }

        run.setLang(Language.toWordLang(language));
        runNumber += 1;
        runCharactersNumber = 0;

        // Set formatting for the link.
        // TODO: Rather use the Hyperlink style? Only if not in a not-Normal style...
        if (currentLink != null) {
            run.setUnderline(UnderlinePatterns.SINGLE);
            run.setColor("0563c1");
        }
    }

    private void parseLanguage(Attributes attributes) {
        Map<String, String> attr = SAXHelpers.attributes(attributes);
        String xmllang = attr.getOrDefault("xml:lang", attr.getOrDefault("lang", "en"));
        language = Language.fromXmlLang(xmllang);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        // Note: XInclude not supported right now. Could easily be done to export to DOCX, but the reverse would be
        // much harder. TODO at a later point.

        if (currentLink != null) {
            throw new IllegalStateException("Not yet implemented: tag within a ubiquitous link.");
            // This would require to register all opening/closing tags since the opening of the ubiquitous link.
            // Indeed, when closing a tag, the attributes are not passed anymore! So there is no other way to find which
            // tag opened the link. A lot of effort that will probably not be required.
        }

        // Root tags and assimilate.
        if (DocBookBlock.blockToPredicate.get(DocBookBlock.ARTICLE).test(qName)) {
            if (currentLevel.peekRootBook()) {
                throw new DocxException("unexpected article within a book (use stand-alone articles).");
                // TODO: Maybe this is too conservative? Articles in books are allowed by the standard (equivalent to chapters).
            }

            parseLanguage(attributes);

            currentLevel.push(Level.ROOT_ARTICLE);
            ensureNoTextAllowed();
            // Don't warn about unknown attributes, as it will most likely just be version and name spaces.
        } else if (DocBookBlock.blockToPredicate.get(DocBookBlock.BOOK).test(qName)) {
            if (currentLevel.peekRootArticle()) {
                throw new DocxException("unexpected book within an article (the other direction works).");
            }

            parseLanguage(attributes);

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

        // Authors and various credits.
        else if (SAXHelpers.isAuthorTag(qName) || SAXHelpers.isOtherCreditTag(qName)) {
            if (! currentLevel.peekInfo()) {
                throw new DocxException("unexpected author/contributor outside an info container.");
            }

            Map<String, String> attr = SAXHelpers.attributes(attributes);

            paragraph = null;
            allocateNewParagraph();
            run.setBold(true);

            String text = "";
            if (SAXHelpers.isAuthorTag(qName)) {
                text = Translations.author.get(language);
            } else if (SAXHelpers.isOtherCreditTag(qName)) {
                switch (attr.getOrDefault("class", "reviewer")) {
                    case "proofreader":
                        text = Translations.proofreader.get(language);
                        break;
                    case "conversion":
                        text = Translations.converter.get(language);
                        break;
                    case "reviewer":
                        text = Translations.reviewer.get(language);
                        break;
                    case "translator":
                        text = Translations.translator.get(language);
                        break;
                }
            } else {
                throw new DocxException("unexpected author/contributor tag.");
            }
            run.setText(text + ".");

            createNewRun();
            warnUnknownAttributes(attr, Stream.of("class"));
        } else if (SAXHelpers.isPersonNameTag(qName)) {
            // Do nothing: this is usually just a placeholder. It might also contain the full name without further
            // structure, so still allow text here.
            warnUnknownAttributes(attributes);
        } else if (SAXHelpers.isFirstNameTag(qName)) {
            run.setText(Translations.firstName.get(language) + Translations.colon.get(language));
            warnUnknownAttributes(attributes);
        } else if (SAXHelpers.isSurNameTag(qName)) {
            run.setText(Translations.surname.get(language) + Translations.colon.get(language));
            warnUnknownAttributes(attributes);
        } else if (SAXHelpers.isOtherNameTag(qName)) {
            Map<String, String> attr = SAXHelpers.attributes(attributes);

            if (attr.get("role") == null) {
                throw new DocxException("unsupported othername: the role attribute must be specified");
            }
            if (! attr.get("role").equals("pseudonym")) {
                throw new DocxException("unsupported othername role: " + attr.get("role"));
            }

            run.setText(Translations.pseudonym.get(language) + Translations.colon.get(language));
            warnUnknownAttributes(attr, Stream.of("role"));
        } else if (SAXHelpers.isAuthorGroupTag(qName)) {
            if (! currentLevel.peekInfo()) {
                throw new DocxException("unexpected author group outside an info container.");
            }

            throw new DocxException("authorgroup not implemented.");
        } else if (SAXHelpers.isURI(qName) && currentLevel.peekInfo()) { // URIs are also considered as monospace formatting.
            // TODO: check that this is within an author?
            run.setText(Translations.pseudonym.get(language) + Translations.colon.get(language));

            Map<String, String> attr = SAXHelpers.attributes(attributes);
            String text = "";
            switch (attr.getOrDefault("type", "main-uri").toLowerCase()) {
                case "main-uri":
                    text = Translations.uriMain.get(language);
                    break;
                case "homepage":
                case "webpage":
                case "website":
                    text = Translations.uriHomepage.get(language);
                    break;
                case "blog":
                case "weblog":
                    text = Translations.uriBlog.get(language);
                    break;
                case "google-plus":
                    text = Translations.uriGooglePlus.get(language);
                    break;
                case "linkedin":
                    text = Translations.uriLinkedIn.get(language);
                    break;
            }
            run.setText(text + Translations.colon.get(language));

            createNewRun();
            warnUnknownAttributes(attr, Stream.of("type"));
        }

        // Other parts of the info container.
        else if (SAXHelpers.isDate(qName)) {
            if (! currentLevel.peekInfo()) {
                throw new DocxException("unexpected date outside an info container.");
            }

            createNewParagraph();
            run.setText(Translations.date.get(language) + Translations.colon.get(language));
            run.setBold(true);

            createNewRun();
            warnUnknownAttributes(attributes);
        } else if (SAXHelpers.isPubDate(qName)) {
            if (! currentLevel.peekInfo()) {
                throw new DocxException("unexpected pubdate outside an info container.");
            }

            createNewParagraph();
            run.setText(Translations.pubdate.get(language) + Translations.colon.get(language));
            run.setBold(true);

            createNewRun();
            warnUnknownAttributes(attributes);
        }

        // Sections.
        else if (SAXHelpers.isSectionTag(qName)) {
            currentLevel.push(Level.SECTION);
            currentSectionDepth += 1;
            paragraphNumber = 0;
            ensureNoTextAllowed();
            warnUnknownAttributes(attributes);
        }

        // Section titles (only delimiter for sections in DOCX).
        else if (SAXHelpers.isTitleTag(qName) && ! currentLevel.peekFigure()) {
            paragraph = null;
            allocateNewParagraph();

            if (currentSectionDepth == 0) {
                if (currentLevel.peekRootArticle() || currentLevel.peekRootArticleInfo()) {
                    paragraph.getLast().setStyle(DocBookBlock.tagToStyleID("article", attributes));
                } else if (currentLevel.peekRootBook() || currentLevel.peekRootBookInfo()) {
                    paragraph.getLast().setStyle(DocBookBlock.tagToStyleID("book", attributes));
                } else if (currentLevel.peekPart()) {
                    paragraph.getLast().setStyle(DocBookBlock.tagToStyleID("part", attributes));
                } else if (currentLevel.peekChapter()) {
                    paragraph.getLast().setStyle(DocBookBlock.tagToStyleID("chapter", attributes));
                } else {
                    throw new DocxException("unexpected root title");
                }
            } else if (currentLevel.peekPart()) {
                paragraph.getLast().setStyle(DocBookBlock.tagToStyleID("part", attributes));
            } else if (currentLevel.peekChapter()) {
                paragraph.getLast().setStyle(DocBookBlock.tagToStyleID("chapter", attributes));
            } else if (currentLevel.peekSection() || currentLevel.peekSectionInfo()) {
                paragraph.getLast().setStyle("Heading" + currentSectionDepth);
            } else {
                throw new DocxException("title not expected");
            }

            createNewRun();
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

            currentLevel.push(Level.fromAdmonitionQname(qName));

            warnUnknownAttributes(attributes);
        }

        // Paragraphs: lots of special cases...
        else if (SAXHelpers.isParagraphTag(qName)) {
            // For tables, the paragraph is automatically created within a table cell. Same for a footnote.
            if (currentLevel.peekTable() && paragraphNumber == 0) {
                return;
            }
            if (footnote != null && paragraphNumber >= -1) { // TODO: Second part of the condition wrong, but will do for now. Would need to check if this is the first paragraph OF THE FOOTNOTE, not more globally.
                return;
            }

            // Consistency checks.
            if (footnote == null && ((paragraph != null && paragraph.size() > 0) || run != null)) {
                throw new DocxException("tried to create a new paragraph, but one was already being filled.");
            }

            // Create the new paragraph.
            allocateNewParagraph();

            // If within a list, number correctly this paragraph.
            if (currentLevel.peekList()) {
                paragraph.getLast().setNumID(numbering);

                CTDecimalNumber depth = CTDecimalNumber.Factory.newInstance();
                depth.setVal(BigInteger.valueOf(currentLevel.countListDepth() - 1));
                paragraph.getLast().getCTP().getPPr().getNumPr().setIlvl(depth);
                // https://bz.apache.org/bugzilla/show_bug.cgi?id=63465 -- at some point on GitHub too?

                // If within a list, this must be a new item (only one paragraph allowed per item).
                // This is just allowed for variable lists.
                if (numberingItemParagraphNumber > 0) {
                    throw new DocxException("more than one paragraph in a list item, this is not supported.");
                    // How to make the difference when decoding this back? For implementation, maybe hack
                    // something based on https://stackoverflow.com/a/43164999/1066843.
                    // However, easy to do for variable list: new items are indicated with a title.
                }
            } else {
                numbering = null;
            }

            // Apply styling.
            paragraph.getLast().setStyle(paragraphStyle);

            // Prepare a first run to receive text.
            createNewRun();
            warnUnknownAttributes(attributes);
        }

        // Inline tags.
        else if (SAXHelpers.isFormatting(qName)) {
            if (currentLevel.peekBlockPreformatted() && currentFormatting.size() > 1) {
                // Adding one more tag is possible (like <screen><prompt>…</prompt>…</screen>).
                // More complex things are not.
                System.err.println(getLocationString() + "formatting (" + qName + ") within a preformatted block: " +
                        "the document is probably too complex for this kind of tool to ever success at round-tripping; " +
                        "it will do its best, but don't complain if some content is lost during round-tripping.");
            }

            if (currentFormatting.size() > 0) {
                // Word cannot store infinitely many tags: one for paragraphs (blocks) and one for runs (inlines).
                // More than one formatting at a time is thus impossible to render.
                // Exceptions: italics within <filename>.
                // TODO: add an exception for bold, italics, underline + something else.
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

            // Add this inline formatting to the list, so that it is taken into account when creating a new run.
            try {
                currentFormatting.add(DocBookFormatting.tagToFormatting(qName, attributes));
            } catch (IllegalArgumentException e) {
                throw new DocxException("formatting not recognised", e);
            }

            // Handle ubiquitous links.
            boolean hasUbiquitous = false;
            for (int i = 0; i < attributes.getLength(); ++i) {
                if (attributes.getLocalName(i).equalsIgnoreCase("xlink:href")) {
                    hasUbiquitous = true;
                    setCurrentLink(attributes.getValue(i));
                    break;
                }
            }

            // Create a new run if this one is already started. Ubiquitous links already create a new run.
            if (run == null) {
                assert run != null;
            }
            if (run.text() != null && run.text().length() > 0 && ! hasUbiquitous) {
                createNewRun();
            }

            setRunFormatting();
            warnUnknownAttributes(attributes);
        } else if (SAXHelpers.isLinkTag(qName)) {
            Map<String, String> attr = SAXHelpers.attributes(attributes);
            warnUnknownAttributes(attr, Stream.of("href"));


            // Always create a new run, as it is much easier than to replace a run within the paragraph.
            setCurrentLink(attr.get("href"));
            createNewRun();
        } else if (SAXHelpers.isFootnoteTag(qName)) {
            if (paragraph.size() < 1) {
                throw new DocxException("unexpected end of footnote");
            }

            // Loosely based on https://stackoverflow.com/questions/39939057/adding-footnotes-to-a-word-document, then modernised.
            // Hypothesis of the rest of the code: creating a footnote does *not* use doc.createParagraph().
            footnote = doc.createFootnote();
            paragraph.getLast().addFootnoteReference(footnote); // Creates a new run in the current paragraph.

            CTP ctp = footnote.getCTFtnEdn().addNewP(); // https://github.com/apache/poi/pull/156
            paragraph.addLast(new XWPFParagraph(ctp, footnote));
            paragraph.getLast().setStyle("FootnoteText");
            paragraphStyle = "FootnoteText";

            createNewRun();
            run.setStyle("FootnoteReference");
            run.getCTR().addNewFootnoteRef(); // Not addNewFootnoteReference, this is not recognised by Word.

            // Create a new run, so that the new text is not within the same run.
            createNewRun();
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
            paragraph.addLast(tableCell.getParagraphs().get(0));
            runNumber = 0;
            createNewRun();
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

            createNewParagraph();
            paragraph.getLast().setStyle(DocBookBlock.tagToStyleID(qName, attributes));

            // For program listings, add the language and other parameters as a first paragraph.
            if (SAXHelpers.isProgramListingTag(qName)) {
                // https://github.com/apache/poi/pull/152
                CTOnOff on = CTOnOff.Factory.newInstance();
                on.setVal(STOnOff.ON);
                paragraph.getLast().getCTP().getPPr().setKeepNext(on);

                String text = Translations.programListing.get(language) + ". ";
                if (attr.containsKey("language")) {
                    text += Translations.programListingLanguage.get(language) + Translations.colon.get(language) + attr.get("language") + ". ";
                }
                if (attr.containsKey("continuation")) {
                    text += Translations.programListingContinuation + Translations.colon.get(language);
                    if (attr.get("continuation").equals("continues")) {
                        text += Translations.programListingContinuationValueContinues.get(language);
                    } else if (attr.get("continuation").equals("restarts")) {
                        text += Translations.programListingContinuationValueRestarts.get(language);
                    } else {
                        throw new IllegalStateException("Unknown value for continuation attribute: " + attr.get("continuation"));
                    }
                    text += ". ";
                }
                if (attr.containsKey("linenumbering")) {
                    text += Translations.programListingLineNumbering + Translations.colon.get(language);
                    if (attr.get("linenumbering").equals("numbered")) {
                        text += Translations.programListingLineNumberingValueNumbered.get(language);
                    } else if (attr.get("linenumbering").equals("unnumbered")) {
                        text += Translations.programListingLineNumberingValueUnnumbered.get(language);
                    } else {
                        throw new IllegalStateException("Unknown value for linenumbering attribute: " + attr.get("linenumbering"));
                    }
                    text += ". ";
                }
                if (attr.containsKey("startinglinenumber")) {
                    text += Translations.programListingStartingLineNumber.get(language) + Translations.colon.get(language) + attr.get("language") + ". ";
                }

                run.setBold(true);
                run.setText(text);

                createNewParagraph();
                paragraph.getLast().setStyle(DocBookBlock.tagToStyleID(qName, attributes));

                warnUnknownAttributes(attr, Stream.of("language", "continuation", "linenumbering", "startinglinenumber"));
                return;
            } else {
                warnUnknownAttributes(attr);
            }

            createNewRun();
            runNumber = 0;
        }

        // Media tags: for now, only images are implemented.
        else if (SAXHelpers.isFigureTag(qName)) {
            currentLevel.push(Level.FIGURE);

            createNewParagraph();
            warnUnknownAttributes(attributes);

            // Attributes are read with <db:mediaobject>.
        } else if (SAXHelpers.isMediaObjectTag(qName) && currentLevel.peekFigure()) {
            // Not many things to do here, everything is handled at the level of the figure.
            createNewParagraph();
            warnUnknownAttributes(attributes);
        } else if (SAXHelpers.isInlineMediaObjectTag(qName)) {
            warnUnknownAttributes(attributes);
        } else if (SAXHelpers.isMediaObjectTag(qName)) {
            // If there has already been a <db:figure>, no need to create a new paragraph.
            if (! currentLevel.peekFigure()) {
                createNewParagraph();
            }

            Map<String, String> attr = SAXHelpers.attributes(attributes);
            if (attr.containsKey("align")) {
                paragraph.getLast().setAlignment(DocBookAlignment.docbookAttributeToParagraphAlignment(attr.get("align").toLowerCase()));
            }
            warnUnknownAttributes(attr, Stream.of("align"));
        } else if (SAXHelpers.isImageDataTag(qName)) {
            h.createImage(attributes); // Already warns about unknown attributes.
        } else if (SAXHelpers.isImageObjectTag(qName)) {
            // Nothing to do, as everything is under <db:imagedata>.
            // TODO: check if there is only one imagedata per imageobject?
            warnUnknownAttributes(attributes);
        } else if (SAXHelpers.isCaptionTag(qName) || (currentLevel.peekFigure() && SAXHelpers.isTitleTag(qName))) {
            // TODO: <db:caption> may also appear in tables and other items; use currentLevel or is this implementation good enough?
            createNewParagraph();
            paragraph.getLast().setStyle("Caption");

            // If in an admonition, update its style to match the admonition.
            if (currentLevel.peekSecondAdmonition()) {
                XWPFStyle s = doc.getStyles().getStyle(DocBookBlock.docbookTagToStyleID.get(Level.qnameFromAdmonition(currentLevel.peekSecond())));
                h.copyStyleToParagraph(s, paragraph.getLast());
            }

            // Make the image and its caption stick together (i.e. on the same page).
            if (currentLevel.peekFigure()) {
                paragraph.getLast().setKeepNext(true);
            } else {
                int pos = doc.getPosOfParagraph(paragraph.getLast());
                if (pos > 0) {
                    XWPFParagraph imageP = doc.getParagraphArray(pos - 1);
                    imageP.setKeepNext(true);
                }
            }

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
            createNewParagraph();
            paragraph.getLast().setStyle("DefinitionListTitle");
            run.setText(segmentedListHeaders.get(segmentNumber));

            createNewParagraph();
            paragraph.getLast().setStyle("DefinitionListItem");
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

            createNewParagraph();
            paragraph.getLast().setStyle("VariableListTitle");
            createNewRun();
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
        // Roots or assimilate.
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
        } else if (SAXHelpers.isAuthorTag(qName) || SAXHelpers.isOtherCreditTag(qName)) {
            if (SAXHelpers.isAuthorTag(qName)) {
                POIXMLProperties props = doc.getProperties();
                POIXMLProperties.CoreProperties coreProps = props.getCoreProperties();

                // Append this as a new author or replace the empty string.
                String currentAuthor = paragraph.getLast().getText();
                if (coreProps.getCreator() != null && ! coreProps.getCreator().isBlank()) {
                    currentAuthor = coreProps.getCreator() + currentAuthor;
                }
                coreProps.setCreator(currentAuthor);
            }

            ensureNoTextAllowed();
            restoreParagraphStyle();
        } else if (SAXHelpers.isPersonNameTag(qName)) {
            // No dot here, as a personname might be a container.
            createNewRun();
            setRunFormatting();
        } else if (SAXHelpers.isFirstNameTag(qName) || SAXHelpers.isSurNameTag(qName) || SAXHelpers.isOtherNameTag(qName)) {
            run.setText(". ");
            run.setLang(Language.toWordLang(language));
        } else if (SAXHelpers.isURI(qName) && currentLevel.peekInfo()) {
            run.setText(". ");
            run.setLang(Language.toWordLang(language));
        } else if (SAXHelpers.isSectionTag(qName)) {
            currentLevel.pop(Level.SECTION, new DocxException("unexpected end of section"));
            currentSectionDepth -= 1;
            ensureNoTextAllowed();
        }

        // Other parts of the info container.
        else if (SAXHelpers.isDate(qName) || SAXHelpers.isPubDate(qName)) {
            run.setText(". ");
            run.setLang(Language.toWordLang(language));
            ensureNoTextAllowed();
        }

        // Section titles.
        else if (SAXHelpers.isTitleTag(qName) && ! currentLevel.peekFigure()) {
            // Set the title to the core properties.
            POIXMLProperties props = doc.getProperties();
            POIXMLProperties.CoreProperties coreProps = props.getCoreProperties();
            coreProps.setTitle(paragraph.getLast().getText());

            ensureNoTextAllowed();
            restoreParagraphStyle();
        }

        // Abstracts.
        else if (SAXHelpers.isAbstractTag(qName) || SAXHelpers.isPartIntroTag(qName)) {
            ensureNoTextAllowed();
            restoreParagraphStyle();
        }

        // Admonitions.
        else if (DocBookBlock.isAdmonition(qName)) {
            currentLevel.pop(Level.fromAdmonitionQname(qName));
            ensureNoTextAllowed();
            restoreParagraphStyle();
        }

        // Paragraph: mostly, update counters.
        else if (SAXHelpers.isParagraphTag(qName)) {
            paragraphNumber += 1;

            if (currentLevel.peekTable() && paragraph.getLast().getRuns().size() == 0) {
                paragraph.removeLast();
            }
            if (currentLevel.peekList()) {
                numberingItemParagraphNumber += 1;
            }

            ensureNoTextAllowed();
        }

        // Inline tags: ensure the formatting is no more included in the next runs.
        else if (DocBookFormatting.isRunFormatting(qName)) {
            if (currentFormatting.size() == 0) {
                throw new DocxException("Assertion failed: end of formatting [" + qName + "], but no current formatting");
            }

            // In case of ubiquitous link.
            setCurrentLink();

            currentFormatting.remove(currentFormatting.size() - 1);
            createNewRun();
            setRunFormatting();
        } else if (SAXHelpers.isLinkTag(qName)) {
            // Create a new run, so that the new text is not within the same run.
            setCurrentLink();
            createNewRun();
        } else if (SAXHelpers.isFootnoteTag(qName)) {
            footnote = null;
            createNewRun();
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
                || SAXHelpers.isImageDataTag(qName) || SAXHelpers.isImageObjectTag(qName)
                || (SAXHelpers.isTitleTag(qName) && currentLevel.peekFigure())) {
            ensureNoTextAllowed();
        } else if (SAXHelpers.isFigureTag(qName)) {
            currentLevel.pop(Level.FIGURE, new DocxException("unexpected end of figure"));
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

        // This function is called for anything that is not a tag, including whitespace. In most cases, this whitespace
        // can be ignored, but it may also be important (the previous run has no space at the end and the next one
        // starts a new style).
        if (content.length() == 0) {
            return;
        }

        if (content.replaceAll("(\\s|\r|\n)+", "").length() == 0) {
            if (run == null) {
                return;
            }

            List<XWPFRun> runs = paragraph.getLast().getRuns();
            XWPFRun previous = runs.get(runs.size() - 1);
            String prevText = previous.text();

            if (! prevText.endsWith(" ")) {
                content = " ";
            } else {
                return;
            }
        }

        // More consistency checks, now that many cases have been excluded.
        if (run == null || runNumber < 0 || runCharactersNumber < 0) {
            throw new DocxException("no text allowed here");
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

            // TODO: Can't the conditions on runNumber be replaced by looking at paragraph.getLast().getRuns()?

            // If this is the first run of the paragraph, white space at the beginning is not important.
            if (runNumber == 0 && runCharactersNumber == 0) {
                content = content.replaceAll("^\\s*", ""); // Trim left.
            }

            // If the previous run ends with white space, as it is not relevant in this run, remove it from
            // the beginning of this run (i.e. trim left).
            // When adding text multiple times to the same run (i.e. runCharactersNumber > 0), the implemented
            // test does not work as expected.
            if (runNumber > 0 && runCharactersNumber == 0) {
                List<XWPFRun> runs = paragraph.getLast().getRuns();
                XWPFRun previous = runs.get(runs.size() - 1);
                if (previous.getCTR().getFootnoteReferenceList().size() == 0) {
                    String prevText = previous.text();

                    if (prevText.endsWith(" ")) {
                        content = content.replaceAll("^\\s+", ""); // Trim left.
                    }
                }
            }

            run.setText(content);
            runCharactersNumber += 1;
        }
    }

    @SuppressWarnings("RedundantIfStatement")
    private boolean isIgnorablePI(String target, String data) {
        // Ignore things like:
        // <? xml-model href="http://docbook.org/xml/5.0/rng/docbook.rng" schematypens="http://relaxng.org/ns/structure/1.0" ?>
        // <? xml-model href="http://docbook.org/xml/5.0/sch/docbook.sch" type="application/xml" schematypens="http://purl.oclc.org/dsdl/schematron" ?>
        if (! target.equals("xml-model")) {
            return false;
        }

        if (! data.startsWith("href=\"http://docbook.org/xml/")) {
            return false;
        }

        if (! data.endsWith("/rng/docbook.rng\" schematypens=\"http://relaxng.org/ns/structure/1.0\"")
                && ! data.endsWith("/sch/docbook.sch\" type=\"application/xml\" schematypens=\"http://purl.oclc.org/dsdl/schematron\"")) {
            return false;
        }

        // The part that has not been tested is just the version of DocBook.
        return true;
    }

    public void processingInstruction(String target, String data) {
        // Don't warn for all processing instructions. For instance, Oxygen always inserts this at the beginning
        // of a DocBook file (depending on the version of DocBook):
        //      <?xml-model href="http://docbook.org/xml/5.1/rng/docbook.rng" schematypens="http://relaxng.org/ns/structure/1.0"?>
        //      <?xml-model href="http://docbook.org/xml/5.1/sch/docbook.sch" type="application/xml" schematypens="http://purl.oclc.org/dsdl/schematron"?>
        // This warning is directed towards processing instructions in the middle of the text that are supposed
        // to be used when generating HTML or PDF.
        // The ignored processing instructions are still not round-tripped, but their loss is deemed not so important.
        if (isIgnorablePI(target, data)) {
            return;
        }

        System.err.println("Processing instructions are not taken into account and will not be round-tripped.");
        System.err.println("<?" + target + " " + data + "?>");
    }
}
