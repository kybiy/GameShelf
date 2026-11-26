package br.com.gameshelf.service.update;

import java.util.Locale;

/** Semantic versioning comparator supporting stable, alpha, beta, and rc releases. */
public final class VersionComparator {
    public enum Stage { ALPHA, BETA, STABLE }

    private VersionComparator() {}

    public static String normalize(String version) {
        if (version == null) return "";
        String s = version.strip();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        int plus = s.indexOf('+');
        if (plus >= 0) s = s.substring(0, plus);
        return s;
    }

    public static Stage stage(String version) {
        String norm = normalize(version).toLowerCase(Locale.ROOT);
        int dash = norm.indexOf('-');
        if (dash < 0) return Stage.STABLE;
        String pre = norm.substring(dash + 1);
        if (pre.contains("alpha")) return Stage.ALPHA;
        if (pre.contains("beta") || pre.contains("rc")) return Stage.BETA;
        return Stage.BETA;
    }

    public static boolean isValid(String version) {
        return parse(version) != null;
    }

    /** Compares two SemVer strings; returns &lt; 0 if a &lt; b, 0 if equal, &gt; 0 if a &gt; b. */
    public static int compare(String a, String b) {
        Parsed pa = parse(a);
        Parsed pb = parse(b);
        if (pa == null && pb == null) return 0;
        if (pa == null) return -1;
        if (pb == null) return 1;

        int c = Integer.compare(pa.major, pb.major);
        if (c != 0) return c;
        c = Integer.compare(pa.minor, pb.minor);
        if (c != 0) return c;
        c = Integer.compare(pa.patch, pb.patch);
        if (c != 0) return c;

        if (pa.pre.length == 0 && pb.pre.length == 0) return 0;
        if (pa.pre.length == 0) return 1;
        if (pb.pre.length == 0) return -1;

        int len = Math.min(pa.pre.length, pb.pre.length);
        for (int i = 0; i < len; i++) {
            String sa = pa.pre[i];
            String sb = pb.pre[i];
            boolean na = isDigits(sa);
            boolean nb = isDigits(sb);
            if (na && nb) {
                int cmp = Long.compare(Long.parseLong(sa), Long.parseLong(sb));
                if (cmp != 0) return cmp;
            } else if (na != nb) {
                return na ? -1 : 1;
            } else {
                int cmp = sa.compareToIgnoreCase(sb);
                if (cmp != 0) return cmp;
            }
        }
        return Integer.compare(pa.pre.length, pb.pre.length);
    }

    private static boolean isDigits(String s) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    private record Parsed(int major, int minor, int patch, String[] pre) {}

    private static Parsed parse(String version) {
        String norm = normalize(version);
        if (norm.isEmpty()) return null;
        int dash = norm.indexOf('-');
        String core = dash < 0 ? norm : norm.substring(0, dash);
        String preRaw = dash < 0 ? "" : norm.substring(dash + 1);
        String[] parts = core.split("\\.");
        if (parts.length < 1 || parts.length > 3) return null;
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            if (major < 0 || minor < 0 || patch < 0) return null;
            String[] pre = preRaw.isEmpty() ? new String[0] : preRaw.split("\\.");
            return new Parsed(major, minor, patch, pre);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
