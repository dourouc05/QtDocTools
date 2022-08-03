package be.tcuvelier.qdoctools.core.config;

import be.tcuvelier.qdoctools.core.exceptions.ConfigurationMissingField;

public class QdtPaths {
    // TODO: move these files into the JAR, when building one? Far from trivial: https://stackoverflow.com/questions/20389255/reading-a-resource-file-from-within-jar
    private final GlobalConfiguration config;

    public QdtPaths(GlobalConfiguration config) {
        this.config = config;
    }

    public String getDocBookRNGPath() throws ConfigurationMissingField {
        return config.getQtDocToolsRoot() + "/schemas/docbook52/docbookxi.rnc";
    }

    public String getDvpMLXSDPath() throws ConfigurationMissingField {
        return config.getQtDocToolsRoot() + "/schemas/dvpml/article.xsd";
    }

    public String getXsltFromDvpMLPath() throws ConfigurationMissingField {
        return config.getQtDocToolsRoot() + "/import/from_dvpml/xslt/dvpml_to_docbook.xslt";
    }

    public String getXsltToDvpMLPath() throws ConfigurationMissingField {
        return config.getQtDocToolsRoot() + "/export/to_dvpml/xslt/docbook_to_dvpml.xslt";
    }

    public String getToDocxTemplate() throws ConfigurationMissingField {
        return config.getQtDocToolsRoot() + "/export/to_docx/template/template.docx";
    }

    public String getToDocxTests() throws ConfigurationMissingField {
        return config.getQtDocToolsRoot() + "/export/to_docx/tests/";
    }

    public String getFromDocxTests() throws ConfigurationMissingField {
        return config.getQtDocToolsRoot() + "/import/from_docx/tests/";
    }

    public String getXsltMergeAfterProofreading() throws ConfigurationMissingField {
        return config.getQtDocToolsRoot() + "/proofread/merge_after_proofread/xslt" +
                "/after_proofreading.xsl";
    }
}
