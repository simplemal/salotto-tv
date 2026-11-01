package it.simplemal.firehome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/** The saved appearance of the Home: grid, clock and backdrop. Only the weather backdrop changes on its own. */
final class Look {
    static final int[] COLUMN_CHOICES = {5, 6};
    static final String[] FONT_NAMES = {"Sottile", "Leggero", "Normale", "Medio", "Serif", "Monospazio", "Corsivo"};
    static final String[] FONT_FAMILIES = {"sans-serif-thin", "sans-serif-light", "sans-serif",
        "sans-serif-medium", "serif", "monospace", "cursive"};
    static final String[] NAME_MODES = {"Solo sulla selezionata", "Sempre", "Mai"};
    static final int NAMES_ON_FOCUS = 0;
    static final int NAMES_ALWAYS = 1;
    static final String[] SIZE_NAMES = {"Piccolo", "Medio", "Grande"};
    static final int[] SIZES = {64, 96, 132};
    static final String[] PRESET_NAMES = {"Notte", "Oceano", "Bosco", "Tramonto", "Grafite"};
    private static final int[][] PRESET_COLORS = {
        {Color.rgb(44, 50, 62), Color.rgb(10, 12, 16)},
        {Color.rgb(24, 66, 120), Color.rgb(6, 14, 30)},
        {Color.rgb(24, 84, 76), Color.rgb(8, 22, 26)},
        {Color.rgb(128, 72, 92), Color.rgb(30, 20, 50)},
        {Color.rgb(72, 74, 80), Color.rgb(20, 20, 24)}
    };
    static final String WEATHER = "Meteo dinamico";
    static final String PICTURES = "Meteo illustrato";
    static final String PHOTOS = "Meteo fotografico";
    private static final String FILE = "look";
    private static final String FOLDER = "sfondi";
    private static final int WIDE = 1920;

    private static String cachedName;
    private static Bitmap cachedImage;

    private Look() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
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
    static void setDate(Context context, boolean value) { prefs(context).edit().putBoolean("date", value).apply(); }

    /** The clock pattern chosen in Settings. */
    static String timePattern(Context context) {
        return (hours24(context) ? "HH:mm" : "h:mm") + (seconds(context) ? ":ss" : "");
    }

    /** Not a picture: the Home draws it with WeatherView, and it needs a place for the weather. */
    static boolean weatherBackdrop(Context context) {
        return WEATHER.equals(backdrop(context)) || weatherPictures(context);
    }

    static boolean weatherPictures(Context context) { return PICTURES.equals(backdrop(context)) || weatherPhotos(context); }

    static boolean weatherPhotos(Context context) { return PHOTOS.equals(backdrop(context)); }

    static String backdrop(Context context) { return prefs(context).getString("backdrop", PICTURES); }
    static void setBackdrop(Context context, String value) { prefs(context).edit().putString("backdrop", value).apply(); }

    /** Changes when the Home has to be drawn again. */
    static String signature(Context context) {
        return columns(context) + "/" + font(context) + "/" + size(context) + "/" + clock(context)
            + "/" + backdrop(context) + "/" + names(context) + "/" + timePattern(context) + "/" + date(context);
    }

    /** Images copied into the app's own folder: the Stick has no system image picker. */
    static List<File> images(Context context) {
        List<File> images = new ArrayList<>();
        File[] files = new File(context.getFilesDir(), FOLDER).listFiles();
        if (files == null) return images;
        Arrays.sort(files);
        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) images.add(file);
        }
        return images;
    }

    private static Drawable preset(String chosen) {
        int preset = Math.max(0, Arrays.asList(PRESET_NAMES).indexOf(chosen));
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, PRESET_COLORS[preset]);
    }

    /** The dark shading of the Settings, for pages that do not follow the chosen backdrop. */
    static Drawable plainBackdrop() { return preset(PRESET_NAMES[0]); }

    /** Hands over the chosen backdrop: at once, or a moment later for an image not decoded yet. */
    static void show(Context context, Consumer<Drawable> done) {
        String chosen = backdrop(context);
        File file = new File(new File(context.getFilesDir(), FOLDER), chosen);
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
    private static Bitmap load(File file) {
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
