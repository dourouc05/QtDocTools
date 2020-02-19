package be.tcuvelier.qdoctools.core.config;

import be.tcuvelier.qdoctools.core.exceptions.ConfigurationMissingField;

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Optional;

public class ArticleConfiguration extends AbstractConfiguration {
    private final Path articleName;
    private final Path configName;

    public static Path getConfigurationFileName(String file) {
        Path articleName = Paths.get(file);
        Path parent = articleName.getParent();
        String filename = articleName.getFileName().toString();
        String fileRoot = filename.substring(0, filename.lastIndexOf('.'));

        // Try just appending the extension.
        Path potentialPath = parent.resolve(fileRoot + ".json");
        if (potentialPath.toFile().exists()) {
            return potentialPath;
        }

        // Maybe there is a suffix to remove?
        String fileRootSuffixless = fileRoot.replace("_dvp", "");
        potentialPath = parent.resolve(fileRootSuffixless + ".json");
        if (potentialPath.toFile().exists()) {
            return potentialPath;
        }

        // Give up.
        return null;
    }

    public static String parseConfigurationFileFromXml(String file) {
        // TODO:
        return "{}";
    }

    public static String proposeConfigurationFile() {
        return "{\n" +
                "\t\"section\": 1,\n" +
                "\t\"license-author\": \"\",\n" +
                "\t\"license-year\": " + Calendar.getInstance().get(Calendar.YEAR) + ",\n" +
                "\t\"license-number\": 1,\n" +
                "\t\"license-text\": \"\",\n" +
                "\t\"forum-topic\": -1,\n" +
                "\t\"forum-post\": -1,\n" +
                "\t\"ftp-server\": \"\",\n" +
                "\t\"ftp-user\": \"\",\n" +
                "\t\"ftp-port\": \"\",\n" +
                "\t\"ftp-folder\": \"\",\n" +
                "\t\"google-analytics\": \"\"\n" +
                "}";
    }

    public ArticleConfiguration(String file) throws FileNotFoundException {
        super(getConfigurationFileName(file));
        articleName = Paths.get(file);
        configName = getConfigurationFileName(file);
    }

    public boolean getDocQt() {
        try {
            return Boolean.parseBoolean(getStringAttribute("doc-qt"));
        } catch (ConfigurationMissingField e) {
            return false;
        }
    }

    public Optional<String> getGoogleAnalytics() {
        return getOptionalStringAttribute("google-analytics");
    }

    public int getSection() {
        try {
            return Integer.parseInt(getStringAttribute("section"));
        } catch (ConfigurationMissingField e) {
            return 1;
        }
    }

    public Optional<String> getLicenseAuthor() {
        return getOptionalStringAttribute("license-author");
    }

    public Optional<Integer> getLicenseYear() {
        return getOptionalIntegerAttribute("license-year");
    }

    public Optional<Integer> getLicenseNumber() {
        return getOptionalIntegerAttribute("license-number");
    }

    public Optional<String> getLicenseText() {
        return getOptionalStringAttribute("license-text");
    }

    public String getFtpServer() throws ConfigurationMissingField {
        return getStringAttribute("ftp-server");
    }

    public Optional<String> getFtpUser() {
        return getOptionalStringAttribute("ftp-user");
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
}
