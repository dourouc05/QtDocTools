package be.tcuvelier.qdoctools.cli;

import be.tcuvelier.qdoctools.core.UploadCore;
import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(name = "upload", description = "Generates and uploads an article or a website")
public class UploadCommand implements Callable<Void> {
    @Option(names = { "-i", "--input-file", "--input-folder" },
            description = "File or folder to generate and upload", required = true)
    private String input;

    @Option(names = "--follow-links",
            description = "Follows the links in the files (default: ${DEFAULT-VALUE}). " +
                          "If true, for reference files, all linked files will be generated.")
    private boolean followLinks = true;

    @Option(names = "--upload",
            description = "Uploads the generated files (default: ${DEFAULT-VALUE})")
    private boolean upload = true;

    @Option(names = { "-c", "--configuration-file" },
            description = "Configuration file (default: ${DEFAULT-VALUE})")
    private String configurationFile = "config.json";

    @Override
    public Void call() throws Exception {
        if (followLinks) {
            UploadCore.callRelatedAndSubarticles(input, upload, new GlobalConfiguration(configurationFile));
        } else {
            UploadCore.callRelated(input, upload, new GlobalConfiguration(configurationFile));
        }
        return null;
    }
}
