package be.tcuvelier.qdoctools;

import net.sf.saxon.lib.StandardErrorListener;
import net.sf.saxon.lib.StandardLogger;
import net.sf.saxon.s9api.*;

import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class XsltHandler {
    private Processor saxonProcessor;
    private XsltCompiler saxonCompiler;
    private XsltExecutable saxonExecutable;

    public XsltHandler(String sheet) throws SaxonApiException {
        saxonProcessor = new Processor(false);
        saxonCompiler = saxonProcessor.newXsltCompiler();
        saxonExecutable = saxonCompiler.compile(new StreamSource(new File(sheet)));
    }

    private StandardErrorListener createLogger(ByteArrayOutputStream os) {
        PrintStream ps = new PrintStream(os, true, StandardCharsets.UTF_8);
        StandardErrorListener sel = new StandardErrorListener();
        sel.setLogger(new StandardLogger(ps));
        return sel;
    }

    public XsltTransformer createTransformer(File file, Path destination, ByteArrayOutputStream os) throws SaxonApiException {
        XdmNode source = saxonProcessor.newDocumentBuilder().build(new StreamSource(file));
        Serializer out = saxonProcessor.newSerializer();
        out.setOutputFile(destination.toFile());
        saxonCompiler.setErrorListener(createLogger(os));

        XsltTransformer trans = saxonExecutable.load();
        trans.setInitialContextNode(source);
        trans.setDestination(out);
        return trans;
    }
}
