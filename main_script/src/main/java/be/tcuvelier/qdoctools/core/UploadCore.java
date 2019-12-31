package be.tcuvelier.qdoctools.core;

import be.tcuvelier.qdoctools.core.handlers.DvpToolchainHandler;
import be.tcuvelier.qdoctools.core.utils.Configuration;
import net.sf.saxon.s9api.SaxonApiException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class UploadCore {
    public static void call(String input, String folder, boolean upload, String configurationFile)
            throws IOException, SaxonApiException, InterruptedException {
        Configuration config = new Configuration(configurationFile);

        // Find the configuration file for the operation to perform (contains info about uploading).

        // Perform generation with Dvp toolchain.
        String output = folder.isEmpty() ? Files.createTempDirectory("qdt").toString() : folder;
        DvpToolchainHandler.generateHTML(input, output, config);

        // Upload if required
        if (upload) {
            // Get FTP configuration from keyring.

            // Perform the upload.
        }
    }
}
