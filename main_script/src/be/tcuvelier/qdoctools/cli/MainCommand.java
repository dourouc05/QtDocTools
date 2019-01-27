package be.tcuvelier.qdoctools.cli;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@Command(description = "Version control", subcommands = {GenerateCommand.class, ProofreadCommand.class, QdocCommand.class})
public class MainCommand implements Callable<Void> {
    public final static String xsltWebXMLToDocBookPath = "../import/from_qdoc_v2/xslt/webxml_to_docbook.xslt"; // Path to the XSLT sheet WebXML to DocBook.
    public final static String xsltWebXMLToDocBookUtilPath = "../import/from_qdoc_v2/xslt/class_parser.xslt"; // Path to the XSLT sheet that contains utilities for the WebXML to DocBook transformation.
    public final static String xsltDvpMLToDocBookPath = "../import/from_dvpml/xslt/dvpml_to_docbook.xslt"; // Path to the XSLT sheet DvpML to DocBook.
    public final static String xsltDocBookToDvpMLPath = "../export/to_dvpml/xslt/docbook_to_dvpml.xslt"; // Path to the XSLT sheet DocBook to DvpML.
    public final static String xsltDocBookToFO = "../docbook_xsl/fo/profile-docbook.xsl";
    public final static String docBookRNGPath = "../import/from_qdoc_v2/schema/docbook52qdt/custom.rnc";
    public final static String dvpMLXSDPath = "../export/to_dvpml/schema/article.xsd";

    @Override
    public Void call() {
        // Nothing to do, everything is implemented in the subcommands.
        return null;
    }
}
