package be.tcuvelier.qdoctools.io;

import be.tcuvelier.qdoctools.io.helpers.DocBookAlignment;
import be.tcuvelier.qdoctools.io.helpers.DocBookBlock;
import be.tcuvelier.qdoctools.io.helpers.DocBookFormatting;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.*;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("WeakerAccess")
public class DocxInputImpl {
    private final OutputStream writer;
    private XWPFDocument doc;
    private XMLStreamWriter xmlStream;
    private Map<String, byte[]> images = new HashMap<>();

    private int currentDepth;
    private int currentSectionLevel;
    private boolean isDisplayedFigure = false;
    private boolean isWithinList = false; // TODO: Introduce a stack, like DocxOutput? Could merge in isWithinVariableList.

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

    @SuppressWarnings("WeakerAccess")
    public String toDocBook() throws XMLStreamException, IOException {
        // Initialise counters.
        currentDepth = 0;
        currentSectionLevel = 0;

        // Generate the document: root, prefixes, content, then close the sections that should be.
        xmlStream.writeStartDocument("UTF-8", "1.0");

        writeNewLine(); writeIndent();
        xmlStream.writeStartElement("db", "article", docbookNS);
        xmlStream.setPrefix("db", docbookNS);
        xmlStream.writeNamespace("db", docbookNS);
        xmlStream.setPrefix("xlink", xlinkNS);
        xmlStream.writeNamespace("xlink", xlinkNS);
        increaseIndent();

        writeNewLine();

        for (IBodyElement b: doc.getBodyElements()) {
            visit(b);
        }

        while (0 < currentSectionLevel) {
            decreaseIndent();
            writeIndent();
            xmlStream.writeEndElement(); // </db:section>
            writeNewLine();
            currentSectionLevel -= 1;
        }

        decreaseIndent(); // For consistency: this has no impact on the produced XML.
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
        if (p.getRuns().size() > 0 && p.getRuns().stream().anyMatch(r -> r.text().length() > 0)) {
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
            if (p.getStyleID() == null) {
                visitNormalParagraph(p);
                return;
            }

            switch (p.getStyleID()) {
                case "Title":
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
                    // TODO: Part, Chapter?
                    visitSectionTitle(p);
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

        writeIndent();
        xmlStream.writeStartElement(docbookNS, "info");
        increaseIndent();

        writeNewLine();
        writeIndent();

        xmlStream.writeStartElement(docbookNS, "title");
        visitRuns(p.getRuns());
        xmlStream.writeEndElement(); // </db:title>
        writeNewLine();

        // TODO: What about the abstract?

        decreaseIndent();
        writeIndent();
        xmlStream.writeEndElement(); // </db:info>
        writeNewLine();
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
            decreaseIndent();
            writeIndent();
            xmlStream.writeEndElement(); // </db:section>
            writeNewLine();
            currentSectionLevel -= 1;
        }

        // Deal with this section.
        currentSectionLevel += 1;

        writeIndent();
        xmlStream.writeStartElement(docbookNS, "section");
        increaseIndent();
        writeNewLine();

        writeIndent();
        xmlStream.writeStartElement(docbookNS, "title");
        visitRuns(p.getRuns());
        xmlStream.writeEndElement(); // </db:title>
        writeNewLine();

        // TODO: Implement a check on the currentSectionLevel and the level (in case someone missed a level in the headings).
    }

    /** Paragraphs. **/

    private void visitNormalParagraph(XWPFParagraph p) throws XMLStreamException {
        // Ignore captions, as they are handled directly within mediaobjects/tables.
        if (p.getStyleID() != null && p.getStyleID().equals("Caption")) {
            if (! captionPositions.contains(doc.getPosOfParagraph(p))) {
                throw new XMLStreamException("Caption not expected.");
            } else {
                return;
            }
        }

        if (p.getRuns().size() == 1 && p.getRuns().get(0).getEmbeddedPictures().size() == 1) { // TODO: Several pictures per run? Seems unlikely.
            // This paragraph only contains an image, no need for a <db:para>, but rather a <db:mediaobject>.
            isDisplayedFigure = true;

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

    private void visitPreformatted(XWPFParagraph p) throws XMLStreamException {
        // Indentation must NOT be increased here: the content of such a tag, in particular the line feeds, must be
        // respected to the letter.
        writeIndent();
        xmlStream.writeStartElement(docbookNS, DocBookBlock.styleIDToDocBookTag.get(p.getStyleID()));

        visitRuns(p.getRuns());

        xmlStream.writeEndElement(); // </db:programlisting> or something else.
        writeNewLine();
    }

    private void visitPictureRun(XWPFRun r) throws XMLStreamException {
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

        writeIndent();
        xmlStream.writeStartElement(docbookNS, "imageobject");
        writeNewLine();
        increaseIndent();

        writeIndent();
        xmlStream.writeEmptyElement(docbookNS, "imagedata");
        xmlStream.writeAttribute(docbookNS, "fileref", imageName);
        // https://stackoverflow.com/questions/16142634/getting-image-size-from-xwpf-document-apache-poi
        // Cx/Cx return values in EMUs (very different from EM).
//        https://github.com/apache/poi/pull/150
        xmlStream.writeAttribute(docbookNS, "width", (picture.getCTPicture().getSpPr().getXfrm().getExt().getCx() / 914_400) + "in");
        xmlStream.writeAttribute(docbookNS, "depth", (picture.getCTPicture().getSpPr().getXfrm().getExt().getCy() / 914_400) + "in");
        if (isDisplayedFigure) {
            XWPFParagraph parent = ((XWPFParagraph) r.getParent());
            String dbAlign = DocBookAlignment.paragraphAlignmentToDocBookAttribute(parent.getAlignment());
            if (dbAlign.length() > 0) {
                xmlStream.writeAttribute(docbookNS, "align", dbAlign);
            }
        }
        writeNewLine();
        decreaseIndent();

        writeIndent();
        xmlStream.writeEndElement(); // </db:imageobject>
        writeNewLine();
        decreaseIndent();

        if (! isDisplayedFigure) {
            writeIndent();
            xmlStream.writeEndElement(); // </db:inlinemediaobject>
            // No line feed as within a paragraph.
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
        BigInteger numbering = p.getNumID();
        int depth = p.getNumIlvl() == null? 0 : p.getNumIlvl().intValue(); // 0: one list to close; 1: two lists to close; etc.
        int pos = p.getDocument().getPosOfParagraph(p);
        List<XWPFParagraph> paragraphs = p.getDocument().getParagraphs();
        Optional<XWPFParagraph> nextPara = (pos + 1) < paragraphs.size() ? Optional.of(paragraphs.get(pos + 1)) : Optional.empty();

        if (depth > 0 && ! isWithinList) {
            throw new XMLStreamException("Assertion error: not at the first level of a list that has never started.");
        }

        { // At the beginning of the list (i.e. if not within a list right now), write the begin tag.
            boolean openList = false;
//            if (! isWithinList) {
                if (pos == 0) { // The first paragraph of the document is already within a list: open one.
                    openList = true;
                } else { // Otherwise, this paragraph has a previous one; check numbering differences between them.
                    XWPFParagraph prevP = paragraphs.get(pos - 1);
                    if (prevP.getNumID() == null) { // Previous paragraph was not within a list: open one.
                        openList = true;
                    } else { // Previous paragraph was already in a list: is there any meaningful difference between them?
                        // Two things to compare: the numbering and the depth.
                        BigInteger prevNumbering = prevP.getNumID();
                        int prevDepth = prevP.getNumIlvl() == null ? 0 : prevP.getNumIlvl().intValue();

                        if (! prevNumbering.equals(numbering)) { // Different numbering, hence different list: open one.
                            openList = true;
                        } else if (depth > prevDepth) { // Getting deeper in lists: open one.
                            openList = true;

                            // When increasing depth, only allow one level (otherwise, will get troubles when
                            // closing lists). Another solution would be to keep an internal level, but round-tripping
                            // would not be possible (this strange pattern in list depths would be lost).
                            if (depth != prevDepth + 1) {
                                throw new XMLStreamException("Difference in list depth larger than one: ");
                            }
                        }
                    }
                }
//            }

            if (openList) { // Implement the previous decision.
                writeIndent();
                xmlStream.writeStartElement(docbookNS, isListOrdered(p) ? "orderedlist" : "itemizedlist");
                writeNewLine();
                increaseIndent();

                isWithinList = true;
            }
        }

        // Write the list item.
        writeIndent();
        xmlStream.writeStartElement(docbookNS, "listitem");
        writeNewLine();
        increaseIndent();

        // Write the content of the list item (and wrap it into a <para>). s
        visitNormalParagraph(p);

        // Deal with the last paragraph of the document: end the list item and all opened lists if required.
        if (nextPara.isEmpty()) {
            // Close the opened lists. If depth is 0, just end the current list item and the list. Otherwise,
            // also close the containing lists.
            int levelsToClose = depth + 1;
            while (levelsToClose > 0) {
                closeOneBlock(); // </db:listitem>
                closeOneBlock(); // </db:orderedlist> or </db:itemizedlist>
                levelsToClose -= 1;
            }
            isWithinList = false;
        }
        // There is a next paragraph, and things can get very complicated.
        else {
            XWPFParagraph nextP = nextPara.get();
            int nextDepth = nextP.getNumIlvl() == null? 0 : nextP.getNumIlvl().intValue();
            BigInteger nextNumbering = nextP.getNumID();

            int nCloses = 0;

            // Should this list/list item be closed? That's gory.
            if (! numbering.equals(nextNumbering)) { // Is the next paragraph not in a list (its numbering is null)
                // or in a different list (indicated by a different numbering)? Close it.
                nCloses = 2 * (depth + 1); // </db:listitem>, then </db:orderedlist> or </db:itemizedlist>, for each level.
                isWithinList = false;
            } else if (nextDepth == depth) { // If staying at the same level, close this item, and that's it.
                nCloses = 1; // </db:listitem>
            } else if (nextDepth < depth) { // Going less deep: must close a few things (a list item and a list per
                // difference in depth, plus a list item).
                nCloses = 2 * (depth - nextDepth) + 1;
            }

            // Implement the required closes.
            for (int i = 0; i < nCloses; ++i) {
                closeOneBlock();
            }
        }
    }

    private void closeOneBlock() throws XMLStreamException {
        decreaseIndent();
        writeIndent();
        xmlStream.writeEndElement();
        writeNewLine();
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
        writeIndent();
        xmlStream.writeStartElement(docbookNS, "segmentedlist");
        increaseIndent();
        writeNewLine();

        for (XWPFParagraph title: currentDefinitionListTitles) {
            writeIndent();
            xmlStream.writeStartElement(docbookNS, "segtitle");

            // No indentation, inline content.
            visitRuns(title.getRuns());

            xmlStream.writeEndElement(); // </db:segtitle>
            writeNewLine();
        }

        for (List<XWPFParagraph> item: currentDefinitionListContents) {
            writeIndent();
            xmlStream.writeStartElement(docbookNS, "seglistitem");
            increaseIndent();
            writeNewLine();

            for (XWPFParagraph value: item) {
                writeIndent();
                xmlStream.writeStartElement(docbookNS, "seg");

                // No indentation, inline content.
                visitRuns(value.getRuns());

                xmlStream.writeEndElement(); // </db:seg>
                writeNewLine();
            }

            decreaseIndent();
            writeIndent();
            xmlStream.writeEndElement(); // </db:seglistitem>
            writeNewLine();
        }

        decreaseIndent();
        writeIndent();
        xmlStream.writeEndElement(); // </db:segmentedlist>
        writeNewLine();
    }

    /** Variable lists. **/

    private void visitVariableListTitle(XWPFParagraph p) throws XMLStreamException {
        if (! isWithinVariableList) {
            writeIndent();
            xmlStream.writeStartElement(docbookNS, "variablelist");
            increaseIndent();
            writeNewLine();

            isWithinVariableList = true;
        }

        if (! isWithinVariableListEntry) {
            writeIndent();
            xmlStream.writeStartElement(docbookNS, "varlistentry");
            increaseIndent();
            writeNewLine();

            isWithinVariableListEntry = true;
        }

        writeIndent();
        xmlStream.writeStartElement(docbookNS, "term");

        // Inline content!
        visitRuns(p.getRuns());

        xmlStream.writeEndElement(); // </db:term>
        writeNewLine();
    }

    private void visitVariableListItem(XWPFParagraph p) throws XMLStreamException {
        if (! isWithinVariableList) {
            throw new XMLStreamException("Unexpected variable list item: must have a variable list title beforehand.");
        }

        if (! isWithinVariableListEntry) {
            throw new XMLStreamException("Inconsistent state when dealing with a variable list.");
        }

        isWithinVariableListEntry = false;

        writeIndent();
        xmlStream.writeStartElement(docbookNS, "listitem");
        increaseIndent();
        writeNewLine();

        // Container for paragraphs.
        visitNormalParagraph(p);

        decreaseIndent();
        writeIndent();
        xmlStream.writeEndElement(); // </db:listitem>
        writeNewLine();

        decreaseIndent();
        writeIndent();
        xmlStream.writeEndElement(); // </db:varlistentry>
        writeNewLine();

        // If the next item is no more within a variable list, end the fight.
        if (isLastParagraph(p) || ! hasFollowingParagraphWithStyle(p, "VariableListTitle")) {
            decreaseIndent();
            writeIndent();
            xmlStream.writeEndElement(); // </db:variablelist>
            writeNewLine();

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
        writeIndent();
        xmlStream.writeStartElement(docbookNS, "informaltable");
        increaseIndent();
        writeNewLine();

        // Output the table row per row, in HTML format.
        for (XWPFTableRow row: t.getRows()) {
            writeIndent();
            xmlStream.writeStartElement(docbookNS, "tr");
            increaseIndent();
            writeNewLine();

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
                writeIndent();
                xmlStream.writeStartElement(docbookNS, "td");
                increaseIndent();
                writeNewLine();

                for (XWPFParagraph p: cell.getParagraphs()){
                    visitParagraph(p);
                }

                decreaseIndent();
                writeIndent();
                xmlStream.writeEndElement(); // </db:td>
                writeNewLine();
            }

            decreaseIndent();
            writeIndent();
            xmlStream.writeEndElement(); // </db:tr>
            writeNewLine();
        }

        decreaseIndent();
        writeIndent();
        xmlStream.writeEndElement(); // </db:informaltable>
        writeNewLine();
    }

    /** Inline elements (runs, in Word parlance). **/

    private void visitRuns(List<XWPFRun> runs) throws XMLStreamException {
        for (XWPFRun r: runs) {
            if (r instanceof XWPFHyperlinkRun) {
                visitHyperlinkRun((XWPFHyperlinkRun) r);
            } else if (r.getEmbeddedPictures().size() >= 1) {
                visitPictureRun(r);
            } else {
                visitRun(r);
            }
        }
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

    private void visitRun(XWPFRun run) throws XMLStreamException {
        // TODO: maybe implement simplifications if two runs have the same set of formattings (compute the difference between sets of formatting).

        // Copied from STVerticalAlignRun.Enum. TODO: Better way to have these constants?
        int INT_SUPERSCRIPT = 2;
        int INT_SUBSCRIPT = 3;

        // Formatting tags (maybe several ones to add!).
        if (run.isBold()) {
            xmlStream.writeStartElement(docbookNS, "emphasis");
            xmlStream.writeAttribute(docbookNS, "role", "bold"); // TODO: Check if bold is used
            // everywhere else (or is strong preferred in other parts of the tool suite like QDoc import?).
            // Also adapt DocxOutput.Formatting.tagToFormatting().
        }
        if (run.isItalic()) {
            xmlStream.writeStartElement(docbookNS, "emphasis");
        }
        if (run.getUnderline() != UnderlinePatterns.NONE) {
            xmlStream.writeStartElement(docbookNS, "emphasis");
            xmlStream.writeAttribute(docbookNS, "role", "underline");
        }
        if (run.isStrikeThrough() || run.isDoubleStrikeThrough()) {
            xmlStream.writeStartElement(docbookNS, "emphasis");
            xmlStream.writeAttribute(docbookNS, "role", "strikethrough");
        }
        if (run.getVerticalAlignment().intValue() == INT_SUPERSCRIPT) {
            xmlStream.writeStartElement(docbookNS, "superscript");
        }
        if (run.getVerticalAlignment().intValue() == INT_SUBSCRIPT) {
            xmlStream.writeStartElement(docbookNS, "subscript");
        }

        String styleID = getStyle(run);
        if (DocBookFormatting.styleIDToDocBookTag.containsKey(styleID)) {
            xmlStream.writeStartElement(docbookNS, DocBookFormatting.styleIDToDocBookTag.get(styleID));
        } else if (! styleID.equals("")) {
            throw new XMLStreamException("Unrecognised run style: " + styleID);
        } else {
            // No style, but maybe the user wants to tell the software something.

            if (run.getFontName() != null) {
                // Cannot make a test on the font family, as it does not support monospaced information:
                // https://docs.microsoft.com/en-us/dotnet/api/documentformat.openxml.spreadsheet.fontfamily?view=openxml-2.8.1
                if (run.getFontName().equals("Consolas") || run.getFontName().equals("Courier New")) {
                    System.out.println("Warning: text in a monospaced font (" + run.getFontName() + ") but not marked " +
                            "with a style to indicate its meaning. By default, it will be wrapped in <code>.");
                    xmlStream.writeStartElement(docbookNS, "code");
                }
            }
        }

        // Actual text for this run.
        xmlStream.writeCharacters(run.text());

        // Close the tags if needed (strictly the reverse order from opening tags).
        // No need to respect order in the tests, as every thing here is just closing a DocBook tag.
        if (run.getUnderline() != UnderlinePatterns.NONE) {
            xmlStream.writeEndElement(); // </db:emphasis> for underline
        }
        if (run.isItalic()) {
            xmlStream.writeEndElement(); // </db:emphasis> for italics
        }
        if (run.isBold()) {
            xmlStream.writeEndElement(); // </db:emphasis> for bold
        }
        if (run.isStrikeThrough() || run.isDoubleStrikeThrough()) {
            xmlStream.writeEndElement(); // </db:emphasis> for strikethrough
        }
        if (run.getVerticalAlignment().intValue() == INT_SUPERSCRIPT) {
            xmlStream.writeEndElement(); // </db:superscript>
        }
        if (run.getVerticalAlignment().intValue() == INT_SUBSCRIPT) {
            xmlStream.writeEndElement(); // </db:subscript>
        }
        if (! styleID.equals("")) {
            xmlStream.writeEndElement(); // </db:something in the switch above>
        }
    }

    private void visitHyperlinkRun(XWPFHyperlinkRun r) throws XMLStreamException {
        XWPFHyperlink link = r.getHyperlink(doc);

        xmlStream.writeStartElement(docbookNS, "link");
        xmlStream.writeAttribute(xlinkNS, "href", link.getURL());
        visitRun(r); // Text and formatting attributes are inherited for XWPFHyperlinkRun.
        xmlStream.writeEndElement(); // </db:link>
    }
}
