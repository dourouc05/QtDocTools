package be.tcuvelier.qdoctools.core.handlers;

import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.helpers.ValidationHelper;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
        for (Path filePath : findDocBook()) {
            nFiles += 1;
            if (Files.size(filePath) == 0) {
                // Validation can only fail for empty files.
                nEmptyFiles += 1;
                continue;
            }

            if (ValidationHelper.validateDocBook(filePath, config)) {
                nValidFiles += 1;
            } else {
                System.out.println("!!> Invalid file: " + filePath);
            }
        }
        System.out.println("++> " + nFiles + " validated, " +
                nValidFiles + " valid, " + (nFiles - nValidFiles) + " invalid, " + nEmptyFiles +
                " empty.");
    }

    // TODO: this does not belong to a handler.
    private List<Path> findWithExtension(@SuppressWarnings("SameParameterValue") String extension) {
        String[] fileNames =
                outputFolder.toFile().list((current, name) -> name.endsWith(extension));
        if (fileNames == null || fileNames.length == 0) {
            return Collections.emptyList();
        } else {
            return Arrays.stream(fileNames).map(outputFolder::resolve).collect(Collectors.toList());
        }
    }

    // TODO: this does not belong to a handler.
    public List<Path> findDocBook() {
        return findWithExtension(".xml");
    }
}
