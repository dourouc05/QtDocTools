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
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
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
        List<String> docbookFolders;
        List<String> htmlFolders;

        {
            String[] docbookFolders_ = docbookFolder.toFile().list((dir, name) ->
                    new File(dir, name).isDirectory() && isQtModulePredicate.test(name));
            String[] htmlFolders_ = htmlFolder.toFile().list((dir, name) ->
                    new File(dir, name).isDirectory() && isQtModulePredicate.test(name));

            if (docbookFolders_ == null || docbookFolders_.length == 0) {
                return ConsistencyResults.fromMajorError("No DocBook subfolders.");
            }
            if (htmlFolders_ == null || htmlFolders_.length == 0) {
                return ConsistencyResults.fromMajorError("No HTML subfolders.");
            }

            docbookFolders = Arrays.asList(docbookFolders_);
            htmlFolders = Arrays.asList(htmlFolders_);
        }

        // Operate on sorted arrays to ease the cases where the two versions do not have the same
        // subfolders (e.g., no ActiveQt installed, but its source is available).
        List<String> folders = SetHelpers.sortedUnion(docbookFolders, htmlFolders);

        docbookFolders.sort(null);
        htmlFolders.sort(null);

        int indexDocBook = 0;
        int indexHtml = 0;

        ConsistencyResults cr = ConsistencyResults.fromNoError();

        for (final String folderName : folders) {
            if (!docbookFolders.get(indexDocBook).equals(folderName)) {
                cr.add(ConsistencyResults.fromMissingDocBookModules(1));
                System.out.println(logPrefix + " No DocBook module with name " + folderName + ".");
                indexHtml += 1;
                continue;
            } else if (!htmlFolders.get(indexHtml).equals(folderName)) {
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

    List<String> getFilesRecursivelyWithOneExtensionOf(final Path path, final String[] extensions)
            throws IOException {
        try (Stream<Path> paths = Files.walk(path)) {
            return paths.filter(isFileAndHasOneExtensionOf(extensions))
                    .map((Path p) -> path.relativize(p).toString())
                    .collect(Collectors.toList());
            // Don't use Stream::toList, as this list is immutable.
        }
    }

    private List<String> getAllHtmlPagesSorted(final String moduleName) throws IOException,
            SaxonApiException {
        String[] pagesArray = htmlFolder.resolve(moduleName).toFile().list(
                isFileAndHasExtension(".html"));
        if (pagesArray == null || pagesArray.length == 0) {
            return Collections.emptyList();
        }

        Stream<String> pages = Arrays.stream(pagesArray);

        // Remove the extension ".html" so that comparisons can be performed with DocBook.
        pages = pages.map((String pageName) -> pageName.substring(0, pageName.length() - 5));

        // DocBook doesn't have "-members" or "-obsolete" pages, remove them from HTML so that they
        // do not appear in the comparison.
        pages = pages.filter((String pageName) ->
                !pageName.endsWith("-members") && !pageName.endsWith("-obsolete"));

        // DocBook doesn't have examples-manifest.xml files.
        pages = pages.filter((String pageName) -> pageName.equals("examples-manifest"));

        return pages.sorted().toList();
    }

    private List<String> getAllDocBookPagesSorted(final String moduleName) throws IOException,
            SaxonApiException {
        String[] pagesArray = htmlFolder.resolve(moduleName).toFile().list(
                isFileAndHasExtension(".xml"));
        if (pagesArray == null || pagesArray.length == 0) {
            return Collections.emptyList();
        }

        Stream<String> pages = Arrays.stream(pagesArray);

        // Remove the extension ".xml" so that comparisons can be performed with HTML.
        pages = pages.map((String pageName) -> pageName.substring(0, pageName.length() - 4));

        return pages.sorted().toList();
    }

    public ConsistencyResults checkModulePages(final String moduleName) throws IOException,
            SaxonApiException {
        // List all files within the module.
        // Operate on sorted arrays to ease the cases where the two versions do not have the same
        // pages.
        List<String> docbookPages = getAllDocBookPagesSorted(moduleName);
        List<String> htmlPages = getAllHtmlPagesSorted(moduleName);
        if (docbookPages.isEmpty()) {
            return ConsistencyResults.fromMajorError(
                    "No DocBook pages within the module " + moduleName + ".");
        }
        if (htmlPages.isEmpty()) {
            return ConsistencyResults.fromMajorError(
                    "No HTML pages within the module " + moduleName + ".");
        }

        int indexDocBook = 0;
        int indexHtml = 0;

        ConsistencyResults cr = ConsistencyResults.fromNoError();

        for (final String pageName : SetHelpers.sortedUnion(docbookPages, htmlPages)) {
            if (!docbookPages.get(indexDocBook).equals(pageName)) {
                cr.add(ConsistencyResults.fromMissingDocBookPages(1));
                System.out.println(logPrefix + " No DocBook page with name " + pageName +
                        " in the module " + moduleName + ".");
                indexHtml += 1;
                continue;
            } else if (!htmlPages.get(indexHtml).equals(pageName)) {
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
        List<String> docbookImages = getFilesRecursivelyWithOneExtensionOf(docbookFolder, extensions);
        List<String> htmlImages = getFilesRecursivelyWithOneExtensionOf(htmlFolder, extensions);

        // Operate on sorted arrays to ease the cases where the two versions do not have the same
        // pages.
        List<String> images = SetHelpers.sortedUnion(docbookImages, htmlImages);

        docbookImages.sort(null);
        htmlImages.sort(null);

        int indexDocBook = 0;
        int indexHtml = 0;

        ConsistencyResults cr = ConsistencyResults.fromNoError();

        for (final String imageName : images) {
            if (!docbookImages.get(indexDocBook).equals(imageName)) {
                cr.add(ConsistencyResults.fromMissingDocBookPages(1));
                System.out.println(logPrefix + " No image in the DocBook version with name " +
                        imageName + " in the module " + moduleName + ".");
                indexHtml += 1;
                continue;
            } else if (!htmlImages.get(indexHtml).equals(imageName)) {
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

    public ConsistencyResults checkPage(final String moduleName, final String pageName) {
        final Path docbookFile = docbookFolder.resolve(moduleName).resolve(pageName + ".xml");
        final Path htmlFile = htmlFolder.resolve(moduleName).resolve(pageName + ".html");

        assert docbookFile.toFile().exists();
        assert htmlFile.toFile().exists();

        XdmNode xdm;
        try {
            xdm = processor.newDocumentBuilder().build(
                    new StreamSource(new FileReader(docbookFile.toFile())));
        } catch (IOException ioe) {
            String error = "Error while reading " + docbookFile;
            System.out.println(">>> " + error);
            return ConsistencyResults.fromMajorError(error);
        } catch (SaxonApiException ioe) {
            String error = "Error while parsing " + docbookFile;
            System.out.println(">>> " + error);
            return ConsistencyResults.fromMajorError(error);
        }

        Document html;
        try {
            html = Jsoup.parse(Files.readString(htmlFile));
        } catch (IOException ioe) {
            String error = "Error while reading " + docbookFile;
            System.out.println(">>> " + error);
            return ConsistencyResults.fromMajorError(error);
        }

        ConsistencyChecker cc = new ConsistencyChecker(logPrefix, processor, docbookFile,
                htmlFile, xdm, html, compiler);
        cc.perform(InheritedBy::checkInheritedBy);
        cc.perform(Items::checkItems);


        return cc.cr;
    }
}
