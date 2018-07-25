package io.github.dourouc05.qtdoctools.from.qdoc;

import java.io.IOException;
import java.nio.file.Path;

class WriteQdocconfException extends IOException {
    WriteQdocconfException(String module, Path originalFile, Path destinationFile, Throwable cause) {
        super("Problem while writing module's qdocconf: " + module + "; " +
                "reading from: " + originalFile.toString() + "; " +
                "writing to: " + destinationFile.toString(), cause);
    }
}
