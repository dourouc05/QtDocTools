package be.tcuvelier.qdoctools.core.helpers;

import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import be.tcuvelier.qdoctools.core.config.QdtPaths;
import be.tcuvelier.qdoctools.core.handlers.ValidationHandler;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class ValidationHelper {
    public static boolean validateDvpML(String file, GlobalConfiguration config) throws IOException, SAXException {
        return ValidationHandler.validateXSD(new File(file),
                new QdtPaths(config).getDvpMLXSDPath());
    }

    public static boolean validateDvpML(Path file, GlobalConfiguration config) throws IOException
            , SAXException {
        return ValidationHandler.validateXSD(file.toFile(), new QdtPaths(config).getDvpMLXSDPath());
    }

    public static boolean validateDocBook(String file, GlobalConfiguration config) throws IOException, SAXException {
        return validateDocBook(new File(file), config);
    }

    public static boolean validateDocBook(Path file, GlobalConfiguration config) throws IOException, SAXException {
        return validateDocBook(file.toFile(), config);
    }

    public static boolean validateDocBook(File file, GlobalConfiguration config) throws IOException, SAXException {
        return ValidationHandler.validateRNG(file, new QdtPaths(config).getDocBookRNGPath());
    }
}
