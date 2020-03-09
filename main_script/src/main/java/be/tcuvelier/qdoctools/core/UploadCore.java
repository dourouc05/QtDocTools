package be.tcuvelier.qdoctools.core;

import be.tcuvelier.qdoctools.core.exceptions.ConfigurationMissingField;
import be.tcuvelier.qdoctools.core.handlers.DvpToolchainHandler;
import be.tcuvelier.qdoctools.core.handlers.FtpHandler;
import be.tcuvelier.qdoctools.core.config.ArticleConfiguration;
import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import net.sf.saxon.s9api.SaxonApiException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
        String output = generateRelated(input, config);
        if (upload) {
            uploadRelated(input, output);
        }
    }

    public static void call(String input, boolean upload, GlobalConfiguration config)
            throws IOException, SaxonApiException, InterruptedException {
        call(Paths.get(input), upload, config);
    }

    public static void call(Path input, boolean upload, GlobalConfiguration config)
            throws IOException, SaxonApiException, InterruptedException {
        call(List.of(input), upload, config);
    }

    public static void call(List<Path> inputs, boolean upload, GlobalConfiguration config)
            throws IOException, SaxonApiException, InterruptedException {
        // Perform generation with Dvp toolchain.
        Map<Path, String> outputs = new HashMap<>();
        for (Path input : inputs) {
            System.out.println("Generating article: " + input);
            outputs.put(input, generateArticle(input, config));
        }

        // Upload if required.
        // TODO: check whether all these articles share the same FTP (they should)? If so, start the FTP connection with any one of them, and reuse it.
        if (upload) {
            for (Path input : inputs) {
                System.out.println("Uploading article: " + input);
                uploadArticle(input, outputs.get(input));
            }
        }
    }

    public static String generateArticle(String input, GlobalConfiguration config) throws IOException, SaxonApiException, InterruptedException {
        return generateArticle(Paths.get(input), config);
    }

    public static String generateArticle(Path input, GlobalConfiguration config) throws IOException, SaxonApiException, InterruptedException {
        String output = outputFolder();
        DvpToolchainHandler.generateHTML(input, output, config);
        return output;
    }

    public static String generateRelated(String input, GlobalConfiguration config) throws IOException, InterruptedException {
        String output = outputFolder();
        DvpToolchainHandler.generateRelated(input, output, config);
        return output;
    }

    public static FtpHandler connectFtp(ArticleConfiguration articleConfig) throws IOException {
        askForFtpPasswordIfNeeded(articleConfig);
        return new FtpHandler(articleConfig);
    }

    public static void uploadRelated(String input, String output) throws IOException {
        ArticleConfiguration articleConfig = new ArticleConfiguration(input + "/related.json");
        connectFtp(articleConfig).uploadDvpArticle(articleConfig, output);
    }

    public static void uploadArticle(String input, String output) throws IOException {
        uploadArticle(Paths.get(input), output);
    }

    public static void uploadArticle(Path input, String output) throws IOException {
        ArticleConfiguration articleConfig = new ArticleConfiguration(input);
        connectFtp(articleConfig).uploadDvpArticle(articleConfig, output);
    }
}
