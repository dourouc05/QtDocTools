package be.tcuvelier.qdoctools.core;

import be.tcuvelier.qdoctools.core.handlers.DvpToolchainHandler;
import be.tcuvelier.qdoctools.core.handlers.FtpHandler;
import be.tcuvelier.qdoctools.core.utils.Configuration;
import net.sf.saxon.s9api.SaxonApiException;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.netbeans.api.keyring.Keyring;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class UploadCore {
    public static void call(String input, String folder, boolean upload)
            throws IOException, SaxonApiException, InterruptedException {
        // Find the configuration file.
        String configurationFile = null;

        // Hand over to the general case.
        call(input, folder, upload, configurationFile);
    }

    public static void call(String input, String folder, boolean upload, String configurationFile)
            throws IOException, SaxonApiException, InterruptedException {
        Configuration config = new Configuration(configurationFile);

        // Find the configuration file for the operation to perform (contains info about uploading).
        Configuration articleConfig = null;

        // Perform generation with Dvp toolchain.
        String output = folder.isEmpty() ? Files.createTempDirectory("qdt").toString() : folder;
        DvpToolchainHandler.generateHTML(input, output, config);

        // Upload if required
        if (upload) {
            // Get FTP configuration from this article's configuration file and the keyring.
            String server = articleConfig.(...);
            String user = articleConfig.(...);
            int port = articleConfig.(...);
            String passwordKey = "qtdoctools-upload-ftp-password:server:" + server + ":user:" + user;
            char[] pwd_char = Keyring.read(passwordKey);
            String password = "";

            if (user.length() > 0) {
                if (pwd_char == null) {
                    System.out.println("Please enter your FTP password for the user " + user + ".");
                    System.out.println("It will be safely stored in your operating system's keyring.");
                    System.out.println("When entering your password, it won't display, as a safety measure.");

                    password = new String(System.console().readPassword());
                    Keyring.save(passwordKey, password.toCharArray(),
                            "Password used by QtDocTools to upload files to the FTP server " + server + " with the user " + user + ".");
                } else {
                    password = new String(pwd_char);
                }
            }

            // Perform the upload.
            FtpHandler ftp = new FtpHandler(user, password, server, port);
            ftp.connect();
            ftp.changeAndCreateDirectory(articleConfig.(...));
            // TODO
        }
    }
}
