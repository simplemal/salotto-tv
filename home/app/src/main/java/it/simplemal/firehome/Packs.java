package it.simplemal.firehome;

import android.content.Context;
import android.os.StatFs;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The collections of backdrops. Installa Salotto writes which ones were chosen in `sfondi/scelte`,
 * in the folder Salotto shares with the computer; Salotto downloads them from the release "sfondi"
 * on GitHub, each into a folder of the same name, which counts only once `.completo` is written in
 * it after its last file. A collection not on the TV is read from inside the app, while it carries it.
 */
final class Packs {
    /** Every collection, by its folder: the weather's three and the four that change on a timer. */
    static final String[] ALL = {Look.PICTURES_FOLDER, Look.PHOTOS_FOLDER, Look.AERIAL_FOLDER, Look.ABSTRACT_FOLDER,
        Look.ABSTRACT_3D_FOLDER, Look.NOX_FOLDER, Look.NATURE_FOLDER};
    static final String ROOT = "sfondi";
    /** Written last, after the last file: a folder without it is a download that stopped half way. */
    static final String COMPLETE = ".completo";
    /** Written by the installer: the collections chosen, one per line. Salotto fetches the missing ones. */
    static final String CHOSEN = "scelte";
    private static final String BASE = "https://github.com/" + Fire.UPDATES_REPO + "/releases/download/sfondi/";
    /** Room for the archive and for what comes out of it, with something to spare. */
    private static final int ROOM_TIMES = 2;
    private static final long ROOM_SPARE = 200L * 1024 * 1024;
    private static final AtomicBoolean fetching = new AtomicBoolean();

    private Packs() {}

    static File root(Context context) { return Updater.shared(context, ROOT); }

    /** On the TV, whole. */
    static boolean onTv(Context context, String pack) {
        return new File(new File(root(context), pack), COMPLETE).isFile();
    }

    /** On the TV or inside the app: a collection that is neither has no row anywhere. */
    static boolean present(Context context, String pack) { return onTv(context, pack) || inside(context, pack); }

    private static boolean inside(Context context, String pack) {
        try {
            String[] files = context.getAssets().list(pack);
            return files != null && files.length > 0;
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

    /** The collections the installer was told to bring, still missing on the TV. */
    static List<String> missing(Context context) {
        List<String> wanted = new ArrayList<>();
        File chosen = new File(root(context), CHOSEN);
        if (!chosen.isFile()) return wanted;
        for (String line : Updater.read(chosen).split("\\s+")) {
            String pack = line.trim();
            // One still inside the app would only be a second copy: it comes once the app no longer carries it.
            if (Arrays.asList(ALL).contains(pack) && !onTv(context, pack) && !inside(context, pack)) wanted.add(pack);
        }
        return wanted;
    }

    /**
     * Away from the main thread: fetches the list signed with the update key, then each missing
     * collection, checks its fingerprint, opens it in its folder and marks it whole. A failure
     * leaves nothing half done, and the next start of the Home tries again.
     */
    static void fetchChosen(Context context) {
        if (missing(context).isEmpty() || !fetching.compareAndSet(false, true)) return;
        Background.run(() -> {
            try {
                byte[] list = Updater.fetch(BASE + "sfondi.json");
                Updater.verify(list, Updater.fetch(BASE + "sfondi.json.sig"));
                JSONArray packs = new JSONObject(new String(list, StandardCharsets.UTF_8)).getJSONArray("packs");
                for (String pack : missing(context)) {
                    for (int index = 0; index < packs.length(); index++) {
                        JSONObject item = packs.getJSONObject(index);
                        if (pack.equals(item.getString("id"))) fetch(context, pack, item);
                    }
                }
            } catch (Updater.Failure | JSONException | IOException | GeneralSecurityException e) {
                // Not now: the network, the room or GitHub. The next look tries again.
            } finally {
                fetching.set(false);
            }
        });
    }

    private static void fetch(Context context, String pack, JSONObject item)
            throws JSONException, IOException, GeneralSecurityException {
        long size = item.getLong("bytes");
        File root = root(context);
        if (!root.exists() && !root.mkdirs()) throw new IOException("room");
        if (new StatFs(root.getPath()).getAvailableBytes() < size * ROOM_TIMES + ROOM_SPARE) throw new IOException("room");
        String url = item.getString("url");
        if (!url.startsWith("https://")) throw new IOException("url");
        File archive = new File(root, pack + ".tar");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        HttpURLConnection connection = Updater.open(url);
        try (InputStream input = connection.getInputStream(); OutputStream output = new FileOutputStream(archive)) {
            byte[] chunk = new byte[65536];
            for (int read; (read = input.read(chunk)) > 0; ) {
                output.write(chunk, 0, read);
                digest.update(chunk, 0, read);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", value));
        try {
            if (archive.length() != size || !hex.toString().equals(item.getString("sha256"))) throw new IOException("fingerprint");
            File folder = new File(root, pack);
            empty(folder);
            Tar.unpack(archive, root, pack);
            Files.write(new File(folder, COMPLETE).toPath(), new byte[0]);
        } finally {
            archive.delete();
        }
    }

    private static void empty(File folder) {
        File[] inside = folder.listFiles();
        if (inside != null) for (File file : inside) {
            if (file.isDirectory()) empty(file);
            file.delete();
        }
    }

    /** One file of a collection, from the TV when the collection is there; anything else from inside the app. */
    static InputStream open(Context context, String path) throws IOException {
        String pack = packOf(path);
        if (pack != null && onTv(context, pack)) return new FileInputStream(new File(root(context), path));
        return context.getAssets().open(path);
    }
}
