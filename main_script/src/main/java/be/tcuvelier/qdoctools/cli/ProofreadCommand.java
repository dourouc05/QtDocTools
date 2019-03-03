package be.tcuvelier.qdoctools.cli;

/*
 * TODO: How to encode/retrieve block <screen> vs. <programlisting>? screen/prompt?
 * TODO: How to encode/retrieve inline <filename>? filename/replaceable?
 */

import be.tcuvelier.qdoctools.utils.handlers.SanityCheckHandler;
import be.tcuvelier.qdoctools.utils.handlers.XsltHandler;
import be.tcuvelier.qdoctools.utils.helpers.FileHelpers;
import com.xmlmind.fo.converter.Converter;
import com.xmlmind.fo.converter.OutputDestination;
import com.xmlmind.util.Console;
import com.xmlmind.util.ProgressMonitor;
import com.xmlmind.w2x.processor.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XsltTransformer;
import org.xml.sax.InputSource;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

@Command(name = "proofread", description = "Perform transformations pertaining to proofreading")
public class ProofreadCommand implements Callable<Void> {
    @Option(names = { "-i", "--input-file" },
            description = "File to process", required = true)
    private String input;

    public enum OutputFormat { DOCX, ODT }

    @Option(names = { "-of", "--output-format" },
            description = "Requested output format. Allowed values: ${COMPLETION-CANDIDATES}. " +
                    "Default value: ${DEFAULT-VALUE}")
    private OutputFormat outputFormat = OutputFormat.DOCX;

    @Option(names = { "--disable-sanity-checks" },
            description = "Perform the sanity checks, but continue with generation even in case of failure")
    private boolean disableSanityChecks = false;

    @Override
    public Void call() throws Exception {
        if (! new File(input).exists()) {
            throw new RuntimeException("Input file " + input + " does not exist!");
        }

        if (FileHelpers.isDOCX(input)) {
            String output = FileHelpers.changeExtension(input, ".qdt");
            fromDOCXToDocBook(input, output);
        } else if (FileHelpers.isODT(input)) {
            System.out.println("NOT YET IMPLEMENYED");
        } else if (FileHelpers.isDocBook(input)) {
            if (! checkSanity(input)) {
                System.out.println("SANITY CHECK: one or more sanity checks did not pass. It is better if you correct" +
                        "these problems now; otherwise, you may use the option --disable-sanity-checks to ignore them.");
                throw new RuntimeException("Input DocBook file does not pass the sanity checks! ");
            }

            if (outputFormat == OutputFormat.DOCX) {
                String output = FileHelpers.changeExtension(input, ".docx");
                fromDocBookToDOCX(input, output);
            } else if (outputFormat == OutputFormat.ODT) {
                System.out.println("ODT NOT YET IMPLEMENTED");
            }
        } else {
            throw new RuntimeException("File format not recognised for input file!");
        }

        // TODO: Also work with ODT? Would require Pandoc for ODT > DocBook (XFC can also output ODT).
        // TODO: If XFC is not good enough, can use Pandoc for DocBook > DocX & ODT.

        return null;
    }

    private boolean checkSanity(String input) throws SaxonApiException, FileNotFoundException {
        if (! disableSanityChecks) {
            return new SanityCheckHandler(input).performSanityCheck();
        } else {
            return true;
        }
    }

    private static void fromDOCXToDocBook(String input, String output) throws Exception {
        // http://www.xmlmind.com/w2x/what_is_w2x.html
        String temporary = FileHelpers.changeExtension(output, ".tmp");

        // "C:\Program Files (x86)\XMLmind_Word_To_XML\bin\w2x" -vvv "D:\Dvp\QtDoc\QtDocTools\proofread\proofread_fromdocx\tests\CPLEX.docx" "D:\Dvp\QtDoc\QtDocTools\proofread\proofread_fromdocx\tests\CPLEX.xhtml"
        // "C:\Program Files (x86)\XMLmind_Word_To_XML\bin\w2x" -vvv -p edit.prune.preserve "p-XFC_P_ProgramListing" -p edit.blocks.convert "p-XFC_P_ProgramListing pre" "D:\Dvp\QtDoc\QtDocTools\proofread\proofread_fromdocx\tests\CPLEX.docx" "D:\Dvp\QtDoc\QtDocTools\proofread\proofread_fromdocx\tests\CPLEX.xhtml"
        // "C:\Program Files (x86)\XMLmind_Word_To_XML\bin\w2x" -vvv -o docbook5 -p edit.prune.preserve "p-XFC_P_ProgramListing" -p edit.blocks.convert "p-XFC_P_ProgramListing pre" "D:\Dvp\QtDoc\QtDocTools\proofread\proofread_fromdocx\tests\CPLEX.docx" "D:\Dvp\QtDoc\QtDocTools\proofread\proofread_fromdocx\tests\CPLEX.xml"
        // "C:\Program Files (x86)\XMLmind_Word_To_XML\bin\w2x" -vvv -o docbook5 -p edit.prune.preserve "^p-.*ProgramListing.*$" -p edit.blocks.convert "^p-.*ProgramListing.*$ pre" "D:\Dvp\QtDoc\QtDocTools\proofread\proofread_fromdocx\tests\CPLEX.docx" "D:\Dvp\QtDoc\QtDocTools\proofread\proofread_fromdocx\tests\CPLEX.xml"
        // "C:\Program Files (x86)\XMLmind_Word_To_XML\bin\w2x" -vvv -o docbook5 -t file:///D:/Dvp/QtDoc/QtDocTools/proofread/proofread_fromdocx/xslt/custom_docbook5.xsl -pu edit.after.blocks file:///D:/Dvp/QtDoc/QtDocTools/proofread/proofread_fromdocx/xslt/custom_docbook5.xed -p edit.prune.preserve "^p-.*ProgramListing.*$" -p edit.blocks.convert "^p-.*ProgramListing.*$ pre" "D:\Dvp\QtDoc\QtDocTools\proofread\proofread_fromdocx\tests\CPLEX.docx" "D:\Dvp\QtDoc\QtDocTools\proofread\proofread_fromdocx\tests\CPLEX.xml"
        // "C:\Program Files (x86)\XMLmind_Word_To_XML\bin\w2x" -vvv -o docbook5 -t file:///D:/Dvp/QtDoc/QtDocTools/proofread/proofread_fromdocx/xslt/custom_docbook5.xsl -pu edit.before.init-styles file:///D:/Dvp/QtDoc/QtDocTools/proofread/proofread_fromdocx/xslt/custom_docbook5.xed -p edit.prune.preserve "^p-.*ProgramListing.*$" -p edit.blocks.convert "^p-.*ProgramListing.*$ pre" "D:\Dvp\QtDoc\QtDocTools\proofread\proofread_fromdocx\tests\CPLEX.docx" CPLEX.xml

        System.out.println(">>> Generating the DocBook...");
        Processor processor = new Processor();
        processor.configure(new String[]{
                "-o", "docbook5",
                "-t", "file:///" + Paths.get(MainCommand.xsltXEDTransform).toRealPath().toString().replace('\\', '/'),
                "-pu", "edit.before.init-styles", "file:///" + Paths.get(MainCommand.xsltXEDScript).toRealPath().toString().replace('\\', '/'),
                "-p", "transform.hierarchy-name", "article",
                "-p", "transform.pre-element-name", "programlisting",
                "-p", "transform.media-alt", "yes",
                "-p", "edit.prune.preserve", "^p-.*programlisting.*$",
                "-p", "edit.blocks.convert", "^p-.*programlisting.*$ span g:id='pre' g:container='pre'",
                "-p", "edit.inlines.convert", "c-Code code ! c-Abbrev abbr",
        });
        ProgressMonitor pm = new ProgressMonitor() {
            @Override
            public void start() {
                System.out.println(">>> Start");
            }

            @Override
            public boolean message(String s, Console.MessageType messageType) {
                System.out.println(">>> Message: ");
                System.out.println("||| " + s);
                return false;
            }

            @Override
            public boolean stepCount(int i) {
                System.out.println(">>> Step count: " + i);
                return false;
            }

            @Override
            public boolean step(int i) {
                System.out.println(">>> Step: #" + i);
                return false;
            }

            @Override
            public void stop() {
                System.out.println(">>> Stop");
            }
        };
        processor.process(new File(input), new File(temporary), null);

        // Finalise by some postprocessing (w2x does zero pretty printing...).
        System.out.println(">>> Performing post-processing...");
        new XsltHandler(MainCommand.xsltXEDPostProcess)
                .createTransformer(temporary, output, null)
                .transform();

        System.out.println(">>> Done!");
    }

    private static void fromDocBookToDOCX(String input, String output) throws Exception {
        // http://www.xmlmind.com/foconverter/what_is_xfc.html -> XSL Utility

        String temporary = FileHelpers.changeExtension(output, ".fo");

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
