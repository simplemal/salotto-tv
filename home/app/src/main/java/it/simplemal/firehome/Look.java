package it.simplemal.firehome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Consumer;

/** The saved appearance of the Home: grid, clock and backdrop. Only the weather backdrop changes on its own. */
final class Look {
    private static final int SMALL_SAMPLE = 4;
    private static final int SMALL_WIDTH = 480;
    private static final int SMALL_HEIGHT = 270;
    static final int[] COLUMN_CHOICES = {5, 6};
    private static final int[] FONT_LABELS = {R.string.look_font_thin, R.string.look_font_light, R.string.look_font_normal,
        R.string.look_font_medium, R.string.look_font_serif, R.string.look_font_mono, R.string.look_font_cursive};
    static final String[] FONT_FAMILIES = {"sans-serif-thin", "sans-serif-light", "sans-serif",
        "sans-serif-medium", "serif", "monospace", "cursive"};
    private static final int[] NAME_MODE_LABELS = {R.string.look_names_on_focus, R.string.look_names_always, R.string.look_names_never};
    static final int NAMES_ON_FOCUS = 0;
    static final int NAMES_ALWAYS = 1;
    private static final int[] SIZE_LABELS = {R.string.look_size_small, R.string.look_size_medium, R.string.look_size_large};
    static final int[] SIZES = {64, 96, 132};
    static final String[] PRESET_NAMES = {"Notte", "Oceano", "Bosco", "Tramonto", "Grafite", "Aurora", "Prugna",
        "Ambra", "Laguna", "Rubino", "Crepuscolo", "Ghiacciaio", "Nero", "Petrolio", "Bottiglia", "Vinaccia"};
    private static final int[] PRESET_LABELS = {R.string.first_backdrop_night, R.string.first_backdrop_ocean,
        R.string.first_backdrop_forest, R.string.first_backdrop_sunset, R.string.first_backdrop_graphite,
        R.string.first_backdrop_aurora, R.string.first_backdrop_plum, R.string.first_backdrop_amber,
        R.string.first_backdrop_lagoon, R.string.first_backdrop_ruby, R.string.first_backdrop_dusk,
        R.string.first_backdrop_glacier, R.string.first_backdrop_black, R.string.first_backdrop_petrol,
        R.string.first_backdrop_bottle, R.string.first_backdrop_wine};
    /** Three colours down the slant, then the light in the corner. */
    private static final int[][] PRESET_COLORS = {
        {0xff3a4252, 0xff1c2029, 0xff0a0c10, 0x286e86b4},
        {0xff18487c, 0xff0d2a4e, 0xff060e1e, 0x2e3aa0d8},
        {0xff1c5a4e, 0xff0f3330, 0xff08161a, 0x285cc08a},
        {0xff8a4c5e, 0xff4a2a4e, 0xff1e1432, 0x34ff9a5c},
        {0xff4c4e54, 0xff2c2d32, 0xff141418, 0x1ea0a4ac},
        {0xff14504e, 0xff1a2a52, 0xff0c0e24, 0x305ce0b0},
        {0xff5a2a5e, 0xff341a40, 0xff140c20, 0x28c070d0},
        {0xff6e4a1c, 0xff3e2812, 0xff18100a, 0x2ef0b050},
        {0xff126a72, 0xff0c3c4c, 0xff061a24, 0x2a50d8d8},
        {0xff701e30, 0xff40121e, 0xff1a080e, 0x28f05060},
        {0xff3c3468, 0xff2a2048, 0xff100e24, 0x28f09070},
        {0xff5a7a90, 0xff2e4658, 0xff101c26, 0x28b8e0f0},
        {0xff000000, 0xff000000, 0xff000000, 0},
        {0xff0f3340, 0xff0f3340, 0xff0f3340, 0},
        {0xff133a30, 0xff133a30, 0xff133a30, 0},
        {0xff4a1626, 0xff4a1626, 0xff4a1626, 0}
    };
    /** Behind every menu and page of Salotto: dark enough that only the words stand out. */
    private static final int[] PAGE_COLORS = {0xff0b0c10, 0xff07080a, 0xff040506, 0};
    static final String WEATHER = "Meteo dinamico";
    static final String PICTURES = "Meteo illustrato";
    static final String PHOTOS = "Meteo fotografico";
    static final String AERIAL = "Meteo aereo";
    static final String ABSTRACT = "Abstract Illustrated";
    static final String ABSTRACT_3D = "Abstract 3D";
    static final String NOX = "Nox City";
    static final String NATURE = "Aerial Nature";
    /** The photos of Personale, changing on the timer like the sets: offered from two photos on. */
    static final String PERSONAL = "Personal";
    static final int PERSONAL_TIMED_FROM = 2;
    static final String PICTURES_FOLDER = "meteo-illustrato";
    static final String PHOTOS_FOLDER = "meteo-foto";
    static final String AERIAL_FOLDER = "meteo-aereo";
    static final String ABSTRACT_FOLDER = "astratti";
    static final String ABSTRACT_3D_FOLDER = "astratti-3d";
    static final String NOX_FOLDER = "nox-city";
    static final String NATURE_FOLDER = "natura-aerea";
    static final int[] ABSTRACT_MINUTES = {1, 5, 15, 30, 60};
    private static final int ABSTRACT_DEFAULT = 5;
    private static final long MINUTE_MS = 60 * 1000L;
    private static final String FILE = "look";
    private static final String FOLDER = "sfondi";
    private static final int WIDE = 1920;

    private static String cachedName;
    private static Bitmap cachedImage;

    private Look() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private static String[] words(Context context, int[] labels) {
        String[] words = new String[labels.length];
        for (int index = 0; index < labels.length; index++) words[index] = context.getString(labels[index]);
        return words;
    }

    static String[] fontNames(Context context) { return words(context, FONT_LABELS); }
    static String[] nameModes(Context context) { return words(context, NAME_MODE_LABELS); }
    static String[] sizeNames(Context context) { return words(context, SIZE_LABELS); }

    /** What a backdrop is called on screen. The key stays what is saved; a file keeps its own name. */
    static String backdropName(Context context, String key) {
        if (WEATHER.equals(key)) return context.getString(R.string.look_backdrop_weather);
        if (PICTURES.equals(key)) return context.getString(R.string.first_backdrop_pictures);
        if (PHOTOS.equals(key)) return context.getString(R.string.first_backdrop_photos);
        if (AERIAL.equals(key)) return context.getString(R.string.first_backdrop_aerial);
        if (PERSONAL.equals(key)) return context.getString(R.string.settings_backdrop_personal);
        if (key.endsWith(Weather.PICTURE_FILE)) return pictureName(key);
        int preset = Arrays.asList(PRESET_NAMES).indexOf(key);
        return preset >= 0 ? context.getString(PRESET_LABELS[preset]) : key;
    }

    /** From the file: 001-marea-obliqua.webp is Marea obliqua. */
    private static String pictureName(String path) {
        String words = path.substring(path.lastIndexOf('/') + 1).replaceAll("^\\d+-|(-4k)?\\.webp$", "").replace('-', ' ');
        return words.isEmpty() ? path : words.substring(0, 1).toUpperCase(Locale.getDefault()) + words.substring(1);
    }

    static int columns(Context context) { return prefs(context).getInt("columns", 6); }
    static void setColumns(Context context, int value) { prefs(context).edit().putInt("columns", value).apply(); }
    static int font(Context context) { return prefs(context).getInt("font", 2); }
    static void setFont(Context context, int value) { prefs(context).edit().putInt("font", value).apply(); }
    static int size(Context context) { return prefs(context).getInt("size", 1); }
    static void setSize(Context context, int value) { prefs(context).edit().putInt("size", value).apply(); }
    static boolean clock(Context context) { return prefs(context).getBoolean("clock", true); }
    static void setClock(Context context, boolean value) { prefs(context).edit().putBoolean("clock", value).apply(); }
    static int names(Context context) { return prefs(context).getInt("names", NAMES_ON_FOCUS); }
    static void setNames(Context context, int value) { prefs(context).edit().putInt("names", value).apply(); }
    static boolean hours24(Context context) { return prefs(context).getBoolean("hours24", true); }
    static void setHours24(Context context, boolean value) { prefs(context).edit().putBoolean("hours24", value).apply(); }
    static boolean seconds(Context context) { return prefs(context).getBoolean("seconds", false); }
    static void setSeconds(Context context, boolean value) { prefs(context).edit().putBoolean("seconds", value).apply(); }
    static boolean date(Context context) { return prefs(context).getBoolean("date", true); }
    /** The Home without the big clock and weather: it rests with the dock up, as when a lower row has the focus. */
    static boolean appsOnly(Context context) { return prefs(context).getBoolean("appsOnly", false); }
    static void setAppsOnly(Context context, boolean value) { prefs(context).edit().putBoolean("appsOnly", value).apply(); }
    static void setDate(Context context, boolean value) { prefs(context).edit().putBoolean("date", value).apply(); }

    /** The clock pattern chosen in Settings. */
    static String timePattern(Context context) {
        return (hours24(context) ? "HH:mm" : "h:mm") + (seconds(context) ? ":ss" : "");
    }

    /** Not a picture: the Home draws it with WeatherView, and it needs a place for the weather. */
    static boolean weatherBackdrop(Context context) {
        return WEATHER.equals(backdrop(context)) || weatherPictures(context);
    }

    /** The ones drawn from a file of the app. The abstract ones follow no weather, only the clock. */
    static boolean weatherPictures(Context context) {
        return PICTURES.equals(backdrop(context)) || weatherPhotos(context) || weatherAerial(context) || abstractPictures(context);
    }

    static boolean abstractPictures(Context context) { return timedFolder(context) != null || fixedPicture(context); }

    /** One picture of a set, chosen and kept: the backdrop is then that file. */
    static boolean fixedPicture(Context context) { return backdrop(context).endsWith(Weather.PICTURE_FILE); }

    /** One of the sets that change on a timer. */

    private static String timedFolder(Context context) {
        String chosen = backdrop(context);
        if (ABSTRACT.equals(chosen)) return ABSTRACT_FOLDER;
        if (ABSTRACT_3D.equals(chosen)) return ABSTRACT_3D_FOLDER;
        if (NOX.equals(chosen)) return NOX_FOLDER;
        if (PERSONAL.equals(chosen)) return personalFolder(context).getPath();
        return NATURE.equals(chosen) ? NATURE_FOLDER : null;
    }

    /** A picture to draw whole: one of the app's sets, or a photo of Personale by its full path. */
    static boolean picture(String name) {
        return name.endsWith(Weather.PICTURE_FILE) || name.startsWith("/");
    }

    static int abstractMinutes(Context context) { return prefs(context).getInt("abstract_minutes", ABSTRACT_DEFAULT); }
    static void setAbstractMinutes(Context context, int value) {
        prefs(context).edit().putInt("abstract_minutes", value).apply();
    }

    /** The one on show until its time is up, then any other: nothing is decided ahead. */
    static String abstractPicture(Context context) {
        if (fixedPicture(context)) return backdrop(context);
        SharedPreferences saved = prefs(context);
        String folder = timedFolder(context);
        String shown = saved.getString("abstract", null);
        boolean same = shown != null && shown.startsWith(folder + "/");
        long now = System.currentTimeMillis();
        long since = now - saved.getLong("abstract_at", 0);
        if (same && since >= 0 && since < abstractMinutes(context) * MINUTE_MS) return shown;
        String picked = anyPicture(context, folder, shown);
        if (picked == null) return shown;
        saved.edit().putString("abstract", picked).putLong("abstract_at", now).apply();
        return picked;
    }

    /** Time away from the Home does not count: a picture changes under the eyes, never on the way back. */
    static void leaveHome(Context context) {
        prefs(context).edit().putLong("home_left", System.currentTimeMillis()).apply();
    }

    static void backHome(Context context) {
        SharedPreferences saved = prefs(context);
        long left = saved.getLong("home_left", 0);
        long away = System.currentTimeMillis() - left;
        if (left == 0 || away <= 0 || !saved.contains("abstract_at")) return;
        saved.edit().putLong("abstract_at", saved.getLong("abstract_at", 0) + away).apply();
    }

    /** True once per power-on, for the picture that fades in with the Home. */
    static boolean firstSinceBoot(Context context) {
        int boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, 0);
        SharedPreferences saved = prefs(context);
        if (saved.getInt("faded_boot", -1) == boot) return false;
        saved.edit().putInt("faded_boot", boot).apply();
        return true;
    }

    /** Any but the one on show. Null if the set cannot be read. */
    static String anyPicture(Context context, String folder, String shown) {
        List<String> files = new ArrayList<>();
        if (folder.startsWith("/")) {
            for (File image : images(context)) {
                if (!image.getPath().equals(shown)) files.add(image.getPath());
            }
            return files.isEmpty() ? null : files.get(new Random().nextInt(files.size()));
        }
        try {
            for (String name : context.getAssets().list(folder)) {
                if (!(folder + "/" + name).equals(shown)) files.add(folder + "/" + name);
            }
        } catch (IOException e) {
            return null;
        }
        return files.isEmpty() ? null : files.get(new Random().nextInt(files.size()));
    }

    /** Which set Salotto's screensaver shows. */
    static String saverFolder(Context context) { return prefs(context).getString("saver_folder", ABSTRACT_3D_FOLDER); }
    static void setSaverFolder(Context context, String value) { prefs(context).edit().putString("saver_folder", value).apply(); }

    /** Salotto's screensaver is the one chosen. */
    static boolean ownSaver(Context context) {
        String chosen = Settings.Secure.getString(context.getContentResolver(), "screensaver_components");
        return chosen != null && chosen.startsWith(context.getPackageName() + "/");
    }

    /** What the screensaver shows over its pictures. */
    static boolean saverTime(Context context) { return prefs(context).getBoolean("saver_time", true); }
    static void setSaverTime(Context context, boolean on) { prefs(context).edit().putBoolean("saver_time", on).apply(); }
    static boolean saverDate(Context context) { return prefs(context).getBoolean("saver_date", true); }
    static void setSaverDate(Context context, boolean on) { prefs(context).edit().putBoolean("saver_date", on).apply(); }
    /** 0 moves with each picture, then the four corners and the centre. */
    static int saverPlace(Context context) { return prefs(context).getInt("saver_place", 0); }
    static void setSaverPlace(Context context, int place) { prefs(context).edit().putInt("saver_place", place).apply(); }
    static boolean saverWeather(Context context) { return prefs(context).getBoolean("saver_weather", false); }
    static void setSaverWeather(Context context, boolean on) { prefs(context).edit().putBoolean("saver_weather", on).apply(); }

    static boolean weatherPhotos(Context context) { return PHOTOS.equals(backdrop(context)); }
    static boolean weatherAerial(Context context) { return AERIAL.equals(backdrop(context)); }

    /**
     * The weather's backdrops need a place: the default follows it, and a chosen one gives way to
     * Abstract 3D once the place is gone. The dynamic weather is no longer offered.
     */
    static String backdrop(Context context) {
        String chosen = prefs(context).getString("backdrop", null);
        boolean place = Weather.active(context);
        if (chosen == null) return place ? PICTURES : ABSTRACT_3D;
        // Its photos can be gone, after a backup is restored on another Stick.
        if (PERSONAL.equals(chosen) && images(context).isEmpty()) {
            setBackdrop(context, ABSTRACT_3D);
            return ABSTRACT_3D;
        }
        if (!WEATHER.equals(chosen) && !PICTURES.equals(chosen) && !PHOTOS.equals(chosen) && !AERIAL.equals(chosen)) return chosen;
        if (!place) {
            setBackdrop(context, ABSTRACT_3D);
            return ABSTRACT_3D;
        }
        return WEATHER.equals(chosen) ? PICTURES : chosen;
    }

    static void setBackdrop(Context context, String value) { prefs(context).edit().putString("backdrop", value).apply(); }

    /** Changes when the Home has to be drawn again. */
    static String signature(Context context) {
        return columns(context) + "/" + font(context) + "/" + size(context) + "/" + clock(context)
            + "/" + backdrop(context) + "/" + names(context) + "/" + timePattern(context) + "/" + date(context)
            + "/" + appsOnly(context);
    }

    /** The part of it the Home's cards are built from: the rest changes without touching them. */
    static String cardsSignature(Context context) {
        return columns(context) + "/" + names(context);
    }

    /** Where the images of Personale are, sent from a phone or copied before that. */
    static File personalFolder(Context context) { return new File(context.getFilesDir(), FOLDER); }

    /** The backdrop is one of them. */
    static boolean personal(Context context, String name) { return new File(personalFolder(context), name).isFile(); }

    /** Images copied into the app's own folder: the Stick has no system image picker. */
    static List<File> images(Context context) {
        List<File> images = new ArrayList<>();
        File[] files = personalFolder(context).listFiles();
        if (files == null) return images;
        Arrays.sort(files);
        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) images.add(file);
        }
        return images;
    }

    /** The backdrop by that name, when it is one of the plain ones. */
    static Drawable preset(String chosen) {
        int preset = Arrays.asList(PRESET_NAMES).indexOf(chosen);
        return new Shade(preset >= 0 ? PRESET_COLORS[preset] : PAGE_COLORS);
    }

    /** The dark shading of the Settings and of every page that does not follow the chosen backdrop. */
    static Drawable plainBackdrop() { return new Shade(PAGE_COLORS); }

    /** What is behind the Home now, by name: it changes only when the picture does. */
    static String currentName(Context context) {
        return weatherPictures(context) ? Weather.picture(context) : backdrop(context);
    }

    /** That same backdrop as a small bitmap, for a page that lays it under itself. On the main thread. */
    static void current(Context context, Consumer<Bitmap> done) {
        String name = currentName(context);
        Runnable plain = () -> show(context, drawable -> done.accept(painted(drawable)));
        if (!weatherPictures(context) || name == null || !picture(name)) {
            plain.run();
            return;
        }
        Handler main = new Handler(Looper.getMainLooper());
        Context app = context.getApplicationContext();
        Background.draw(() -> {
            Bitmap picture = small(app, name);
            main.post(() -> {
                if (picture == null) plain.run();
                else done.accept(picture);
            });
        });
    }

    private static Bitmap small(Context context, String asset) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = SMALL_SAMPLE;
        if (asset.startsWith("/")) return BitmapFactory.decodeFile(asset, options);
        try (InputStream input = context.getAssets().open(asset)) {
            return BitmapFactory.decodeStream(input, null, options);
        } catch (IOException e) {
            return null;
        }
    }

    private static Bitmap painted(Drawable drawable) {
        Bitmap picture = Bitmap.createBitmap(SMALL_WIDTH, SMALL_HEIGHT, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, SMALL_WIDTH, SMALL_HEIGHT);
        drawable.draw(new Canvas(picture));
        return picture;
    }

    /** Hands over the chosen backdrop: at once, or a moment later for an image not decoded yet. */
    static void show(Context context, Consumer<Drawable> done) {
        String chosen = backdrop(context);
        File file = new File(personalFolder(context), chosen);
        if (!file.isFile()) {
            trim();
            done.accept(preset(chosen));
        } else if (chosen.equals(cachedName)) {
            done.accept(new BitmapDrawable(context.getResources(), cachedImage));
        } else {
            Handler main = new Handler(Looper.getMainLooper());
            Background.draw(() -> {
                Bitmap image = load(file);
                main.post(() -> {
                    if (image == null) {
                        done.accept(preset(chosen));
                        return;
                    }
                    cachedName = chosen;
                    cachedImage = image;
                    done.accept(new BitmapDrawable(context.getResources(), image));
                });
            });
        }
    }

    /** Whoever still shows the image keeps it: this only stops holding it for later. */
    static void trim() {
        cachedName = null;
        cachedImage = null;
    }

    /** Decoded close to screen size and cropped to 16:9, so nothing is stretched. */
    static Bitmap load(File file) {
        BitmapFactory.Options size = new BitmapFactory.Options();
        size.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getPath(), size);
        BitmapFactory.Options options = new BitmapFactory.Options();
        int sample = 1;
        while (size.outWidth / sample >= 2 * WIDE) sample *= 2;
        options.inSampleSize = sample;
        Bitmap image = BitmapFactory.decodeFile(file.getPath(), options);
        if (image == null) return null;
        int width = image.getWidth();
        int height = Math.min(image.getHeight(), width * 9 / 16);
        width = Math.min(width, height * 16 / 9);
        Bitmap cropped = Bitmap.createBitmap(image, (image.getWidth() - width) / 2,
            (image.getHeight() - height) / 2, width, height);
        if (cropped != image) image.recycle();
        Bitmap finished = cropped.copy(Bitmap.Config.ARGB_8888, true);
        cropped.recycle();
        if (finished != null) BottomBlur.apply(finished);
        return finished;
    }
}
