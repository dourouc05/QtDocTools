package be.tcuvelier.qdoctools.cli;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(description = "Version control", subcommands = {
        GenerateCommand.class,
        ProofreadCommand.class,
        QdocCommand.class,
        MergeCommand.class
})
public class MainCommand implements Callable<Void> {
    public final static String xsltWebXMLToDocBookPath = "../import/from_qdoc_v2/xslt/webxml_to_docbook.xslt"; // Path to the XSLT sheet WebXML to DocBook.
    public final static String xsltWebXMLToDocBookUtilPath = "../import/from_qdoc_v2/xslt/class_parser.xslt"; // Path to the XSLT sheet that contains utilities for the WebXML to DocBook transformation.
    public final static String xsltDvpMLToDocBookPath = "../import/from_dvpml/xslt/dvpml_to_docbook.xslt"; // Path to the XSLT sheet DvpML to DocBook.
    public final static String xsltDocBookToDvpMLPath = "../export/to_dvpml/xslt/docbook_to_dvpml.xslt"; // Path to the XSLT sheet DocBook to DvpML.
    public final static String xsltXEDTransform = "../proofread/proofread_fromdocx/xslt/custom_docbook5.xsl";
    public final static String xsltXEDScript = "../proofread/proofread_fromdocx/xslt/custom_docbook5.xed";
    public final static String xsltXEDPrettyPrint = "../proofread/proofread_fromdocx/xslt/pretty_print.xsl";
    public final static String xsltDocBookToFO = "../proofread/proofread_todocx/xslt/main.xsl";
    public final static String xfcDocBookToFO = "../proofread/proofread_todocx/xslt/styles.xfc"; // http://www.xmlmind.com/foconverter/samples/styles.xfc
    public final static String docBookRNGPath = "../import/from_qdoc_v2/schema/docbook52qdt/custom.rnc";
    public final static String dvpMLXSDPath = "../export/to_dvpml/schema/article.xsd";

    @Override
    public Void call() {
        // Nothing to do, everything is implemented in the subcommands.
        return null;
    }
}
