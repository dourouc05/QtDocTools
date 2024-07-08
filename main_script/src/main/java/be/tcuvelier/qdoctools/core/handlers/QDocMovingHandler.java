package be.tcuvelier.qdoctools.core.handlers;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;

// Handler for normalising the output folders of QDoc: make the hierarchy flatter, remove idiosyncrasies.
public class QDocMovingHandler {
    private final Path outputFolder; // Where all the generated files should be put (QDoc may
    // also output in a subfolder, in which case the files are automatically moved to a flatter
    // hierarchy).

    public QDocMovingHandler(String outputFolder) {
        this.outputFolder = Paths.get(outputFolder);
    }

    public void copyGeneratedFiles() throws IOException {
        // Only copy, no move, because consistency checks requires the same folder structure.

        // Maybe everything is under the `html` folder.
        Path abnormalPath = outputFolder.resolve("html");
        if (Files.exists(abnormalPath)) {
            String[] files = abnormalPath.toFile().list((dir, name) -> name.endsWith(".xml"));
            if (files != null && files.length > 0) {
                System.out.println("++> Moving qdoc's result to the expected folder");

                if (Arrays.stream(files)
                        .map(abnormalPath::resolve)
                        .map(Path::toFile)
                        .map(file -> file.renameTo(outputFolder.resolve(file.getName()).toFile()))
                        .anyMatch(val -> !val)) {
                    System.out.println("!!> Moving some files was not possible!");
                }

                if (!abnormalPath.resolve("images").toFile().renameTo(outputFolder.resolve(
                        "images").toFile())) {
                    System.out.println("!!> Moving the `images` folder was not possible!");
                }
            }
        }

        // Or even in one folder per module.
        File[] fs = outputFolder.toFile().listFiles();
        if (fs == null || fs.length == 0) {
            System.out.println("!!> No generated file or folder!");
            System.exit(0);
        }
        List<File> subfolders = Arrays.stream(fs).filter(File::isDirectory).toList();
        for (File subfolder : subfolders) { // For each module...
            // First, deal with the DocBook files.
            File[] files = subfolder.listFiles((dir, name) -> name.endsWith(".xml"));
            if (files != null && files.length > 0) {
                System.out.println("++> Moving qdoc's result from " + subfolder + " to the " +
                        "expected folder");
                for (File f : files) { // For each DocBook file...
                    String name = f.getName();

                    if (name.equals("search-results.xml")) {
                        continue;
                    }

                    Path destination = outputFolder.resolve(name);
                    try {
                        Files.copy(f.toPath(), destination);
                    } catch (FileAlreadyExistsException e) {
                        // TODO: add a CLI option to control overwriting.
                        System.out.println("!!> File already exists: " + destination + ". Tried " +
                                "to copy from: " + f + ". Retrying.");
                        Files.copy(f.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            // Maybe there is an `images` folder to move one level up.
            // Sometimes, the folder has a stranger name, like "images".
            File[] folders = subfolder.listFiles((f, name) -> f.isDirectory());
            if (folders != null) {
                for (File f : folders) {
                    if (f.getName().endsWith("images")) {
                        moveGeneratedImagesRecursively(f, outputFolder.resolve("images"));
                    }
                }
            }
        }

        // Examples may have weird names. For instance:
        //   qtquick3d-d-qt-6-5-3-src-qtquick3d-examples-quick3d-antialiasing-antialiasing-pro.xml ->
        //     qtquick3d-antialiasing-antialiasing-pro.xml
        //     https://doc.qt.io/qt-6/qtquick3d-antialiasing-example.html
        //   qtcoap-d-qt-6-5-3-src-qtcoap-examples-coap-quickmulticastclient-cmakelists-txt.xml ->
        //     qtcoap-quickmulticastclient-cmakelists-txt.xml
        //     https://doc.qt.io/qt-6/qtcoap-quickmulticastclient-cmakelists-txt.html
        //   qthttpserver-d-qt-6-5-3-src-qthttpserver-examples-httpserver-colorpalette-apibehavior-h.xml
        //   qtmqtt-d-qt-6-5-3-src-qtmqtt-examples-mqtt-quicksubscription-cmakelists-txt.xml
        //   qtopcua-d-qt-6-5-3-src-qtopcua-examples-opcua-opcuaviewer-certificatedialog-cpp.xml
        //   qtquick3dphysics-d-qt-6-5-3-src-qtquick3dphysics-examples-quick3dphysics-cannon-box-qml.xml
        //   qtquickeffectmaker-d-qt-6-5-3-src-qtquickeffectmaker-examples-quickeffectmaker-wiggly-cmakelists-txt.xml
        // First, find the prefix that must be removed; then, move the files within the output folder.
        File[] outfs = outputFolder.toFile().listFiles();
        assert outfs != null;
        assert outfs.length > 0;

        String prefix_path = "";
        for (File outf : outfs) {
            if (outf.getName().contains("qtquick3d-examples-quick3d-antialiasing-antialiasing-pro")) {
                // outf.getName() is something like
                // "qtquick3d-d-qt-6-5-3-src-qtquick3d-examples-quick3d-antialiasing-antialiasing-pro.xml"
                String[] parts = outf.getName().split("-", 2); // qtquick3d, d-qt-6-5-3-src-qtquick3d-examples-antialiasing
                parts = parts[1].split("qtquick3d-examples-", 2); // d-qt-6-5-3-src, antialiasing...
                prefix_path = parts[0];
                break;
            }
        }
        if (prefix_path.isEmpty()) {
            // This example wasn't found. Not much to do, apart from testing other file names.
            return;
        }

        for (File outf : outfs) {
            if (outf.getName().contains(prefix_path)) {
                prefix_path = outf.getName();
                break;
            }
        }
    }

    private void moveGeneratedImagesRecursively(File folder, Path destination) throws IOException {
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            return;
        }

        if (!destination.toFile().exists()) {
            if (!destination.toFile().mkdirs()) {
                throw new IOException("Could not create directories: " + destination);
            }
        }

        for (File f : files) {
            String name = f.getName();

            if (f.isDirectory()) {
                moveGeneratedImagesRecursively(f, destination.resolve(name));
                continue;
            }

            Path d = destination.resolve(name);
            try {
                Files.copy(f.toPath(), d);
            } catch (FileAlreadyExistsException e) {
                // TODO: add a CLI option to control overwriting.
                System.out.println("!!> File already exists: " + d + ". Tried to copy from: " + f + ". Retrying.");
                Files.copy(f.toPath(), d, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
