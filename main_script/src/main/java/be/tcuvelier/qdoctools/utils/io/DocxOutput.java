package be.tcuvelier.qdoctools.utils.io;

import be.tcuvelier.qdoctools.cli.MainCommand;
import org.apache.poi.xwpf.usermodel.*;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.*;

public class DocxOutput {
    public static void main(String[] args) throws Exception {
        String test = "basic";
//        String test = "sections";
//        String test = "images";

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
        SAXHandler handler = new SAXHandler();
        saxParser.parse(new File(input), handler);
        return handler.doc;
    }

    private enum Level {
        ROOT, ROOT_INFO,
        SECTION, SECTION_INFO,
        TABLE
    }

    private enum Formatting {
        EMPHASIS, EMPHASIS_BOLD, EMPHASIS_UNDERLINE, EMPHASIS_STRIKETHROUGH;

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
                return Formatting.EMPHASIS_BOLD;
            } else if (SAXHelpers.isEmphasisTag(localName) && role.equals("underline")) {
                return Formatting.EMPHASIS_UNDERLINE;
            } else if (SAXHelpers.isEmphasisTag(localName) && role.equals("strikethrough")) {
                return Formatting.EMPHASIS_STRIKETHROUGH;
            } else {
                return Formatting.EMPHASIS;
            }
        }
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
    }

    private static class SAXHandler extends DefaultHandler {
        private Locator locator;

        private XWPFDocument doc;
        private XWPFParagraph paragraph;
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

        SAXHandler() throws IOException {
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
            } else if (SAXHelpers.isInfoTag(qName)) {
                currentLevel.push((currentLevel.peek() == Level.ROOT) ? Level.ROOT_INFO : Level.SECTION_INFO);
                ensureNoTextAllowed();
            } else if (SAXHelpers.isSectionTag(qName)) {
                currentLevel.push(Level.SECTION);
                currentSectionDepth += 1;
                ensureNoTextAllowed();
            } else if (SAXHelpers.isTitleTag(qName)) {
                paragraph = doc.createParagraph();
                if (currentSectionDepth == 0) {
                    paragraph.setStyle("Title");
                } else {
                    paragraph.setStyle("Heading" + currentSectionDepth);
                }
                run = paragraph.createRun();
            } else if (SAXHelpers.isParagraphTag(qName)) {
                // For tables, the paragraph is automatically created within a table cell.
                if (currentLevel.peek() == Level.TABLE) {
                    return;
                }

                if (paragraph != null || run != null) {
                    throw new SAXException(getLocationString() + "tried to create a new paragraph, but one was " +
                            "already being filled.");
                }

                paragraph = doc.createParagraph();
                run = paragraph.createRun();
            } else if (SAXHelpers.isEmphasisTag(qName)) {
                currentFormatting.add(Formatting.tagToFormatting(qName, attributes));

                // Create a new run if this one is already started.
                if (run.text().length() > 0) {
                    run = paragraph.createRun();
                }

                paragraph = doc.createParagraph();
                run = paragraph.createRun();
                setRunFormatting();
            } else if (SAXHelpers.isTableTag(qName)) {
                currentLevel.push(Level.TABLE);

                table = doc.createTable();
                tableRowNumber = 0;
                tableColumnNumber = 0;
            } else if (SAXHelpers.isTableBodyTag(qName)) {
                // Nothing to do, just go inside the table.
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
            } else if (SAXHelpers.isTableHeaderTag(qName) || SAXHelpers.isTableFooterTag(qName)) {
                throw new SAXException(getLocationString() + "table headers/footers are not handled.");
            } else if (SAXHelpers.isCALSTag(qName)) {
                throw new SAXException(getLocationString() + "CALS tables are not handled.");
            }

            // There might be return instructions in the long switch.
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
                ensureNoTextAllowed();
            } else if (SAXHelpers.isEmphasisTag(qName)) {
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
                ensureNoTextAllowed();
            } else if (SAXHelpers.isTableBodyTag(qName)) {
                // Nothing to do, just go outside the table.
                ensureNoTextAllowed();
            } else if (SAXHelpers.isTableRowTag(qName)) {
                tableRowNumber += 1;
                ensureNoTextAllowed();
            } else if (SAXHelpers.isTableColumnTag(qName)) {
                tableColumnNumber += 1;
                ensureNoTextAllowed();
            } else if (SAXHelpers.isTableHeaderTag(qName) || SAXHelpers.isTableFooterTag(qName)) {
                throw new SAXException(getLocationString() + "table headers/footers are not handled.");
            } else if (SAXHelpers.isCALSTag(qName)) {
                throw new SAXException(getLocationString() + "CALS tables are not handled.");
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
