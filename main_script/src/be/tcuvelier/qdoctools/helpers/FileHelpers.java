package be.tcuvelier.qdoctools.helpers;

import java.nio.file.Path;

public class FileHelpers {
    public static boolean isDvpML(String path) {
        return path.endsWith(".xml");
    }

    public static boolean isDocBook(String path) {
        return path.endsWith(".db") || path.endsWith(".dbk") || path.endsWith(".qdt");
    }

    public static boolean isWebXML(String path) {
        return path.endsWith(".webxml");
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
        return file.replaceFirst("[.][^.]+$", "") + extension;
    }
}
