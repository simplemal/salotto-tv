package it.simplemal.firehome;

import android.os.Build;
import java.util.Arrays;

/**
 * Every feature that is not on every Fire TV, in one place: each lists the models it is for, and is
 * true on those and false on any other. A new feature, or a new model, is a line here; the rest of
 * the code only asks, as Features.CIRCLE_ANIMATION.
 *
 * What changes with Fire OS itself and not by choice, such as the log's tags or how long a start
 * takes, stays with Fire.ANDROID_9.
 */
final class Features {
    // The Fire TVs Salotto installs on, as the installer lists them: installatore/src/main/models.js.
    static final String STICK_4K_2023 = "AFTKM";
    static final String STICK_4K_MAX_2023 = "AFTKRT";
    static final String STICK_4K_MAX_2021 = "AFTKA";
    static final String STICK_2020 = "AFTSSS";
    static final String STICK_LITE_2020 = "AFTSS";
    /** Fire OS 6, Android 7, 1.5 GB: like the Sticks of 2020, with what Android 7 lacks left to Fire.ANDROID_7. */
    static final String STICK_4K_2018 = "AFTMM";
    /** Added on 2026-11-15 from Amazon's specifications, not tried: the hardware of the Stick 4K of 2023, and Fire OS 8. */
    static final String STICK_4K_PLUS_2025 = "AFTMA08C15";

    /** The emulator stands for the Stick 4K of 2023, the one everything is tried on. */
    static final String MODEL = Build.MODEL.startsWith("sdk_") ? STICK_4K_2023 : Build.MODEL;

    // ---------- 2 GB and a graphics chip that draws two whole screens at once ----------

    /** Opening and closing an app from the wheel with its circle: elsewhere the switch is shown, off. */
    static final boolean CIRCLE_ANIMATION = on(STICK_4K_2023, STICK_4K_MAX_2023, STICK_4K_MAX_2021, STICK_4K_PLUS_2025);
    /** The Home's backdrop, blurred, under the settings and the pictures' page: elsewhere black. */
    static final boolean MENU_BACKDROP = on(STICK_4K_2023, STICK_4K_MAX_2023, STICK_4K_MAX_2021, STICK_4K_PLUS_2025);
    /** A new backdrop fades in over the old one: elsewhere it comes in one step. */
    static final boolean BACKDROP_FADE = on(STICK_4K_2023, STICK_4K_MAX_2023, STICK_4K_MAX_2021, STICK_4K_PLUS_2025);
    /** The pictures of cards, circles and grid kept in memory at full size: elsewhere half. */
    static final boolean LARGE_PICTURE_MEMORY = on(STICK_4K_2023, STICK_4K_MAX_2023, STICK_4K_MAX_2021, STICK_4K_PLUS_2025);
    /** Which window is in front, asked at once: the Sticks of 2020 can take seconds, so it is asked aside. */
    static final boolean QUICK_WINDOW_LOOKUP = on(STICK_4K_2023, STICK_4K_MAX_2023, STICK_4K_MAX_2021, STICK_4K_PLUS_2025);
    /** Resolutions above 1080p: the Sticks of 2020 would show a black screen. */
    static final boolean RESOLUTION_4K = on(STICK_4K_2023, STICK_4K_MAX_2023, STICK_4K_MAX_2021, STICK_4K_PLUS_2025);

    // ---------- Fire OS 8, the models of 2023 ----------

    /** The number of notifications on the Home, read by Salotto's own listener. */
    static final boolean NOTIFICATION_COUNT = on(STICK_4K_2023, STICK_4K_MAX_2023, STICK_4K_PLUS_2025);
    /** The Wi-Fi networks listed in Salotto's settings: elsewhere one line opens Fire OS's page. */
    static final boolean WIFI_NETWORKS = on(STICK_4K_2023, STICK_4K_MAX_2023, STICK_4K_PLUS_2025);
    /** The USB page in Salotto's settings. */
    static final boolean USB_SETTINGS = on(STICK_4K_2023, STICK_4K_MAX_2023, STICK_4K_PLUS_2025);
    /** Starting Fire OS's own screensaver from the settings. */
    static final boolean FIRE_SAVER_NOW = on(STICK_4K_2023, STICK_4K_MAX_2023, STICK_4K_PLUS_2025);
    /** An app's data cleared from Salotto's settings: elsewhere Fire OS's page of the app opens. */
    static final boolean CLEAR_DATA_HERE = on(STICK_4K_2023, STICK_4K_MAX_2023, STICK_4K_PLUS_2025);

    private Features() {}

    private static boolean on(String... models) { return Arrays.asList(models).contains(MODEL); }
}
