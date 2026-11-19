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
 * The collections of backdrops. Salotto TV Setup writes which ones were chosen in `sfondi/scelte`,
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
    /**
     * Beside each collection, `miniature-` and its name: its small copies, which the grid and the
     * mosaic read in a moment, `grandi` and `piccole`. A small archive of their own, so that a
     * collection already on the TV needs only these; whole, like a collection, with `.completo`.
     */
    private static final String THUMBS = "miniature-";
    private static final String THUMBS_LARGE = "/grandi/";
    private static final String THUMBS_SMALL = "/piccole/";
    /** Written last, after the last file: a folder without it is a download that stopped half way. */
    static final String COMPLETE = ".completo";
    /**
     * Written after the first picture of a collection whose rest is still to come, with the names of
     * the files that came, each checked: until the collection is whole, only those are read.
     */
    static final String PARTIAL = ".inizio";
    /** One picture cut out of an archive is never larger than this. */
    private static final long MAX_PART = 16L * 1024 * 1024;
    /** Called on the main thread when something arrives that the Home could show. */
    static volatile Runnable arrived;
    /** Written by the installer: the collections chosen, one per line. Salotto fetches the missing ones. */
    static final String CHOSEN = "scelte";
    private static final String BASE = "https://github.com/" + Fire.UPDATES_REPO + "/releases/download/sfondi/";
    private static final AtomicBoolean fetching = new AtomicBoolean();
    private static volatile boolean thumbsTried;
    /** What is coming in now, for the page that waits for it. */
    private static volatile String coming;
    private static volatile long received;
    private static volatile long expected = 1;
    private static volatile List<String> queue = new ArrayList<>();
    /**
     * A copy of the last signed list, with its signature, checked again when read: the collections
     * published after this version of Salotto are known from it, even with no network.
     */
    private static final String LIST_COPY = ".elenco.json";
    private static final String LIST_COPY_SIG = ".elenco.json.sig";
    /** Beside each file, in a collection whole on the TV: its fingerprint, as the signed index gave it. */
    private static final String PRINTS = ".impronte";
    /** The still collage of a collection added later, which the app cannot carry: `collage-` and its name. */
    private static final String COLLAGE = "collage-";
    private static final String COLLAGE_FILE = ".webp";
    /** The collections are looked at for new versions of their files at most this often. */
    private static final long UPDATES_EVERY_MS = 12 * 60 * 60 * 1000L;
    private static volatile long updatesAt;
    /** Only a plain name: it becomes a folder, and a line in `scelte`. */
    private static final java.util.regex.Pattern EXTRA_ID = java.util.regex.Pattern.compile("[a-z0-9][a-z0-9-]{0,39}");
    private static final int EXTRA_NAME_MAX = 40;
    /** The only kind a collection added later can be: one that changes on a timer. The weather's need their own code. */
    private static final String TIMED_KIND = "timed";

    /** A collection published after this version of Salotto, named in the signed list. */
    static final class Extra {
        final String id;
        final String name;

        Extra(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static volatile List<Extra> extras;

    /** Why the last fetch stopped: told to the user, who can do something only about the room. */
    enum Stop { NONE, ROOM, NETWORK, SERVER, DAMAGED }
    private static volatile Stop stop = Stop.NONE;
    /** For Stop.ROOM: what the collections that did not fit weigh, and what was free. */
    private static volatile long roomNeeded;
    private static volatile long roomFree;
    /** The two signed lists are small: anything larger is not theirs. */
    private static final int MAX_LIST = 1024 * 1024;
    /** The weather is read again before its picture is chosen, but the Home does not wait longer for it. */
    private static final long WEATHER_WAIT_MS = 5000;

    static boolean fetching() { return fetching.get(); }

    /** How much of `pack` has come, 0 to 100, or -1 if it is not the one coming in now. */
    static int percent(String pack) {
        return pack != null && pack.equals(coming) ? (int) Math.min(100, received * 100 / Math.max(1, expected)) : -1;
    }

    private Packs() {}

    static File root(Context context) { return Updater.shared(context, ROOT); }

    /** The collections added later, from the copy of the signed list: none until it has come once. */
    static List<Extra> extras(Context context) {
        List<Extra> known = extras;
        if (known != null) return known;
        known = new ArrayList<>();
        try {
            byte[] list = Bytes.read(new File(root(context), LIST_COPY));
            Updater.verify(list, Bytes.read(new File(root(context), LIST_COPY_SIG)));
            known = extrasIn(new JSONObject(new String(list, StandardCharsets.UTF_8)).getJSONArray("packs"));
        } catch (Updater.Failure | JSONException | IOException e) {
            // No copy yet, or not ours: only the collections this version knows.
        }
        extras = known;
        return known;
    }

    private static List<Extra> extrasIn(JSONArray packs) {
        List<Extra> found = new ArrayList<>();
        for (int index = 0; index < packs.length(); index++) {
            JSONObject item = packs.optJSONObject(index);
            if (item == null) continue;
            String id = item.optString("id");
            String name = item.optString("name").trim();
            if (!EXTRA_ID.matcher(id).matches() || Arrays.asList(ALL).contains(id) || !TIMED_KIND.equals(item.optString("kind"))
                || name.isEmpty() || name.length() > EXTRA_NAME_MAX) continue;
            found.add(new Extra(id, name));
        }
        return found;
    }

    /** One of the collections added later, by its folder, or null. */
    static Extra extra(Context context, String id) {
        if (id == null) return null;
        for (Extra one : extras(context)) if (one.id.equals(id)) return one;
        return null;
    }

    /** Every collection, the ones of this version first, then those added later. */
    static List<String> all(Context context) {
        List<String> names = new ArrayList<>(Arrays.asList(ALL));
        for (Extra one : extras(context)) names.add(one.id);
        return names;
    }

    /** The collage of a collection added later, or null: the app carries only those of its own. */
    static InputStream collage(Context context, String pack) {
        if (extra(context, pack) == null) return null;
        try {
            return new FileInputStream(new File(root(context), COLLAGE + pack + COLLAGE_FILE));
        } catch (IOException e) {
            return null;
        }
    }

    /** On the TV, whole. */
    static boolean onTv(Context context, String pack) {
        return new File(new File(root(context), pack), COMPLETE).isFile();
    }

    /** Its small copies are on the TV, whole. */
    static boolean thumbsOnTv(Context context, String pack) {
        return new File(new File(root(context), THUMBS + pack), COMPLETE).isFile();
    }

    /**
     * The small copy of a picture of a collection, `small` for the smallest: null if it is not on
     * the TV, and the picture itself is to be read instead.
     */
    static InputStream thumbnail(Context context, String path, boolean small) {
        String pack = packOf(context, path);
        if (pack == null || !thumbsOnTv(context, pack)) return null;
        String inside = path.substring(pack.length() + 1);
        try {
            return new FileInputStream(new File(root(context), THUMBS + pack + (small ? THUMBS_SMALL : THUMBS_LARGE) + inside));
        } catch (IOException e) {
            return null;
        }
    }

    /** A collection taken away whole loses its small copies too. */
    static void forgetThumbs(Context context, String pack) {
        delete(new File(root(context), THUMBS + pack));
    }

    private static void delete(File file) {
        File[] inside = file.listFiles();
        if (inside != null) for (File one : inside) delete(one);
        file.delete();
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
    private static String packOf(Context context, String path) {
        int slash = path.indexOf('/');
        String first = slash > 0 ? path.substring(0, slash) : path;
        return all(context).contains(first) ? first : null;
    }

    /**
     * What a folder holds, sorted, without the marks: from the TV when the collection is there. One
     * still coming shows only the files that came first, checked one by one, not those arriving.
     */
    static String[] list(Context context, String path) throws IOException {
        String pack = packOf(context, path);
        if (pack != null && onTv(context, pack)) {
            String[] names = new File(root(context), path).list((folder, name) -> !name.startsWith("."));
            if (names == null) return new String[0];
            Arrays.sort(names);
            return names;
        }
        if (pack != null && partial(context, pack)) {
            String inside = path.endsWith("/") ? path : path + "/";
            List<String> names = new ArrayList<>();
            for (String name : firstNames(context, pack)) {
                if (name.startsWith(inside) && name.indexOf('/', inside.length()) < 0) names.add(name.substring(inside.length()));
            }
            java.util.Collections.sort(names);
            return names.toArray(new String[0]);
        }
        return context.getAssets().list(path);
    }

    /** The files that came first, as `.inizio` names them. */
    private static java.util.Set<String> firstNames(Context context, String pack) {
        java.util.Set<String> names = new java.util.HashSet<>();
        File marks = new File(new File(root(context), pack), PARTIAL);
        if (!marks.isFile()) return names;
        for (String line : Updater.read(marks).split("\n")) if (!line.trim().isEmpty()) names.add(line.trim());
        return names;
    }

    /**
     * The collections this TV should have: those chosen in the installer, or every one on a TV
     * installed before the choice existed, the user's decision of 2026-11-04. Never one deleted
     * with the trash.
     */
    static List<String> wanted(Context context) {
        List<String> names = new ArrayList<>();
        File chosen = new File(root(context), CHOSEN);
        // Before the choice existed, every collection of then: one added later only when asked for.
        List<String> listed = chosen.isFile() ? Arrays.asList(Updater.read(chosen).split("\\s+")) : Arrays.asList(ALL);
        for (String pack : all(context)) {
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

    /** Whole on the TV, but without their small copies: from before they left the app, or not yet come. */
    static List<String> thumbsMissing(Context context) {
        List<String> names = new ArrayList<>();
        for (String pack : all(context)) {
            if (onTv(context, pack) && !thumbsOnTv(context, pack) && !Look.setTaken(context, pack)) names.add(pack);
        }
        return names;
    }

    /**
     * Has a row where a backdrop is chosen: whole on the TV, or inside the app. One with only its
     * first picture only if the Home is showing it, so the choice in use is never without its row;
     * the user's rule of 2026-11-05: a collection that is not here is not listed.
     */
    static boolean listed(Context context, String pack) {
        return onTv(context, pack) || inside(context, pack) || partial(context, pack) && pack.equals(Look.homePack(context));
    }

    /** The collections coming in now, in their order, for the page that shows them arriving. */
    static List<String> queue() { return queue; }

    /** Why the last fetch did not bring everything, or NONE. */
    static Stop stop() { return stop; }
    static long roomNeeded() { return roomNeeded; }
    static long roomFree() { return roomFree; }

    /** Not enough room for a collection: the next, smaller or not, is tried all the same. */
    private static final class NoRoom extends IOException {
        NoRoom() { super("room"); }
    }

    /** What the user is told: only the room is theirs to solve; the rest Salotto tries again by itself. */
    private static Stop why(Exception e) {
        if (e instanceof NoRoom) return Stop.ROOM;
        if (e instanceof GeneralSecurityException) return Stop.DAMAGED;
        if (e instanceof JSONException) return Stop.SERVER;
        if (e instanceof Updater.Failure) return ((Updater.Failure) e).error == UpdateError.F104 ? Stop.DAMAGED : Stop.SERVER;
        String what = e.getMessage();
        if ("fingerprint".equals(what) || "index".equals(what) || "path".equals(what)) return Stop.DAMAGED;
        if ("HTTP".equals(what) || "large".equals(what)) return Stop.SERVER;
        return Stop.NETWORK;
    }

    /**
     * Asked by the user, from the page of backdrops: the collection becomes wanted, even one taken
     * away before, and every missing one starts coming. The user's rule of 2026-11-09: a collection
     * taken away never comes back by itself, only when the user asks for it here.
     */
    static void ask(Context context, String pack) {
        Look.untakeSet(context, pack);
        File chosen = new File(root(context), CHOSEN);
        if (chosen.isFile()) {
            String listed = Updater.read(chosen);
            if (!Arrays.asList(listed.split("\\s+")).contains(pack)) Updater.write(chosen, listed.trim() + "\n" + pack + "\n");
        }
        // One fetch at a time: one already going takes this one too, at the end.
        if (fetching() && !queue.contains(pack)) {
            List<String> longer = new ArrayList<>(queue);
            longer.add(pack);
            queue = longer;
        }
        fetchNow(context);
    }

    /** Blocking: what one collection weighs, from the signed list. -1 if it cannot be read. */
    static long bytes(String pack) {
        try {
            JSONObject item = itemOf(signedList(), pack);
            return item == null ? -1 : item.getLong("bytes");
        } catch (Updater.Failure | JSONException | IOException e) {
            return -1;
        }
    }

    /** Blocking: how much the missing collections weigh, from the signed list. -1 if it cannot be read. */
    static long missingBytes(Context context) {
        try {
            JSONArray packs = signedList();
            long total = 0;
            for (String pack : missing(context)) {
                JSONObject item = itemOf(packs, pack);
                if (item != null) total += item.getLong("bytes");
            }
            return total;
        } catch (Updater.Failure | JSONException | IOException e) {
            return -1;
        }
    }

    /** The list signed with the update key: its collections, each with the place of every file in its archive. */
    private static JSONArray signedList() throws Updater.Failure, JSONException, IOException {
        return signedList(null);
    }

    /** With `context`, a copy is kept on the TV, and the collections added later are known from it. */
    private static JSONArray signedList(Context context) throws Updater.Failure, JSONException, IOException {
        byte[] list = small(BASE + "sfondi.json");
        byte[] signature = small(BASE + "sfondi.json.sig");
        Updater.verify(list, signature);
        JSONArray packs = new JSONObject(new String(list, StandardCharsets.UTF_8)).getJSONArray("packs");
        if (context != null) {
            File root = root(context);
            if (root.isDirectory() || root.mkdirs()) {
                Bytes.write(new File(root, LIST_COPY), list);
                Bytes.write(new File(root, LIST_COPY_SIG), signature);
            }
            extras = extrasIn(packs);
        }
        return packs;
    }

    /** Where each file sits in its archive, signed apart from the list: by collection, then by path. */
    private static JSONObject signedIndex() throws Updater.Failure, JSONException, IOException {
        byte[] index = small(BASE + "sfondi-indice.json");
        Updater.verify(index, small(BASE + "sfondi-indice.json.sig"));
        return new JSONObject(new String(index, StandardCharsets.UTF_8)).getJSONObject("index");
    }

    /** A small file, its failure kept as it is: the network, or GitHub answering wrong ("HTTP"). */
    private static byte[] small(String url) throws IOException {
        HttpURLConnection connection = Updater.open(url);
        java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
        try (InputStream input = connection.getInputStream()) {
            byte[] chunk = new byte[8192];
            for (int read; (read = input.read(chunk)) > 0; ) {
                body.write(chunk, 0, read);
                if (body.size() > MAX_LIST) throw new IOException("large");
            }
        }
        return body.toByteArray();
    }

    private static JSONObject itemOf(JSONArray packs, String pack) throws JSONException {
        for (int index = 0; index < packs.length(); index++) {
            JSONObject item = packs.getJSONObject(index);
            if (pack.equals(item.getString("id"))) return item;
        }
        return null;
    }

    /**
     * Away from the main thread: fetches the signed list, then the first picture of the collection
     * the Home opens on, then each missing collection whole. A failure leaves nothing half done, and
     * the next start of the Home tries again.
     */
    static void fetchChosen(Context context) { fetchAll(context, false); }

    /** On the first start, asked by the user: every missing collection, the lightest first, so one is ready soon. */
    static void fetchNow(Context context) { fetchAll(context, true); }

    private static void fetchAll(Context context, boolean lightestFirst) {
        boolean onlyThumbs = missing(context).isEmpty();
        boolean updates = System.currentTimeMillis() - updatesAt >= UPDATES_EVERY_MS;
        // The Home asks at every return: the small copies alone are tried once while Salotto runs.
        if (onlyThumbs && !updates && (thumbsTried || thumbsMissing(context).isEmpty()) || !fetching.compareAndSet(false, true)) return;
        if (onlyThumbs) thumbsTried = true;
        stop = Stop.NONE;
        queue = missing(context);
        Background.run(() -> {
            try {
                JSONArray packs = signedList(context);
                List<String> order = missing(context);
                if (lightestFirst) {
                    order.sort((one, other) -> Long.compare(bytesOf(packs, one), bytesOf(packs, other)));
                } else {
                    String first = Look.homePack(context);
                    if (first != null && order.remove(first)) {
                        order.add(0, first);
                        JSONObject item = itemOf(packs, first);
                        try {
                            if (item != null && !partial(context, first)) firstPicture(context, first, item, signedIndex());
                        } catch (Updater.Failure | JSONException | IOException | GeneralSecurityException e) {
                            // Without its first picture the collection still comes whole, below.
                        }
                    }
                }
                queue = new ArrayList<>(order);
                // First the small copies of the collections already here: little, and the grid wants them now.
                for (String pack : thumbsMissing(context)) {
                    JSONObject item = itemOf(packs, pack);
                    JSONObject thumbs = item != null ? item.optJSONObject("thumbs") : null;
                    if (thumbs == null) continue;
                    try {
                        fetchThumbs(context, pack, thumbs);
                    } catch (NoRoom e) {
                        // The collections need the room more: these come another time.
                    }
                }
                long needed = 0;
                for (int at = 0; at < order.size() || joined(context, order); at++) {
                    String pack = order.get(at);
                    JSONObject item = itemOf(packs, pack);
                    if (item == null) continue;
                    try {
                        fetchWhole(context, pack, item);
                    } catch (NoRoom e) {
                        // Too large for what is free: the others may still fit, the user's choice of 2026-11-05.
                        needed += item.getLong("bytes");
                    }
                }
                // Last, and quietly: new versions of what is here already, and the collages of the collections added later.
                if (updates) {
                    coming = null;
                    if (update(context, packs)) updatesAt = System.currentTimeMillis();
                }
                if (needed > 0) {
                    roomNeeded = needed;
                    roomFree = new StatFs(root(context).getPath()).getAvailableBytes();
                    stop = Stop.ROOM;
                }
            } catch (Updater.Failure | JSONException | IOException | GeneralSecurityException e) {
                // Not now: the network, GitHub or a damaged archive. The next look tries again.
                stop = why(e);
            } finally {
                coming = null;
                queue = new ArrayList<>();
                fetching.set(false);
                tell();
            }
        });
    }

    /**
     * The collections whole on the TV, brought to the version in the signed list: only the files
     * that changed come, each checked, and the ones no longer in it go. A collection that came
     * before versions were written, with an empty `.completo`, has its files read once to know
     * which. Their small copies come again whole, being small; the collages of the collections added
     * later come too. True if everything is now as the list says.
     */
    private static boolean update(Context context, JSONArray packs) {
        JSONObject indexes = null;
        boolean done = true;
        for (String pack : all(context)) {
            try {
                JSONObject item = itemOf(packs, pack);
                if (item == null || Look.setTaken(context, pack)) continue;
                if (onTv(context, pack) && !item.getString("sha256").equals(version(new File(root(context), pack)))) {
                    if (indexes == null) indexes = signedIndex();
                    renew(context, pack, item, indexes.optJSONObject(pack));
                }
                JSONObject thumbs = item.optJSONObject("thumbs");
                if (thumbs != null && thumbsOnTv(context, pack)
                    && !thumbs.getString("sha256").equals(version(new File(root(context), THUMBS + pack)))) {
                    fetchThumbs(context, pack, thumbs);
                    tell();
                }
                if (extra(context, pack) != null) fetchCollage(context, pack, item.optJSONObject("collage"));
            } catch (Updater.Failure | JSONException | IOException | GeneralSecurityException e) {
                // This one another time: the others may still come.
                done = false;
            }
        }
        return done;
    }

    /** What `.completo` says it came from: empty if it came before versions were written. */
    private static String version(File folder) {
        return Updater.read(new File(folder, COMPLETE)).trim();
    }

    /** One collection, from what is on the TV to the version of `item`, file by file. */
    private static void renew(Context context, String pack, JSONObject item, JSONObject index)
            throws JSONException, IOException, GeneralSecurityException {
        if (index == null) throw new IOException("index");
        File root = root(context);
        File folder = new File(root, pack);
        java.util.Map<String, String> have = printsHere(folder, pack);
        java.util.Map<String, String> want = printsOf(index);
        List<String> come = new ArrayList<>();
        long size = 0;
        for (java.util.Map.Entry<String, String> one : want.entrySet()) {
            String name = one.getKey();
            if (!name.startsWith(pack + "/")) throw new IOException("index");
            if (Look.taken(context, name) || one.getValue().equals(have.get(name))) continue;
            come.add(name);
            size += index.getJSONArray(name).getLong(1);
        }
        if (new StatFs(root.getPath()).getAvailableBytes() < size) throw new NoRoom();
        String url = item.getString("url");
        for (String name : come) {
            part(context, url, index, name);
            have.put(name, want.get(name));
            if (name.equals(Weather.tableOf(pack))) Weather.forgetTable(pack + "/");
        }
        List<String> gone = new ArrayList<>();
        for (String name : new ArrayList<>(have.keySet())) {
            if (want.containsKey(name)) continue;
            new File(root, name).delete();
            have.remove(name);
            gone.add(name);
        }
        // A picture chosen on its own and published again under another name stays chosen: the same number before the name.
        for (String old : gone) Look.replaced(context, old, sameNumber(old, come));
        writePrints(folder, have);
        Bytes.write(new File(folder, COMPLETE), item.getString("sha256").getBytes(StandardCharsets.UTF_8));
        tell();
    }

    /** Among `names`, the only one in the same folder as `old` that starts with its number, as `028-`. Null if none, or more. */
    private static String sameNumber(String old, List<String> names) {
        int slash = old.lastIndexOf('/');
        String file = old.substring(slash + 1);
        int dash = file.indexOf('-');
        if (dash <= 0) return null;
        String start = old.substring(0, slash + 1) + file.substring(0, dash + 1);
        String found = null;
        for (String name : names) {
            if (!name.startsWith(start) || name.indexOf('/', slash + 1) >= 0) continue;
            if (found != null) return null;
            found = name;
        }
        return found;
    }

    /** The fingerprints of a collection's files, by path from the collections' root, as the signed index gives them. */
    private static java.util.Map<String, String> printsOf(JSONObject index) throws JSONException {
        java.util.Map<String, String> prints = new java.util.TreeMap<>();
        for (java.util.Iterator<String> keys = index.keys(); keys.hasNext(); ) {
            String name = keys.next();
            prints.put(name, index.getJSONArray(name).getString(2));
        }
        return prints;
    }

    /** The fingerprints of the files on the TV: from `.impronte`, or read from each file when it is not there. */
    private static java.util.Map<String, String> printsHere(File folder, String pack) throws IOException, GeneralSecurityException {
        java.util.Map<String, String> prints = new java.util.TreeMap<>();
        File written = new File(folder, PRINTS);
        if (written.isFile()) {
            for (String line : Updater.read(written).split("\n")) {
                int space = line.indexOf(' ');
                if (space > 0) prints.put(line.substring(space + 1), line.substring(0, space));
            }
            return prints;
        }
        readPrints(folder, pack, prints);
        return prints;
    }

    private static void readPrints(File folder, String path, java.util.Map<String, String> prints) throws IOException, GeneralSecurityException {
        File[] inside = folder.listFiles();
        if (inside == null) return;
        for (File file : inside) {
            if (file.getName().startsWith(".")) continue;
            String name = path + "/" + file.getName();
            if (file.isDirectory()) {
                readPrints(file, name, prints);
                continue;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new FileInputStream(file)) {
                byte[] chunk = new byte[65536];
                for (int read; (read = input.read(chunk)) > 0; ) digest.update(chunk, 0, read);
            }
            prints.put(name, hex(digest.digest()));
        }
    }

    private static void writePrints(File folder, java.util.Map<String, String> prints) throws IOException {
        StringBuilder lines = new StringBuilder();
        for (java.util.Map.Entry<String, String> one : prints.entrySet()) lines.append(one.getValue()).append(' ').append(one.getKey()).append('\n');
        Bytes.write(new File(folder, PRINTS), lines.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** The collage of a collection added later, checked against the signed list; only if it is not here already, or changed. */
    private static void fetchCollage(Context context, String pack, JSONObject collage)
            throws JSONException, IOException, GeneralSecurityException {
        if (collage == null) return;
        String url = collage.getString("url");
        String sha = collage.getString("sha256");
        if (!url.startsWith("https://")) throw new IOException("url");
        File file = new File(root(context), COLLAGE + pack + COLLAGE_FILE);
        if (file.isFile() && hex(MessageDigest.getInstance("SHA-256").digest(Bytes.read(file))).equals(sha)) return;
        byte[] body = small(url);
        if (!hex(MessageDigest.getInstance("SHA-256").digest(body)).equals(sha)) throw new IOException("fingerprint");
        Bytes.write(file, body);
        tell();
    }

    /** Asked by the user while the others came: added at the end. False if none was. */
    private static boolean joined(Context context, List<String> order) {
        boolean more = false;
        for (String pack : missing(context)) {
            if (order.contains(pack)) continue;
            order.add(pack);
            more = true;
        }
        if (more) queue = new ArrayList<>(order);
        return more;
    }

    /** A collection with its small copies first, on one bar: the copies are a few MB, the collection tens. */
    private static void fetchWhole(Context context, String pack, JSONObject item)
            throws JSONException, IOException, GeneralSecurityException {
        JSONObject thumbs = thumbsOnTv(context, pack) ? null : item.optJSONObject("thumbs");
        received = 0;
        expected = item.getLong("bytes") + (thumbs != null ? thumbs.getLong("bytes") : 0);
        coming = pack;
        if (thumbs != null) fetchThumbs(context, pack, thumbs);
        fetch(context, pack, item);
    }

    /** The small copies of a collection, checked over every byte like the collection, and only then marked whole. */
    private static void fetchThumbs(Context context, String pack, JSONObject thumbs)
            throws JSONException, IOException, GeneralSecurityException {
        long size = thumbs.getLong("bytes");
        File root = root(context);
        if (!root.exists() && !root.mkdirs()) throw new IOException("folder");
        if (new StatFs(root.getPath()).getAvailableBytes() < size) throw new NoRoom();
        String url = thumbs.getString("url");
        if (!url.startsWith("https://")) throw new IOException("url");
        File folder = new File(root, THUMBS + pack);
        delete(folder);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long before = received;
        boolean whole = false;
        HttpURLConnection connection = Updater.open(url);
        try {
            try (InputStream input = new Reading(new DigestInputStream(new Counting(connection.getInputStream()), digest))) {
                try {
                    Tar.unpack(input, root, THUMBS + pack);
                } catch (IOException e) {
                    String what = String.valueOf(e.getMessage());
                    if (what.contains("ENOSPC") || what.contains("No space")) throw new NoRoom();
                    throw e;
                }
                byte[] rest = new byte[65536];
                while (input.read(rest) > 0) { }
            }
            if (received - before != size || !hex(digest.digest()).equals(thumbs.getString("sha256"))) throw new IOException("fingerprint");
            Bytes.write(new File(folder, COMPLETE), thumbs.getString("sha256").getBytes(StandardCharsets.UTF_8));
            whole = true;
        } finally {
            if (!whole) delete(folder);
        }
    }

    private static long bytesOf(JSONArray packs, String pack) {
        try {
            JSONObject item = itemOf(packs, pack);
            return item == null ? Long.MAX_VALUE : item.getLong("bytes");
        } catch (JSONException e) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * Blocking, away from the main thread: only what the Home opens on, out of `pack`. False if it
     * could not come; the Home then shows something else until the collection arrives.
     */
    static boolean fetchFirst(Context context, String pack) {
        if (!wanted(context).contains(pack)) return false;
        if (onTv(context, pack) || partial(context, pack) || inside(context, pack)) return true;
        try {
            JSONObject item = itemOf(signedList(), pack);
            if (Weather.weatherPack(pack)) freshWeather(context);
            return item != null && firstPicture(context, pack, item, signedIndex());
        } catch (Updater.Failure | JSONException | IOException | GeneralSecurityException e) {
            return false;
        }
    }

    /**
     * The weather as it is now, so the picture fetched is the one the Home will want: the saved
     * reading can be from another day, with its sunrise and sunset. Waits a few seconds at most;
     * then the saved reading, or the hour, decides.
     */
    private static void freshWeather(Context context) {
        if (!Weather.active(context)) return;
        Thread reading = new Thread(() -> {
            try {
                Weather.refresh(context);
            } catch (IOException | JSONException e) {
                // The saved reading, or the hour, chooses.
            }
        });
        reading.start();
        try {
            reading.join(WEATHER_WAIT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
            // Not readable through open() until `.inizio` names it, which comes after the picture.
            java.util.Properties table = new java.util.Properties();
            try (InputStream input = new FileInputStream(new File(root(context), Weather.tableOf(pack)))) {
                table.load(input);
            }
            Weather.knowTable(pack + "/", table);
            picture = Weather.pictureIn(context, pack);
        } else {
            List<String> names = new ArrayList<>();
            for (java.util.Iterator<String> keys = index.keys(); keys.hasNext(); ) names.add(keys.next());
            java.util.Collections.sort(names);
            picture = Look.firstPicture(context, pack, names);
        }
        if (picture == null || !index.has(picture)) return false;
        part(context, url, index, picture);
        String names = (Weather.weatherPack(pack) ? Weather.tableOf(pack) + "\n" : "") + picture + "\n";
        Bytes.write(new File(new File(root(context), pack), PARTIAL), names.getBytes(StandardCharsets.UTF_8));
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
        if (!parent.exists() && !parent.mkdirs()) throw new IOException("folder");
        // Checked already, and read only once `.inizio` names it: written in place, never moved.
        Bytes.write(target, body);
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
     * The whole collection, opened as it arrives straight into its own folder, its fingerprint checked
     * over every byte; only then is it marked whole. Nothing is moved: on the Sticks with Android 11
     * moving a folder can be refused for good. It needs the room of itself, not of an archive and its
     * contents. Until it is whole only the files that came first are read, and those stay as they are.
     */
    private static void fetch(Context context, String pack, JSONObject item)
            throws JSONException, IOException, GeneralSecurityException {
        long size = item.getLong("bytes");
        File root = root(context);
        if (!root.exists() && !root.mkdirs()) throw new IOException("folder");
        if (new StatFs(root.getPath()).getAvailableBytes() < size) throw new NoRoom();
        String url = item.getString("url");
        if (!url.startsWith("https://")) throw new IOException("url");
        File folder = new File(root, pack);
        java.util.Set<String> first = firstNames(context, pack);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long before = received;
        boolean whole = false;
        HttpURLConnection connection = Updater.open(url);
        try {
            // Skipping must read too: a skipped byte would be missing from the fingerprint.
            try (InputStream input = new Reading(new DigestInputStream(new Counting(connection.getInputStream()), digest))) {
                try {
                    Tar.unpack(input, root, pack, first);
                } catch (IOException e) {
                    // The room can also end while the files are written, if something else took it meanwhile.
                    String what = String.valueOf(e.getMessage());
                    if (what.contains("ENOSPC") || what.contains("No space")) throw new NoRoom();
                    throw e;
                }
                // What is left after the end marker counts for the fingerprint too.
                byte[] rest = new byte[65536];
                while (input.read(rest) > 0) { }
            }
            if (received - before != size || !hex(digest.digest()).equals(item.getString("sha256"))) throw new IOException("fingerprint");
            // Without the index the prints are made from the files, the first time they are needed.
            JSONObject index = null;
            try {
                index = signedIndex().optJSONObject(pack);
            } catch (Updater.Failure | JSONException | IOException e) {
                // Only slower, once.
            }
            if (index != null) writePrints(folder, printsOf(index));
            Bytes.write(new File(folder, COMPLETE), item.getString("sha256").getBytes(StandardCharsets.UTF_8));
            new File(folder, PARTIAL).delete();
            whole = true;
            tell();
        } finally {
            // A collection that did not come whole keeps only what came first, checked.
            if (!whole) clearBut(folder, folder, first);
        }
    }

    /** Everything in `folder` except the marks and the files named in `keep`, by their path from the collections' root. */
    private static void clearBut(File top, File folder, java.util.Set<String> keep) {
        File[] inside = folder.listFiles();
        if (inside == null) return;
        String root = top.getParentFile().getPath() + File.separator;
        for (File file : inside) {
            if (file.isDirectory()) {
                clearBut(top, file, keep);
                if (file.list() != null && file.list().length == 0) file.delete();
            } else if (!file.getName().equals(PARTIAL) && !keep.contains(file.getPath().substring(root.length()))) {
                file.delete();
            }
        }
        if (folder == top && folder.list() != null && folder.list().length == 0) folder.delete();
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

    /** One file of a collection, from the TV when it is there, even before the rest; anything else from inside the app. */
    static InputStream open(Context context, String path) throws IOException {
        String pack = packOf(context, path);
        File file = new File(root(context), path);
        if (pack != null && (onTv(context, pack) || firstNames(context, pack).contains(path))) return new FileInputStream(file);
        return context.getAssets().open(path);
    }
}
