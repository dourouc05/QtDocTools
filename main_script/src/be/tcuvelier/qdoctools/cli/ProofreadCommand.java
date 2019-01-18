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
        String output = FileHelpers.changeExtension(input, ".qdt");
        if (FileHelpers.isDocX(input)) {
            fromDocXToDocBook(input, output);
        } else {
            throw new RuntimeException("File format not recognised for input file!");
        }

        return null;
    }

    private static void fromDocXToDocBook(String input, String output) throws Exception {
        Processor processor = new Processor();
        processor.configure(new String[]{ "-o", "docbook5" });
        File inFile = new File(input);
        File outFile = new File(output);
        processor.process(inFile, outFile, null);
    }
}
