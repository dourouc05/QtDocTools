package be.tcuvelier.qdoctools.core;

import be.tcuvelier.qdoctools.core.handlers.DvpToolchainHandler;
import be.tcuvelier.qdoctools.core.handlers.FtpHandler;
import be.tcuvelier.qdoctools.core.config.ArticleConfiguration;
import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import net.sf.saxon.s9api.SaxonApiException;
import org.netbeans.api.keyring.Keyring;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

public class UploadCore {
    public static void call(String input, String folder, boolean upload, String configurationFile)
            throws IOException, SaxonApiException, InterruptedException {
        GlobalConfiguration config = new GlobalConfiguration(configurationFile);

        // Find the configuration file for the operation to perform (contains info about uploading).
        ArticleConfiguration articleConfig = new ArticleConfiguration(input);

        // Perform generation with Dvp toolchain.
        String output = folder.isEmpty() ? Files.createTempDirectory("qdt").toString() : folder;
        DvpToolchainHandler.generateHTML(input, output, config);

        // Upload if required
        if (upload) {
            // Get FTP configuration from this article's configuration file and the keyring.
            String server = articleConfig.getFtpServer();
            String user = articleConfig.getFtpUser().orElse("");
            int port = articleConfig.getFtpPort();
            Optional<String> password = Optional.empty();

            if (user.length() > 0) {
                password = articleConfig.getFtpPassword();
                if (password.isEmpty()) {
                    System.out.println("Please enter your FTP password for the user " + user + ".");
                    System.out.println("It will be safely stored in your operating system's keyring.");
                    System.out.println("When entering your password, it won't display, as a safety measure.");

                    password = Optional.of(new String(System.console().readPassword()));
                    articleConfig.setFtpPassword(password.get());
                }
            }

            // Perform the upload.
            FtpHandler ftp = new FtpHandler(user, password.orElse(""), server, port);
            ftp.connect();
            ftp.changeAndCreateDirectory(Paths.get(articleConfig.getFtpFolder()));
            // TODO
        }
    }
}
