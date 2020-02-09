package be.tcuvelier.qdoctools.core.config;

import be.tcuvelier.qdoctools.core.exceptions.ConfigurationMissingField;

import java.io.IOException;
import java.nio.file.Path;

public class PerlPath {
    private final Configuration config;

    public PerlPath(Configuration config) {
        this.config = config;
    }

    public static boolean isPerlInPath() {
        // Based on https://stackoverflow.com/questions/934191/how-to-check-existence-of-a-program-in-the-path/38073998#38073998.
        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("windows");
        ProcessBuilder pb = new ProcessBuilder(isWindows ? "where" : "which", "perl");

        try {
            Process proc = pb.start();
            return proc.waitFor() == 0;
        } catch (IOException | InterruptedException ex) {
            return false;
        }
    }

    public Path getToolchainPerlPath() throws ConfigurationMissingField {
        return config.getDvpToolchainPath().resolve("langdvp").resolve("perl").resolve("perl").resolve("bin").resolve("perl.exe");
    }

    public String getPerlPath() {
        // Always first try to use the toolchain's Perl version, as it is more or less ensured to work.
        try {
            Path toolchain = getToolchainPerlPath();
            if (toolchain.toFile().exists()) {
                return toolchain.toString();
            }
        } catch (ConfigurationMissingField ignored) {}

        // Either the configuration is missing or Perl is missing: try to use system Perl.
        if (isPerlInPath()) {
            return "perl";
        }

        throw new RuntimeException("Impossible to find an installation of Perl. Did you configure QtDocTools properly," +
                "especially its dvp_toolchain parameter in " + config.getConfigName() + "?");
    }
}
