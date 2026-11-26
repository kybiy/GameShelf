package br.com.gameshelf.service.i18n;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

/**
 * Manages active locale, UTF-8 {@link ResourceBundle} lookup, and regional formatting
 * for dates, times, numbers, durations, and storage sizes.
 */
public final class LocalizationService {
    private static final String BUNDLE_BASE = "i18n.messages";
    private static final ResourceBundle.Control UTF8_NO_FALLBACK = new ResourceBundle.Control() {
        @Override
        public Locale getFallbackLocale(String baseName, Locale locale) {
            return null;
        }

        @Override
        public ResourceBundle newBundle(String baseName, Locale locale, String format,
                                        ClassLoader loader, boolean reload)
                throws IllegalAccessException, InstantiationException, IOException {
            String bundleName = toBundleName(baseName, locale);
            String resourceName = toResourceName(bundleName, "properties");
            URL url = loader.getResource(resourceName);
            if (url == null) return null;
            URLConnection conn = url.openConnection();
            if (reload) conn.setUseCaches(false);
            try (InputStream stream = conn.getInputStream();
                 InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return new PropertyResourceBundle(reader);
            }
        }
    };

    private final ObjectProperty<Locale> locale = new SimpleObjectProperty<>();
    private LanguagePreference preference;
    private volatile ResourceBundle bundle;

    public LocalizationService(LanguagePreference initialPreference) {
        this(initialPreference, Locale.getDefault());
    }

    public LocalizationService(LanguagePreference initialPreference, Locale systemDefault) {
        setPreference(initialPreference == null ? LanguagePreference.SYSTEM : initialPreference, systemDefault);
    }

    public synchronized void setPreference(LanguagePreference pref) {
        setPreference(pref, Locale.getDefault());
    }

    public synchronized void setPreference(LanguagePreference pref, Locale systemDefault) {
        this.preference = pref == null ? LanguagePreference.SYSTEM : pref;
        Locale resolved = this.preference.resolveLocale(systemDefault);
        this.bundle = ResourceBundle.getBundle(BUNDLE_BASE, resolved, UTF8_NO_FALLBACK);
        this.locale.set(resolved);
    }

    public synchronized LanguagePreference preference() {
        return preference;
    }

    public Locale locale() {
        return locale.get();
    }

    public ReadOnlyObjectProperty<Locale> localeProperty() {
        return locale;
    }

    /** Translates a message key using the active locale bundle. Returns {@code [key]} if missing. */
    public String t(String key) {
        if (key == null || key.isBlank()) return "";
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return "[" + key + "]";
        }
    }

    /** Translates and formats a message key with {@link String#format(Locale, String, Object...)}. */
    public String t(String key, Object... args) {
        String pattern = t(key);
        if (args == null || args.length == 0) return pattern;
        if (pattern.startsWith("[") && pattern.endsWith("]")) return pattern;
        return String.format(locale(), pattern, args);
    }

    public boolean hasKey(String key) {
        return key != null && bundle.containsKey(key);
    }

    public String platformName(String canonical) {
        if ("Local".equals(canonical)) return t("platform.local");
        if ("Outros".equals(canonical)) return t("platform.other");
        return canonical;
    }

    public String formatDate(Instant instant) {
        if (instant == null) return "—";
        Locale loc = locale();
        String pattern = loc.getLanguage().equals("pt") ? "dd MMM yyyy" : "MMM d, yyyy";
        return DateTimeFormatter.ofPattern(pattern, loc).withZone(ZoneId.systemDefault()).format(instant);
    }

    public String formatTime(Instant instant) {
        if (instant == null) return "—";
        Locale loc = locale();
        String pattern = loc.getLanguage().equals("pt") ? "HH:mm" : "h:mm a";
        return DateTimeFormatter.ofPattern(pattern, loc).withZone(ZoneId.systemDefault()).format(instant);
    }

    public String formatTimeWithSeconds(Instant instant) {
        if (instant == null) return "—";
        Locale loc = locale();
        return DateTimeFormatter.ofPattern("HH:mm:ss", loc).withZone(ZoneId.systemDefault()).format(instant);
    }

    public String formatDateTime(Instant instant) {
        if (instant == null) return "—";
        Locale loc = locale();
        String pattern = loc.getLanguage().equals("pt") ? "dd MMM yyyy, HH:mm" : "MMM d, yyyy, h:mm a";
        return DateTimeFormatter.ofPattern(pattern, loc).withZone(ZoneId.systemDefault()).format(instant);
    }

    public String formatWeekdayShort(LocalDate day) {
        return day.format(DateTimeFormatter.ofPattern("EEE", locale()));
    }

    public String formatMonthShort(LocalDate month) {
        return month.format(DateTimeFormatter.ofPattern("MMM", locale()));
    }

    public String formatInt(long value) {
        NumberFormat nf = NumberFormat.getIntegerInstance(locale());
        return nf.format(value);
    }

    public String formatDecimal(double value, int fractionDigits) {
        NumberFormat nf = NumberFormat.getNumberInstance(locale());
        nf.setMinimumFractionDigits(fractionDigits);
        nf.setMaximumFractionDigits(fractionDigits);
        return nf.format(value);
    }

    public String formatDurationShort(long seconds) {
        long safe = Math.max(0, seconds);
        if (safe < 60) return safe + t("unit.second.short");
        if (safe < 3600) return (safe / 60) + " " + t("unit.minute.short");
        long hours = safe / 3600;
        long mins = (safe % 3600) / 60;
        return String.format(locale(), "%d%s %02d%s", hours, t("unit.hour.short"), mins, t("unit.minute.short"));
    }

    public String formatDurationClock(long seconds) {
        long safe = Math.max(0, seconds);
        return String.format(locale(), "%d%s %02d%s %02d%s",
                safe / 3600, t("unit.hour.short"),
                (safe % 3600) / 60, t("unit.minute.short"),
                safe % 60, t("unit.second.short"));
    }

    public String formatBytes(long bytes) {
        if (bytes < 0) return t("common.unavailable");
        if (bytes < 1024) return formatInt(bytes) + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024.0) return formatDecimal(kb, 1) + " KB";
        double mb = kb / 1024.0;
        if (mb < 1024.0) return formatDecimal(mb, 1) + " MB";
        double gb = mb / 1024.0;
        return formatDecimal(gb, 2) + " GB";
    }
}
