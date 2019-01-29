package be.tcuvelier.qdoctools.utils;

import org.docx4j.convert.out.AbstractConversionSettings;
import org.docx4j.convert.out.common.*;
import org.docx4j.convert.out.common.writer.*;
import org.docx4j.convert.out.html.SymbolWriter;
import org.docx4j.events.EventFinished;
import org.docx4j.events.ProcessStep;
import org.docx4j.events.StartEvent;
import org.docx4j.fonts.RunFontSelector;
import org.docx4j.model.properties.Property;
import org.docx4j.openpackaging.exceptions.Docx4JException;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.wml.Br;
import org.docx4j.wml.CTBookmark;
import org.docx4j.wml.PPr;
import org.docx4j.wml.RPr;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.List;

public class DocxToDocBook {
    private static class TableWriter extends AbstractTableWriter {
        @Override
        protected Element createNode(Document doc, int nodeType) {
            switch (nodeType) {
                case NODE_TABLE:
                    return doc.createElement("db:table");
                case NODE_TABLE_COLUMN_GROUP:
                    return doc.createElement("db:colgroup");
                case NODE_TABLE_COLUMN:
                    return doc.createElement("db:col");
                case NODE_TABLE_HEADER:
                    return doc.createElement("db:thead");
                case NODE_TABLE_HEADER_ROW:
                    return doc.createElement("db:tr");
                case NODE_TABLE_HEADER_CELL:
                    return doc.createElement("db:th");
                case NODE_TABLE_BODY:
                    return doc.createElement("db:tbody");
                case NODE_TABLE_BODY_ROW:
                    return doc.createElement("db:tr");
                case NODE_TABLE_BODY_CELL:
                    return doc.createElement("db:td");
                default:
                    return null;
            }
        }

        @Override
        protected void applyAttributes(AbstractWmlConversionContext abstractWmlConversionContext, List<Property> list, Element element) {
            // Nothing to do.
        }
    }

    private static class BrWriter extends AbstractBrWriter {
        @Override
        public Node toNode(AbstractWmlConversionContext abstractWmlConversionContext, Object o, Node node, TransformState transformState, Document document) {
            // Nothing to do.
            // TODO: Maybe still for code output?
            return null;
        }
    }

    private static class FldSimpleWriter extends AbstractFldSimpleWriter {
        protected FldSimpleWriter() {
            super("db", "phrase"); // More of a placeholder.
        }

        @Override
        protected void applyProperties(List<Property> list, Node node) {
            // TODO: Probably something to do here in the future (like inserting references, gentext).
            // http://officeopenxml.com/WPfields.php
            // https://github.com/plutext/docx4j/blob/e53d6084d171eb919d3c6e6830e2ecea2bbeac1c/src/main/java/org/docx4j/convert/out/html/FldSimpleWriter.java
        }
    }

    private static class BookmarkStartWriter extends AbstractBookmarkStartWriter {
        @Override
        public Node toNode(AbstractWmlConversionContext context, Object o, Node node,
                           TransformState transformState, Document document) {
            // Don't generate a node, but rather a xml:id.
            CTBookmark modelData = (CTBookmark) o;
            ((Context) context).setBookmarkStart(modelData);
            return null;
        }
    }

    private static class HyperlinkWriter extends AbstractHyperlinkWriter {
        @Override
        protected Node toNode(AbstractWmlConversionContext context, AbstractHyperlinkWriterModel model, Document doc) {
            Element ret = doc.createElement("db:link");
            String internalTarget = model.getInternalTarget();
            String externalTarget = model.getExternalTarget();
            String location;
            if (model.isExternal()) {
                location = externalTarget;
                if (internalTarget != null && internalTarget.length() > 0) {
                    location += "#" + internalTarget;
                }

                ret.setAttribute("xlink:href", location);
            } else {
                ret.setAttribute("xlink:href", "#" + internalTarget);
            }

            if (model.getTooltip() != null && model.getTooltip().length() > 0) {
                ret.setAttribute("alt", model.getTooltip());
            }

            return ret;
        }

    }

    private static final AbstractWriterRegistry DOCBOOK_WRITER_REGISTRY =
            new AbstractWriterRegistry() {
                @Override
                protected void registerDefaultWriterInstances() {
                    registerWriter(new TableWriter());
                    registerWriter(new SymbolWriter()); // Same as HTML
                    registerWriter(new BrWriter());
                    registerWriter(new FldSimpleWriter());
                    registerWriter(new BookmarkStartWriter());
                    registerWriter(new HyperlinkWriter());
                }
            };

    private static final AbstractMessageWriter DOCBOOK_MESSAGE_WRITER =
            new AbstractMessageWriter() {
                @Override
                protected String getOutputPrefix() {
                    return "<!-- ERROR: ";
                }

                @Override
                protected String getOutputSuffix() {
                    return "-->";
                }
            };

    private static RunFontSelector createRunFontSelector(WordprocessingMLPackage wmlPackage) { // For now, a copy of HTML.
        return new RunFontSelector(wmlPackage,
                new RunFontSelector.RunFontCharacterVisitor() {
                    DocumentFragment df;
                    StringBuilder sb = new StringBuilder(1024);
                    Element span;

                    String lastFont;
                    String fallbackFontName;

                    private Document document;
                    @Override
                    public void setDocument(Document document) {
                        this.document = document;
                        df = document.createDocumentFragment();
                    }

                    private boolean spanReusable = true;
                    @Override
                    public boolean isReusable() {
                        return spanReusable;
                    }

                    @Override
                    public void addCharacterToCurrent(char c) {
                        sb.append(c);
                    }

                    @Override
                    public void addCodePointToCurrent(int cp) {
                        sb.append(new String(Character.toChars(cp)));
                    }

                    @Override
                    public void finishPrevious() {
                        if (sb.length() > 0) {
                            if (span == null) { // init
                                span = runFontSelector.createElement(document);
                                // so that spaces have correct font set
                                if (lastFont!=null) {
                                    runFontSelector.setAttribute(span, lastFont);
                                }
                            }
                            df.appendChild(span);
                            span.setTextContent(sb.toString());
                            sb.setLength(0);
                        }
                    }

                    @Override
                    public void createNew() {
                        span = runFontSelector.createElement(document);
                    }

                    @Override
                    public void setMustCreateNewFlag(boolean val) {
                        spanReusable = !val;
                    }

                    @Override
                    public void fontAction(String fontname) {
                        if (fontname == null) {
                            runFontSelector.setAttribute(span, fallbackFontName);
                        } else {
                            runFontSelector.setAttribute(span, fontname);
                            lastFont = fontname;
                        }
                    }

                    @Override
                    public Object getResult() {
                        span = null; // ready for next time
                        return df;
                    }

                    private RunFontSelector runFontSelector;
                    @Override
                    public void setRunFontSelector(RunFontSelector runFontSelector) {
                        this.runFontSelector = runFontSelector;
                    }

                    @Override
                    public void setFallbackFont(String fontname) {
                        fallbackFontName = fontname;

                    }
                },
                RunFontSelector.RunFontActionType.XHTML);
    }

    private static class Context extends AbstractWmlConversionContext {
        private Settings settings;
        private CTBookmark bookmarkStart;

        Context(Settings settings, WordprocessingMLPackage preprocessedPackage, ConversionSectionWrappers conversionSectionWrappers) {
            super(DOCBOOK_WRITER_REGISTRY, DOCBOOK_MESSAGE_WRITER, new AbstractConversionSettings() {},
                    preprocessedPackage, conversionSectionWrappers, createRunFontSelector(preprocessedPackage));
            this.settings = settings;
        }

        public CTBookmark getBookmarkStart() {
            return bookmarkStart;
        }

        public void setBookmarkStart(CTBookmark bookmarkStart) {
            this.bookmarkStart = bookmarkStart;
        }
    }

    private static class Settings extends AbstractConversionSettings {}

    private static class DocBookProcessStep implements ProcessStep {
        @Override
        public String name() {
            return "DOCBOOK_EXPORT";
        }
    }

    private static class ExporterVisitorDelegate extends AbstractVisitorExporterDelegate<Settings, Context> {
        protected ExporterVisitorDelegate() {
            super(new AbstractVisitorExporterGeneratorFactory<Context>() {
                public AbstractVisitorExporterGenerator<Context> createInstance(Context conversionContext, Document document, Node parentNode) {
                    return new ExporterVisitorGenerator(conversionContext, document, parentNode);
                }
            });
        }

        @Override
        protected Element createDocumentRoot(Context context, Document document) {
            return document.createElement("db:article");
        }
    }

    private static class ExporterVisitor extends AbstractWmlExporter<Settings, Context> {
        protected static final ExporterVisitorDelegate EXPORTER_DELEGATE_INSTANCE = new ExporterVisitorDelegate();
        protected static ExporterVisitor instance = null;

        protected ExporterVisitor() {
            super(EXPORTER_DELEGATE_INSTANCE);
        }

        @Override
        protected Context createContext(Settings settings, WordprocessingMLPackage preprocessedPackage, ConversionSectionWrappers sectionWrappers) {
            return new Context(settings, preprocessedPackage, sectionWrappers);
        }

        public static Exporter<Settings> getInstance() {
            if (instance == null) {
                synchronized(org.docx4j.convert.out.html.HTMLExporterVisitor.class) {
                    if (instance == null) {
                        instance = new ExporterVisitor();
                    }
                }
            }

            return instance;
        }
    }

    private static class ExporterVisitorGenerator extends AbstractVisitorExporterGenerator<Context> {
        private ExporterVisitorGenerator(Context conversionContext, Document document, Node parentNode) {
            super(conversionContext, document, parentNode);
        }

        @Override
        protected void handleBr(Br br) {

        }

        @Override
        protected Element handlePPr(Context context, PPr pPr, boolean b, Element element) {
            return null;
        }

        @Override
        protected void handleRPr(Context context, PPr pPr, RPr rPr, Element element) {

        }

        @Override
        protected AbstractVisitorExporterDelegate.AbstractVisitorExporterGeneratorFactory<Context> getFactory() {
            return null;
        }

        @Override
        protected DocumentFragment createImage(int i, Context context, Object o) {
            return null;
        }

        @Override
        protected Element createNode(Document document, int i) {
            return null;
        }
    }

    public static void convertDOCXToDocBook(String in, String out) throws Docx4JException, FileNotFoundException {
        // Avoid a warning from JAXB (should be fixed by JAXB 2.4, yet-to-be-released).
        System.getProperties().setProperty("com.sun.xml.bind.v2.bytecode.ClassTailor.noOptimize", "true");

        WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(new File(in));
        Settings dbSettings = new Settings();
        dbSettings.setWmlPackage(wordMLPackage);

        StartEvent startEvent = new StartEvent(wordMLPackage, new DocBookProcessStep());
        startEvent.publish();

        Exporter<Settings> exporter = ExporterVisitor.getInstance();
        OutputStream os = new FileOutputStream(new File(out));
        exporter.export(dbSettings, os);
        (new EventFinished(startEvent)).publish();
    }
}
