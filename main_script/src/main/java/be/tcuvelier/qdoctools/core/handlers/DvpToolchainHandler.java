package be.tcuvelier.qdoctools.core.handlers;

import be.tcuvelier.qdoctools.core.config.Configuration;
import net.sf.saxon.s9api.*;

import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class DvpToolchainHandler {
    public static void updateToolchain(Configuration config) throws IOException, InterruptedException {
        String script = config.getPerlScriptsPath().resolve("mise-a-jour-kit-generation.pl").toString();
        List<String> params = new ArrayList<>(Arrays.asList(config.getPerlPath(), script));
        new ProcessBuilder(params).start().waitFor();
    }

    public static List<Path> neededFiles(String fileName) throws FileNotFoundException, SaxonApiException {
        // First, parse the file.
        Path file = Paths.get(fileName);
        Processor processor = new Processor(false);
        XdmNode xdm = processor.newDocumentBuilder().build(new StreamSource(new FileReader(file.toFile())));
        XPathCompiler compiler = processor.newXPathCompiler();
        compiler.declareNamespace("db", "http://docbook.org/ns/docbook");
        compiler.declareNamespace("xlink", "http://www.w3.org/1999/xlink");

        // Look for all media objects.
        XdmValue images = compiler.evaluate("//imagedata/@fileref", xdm);

        // Make a list of it.
        List<Path> list = new ArrayList<>(images.size());
        for (int i = 0; i < images.size(); ++i) {
            list.add(file.resolve(images.itemAt(i).getStringValue()));
        }

        return list;
    }

    public static void generateHTML(String file, String outputFolder, Configuration config) throws IOException, InterruptedException, SaxonApiException {
        // Find a good folder name (i.e. one that does not exist yet).
        Path root = config.getToolchainPath().resolve("documents");
        String folderName = "qdt";
        if (root.resolve(folderName).toFile().exists()) {
            int i = 0;
            while (root.resolve(folderName + i).toFile().exists()) {
                ++i;
            }
            folderName += i;
        }

        Path folder = root.resolve(folderName);
        if (!folder.toFile().mkdir()) {
            throw new IOException("Impossible to create a folder within " + root.toString());
        }

        // Copy the XML at the right place.
        Path fileFolder = Paths.get(file).getParent();
        Path xml = Paths.get(file);
        List<Path> neededFiles = neededFiles(file).stream().map(f -> f.relativize(xml)).collect(Collectors.toList());

        Files.copy(xml, folder.resolve(xml.getFileName()));
        for (Path f: neededFiles) {
            Files.copy(fileFolder.resolve(f), folder.resolve(f));
        }

        // Start generation.
        String script = config.getToolchainPath().resolve("script").resolve("buildart").toString();
        List<String> params = new ArrayList<>(Arrays.asList(config.getPerlPath(), script, folderName));
        new ProcessBuilder(params).start().waitFor();

        // Copy the result in the right place.
        Path cache = config.getToolchainPath().resolve("cache").resolve(folderName); // cache: PHP files; html: HTML files.
        Path output = Paths.get(outputFolder);

        Files.copy(cache.resolve("index.php"), output.resolve("index.php"));
        for (Path f: neededFiles) {
            Files.copy(cache.resolve(f), output.resolve(f));
        }

        // TODO: what about multipage articles?

        // Clean the toolchain's folders.
        if (!Files.walk(folder).map(Path::toFile).allMatch(File::delete))
            System.out.println("There was a problem cleaning the contents of the folder " + folder);
        if (!folder.toFile().delete())
            System.out.println("There was a problem cleaning the folder " + folder);

        if (!Files.walk(cache).map(Path::toFile).allMatch(File::delete))
            System.out.println("There was a problem cleaning the contents of the folder " + cache);
        if (!cache.toFile().delete())
            System.out.println("There was a problem cleaning the folder " + cache);
    }
}
