package it.simplemal.firehome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
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
import java.security.DigestInputStream;
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
 *
 * Before the Home shows, only the picture it opens on comes, cut out of the archive by its place in
 * the signed list; the collections follow quietly, the user's design of 2026-11-04. Each is opened
 * as it arrives, so it needs no more room than itself.
 */
final class Packs {
    /** Every collection, by its folder: the weather's three and the four that change on a timer. */
    static final String[] ALL = {Look.PICTURES_FOLDER, Look.PHOTOS_FOLDER, Look.AERIAL_FOLDER, Look.ABSTRACT_FOLDER,
        Look.ABSTRACT_3D_FOLDER, Look.NOX_FOLDER, Look.NATURE_FOLDER};
    static final String ROOT = "sfondi";
    /** Written last, after the last file: a folder without it is a download that stopped half way. */
    static final String COMPLETE = ".completo";
    /** Written after the first picture of a collection whose rest is still to come. */
    static final String PARTIAL = ".inizio";
    /** A collection being opened, before it takes the place of the one there. */
    private static final String INCOMING = ".in-arrivo";
    /** One picture cut out of an archive is never larger than this. */
    private static final long MAX_PART = 16L * 1024 * 1024;
    /** Called on the main thread when something arrives that the Home could show. */
    static volatile Runnable arrived;
    /** Written by the installer: the collections chosen, one per line. Salotto fetches the missing ones. */
    static final String CHOSEN = "scelte";
    private static final String BASE = "https://github.com/" + Fire.UPDATES_REPO + "/releases/download/sfondi/";
    private static final AtomicBoolean fetching = new AtomicBoolean();
    /** What is coming in now, for the page that waits for it. */
    private static volatile String coming;
    private static volatile long received;
    private static volatile long expected = 1;

    static boolean fetching() { return fetching.get(); }

    /** How much of `pack` has come, 0 to 100, or -1 if it is not the one coming in now. */
    static int percent(String pack) {
        return pack != null && pack.equals(coming) ? (int) Math.min(100, received * 100 / Math.max(1, expected)) : -1;
    }

    private Packs() {}

    static File root(Context context) { return Updater.shared(context, ROOT); }

    /** On the TV, whole. */
    static boolean onTv(Context context, String pack) {
        return new File(new File(root(context), pack), COMPLETE).isFile();
    }

    /** Only its first picture so far: the Home can show it, the rest is coming. */
    static boolean partial(Context context, String pack) {
        return new File(new File(root(context), pack), PARTIAL).isFile();
    }

    /** On the TV, whole or with its first picture, or inside the app: a collection that is none has no row anywhere. */
    static boolean present(Context context, String pack) {
        return onTv(context, pack) || partial(context, pack) || inside(context, pack);
    }

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
        if (pack != null && (onTv(context, pack) || partial(context, pack))) {
            String[] names = new File(root(context), path).list((folder, name) -> !name.startsWith("."));
            if (names == null) return new String[0];
            Arrays.sort(names);
            return names;
        }
        return context.getAssets().list(path);
    }

    /**
     * The collections this TV should have: those chosen in the installer, or every one on a TV
     * installed before the choice existed, the user's decision of 2026-11-04. Never one deleted
     * with the trash.
     */
    static List<String> wanted(Context context) {
        List<String> names = new ArrayList<>();
        File chosen = new File(root(context), CHOSEN);
        List<String> listed = chosen.isFile() ? Arrays.asList(Updater.read(chosen).split("\\s+")) : Arrays.asList(ALL);
        for (String pack : ALL) {
            if (listed.contains(pack) && !Look.setTaken(context, pack)) names.add(pack);
        }
        return names;
    }

    /** Wanted and not yet here. One still inside the app would only be a second copy. */
    static List<String> missing(Context context) {
        List<String> names = new ArrayList<>();
        for (String pack : wanted(context)) {
            if (!onTv(context, pack) && !inside(context, pack)) names.add(pack);
        }
        return names;
    }

    /** Offered where a backdrop is chosen before it may have arrived: on the TV, or on its way. */
    static boolean offered(Context context, String pack) {
        return present(context, pack) || wanted(context).contains(pack);
    }

    /** The list signed with the update key: its collections, each with the place of every file in its archive. */
    private static JSONArray signedList() throws Updater.Failure, JSONException {
        byte[] list = Updater.fetch(BASE + "sfondi.json");
        Updater.verify(list, Updater.fetch(BASE + "sfondi.json.sig"));
        return new JSONObject(new String(list, StandardCharsets.UTF_8)).getJSONArray("packs");
    }

    /** Where each file sits in its archive, signed apart from the list: by collection, then by path. */
    private static JSONObject signedIndex() throws Updater.Failure, JSONException {
        byte[] index = Updater.fetch(BASE + "sfondi-indice.json");
        Updater.verify(index, Updater.fetch(BASE + "sfondi-indice.json.sig"));
        return new JSONObject(new String(index, StandardCharsets.UTF_8)).getJSONObject("index");
    }

    private static JSONObject itemOf(JSONArray packs, String pack) throws JSONException {
        for (int index = 0; index < packs.length(); index++) {
            JSONObject item = packs.getJSONObject(index);
            if (pack.equals(item.getString("id"))) return item;
        }
        return null;
    }

    /**
     * Away from the main thread: fetches the signed list, then the first picture of the chosen
     * backdrop's collection, then each missing collection whole. A failure leaves nothing half
     * done, and the next start of the Home tries again.
     */
    static void fetchChosen(Context context) {
        if (missing(context).isEmpty() || !fetching.compareAndSet(false, true)) return;
        Background.run(() -> {
            try {
                JSONArray packs = signedList();
                List<String> order = missing(context);
                String first = Look.chosenPack(context);
                if (first != null && order.remove(first)) {
                    order.add(0, first);
                    JSONObject item = itemOf(packs, first);
                    try {
                        if (item != null && !partial(context, first)) firstPicture(context, first, item, signedIndex());
                    } catch (Updater.Failure | JSONException | IOException | GeneralSecurityException e) {
                        // Without its first picture the collection still comes whole, below.
                    }
                }
                for (String pack : order) {
                    JSONObject item = itemOf(packs, pack);
                    if (item != null) fetch(context, pack, item);
                }
            } catch (Updater.Failure | JSONException | IOException | GeneralSecurityException e) {
                // Not now: the network, the room or GitHub. The next look tries again.
            } finally {
                coming = null;
                fetching.set(false);
            }
        });
    }

    /**
     * Blocking, away from the main thread: only what the Home opens on, out of `pack`. False if it
     * could not come; the Home then shows something else until the collection arrives.
     */
    static boolean fetchFirst(Context context, String pack) {
        if (onTv(context, pack) || partial(context, pack) || inside(context, pack)) return true;
        try {
            JSONObject item = itemOf(signedList(), pack);
            return item != null && firstPicture(context, pack, item, signedIndex());
        } catch (Updater.Failure | JSONException | IOException | GeneralSecurityException e) {
            return false;
        }
    }

    /** A weather collection needs its table first, to know which of its pictures this hour and weather want. */
    private static boolean firstPicture(Context context, String pack, JSONObject item, JSONObject indexes)
            throws JSONException, IOException, GeneralSecurityException {
        JSONObject index = indexes.optJSONObject(pack);
        if (index == null) return false;
        String url = item.getString("url");
        String picture;
        if (Weather.weatherPack(pack)) {
            part(context, url, index, Weather.tableOf(pack));
            picture = Weather.pictureIn(context, pack);
        } else {
            List<String> names = new ArrayList<>();
            for (java.util.Iterator<String> keys = index.keys(); keys.hasNext(); ) names.add(keys.next());
            java.util.Collections.sort(names);
            picture = Look.firstPicture(context, pack, names);
        }
        if (picture == null || !index.has(picture)) return false;
        part(context, url, index, picture);
        Files.write(new File(new File(root(context), pack), PARTIAL).toPath(), new byte[0]);
        tell();
        return true;
    }

    /** One file cut out of the archive by its place in the signed list, checked against its fingerprint. */
    private static void part(Context context, String url, JSONObject index, String name)
            throws JSONException, IOException, GeneralSecurityException {
        JSONArray at = index.getJSONArray(name);
        long offset = at.getLong(0);
        long size = at.getLong(1);
        if (offset < 0 || size <= 0 || size > MAX_PART || !url.startsWith("https://") || name.contains("..")) throw new IOException("index");
        File target = new File(root(context), name);
        if (!target.getCanonicalPath().startsWith(root(context).getCanonicalPath() + File.separator)) throw new IOException("path");
        HttpURLConnection connection = Updater.openPart(url, offset, size);
        byte[] body = new byte[(int) size];
        try (InputStream input = connection.getInputStream()) {
            int done = 0;
            for (int read; done < body.length && (read = input.read(body, done, body.length - done)) > 0; ) done += read;
            if (done != body.length) throw new IOException("short");
        }
        if (!hex(MessageDigest.getInstance("SHA-256").digest(body)).equals(at.getString(2))) throw new IOException("fingerprint");
        File parent = target.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) throw new IOException("room");
        File temporary = new File(parent, "." + target.getName() + ".part");
        Files.write(temporary.toPath(), body);
        if (!temporary.renameTo(target)) throw new IOException("rename");
    }

    private static String hex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte value : bytes) hex.append(String.format(Locale.ROOT, "%02x", value));
        return hex.toString();
    }

    private static void tell() {
        Runnable then = arrived;
        if (then != null) new Handler(Looper.getMainLooper()).post(then);
    }

    /**
     * The whole collection, opened as it arrives into a folder of its own, its fingerprint checked
     * over every byte; only then does it take the place of what was there. It needs the room of
     * itself, not of an archive and its contents.
     */
    private static void fetch(Context context, String pack, JSONObject item)
            throws JSONException, IOException, GeneralSecurityException {
        long size = item.getLong("bytes");
        File root = root(context);
        if (!root.exists() && !root.mkdirs()) throw new IOException("room");
        if (new StatFs(root.getPath()).getAvailableBytes() < size) throw new IOException("room");
        String url = item.getString("url");
        if (!url.startsWith("https://")) throw new IOException("url");
        File incoming = new File(root, INCOMING);
        empty(incoming);
        if (!incoming.exists() && !incoming.mkdirs()) throw new IOException("room");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        received = 0;
        expected = size;
        coming = pack;
        HttpURLConnection connection = Updater.open(url);
        try {
            // Skipping must read too: a skipped byte would be missing from the fingerprint.
            try (InputStream input = new Reading(new DigestInputStream(new Counting(connection.getInputStream()), digest))) {
                Tar.unpack(input, incoming, pack);
                // What is left after the end marker counts for the fingerprint too.
                byte[] rest = new byte[65536];
                while (input.read(rest) > 0) { }
            }
            if (received != size || !hex(digest.digest()).equals(item.getString("sha256"))) throw new IOException("fingerprint");
            File folder = new File(root, pack);
            File old = new File(root, INCOMING + "-" + pack);
            empty(old);
            old.delete();
            if (folder.exists() && !folder.renameTo(old)) throw new IOException("rename");
            if (!new File(incoming, pack).renameTo(folder)) throw new IOException("rename");
            Files.write(new File(folder, COMPLETE).toPath(), new byte[0]);
            empty(old);
            old.delete();
            tell();
        } finally {
            empty(incoming);
            incoming.delete();
        }
    }

    /** Skips by reading. */
    private static final class Reading extends java.io.FilterInputStream {
        private final byte[] waste = new byte[65536];

        Reading(InputStream input) { super(input); }

        @Override public long skip(long count) throws IOException {
            long gone = 0;
            while (gone < count) {
                int read = read(waste, 0, (int) Math.min(waste.length, count - gone));
                if (read < 0) break;
                gone += read;
            }
            return gone;
        }
    }

    /** Counts what comes, for the page that shows how much has. */
    private static final class Counting extends java.io.FilterInputStream {
        Counting(InputStream input) { super(input); }

        @Override public int read() throws IOException {
            int value = super.read();
            if (value >= 0) received++;
            return value;
        }

        @Override public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) received += read;
            return read;
        }

        @Override public long skip(long count) throws IOException {
            long gone = super.skip(count);
            if (gone > 0) received += gone;
            return gone;
        }
    }

    private static void empty(File folder) {
        File[] inside = folder.listFiles();
        if (inside != null) for (File file : inside) {
            if (file.isDirectory()) empty(file);
            file.delete();
        }
    }

    /** One file of a collection, from the TV when it is there, even before the rest; anything else from inside the app. */
    static InputStream open(Context context, String path) throws IOException {
        String pack = packOf(path);
        File file = new File(root(context), path);
        if (pack != null && (onTv(context, pack) || file.isFile())) return new FileInputStream(file);
        return context.getAssets().open(path);
    }
}
