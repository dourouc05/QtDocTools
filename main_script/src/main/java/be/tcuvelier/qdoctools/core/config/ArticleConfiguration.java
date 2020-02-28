package be.tcuvelier.qdoctools.core.config;

import be.tcuvelier.qdoctools.core.exceptions.ConfigurationMissingField;
import org.netbeans.api.keyring.Keyring;

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Optional;

public class ArticleConfiguration extends AbstractConfiguration {
    public static class Helpers {
        public static Path getConfigurationFileName(String file) {
            Path path = Paths.get(file);

            // Related files are their own configuration files.
            if (path.getFileName().endsWith("related.json")) {
                return path;
            }
            if (path.toFile().isDirectory()) {
                return path.resolve("related.json");
            }

            // Most usual cases.
            Path parent = path.getParent();
            String filename = path.getFileName().toString();
            String fileRoot = filename.substring(0, filename.lastIndexOf('.'));

            // If there is a file suffix, remove it.
            if (filename.endsWith("_dvp.xml")) {
                String fileRootSuffixless = fileRoot.replace("_dvp", "");
                return parent.resolve(fileRootSuffixless + ".json");
            }

            // Otherwise, just append the extension.
            return parent.resolve(fileRoot + ".json");
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
    }

    private static class RootArticleConfiguration extends AbstractConfiguration {
        RootArticleConfiguration(Path root) throws FileNotFoundException {
            super(root);
        }

        private static Optional<RootArticleConfiguration> getRootConfig(Path configName) throws FileNotFoundException {
            final Optional<Path> rootConfig = recursiveFindFile(configName, "root.json");
            if (rootConfig.isPresent()) {
                return Optional.of(new RootArticleConfiguration(rootConfig.get()));
            } else {
                return Optional.empty();
            }
        }
    }

    private static class RelatedArticleConfiguration extends AbstractConfiguration {
        RelatedArticleConfiguration(Path related) throws FileNotFoundException {
            super(related);
        }

        private static Optional<RelatedArticleConfiguration> getRelatedConfig(Path configName) throws FileNotFoundException {
            final Optional<Path> relatedConfig = recursiveFindFile(configName, "related.json");
            if (relatedConfig.isPresent()) {
                return Optional.of(new RelatedArticleConfiguration(relatedConfig.get()));
            } else {
                return Optional.empty();
            }
        }
    }

    private final Path articleName;
    private final Path configName;
    private final Optional<RootArticleConfiguration> root;
    private final Optional<RelatedArticleConfiguration> related;

    public ArticleConfiguration(String file) throws FileNotFoundException {
        super(Helpers.getConfigurationFileName(file));
        articleName = Paths.get(file);
        configName = Helpers.getConfigurationFileName(file);

        root = RootArticleConfiguration.getRootConfig(configName);
        related = RelatedArticleConfiguration.getRelatedConfig(configName);

        // Check if this is really an article configuration, not a global configuration.
        if (config.get("qdoc") != null || config.get("dvp_toolchain") != null || config.get("qdoctools_root") != null) {
            throw new IllegalArgumentException("An ArticleConfiguration object was built with a global configuration file.");
        }
    }

    private Optional<String> getOptionalStringAttributeOrRoot(@SuppressWarnings("SameParameterValue") String field) {
        Optional<String> value = getOptionalStringAttribute(field);
        if (value.isPresent()) {
            return value;
        }

        if (root.isPresent()) {
            value = root.get().getOptionalStringAttribute(field);
        }

        return value;
    }

    private String getStringAttributeOrRoot(String field) throws ConfigurationMissingField {
        Optional<String> value = getOptionalStringAttribute(field);
        if (value.isPresent()) {
            return value.get();
        }

        if (root.isPresent()) {
            return root.get().getStringAttribute(field);
        }

        throw new ConfigurationMissingField(field);
    }

    public boolean getDocQt() {
        try {
            return Boolean.parseBoolean(getStringAttributeOrRoot("doc-qt"));
        } catch (ConfigurationMissingField configurationMissingField) {
            return false;
        }
    }

    public Optional<String> getGoogleAnalytics() {
        return getOptionalStringAttributeOrRoot("google-analytics");
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
        return getStringAttributeOrRoot("ftp-server");
    }

    public String getFtpFolder() throws ConfigurationMissingField {
        return getStringAttributeOrRoot("ftp-folder");
    }

    public Optional<String> getFtpUser() {
        return getOptionalStringAttributeOrRoot("ftp-user");
    }

    public int getFtpPort() {
        try {
            return Integer.parseInt(getStringAttributeOrRoot("ftp-port"));
        } catch (ConfigurationMissingField e) {
            return 21;
        }
    }

    private static String getFtpPasswordKey(String server, String user) {
        return "qtdoctools-upload-ftp-password:server:" + server + ":user:" + user;
    }

    private static String getFtpPasswordDescription(String server, String user) {
        return "Password used by QtDocTools to upload files to the FTP server " + server + " with the user " + user + ".";
    }

    public boolean needsFtpPassword() {
        return getFtpUser().isPresent();
    }

    public Optional<String> getFtpPassword() throws ConfigurationMissingField {
        if (!needsFtpPassword()) {
            throw new ConfigurationMissingField("Missing field FTP user when retrieving the password.");
        }
        assert getFtpUser().isPresent(); // To help static analysis. Ensured by the previous condition.

        char[] password = Keyring.read(getFtpPasswordKey(getFtpServer(), getFtpUser().get()));
        if (password != null) {
            return Optional.of(new String(password));
        }
        return Optional.empty();
    }

    public void setFtpPassword(String password) throws ConfigurationMissingField {
        if (getFtpUser().isEmpty()) {
            throw new ConfigurationMissingField("Missing field FTP user when setting the password.");
        }

        Keyring.save(getFtpPasswordKey(getFtpServer(), getFtpUser().get()),
                password.toCharArray(),
                getFtpPasswordDescription(getFtpServer(), getFtpUser().get()));
    }

    public Optional<Integer> getForumTopic() {
        return getOptionalIntegerAttribute("forum-topic");
    }

    public Optional<Integer> getForumPost() {
        return getOptionalIntegerAttribute("forum-post");
    }
}
