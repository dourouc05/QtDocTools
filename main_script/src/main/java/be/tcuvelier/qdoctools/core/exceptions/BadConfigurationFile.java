package be.tcuvelier.qdoctools.core.exceptions;

import java.io.IOException;

public class BadConfigurationFile extends IOException {
    public BadConfigurationFile(String field) {
        this(field, new NullPointerException());
    }

    @SuppressWarnings("WeakerAccess")
    public BadConfigurationFile(String field, Throwable cause) {
        super("Problem while reading configuration file: field " + field + " has not been found", cause);
    }
}
