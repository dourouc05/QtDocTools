package be.tcuvelier.qdoctools.core;

import be.tcuvelier.qdoctools.core.handlers.DvpToolchainHandler;
import be.tcuvelier.qdoctools.core.handlers.FtpHandler;
import be.tcuvelier.qdoctools.core.config.ArticleConfiguration;
import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import net.sf.saxon.s9api.SaxonApiException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;

public class UploadCore {
    private static String readPassword() throws IOException {
        // Bypass limitation of IDEs.
        // https://stackoverflow.com/questions/4203646/system-console-returns-null

        if (System.console() != null) {
            return new String(System.console().readPassword());
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        return reader.readLine();
    }

    private static void askForFtpPassword(ArticleConfiguration articleConfig) throws IOException {
        assert articleConfig.getFtpUser().isPresent();

        String user = articleConfig.getFtpUser().get();
        System.out.println("Please enter your FTP password for the user " + user + ".");
        System.out.println("It will be safely stored in your operating system's keyring.");
        System.out.println("When entering your password, it won't display, as a safety measure.");

        articleConfig.setFtpPassword(readPassword());
    }

    private static String outputFolderOrCreate(String folder) throws IOException {
        return folder.isEmpty() ? Files.createTempDirectory("qdt").toString() : folder;
    }

    public static void callRelated(String input, String folder, boolean upload, GlobalConfiguration config) throws IOException, SaxonApiException, InterruptedException {
        // Perform generation with Dvp toolchain.
        String output = outputFolderOrCreate(folder);
        DvpToolchainHandler.generateHTML(input, output, config);

        // Upload if required
        if (upload) {
            ArticleConfiguration articleConfig = new ArticleConfiguration(input + "/related.json"); // TODO: merge with call(), this line is the only difference. 

            // Get FTP configuration from this article's configuration file and the keyring.
            if (articleConfig.needsFtpPassword() && articleConfig.getFtpPassword().isEmpty()) {
                askForFtpPassword(articleConfig);
            }

            // Perform the upload.
            FtpHandler ftp = new FtpHandler(articleConfig);
            ftp.uploadDvpArticle(articleConfig, output);
        }
    }

    public static void call(String input, String folder, boolean upload, GlobalConfiguration config)
            throws IOException, SaxonApiException, InterruptedException {
        // Perform generation with Dvp toolchain.
        String output = outputFolderOrCreate(folder);
        DvpToolchainHandler.generateHTML(input, output, config);

        // Upload if required
        if (upload) {
            ArticleConfiguration articleConfig = new ArticleConfiguration(input);

            // Get FTP configuration from this article's configuration file and the keyring.
            if (articleConfig.needsFtpPassword() && articleConfig.getFtpPassword().isEmpty()) {
                askForFtpPassword(articleConfig);
            }

            // Perform the upload.
            FtpHandler ftp = new FtpHandler(articleConfig);
            ftp.uploadDvpArticle(articleConfig, output);
        }
    }
}
