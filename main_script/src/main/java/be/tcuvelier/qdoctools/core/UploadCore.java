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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private static void askForFtpPasswordIfNeeded(ArticleConfiguration articleConfig) throws IOException {
        if (articleConfig.needsFtpPassword() && articleConfig.getFtpPassword().isEmpty()) {
            askForFtpPassword(articleConfig);
        }
    }

    private static String outputFolder() throws IOException {
        return Files.createTempDirectory("qdt").toString();
    }

    public static void callRelated(String input, boolean upload, GlobalConfiguration config) throws IOException, InterruptedException {
        // Generates and possibly uploads a list of related articles.

        // Perform generation with Dvp toolchain (not the same tool as HTML).
        String output = outputFolder();
        DvpToolchainHandler.generateRelated(input, output, config);

        // Upload if required.
        if (upload) {
            ArticleConfiguration articleConfig = new ArticleConfiguration(input + "/related.json");
            askForFtpPasswordIfNeeded(articleConfig);
            new FtpHandler(articleConfig).uploadDvpArticle(articleConfig, output);
        }
    }

    public static void callRelatedAndSubarticles(String input, boolean upload, GlobalConfiguration config) throws IOException, SaxonApiException, InterruptedException {
        // Generates and possibly uploads a list of related articles, and the articles contained in this list.

        // Work on the list of related articles.
        callRelated(input, upload, config);
    }

    public static void call(String input, boolean upload, GlobalConfiguration config)
            throws IOException, SaxonApiException, InterruptedException {
        // Generates and possibly uploads an article.
        call(List.of(input), upload, config);
    }

    public static void call(List<String> inputs, boolean upload, GlobalConfiguration config)
            throws IOException, SaxonApiException, InterruptedException {
        // Like call(), but for a list of articles. There are no predefined output paths.

        // Perform generation with Dvp toolchain.
        Map<String, String> outputs = new HashMap<>();
        for (String input : inputs) {
            String output = outputFolder();
            DvpToolchainHandler.generateHTML(input, output, config);
            outputs.put(input, output);
        }

        // Upload if required.
        // TODO: check whether all these articles share the same FTP (they should)? If so, start the FTP connection with any one of them, and reuse it.
        if (upload) {
            for (String input : inputs) {
                ArticleConfiguration articleConfig = new ArticleConfiguration(input);
                askForFtpPasswordIfNeeded(articleConfig);
                new FtpHandler(articleConfig).uploadDvpArticle(articleConfig, outputs.get(input));
            }
        }
    }
}
