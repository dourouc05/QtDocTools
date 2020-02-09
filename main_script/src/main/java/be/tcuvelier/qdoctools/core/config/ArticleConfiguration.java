package be.tcuvelier.qdoctools.core.config;

import be.tcuvelier.qdoctools.core.exceptions.BadConfigurationFile;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class ArticleConfiguration extends AbstractConfiguration {
    private final Path articleName;
    private final Path configName;

    public static Path getConfigurationFileName(String file) {
        Path articleName = Paths.get(file);
        Path parent = articleName.getParent();
        String filename = articleName.getFileName().toString();
        String fileRoot = filename.substring(0, filename.lastIndexOf('.'));
        return parent.resolve(fileRoot + ".json");
    }

    public ArticleConfiguration(String file) throws FileNotFoundException {
        super(getConfigurationFileName(file));
        articleName = Paths.get(file);
        configName = getConfigurationFileName(file);
    }

    public String getFtpServer() throws BadConfigurationFile {
        return getStringAttribute("ftp-server");
    }

    public Optional<String> getFtpUser() {
        try {
            return Optional.of(getStringAttribute("ftp-user"));
        } catch (BadConfigurationFile e) {
            return Optional.empty();
        }
    }

    public int getFtpPort() {
        try {
            return Integer.parseInt(getStringAttribute("ftp-port"));
        } catch (BadConfigurationFile e) {
            return 21;
        }
    }

    public String getFtpFolder() throws BadConfigurationFile {
        return getStringAttribute("ftp-folder");
    }
}
