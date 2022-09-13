package be.tcuvelier.qdoctools.consistency;

import be.tcuvelier.qdoctools.core.helpers.SetHelpers;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XPathCompiler;
import net.sf.saxon.s9api.XdmNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class ConsistencyChecks {
    private final Path docbookFolder;
    private final Path htmlFolder;
    private final String logPrefix;

    private final Processor processor;
    private final XPathCompiler compiler;

    public ConsistencyChecks(Path docbookFolder, Path htmlFolder, String logPrefix)
            throws IOException {
        this.docbookFolder = docbookFolder;
        this.htmlFolder = htmlFolder;
        this.logPrefix = logPrefix;

        if (!docbookFolder.toFile().exists()) {
            throw new IOException("DocBook folder " + docbookFolder + " does not exist");
        }
        if (!htmlFolder.toFile().exists()) {
            throw new IOException("HTML folder " + htmlFolder + " does not exist");
        }

        // Initialise XML/XPath processor.
        processor = new Processor(false);
        compiler = processor.newXPathCompiler();
        compiler.declareNamespace("db", "http://docbook.org/ns/docbook");
        compiler.declareNamespace("xlink", "http://www.w3.org/1999/xlink");
    }

    public ConsistencyResults checkAll() throws IOException, SaxonApiException {
        // List all folders within the generated docs folders. (Mostly, avoid the global and config
        // subfolders.)
        Predicate<String> isQtModulePredicate =
                (String moduleName) -> moduleName.startsWith("q") || moduleName.equals("activeqt");
        String[] docbookFolders = docbookFolder.toFile().list((dir, name) ->
                new File(dir, name).isDirectory() && isQtModulePredicate.test(name));
        String[] htmlFolders = htmlFolder.toFile().list((dir, name) ->
                new File(dir, name).isDirectory() && isQtModulePredicate.test(name));

        if (docbookFolders == null || docbookFolders.length == 0) {
            return ConsistencyResults.fromMajorError("No DocBook subfolders.");
        }
        if (htmlFolders == null || htmlFolders.length == 0) {
            return ConsistencyResults.fromMajorError("No HTML subfolders.");
        }

        // Operate on sorted arrays to ease the cases where the two versions do not have the same
        // subfolders (e.g., no ActiveQt installed, but its source is available).
        String[] folders = SetHelpers.sortedUnion(docbookFolders, htmlFolders);

        Arrays.sort(docbookFolders);
        Arrays.sort(htmlFolders);

        int indexDocBook = 0;
        int indexHtml = 0;

        ConsistencyResults cr = ConsistencyResults.fromNoError();

        for (final String folderName : folders) {
            if (!docbookFolders[indexDocBook].equals(folderName)) {
                cr.add(ConsistencyResults.fromMissingDocBookModules(1));
                System.out.println(logPrefix + " No DocBook module with name " + folderName + ".");
                indexHtml += 1;
                continue;
            } else if (!htmlFolders[indexHtml].equals(folderName)) {
                cr.add(ConsistencyResults.fromMissingHTMLModules(1));
                System.out.println(logPrefix + " No HTML module with name " + folderName + ".");
                indexDocBook += 1;
                continue;
            }

            cr.add(checkModulePages(folderName));
            cr.add(checkModuleImages(folderName));

            indexHtml += 1;
            indexDocBook += 1;
        }

        return cr;
    }

    FilenameFilter isFileAndHasExtension(String extension) {
        return (dir, name) ->
                new File(dir, name).isFile() && !name.startsWith("search-results") &&
                        name.endsWith(extension);
    }

    Predicate<Path> isFileAndHasOneExtensionOf(String[] extensions) {
        return (Path path) -> path.toFile().isFile() && Arrays.stream(extensions).anyMatch(
                (String extension) -> path.getFileName().endsWith(extension));
    }

    String[] getFilesRecursivelyWithOneExtensionOf(final Path path, final String[] extensions)
            throws IOException {
        try (Stream<Path> paths = Files.walk(path)) {
            return paths.filter(isFileAndHasOneExtensionOf(extensions))
                    .map((Path p) -> path.relativize(p).toString())
                    .toArray(String[]::new);
        }
    }

    public ConsistencyResults checkModulePages(final String moduleName) throws IOException,
            SaxonApiException {
        // List all files within the module.
        String[] docbookPages = docbookFolder.resolve(moduleName).toFile().list(
                isFileAndHasExtension(".xml"));
        String[] htmlPages = htmlFolder.resolve(moduleName).toFile().list(
                isFileAndHasExtension(".html"));

        if (docbookPages == null || docbookPages.length == 0) {
            return ConsistencyResults.fromMajorError(
                    "No DocBook pages within the module " + moduleName + ".");
        }
        if (htmlPages == null || htmlPages.length == 0) {
            return ConsistencyResults.fromMajorError(
                    "No HTML pages within the module " + moduleName + ".");
        }

        // Remove extensions so that comparisons can be performed. DocBook: remove ".xml".
        // HTML: remove ".html".
        docbookPages = (String[]) Arrays.stream(docbookPages).map(
                (String pageName) -> pageName.substring(0, pageName.length() - 4)).sorted().toArray();
        htmlPages = (String[]) Arrays.stream(htmlPages).map(
                (String pageName) -> pageName.substring(0, pageName.length() - 5)).sorted().toArray();

        // Operate on sorted arrays to ease the cases where the two versions do not have the same
        // pages.
        String[] pages = SetHelpers.sortedUnion(docbookPages, htmlPages);

        int indexDocBook = 0;
        int indexHtml = 0;

        ConsistencyResults cr = ConsistencyResults.fromNoError();

        for (final String pageName : pages) {
            if (!docbookPages[indexDocBook].equals(pageName)) {
                cr.add(ConsistencyResults.fromMissingDocBookPages(1));
                System.out.println(logPrefix + " No DocBook page with name " + pageName +
                        " in the module " + moduleName + ".");
                indexHtml += 1;
                continue;
            } else if (!htmlPages[indexHtml].equals(pageName)) {
                cr.add(ConsistencyResults.fromMissingHTMLPages(1));
                System.out.println(logPrefix + " No HTML page with name " + pageName +
                        " in the module " + moduleName + ".");
                indexDocBook += 1;
                continue;
            }

            cr.add(checkPage(moduleName, pageName));

            indexHtml += 1;
            indexDocBook += 1;
        }

        return cr;
    }

    public ConsistencyResults checkModuleImages(final String moduleName) throws IOException {
        // List all images within the module. They may be at various depths, but always have an
        // image extension: JPG, GIF, PNG, SVG.
        final String[] extensions = new String[]{".jpg", ".gif", ".png", ".svg"};
        String[] docbookImages = getFilesRecursivelyWithOneExtensionOf(docbookFolder, extensions);
        String[] htmlImages = getFilesRecursivelyWithOneExtensionOf(htmlFolder, extensions);

        // Operate on sorted arrays to ease the cases where the two versions do not have the same
        // pages.
        String[] images = SetHelpers.sortedUnion(docbookImages, htmlImages);

        Arrays.sort(docbookImages);
        Arrays.sort(htmlImages);

        int indexDocBook = 0;
        int indexHtml = 0;

        ConsistencyResults cr = ConsistencyResults.fromNoError();

        for (final String imageName : images) {
            if (!docbookImages[indexDocBook].equals(imageName)) {
                cr.add(ConsistencyResults.fromMissingDocBookPages(1));
                System.out.println(logPrefix + " No image in the DocBook version with name " +
                        imageName + " in the module " + moduleName + ".");
                indexHtml += 1;
                continue;
            } else if (!htmlImages[indexHtml].equals(imageName)) {
                cr.add(ConsistencyResults.fromMissingHTMLPages(1));
                System.out.println(logPrefix + " No image in the HTML version with name " +
                        imageName + " in the module " + moduleName + ".");
                indexDocBook += 1;
                continue;
            }

            indexHtml += 1;
            indexDocBook += 1;
        }

        return cr;
    }

    public ConsistencyResults checkPage(final String moduleName, final String pageName)
            throws IOException, SaxonApiException {
        final Path docbookFile = docbookFolder.resolve(moduleName).resolve(pageName + ".xml");
        final Path htmlFile = htmlFolder.resolve(moduleName).resolve(pageName + ".html");

        assert docbookFile.toFile().exists();
        assert htmlFile.toFile().exists();

        final XdmNode xdm = processor.newDocumentBuilder().build(
                new StreamSource(new FileReader(docbookFile.toFile())));
        final Document html = Jsoup.parse(Files.readString(htmlFile));

        ConsistencyChecker cc = new ConsistencyChecker(logPrefix, processor, docbookFile,
                htmlFile, xdm, html, compiler);
        cc.perform(InheritedBy::checkInheritedBy);
        cc.perform(Items::checkItems);

        return cc.cr;
    }
}
