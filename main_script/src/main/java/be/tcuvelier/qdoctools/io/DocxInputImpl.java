package be.tcuvelier.qdoctools.io;

import be.tcuvelier.qdoctools.io.helpers.DocBookAlignment;
import be.tcuvelier.qdoctools.io.helpers.DocBookBlock;
import be.tcuvelier.qdoctools.io.helpers.DocBookFormatting;
import be.tcuvelier.qdoctools.io.helpers.Tuple;
import org.apache.poi.xwpf.usermodel.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings("WeakerAccess")
public class DocxInputImpl {
    private final OutputStream writer;
    private XWPFDocument doc;
    private XMLStreamWriter xmlStream;
    private Map<String, byte[]> images = new HashMap<>();

    private FormattingStack currentFormatting;
    private int currentDepth;
    private int currentSectionLevel;
    private boolean isWithinPart = false;
    private boolean isWithinChapter = false;

    private PreformattedMetadata preformattedMetadata;
    private boolean isDisplayedFigure = false;
    private boolean isWithinAdmonition = false;
    private boolean isWithinList = false; // TODO: Introduce a stack, like DocxOutput? Could merge in isWithinVariableList. Would help deal with nested lists.

    private int currentDefinitionListItemNumber = -1;
    private int currentDefinitionListItemSegmentNumber = -1;
    private List<XWPFParagraph> currentDefinitionListTitles = null;
    private List<List<XWPFParagraph>> currentDefinitionListContents = null;

    private boolean isWithinVariableList = false;
    private boolean isWithinVariableListEntry = false;

    private Set<Integer> captionPositions = new HashSet<>(); // Store position of paragraphs that have been recognised
    // as captions: find those that have not been, so the user can be warned when one of them is visited.

    @SuppressWarnings("FieldCanBeLocal")
    private static String indentation = "  ";
    private static String docbookNS = "http://docbook.org/ns/docbook";
    private static String xlinkNS = "http://www.w3.org/1999/xlink";

    @SuppressWarnings("WeakerAccess")
    public DocxInputImpl(String filename) throws IOException, XMLStreamException {
        writer = new ByteArrayOutputStream();
        doc = new XWPFDocument(new FileInputStream(filename));
        xmlStream = XMLOutputFactory.newInstance().createXMLStreamWriter(writer, "UTF-8");
    }

    @SuppressWarnings("WeakerAccess")
    public Map<String, byte[]> getImages() {
        return images;
    }

    private void writeIndent() throws XMLStreamException {
        xmlStream.writeCharacters(indentation.repeat(currentDepth));
    }

    private void increaseIndent() {
        currentDepth += 1;
    }

    private void decreaseIndent() throws XMLStreamException {
        if (currentDepth == 0) {
            throw new XMLStreamException("Cannot decrease indent when it is already zero.");
        }

        currentDepth -= 1;
    }

    private void writeNewLine() throws XMLStreamException {
        xmlStream.writeCharacters("\n");
    }

    private void openParagraphTag(String tag) throws XMLStreamException { // Paragraphs start on a new line, but contain inline elements on the same line. Examples: paragraphs, titles.
        openParagraphTag(tag, Collections.emptyMap());
    }

    private void openParagraphTag(String tag, Map<String, String> attributes) throws XMLStreamException {
        writeIndent();
        xmlStream.writeStartElement(docbookNS, tag);

        for (Map.Entry<String, String> attribute: attributes.entrySet()) {
            xmlStream.writeAttribute(attribute.getKey(), attribute.getValue());
        }
    }

    private void closeParagraphTag() throws XMLStreamException {
        xmlStream.writeEndElement();
        writeNewLine();
    }

    private void openInlineTag(String tag) throws XMLStreamException { // Inline elements are on the same line.
        openInlineTag(tag, Collections.emptyMap());
    }

    private void openInlineTag(String tag, Map<String, String> attributes) throws XMLStreamException {
        xmlStream.writeStartElement(docbookNS, tag);

        for (Map.Entry<String, String> attribute: attributes.entrySet()) {
            xmlStream.writeAttribute(attribute.getKey(), attribute.getValue());
        }
    }

    private void closeInlineTag() throws XMLStreamException {
        xmlStream.writeEndElement();
    }

    private void openBlockTag(String tag) throws XMLStreamException { // Blocks start on a new line, and have nothing else on the same line.
        openBlockTag(tag, Collections.emptyMap());
    }

    private void openBlockTag(String tag, Map<String, String> attributes) throws XMLStreamException {
        writeIndent();
        xmlStream.writeStartElement(docbookNS, tag);

        for (Map.Entry<String, String> attribute: attributes.entrySet()) {
            xmlStream.writeAttribute(attribute.getKey(), attribute.getValue());
        }

        increaseIndent();
        writeNewLine();
    }

    private void closeBlockTag() throws XMLStreamException {
        decreaseIndent();
        writeIndent();
        xmlStream.writeEndElement();
        writeNewLine();
    }

    private String detectDocumentType() throws XMLStreamException {
        switch (doc.getParagraphs().get(0).getStyleID()) {
            case "Title":
                return "article";
            case "Titlebook":
                return "book";
            default:
                // TODO: Should this rather default to article? But how would the title field be filled (it is necessary per the RNG)?
                throw new XMLStreamException("Unrecognised document type. Is the first paragraph a Title or a Title (book)?");
        }
    }

    @SuppressWarnings("WeakerAccess")
    public String toDocBook() throws XMLStreamException, IOException {
        // Initialise counters.
        currentDepth = 0;
        currentSectionLevel = 0;

        // Generate the document: root, prefixes, content, then close the sections that should be.
        xmlStream.writeStartDocument("UTF-8", "1.0");

        writeNewLine();
        writeIndent();
        xmlStream.writeStartElement("db", detectDocumentType(), docbookNS);
        xmlStream.setPrefix("db", docbookNS);
        xmlStream.writeNamespace("db", docbookNS);
        xmlStream.setPrefix("xlink", xlinkNS);
        xmlStream.writeNamespace("xlink", xlinkNS);
        xmlStream.writeAttribute("version", "5.1");
        increaseIndent();

        writeNewLine();

        for (IBodyElement b: doc.getBodyElements()) {
            visit(b);
        }

        int nCloses = currentSectionLevel; // </db:section>, as many times as required
        currentSectionLevel = 0;
        if (isWithinChapter) {
            nCloses += 1; // </db:chapter>
        }
        if (isWithinPart) { // </db:part>
            nCloses += 1;
        }

        for (int i = 0; i < nCloses; ++i) {
            closeBlockTag();
        }

        decreaseIndent(); // For consistency: this has no impact on the produced XML, but helps detect potential errors.
        xmlStream.writeEndElement();
        xmlStream.writeEndDocument();

        if (currentDepth != 0) {
            System.err.println("Reached the end of the document, but the indentation depth is not zero: " +
                    "there is a bug! Current depth: " + currentDepth);
        }

        // Generate the string from the stream.
        String xmlString = writer.toString();
        writer.close();
        return xmlString;
    }

    private void visit(IBodyElement b) throws XMLStreamException {
        // As the XWPF hierarchy is not made for visitors, and as it is not possible to alter it, use reflection...
        if (b instanceof XWPFParagraph) {
            visitParagraph((XWPFParagraph) b);
        } else if (b instanceof XWPFTable) {
            visitTable((XWPFTable) b);
        } else {
            System.out.println(b.getElementType());
            throw new RuntimeException("An element of type " + b.getClass().getName() + " has not been caught " +
                    "by a visit() method.");
        }
    }

    /** Dispatcher code, when information available in the body element is not enough. **/

    private void visitParagraph(XWPFParagraph p) throws XMLStreamException {
        // Only deal with paragraphs having some content (i.e. at least one non-empty run).
        if (p.getRuns().size() == 0) {
            return;
        }

        if (p.getRuns().stream().anyMatch(r -> r.text().length() > 0 || r.getEmbeddedPictures().size() > 0)) {
            // Once a paragraph has found its visitor, return from this function. This way, if a paragraph only
            // partially matches the conditions for a visitor, it can be handled by the generic ones.
            // (Just for robustness and ability to import external Word documents into the system.)

            // First, handle numbered paragraphs. They mostly indicate lists (or this is an external document, and no
            // assumption should be made -- it could very well be a heading).
            if (p.getNumID() != null &&
                    (p.getStyleID() == null || p.getStyleID().equals("Normal") || p.getStyleID().equals("ListParagraph"))) {
                visitListItem(p);
                return;
            }

            if (isWithinList) {
                throw new XMLStreamException("Assertion error: paragraph detected as within list while it has no numbering.");
            }

            // Then, dispatch along the style.
            if (p.getStyleID() == null || p.getStyleID().equals("") || p.getStyleID().equals("Caption")) {
                visitNormalParagraph(p);
                return;
            }

            switch (p.getStyleID()) {
                case "Title":
                case "Titlebook":
                    visitDocumentTitle(p);
                    return;
                case "Heading1":
                case "Heading2":
                case "Heading3":
                case "Heading4":
                case "Heading5":
                case "Heading6":
                case "Heading7":
                case "Heading8":
                case "Heading9":
                    visitSectionTitle(p);
                    return;
                case "Titlepart":
                    visitPartTitle(p);
                    return;
                case "Titlechapter":
                    visitChapterTitle(p);
                    return;
                case "ProgramListing":
                case "Screen":
                case "Synopsis":
                    visitPreformatted(p);
                    return;
                case "DefinitionListTitle":
                    visitDefinitionListTitle(p);
                    return;
                case "DefinitionListItem":
                    visitDefinitionListItem(p);
                    return;
                case "VariableListTitle":
                    visitVariableListTitle(p);
                    return;
                case "VariableListItem":
                    visitVariableListItem(p);
                    return;
                // TODO: Note, etc.?
                case "Normal": // The case with no style ID is already handled.
                    visitNormalParagraph(p);
                    return;
                case "ListParagraph":
                    throw new XMLStreamException("Found a list paragraph that has not been recognised as a list.");
                case "Caution":
                case "Important":
                case "Note":
                case "Tip":
                case "Warning":
                    visitAdmonition(p);
                    return;
                default:
                    // TODO: Don't panic when seeing something new, unless a command-line parameter says to (much more convenient for development!). For users, better to have a para than a crash.
                    System.err.println("Found a paragraph with an unsupported style: " + p.getStyleID());
                    throw new XMLStreamException("Found a paragraph with an unsupported style: " + p.getStyleID());
            }

            // The last switch returned from the function.
        }
    }

    /** Structure elements. **/

    private void visitDocumentTitle(XWPFParagraph p) throws XMLStreamException {
        // Called only once, at tbe beginning of the document. This function is thus also responsible for the main
        // <db:info> tag.
        currentSectionLevel = 0;

        openBlockTag("info");

        openParagraphTag("title");
        visitRuns(p.getRuns());
        closeParagraphTag(); // </db:title>

        // TODO: What about the abstract?

        closeBlockTag(); // </db:info>
    }

    private void visitChapterTitle(XWPFParagraph p) throws XMLStreamException {
        if (isWithinChapter) {
            closeBlockTag(); // </db:chapter>
        }
        isWithinChapter = true;

        openBlockTag("chapter");

        openParagraphTag("title");
        visitRuns(p.getRuns());
        closeParagraphTag(); // </db:title>
    }

    private void visitPartTitle(XWPFParagraph p) throws XMLStreamException {
        if (isWithinPart) {
            closeBlockTag(); // </db:part>
        }
        isWithinPart = true;

        openBlockTag("part");

        openParagraphTag("title");
        visitRuns(p.getRuns());
        closeParagraphTag(); // </db:title>

        // TODO: Part intro?
    }

    private void visitSectionTitle(XWPFParagraph p) throws XMLStreamException {
        // Pop sections until the current level is reached.
        int level = Integer.parseInt(p.getStyleID().replace("Heading", ""));
        if (level > currentSectionLevel + 1) {
            System.err.println("A section of level " + level + " was found within a section of level " +
                    currentSectionLevel + " (for instance, a subsubsection within a section): it seems there is " +
                    "a bad of use section levels in the input document. " +
                    "You could get a bad output (invalid XML and/or exceptions) in some cases.");
        }
        while (level <= currentSectionLevel) {
            closeBlockTag(); // </db:section>
            currentSectionLevel -= 1;
        }

        // Deal with this section.
        currentSectionLevel += 1;

        openBlockTag("section");

        openParagraphTag("title");
        visitRuns(p.getRuns());
        closeParagraphTag(); // </db:title>

        // TODO: Implement a check on the currentSectionLevel and the level (in case someone missed a level in the headings).
    }

    /** Paragraphs. **/

    private void visitNormalParagraph(@NotNull XWPFParagraph p) throws XMLStreamException {
        // Ignore captions, as they are handled directly within mediaobjects/tables.
        if (p.getStyleID() != null && p.getStyleID().equals("Caption")) {
            if (! captionPositions.contains(doc.getPosOfParagraph(p))) {
                throw new XMLStreamException("Caption not expected.");
            } else {
                return;
            }
        }

        if (p.getRuns().stream().anyMatch(r -> r.getEmbeddedPictures().size() >= 1)) {
            if (p.getRuns().stream().anyMatch(r -> r.getEmbeddedPictures().size() > 1)) {
                throw new XMLStreamException("Not yet implemented: multiple images per run."); // TODO: Several pictures per run? Seems unlikely.
            }

            // This paragraph only contains an image, no need for a <db:para>, but rather a <db:mediaobject>.
            // TODO: to be adapted if there are multiple pictures per run!
            isDisplayedFigure = p.getRuns().size() == 1;

            writeIndent();
            xmlStream.writeStartElement(docbookNS, "mediaobject");

            visitRuns(p.getRuns());

            // Write the caption (if it corresponds to the next paragraph).
            if (! isLastParagraph(p) && hasFollowingParagraphWithStyle(p, "Caption")) {
                int pos = doc.getPosOfParagraph(p);

                increaseIndent();
                writeIndent();
                decreaseIndent();
                xmlStream.writeStartElement(docbookNS, "caption");
                visitRuns(doc.getParagraphs().get(pos + 1).getRuns());
                xmlStream.writeEndElement(); // <db:caption>
                writeNewLine();

                captionPositions.add(pos + 1);
            }

            writeIndent();
            xmlStream.writeEndElement(); // </db:mediaobject> must be indented.
            writeNewLine();

            isDisplayedFigure = false;
        } else {
            // Normal case for a paragraph.
            writeIndent();
            xmlStream.writeStartElement(docbookNS, "para");
            visitRuns(p.getRuns());
            xmlStream.writeEndElement(); // </db:para> should not be indented.
            writeNewLine();
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static class PreformattedMetadata {
        final DocBookBlock type;
        Optional<String> language;
        Optional<Boolean> continuation;
        Optional<Integer> linenumbering;
        Optional<Integer> startinglinenumber;

        PreformattedMetadata(@NotNull String p) throws XMLStreamException {
            String[] options = Arrays.stream(p.split("\\.")).map(String::strip).filter(Predicate.not(String::isEmpty)).toArray(String[]::new);

            // Parse the type.
            if (options[0].equals("Program listing")) {
                type = DocBookBlock.PROGRAM_LISTING;
            } else {
                throw new XMLStreamException("Unrecognised preformatted metadata block: " + options[0]);
            }

            // Prefill all fields.
            language = Optional.empty();
            continuation = Optional.empty();
            linenumbering = Optional.empty();
            startinglinenumber = Optional.empty();

            // Parse the rests, if there is anything left.
            for (int i = 1; i < options.length; ++i) {
                String[] option = Arrays.stream(options[i].split(":")).map(String::strip).filter(Predicate.not(String::isEmpty)).toArray(String[]::new);

                if (option[0].equalsIgnoreCase("Language")) {
                    language = Optional.of(option[1]);
                } else if (option[0].equalsIgnoreCase("Continuation")) {
                    continuation = Optional.of(Boolean.valueOf(option[1]));
                } else if (option[0].equalsIgnoreCase("Line numbering")) {
                    linenumbering = Optional.of(Integer.parseInt(option[1]));
                } else if (option[0].equalsIgnoreCase("Starting line number")) {
                    startinglinenumber = Optional.of(Integer.parseInt(option[1]));
                } else {
                    throw new XMLStreamException("Unrecognised preformatted option: " + option[0]);
                }
            }
        }

        PreformattedMetadata(@NotNull XWPFParagraph p) throws XMLStreamException {
            this(p.getText());
        }

        Map<String, String> toMap() {
            Map<String, String> map = new HashMap<>();

            language.ifPresent(s -> map.put("language", s));
            continuation.ifPresent(b -> map.put("continuation", b.toString()));
            linenumbering.ifPresent(i -> map.put("linenumbering", i.toString()));
            startinglinenumber.ifPresent(i -> map.put("startinglinenumber", i.toString()));

            return Map.copyOf(map);
        }
    }

    private void visitPreformatted(@NotNull XWPFParagraph p) throws XMLStreamException {
        // The first paragraph of a program listing can give metadata about the listing that comes after.
        // Conditions: the whole paragraph must be in bold; the next paragraph must have the same style.
        if (p.getRuns().stream().allMatch(XWPFRun::isBold)) {
            int pos = p.getDocument().getPosOfParagraph(p);
            List<XWPFParagraph> lp = p.getDocument().getParagraphs();

            if (pos + 1 < lp.size()) {
                preformattedMetadata = new PreformattedMetadata(p);
                return;
            }
        }

        // Indentation must NOT be increased here: the content of such a tag, in particular the line feeds, must be
        // respected to the letter.

        if (preformattedMetadata != null) {
            if (preformattedMetadata.type != DocBookBlock.styleIDToBlock.get(p.getStyleID())) {
                throw new XMLStreamException("Preformatted metadata style does not correspond to the style of the next paragraph.");
            }

            openParagraphTag(DocBookBlock.styleIDToDocBookTag.get(p.getStyleID()), preformattedMetadata.toMap());
            preformattedMetadata = null;
        } else {
            openParagraphTag(DocBookBlock.styleIDToDocBookTag.get(p.getStyleID()));
        }

        visitRuns(p.getRuns());

        closeParagraphTag(); // </db:programlisting> or something else.
    }

    private void visitPictureRun(@NotNull XWPFRun r, @Nullable @SuppressWarnings("unused") XWPFRun prevRun,
                                 @SuppressWarnings("unused") boolean isLastRun) throws XMLStreamException {
        // TODO: saner implementation based on
        //  https://github.com/apache/tika/blob/master/tika-parsers/src/main/java/org/apache/tika/parser/microsoft/ooxml/XWPFWordExtractorDecorator.java#L361?
        // TODO: the current implementation might miss some images, it seems:
        //  https://stackoverflow.com/questions/47923079/apache-poi-xwpf-check-if-a-run-contains-a-picture

        if (r.getEmbeddedPictures().size() == 0) {
            throw new XMLStreamException("Supposed to get a picture run, but it has no picture.");
        }

        if (r.getEmbeddedPictures().size() > 1) {
            throw new XMLStreamException("More than one image in a run, which is not supported.");
        }

        // Output the image in a separate file.
        XWPFPicture picture = r.getEmbeddedPictures().get(0);
        byte[] image = picture.getPictureData().getData();
        String imageName = picture.getPictureData().getFileName();
        images.put(imageName, image);

        // Do the XML part: output a <db:inlinemediaobject> (whose beginning is on the same line as the rest
        // of the text; the inside part is indented normally; the closing tag is directly followed by the rest
        // of the text, if any).
        if (! isDisplayedFigure) {
            xmlStream.writeStartElement(docbookNS, "inlinemediaobject");
        }
        writeNewLine();
        increaseIndent();

        openBlockTag("imageobject");

        Map<String, String> attrs = new HashMap<>(Map.ofEntries(
                Map.entry("fileref", imageName),
                // https://stackoverflow.com/questions/16142634/getting-image-size-from-xwpf-document-apache-poi
                // Cx/Cx return values in EMUs (very different from EM).
                // https://github.com/apache/poi/pull/150
                Map.entry("width", (picture.getCTPicture().getSpPr().getXfrm().getExt().getCx() / 914_400) + "in"),
                Map.entry("depth", (picture.getCTPicture().getSpPr().getXfrm().getExt().getCy() / 914_400) + "in")
        ));
        if (isDisplayedFigure) {
            XWPFParagraph parent = ((XWPFParagraph) r.getParent());
            String dbAlign = DocBookAlignment.paragraphAlignmentToDocBookAttribute(parent.getAlignment());
            if (dbAlign.length() > 0) {
                attrs.put("align", dbAlign);
            }
        }
        openBlockTag("imagedata", attrs);

        closeBlockTag(); // </db:imageobject>
        decreaseIndent(); // TODO: ?

        if (! isDisplayedFigure) {
            writeIndent();
            xmlStream.writeEndElement(); // </db:inlinemediaobject>
            // No line feed as within a paragraph.
        }
    }

    private void visitAdmonition(XWPFParagraph p) throws XMLStreamException {
        // Open the admonition if this is the first paragraph with this kind of style.
        if (! isWithinAdmonition) {
            isWithinAdmonition = true;
            openBlockTag(DocBookBlock.styleIDToDocBookTag.get(p.getStyleID()));
        }

        visitNormalParagraph(p);

        // Close the admonition if the next paragraph does not have the same style.
        {
            int pos = p.getDocument().getPosOfParagraph(p);
            List<XWPFParagraph> paragraphs = p.getDocument().getParagraphs();

            boolean close;
            if (pos >= paragraphs.size() - 1) {
                close = true;
            } else {
                XWPFParagraph nextP = paragraphs.get(pos + 1);
                close = ! p.getStyleID().equals(nextP.getStyleID());
            }

            if (close) {
                closeBlockTag(); // </db:admonition tag>
                isWithinAdmonition = false;
            }
        }
    }

    /** Lists (implemented as paragraphs with a specific style and a numbering attribute). **/

    private boolean isListOrdered(XWPFParagraph p) {
        // What would be best to write...
//            if (p.getNumFmt() != null) {
//                isOrderedList = p.getNumFmt()...;
//            }

        // Instead, we have to dig deeper (have a look at what is done in DocxOutputImpl::createNumbering).
        int depth = p.getNumIlvl() == null? 0 : p.getNumIlvl().intValue();

        XWPFNumbering numbering = p.getDocument().getNumbering();
        BigInteger abstractNumID = numbering.getAbstractNumID(p.getNumID());
        XWPFAbstractNum abstractNum = numbering.getAbstractNum(abstractNumID);
        CTAbstractNum ctAbstractNum = abstractNum.getCTAbstractNum();

        if (ctAbstractNum.getLvlList().get(depth).getNumFmt() != null) {
            CTNumFmt ctNumFmt = ctAbstractNum.getLvlList().get(depth).getNumFmt();
            return ! ctNumFmt.getVal().equals(STNumberFormat.BULLET);
        } else {
            return false;
        }
    }

    private void visitListItem(XWPFParagraph p) throws XMLStreamException {
        int depth = p.getNumIlvl() == null? 0 : p.getNumIlvl().intValue(); // 0: one list to close; 1: two lists to close; etc.
        Optional<XWPFParagraph> prevPara;
        Optional<XWPFParagraph> nextPara;
        {
            int pos = p.getDocument().getPosOfParagraph(p);
            List<XWPFParagraph> paragraphs = p.getDocument().getParagraphs();
            prevPara = (pos > 0) ? Optional.of(paragraphs.get(pos - 1)) : Optional.empty();
            nextPara = (pos + 1) < paragraphs.size() ? Optional.of(paragraphs.get(pos + 1)) : Optional.empty();
        }

        if (depth > 0 && ! isWithinList) {
            throw new XMLStreamException("Assertion error: not at the first level of a list that has never started.");
        }

        { // At the beginning of the list (i.e. if not within a list right now), write the begin tag.
            boolean openList = false;
            if (prevPara.isEmpty()) { // The first paragraph of the document is already within a list: open one.
                openList = true;
            } else { // Otherwise, this paragraph has a previous one; check numbering differences between them.
                if (prevPara.get().getNumID() == null) { // Previous paragraph was not within a list: open one.
                    openList = true;
                } else { // Previous paragraph was already in a list: is there any meaningful difference between them?
                    // Two things to compare: the numbering and the depth.
                    int prevDepth = prevPara.get().getNumIlvl() == null ? 0 : prevPara.get().getNumIlvl().intValue();

                    if (! p.getNumID().equals(prevPara.get().getNumID())) { // Different numbering, hence different list: open one.
                        openList = true;
                    } else if (depth > prevDepth) { // Getting deeper in lists: open one.
                        openList = true;

                        // When increasing depth, only allow one level (otherwise, will get troubles when
                        // closing lists). Another solution would be to keep an internal level, but round-tripping
                        // would not be possible (this strange pattern in list depths would be lost).
                        if (depth != prevDepth + 1) {
                            throw new XMLStreamException("Difference in list depth larger than one: did you indent too much at some point?");
                        }
                    }
                }
            }

            if (openList) { // Implement the previous decision.
                openBlockTag(isListOrdered(p) ? "orderedlist" : "itemizedlist");
                isWithinList = true;
            }
        }

        // Write the content of the list item (and wrap it into a <para>).
        openBlockTag("listitem");
        visitNormalParagraph(p);

        // Deal with the last paragraph of the document: end the list item and all opened lists if required.
        int nCloses = 0;
        if (nextPara.isEmpty()) {
            // Close the opened lists. If depth is 0, just end the current list item and the list. Otherwise,
            // also close the containing lists.
            nCloses = 2; // </db:listitem>, then </db:orderedlist> or </db:itemizedlist>.
            isWithinList = false;
        }
        // There is a next paragraph, and things can get very complicated.
        else {
            int nextDepth = nextPara.get().getNumIlvl() == null? 0 : nextPara.get().getNumIlvl().intValue();

            // Should this list/list item be closed? That's gory.
            if (! p.getNumID().equals(nextPara.get().getNumID())) { // Is the next paragraph not in a list (its numbering is null)
                // or in a different list (indicated by a different numbering)? Close it.
                nCloses = 2 * (depth + 1); // </db:listitem>, then </db:orderedlist> or </db:itemizedlist>, for each level.
                isWithinList = false;
            } else if (nextDepth <= depth) { // Two cases:
                // - If staying at the same level, close this item, and that's it.
                // - Going less deep: must close a few things (a list item and a list per difference in depth, plus a list item).
                nCloses = 2 * (depth - nextDepth) + 1;
            }
        }

        // Implement the required closes.
        for (int i = 0; i < nCloses; ++i) {
            closeBlockTag();
        }
    }

    /** Definition lists. **/

    private static String toString(List<XWPFRun> r) {
        return r.stream().map(XWPFRun::text).collect(Collectors.joining(""));
    }

    private void visitDefinitionListTitle(XWPFParagraph p) throws XMLStreamException {
        // If a list is not being treated, initialise what is required.
        // currentDefinitionListTitles: store all the titles that have been found so far, so that the we can check
        // for each item whether it still uses exactly the same segtitle; otherwise, report an error.
        // currentDefinitionListItemNumber: indicates the position within the elements of a segmented list.
        // currentDefinitionListItemSegmentNumber: current position within an item of a segmented list (i.e. a segment).
        if (currentDefinitionListTitles == null) {
            currentDefinitionListItemNumber = 0;
            currentDefinitionListItemSegmentNumber = 0;
            currentDefinitionListTitles = new ArrayList<>();
            currentDefinitionListContents = new ArrayList<>();
        }

        // Check whether this title is new or has already been seen before.
        String thisTitle = toString(p.getRuns());
        int position = -1;
        for (int i = 0; i < currentDefinitionListTitles.size(); i++) {
            XWPFParagraph title = currentDefinitionListTitles.get(i);
            if (toString(title.getRuns()).equals(thisTitle)) {
                position = i;
                break;
            }
        }

        if (position == -1) {
            // Never seen before: add to the list of titles.
            currentDefinitionListTitles.add(p);
        } else {
            // Already seen: either this is normal (i.e. at the right place) or completely unexpected (e.g., one title
            // has been skipped in the input document).
            if (position == 0) {
                // New item.
                currentDefinitionListItemNumber += 1;
                currentDefinitionListItemSegmentNumber = 0;
            } else if (position != currentDefinitionListItemSegmentNumber) {
                // Error!
                throw new XMLStreamException("Mismatch within a definition list: expected to have " +
                        "a title '" + toString(currentDefinitionListTitles.get(position).getRuns()) + "', " +
                        "but got '" + thisTitle + "' instead.");
            }
        }

        // currentDefinitionListItemSegmentNumber increased when the item is seen.
    }

    private void visitDefinitionListItem(XWPFParagraph p) throws XMLStreamException {
        if (currentDefinitionListTitles == null) {
            throw new XMLStreamException("Unexpected definition list item; at least one title line must be present " +
                    "beforehand.");
        }

        // First item in the item: initialise a new list to store the segments of the item.
        if (currentDefinitionListItemSegmentNumber == 0) {
            currentDefinitionListContents.add(new ArrayList<>(currentDefinitionListTitles.size()));
        }

        // Buffer the paragraph at the right place.
        currentDefinitionListContents.get(currentDefinitionListItemNumber).add(p);

        currentDefinitionListItemSegmentNumber += 1;

        // If the next item is no more within a segmented list, serialise it all and forbid adding elements to the list.
        if (isLastParagraph(p) || ! hasFollowingParagraphWithStyle(p, "DefinitionListTitle")) {
            serialiseDefinitionList();

            currentDefinitionListItemNumber = -1;
            currentDefinitionListItemSegmentNumber = -1;
            currentDefinitionListTitles = null;
            currentDefinitionListContents = null;
        }
    }

    private void serialiseDefinitionList() throws XMLStreamException {
        // Once the whole definition list is built in currentDefinitionListTitles and currentDefinitionListContents,
        // serialise it as DocBook.
        openBlockTag("segmentedlist");

        for (XWPFParagraph title: currentDefinitionListTitles) {
            openParagraphTag("segtitle");
            visitRuns(title.getRuns());
            closeParagraphTag();
        }

        for (List<XWPFParagraph> item: currentDefinitionListContents) {
            openBlockTag("seglistitem");
            for (XWPFParagraph value: item) {
                openParagraphTag("seg");
                visitRuns(value.getRuns());
                closeParagraphTag();
            }
            closeBlockTag(); // </db:seglistitem>
        }

        closeBlockTag(); // </db:segmentedlist>
    }

    /** Variable lists. **/

    private void visitVariableListTitle(XWPFParagraph p) throws XMLStreamException {
        if (! isWithinVariableList) {
            openBlockTag("variablelist");
            isWithinVariableList = true;
        }

        if (! isWithinVariableListEntry) {
            openBlockTag("varlistentry");
            isWithinVariableListEntry = true;
        }

        openParagraphTag("term");
        visitRuns(p.getRuns());
        closeParagraphTag();
    }

    private void visitVariableListItem(XWPFParagraph p) throws XMLStreamException {
        if (! isWithinVariableList) {
            throw new XMLStreamException("Unexpected variable list item: must have a variable list title beforehand.");
        }

        if (! isWithinVariableListEntry) {
            throw new XMLStreamException("Inconsistent state when dealing with a variable list.");
        }

        isWithinVariableListEntry = false;
        openBlockTag("listitem");

        // Container for paragraphs.
        visitNormalParagraph(p);

        closeBlockTag(); // </db:listitem>
        closeBlockTag(); // </db:varlistentry>

        // If the next item is no more within a variable list, end the fight.
        if (isLastParagraph(p) || ! hasFollowingParagraphWithStyle(p, "VariableListTitle")) {
            closeBlockTag(); // </db:variablelist>
            isWithinVariableList = false;
        }
    }

    private boolean isLastParagraph(XWPFParagraph p) {
        int pos = doc.getPosOfParagraph(p);
        return pos == doc.getParagraphs().size() - 1;
    }

    private boolean hasFollowingParagraphWithStyle(XWPFParagraph p, String styleID) {
        int pos = doc.getPosOfParagraph(p);

        if (isLastParagraph(p)) {
            throw new AssertionError("Called hasFollowingParagraphWithStyle when this is the last paragraph; always call isLastParagraph first");
        }

        return doc.getParagraphs().get(pos + 1).getStyleID() != null
                && doc.getParagraphs().get(pos + 1).getStyleID().equals(styleID);
    }

    /** Tables. **/

    private void visitTable(XWPFTable t) throws XMLStreamException {
        openBlockTag("informaltable");

        // Output the table row per row, in HTML format.
        for (XWPFTableRow row: t.getRows()) {
            openBlockTag("tr");

            for (XWPFTableCell cell: row.getTableCells()) {
                // Special case: an empty cell. One paragraph with zero runs.
                if (cell.getParagraphs().size() == 0 ||
                        (cell.getParagraphs().size() == 1 && cell.getParagraphs().get(0).getRuns().size() == 0)) {
                    writeIndent();
                    xmlStream.writeEmptyElement(docbookNS, "td");
                    writeNewLine();

                    continue;
                }

                // Normal case.
                openBlockTag("td");
                for (XWPFParagraph p: cell.getParagraphs()){
                    visitParagraph(p);
                }
                closeBlockTag(); // </db:td>
            }

            closeBlockTag(); // </db:tr>
        }

        closeBlockTag(); // </db:informaltable>
    }

    /** Inline elements (runs, in Word parlance). **/

    private void visitRuns(List<XWPFRun> runs) throws XMLStreamException {
        currentFormatting = new FormattingStack();
        XWPFRun prevRun = null;

        for (Iterator<XWPFRun> iterator = runs.iterator(); iterator.hasNext(); ) {
            XWPFRun r = iterator.next();
            if (r instanceof XWPFHyperlinkRun) {
                visitHyperlinkRun((XWPFHyperlinkRun) r, prevRun, ! iterator.hasNext());
            } else if (r.getEmbeddedPictures().size() >= 1) {
                visitPictureRun(r, prevRun, ! iterator.hasNext());
            } else {
                visitRun(r, prevRun, ! iterator.hasNext());
            }
            prevRun = r;
        }

        currentFormatting = null;
    }

    private static String getStyle(XWPFRun r) {
        // https://github.com/apache/poi/pull/151
        CTRPr pr = r.getCTR().getRPr();
        if (pr == null) {
            return "";
        }

        CTString style = pr.getRStyle();
        if (style == null) {
            return "";
        }

        return style.getVal();
    }

    private static class FormattingStack {
        private Deque<DocBookFormatting> stack = new ArrayDeque<>();
        private Deque<DocBookFormatting> addedInRun;
        private Deque<DocBookFormatting> removedInRun;

        private void unstackUntilAndRemove(@Nullable DocBookFormatting f) throws XMLStreamException {
            // Example stack: BOLD, EMPHASIS, STRIKE.
            // Close EMPHASIS. Should unstack both STRIKE and EMPHASIS, then stack again STRIKE.
            // (Could do something better by storing the full paragraph before outputting the formattings, but the
            // added complexity is not worth it, as this case will probably not happen often.)

            // Unstack the tags until you reach the required formatting (or no formatting at all).
            Deque<DocBookFormatting> removed = new ArrayDeque<>();
            if (f != null) {
                while (stack.getLast() != f) {
                    DocBookFormatting current = stack.removeLast();
                    removed.push(current);
                }
            } else {
                while (stack.size() > 0) {
                    DocBookFormatting current = stack.removeLast();
                    removed.push(current);
                }
            }

            // Pop the formatting you're looking for.
            if (f != null) {
                removedInRun.add(f);
                stack.removeLast();
                if (stack.size() > 0 && stack.getLast().equals(f)) {
                    throw new XMLStreamException("Assertion failed.");
                }
            }

            // Push the untouched formattings. This destroys removed.
            Iterator<DocBookFormatting> itr = removed.descendingIterator();
            while (itr.hasNext()) {
                DocBookFormatting elt = itr.next();
                removedInRun.push(elt);
            }
        }

        private void dealWith(boolean isFormattingEnabled, DocBookFormatting f) throws XMLStreamException {
            if (isFormattingEnabled && ! stack.contains(f)) { // If this formatting is new, add it.
                addedInRun.add(f);
                stack.add(f);
            } else if (! isFormattingEnabled && stack.contains(f)) { // If this formatting is not enabled but was there
                // before, remove it.
                unstackUntilAndRemove(f);
            } // Otherwise, nothing going on (two cases: not enabled and not pending; enabled and opened previously).
        }

        private void unrecognisedStyle(@NotNull XWPFRun run) throws XMLStreamException {
            String styleID = getStyle(run);
            if (! styleID.equals("")) {
                throw new XMLStreamException("Unrecognised run style: " + styleID);
            } else {
                // No style, but maybe the user wants to tell the software something.

                if (run.getFontName() != null) {
                    // Cannot make a test on the font family, as it does not support monospaced information:
                    // https://docs.microsoft.com/en-us/dotnet/api/documentformat.openxml.spreadsheet.fontfamily?view=openxml-2.8.1
                    if (run.getFontName().equals("Consolas") || run.getFontName().equals("Courier New")) {
                        System.out.println("Error: text in a monospaced font (" + run.getFontName() + ") but not marked " +
                                "with a style to indicate its meaning.");
                        // TODO: default to <code>.
                    }
                }
            }
        }

        Tuple<Deque<DocBookFormatting>, Deque<DocBookFormatting>> processRun(@NotNull XWPFRun run, @Nullable XWPFRun prevRun)
                throws XMLStreamException {
            // Copied from STVerticalAlignRun.Enum. TODO: Better way to have these constants?
            int INT_SUPERSCRIPT = 2;
            int INT_SUBSCRIPT = 3;

            // Start dealing with this run: iterate through the formattings, translate them into DocBookFormatting
            // instances, and call dealWith.
            addedInRun = new ArrayDeque<>();
            removedInRun = new ArrayDeque<>();

            // Formattings encoded as run attributes.
            boolean isLink = run instanceof XWPFHyperlinkRun;
            dealWith(run.isBold(), DocBookFormatting.EMPHASIS_BOLD);
            dealWith(run.isItalic(), DocBookFormatting.EMPHASIS);
            dealWith(! isLink && run.getUnderline() != UnderlinePatterns.NONE, DocBookFormatting.EMPHASIS_UNDERLINE);
            dealWith(run.isStrikeThrough() || run.isDoubleStrikeThrough(), DocBookFormatting.EMPHASIS_STRIKETHROUGH);
            dealWith(run.getVerticalAlignment().intValue() == INT_SUPERSCRIPT, DocBookFormatting.SUPERSCRIPT);
            dealWith(run.getVerticalAlignment().intValue() == INT_SUBSCRIPT, DocBookFormatting.SUPERSCRIPT);

            // Formattings encoded as styles.
            String styleID = getStyle(run);
            String prevStyleID = prevRun == null? "" : getStyle(prevRun);
            if ((DocBookFormatting.styleIDToDocBookTag.containsKey(styleID) || styleID.equals(""))
                    && (prevRun == null || prevStyleID.equals("") || DocBookFormatting.styleIDToDocBookTag.containsKey(prevStyleID))) {
                // If both styles are equal, nothing to do. Otherwise...
                if (! prevStyleID.equals(styleID)) {
                    if (prevStyleID.equals("Normal") || prevStyleID.equals("")) {
                        DocBookFormatting f = DocBookFormatting.styleIDToFormatting.get(styleID);
                        addedInRun.add(f);
                        stack.add(f);
                    } else if (styleID.equals("Normal") || styleID.equals("")) {
                        unstackUntilAndRemove(DocBookFormatting.styleIDToFormatting.get(styleID));
                    } else {
                        DocBookFormatting f = DocBookFormatting.styleIDToFormatting.get(prevStyleID);
                        unstackUntilAndRemove(f);
                        addedInRun.add(f);
                        stack.add(f);
                    }
                }
            } else {
                // It's not because the previous condition was not met that an error should be shown.
                // Ignore the style Hyperlink, used for links: this is properly handled elsewhere, not using the
                // standard style mechanism (links have a special run type: XWPFHyperlinkRun).
                if (! styleID.equals("Hyperlink") && ! DocBookFormatting.styleIDToDocBookTag.containsKey(styleID)) {
                    unrecognisedStyle(run);
                }
                if (prevRun != null && ! prevStyleID.equals("Hyperlink") && ! DocBookFormatting.styleIDToDocBookTag.containsKey(prevStyleID)) {
                    unrecognisedStyle(prevRun);
                }
            }

            return new Tuple<>(addedInRun, removedInRun);
        }

        Deque<DocBookFormatting> formattings() {
            return stack;
        }
    }

    private void visitRun(@NotNull XWPFRun run, @Nullable XWPFRun prevRun, boolean isLastRun) throws XMLStreamException {
        // Deal with changes of formattings between the previous run and the current one.
        Tuple<Deque<DocBookFormatting>, Deque<DocBookFormatting>> formattings = currentFormatting.processRun(run, prevRun);
        for (DocBookFormatting ignored : formattings.second) {
            xmlStream.writeEndElement();
        }
        for (DocBookFormatting f: formattings.first) {
            // Emphasis and its variants.
            if (f == DocBookFormatting.EMPHASIS) {
                // Replaceable special case.
                if (currentFormatting.formattings().stream().anyMatch(DocBookFormatting::isMonospacedFormatting)) {
                    openInlineTag("replaceable");
                } else {
                    openInlineTag("emphasis");
                }
            } else if (f == DocBookFormatting.EMPHASIS_BOLD) {
                openInlineTag("emphasis", Map.of("role", "bold"));
            } else if (f == DocBookFormatting.EMPHASIS_STRIKETHROUGH) {
                openInlineTag("emphasis", Map.of("role", "strikethrough"));
            } else if (f == DocBookFormatting.EMPHASIS_UNDERLINE) {
                openInlineTag("emphasis", Map.of("role", "underline"));
            }
            // Full-blown tags.
            else {
                openInlineTag(DocBookFormatting.formattingToDocBookTag.get(f));
            }
        }

        // Actual text for this run.
        xmlStream.writeCharacters(run.text());

        // Close the tags at the end of the paragraph.
        if (isLastRun && currentFormatting.formattings().size() > 0) {
            for (DocBookFormatting ignored: currentFormatting.formattings()) {
                closeInlineTag();
            }
        }
    }

    private void visitHyperlinkRun(@NotNull XWPFHyperlinkRun r, @Nullable XWPFRun prevRun, boolean isLastRun)
            throws XMLStreamException {
        XWPFHyperlink link = r.getHyperlink(doc);

        xmlStream.writeStartElement(docbookNS, "link");
        xmlStream.writeAttribute(xlinkNS, "href", link.getURL());
        visitRun(r, prevRun, isLastRun); // Text and formatting attributes are inherited for XWPFHyperlinkRun.
        xmlStream.writeEndElement(); // </db:link>
    }
}
