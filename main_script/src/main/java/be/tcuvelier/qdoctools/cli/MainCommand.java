package be.tcuvelier.qdoctools.cli;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@SuppressWarnings("WeakerAccess")
@Command(description = "QDocTools", subcommands = {
        GenerateCommand.class,
        ProofreadCommand.class,
        QdocCommand.class,
        MergeCommand.class
})
public class MainCommand implements Callable<Void> {
    // Schemas.
    public final static String docBookRNGPath = "../import/from_qdoc_v2/schema/docbook52qdt/custom.rnc";
    public final static String dvpMLXSDPath = "../export/to_dvpml/schema/article.xsd";

    // From qdoc to DocBook.
    public final static String xsltWebXMLToDocBookPath = "../import/from_qdoc_v2/xslt/webxml_to_docbook.xslt"; // Path to the XSLT sheet WebXML to DocBook.
    public final static String xsltWebXMLToDocBookUtilPath = "../import/from_qdoc_v2/xslt/class_parser.xslt"; // Path to the XSLT sheet that contains utilities for the WebXML to DocBook transformation.

    // Between DocBook and DvpML.
    public final static String xsltDvpMLToDocBookPath = "../import/from_dvpml/xslt/dvpml_to_docbook.xslt"; // Path to the XSLT sheet DvpML to DocBook.
    public final static String xsltDocBookToDvpMLPath = "../export/to_dvpml/xslt/docbook_to_dvpml.xslt"; // Path to the XSLT sheet DocBook to DvpML.

    // Between DocBook and DOCX.
    public final static String toDocxTemplate = "../proofread/proofread_todocx/template/template.docx";
    public final static String toDocxTemplateNumberingBullet = "../proofread/proofread_todocx/template/numbering_bullet.xml";
    public final static String fromDocxTests = "../proofread/proofread_fromdocx/tests/";
    public final static String toDocxTests = "../proofread/proofread_todocx/tests/";

    // Merge operations.
    public final static String xsltMergeAfterProofreading = "../proofread/merge_after_proofread/xslt/after_proofreading.xsl";


    @Override
    public Void call() {
        // Nothing to do, everything is implemented in the subcommands.
        return null;
    }
}
