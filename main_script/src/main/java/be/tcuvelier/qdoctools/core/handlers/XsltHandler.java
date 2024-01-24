package be.tcuvelier.qdoctools.core.handlers;

import net.sf.saxon.lib.StandardErrorReporter;
import net.sf.saxon.lib.StandardLogger;
import net.sf.saxon.s9api.*;

import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class XsltHandler {
    private final Processor saxonProcessor;
    private final XsltCompiler saxonCompiler;
    private final XsltExecutable saxonExecutable;

    public XsltHandler(String sheet) throws SaxonApiException {
        // TODO: when deploying as JAR, do something like https://stackoverflow.com/questions/20389255/reading-a-resource-file-from-within-jar
        //  to get a Reader object for the style sheet.
        saxonProcessor = new Processor(false);
        saxonCompiler = saxonProcessor.newXsltCompiler();
        saxonExecutable = saxonCompiler.compile(new StreamSource(new File(sheet)));
    }

    private StandardErrorReporter createLogger(ByteArrayOutputStream os) {
        PrintStream ps = new PrintStream(os, true, StandardCharsets.UTF_8);
        StandardErrorReporter ser = new StandardErrorReporter();
        ser.setLogger(new StandardLogger(ps));
        return ser;
    }

    public void transform(File input, File output, Map<String, Object> parameters) throws SaxonApiException {
        // Using an Object in the parameters allow passing more information to Saxon without many risks: if it was
        // forced to be a String, no Integer could be passed to Saxon; parsing the String to find out if it is an
        // Integer could create false positives (e.g.: the stylesheets expects a String, but a special case is an
        // Integer).

        // Prepare the transformation.
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        saxonCompiler.setErrorReporter(createLogger(os));

        XsltTransformer transformer = saxonExecutable.load();
        transformer.setInitialContextNode(saxonProcessor.newDocumentBuilder().build(new StreamSource(input)));

        Serializer out = saxonProcessor.newSerializer();
        out.setOutputFile(output);
        transformer.setDestination(out);

        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            if (entry.getValue() instanceof Integer) {
                transformer.setParameter(new QName(entry.getKey()), new XdmAtomicValue((Integer) entry.getValue()));
            } else if (entry.getValue() instanceof String) {
                transformer.setParameter(new QName(entry.getKey()), new XdmAtomicValue((String) entry.getValue()));
            } else if (entry.getValue() instanceof Boolean) {
                transformer.setParameter(new QName(entry.getKey()), new XdmAtomicValue((Boolean) entry.getValue()));
            } else {
                throw new IllegalArgumentException("Only objects of type Integer, Boolean, or String are allowed " +
                        "as parameters");
            }
        }

        // Run the transformation.
        transformer.transform();

        // If there were errors, print them out.
        String errors = os.toString(StandardCharsets.UTF_8);
        if (!errors.isEmpty()) {
            System.err.println(errors);
        }
    }
}
