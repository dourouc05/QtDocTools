package io.github.dourouc05.qtdoctools.from.qdoc;

import java.io.IOException;
import java.nio.file.Path;

public class ReadQdocconfException extends IOException {
    public ReadQdocconfException(String module, Path originalFile, Throwable cause) {
        super("Problem while reading module's qdocconf: " + module + "; " +
                "reading from: " + originalFile.toString(), cause);
    }
}
