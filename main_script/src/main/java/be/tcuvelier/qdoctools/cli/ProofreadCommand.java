package be.tcuvelier.qdoctools.cli;

/*
 * TODO: How to encode/retrieve block <screen> vs. <programlisting>? screen/prompt?
 * TODO: How to encode/retrieve inline <filename>? filename/replaceable?
 */

import be.tcuvelier.qdoctools.utils.handlers.SanityCheckHandler;
import be.tcuvelier.qdoctools.utils.helpers.FileHelpers;
import be.tcuvelier.qdoctools.utils.io.DocxInput;
import be.tcuvelier.qdoctools.utils.io.DocxOutput;
import net.sf.saxon.s9api.SaxonApiException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.FileNotFoundException;
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
                System.out.println("SANITY CHECK: one or more sanity checks did not pass. It is better if you " +
                        "correct these problems now; otherwise, you may use the option --disable-sanity-checks to " +
                        "ignore them. In the latter case, you may face errors while producing the DOCX file or, " +
                        "more likely, you will not get a comparable DocBook file when round-tripping.");
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
        new DocxInput(input).toDocBook(output);
    }

    private static void fromDocBookToDOCX(String input, String output) throws Exception {
        new DocxOutput(input).toDocx(output);
    }
}
