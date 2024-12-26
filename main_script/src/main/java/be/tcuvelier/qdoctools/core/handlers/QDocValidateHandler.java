package be.tcuvelier.qdoctools.core.handlers;

import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.helpers.FileHelpers;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class QDocValidateHandler {
    private final Path outputFolder; // Where all the generated files should be put.
    private final GlobalConfiguration config;

    public QDocValidateHandler(String output, GlobalConfiguration config)
            throws IOException {
        outputFolder = Paths.get(output);
        this.config = config;

        if (!outputFolder.toFile().isDirectory()) {
            throw new IOException("Invalid output folder: " + outputFolder);
        }
    }

    public void validateDocBook() throws IOException, SAXException {
        int nFiles = 0;
        int nEmptyFiles = 0;
        int nValidFiles = 0;
        for (Path filePath : FileHelpers.findWithExtension(outputFolder, ".xml")) {
            nFiles += 1;
            if (nFiles % 1000 == 0) {
                System.out.println("++> " + nFiles + " validated");
            }

            if (Files.size(filePath) == 0) {
                // Validation can only fail for empty files.
                nEmptyFiles += 1;
                continue;
            }

            if (ValidationHandler.validateDocBook(filePath.toFile(), config)) {
                nValidFiles += 1;
            } else {
                System.out.println("!!> Invalid file: " + filePath);
            }
        }
        System.out.println("++> " + nFiles + " validated, " +
                nValidFiles + " valid, " + (nFiles - nValidFiles) + " invalid, " + nEmptyFiles +
                " empty.");
    }
}
