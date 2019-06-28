package be.tcuvelier.qdoctools.utils;

import be.tcuvelier.qdoctools.exceptions.BadConfigurationFile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

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

    private List<String> getListStringAttribute(String field) throws BadConfigurationFile {
        JsonElement node = config.getAsJsonObject().get(field);
        if (node == null) {
            throw new BadConfigurationFile(field);
        }
        JsonArray array = node.getAsJsonArray();
        List<String> list = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); ++i) {
            list.set(i, array.get(i).getAsString());
        }
        return list;
    }

    public String getQdocLocation() throws BadConfigurationFile {
        return getStringAttribute("qdoc");
    }

    public String getKitUnixLocation() throws BadConfigurationFile {
        return getStringAttribute("kit_unix");
    }

    public List<String> getCppCompilerIncludes() throws BadConfigurationFile {
        return getListStringAttribute("cpp_compiler_includes");
    }
}
