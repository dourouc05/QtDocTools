package be.tcuvelier.qdoctools.cli;

import be.tcuvelier.qdoctools.helpers.FileHelpers;
import picocli.CommandLine.Option;
import picocli.CommandLine.Command;
import com.xmlmind.w2x.processor.Processor;

import java.io.File;
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
        if (FileHelpers.isDOCX(input)) {
            String output = FileHelpers.changeExtension(input, ".qdt");
            fromDocXToDocBook(input, output);
        } else if (FileHelpers.isODT(input)) {
            System.out.println("NOT YET IMPLEMENYED");
        } else if (FileHelpers.isDocBook(input)) {
            if (outputFormat == OutputFormat.DOCX) {
                String output = FileHelpers.changeExtension(input, ".docx");
                fromDocBookToDocX(input, output);
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

    private static void fromDocXToDocBook(String input, String output) throws Exception {
        // http://www.xmlmind.com/w2x/what_is_w2x.html
        Processor processor = new Processor();
        processor.configure(new String[]{
                "-o", "docbook5",
                "-p", "transform.hierarchy-name", "article",
                "-p", "transform.pre-element-name", "programlisting"
        });
        File inFile = new File(input);
        File outFile = new File(output);
        processor.process(inFile, outFile, null);
    }

    private static void fromDocBookToDocX(String input, String output) throws Exception {
        // http://www.xmlmind.com/foconverter/what_is_xfc.html -> XSL Utility
        // TODO: have a look at C:\Program Files (x86)\XMLmind_XSL_Utility\addon\config\docbook5\xslutil.conversions to know what to implement
    }
}
