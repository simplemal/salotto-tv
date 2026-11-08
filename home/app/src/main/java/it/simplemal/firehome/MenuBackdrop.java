package it.simplemal.firehome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.function.Consumer;

/**
 * The Home's backdrop, blurred and darkened, under the settings. Blurring takes a moment on a Stick,
 * so the Home makes it after every change of backdrop, when it has nothing else to do, and keeps it
 * in a small file: the settings open with it already there.
 */
final class MenuBackdrop {
    private static final int BLUR_RADIUS = 6;
    /** Lighter than behind the task manager: here the backdrop is meant to be seen. */
    private static final int SCRIM = Color.argb(150, 8, 10, 14);
    private static final String PICTURE = "menu-backdrop.jpg";
    private static final String NAME = "menu-backdrop.name";
    private static final int QUALITY = 88;
    private static String keptName;
    private static Bitmap kept;
    private static String making;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private MenuBackdrop() {}

    /** On the main thread. Nothing to do if the file is already the one of this backdrop. */
    static void prepare(Context context) {
        String name = Look.currentName(context);
        if (name == null || name.equals(savedName(context)) || name.equals(making)) return;
        make(context, picture -> { });
    }

    /** Ready at once, from memory or from the file, or null if it still has to be made. */
    static Bitmap ready(Context context) {
        String name = Look.currentName(context);
        if (name == null) return null;
        if (name.equals(keptName)) return kept;
        if (!name.equals(savedName(context))) return null;
        Bitmap read = BitmapFactory.decodeFile(new File(context.getCacheDir(), PICTURE).getPath());
        if (read != null) keep(name, read);
        return read;
    }

    /** On the main thread; `done` too, with the picture made. */
    static void make(Context context, Consumer<Bitmap> done) {
        String name = Look.currentName(context);
        if (name == null) return;
        making = name;
        Context app = context.getApplicationContext();
        Look.current(context, picture -> Background.draw(() -> {
            Bitmap made = Blur.veiled(picture, BLUR_RADIUS, SCRIM);
            save(app, name, made);
            MAIN.post(() -> {
                if (name.equals(making)) making = null;
                keep(name, made);
                done.accept(made);
            });
        }));
    }

    private static void keep(String name, Bitmap picture) {
        keptName = name;
        kept = picture;
    }

    private static String savedName(Context context) {
        try {
            return new String(Files.readAllBytes(new File(context.getCacheDir(), NAME).toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** The picture first, then its name: a name never stands for a picture that is not all there. */
    private static void save(Context context, String name, Bitmap picture) {
        File file = new File(context.getCacheDir(), PICTURE);
        File named = new File(context.getCacheDir(), NAME);
        named.delete();
        try (FileOutputStream out = new FileOutputStream(file)) {
            picture.compress(Bitmap.CompressFormat.JPEG, QUALITY, out);
            Files.write(named.toPath(), name.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            // Made again the next time the settings open.
        }
    }
}
