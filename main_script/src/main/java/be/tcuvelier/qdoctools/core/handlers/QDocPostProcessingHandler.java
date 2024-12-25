package be.tcuvelier.qdoctools.core.handlers;

import be.tcuvelier.qdoctools.consistency.ConsistencyChecks;
import be.tcuvelier.qdoctools.consistency.ConsistencyResults;
import net.sf.saxon.s9api.SaxonApiException;

import java.io.IOException;
import java.nio.file.*;

// Handler for postprocessing QDoc's DocBook output, including validation.
public class QDocPostProcessingHandler {
    private final Path outputFolder; // Where all the generated files should be put (QDoc may
    // also output in a subfolder, in which case the files are automatically moved to a flatter hierarchy).
    private final Path htmlFolder; // A preexisting copy of the HTML docs.

    public QDocPostProcessingHandler(String output, String htmlFolder)
            throws IOException {
        outputFolder = Paths.get(output);
        this.htmlFolder = Paths.get(htmlFolder);

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
}
