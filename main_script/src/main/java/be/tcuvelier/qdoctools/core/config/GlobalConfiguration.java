package be.tcuvelier.qdoctools.core.config;

import be.tcuvelier.qdoctools.core.exceptions.ConfigurationMissingField;

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class GlobalConfiguration extends AbstractConfiguration {
    private final String configName;

    public GlobalConfiguration(String file) throws FileNotFoundException {
        super(file);
        configName = file;

        // Check if this is really a global configuration, not an article configuration.
        if (config.get("qdoc") == null && config.get("dvp_toolchain") == null && config.get(
                "qdoctools_root") == null) {
            throw new IllegalArgumentException("A GlobalConfiguration object was built with an " +
                    "article configuration file.");
        }
    }

    public String getConfigName() {
        return configName;
    }

    public String getQDocLocation() throws ConfigurationMissingField {
        return getStringAttribute("qdoc");
    }

    public String getDvpKitUnix() throws ConfigurationMissingField {
        return getStringAttribute("dvp_toolchain");
    }

    public Path getDvpKitUnixPath() throws ConfigurationMissingField {
        return Paths.get(getDvpKitUnix());
    }

    public Path getDvpToolchainPath() throws ConfigurationMissingField {
        return getDvpKitUnixPath().getParent();
    }

    public Path getDvpPerlScriptsPath() throws ConfigurationMissingField {
        return getDvpToolchainPath().resolve("script").resolve("Perl");
    }

    public List<String> getCppCompilerIncludes() throws ConfigurationMissingField {
        // g++ -E -x c++ - -v
        // clang++ -E -x c++ - -v
        return getListStringAttribute("cpp_compiler_includes");
    }

    public List<String> getNdkIncludes() throws ConfigurationMissingField {
        return getListStringAttribute("ndk_includes");
    }

    public String getQtDocToolsRoot() throws ConfigurationMissingField {
        return getStringAttribute("qdoctools_root");
    }
}
