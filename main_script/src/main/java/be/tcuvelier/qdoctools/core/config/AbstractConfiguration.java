package be.tcuvelier.qdoctools.core.config;

import be.tcuvelier.qdoctools.core.exceptions.BadConfigurationFile;
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

    protected String getStringAttribute(String field) throws BadConfigurationFile {
        JsonElement node = config.get(field);
        if (node == null) {
            throw new BadConfigurationFile(field);
        }
        return node.getAsString();
    }

    protected List<String> getListStringAttribute(String field) throws BadConfigurationFile {
        JsonElement node = config.get(field);
        if (node == null) {
            throw new BadConfigurationFile(field);
        }
        JsonArray array = node.getAsJsonArray();
        List<String> list = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); ++i) {
            list.add(array.get(i).getAsString());
        }
        return list;
    }
}
