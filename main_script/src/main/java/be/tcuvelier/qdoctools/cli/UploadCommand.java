package be.tcuvelier.qdoctools.cli;

import be.tcuvelier.qdoctools.core.UploadCore;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(name = "upload", description = "Generates and uploads an article or a website")
public class UploadCommand implements Callable<Void> {
    @Option(names = { "-i", "--input-file", "--input-folder" },
            description = "File or folder to generate and upload", required = true)
    private String input;

    @Option(names = { "-f", "--generate-folder" },
            description = "Folder where the generated files are put. If empty, a temporary folder is created and " +
                    "files are removed upon completion. Otherwise, the files are kept (default: ${DEFAULT-VALUE})")
    private String folder = "";

    @Option(names = "--upload",
            description = "Uploads the generated files (default: ${DEFAULT-VALUE})")
    private boolean upload = true;

    @Option(names = { "-c", "--configuration-file" },
            description = "Configuration file (default: ${DEFAULT-VALUE})")
    private String configurationFile = "config.json";

    @Override
    public Void call() throws Exception {
        UploadCore.call(input, folder, upload, configurationFile);
        return null;
    }
}
