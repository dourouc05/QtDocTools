package be.tcuvelier.qdoctools.core.handlers;

import be.tcuvelier.qdoctools.consistency.ConsistencyChecks;
import be.tcuvelier.qdoctools.consistency.ConsistencyResults;
import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.helpers.ValidationHelper;
import net.sf.saxon.s9api.SaxonApiException;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

// Handler for postprocessing QDoc's DocBook output, including validation.
public class QDocPostProcessingHandler {
    private final Path outputFolder; // Where all the generated files should be put (QDoc may
    // also output in a subfolder, in which case the files are automatically moved to a flatter hierarchy).
    private final Path htmlFolder; // A preexisting copy of the HTML docs.
    private final GlobalConfiguration config;

    public QDocPostProcessingHandler(String output, String htmlFolder, GlobalConfiguration config)
            throws IOException {
        outputFolder = Paths.get(output);
        this.htmlFolder = Paths.get(htmlFolder);
        this.config = config;

        ensureOutputFolderExists();
    }

    private void ensureOutputFolderExists() throws IOException {
        if (!outputFolder.toFile().isDirectory()) {
            Files.createDirectories(outputFolder);
        }
        if (!outputFolder.resolve("images").toFile().isDirectory()) {
            Files.createDirectories(outputFolder.resolve("images"));
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

    // TODO: move this to another handler.
    public void checkDocBookConsistency() throws IOException, SaxonApiException {
        ConsistencyResults cr = new ConsistencyChecks(outputFolder, htmlFolder, ">>> ").checkAll();
        if (! cr.hasErrors()) {
            System.out.println("++> Consistency checks revealed no discrepancy.");
        } else {
            System.out.println("++> Consistency checks revealed differences between DocBook and HTML.");
            System.out.println(cr.describe("++> "));
        }
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
