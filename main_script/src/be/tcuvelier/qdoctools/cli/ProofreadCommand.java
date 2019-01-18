package be.tcuvelier.qdoctools.cli;

import picocli.CommandLine.Option;
import picocli.CommandLine.Command;

@Command(name = "proofread", description = "Perform transformations pertaining to proofreading")
public class ProofreadCommand {
    @Option(names = { "-i", "--input-file", "--input-folder" },
            description = "File (normal mode) or folder (qdoc mode) to process", required = true)
    private String input;
}
