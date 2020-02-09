package be.tcuvelier.qdoctools.core.exceptions;

public class ConfigurationMissingField extends BadConfigurationFile {
    private static String generateMessage(String field) {
        return "Problem while reading configuration file: field " + field + " has not been found";
    }

    public ConfigurationMissingField(String field) {
        super(generateMessage(field));
    }

    @SuppressWarnings("WeakerAccess")
    public ConfigurationMissingField(String field, Throwable cause) {
        super(generateMessage(field), cause);
    }
}
