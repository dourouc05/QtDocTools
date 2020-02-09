package be.tcuvelier.qdoctools.core.config;

import be.tcuvelier.qdoctools.core.exceptions.BadConfigurationFile;

public class QdtPaths {
    // TODO: move these files into the JAR, when building one? Far from trivial: https://stackoverflow.com/questions/20389255/reading-a-resource-file-from-within-jar
    private final Configuration config;

    public QdtPaths(Configuration config) {
        this.config = config;
    }

    public String getDocBookRNGPath() throws BadConfigurationFile {
        return config.getQtDocToolsRoot() + "/import/from_qdoc_v2/schema/docbook52qdt/custom.rnc";
    }

    public String getDvpMLXSDPath() throws BadConfigurationFile {
        return config.getQtDocToolsRoot() + "/export/to_dvpml/schema/article.xsd";
    }

    public String getXsltFromDvpMLPath() throws BadConfigurationFile {
        return config.getQtDocToolsRoot() + "/import/from_dvpml/xslt/dvpml_to_docbook.xslt";
    }

    public String getXsltToDvpMLPath() throws BadConfigurationFile {
        return config.getQtDocToolsRoot() + "/export/to_dvpml/xslt/docbook_to_dvpml.xslt";
    }

    public String getToDocxTemplate() throws BadConfigurationFile {
        return config.getQtDocToolsRoot() + "/export/to_docx/template/template.docx";
    }

    public String getToDocxTests() throws BadConfigurationFile {
        return config.getQtDocToolsRoot() + "/export/to_docx/tests/";
    }

    public String getFromDocxTests() throws BadConfigurationFile {
        return config.getQtDocToolsRoot() + "/import/from_docx/tests/";
    }

    public String getXsltMergeAfterProofreading() throws BadConfigurationFile {
        return config.getQtDocToolsRoot() + "/proofread/merge_after_proofread/xslt/after_proofreading.xsl";
    }
}
