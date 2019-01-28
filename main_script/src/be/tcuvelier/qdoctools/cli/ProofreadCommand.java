package be.tcuvelier.qdoctools.cli;

import be.tcuvelier.qdoctools.helpers.FileHelpers;
import be.tcuvelier.qdoctools.utils.XsltHandler;
import com.xmlmind.fo.converter.Converter;
import com.xmlmind.fo.converter.OutputDestination;
import com.xmlmind.w2x.processor.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XsltTransformer;
import org.xml.sax.InputSource;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

@Command(name = "proofread", description = "Perform transformations pertaining to proofreading")
public class ProofreadCommand implements Callable<Void> {
    @Option(names = { "-i", "--input-file" },
            description = "File to process", required = true)
    private String input;

    public enum OutputFormat { DOCX, ODT };

    @Option(names = { "-of", "--output-format" },
            description = "Requested output format. Allowed values: ${COMPLETION-CANDIDATES}. " +
                    "Default value: ${DEFAULT-VALUE}")
    private OutputFormat outputFormat = OutputFormat.DOCX;

    @Override
    public Void call() throws Exception {
        if (! new File(input).exists()) {
            throw new RuntimeException("Input file does not exist!");
        }

        if (FileHelpers.isDOCX(input)) {
            String output = FileHelpers.changeExtension(input, ".qdt");
            fromDOCXToDocBook(input, output);
        } else if (FileHelpers.isODT(input)) {
            System.out.println("NOT YET IMPLEMENYED");
        } else if (FileHelpers.isDocBook(input)) {
            if (outputFormat == OutputFormat.DOCX) {
                String output = FileHelpers.changeExtension(input, ".docx");
                fromDocBookToDOCX(input, output);
            } else {
                System.out.println("NOT YET IMPLEMENYED");
            }
        } else {
            throw new RuntimeException("File format not recognised for input file!");
        }

        // TODO: Also work with ODT? Would require Pandoc for ODT > DocBook (XFC can also output ODT).
        // TODO: If XFC is not good enough, can use Pandoc for DocBook > DocX & ODT.

        return null;
    }

    private static void fromDOCXToDocBook(String input, String output) throws Exception {
        // http://www.xmlmind.com/w2x/what_is_w2x.html

        String temporary = FileHelpers.changeExtension(output, ".tmp");

        System.out.println(">>> Generating the DocBook...");
        Processor processor = new Processor();
        processor.configure(new String[]{
                "-o", "docbook5",
                "-p", "transform.hierarchy-name", "article",
                "-p", "transform.pre-element-name", "programlisting"
        });
        processor.process(new File(input), new File(temporary), null);

        // Finalise by some postprocessing (w2x does zero pretty printing, what a shame...).
        System.out.println(">>> Performing pretty printing...");
        XsltHandler h = new XsltHandler(MainCommand.xsltPrettyPrint);
        h.createTransformer(temporary, output, null).transform();

        System.out.println(">>> Done!");
    }

    private static void fromDocBookToDOCX(String input, String output) throws Exception {
        // http://www.xmlmind.com/foconverter/what_is_xfc.html -> XSL Utility

        String temporary = FileHelpers.changeExtension(output, ".fo");

//        Path xslfo = Paths.get(MainCommand.xsltDocBookToFO).toAbsolutePath();

        // First, transform the DocBook files into XSL/FO.
        System.out.println(">>> Generating the XSL/FO...");
        XsltHandler h = new XsltHandler(MainCommand.xsltDocBookToFO);
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        XsltTransformer trans = h.createTransformer(input, temporary, os);
        trans.setParameter(new QName("paper.type"), new XdmAtomicValue("A4"));
        trans.setParameter(new QName("draft.mode"), new XdmAtomicValue("no"));
        trans.setParameter(new QName("section.autolabel"), new XdmAtomicValue("0"));
        trans.setParameter(new QName("section.autolabel.max.depth"), new XdmAtomicValue(0));
        trans.setParameter(new QName("toc.section.depth"), new XdmAtomicValue("6"));
        trans.setParameter(new QName("callout.graphics"), new XdmAtomicValue("1"));
        trans.setParameter(new QName("variablelist.as.blocks"), new XdmAtomicValue("1"));
        trans.setParameter(new QName("ulink.show"), new XdmAtomicValue(false));
        trans.transform();

        // If there were errors, print them out.
        String errors = new String(os.toByteArray(), StandardCharsets.UTF_8);
        if (errors.length() > 0) {
            System.err.println(errors);
        }

        // Second, run XFC to get DOCX (only part to be changed to output ODT, for instance).
        System.out.println(">>> Generating the DOCX...");

        Converter converter = new Converter();
        converter.setProperty("outputFormat", "docx");
        converter.setProperty("outputEncoding", "UTF-8");
        converter.setProperty("prescaleImages", "false");
        converter.setProperty("genericFontFamilies", "serif=Times New Roman,sans-serif=Arial,monospace=Courier New");
        converter.setProperty("styles", MainCommand.xfcDocBookToFO);

        InputSource src = new InputSource(temporary);
        OutputDestination dst = new OutputDestination(output);
        converter.convert(src, dst);

        System.out.println(">>> Done!");
    }
}
