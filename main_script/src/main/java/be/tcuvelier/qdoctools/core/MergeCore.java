package be.tcuvelier.qdoctools.core;

import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.exceptions.ConfigurationMissingField;
import be.tcuvelier.qdoctools.core.handlers.MergeHandler;
import be.tcuvelier.qdoctools.core.helpers.FileHelpers;
import net.sf.saxon.s9api.SaxonApiException;

import java.io.File;
import java.net.MalformedURLException;

public class MergeCore {
    public enum MergeType { AFTER_PROOFREADING, UPDATE_QT, UPDATE_QT_TRANSLATION }

    public static void call(String original, String altered, String merged, MergeType type, GlobalConfiguration config) throws SaxonApiException, MalformedURLException, ConfigurationMissingField {
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
                MergeHandler.mergeAfterProofreading(original, altered, merged, config);
                break;
            case UPDATE_QT:
                MergeHandler.mergeUpdateQt();
                break;
            case UPDATE_QT_TRANSLATION:
                MergeHandler.mergeUpdateQtTranslation();
                break;
        }
    }
}
