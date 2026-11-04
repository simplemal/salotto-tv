package it.simplemal.firehome;

import android.content.Context;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * The collections of backdrops. Installa Salotto copies the chosen ones to the TV, into `sfondi`
 * in the folder Salotto shares with the computer; there each is a folder of the same name, and it
 * counts only once the installer has written `.completo` in it, after its last file. A collection
 * not on the TV is read from inside the app, as long as the app still carries it.
 */
final class Packs {
    /** Every collection, by its folder: the weather's three and the four that change on a timer. */
    static final String[] ALL = {Look.PICTURES_FOLDER, Look.PHOTOS_FOLDER, Look.AERIAL_FOLDER, Look.ABSTRACT_FOLDER,
        Look.ABSTRACT_3D_FOLDER, Look.NOX_FOLDER, Look.NATURE_FOLDER};
    static final String ROOT = "sfondi";
    /** Written last by the installer: a folder without it is a copy that stopped half way. */
    static final String COMPLETE = ".completo";

    private Packs() {}

    static File root(Context context) { return Updater.shared(context, ROOT); }

    /** On the TV, whole. */
    static boolean onTv(Context context, String pack) {
        return new File(new File(root(context), pack), COMPLETE).isFile();
    }

    /** On the TV or inside the app: a collection that is neither has no row anywhere. */
    static boolean present(Context context, String pack) {
        if (onTv(context, pack)) return true;
        try {
            String[] inside = context.getAssets().list(pack);
            return inside != null && inside.length > 0;
        } catch (IOException e) {
            return false;
        }
    }

    /** The collection's own folder of a path like `astratti-3d/001.webp`, or null. */
    private static String packOf(String path) {
        int slash = path.indexOf('/');
        String first = slash > 0 ? path.substring(0, slash) : path;
        return Arrays.asList(ALL).contains(first) ? first : null;
    }

    /** What a folder holds, sorted, without the installer's mark: from the TV when the collection is there. */
    static String[] list(Context context, String path) throws IOException {
        String pack = packOf(path);
        if (pack != null && onTv(context, pack)) {
            String[] names = new File(root(context), path).list((folder, name) -> !name.startsWith("."));
            if (names == null) return new String[0];
            Arrays.sort(names);
            return names;
        }
        return context.getAssets().list(path);
    }

    /** One file of a collection, from the TV when the collection is there; anything else from inside the app. */
    static InputStream open(Context context, String path) throws IOException {
        String pack = packOf(path);
        if (pack != null && onTv(context, pack)) return new FileInputStream(new File(root(context), path));
        return context.getAssets().open(path);
    }
}
