package be.tcuvelier.qdoctools.cli;

import be.tcuvelier.qdoctools.core.handlers.XsltHandler;
import be.tcuvelier.qdoctools.core.helpers.FileHelpers;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XsltTransformer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.net.MalformedURLException;
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
    public Void call() throws SaxonApiException, MalformedURLException {
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

    private void mergeAfterProofreading() throws SaxonApiException, MalformedURLException {
        XsltTransformer trans = new XsltHandler(MainCommand.xsltMergeAfterProofreading)
                .createTransformer(altered, merged, null);
        trans.setParameter(new QName("originalDocument"),
                new XdmAtomicValue(new File(original).toURI().toURL().toString()));
        trans.transform();
    }

    private void mergeUpdateQt() {
    }

    private void mergeUpdateQtTranslation() {
    }
}
