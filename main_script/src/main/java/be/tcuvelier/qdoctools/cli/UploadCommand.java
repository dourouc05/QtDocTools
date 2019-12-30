package be.tcuvelier.qdoctools.cli;

import be.tcuvelier.qdoctools.utils.handlers.SanityCheckHandler;
import be.tcuvelier.qdoctools.utils.helpers.FileHelpers;
import be.tcuvelier.qdoctools.io.DocxInput;
import be.tcuvelier.qdoctools.io.DocxOutput;
import net.sf.saxon.s9api.SaxonApiException;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.FileNotFoundException;
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

    @Override
    public Void call() throws Exception {
        // Find the configuration file for the operation to perform (contains info about uploading).

        // Perform generation with Dvp toolchain.

        // Upload if required
        if (upload) {
            // Get FTP configuration from keyring.

            // Perform the upload.
        }

        return null;
    }
}
