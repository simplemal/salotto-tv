package it.simplemal.firehome;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import java.util.Arrays;
import java.util.Locale;

/**
 * Salotto's language: the one chosen here, or else Fire OS's. A language Salotto does not have falls
 * back on English, not on Italian, which is only where the words start from.
 */
final class Language {
    static final String[] CODES = {"it", "en", "es", "fr", "de", "pt", "ru", "ar", "hi", "zh"};
    /** Each in its own words: this list is not translated. */
    static final String[] NAMES = {"italiano", "English", "español", "français", "Deutsch",
        "português", "русский", "العربية", "हिन्दी", "中文"};
    private static final String FILE = "language";
    private static final String CHOSEN = "chosen";
    private static final String FALLBACK = "en";

    private Language() {}

    static String code(Context context) {
        String chosen = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(CHOSEN, null);
        if (chosen != null) return chosen;
        String system = Resources.getSystem().getConfiguration().getLocales().get(0).getLanguage();
        return Arrays.asList(CODES).contains(system) ? system : FALLBACK;
    }

    static String name(Context context) { return NAMES[Arrays.asList(CODES).indexOf(code(context))]; }

    static void set(Context context, String code) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(CHOSEN, code).commit();
    }

    /** For attachBaseContext: dates and numbers follow too. */
    static Context apply(Context base) {
        Locale locale = new Locale(code(base));
        Locale.setDefault(locale);
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        return base.createConfigurationContext(config);
    }
}
