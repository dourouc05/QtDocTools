package be.tcuvelier.qdoctools.core.utils;

import be.tcuvelier.qdoctools.core.exceptions.BadConfigurationFile;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Configuration {
    private final String configName;
    private final JsonElement config;

    public Configuration(String file) throws FileNotFoundException {
        configName = file;
        config = JsonParser.parseReader(new FileReader(file));
    }

    private String getStringAttribute(String field) throws BadConfigurationFile {
        JsonElement node = config.getAsJsonObject().get(field);
        if (node == null) {
            throw new BadConfigurationFile(field);
        }
        return node.getAsString();
    }

    private List<String> getListStringAttribute(String field) throws BadConfigurationFile {
        JsonElement node = config.getAsJsonObject().get(field);
        if (node == null) {
            throw new BadConfigurationFile(field);
        }
        JsonArray array = node.getAsJsonArray();
        List<String> list = new ArrayList<>(array.size());
        for (int i = 0; i < array.size(); ++i) {
            list.add(array.get(i).getAsString());
        }
        return list;
    }

    public String getQdocLocation() throws BadConfigurationFile {
        return getStringAttribute("qdoc");
    }

    public String getKitUnix() throws BadConfigurationFile {
        return getStringAttribute("dvp_toolchain");
    }

    public Path getKitUnixPath() throws BadConfigurationFile {
        return Paths.get(getKitUnix());
    }

    public Path getToolchainPath() throws BadConfigurationFile {
        return getKitUnixPath().getParent();
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

    public Path getToolchainPerlPath() throws BadConfigurationFile {
        return getToolchainPath().resolve("langdvp").resolve("perl").resolve("perl").resolve("bin").resolve("perl.exe");
    }

    public String getPerlPath() {
        // Always first try to use the toolchain's Perl version, as it is more or less ensured to work.
        try {
            Path toolchain = getToolchainPerlPath();
            if (toolchain.toFile().exists()) {
                return toolchain.toString();
            }
        } catch (BadConfigurationFile ignored) {}

        // Either the configuration is missing or Perl is missing: try to use system Perl.
        if (isPerlInPath()) {
            return "perl";
        }

        throw new RuntimeException("Impossible to find an installatio of Perl. Did you configure QtDocTools properly," +
                "especially its dvp_toolchain parameter in " + configName + "?");
    }

    public Path getPerlScriptsPath() throws BadConfigurationFile {
        return getToolchainPath().resolve("script").resolve("Perl");
    }

    public List<String> getCppCompilerIncludes() throws BadConfigurationFile {
        // g++ -E -x c++ - -v
        // clang++ -E -x c++ - -v
        return getListStringAttribute("cpp_compiler_includes");
    }

    public List<String> getNdkIncludes() throws BadConfigurationFile {
        return getListStringAttribute("ndk_includes");
    }
}
