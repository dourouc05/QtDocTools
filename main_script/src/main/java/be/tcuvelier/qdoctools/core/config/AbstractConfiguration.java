package be.tcuvelier.qdoctools.core.config;

import be.tcuvelier.qdoctools.core.exceptions.ConfigurationMissingField;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class AbstractConfiguration {
    protected final JsonObject config;

    protected AbstractConfiguration(String path) throws FileNotFoundException {
        this(Paths.get(path).toFile());
    }

    protected AbstractConfiguration(Path path) throws FileNotFoundException {
        this(path.toFile());
    }

    protected AbstractConfiguration(File file) throws FileNotFoundException {
        config = JsonParser.parseReader(new FileReader(file)).getAsJsonObject();
    }

    protected String getStringAttribute(String field) throws ConfigurationMissingField {
        JsonElement node = config.get(field);
        if (node == null) {
            throw new ConfigurationMissingField(field);
        }
        return node.getAsString();
    }

    protected Optional<String> getOptionalStringAttribute(String field) {
        try {
            return Optional.of(getStringAttribute(field));
        } catch (ConfigurationMissingField e) {
            return Optional.empty();
        }
    }

    protected Optional<Integer> getOptionalIntegerAttribute(String field) {
        String numberUnparsed = "";
        try {
            numberUnparsed = getStringAttribute(field);
            return Optional.of(Integer.parseInt(numberUnparsed));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("The provided field " + field + " is not an integer: " + numberUnparsed);
        } catch (ConfigurationMissingField e) {
            return Optional.empty();
        }
    }

    protected List<String> getListStringAttribute(String field) throws ConfigurationMissingField {
        JsonElement node = config.get(field);
        if (node == null) {
            throw new ConfigurationMissingField(field);
        }
        JsonArray array = node.getAsJsonArray();
        List<String> list = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); ++i) {
            list.add(array.get(i).getAsString());
        }
        return list;
    }
}
