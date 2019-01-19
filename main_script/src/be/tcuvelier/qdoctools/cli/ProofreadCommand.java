package be.tcuvelier.qdoctools.cli;

import be.tcuvelier.qdoctools.helpers.FileHelpers;
import picocli.CommandLine.Option;
import picocli.CommandLine.Command;
import com.xmlmind.w2x.processor.Processor;

import java.io.File;
import java.util.concurrent.Callable;

@Command(name = "proofread", description = "Perform transformations pertaining to proofreading")
public class ProofreadCommand implements Callable<Void> {
    @Option(names = { "-i", "--input-file", "--input-folder" },
            description = "File to process. The ", required = true)
    private String input;

    @Override
    public Void call() throws Exception {
        if (FileHelpers.isDocX(input)) {
            String output = FileHelpers.changeExtension(input, ".qdt");
            fromDocXToDocBook(input, output);
        } else if (FileHelpers.isDocBook(input)) {
            String output = FileHelpers.changeExtension(input, ".docx");
            fromDocBookToDocX(input, output);
        } else {
            throw new RuntimeException("File format not recognised for input file!");
        }

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
