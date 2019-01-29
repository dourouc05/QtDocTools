package be.tcuvelier.qdoctools.exceptions;

import java.io.IOException;
import java.nio.file.Path;

public class WriteQdocconfException extends IOException {
    public WriteQdocconfException(String module, Path originalFile, Path destinationFile, Throwable cause) {
        super("Problem while writing module's qdocconf: " + module + "; " +
                "reading from: " + originalFile.toString() + "; " +
                "writing to: " + destinationFile.toString(), cause);
    }

    public WriteQdocconfException(Path destinationFile, Throwable cause) {
        super("Problem while writing main qdocconf; " +
                "writing to: " + destinationFile.toString(), cause);
    }
}
