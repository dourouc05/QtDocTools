package be.tcuvelier.qdoctools.cli;

import be.tcuvelier.qdoctools.utils.helpers.FileHelpers;
import net.sf.saxon.s9api.*;
import net.sf.saxon.tree.NamespaceNode;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.util.Iterator;
import java.util.concurrent.Callable;

@Command(name = "merge", description = "Perform merges between files, especially after proofreading")
public class MergeCommand implements Callable<Void> {
    @Option(names = { "-l", "--left", "--original-file" },
            description = "Original file, i.e. before proofreading", required = true)
    private String original;

    @Option(names = { "-r", "--right", "--altered-file" },
            description = "Altered file, i.e. after proofreading", required = true)
    private String altered;

    @Option(names = { "-m", "--merged-file" },
            description = "Result of merging the original and the altered file (by default, the original file is " +
                    "overwritten)", required = false)
    private String merged = null;

    public enum MergeType { AFTER_PROOFREADING, UPDATE_QT, UPDATE_QT_TRANSLATION }

    @Option(names = { "-t", "--type" },
            description = "Type of merge to perform. Allowed values: ${COMPLETION-CANDIDATES}. " +
                    "Default value: ${DEFAULT-VALUE}. \n" +
                    "AFTER_PROOFREADING should be used after a proofreading step: the metadata is supposed to be " +
                    "stripped from the altered file; it will be restored from the original. \n" +
                    "UPDATE_QT works on the same document, but with two different versions of Qt: " +
                    "some methods could have been added, their documentation rewritten, their order changed; " +
                    "the original document is the old version, the altered one the new version, the result will " +
                    "highlight the differences using the \"revisionflag\" DocBook attribute (new parts will be " +
                    "indicated by \"added\", changed ones will be marked as \"changed\"). \n" +
                    "UPDATE_QT_TRANSLATION works on the same document, but the original file has an old translation " +
                    "and the altered one corresponds to a newer version of Qt, has not been translated, and has been " +
                    "processed by UPDATE_QT to mark the modifications; afterwards, the differences will be " +
                    "highlighted using the \"revisionflag\" DocBook attribute (new parts will be indicated by " +
                    "\"added\"; changed ones will have the out-of-date translation marked as \"deleted\", while the " +
                    "new version to translate will be \"changed\"). ")
    private MergeType type = MergeType.AFTER_PROOFREADING;

    @Override
    public Void call() throws SaxonApiException, SAXException {
        // Check whether the required files exist.
        if (! new File(original).exists()) {
            throw new RuntimeException("Original file " + original + " does not exist!");
        }
        if (! new File(altered).exists()) {
            throw new RuntimeException("Altered file " + altered + " does not exist!");
        }
        // No need to check whether the merged file exists: it will be overwritten.

        // Check that all files have the required file format.
        if (FileHelpers.isDocBook(original)) {
            throw new RuntimeException("File format of the original file " + original + " not recognised for input file!");
        }
        if (FileHelpers.isDocBook(altered)) {
            throw new RuntimeException("File format of the altered file " + altered + " not recognised for input file!");
        }
        if (merged != null && FileHelpers.isDocBook(merged)) {
            throw new RuntimeException("File format of the merged file " + merged + " not recognised for input file!");
        }

        // Start processing.
        if (merged == null) {
            merged = original;
        }

        switch (type) {
            case AFTER_PROOFREADING:
                mergeAfterProofreading();
                return null;
            case UPDATE_QT:
                mergeUpdateQt();
                return null;
            case UPDATE_QT_TRANSLATION:
                mergeUpdateQtTranslation();
                return null;
            default:
                System.out.println("MERGE MODE NOT RECOGNISED");
                return null;
        }
    }

    private void mergeAfterProofreading() throws SaxonApiException, SAXException {
        // Load the documents.
        Processor saxonProcessor = new Processor(false);
        DocumentBuilder leftDB = saxonProcessor.newDocumentBuilder();
        leftDB.setLineNumbering(true);
        XdmNode left = leftDB.build(new StreamSource(original));
        DocumentBuilder rightDB = saxonProcessor.newDocumentBuilder();
        rightDB.setLineNumbering(true);
        XdmNode right = rightDB.build(new StreamSource(altered));

        XdmNode leftRoot = left.children().iterator().next();
        XdmNode rightRoot = right.children().iterator().next();

        // Prepare a stream for the output.
        BuildingContentHandler out = saxonProcessor.newDocumentBuilder().newBuildingContentHandler();

        // Start the document, deal with namespaces.
        out.startDocument();
        for (XdmSequenceIterator<XdmNode> it = rightRoot.axisIterator(Axis.NAMESPACE); it.hasNext(); ) {
            XdmNode ns = it.next();
            NamespaceNode v = (NamespaceNode) ns.getUnderlyingNode();
            System.out.println(v.getPrefix());
            if (! v.getPrefix().equals("xml")) {
                out.startPrefixMapping(v.getPrefix(), v.getStringValue());
            }

            // TODO: What about the other nodes in the tree? The XML produced by Word-to-XML has namespaces all over the place...
        }

        // Run the transformation (works recursively).
//        out.startElement(right.getBaseURI().toString(), "article", "article", new AttributesImpl());
        mergeAfterProofreadingImplementation(leftRoot, rightRoot, out);
//        out.endElement(right.getBaseURI().toString(), "article", "article");

        // Finish the document.
        for (XdmSequenceIterator<XdmNode> it = rightRoot.axisIterator(Axis.NAMESPACE); it.hasNext(); ) {
            XdmNode ns = it.next();
            NamespaceNode v = (NamespaceNode) ns.getUnderlyingNode();
            if (! v.getPrefix().equals("xml")) {
                out.endPrefixMapping(v.getPrefix());
            }
        }
        out.endDocument();

        // Write.
        Serializer result = saxonProcessor.newSerializer();
        result.setOutputFile(new File(merged));
        result.serializeNode(out.getDocumentNode());
    }

    private void printTagMismatch(XdmNode left, XdmNode right) {
        System.out.println("ERROR! Tag mismatch! Left side has \"" + left.getNodeName().toString() + "\" " +
                "while the right side has \"" + right.getNodeName().toString() + "\". " +
                "Left line: " + left.getLineNumber() + "; left column: " + left.getColumnNumber() + "\n" +
                "Right line: " + right.getLineNumber() + "; Right column: " + right.getColumnNumber() + "\n" +
                "Make sure the documents pass the sanity checks (performed before any transformation toward ODT or DOCX).");
    }

    private void printNumberChildrenMismatch(XdmNode left, XdmNode right) {
        System.out.println("ERROR! Mismatch in the number of children! " +
                "Left side has \"" + iteratorSize(left.children()) + "\" " +
                "while the right side has \"" + iteratorSize(right.children()) + "\". \n" +
                "Left line: " + left.getLineNumber() + "; left column: " + left.getColumnNumber() + "\n" +
                "Right line: " + right.getLineNumber() + "; right column: " + right.getColumnNumber() + "\n" +
                "Make sure the documents pass the sanity checks (performed before any transformation toward ODT or DOCX).");
    }

    private int iteratorSize(Iterable i) {
        Iterator iterator = i.iterator();

        int n = 0;
        while(iterator.hasNext()) {
            n++;
            iterator.next();
        }
        return n;
    }

    private void mergeAfterProofreadingImplementation(XdmNode left, XdmNode right, BuildingContentHandler out)
            throws SaxonApiException, SAXException {
        if (right.getNodeKind() == XdmNodeKind.TEXT) {
            // Text is written as-is.
            String str = right.getTypedValue().toString();
            out.characters(str.toCharArray(), 0, str.length());
        }
        else if (left.getNodeName().toString().contains("article")) {
            if (! right.getNodeName().toString().contains("article")) {
                printTagMismatch(left, right);
                throw new RuntimeException();
            }

            // There should be no difference in tags here.
            if (iteratorSize(left.children()) != iteratorSize(right.children())) {
                printNumberChildrenMismatch(left, right);
                throw new RuntimeException();
            }

            // Recurse within root.
            out.characters("\n".toCharArray(), 0, 1);
            out.startElement(right.getBaseURI().toString(), right.getNodeName().getLocalName(), right.getNodeName().toString(), new AttributesImpl());

            XdmSequenceIterator<XdmNode> itR = right.axisIterator(Axis.CHILD);
            XdmSequenceIterator<XdmNode> itL = left.axisIterator(Axis.CHILD);
            while (itR.hasNext()) {
                XdmNode leftNext = itL.next();
                XdmNode rightNext = itR.next();
                mergeAfterProofreadingImplementation(leftNext, rightNext, out);
            }

            out.endElement(right.getBaseURI().toString(), right.getNodeName().getLocalName(), right.getNodeName().getEQName());
        }
        else if (left.getNodeName().toString().contains("section")) {
            if (! right.getNodeName().toString().contains("section")) {
                printTagMismatch(left, right);
                throw new RuntimeException();
            }

            // There should be no difference in tags here.
            if (iteratorSize(left.children()) != iteratorSize(right.children())) {
                printNumberChildrenMismatch(left, right);
                throw new RuntimeException();
            }

            // Recurse within sections.
            out.characters("\n".toCharArray(), 0, 1);
            out.startElement(right.getBaseURI().toString(), right.getNodeName().getLocalName(), right.getNodeName().toString(), new AttributesImpl());

            XdmSequenceIterator<XdmNode> itR = right.axisIterator(Axis.CHILD);
            XdmSequenceIterator<XdmNode> itL = left.axisIterator(Axis.CHILD);
            while (itR.hasNext()) {
                XdmNode leftNext = itL.next();
                XdmNode rightNext = itR.next();
                mergeAfterProofreadingImplementation(leftNext, rightNext, out);
            }

            out.endElement(right.getBaseURI().toString(), right.getNodeName().getLocalName(), right.getNodeName().getEQName());
        }
    }

    private void mergeUpdateQt() {
    }

    private void mergeUpdateQtTranslation() {
    }
}
