package be.tcuvelier.qdoctools.helpers;

import be.tcuvelier.qdoctools.Main;
import be.tcuvelier.qdoctools.utils.ValidationHandler;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class ValidationHelper {
    public static boolean validateDvpML(String file) throws IOException, SAXException {
        return ValidationHandler.validateXSD(new File(file), Main.dvpMLXSDPath);
    }

    public static boolean validateDvpML(Path file) throws IOException, SAXException {
        return ValidationHandler.validateXSD(file.toFile(), Main.dvpMLXSDPath);
    }

    public static boolean validateDocBook(String file) throws IOException, SAXException {
        return validateDocBook(new File(file));
    }

    public static boolean validateDocBook(Path file) throws IOException, SAXException {
        return validateDocBook(file.toFile());
    }

    public static boolean validateDocBook(File file) throws IOException, SAXException {
        return ValidationHandler.validateRNG(file, Main.docBookRNGPath);
    }
}
