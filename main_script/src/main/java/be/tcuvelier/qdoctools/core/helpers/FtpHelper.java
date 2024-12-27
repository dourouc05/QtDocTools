package be.tcuvelier.qdoctools.core.helpers;

import be.tcuvelier.qdoctools.core.config.GlobalConfiguration;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;

import java.io.*;

public class FtpHelper {
    private FtpHelper(String host, int port, String user, String password) throws IOException {
        ftpClient = new FTPClient();
        ftpClient.connect(host, port);
        final int reply = ftpClient.getReplyCode();
        if (!FTPReply.isPositiveCompletion(reply)) {
            ftpClient.disconnect();
            throw new IOException("Exception in connecting to FTP server " + host + " with reply code " + reply);
        }

        ftpClient.login(user, password);
        ftpClient.enterLocalPassiveMode();
    }

    public static FtpHelper fromQtDoc(GlobalConfiguration config) throws IOException {
        return new FtpHelper(
                config.getQtDocFtpServer(), config.getQtDocFtpPort(),
                config.getQtDocFtpUsername(), config.getQtDocFtpPassword()
        );
    }

    private void checkRemotePathIsAbsolute(String remotePath) throws IOException {
        if (!remotePath.startsWith("/")) {
            throw new IllegalArgumentException("remotePath must be an absolute path");
        }
    }

    public boolean makeDirectory(String remoteDirectory) throws IOException {
        checkRemotePathIsAbsolute(remoteDirectory);
        return ftpClient.makeDirectory(remoteDirectory);
    }

    public boolean uploadTextFile(File file, String remotePath) throws IOException {
        ftpClient.setFileType(FTP.ASCII_FILE_TYPE);
        return uploadFile(file, remotePath);
    }

    public boolean uploadBinaryFile(File file, String remotePath) throws IOException {
        ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
        return uploadFile(file, remotePath);
    }

    private boolean uploadFile(File file, String remotePath) throws IOException {
        checkRemotePathIsAbsolute(remotePath);
        try (InputStream inputStream = new FileInputStream(file)) {
            return ftpClient.storeFile(remotePath, inputStream);
        }
    }

    private final FTPClient ftpClient;
}
