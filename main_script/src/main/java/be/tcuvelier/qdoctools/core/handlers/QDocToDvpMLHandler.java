package be.tcuvelier.qdoctools.core.handlers;

import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

// Handler for transforming QDoc's DocBook output into DvpML.
public class QDocToDvpMLHandler {
    private final Path outputFolder; // Where all the generated files should be put (QDoc may
    // also output in a
    // subfolder, in which case the files are automatically moved to a flatter hierarchy).
    private final GlobalConfiguration config;

    public QDocToDvpMLHandler(String output, GlobalConfiguration config) {
        outputFolder = Paths.get(output);
        this.config = config;
    }

    private List<Path> findWithExtension(@SuppressWarnings("SameParameterValue") String extension) {
        String[] fileNames =
                outputFolder.toFile().list((current, name) -> name.endsWith(extension));
        if (fileNames == null || fileNames.length == 0) {
            return Collections.emptyList();
        } else {
            return Arrays.stream(fileNames).map(outputFolder::resolve).collect(Collectors.toList());
        }
    }

    public List<Path> findDocBook() {
        return findWithExtension(".xml");
    }
}
