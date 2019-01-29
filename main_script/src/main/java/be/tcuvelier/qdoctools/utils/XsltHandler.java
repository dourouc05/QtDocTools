package be.tcuvelier.qdoctools.utils;

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
    private final Processor saxonProcessor;
    private final XsltCompiler saxonCompiler;
    private final XsltExecutable saxonExecutable;

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

    public XsltTransformer createTransformer(String file, String destination, ByteArrayOutputStream os) throws SaxonApiException {
        return createTransformer(new File(file), new File(destination), os);
    }

    public XsltTransformer createTransformer(Path file, Path destination, ByteArrayOutputStream os) throws SaxonApiException {
        return createTransformer(file.toFile(), destination.toFile(), os);
    }

    private XsltTransformer createTransformer(File file, File destination, ByteArrayOutputStream os) throws SaxonApiException {
        Serializer out = saxonProcessor.newSerializer();
        out.setOutputFile(destination);

        if (os != null) {
            saxonCompiler.setErrorListener(createLogger(os));
        }

        XdmNode source = saxonProcessor.newDocumentBuilder().build(new StreamSource(file));

        XsltTransformer trans = saxonExecutable.load();
        trans.setInitialContextNode(source);
        trans.setDestination(out);
        return trans;
    }

    public XsltTransformer createTransformer(Path destination, String initialTemplate) throws SaxonApiException {
        Serializer out = saxonProcessor.newSerializer();
        out.setOutputFile(destination.toFile());

        XsltTransformer trans = saxonExecutable.load();
        trans.setDestination(out);
        trans.setInitialTemplate(new QName(initialTemplate));
        return trans;
    }
}
