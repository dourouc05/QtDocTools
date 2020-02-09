package be.tcuvelier.qdoctools.core.config;

import be.tcuvelier.qdoctools.core.exceptions.ConfigurationMissingField;

import java.io.FileNotFoundException;
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

    public static String proposeConfigurationFile() {
        return "{\n" +
                "\t\"license-author\": \"\",\n" +
                "\t\"license-year\": \"\",\n" +
                "\t\"license-number\": \"\",\n" +
                "\t\"license-text\": \"\",\n" +
                "\t\"ftp-server\": \"\",\n" +
                "\t\"ftp-user\": \"\",\n" +
                "\t\"ftp-port\": \"\",\n" +
                "\t\"ftp-folder\": \"\"\n" +
                "}";
    }

    public ArticleConfiguration(String file) throws FileNotFoundException {
        super(getConfigurationFileName(file));
        articleName = Paths.get(file);
        configName = getConfigurationFileName(file);
    }

    public String getFtpServer() throws ConfigurationMissingField {
        return getStringAttribute("ftp-server");
    }

    public Optional<String> getFtpUser() {
        try {
            return Optional.of(getStringAttribute("ftp-user"));
        } catch (ConfigurationMissingField e) {
            return Optional.empty();
        }
    }

    public int getFtpPort() {
        try {
            return Integer.parseInt(getStringAttribute("ftp-port"));
        } catch (ConfigurationMissingField e) {
            return 21;
    }
    }

    public String getFtpFolder() throws ConfigurationMissingField {
        return getStringAttribute("ftp-folder");
    }

    public Optional<String> getLicenseAuthor() {
        try {
            return Optional.of(getStringAttribute("license-author"));
        } catch (ConfigurationMissingField e) {
            return Optional.empty();
        }
    }

    public Optional<Integer> getLicenseYear() {
        String yearUnparsed = "";
        try {
            yearUnparsed = getStringAttribute("license-year");
            return Optional.of(Integer.parseInt(yearUnparsed));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("The provided year is not an integer: " + yearUnparsed);
        } catch (ConfigurationMissingField e) {
            return Optional.empty();
        }
    }

    public Optional<Integer> getLicenseNumber() {
        String numberUnparsed = "";
        try {
            numberUnparsed = getStringAttribute("license-number");
            return Optional.of(Integer.parseInt(numberUnparsed));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("The provided year is not an integer: " + numberUnparsed);
        } catch (ConfigurationMissingField e) {
            return Optional.empty();
        }
    }

    public Optional<String> getLicenseText() {
        try {
            return Optional.of(getStringAttribute("license-text"));
        } catch (ConfigurationMissingField e) {
            return Optional.empty();
        }
    }
}
