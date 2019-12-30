package be.tcuvelier.qdoctools.core.utils;

public class QtVersion {
    private final int major;
    private final int minor;
    private final int patch;

    @SuppressWarnings("WeakerAccess")
    public QtVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    @SuppressWarnings("unused")
    public QtVersion(int major, int minor) {
        this(major, minor, 0);
    }

    public QtVersion(String str) {
        String[] parts = str.split("\\.");
        if (parts.length < 2) {
            throw new RuntimeException("Qt version has too few parts, must have at least two, " +
                    "has only " + parts.length + " in " + str);
        } else if (parts.length == 2) {
            this.major = Integer.parseInt(parts[0]);
            this.minor = Integer.parseInt(parts[1]);
            this.patch = 0;
        } else if (parts.length == 3) {
            this.major = Integer.parseInt(parts[0]);
            this.minor = Integer.parseInt(parts[1]);
            this.patch = Integer.parseInt(parts[2]);
        } else {
            throw new RuntimeException("Qt version has too many parts, must have at most three, " +
                    "has only " + parts.length + " in " + str);
        }
    }

    @SuppressWarnings("WeakerAccess")
    public String QT_VERSION_TAG() {
        return major + Integer.toString(minor);
    }

    public String QT_VER() {
        return major + "." + minor;
    }

    @SuppressWarnings("WeakerAccess")
    public String QT_VERSION() {
        return major + "." + minor + "." + patch;
    }
}
