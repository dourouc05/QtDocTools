package be.tcuvelier.qdoctools.core.exceptions;

import java.io.IOException;

public class BadConfigurationFile extends IOException {
    public BadConfigurationFile() {
        super();
    }

    public BadConfigurationFile(String message) {
        super(message);
    }

    public BadConfigurationFile(String message, Throwable cause) {
        super(message, cause);
    }

    public BadConfigurationFile(Throwable cause) {
        super(cause);
    }
}
