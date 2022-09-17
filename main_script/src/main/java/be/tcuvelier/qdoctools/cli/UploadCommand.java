package be.tcuvelier.qdoctools.cli;

import be.tcuvelier.qdoctools.core.UploadCore;
import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.helpers.FileHelpers;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(name = "upload", description = "Generates and uploads an article or a website")
public class UploadCommand implements Callable<Void> {
    @Option(names = "--upload",
            description = "Uploads the generated files (default: ${DEFAULT-VALUE})")
    private boolean upload = true;
    @Option(names = {"-c", "--configuration-file"},
            description = "Configuration file (default: ${DEFAULT-VALUE})")
    private String configurationFile = "config.json";
    @Option(names = {"-i", "--input-file", "--input-folder"},
            description = "File or folder to generate and upload", required = true)
    private String input;

    @Override
    public Void call() throws Exception {
        GlobalConfiguration config = new GlobalConfiguration(configurationFile);
        if (FileHelpers.isRelated(input)) {
            UploadCore.callRelated(input, upload, config);
        } else {
            UploadCore.call(input, upload, config);
        }
        return null;
    }
}
