package be.tcuvelier.qdoctools.core.handlers;

import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.config.PerlPath;
import net.sf.saxon.s9api.*;

import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class DvpToolchainHandler {
    public static void updateToolchain(GlobalConfiguration config) throws IOException, InterruptedException {
        String script = config.getDvpPerlScriptsPath().resolve("mise-a-jour-kit-generation.pl").toString();
        List<String> params = new ArrayList<>(Arrays.asList(new PerlPath(config).getPerlPath(), script));
        new ProcessBuilder(params).start().waitFor();
    }

    private static void ensureFolderExists(Path folder) throws IOException {
        if (!folder.toFile().mkdir()) {
            throw new IOException("Impossible to create a folder " + folder.getFileName() + " within " + folder.getParent());
        }
    }

    private static Path findFreeFolderName(GlobalConfiguration config) throws IOException {
        Path root = config.getDvpToolchainPath().resolve("documents");
        String folderName = "qdt";
        if (root.resolve(folderName).toFile().exists()) {
            int i = 0;
            while (root.resolve(folderName + i).toFile().exists()) {
                ++i;
            }
            folderName += i;
        }

        Path folder = root.resolve(folderName);
        ensureFolderExists(folder);
        return folder;
    }

    private static List<Path> neededFiles(String fileName) throws FileNotFoundException, SaxonApiException {
        // First, parse the file.
        Path file = Paths.get(fileName);
        Processor processor = new Processor(false);
        XdmNode xdm = processor.newDocumentBuilder().build(new StreamSource(new FileReader(file.toFile())));
        XPathCompiler compiler = processor.newXPathCompiler();
        compiler.declareNamespace("db", "http://docbook.org/ns/docbook");
        compiler.declareNamespace("xlink", "http://www.w3.org/1999/xlink");

        // Look for all media objects.
        XdmValue images = compiler.evaluate("//image/@src", xdm);

        // Make a list of it.
        List<Path> list = new ArrayList<>(images.size());
        for (int i = 0; i < images.size(); ++i) {
            list.add(file.getParent().resolve(images.itemAt(i).getStringValue()));
        }

        return list;
    }

    private static List<Path> relativisedNeededFiles(String fileName, Path folder) throws FileNotFoundException, SaxonApiException {
        return neededFiles(fileName).stream().map(folder::relativize).collect(Collectors.toList());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static void startDvpMLTool(String tool, String argument, GlobalConfiguration config) throws IOException, InterruptedException {
        String extension = isWindows() ? ".bat" : ".sh";
        Path script = config.getDvpToolchainPath().resolve("script").resolve(tool + extension);
        ensureDvpMLToolsWorked(new ProcessBuilder(script.toString(), argument).start());
    }

    private static void ensureDvpMLToolsWorked(Process process) throws InterruptedException {
        int errorCode = process.waitFor();

        if (errorCode != 0) {
            String error = new BufferedReader(new InputStreamReader(process.getErrorStream())).lines().collect(Collectors.joining("\n"));
            if (error.length() > 0) {
                System.err.println(error);
            }

            throw new RuntimeException("Running the DvpML tools failed.", new RuntimeException(error));
        }
    }

    private static void cleanFolder(Path folder) {
        cleanFolder(folder.toFile());
    }

    private static void cleanFolder(File folder) {
        // Inspired from https://www.baeldung.com/java-delete-directory.
        File[] allContents = folder.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                cleanFolder(file);
            }
        }
        if (!folder.delete()) {
            String what = (folder.isDirectory()) ? "folder" : "file";
            System.out.println("There was a problem cleaning the " + what + " " + folder);
        }
    }

    public static void generateHTML(String file, String outputFolder, GlobalConfiguration config) throws IOException, InterruptedException, SaxonApiException {
        // Find a good folder name (i.e. one that does not exist yet).
        Path folder = findFreeFolderName(config);
        String folderName = folder.getFileName().toString();

        // Copy the XML at the right place.
        Path xml = Paths.get(file);
        Path fileFolder = xml.getParent();
        List<Path> neededFiles = relativisedNeededFiles(file, fileFolder);

        Files.copy(xml, folder.resolve(folderName + ".xml"));
        for (Path f: neededFiles) {
            Files.copy(fileFolder.resolve(f), folder.resolve(f), StandardCopyOption.REPLACE_EXISTING);
        }

        // Start generation.
        startDvpMLTool("buildart", folderName, config);

        // Copy the result in the right place.
        Path cache = config.getDvpToolchainPath().resolve("cache").resolve(folderName); // cache: PHP files; html: HTML files.
        Path output = Paths.get(outputFolder);

        Files.copy(cache.resolve("index.php"), output.resolve("index.php"));
        Files.copy(cache.resolve(folderName + ".xml"), output.resolve("qdt.xml"));
        for (Path f: neededFiles) {
            // The Developpez tools do not copy files outside the "images" and "fichiers" folders...
            if (cache.resolve(f).toFile().exists()) {
                Files.copy(cache.resolve(f), output.resolve(f), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(fileFolder.resolve(f), output.resolve(f), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        // TODO: what about multipage articles?

        // Clean the toolchain's folders.
        cleanFolder(folder);
        cleanFolder(cache);
    }

    public static void generateRelated(String file, String outputFolder, GlobalConfiguration config) throws IOException, InterruptedException {
        // Find a good folder name (i.e. one that does not exist yet).
        Path folder = findFreeFolderName(config);
        String folderName = folder.getFileName().toString();

        // Copy the XML at the right place.
        Path xml = Paths.get(file);
        if (xml.toFile().isDirectory()) {
            xml = xml.resolve("related.xml");
        }
        Files.copy(xml, folder.resolve(folderName + ".xml"));

        // Start generation.
        startDvpMLTool("buildref", folderName, config);

        // Copy the result in the right place.
        Path cache = config.getDvpToolchainPath().resolve("cache").resolve(folderName);
        Path output = Paths.get(outputFolder);

        Files.copy(cache.resolve(folderName + ".inc"), output.resolve("related.inc"));
        Files.copy(xml, output.resolve("related.xml")); // The XML file is not copied to the cache folder.

        // Clean the toolchain's folders.
        cleanFolder(folder);
        cleanFolder(cache);
    }
}
