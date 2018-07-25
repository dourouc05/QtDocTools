package io.github.dourouc05.qtdoctools.from.qdoc;

import java.io.IOException;
import java.nio.file.Path;

class ReadQdocconfException extends IOException {
    ReadQdocconfException(String module, Path originalFile, Throwable cause) {
        super("Problem while reading module's qdocconf: " + module + "; " +
                "reading from: " + originalFile.toString(), cause);
    }
}
