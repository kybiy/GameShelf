package br.com.gameshelf.service.diagnostics;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Redacts personal usernames, local user profile paths, API keys, tokens,
 * and emails before storing logs or exporting diagnostic reports.
 */
public final class DiagnosticSanitizer {
    private static final Pattern WIN_USER_PATH = Pattern.compile("(?i)[A-Z]:[\\\\/]Users[\\\\/][^\\\\/\\s\"']+");
    private static final Pattern UNIX_USER_PATH = Pattern.compile("(?i)/(?:Users|home)/[^/\\s\"']+");
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._\\-+/=]+");
    private static final Pattern KEY_VALUE_SECRET = Pattern.compile("(?i)(api[_-]?key|token|secret|password|passwd|gridDbKey)\\s*([:=])\\s*[^\\s,;\"']+");
    private static final Pattern GITHUB_TOKEN = Pattern.compile("\\b(?:gh[pousr]_[A-Za-z0-9_]{10,}|github_pat_[A-Za-z0-9_]{10,})\\b");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    private DiagnosticSanitizer() {}

    public static String sanitize(String text) {
        return sanitize(text, null);
    }

    public static String sanitize(String text, Collection<String> extraSecrets) {
        if (text == null || text.isEmpty()) return "";
        String out = text;

        if (extraSecrets != null) {
            for (String secret : extraSecrets) {
                if (secret != null && !secret.isBlank()) {
                    out = out.replace(secret, "[REDACTED]");
                }
            }
        }

        Map<String, String> envReplacements = new LinkedHashMap<>();
        putEnv(envReplacements, "LOCALAPPDATA", "%LOCALAPPDATA%");
        putEnv(envReplacements, "APPDATA", "%APPDATA%");
        putEnv(envReplacements, "ProgramData", "%ProgramData%");
        putEnv(envReplacements, "ProgramFiles(x86)", "%ProgramFiles(x86)%");
        putEnv(envReplacements, "ProgramFiles", "%ProgramFiles%");
        String userHome = System.getProperty("user.home", "");
        if (!userHome.isBlank()) envReplacements.put(userHome, "%USERPROFILE%");

        for (var entry : envReplacements.entrySet()) {
            out = replacePathInsensitive(out, entry.getKey(), entry.getValue());
        }

        out = WIN_USER_PATH.matcher(out).replaceAll("%USERPROFILE%");
        out = UNIX_USER_PATH.matcher(out).replaceAll("%USERPROFILE%");

        String username = System.getProperty("user.name", "");
        if (!username.isBlank() && username.length() >= 2) {
            out = out.replaceAll("(?i)(?<=[\\\\/])" + Pattern.quote(username) + "(?=[\\\\/]|$)", "%USERNAME%");
        }

        out = BEARER_TOKEN.matcher(out).replaceAll("$1[REDACTED]");
        out = KEY_VALUE_SECRET.matcher(out).replaceAll("$1$2[REDACTED]");
        out = GITHUB_TOKEN.matcher(out).replaceAll("[REDACTED]");
        out = EMAIL.matcher(out).replaceAll("[REDACTED]");
        return out;
    }

    private static void putEnv(Map<String, String> map, String envName, String placeholder) {
        String val = System.getenv(envName);
        if (val != null && !val.isBlank()) map.put(val, placeholder);
    }

    private static String replacePathInsensitive(String text, String rawPath, String placeholder) {
        String back = rawPath.replace('/', '\\');
        String forward = rawPath.replace('\\', '/');
        String result = text.replaceAll("(?i)" + Pattern.quote(back), placeholder.replace("\\", "\\\\"));
        return result.replaceAll("(?i)" + Pattern.quote(forward), placeholder);
    }
}
