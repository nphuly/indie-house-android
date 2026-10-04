package house.indie.legacy;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import java.util.Locale;

/**
 * The app's own language choice (Vietnamese or English), kept per device.
 * Android 4.3 has no per-app language setting, so the choice is applied to
 * the shared Resources whenever an activity starts or the configuration changes.
 */
final class Locales {
    static final String VI = "vi";
    static final String EN = "en";
    private static final String KEY = "ui_locale";

    private Locales() { }

    static String current(Context context) {
        String saved = context.getSharedPreferences("indie_house", Context.MODE_PRIVATE).getString(KEY, VI);
        return EN.equals(saved) ? EN : VI;
    }

    static void save(Context context, String code) {
        context.getSharedPreferences("indie_house", Context.MODE_PRIVATE).edit()
                .putString(KEY, EN.equals(code) ? EN : VI).apply();
    }

    static void apply(Context context) {
        Locale locale = new Locale(current(context));
        Locale.setDefault(locale);
        update(context.getResources(), locale);
        update(context.getApplicationContext().getResources(), locale);
    }

    private static void update(Resources resources, Locale locale) {
        Configuration config = new Configuration(resources.getConfiguration());
        if (locale.equals(config.locale)) return;
        config.locale = locale;
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }
}
