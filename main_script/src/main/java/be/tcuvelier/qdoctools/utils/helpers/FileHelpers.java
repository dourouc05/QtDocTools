package be.tcuvelier.qdoctools.utils.helpers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collector;
import java.util.stream.Stream;

public class FileHelpers {
    public static boolean isDvpML(String path) {
        return path.endsWith(".xml") && ! isDocBook(path);
    }

    public static boolean isDocBook(String path) {
        // Check the extension: it sometimes is sufficient to conclude.
        boolean extensionOK = path.endsWith(".db") || path.endsWith(".dbk") || path.endsWith(".xml");
        if (extensionOK) {
            return true;
        }

        // Check the contents of the file if it ends with ".xml".
        try {
            // Must find a reference to the DocBook name space near the top.
            // A cleaner way would be to read parse whole XML document and look at the name spaces, but that may
            // take quite a while.
            // The name space declaration can be quite far, beyond the first ten lines:
            // https://docbook.org/docs/howto/howto.xml.
            boolean foundXMLNS = false;
            boolean foundDB = false;
            String[] content = Files.lines(Paths.get(path)).toArray(String[]::new);
            for (String s: content) {
                if (! foundXMLNS && s.contains("xmlns")) {
                    foundXMLNS = true;
                }
                if (!foundDB && s.contains("http://docbook.org/ns/docbook")) {
                    foundDB = true;
                }

                if (foundXMLNS && foundDB) {
                    return true;
                }
            }

            // No name space found, say it's not DocBook. (It might still be, though...)
            return false;
        } catch (IOException e) {
            System.err.println("Unable to read file: " + path);
            return false;
        }
    }

    public static boolean isDOCX(String path) {
        return path.endsWith(".docx");
    }

    public static boolean isODT(String path) {
        return path.endsWith(".odt");
    }

    public static String changeExtension(Path file, String extension) {
        return changeExtension(file.getFileName().toString(), extension);
    }

    public static String changeExtension(String file, String extension) {
        assert extension.startsWith(".");
        return file.replaceFirst("[.][^.]+$", "") + extension;
    }
}
