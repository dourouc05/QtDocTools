package be.tcuvelier.qdoctools.io.docx;

import be.tcuvelier.qdoctools.core.constants.ContributorType;
import be.tcuvelier.qdoctools.core.constants.Language;
import be.tcuvelier.qdoctools.core.constants.Translations;
import be.tcuvelier.qdoctools.io.docx.fromdocx.*;
import be.tcuvelier.qdoctools.io.docx.helpers.DocBookAlignment;
import be.tcuvelier.qdoctools.io.docx.helpers.DocBookBlock;
import be.tcuvelier.qdoctools.io.docx.helpers.DocBookFormatting;
import be.tcuvelier.qdoctools.io.docx.helpers.Tuple;
import be.tcuvelier.qdoctools.io.docx.todocx.Level;
import com.joestelmach.natty.Parser;
import org.apache.poi.xwpf.usermodel.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTNumFmt;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;

import javax.xml.stream.XMLStreamException;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("WeakerAccess")
public class DocxInputImpl {
    private final XWPFDocument doc;
    private final DocBookStreamWriter dbStream;

    private final Map<String, byte[]> images = new HashMap<>();
    private final Deque<FormattingStack> currentFormatting = new ArrayDeque<>();
    private final Set<Integer> captionPositions = new HashSet<>(); // Store position of
    // paragraphs that have been recognised
    // as captions: find those that have not been, so the user can be warned when one of them is
    // visited.
    private final Set<Integer> backwardCaptionPositions = new HashSet<>(); // Store positions of
    // captions that have been seen
    // *before* an actual image. They are interpreted as figures.
    private final Set<Integer> abstractPositions = new HashSet<>(); // Store the position of the
    // abstract.
    private int currentSectionLevel;
    private boolean isWithinPart = false;
    private boolean isWithinChapter = false;
    private PreformattedMetadata preformattedMetadata;
    private boolean isDisplayedFigure = false;
    private Level isWithinAdmonition = null;
    private boolean isWithinList = false;
    private int currentDefinitionListItemNumber = -1;
    private int currentDefinitionListItemSegmentNumber = -1;
    private List<XWPFParagraph> currentDefinitionListTitles = null;
    private List<List<XWPFParagraph>> currentDefinitionListContents = null;
    private boolean isWithinVariableList = false;
    private boolean isWithinVariableListEntry = false;

    @SuppressWarnings("WeakerAccess")
    public DocxInputImpl(@NotNull String filename) throws IOException, XMLStreamException {
        doc = new XWPFDocument(new FileInputStream(filename));
//        dbStream = new DirectDocBookStreamWriter();
        dbStream = new DelayedSimplifyingDocBookStreamWriter(new DirectDocBookStreamWriter());
    }

    /**
     * Structure elements.
     **/

    private static Date parseDate(String str) {
        Parser parser = new Parser();
        return parser.parse(str).get(0).getDates().get(0);
    }

    private static String parseField(String str, Language lang) {
        String[] elements = str.split(Translations.colon.get(lang));
        if (elements.length == 2) {
            return elements[1];
        } else {
            throw new AssertionError("Expected to have only one field separator in " + str + ".");
        }
    }

    private static MetaDataParagraphs.Contributor parseContributor(String str, Language lang) {
        // Get the series of fields.
        String[] split = str.split(". ", 2);
        String contributorKind = split[0];
        String contributorInfo = split[1];

        MetaDataParagraphs.Contributor c = new MetaDataParagraphs.Contributor();

        // Parse the kind of contributor.
        if (contributorKind.equalsIgnoreCase(Translations.author.get(lang))) {
            c.type = ContributorType.AUTHOR;
        } else if (contributorKind.equalsIgnoreCase(Translations.proofreader.get(lang))) {
            c.type = ContributorType.PROOFREADER;
        } else if (contributorKind.equalsIgnoreCase(Translations.converter.get(lang))) {
            c.type = ContributorType.CONVERTER;
        } else if (contributorKind.equalsIgnoreCase(Translations.reviewer.get(lang))) {
            c.type = ContributorType.REVIEWER;
        } else if (contributorKind.equalsIgnoreCase(Translations.translator.get(lang))) {
            c.type = ContributorType.TRANSLATOR;
        }

        // Parse the other fields.
        String[] fields = contributorInfo.split("."); // TODO: why just "." as regex? Looks
        // suspicious.
        for (String field : fields) {
            String[] items = field.strip().split(Translations.colon.get(lang));
            if (items.length == 2) {
                if (items[1].equalsIgnoreCase(Translations.firstName.get(lang))) {
                    c.firstName = items[1];
                } else if (items[1].equalsIgnoreCase(Translations.surname.get(lang))) {
                    c.familyName = items[1];
                } else if (items[1].equalsIgnoreCase(Translations.pseudonym.get(lang))) {
                    c.pseudonym = items[1];
                } else if (items[1].equalsIgnoreCase(Translations.uriMain.get(lang))) {
                    c.mainUri = items[1];
                } else if (items[1].equalsIgnoreCase(Translations.uriHomepage.get(lang))) {
                    c.websiteUri = items[1];
                } else if (items[1].equalsIgnoreCase(Translations.uriBlog.get(lang))) {
                    c.blogUri = items[1];
                } else if (items[1].equalsIgnoreCase(Translations.uriGooglePlus.get(lang))) {
                    c.googlePlusgUri = items[1];
                } else if (items[1].equalsIgnoreCase(Translations.uriLinkedIn.get(lang))) {
                    c.linkedInUri = items[1];
                } else {
                    c.name = items[1]; // TODO: test me specifically.
                }
            } else {
                throw new AssertionError("Expected to have only one field separator in " + field + ", part of the larger contributor string " + str + ".");
            }
        }

        return c;
    }

    private static String dateToISO(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd").format(date);
    }

    /**
     * Definition lists.
     **/

    private static String toString(@NotNull List<XWPFRun> runs) {
        return runs.stream().map(XWPFRun::text).collect(Collectors.joining(""));
    }

    @SuppressWarnings("WeakerAccess")
    public Map<String, byte[]> getImages() {
        return images;
    }

    private String detectDocumentType() throws XMLStreamException {
        return switch (doc.getParagraphs().get(0).getStyleID()) {
            case "Title" -> "article";
            case "Titlebook" -> "book";
            default ->
                    throw new XMLStreamException("Unrecognised document type. The first paragraph" +
                            " must be a Title or " +
                            "a Title (book). Other document types are not implemented for now.");
        };
    }

    @SuppressWarnings("WeakerAccess")
    public String toDocBook() throws XMLStreamException, IOException {
        // Initialise counters.
        currentSectionLevel = 0;

        // Generate the document.
        String documentType = detectDocumentType();
        dbStream.startDocument(documentType, "5.1");
        for (IBodyElement b : doc.getBodyElements()) {
            visit(b);
        }

        // Close block-level tags that have not yet been closed.
        int nCloses = currentSectionLevel; // </db:section>, as many times as required
        currentSectionLevel = 0;
        if (isWithinChapter) {
            nCloses += 1; // </db:chapter>
        }
        if (isWithinPart) { // </db:part>
            nCloses += 1;
        }

        for (int i = 0; i < nCloses; ++i) {
            dbStream.closeBlockTag();
        }

        // Finally done with writing the document!
        dbStream.endDocument();

        // Perform final consistency checks.
        if (dbStream.getCurrentDepth() != 0) {
            System.err.println("Reached the end of the document, but the indentation depth is not" +
                    " zero: " +
                    "there is a bug! Current depth: " + dbStream.getCurrentDepth());
        }

        // Generate the string from the stream.
        return dbStream.write();
    }

    private void visit(@NotNull IBodyElement b) throws XMLStreamException {
        // As the XWPF hierarchy is not made for visitors, and as it is not possible to alter it,
        // use reflection...
        if (b instanceof XWPFParagraph) {
            visitParagraph((XWPFParagraph) b);
        } else if (b instanceof XWPFTable) {
            visitTable((XWPFTable) b);
        } else {
            System.out.println(b.getElementType());
            throw new RuntimeException("An element of type " + b.getClass().getName() + " has not" +
                    " been caught " +
                    "by a visit() method.");
        }
    }

    /**
     * Dispatcher code, when information available in the body element is not enough.
     **/

    private void visitParagraph(@NotNull XWPFParagraph p) throws XMLStreamException {
        // Only deal with paragraphs having some content (i.e. at least one non-empty run).
        if (p.getRuns().size() == 0) {
            return;
        }

        // Ignore empty paragraphs, i.e. no text and no pictures.
        if (p.getRuns().stream().allMatch(r -> r.text().length() == 0 && r.getEmbeddedPictures().size() == 0)) {
            return;
        }

        // Once a paragraph has found its visitor, return from this function. This way, if a
        // paragraph only
        // partially matches the conditions for a visitor, it can be handled by the more generic
        // ones.
        // (Just for robustness and ability to import external Word documents into the system.)

        // First, handle numbered paragraphs. They mostly indicate lists (or this is an external
        // document, and no
        // assumption should be made -- it could very well be a heading).
        if (p.getNumID() != null &&
                (p.getStyleID() == null || p.getStyleID().equals("Normal") || p.getStyleID().equals("ListParagraph"))) {
            visitListItem(p);
            return;
        }

        if (isWithinList) { // Should not really happen, though.
            throw new XMLStreamException("Assertion error: paragraph detected as within list " +
                    "while it has no numbering.");
        }

        // Then, dispatch along the style. Captions and abstracts have special treatment (eaten
        // by the relevant tags,
        // which then registers the ones that have already been output).
        if (p.getStyleID() == null || p.getStyleID().equals("")) {
            visitNormalParagraph(p);
            return;
        }

        if (p.getStyleID().equals("Caption")) {
            int pos = doc.getPosOfParagraph(p);
            if (!captionPositions.contains(pos)) {
                backwardCaptionPositions.add(pos);
            }
            // Captions are generated along with images. If the image is found first,
            // captionPositions is filled in
            // the corresponding visitor.
            // Otherwise, this visitor marks the paragraph in backwardCaptionPositions so that
            // the next paragraph knows
            // there is a caption before the image.
            return;
        }

        // Ignore paragraphs that are already dealt with within the headers.
        if (p.getStyleID().equals("Abstract")) {
            // Abstracts are generated in the header, which fills abstractPositions.
            if (!abstractPositions.contains(doc.getPosOfParagraph(p))) {
                throw new XMLStreamException("Abstract not expected.");
            }
            return;
        }

        if (p.getStyleID().equals("Author")) {
            // TODO: check this paragraph has been output, like abstracts?
            return;
        }

        switch (p.getStyleID()) {
            case "Title", "Titlebook" -> visitDocumentTitle(p);
            case "Heading1", "Heading2", "Heading3", "Heading4", "Heading5", "Heading6",
                    "Heading7", "Heading8", "Heading9" ->
                    visitSectionTitle(p);
            case "Titlepart" -> visitPartTitle(p);
            case "Titlechapter" -> visitChapterTitle(p);
            case "Editor" -> visitAuthor(p);
            case "ProgramListing", "Screen", "Synopsis" -> visitPreformatted(p);
            case "DefinitionListTitle" -> visitDefinitionListTitle(p);
            case "DefinitionListItem" -> visitDefinitionListItem(p);
            case "VariableListTitle" -> visitVariableListTitle(p);
            case "VariableListItem" ->
                    visitVariableListItem(p); // The case with no style ID is already handled.
            case "Normal", "FootnoteText" -> visitNormalParagraph(p);
            case "ListParagraph" ->
                    throw new XMLStreamException("Found a list paragraph that has not been " +
                            "recognised as a list.");
            case "Caution", "Important", "Note", "Tip", "Warning" -> visitAdmonition(p);
            default -> {
                // TODO: Don't panic when seeing something new, unless a command-line parameter
                //  says to (much more convenient for development!). For users, better to have a
                //  para than a crash.
                System.err.println("Found a paragraph with an unsupported style: " + p.getStyleID());
                throw new XMLStreamException("Found a paragraph with an unsupported style: " + p.getStyleID());
            }
        }

        // The last switch returned from the function.
    }

    private MetaDataParagraphs gatherAbstractFollowing(@NotNull XWPFParagraph p) {
        int pos = p.getDocument().getPosOfParagraph(p);
        List<XWPFParagraph> paragraphs = p.getDocument().getParagraphs();

        MetaDataParagraphs metadata = new MetaDataParagraphs();

        // No next paragraph? No possible abstract.
        if (pos + 1 >= paragraphs.size()) {
            return metadata;
        }

        // Abstract is made of a sequence of paragraphs with the Abstract style, which implies
        // that there is
        // no paragraph of another style in between.
        boolean foundRealAbstract = false;
        for (int i = pos + 1; i < paragraphs.size(); ++i) {
            String style = paragraphs.get(i).getStyleID();

            // Found an abstract: record this paragraph, go to the next one.
            if (style.equals("Abstract")) {
                foundRealAbstract = true;
                metadata.abstractParas.add(paragraphs.get(i));
                continue;
            }

            // Already found an abstract, and this is no more an abstract? Stop here.
            if (foundRealAbstract) {
                break;
            }

            // Found an interesting paragraph: record this paragraph, parse it, go to the next one.
            String text = paragraphs.get(i).getText().strip();
            boolean recognised = false;
            for (Language lang : Language.values()) {
                // Dates.
                if (text.startsWith(Translations.date.get(lang))) {
                    metadata.datePara = paragraphs.get(i);
                    metadata.date = parseDate(parseField(text, lang));
                    recognised = true;
                } else if (text.startsWith(Translations.pubdate.get(lang))) {
                    metadata.pubdatePara = paragraphs.get(i);
                    metadata.pubdate = parseDate(parseField(text, lang));
                    recognised = true;
                }

                // Authors and contributors.
                else if (text.startsWith(Translations.author.get(lang))
                        || text.startsWith(Translations.proofreader.get(lang))
                        || text.startsWith(Translations.converter.get(lang))
                        || text.startsWith(Translations.reviewer.get(lang))
                        || text.startsWith(Translations.translator.get(lang))) {
                    metadata.contributorParas.add(p);
                    metadata.contributors.add(parseContributor(text, lang));
                    recognised = true;
                }
            }

            // Nothing else should follow.
            if (!recognised) {
                break;
            }
        }

        return metadata;
    }

    private void visitTitle(@NotNull XWPFParagraph p) throws XMLStreamException {
        dbStream.openParagraphTag("title");
        visitRuns(p.getRuns());
        dbStream.closeParagraphTag();
    }

    private void visitPublicationDate(Date d) throws XMLStreamException {
        if (d == null) {
            return;
        }

        dbStream.openParagraphTag("date");
        dbStream.writeCharacters(dateToISO(d));
        dbStream.closeParagraphTag();
    }

    private void visitUpdateDate(Date d) throws XMLStreamException {
        if (d == null) {
            return;
        }

        dbStream.openParagraphTag("date");
        dbStream.writeCharacters(dateToISO(d));
        dbStream.closeParagraphTag();
    }

    private void visitContributor(@NotNull MetaDataParagraphs.Contributor c) throws XMLStreamException {
        if (c.type == ContributorType.AUTHOR) {
            dbStream.openBlockTag("author");
        } else {
            String type = ContributorType.typeToDocBookOtherCreditClass(c.type);
            assert type != null; // Only if not recognised.
            dbStream.openBlockTag("othercredit", Map.of("class", type));
        }

        dbStream.openParagraphTag("personname");
        if (c.pseudonym != null) {
            dbStream.openParagraphTag("othername", Map.of("role", "pseudonym"));
            dbStream.writeCharacters(c.pseudonym);
            dbStream.closeParagraphTag();
        }
        if (c.name != null) {
            dbStream.writeCharacters(c.name);
        } else {
            if (c.firstName != null) {
                dbStream.openParagraphTag("firstname");
                dbStream.writeCharacters(c.firstName);
                dbStream.closeParagraphTag();
            }
            if (c.familyName != null) {
                dbStream.openParagraphTag("surname");
                dbStream.writeCharacters(c.familyName);
                dbStream.closeParagraphTag();
            }
        }
        dbStream.closeParagraphTag(); // <db:personname>

        if (c.mainUri != null) {
            dbStream.openParagraphTag("uri", Map.of("type", "main-uri"));
            dbStream.writeCharacters(c.mainUri);
            dbStream.closeParagraphTag();
        }
        if (c.websiteUri != null) {
            dbStream.openParagraphTag("uri", Map.of("type", "homepage"));
            dbStream.writeCharacters(c.websiteUri);
            dbStream.closeParagraphTag();
        }
        if (c.blogUri != null) {
            dbStream.openParagraphTag("uri", Map.of("type", "blog"));
            dbStream.writeCharacters(c.blogUri);
            dbStream.closeParagraphTag();
        }
        if (c.googlePlusgUri != null) {
            dbStream.openParagraphTag("uri", Map.of("type", "google-plus"));
            dbStream.writeCharacters(c.googlePlusgUri);
            dbStream.closeParagraphTag();
        }
        if (c.linkedInUri != null) {
            dbStream.openParagraphTag("uri", Map.of("type", "linkedin"));
            dbStream.writeCharacters(c.linkedInUri);
            dbStream.closeParagraphTag();
        }

        dbStream.closeBlockTag(); // <db:author>, <db:editor>, or <db:othercredit>
    }

    private void visitTitleAndAbstract(@NotNull XWPFParagraph p) throws XMLStreamException {
        MetaDataParagraphs metadata = gatherAbstractFollowing(p);

        if (!metadata.hasMetaData()) {
            dbStream.openParagraphTag("title");
            visitRuns(p.getRuns());
            dbStream.closeParagraphTag();
        } else {
            dbStream.openBlockTag("info");
            visitTitle(p);
            visitPublicationDate(metadata.pubdate);
            visitUpdateDate(metadata.date);

            for (MetaDataParagraphs.Contributor c : metadata.contributors) {
                visitContributor(c);
            }

            if (metadata.abstractParas.size() > 0) {
                dbStream.openBlockTag("abstract");
                for (XWPFParagraph ap : metadata.abstractParas) {
                    visitNormalParagraph(ap);
                    abstractPositions.add(doc.getPosOfParagraph(ap));
                }
                dbStream.closeBlockTag();
            }

            dbStream.closeBlockTag(); // </db:info>
        }
    }

    private void visitPartTitle(@NotNull XWPFParagraph p) throws XMLStreamException {
        if (isWithinPart) {
            dbStream.closeBlockTag(); // </db:part>
        }
        isWithinPart = true;
        dbStream.openBlockTag("part");

        // Major difference with visitTitleAndAbstract: the abstract is output as a partintro,
        // outside the info container.
        MetaDataParagraphs metadata = gatherAbstractFollowing(p);

        if (!metadata.hasMetaDataExceptAbstract()) {
            dbStream.openParagraphTag("title");
            visitRuns(p.getRuns());
            dbStream.closeParagraphTag();
        } else {
            dbStream.openBlockTag("info");
            visitTitle(p);
            visitPublicationDate(metadata.pubdate);
            visitUpdateDate(metadata.date);

            for (MetaDataParagraphs.Contributor c : metadata.contributors) {
                visitContributor(c);
            }

            dbStream.closeBlockTag(); // </db:info>
        }

        if (metadata.abstractParas.size() > 0) {
            dbStream.openBlockTag("partintro");
            for (XWPFParagraph ap : metadata.abstractParas) {
                visitNormalParagraph(ap);
                abstractPositions.add(doc.getPosOfParagraph(ap)); // TODO: should be done by
                // gatherAbstractFollowing?
            }
            dbStream.closeBlockTag();
        }
    }

    private void visitDocumentTitle(@NotNull XWPFParagraph p) throws XMLStreamException {
        // Called only once, at tbe beginning of the document. This function is thus also
        // responsible for the main
        // <db:info> tag.
        currentSectionLevel = 0;
        visitTitleAndAbstract(p);
    }

    private void visitChapterTitle(@NotNull XWPFParagraph p) throws XMLStreamException {
        if (isWithinChapter) {
            dbStream.closeBlockTag(); // </db:chapter>
        }
        isWithinChapter = true;

        dbStream.openBlockTag("chapter");
        visitTitleAndAbstract(p);
    }

    private void visitAuthor(@NotNull XWPFParagraph p) throws XMLStreamException {
        dbStream.openParagraphTag("author");
        visitRuns(p.getRuns());
        dbStream.closeParagraphTag(); // </db:author>
    }

    private void visitSectionTitle(@NotNull XWPFParagraph p) throws XMLStreamException {
        // Pop sections until the current level is reached.
        int level = Integer.parseInt(p.getStyleID().replace("Heading", ""));
        if (level > currentSectionLevel + 1) {
            System.err.println("A section of level " + level + " was found within a section of " +
                    "level " +
                    currentSectionLevel + " (for instance, a subsubsection within a section): it " +
                    "seems there is " +
                    "a bad of use section levels in the input document. " +
                    "You could get a bad output (invalid XML and/or exceptions) in some cases.");
        }
        while (level <= currentSectionLevel) {
            dbStream.closeBlockTag(); // </db:section>
            currentSectionLevel -= 1;
        }

        // Deal with this section.
        currentSectionLevel += 1;

        dbStream.openBlockTag("section");

        dbStream.openParagraphTag("title");
        visitRuns(p.getRuns());
        dbStream.closeParagraphTag(); // </db:title>

        // TODO: Implement a check on the currentSectionLevel and the level (in case someone
        //  missed a level in the headings).
    }

    /**
     * Paragraphs.
     **/

    private void visitNormalParagraph(@NotNull XWPFParagraph p) throws XMLStreamException {
        if (p.getRuns().stream().allMatch(r -> r.getEmbeddedPictures().size() >= 1)) {
            // Special case: only images in this paragraph.

            if (p.getRuns().stream().anyMatch(r -> r.getEmbeddedPictures().size() > 1)) {
                throw new XMLStreamException("Not yet implemented: multiple images per run."); //
                // TODO: Several pictures per run? Seems unlikely.
            }

            // This paragraph only contains an image, no need for a <db:para>, but rather a
            // <db:mediaobject> or
            // a <db:figure>.
            // TODO: to be adapted if there are multiple pictures per run!
            isDisplayedFigure = p.getRuns().size() == 1;

            int pos = doc.getPosOfParagraph(p);
            boolean hasCaptionBefore = backwardCaptionPositions.contains(pos - 1);

            if (hasCaptionBefore) {
                dbStream.openBlockTag("figure");
                XWPFParagraph captionP = doc.getParagraphArray(pos - 1);

                dbStream.openParagraphTag("title");
                visitRuns(captionP.getRuns());
                dbStream.closeParagraphTag(); // </db:title>
            }

            dbStream.openBlockTag("mediaobject");
            visitRuns(p.getRuns());

            // Write the caption (if it corresponds to the next paragraph).
            if (!hasCaptionBefore && !isLastParagraph(p) && hasFollowingParagraphWithStyle(p,
                    "Caption")) {
                dbStream.openParagraphTag("caption");
                visitRuns(doc.getParagraphs().get(pos + 1).getRuns());
                dbStream.closeParagraphTag(); // </db:caption>

                captionPositions.add(pos + 1);
            }

            dbStream.closeBlockTag(); // </db:mediaobject>

            if (hasCaptionBefore) {
                dbStream.closeBlockTag(); // </db:figure>
            }

            isDisplayedFigure = false;
        } else {
            // Normal case for a paragraph.
            dbStream.openParagraphTag("para");
            visitRuns(p.getRuns());
            dbStream.closeParagraphTag();
        }
    }

    private void visitPreformatted(@NotNull XWPFParagraph p) throws XMLStreamException {
        // The first paragraph of a program listing can give metadata about the listing that
        // comes after.
        // Conditions: the whole paragraph must be in bold; the next paragraph must have the same
        // style.
        Stream<XWPFRun> usefulRuns = p.getRuns().stream().filter(r -> r.text().replaceAll("\\s+",
                "").length() > 0);
        if (usefulRuns.allMatch(XWPFRun::isBold)) {
            int pos = p.getDocument().getPosOfParagraph(p);
            List<XWPFParagraph> lp = p.getDocument().getParagraphs();

            if (pos + 1 < lp.size()) {
                preformattedMetadata = new PreformattedMetadata(p);
                return;
            }
        }

        // Indentation must NOT be increased here: the content of such a tag, in particular the
        // line feeds, must be
        // respected to the letter.

        if (preformattedMetadata != null) {
            if (preformattedMetadata.getType() != DocBookBlock.styleIDToBlock.get(p.getStyleID())) {
                throw new XMLStreamException("Preformatted metadata style does not correspond to " +
                        "the style of the next paragraph.");
            }

            dbStream.openParagraphTag(DocBookBlock.styleIDToDocBookTag.get(p.getStyleID()),
                    preformattedMetadata.toMap());
            preformattedMetadata = null;
        } else {
            dbStream.openParagraphTag(DocBookBlock.styleIDToDocBookTag.get(p.getStyleID()));
        }

        visitRuns(p.getRuns());

        dbStream.closeParagraphTag(); // </db:programlisting> or something else.
    }

    private void visitPictureRun(@NotNull XWPFRun r,
            @Nullable @SuppressWarnings("unused") XWPFRun prevRun,
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

        // Do the XML part: output a <db:inlinemediaobject> if there is no block-level
        // equivalent, then imageobject.
        if (!isDisplayedFigure) {
            dbStream.openBlockInlineTag("inlinemediaobject");
        }

        dbStream.openBlockTag("imageobject");

        final int pointsToInches = 72;
        Map<String, String> attrs = new LinkedHashMap<>(Map.ofEntries(
                Map.entry("fileref", imageName),
                Map.entry("width", picture.getWidth() / pointsToInches + "in"),
                Map.entry("depth", picture.getDepth() / pointsToInches + "in")
        ));
        if (isDisplayedFigure) {
            XWPFParagraph parent = ((XWPFParagraph) r.getParent());
            String dbAlign =
                    DocBookAlignment.paragraphAlignmentToDocBookAttribute(parent.getAlignment());
            if (dbAlign.length() > 0) {
                attrs.put("align", dbAlign);
            }
        }
        dbStream.emptyBlockTag("imagedata", attrs);

        dbStream.closeBlockTag(); // </db:imageobject>
        if (!isDisplayedFigure) {
            dbStream.closeBlockInlineTag(); // </db:inlinemediaobject>
        }
    }

    private void visitAdmonition(@NotNull XWPFParagraph p) throws XMLStreamException {
        // Open the admonition if this is the first paragraph with this kind of style.
        if (isWithinAdmonition == null) {
            String qname = DocBookBlock.styleIDToDocBookTag.get(p.getStyleID());
            dbStream.openBlockTag(qname);
            isWithinAdmonition = Level.fromAdmonitionQname(qname);
        }

        visitNormalParagraph(p);

        // Close the admonition if the next paragraph does not have the same style.
        {
            int pos = p.getDocument().getPosOfParagraph(p);
            List<XWPFParagraph> paragraphs = p.getDocument().getParagraphs();

            boolean close;
            if (pos >= paragraphs.size() - 1) { // If last paragraph.
                close = true;
            } else { // Check on the next paragraph.
                XWPFParagraph nextP = paragraphs.get(pos + 1);
                String targetStyle =
                        DocBookBlock.tagToStyleID(Level.qnameFromAdmonition(isWithinAdmonition));

                if (nextP.getStyleID().equals(targetStyle)) { // Same style.
                    close = false;
                } else if (pos <= paragraphs.size() - 2) {
                    // Has at least two paragraphs after: if it's a title and a figure (with the
                    // admonition style),
                    // the admonition does not stop here.
                    XWPFParagraph nextNextP = paragraphs.get(pos + 2);
                    close = !nextNextP.getStyleID().equals(targetStyle);
                } else { // Not a single case matches, close the admonition.
                    close = true;
                }
            }

            if (close) {
                dbStream.closeBlockTag(); // </db:admonition tag>
                isWithinAdmonition = null;
            }
        }
    }

    /**
     * Lists (implemented as paragraphs with a specific style and a numbering attribute).
     **/

    private boolean isListOrdered(@NotNull XWPFParagraph p) {
        // What would be best to write...
//            if (p.getNumFmt() != null) {
//                isOrderedList = p.getNumFmt()...;
//            }

        // Instead, we have to dig deeper (have a look at what is done in
        // DocxOutputImpl::createNumbering).
        int depth = p.getNumIlvl() == null ? 0 : p.getNumIlvl().intValue();

        XWPFNumbering numbering = p.getDocument().getNumbering();
        BigInteger abstractNumID = numbering.getAbstractNumID(p.getNumID());
        XWPFAbstractNum abstractNum = numbering.getAbstractNum(abstractNumID);
        CTAbstractNum ctAbstractNum = abstractNum.getCTAbstractNum();

        if (ctAbstractNum.getLvlList().get(depth).getNumFmt() != null) {
            CTNumFmt ctNumFmt = ctAbstractNum.getLvlList().get(depth).getNumFmt();
            return !ctNumFmt.getVal().equals(STNumberFormat.BULLET);
        } else {
            return false;
        }
    }

    private void visitListItem(@NotNull XWPFParagraph p) throws XMLStreamException {
        int depth = p.getNumIlvl() == null ? 0 : p.getNumIlvl().intValue(); // 0: one list to
        // close; 1: two lists to close; etc.
        Optional<XWPFParagraph> prevPara;
        Optional<XWPFParagraph> nextPara;
        {
            int pos = p.getDocument().getPosOfParagraph(p);
            List<XWPFParagraph> paragraphs = p.getDocument().getParagraphs();
            prevPara = (pos > 0) ? Optional.of(paragraphs.get(pos - 1)) : Optional.empty();
            nextPara = (pos + 1) < paragraphs.size() ? Optional.of(paragraphs.get(pos + 1)) :
                    Optional.empty();
        }

        if (depth > 0 && !isWithinList) {
            throw new XMLStreamException("Assertion error: not at the first level of a list that " +
                    "has never started.");
        }

        { // At the beginning of the list (i.e. if not within a list right now), write the begin
            // tag.
            boolean openList = false;
            if (prevPara.isEmpty()) { // The first paragraph of the document is already within a
                // list: open one.
                openList = true;
            } else { // Otherwise, this paragraph has a previous one; check numbering differences
                // between them.
                if (prevPara.get().getNumID() == null) { // Previous paragraph was not within a
                    // list: open one.
                    openList = true;
                } else { // Previous paragraph was already in a list: is there any meaningful
                    // difference between them?
                    // Two things to compare: the numbering and the depth.
                    int prevDepth = prevPara.get().getNumIlvl() == null ? 0 :
                            prevPara.get().getNumIlvl().intValue();

                    if (!p.getNumID().equals(prevPara.get().getNumID())) { // Different
                        // numbering, hence different list: open one.
                        openList = true;
                    } else if (depth > prevDepth) { // Getting deeper in lists: open one.
                        openList = true;

                        // When increasing depth, only allow one level (otherwise, will get
                        // troubles when
                        // closing lists). Another solution would be to keep an internal level,
                        // but round-tripping
                        // would not be possible (this strange pattern in list depths would be
                        // lost).
                        if (depth != prevDepth + 1) {
                            throw new XMLStreamException("Difference in list depth larger than " +
                                    "one: did you indent too much at some point?");
                        }
                    }
                }
            }

            if (openList) { // Implement the previous decision.
                dbStream.openBlockTag(isListOrdered(p) ? "orderedlist" : "itemizedlist");
                isWithinList = true;
            }
        }

        // Write the content of the list item (and wrap it into a <para>).
        dbStream.openBlockTag("listitem");
        visitNormalParagraph(p);

        // Deal with the last paragraph of the document: end the list item and all opened lists
        // if required.
        int nCloses = 0;
        if (nextPara.isEmpty()) {
            // Close the opened lists. If depth is 0, just end the current list item and the list
            // . Otherwise,
            // also close the containing lists.
            nCloses = 2; // </db:listitem>, then </db:orderedlist> or </db:itemizedlist>.
            isWithinList = false;
        }
        // There is a next paragraph, and things can get very complicated.
        else {
            int nextDepth = nextPara.get().getNumIlvl() == null ? 0 :
                    nextPara.get().getNumIlvl().intValue();

            // Should this list/list item be closed? That's gory.
            if (!p.getNumID().equals(nextPara.get().getNumID())) { // Is the next paragraph not
                // in a list (its numbering is null)
                // or in a different list (indicated by a different numbering)? Close it.
                nCloses = 2 * (depth + 1); // </db:listitem>, then </db:orderedlist> or
                // </db:itemizedlist>, for each level.
                isWithinList = false;
            } else if (nextDepth <= depth) { // Two cases:
                // - If staying at the same level, close this item, and that's it.
                // - Going less deep: must close a few things (a list item and a list per
                // difference in depth, plus a list item).
                nCloses = 2 * (depth - nextDepth) + 1;
            }
        }

        // Implement the required closes.
        for (int i = 0; i < nCloses; ++i) {
            dbStream.closeBlockTag();
        }
    }

    private void visitDefinitionListTitle(@NotNull XWPFParagraph p) throws XMLStreamException {
        // If a list is not being treated, initialise what is required.
        // currentDefinitionListTitles: store all the titles that have been found so far, so that
        // the we can check
        // for each item whether it still uses exactly the same segtitle; otherwise, report an
        // error.
        // currentDefinitionListItemNumber: indicates the position within the elements of a
        // segmented list.
        // currentDefinitionListItemSegmentNumber: current position within an item of a segmented
        // list (i.e. a segment).
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
            // Already seen: either this is normal (i.e. at the right place) or completely
            // unexpected (e.g., one title
            // has been skipped in the input document).
            if (position == 0) {
                // New item.
                currentDefinitionListItemNumber += 1;
                currentDefinitionListItemSegmentNumber = 0;
            } else if (position != currentDefinitionListItemSegmentNumber) {
                // Error!
                throw new XMLStreamException("Mismatch within a definition list: expected to have" +
                        " " +
                        "a title '" + toString(currentDefinitionListTitles.get(position).getRuns()) + "', " +
                        "but got '" + thisTitle + "' instead.");
            }
        }

        // currentDefinitionListItemSegmentNumber increased when the item is seen.
    }

    private void visitDefinitionListItem(@NotNull XWPFParagraph p) throws XMLStreamException {
        if (currentDefinitionListTitles == null) {
            throw new XMLStreamException("Unexpected definition list item; at least one title " +
                    "line must be present " +
                    "beforehand.");
        }

        // First item in the item: initialise a new list to store the segments of the item.
        if (currentDefinitionListItemSegmentNumber == 0) {
            currentDefinitionListContents.add(new ArrayList<>(currentDefinitionListTitles.size()));
        }

        // Buffer the paragraph at the right place.
        currentDefinitionListContents.get(currentDefinitionListItemNumber).add(p);

        currentDefinitionListItemSegmentNumber += 1;

        // If the next item is no more within a segmented list, serialise it all and forbid
        // adding elements to the list.
        if (isLastParagraph(p) || !hasFollowingParagraphWithStyle(p, "DefinitionListTitle")) {
            serialiseDefinitionList();

            currentDefinitionListItemNumber = -1;
            currentDefinitionListItemSegmentNumber = -1;
            currentDefinitionListTitles = null;
            currentDefinitionListContents = null;
        }
    }

    private void serialiseDefinitionList() throws XMLStreamException {
        // Once the whole definition list is built in currentDefinitionListTitles and
        // currentDefinitionListContents,
        // serialise it as DocBook.
        dbStream.openBlockTag("segmentedlist");

        for (XWPFParagraph title : currentDefinitionListTitles) {
            dbStream.openParagraphTag("segtitle");
            visitRuns(title.getRuns());
            dbStream.closeParagraphTag();
        }

        for (List<XWPFParagraph> item : currentDefinitionListContents) {
            dbStream.openBlockTag("seglistitem");
            for (XWPFParagraph value : item) {
                dbStream.openParagraphTag("seg");
                visitRuns(value.getRuns());
                dbStream.closeParagraphTag();
            }
            dbStream.closeBlockTag(); // </db:seglistitem>
        }

        dbStream.closeBlockTag(); // </db:segmentedlist>
    }

    /**
     * Variable lists.
     **/

    private void visitVariableListTitle(@NotNull XWPFParagraph p) throws XMLStreamException {
        if (!isWithinVariableList) {
            dbStream.openBlockTag("variablelist");
            isWithinVariableList = true;
        }

        if (!isWithinVariableListEntry) {
            dbStream.openBlockTag("varlistentry");
            isWithinVariableListEntry = true;
        }

        dbStream.openParagraphTag("term");
        visitRuns(p.getRuns());
        dbStream.closeParagraphTag();
    }

    private void visitVariableListItem(@NotNull XWPFParagraph p) throws XMLStreamException {
        if (!isWithinVariableList) {
            throw new XMLStreamException("Unexpected variable list item: must have a variable " +
                    "list title beforehand.");
        }

        if (!isWithinVariableListEntry) {
            throw new XMLStreamException("Inconsistent state when dealing with a variable list.");
        }

        isWithinVariableListEntry = false;
        dbStream.openBlockTag("listitem");

        // Container for paragraphs.
        visitNormalParagraph(p);

        dbStream.closeBlockTag(); // </db:listitem>
        dbStream.closeBlockTag(); // </db:varlistentry>

        // If the next item is no more within a variable list, end the fight.
        if (isLastParagraph(p) || !hasFollowingParagraphWithStyle(p, "VariableListTitle")) {
            dbStream.closeBlockTag(); // </db:variablelist>
            isWithinVariableList = false;
        }
    }

    private boolean isLastParagraph(@NotNull XWPFParagraph p) {
        int pos = doc.getPosOfParagraph(p);
        return pos == doc.getParagraphs().size() - 1;
    }

    private boolean hasParagraphWithStyle(@NotNull XWPFParagraph p, @NotNull String styleID) {
        return p.getStyleID() != null && p.getStyleID().equals(styleID);
    }

    private boolean hasPreviousParagraphWithStyle(@NotNull XWPFParagraph p,
            @NotNull String styleID) {
        int pos = doc.getPosOfParagraph(p);
        return hasParagraphWithStyle(doc.getParagraphs().get(pos - 1), styleID);
    }

    private boolean hasFollowingParagraphWithStyle(@NotNull XWPFParagraph p,
            @NotNull String styleID) {
        int pos = doc.getPosOfParagraph(p);

        if (isLastParagraph(p)) {
            throw new AssertionError("Called hasFollowingParagraphWithStyle when this is the last" +
                    " paragraph; always call isLastParagraph first");
        }

        return hasParagraphWithStyle(doc.getParagraphs().get(pos + 1), styleID);
    }

    /**
     * Tables.
     **/

    private void visitTable(@NotNull XWPFTable t) throws XMLStreamException {
        dbStream.openBlockTag("informaltable");

        // Output the table row per row, in HTML format.
        for (XWPFTableRow row : t.getRows()) {
            dbStream.openBlockTag("tr");

            for (XWPFTableCell cell : row.getTableCells()) {
                // Special case: an empty cell. One paragraph with zero runs.
                if (cell.getParagraphs().size() == 0 ||
                        (cell.getParagraphs().size() == 1 && cell.getParagraphs().get(0).getRuns().size() == 0)) {
                    dbStream.emptyBlockTag("td");
                    continue;
                }

                // Normal case.
                dbStream.openBlockTag("td");
                for (XWPFParagraph p : cell.getParagraphs()) {
                    visitParagraph(p);
                }
                dbStream.closeBlockTag(); // </db:td>
            }

            dbStream.closeBlockTag(); // </db:tr>
        }

        dbStream.closeBlockTag(); // </db:informaltable>
    }

    /**
     * Inline elements (runs, in Word parlance).
     **/

    private void visitRuns(@NotNull List<XWPFRun> runs) throws XMLStreamException {
        currentFormatting.addLast(new FormattingStack());
        XWPFRun prevRun = null;

        for (Iterator<XWPFRun> iterator = runs.iterator(); iterator.hasNext(); ) {
            XWPFRun r = iterator.next();
            if (r instanceof XWPFHyperlinkRun) {
                visitHyperlinkRun((XWPFHyperlinkRun) r, prevRun, !iterator.hasNext());
            } else if (r.getEmbeddedPictures().size() >= 1) {
                visitPictureRun(r, prevRun, !iterator.hasNext());
            } else if (r.getCTR().getFootnoteReferenceList().size() > 0) {
                visitFootNote(r, prevRun, !iterator.hasNext());
            } else {
                visitRun(r, prevRun, !iterator.hasNext());
            }
            prevRun = r;
        }

        currentFormatting.removeLast();
    }

    private void visitFootNote(@NotNull XWPFRun run, @Nullable XWPFRun prevRun,
            boolean isLastRun) throws XMLStreamException {
        BigInteger soughtId = run.getCTR().getFootnoteReferenceList().get(0).getId();
        List<XWPFFootnote> lfn =
                doc.getFootnotes().stream().filter(f -> f.getId().equals(soughtId)).toList();
        for (XWPFFootnote fn : lfn) {
            dbStream.openBlockInlineTag("footnote");
            for (IBodyElement b : fn.getBodyElements()) {
                visit(b);
            }
            dbStream.closeBlockInlineTag();
        }
    }

    private void visitRun(@NotNull XWPFRun run, @Nullable XWPFRun prevRun, boolean isLastRun) throws XMLStreamException {
        // Deal with changes of formattings between the previous run and the current one.
        Tuple<Deque<DocBookFormatting>, Deque<DocBookFormatting>> formattings =
                currentFormatting.getLast().processRun(run, prevRun);
        for (DocBookFormatting ignored : formattings.second) {
            dbStream.closeInlineTag();
        }
        for (DocBookFormatting f : formattings.first) {
            // Emphasis and its variants.
            if (f == DocBookFormatting.EMPHASIS) {
                // Replaceable special case.
                if (currentFormatting.getLast().formattings().stream().anyMatch(DocBookFormatting::isMonospacedFormatting)) {
                    dbStream.openInlineTag("replaceable");
                } else {
                    dbStream.openInlineTag("emphasis");
                }
            } else if (f == DocBookFormatting.EMPHASIS_BOLD) {
                dbStream.openInlineTag("emphasis", Map.of("role", "bold"));
            } else if (f == DocBookFormatting.EMPHASIS_STRIKETHROUGH) {
                dbStream.openInlineTag("emphasis", Map.of("role", "strikethrough"));
            } else if (f == DocBookFormatting.EMPHASIS_UNDERLINE) {
                dbStream.openInlineTag("emphasis", Map.of("role", "underline"));
            }
            // Full-blown tags.
            else {
                dbStream.openInlineTag(DocBookFormatting.formattingToDocBookTag.get(f));
            }
        }

        // Actual text for this run.
        dbStream.writeCharacters(run.text());

        // Close the tags at the end of the paragraph.
        if (isLastRun && currentFormatting.getLast().formattings().size() > 0) {
            for (DocBookFormatting ignored : currentFormatting.getLast().formattings()) {
                dbStream.closeInlineTag();
            }
        }
    }

    private void visitHyperlinkRun(@NotNull XWPFHyperlinkRun r, @Nullable XWPFRun prevRun,
            boolean isLastRun)
            throws XMLStreamException {
        // Code symmetric to link generation: the hyperlink is maybe stored in another package
        // part (main text,
        // footer, and footnotes are in three different packages).
        String url =
                r.getParent().getPart().getPackagePart().getRelationship(r.getHyperlinkId()).getTargetURI().toString();
        dbStream.openInlineTag("link", Map.of("xlink:href", url));
        visitRun(r, prevRun, isLastRun); // Text and formatting attributes are inherited for
        // XWPFHyperlinkRun.
        dbStream.closeInlineTag();
    }
}
