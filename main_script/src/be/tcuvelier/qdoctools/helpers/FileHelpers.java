package be.tcuvelier.qdoctools.helpers;

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
}
