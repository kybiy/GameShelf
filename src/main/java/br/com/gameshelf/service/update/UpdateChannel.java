package br.com.gameshelf.service.update;

/** Release update channels selectable by the user. */
public enum UpdateChannel {
    STABLE("stable"),
    EXPERIMENTAL("experimental");

    private final String code;

    UpdateChannel(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static UpdateChannel defaultForVersion(String currentVersion) {
        return VersionComparator.stage(currentVersion) == VersionComparator.Stage.STABLE
                ? STABLE
                : EXPERIMENTAL;
    }

    public static UpdateChannel fromCode(String code, String currentVersion) {
        if (code == null || code.isBlank()) return defaultForVersion(currentVersion);
        for (UpdateChannel ch : values()) {
            if (ch.code.equalsIgnoreCase(code.strip())) return ch;
        }
        return defaultForVersion(currentVersion);
    }
}
