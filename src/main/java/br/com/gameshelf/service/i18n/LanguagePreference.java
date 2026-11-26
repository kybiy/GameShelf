package br.com.gameshelf.service.i18n;

import java.util.Locale;

/** Supported language selection options for GameShelf. */
public enum LanguagePreference {
    SYSTEM("system"),
    PT_BR("pt-BR"),
    EN_US("en-US");

    private final String code;

    LanguagePreference(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** Resolves a persisted preference code, defaulting to {@link #SYSTEM}. */
    public static LanguagePreference fromCode(String code) {
        if (code == null || code.isBlank()) return SYSTEM;
        for (LanguagePreference pref : values()) {
            if (pref.code.equalsIgnoreCase(code.strip())) return pref;
        }
        return SYSTEM;
    }

    /** Maps this preference to a supported {@link Locale}, falling back to en-US for unsupported OS locales. */
    public Locale resolveLocale(Locale systemDefault) {
        return switch (this) {
            case PT_BR -> Locale.forLanguageTag("pt-BR");
            case EN_US -> Locale.forLanguageTag("en-US");
            case SYSTEM -> {
                Locale effective = systemDefault == null ? Locale.ENGLISH : systemDefault;
                yield effective.getLanguage().equalsIgnoreCase("pt")
                        ? Locale.forLanguageTag("pt-BR")
                        : Locale.forLanguageTag("en-US");
            }
        };
    }
}
