package be.tcuvelier.qdoctools.core.handlers;

import be.tcuvelier.qdoctools.core.config.ArticleConfiguration;
import be.tcuvelier.qdoctools.core.exceptions.ConfigurationMissingField;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPReply;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public class FtpHandler {
    private final String user;
    private final String password;
    private final String server;
    private final int port;

    private FTPClient ftp;

    public FtpHandler(String user, String password, String server, int port) {
        this.user = user;
        this.password = password;
        this.server = server;
        this.port = port;
    }

    public FtpHandler(String user, String password, String server) {
        this(user, password, server, 21);
    }

    public FtpHandler(ArticleConfiguration config) throws ConfigurationMissingField {
        // Some calls in the first branch of the if could, in theory, throw ConfigurationMissingField
        // because there is no user and no password. In practice, needsFtpPassword() checks for their presence,
        // so that it is only possible with the server.
        if (config.needsFtpPassword() && config.getFtpUser().isPresent() && config.getFtpPassword().isPresent()) {
            this.user = config.getFtpUser().get();
            this.password = config.getFtpPassword().get();
            this.server = config.getFtpServer();
            this.port = config.getFtpPort();
        } else {
            this.user = "";
            this.password = "";
            this.server = config.getFtpServer();
            this.port = config.getFtpPort();
        }
    }

    public void connect() throws IOException {
        ftp = new FTPClient();
        FTPClientConfig ftpConfig = new FTPClientConfig();
        ftp.configure(ftpConfig);

        ftp.connect(server, port);
        int reply = ftp.getReplyCode();
        if(!FTPReply.isPositiveCompletion(reply)) {
            disconnect();
            throw new IOException("Unable to connect to the server: server refused connection. " + ftp.getReplyString());
        }

        if (user.length() > 0) {
            if (!ftp.login(user, password)) {
                disconnect();
                throw new IOException("Unable to connect to the server: credentials not recognised. " + ftp.getReplyString());
            }
        }
    }

    public void disconnect() throws IOException {
        ftp.logout();
        ftp.disconnect();
        ftp = null;
    }

    public void changeDirectory(Path path) throws IOException {
        for (Path name : path) {
            if (!ftp.changeWorkingDirectory(name.toString())) {
                throw new IOException("Unable to change folder. " + ftp.getReplyString());
            }
        }
    }

    public void createDirectory(String name) throws IOException {
        if (!ftp.makeDirectory(name)) {
            throw new IOException("Unable to create a folder. " + ftp.getReplyString());
        }
    }

    public void changeAndCreateDirectory(Path path) throws IOException {
        for (Path name : path) {
            if (!ftp.changeWorkingDirectory(name.toString())) {
                // This will throw an exception if it's not possible to create the directory.
                createDirectory(name.toString());
                changeDirectory(name);
            }
        }
    }

    public void sendBinaryFile(String remote, Path local) throws IOException {
        sendFile(remote, local, FTPClient.BINARY_FILE_TYPE);
    }

    public void sendTextFile(String remote, Path local) throws IOException {
        sendFile(remote, local, FTPClient.ASCII_FILE_TYPE);
    }

    private void sendFile(String remote, Path local, int type) throws IOException {
        sendFile(remote, new FileInputStream(local.toFile()), type);
    }

    private void sendFile(String remote, InputStream local, int type) throws IOException {
        ftp.setFileType(type);
        ftp.storeFile(remote, local);

        if (!ftp.completePendingCommand()) {
            throw new IOException("Unable to complete the upload. " + ftp.getReplyString());
        }
    }
}
