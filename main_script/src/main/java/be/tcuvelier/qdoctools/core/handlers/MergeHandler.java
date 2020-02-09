package be.tcuvelier.qdoctools.core.handlers;

import be.tcuvelier.qdoctools.core.config.Configuration;
import be.tcuvelier.qdoctools.core.config.QdtPaths;
import be.tcuvelier.qdoctools.core.exceptions.ConfigurationMissingField;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XsltTransformer;

import java.io.File;
import java.net.MalformedURLException;

public class MergeHandler {
    public static void mergeAfterProofreading(String original, String altered, String merged, Configuration config)
            throws SaxonApiException, MalformedURLException, ConfigurationMissingField {
        XsltTransformer trans = new XsltHandler(new QdtPaths(config).getXsltMergeAfterProofreading())
                .createTransformer(altered, merged, null);
        trans.setParameter(new QName("originalDocument"),
                new XdmAtomicValue(new File(original).toURI().toURL().toString()));
        trans.transform();
    }

    public static void mergeUpdateQt() {
    }

    public static void mergeUpdateQtTranslation() {
    }
}
