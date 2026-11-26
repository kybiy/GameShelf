package br.com.gameshelf;

import br.com.gameshelf.service.i18n.LanguagePreference;
import br.com.gameshelf.service.i18n.LocalizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

final class I18nTest {
    @TempDir Path dir;

    @Test
    void systemLocaleResolvesSupportedAndFallsBackToEnglish() {
        assertEquals(Locale.forLanguageTag("pt-BR"),
                LanguagePreference.SYSTEM.resolveLocale(Locale.forLanguageTag("pt-BR")));
        assertEquals(Locale.forLanguageTag("pt-BR"),
                LanguagePreference.SYSTEM.resolveLocale(Locale.forLanguageTag("pt-PT")));
        assertEquals(Locale.forLanguageTag("en-US"),
                LanguagePreference.SYSTEM.resolveLocale(Locale.forLanguageTag("en-US")));
        assertEquals(Locale.forLanguageTag("en-US"),
                LanguagePreference.SYSTEM.resolveLocale(Locale.JAPAN));
        assertEquals(LanguagePreference.SYSTEM, LanguagePreference.fromCode("unknown"));
    }

    @Test
    void resourceBundlesHaveFullKeyParityAndUtf8Characters() throws Exception {
        Properties base = loadProps("/i18n/messages.properties");
        Properties en = loadProps("/i18n/messages_en_US.properties");
        Properties pt = loadProps("/i18n/messages_pt_BR.properties");

        assertFalse(base.isEmpty());
        assertEquals(base.keySet(), en.keySet(), "Base and en_US bundles must have identical keys");
        assertEquals(base.keySet(), pt.keySet(), "pt_BR and en_US bundles must have identical keys");
        assertTrue(pt.getProperty("nav.settings").contains("ç"));
        assertTrue(pt.getProperty("nav.about").equals("Sobre"));
        assertEquals("Settings", en.getProperty("nav.settings"));
    }

    @Test
    void missingTranslationKeyReturnsBracketedKeyWithoutThrowing() {
        LocalizationService i18n = new LocalizationService(LanguagePreference.EN_US);
        assertEquals("[non.existent.key]", i18n.t("non.existent.key"));
        assertEquals("[non.existent.key]", i18n.t("non.existent.key", "arg"));
        assertFalse(i18n.hasKey("non.existent.key"));
    }

    @Test
    void regionalFormattingAdaptsDatesNumbersStorageAndDurations() {
        LocalizationService pt = new LocalizationService(LanguagePreference.PT_BR);
        LocalizationService en = new LocalizationService(LanguagePreference.EN_US);

        assertEquals("1.234", pt.formatInt(1234));
        assertEquals("1,234", en.formatInt(1234));

        assertEquals("12,5 MB", pt.formatBytes(12 * 1024L * 1024L + 512 * 1024L));
        assertEquals("12.5 MB", en.formatBytes(12 * 1024L * 1024L + 512 * 1024L));

        assertEquals("2h 05min", pt.formatDurationShort(2 * 3600 + 5 * 60));
        assertEquals("2h 05min", en.formatDurationShort(2 * 3600 + 5 * 60));
        assertEquals("1h 02min 03s", pt.formatDurationClock(3600 + 120 + 3));

        Instant sample = Instant.parse("2026-11-26T15:30:00Z");
        assertNotEquals(pt.formatDate(sample), en.formatDate(sample));
        assertEquals("Jogos locais", pt.platformName("Local"));
        assertEquals("Local games", en.platformName("Local"));
        assertEquals("Steam", pt.platformName("Steam"));
        assertEquals("Steam", en.platformName("Steam"));
    }

    @Test
    void languagePreferencePersistsInLibraryDatabase() throws Exception {
        Path legacy = dir.resolve("library.properties");
        Library lib = new Library(legacy);
        lib.settingValue("language", LanguagePreference.EN_US.code());
        lib.save();

        Library reopened = new Library(legacy);
        assertEquals(LanguagePreference.EN_US, LanguagePreference.fromCode(reopened.setting("language", "system")));
    }

    private static Properties loadProps(String resource) throws Exception {
        Properties props = new Properties();
        try (var in = I18nTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "Missing resource: " + resource);
            try (var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
        }
        return props;
    }
}
