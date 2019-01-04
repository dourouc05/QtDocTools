package be.tcuvelier.qdoctools.utils;

import be.tcuvelier.qdoctools.exceptions.BadConfigurationFile;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.FileNotFoundException;
import java.io.FileReader;

public class Configuration {
    private JsonElement config;

    public Configuration(String file) throws FileNotFoundException {
        config = new JsonParser().parse(new FileReader(file));
    }

    private String getStringAttribute(String field) throws BadConfigurationFile {
        JsonElement node = config.getAsJsonObject().get(field);
        if (node == null) {
            throw new BadConfigurationFile(field);
        }
        return node.getAsString();
    }

    public String getQtVersion() throws BadConfigurationFile {
        return getStringAttribute("qt_version");
    }

    public String getQdocLocation() throws BadConfigurationFile {
        return getStringAttribute("qdoc");
    }

    public String getKitUnixLocation() throws BadConfigurationFile {
        return getStringAttribute("kit_unix");
    }
}
