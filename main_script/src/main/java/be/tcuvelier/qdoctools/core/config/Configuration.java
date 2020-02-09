package be.tcuvelier.qdoctools.core.config;

import be.tcuvelier.qdoctools.core.exceptions.BadConfigurationFile;

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Configuration extends AbstractConfiguration {
    private final String configName;

    public Configuration(String file) throws FileNotFoundException {
        super(file);
        configName = file;
    }

    public String getConfigName() {
        return configName;
    }

    public String getQdocLocation() throws BadConfigurationFile {
        return getStringAttribute("qdoc");
    }

    public String getDvpKitUnix() throws BadConfigurationFile {
        return getStringAttribute("dvp_toolchain");
    }

    public Path getDvpKitUnixPath() throws BadConfigurationFile {
        return Paths.get(getDvpKitUnix());
    }

    public Path getDvpToolchainPath() throws BadConfigurationFile {
        return getDvpKitUnixPath().getParent();
    }

    public Path getDvpPerlScriptsPath() throws BadConfigurationFile {
        return getDvpToolchainPath().resolve("script").resolve("Perl");
    }

    public List<String> getCppCompilerIncludes() throws BadConfigurationFile {
        // g++ -E -x c++ - -v
        // clang++ -E -x c++ - -v
        return getListStringAttribute("cpp_compiler_includes");
    }

    public List<String> getNdkIncludes() throws BadConfigurationFile {
        return getListStringAttribute("ndk_includes");
    }

    public String getQtDocToolsRoot() throws BadConfigurationFile {
        return getStringAttribute("qdoctools_root");
    }
}
