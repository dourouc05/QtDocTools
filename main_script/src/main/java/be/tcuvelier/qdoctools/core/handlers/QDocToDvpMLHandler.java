package be.tcuvelier.qdoctools.core.handlers;

import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.helpers.FileHelpers;
import be.tcuvelier.qdoctools.core.helpers.ValidationHelper;
import be.tcuvelier.qdoctools.core.utils.QtVersion;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Handler for transforming QDoc's DocBook output into DvpML.
public class QDocToDvpMLHandler {
    private final Path outputFolder; // Where all the generated files should be put (QDoc may
    // also output in a subfolder, in which case the files are automatically moved to a flatter hierarchy).
    private final Path dvpmlOutputFolder; // Where the DvpML files should be put.
    private final QtVersion qtVersion;
    private final GlobalConfiguration config;

    public QDocToDvpMLHandler(String output, String dvpmlOutput, QtVersion qtVersion, GlobalConfiguration config)
            throws IOException {
        outputFolder = Paths.get(output);
        dvpmlOutputFolder = Paths.get(dvpmlOutput);
        this.qtVersion = qtVersion;
        this.config = config;

        ensureDvpMLOutputFolderExists();
    }

    private void ensureDvpMLOutputFolderExists() throws IOException {
        Files.createDirectories(dvpmlOutputFolder);
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

    public Path rewritePath(Path dbFile) {
        // Output the result in the right subfolder, with the same file name, just add a "_dvp" suffix
        // (the same as in the XSLT sheets).
        String baseFileName = FileHelpers.removeExtension(dbFile);
        Path destinationFolder = dvpmlOutputFolder.resolve(baseFileName);
        return destinationFolder.resolve(baseFileName + "_dvp.xml");
    }

    // Links should no longer point to .xml files. This is expected for DocBook, this is wrong for DvpML (and it's too
    // specific to Qt's doc to be implemented in a more generic XSLT).
    public void fixURLs(Path dvpmlFile) throws IOException {
        // Directly replace the file, no need for a backup here: the input files are not really expensive to generate
        // (compared to running QDoc).
        String rootURL = "https://qt.developpez.com/doc/" + qtVersion.QT_VER() + "/";
        String fileContents = Files.readString(dvpmlFile);
        Pattern regex = Pattern.compile("<link href=\"(.*)\\.xml");
        fileContents = regex.matcher(fileContents).replaceAll("<link href=\"" + rootURL + "$1/");
        Files.write(dvpmlFile, fileContents.getBytes());

        // TODO: at some point, there should be a better implementation to map .xml links to online links.
    }

    public void copyImages(Path dbFile, Path dvpmlFile) throws IOException {
        String fileContents = Files.readString(dvpmlFile);
        Pattern regex = Pattern.compile("<image src=\"([^\"]*)\"");
        Matcher matcher = regex.matcher(fileContents);
        if (matcher.find()) {
            // Create the image folder for this page.
            Path pageFolder = dvpmlFile.getParent();
            Path imageFolder = pageFolder.resolve("images");
            if (!imageFolder.toFile().exists()) {
                if (!imageFolder.toFile().mkdirs()) {
                    throw new IOException("Could not create directories: " + imageFolder);
                }
            }

            // Copy the right images. do-while: consume the first match before moving on to the others.
            // Don't copy if this is an SVG file that the XSLT moved to the right place.
            do {
                String path = matcher.group(1);
                if (!path.startsWith("images/")) {
                    throw new IOException("Unexpected image URI: " + path);
                }

                Path newPath = pageFolder.resolve(path);
                if (path.endsWith(".svg") && path.contains("svg_")) {
                    if (!newPath.toFile().exists()) {
                        throw new IOException("Copying SVG image: " + newPath + " should have been done by the XSLT sheets, but was not");
                    }
                    System.out.println("++> Copying SVG image: " + newPath + " already done");
                } else {
                    Path oldPath = dbFile.getParent().resolve(path);
                    System.out.println("++> Copying image: from " + oldPath + " to " + newPath);
                    Files.copy(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } while (matcher.find());
        }
    }

    public boolean isValid(Path dvpmlFile) throws IOException, SAXException {
        return ValidationHelper.validateDvpML(dvpmlFile, config);
    }

    public void moveIndex() throws IOException {
        Path indexFolder = dvpmlOutputFolder.resolve("index");
        Files.move(indexFolder.resolve("index_dvp.xml"), dvpmlOutputFolder.resolve("index_dvp.xml"));
        Files.move(indexFolder.resolve("images"), dvpmlOutputFolder.resolve("images"));
        Files.delete(indexFolder);
    }
}
