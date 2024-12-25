package be.tcuvelier.qdoctools.core.helpers;

import be.tcuvelier.qdoctools.core.TransformCore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class FileHelpers {
    public static boolean isDvpML(String path) {
        return path.endsWith(".xml") && !isDocBook(path);
    }

    public static boolean isDocBook(String path) {
        // Check the extension: it is sometimes sufficient to conclude.
        boolean extensionOK = path.endsWith(".db") || path.endsWith(".dbk") || path.endsWith(
                ".xml");
        if (extensionOK) {
            return true;
        }

        // Check the contents of the file if it ends with ".xml".
        try {
            // Must find a reference to the DocBook name space near the top.
            // A cleaner way would be to read parse whole XML document and look at the name
            // spaces, but that may
            // take quite a while.
            // The name space declaration can be quite far, beyond the first ten lines:
            // https://docbook.org/docs/howto/howto.xml.
            boolean foundXMLNS = false;
            boolean foundDB = false;

            String[] content;
            try {
                content = Files.lines(Paths.get(path)).toArray(String[]::new);
            } catch (UncheckedIOException e) {
                // Mostly happens when the input is far from ASCII, like a DOCX/ODT file (ZIP
                // header).
                return false;
            }

            for (String s : content) {
                if (!foundXMLNS && s.contains("xmlns")) {
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

    public static boolean isRelated(String path) {
        Path file = Paths.get(path);
        return file.toFile().isDirectory() && file.resolve("related.json").toFile().exists();
    }

    public static TransformCore.Format parseFileFormat(TransformCore.Format format,
            String filename) {
        if (format != TransformCore.Format.Default) {
            return format;
        }

        if (FileHelpers.isDocBook(filename)) {
            return TransformCore.Format.DocBook;
        } else if (FileHelpers.isDOCX(filename)) {
            return TransformCore.Format.DOCX;
        } else if (FileHelpers.isDvpML(filename)) {
            return TransformCore.Format.DvpML;
        } else if (FileHelpers.isODT(filename)) {
            return TransformCore.Format.ODT;
        } else {
            throw new RuntimeException("File format not recognised for input file!");
        }
    }

    public static TransformCore.Format parseOutputFileFormat(TransformCore.Format input,
            TransformCore.Format output) {
        if (output != TransformCore.Format.Default) {
            return output;
        }

        if (input == TransformCore.Format.DocBook) {
            return TransformCore.Format.DOCX;
        } else {
            return TransformCore.Format.DocBook;
        }
    }

    public static String removeExtension(Path file) {
        return removeExtension(file.getFileName().toString());
    }

    public static String removeExtension(String file) {
        return file.replaceFirst("[.][^.]+$", "");
    }

    public static String changeExtension(Path file, String extension) {
        return changeExtension(file.getFileName().toString(), extension);
    }

    public static String changeExtension(String file, String extension) {
        assert extension.startsWith(".");
        return removeExtension(file) + extension;
    }

    public static String generateOutputFilename(Path input, TransformCore.Format outputFormat) {
        return generateOutputFilename(input.toString(), outputFormat);
    }

    public static String generateOutputFilename(String input, TransformCore.Format outputFormat) {
        // Specific handling for collisions between DocBook and DvpML: add a suffix (just before
        // the extension).
        if (input.endsWith("_dvp.xml")) {
            input = input.replace("_dvp.xml", ".xml");
        } else if (input.endsWith("_db.xml")) {
            input = input.replace("_db.xml", ".xml");
        }

        // Specific handling of related: the input can be just a directory.
        Path file = Paths.get(input);
        if (file.toFile().isDirectory()) {
            return file.resolve("related.xml").toString();
        }

        // Change the extension based on the target format.
        if (outputFormat == TransformCore.Format.DOCX) {
            return changeExtension(input, ".docx");
        } else if (outputFormat == TransformCore.Format.ODT) {
            return changeExtension(input, ".odt");
        } else if (outputFormat == TransformCore.Format.DvpML) {
            String output = changeExtension(input, ".xml");

            if (input.equals(output)) {
                output = output.substring(0, output.length() - 4) + "_dvp.xml";
            }

            return output;
        } else if (outputFormat == TransformCore.Format.DocBook) {
            String output = changeExtension(input, ".xml");

            if (input.equals(output)) {
                output = output.substring(0, output.length() - 4) + "_db.xml";
            }

            return output;
        }

        // Format not found. This is mostly a Java requirement...
        throw new IllegalArgumentException("Format not recognised when generating a new file name" +
                ".");
    }
}
