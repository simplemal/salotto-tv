package it.simplemal.firehome;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.StatFs;
import android.provider.Settings;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Finds and checks a new version. Nothing here changes the installed app. */
final class Updater {
    static final class Failure extends Exception {
        final UpdateError error;
        Failure(UpdateError error) { this.error = error; }
    }

    static final class Release {
        int versionCode;
        String versionName;
        String url;
        String sha256;
        long size;
        final List<String> changes = new ArrayList<>();
    }

    interface Progress { void percent(int done); }

    private static final String BASE = "https://github.com/" + Fire.UPDATES_REPO + "/releases/latest/download/";
    /** Only updates signed by the private half of this key are ever considered. */
    private static final String PUBLIC_KEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEV+Xr4FLe8cXoDAqAen4eIgxam1dviQ81GfwoMv51vrd0sajsazDnwlEzdFR3+VfTHJDTFK7qeOebtfvoWzgePQ==";
    private static final String FILE = "update";
    private static final String APK = "update.apk";
    private static final String PREVIOUS = "update-previous.apk";
    /** A failed look is usually a network still waking up. */
    private static final long RETRY = 10 * Fire.MINUTE_MS;
    /** The script reports well within this; a state left older than this has no one behind it. */
    private static final long STALE = 5 * Fire.MINUTE_MS;
    private static final long ROOM_FACTOR = 3;
    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_MANIFEST = 256 * 1024;
    private static final int PERCENT = 100;

    private static volatile long nextCheck;

    private Updater() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    private static File mediaFolder(Context context) { return context.getExternalMediaDirs()[0]; }

    /** Where Salotto and the install script, which runs as another user, can both read and write. */
    static File shared(Context context, String name) {
        return new File(mediaFolder(context), name);
    }

    static File state(Context context) { return shared(context, "update-state"); }
    static File health(Context context) { return shared(context, "update-health"); }
    static File previous(Context context) { return shared(context, PREVIOUS); }

    static int target(Context context) { return prefs(context).getInt("target", 0); }
    static void setTarget(Context context, int code) { prefs(context).edit().putInt("target", code).commit(); }

    /** True at every start of Salotto and once a day after that: "later" lasts until then. */
    static boolean due() {
        long now = System.currentTimeMillis();
        if (now < nextCheck) return false;
        nextCheck = now + RETRY;
        return true;
    }

    /** Quietly looks for a version to offer. A failure here only means asking again later. */
    static Release offer(Context context) {
        try {
            Release found = check(context);
            nextCheck = System.currentTimeMillis() + Fire.DAY_MS;
            return found;
        } catch (Failure e) {
            return null;
        }
    }

    /** Android's installer only goes forward, so the way back has to be set aside first. */
    static void keepCurrent(Context context) throws Failure {
        try {
            Files.copy(new File(context.getPackageCodePath()).toPath(), previous(context).toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new Failure(UpdateError.R301);
        }
    }

    /** This is the version that was being installed. */
    static boolean installed(Context context) {
        return "installing".equals(read(state(context))) && installedCode(context) == target(context);
    }

    static void clean(Context context) {
        state(context).delete();
        health(context).delete();
        previous(context).delete();
        shared(context, APK).delete();
    }

    /** The offer could not be shown: it has not been seen, so "later" has not started. */
    static void askAgain() { nextCheck = 0; }

    /** An update that never reached its end: the power went, or something stopped the script. */
    static boolean interrupted(Context context) {
        File file = state(context);
        return file.exists() && System.currentTimeMillis() - file.lastModified() > STALE;
    }

    /** Null when the installed version is already the newest. */
    static Release check(Context context) throws Failure {
        ConnectivityManager network = context.getSystemService(ConnectivityManager.class);
        NetworkCapabilities link = network.getNetworkCapabilities(network.getActiveNetwork());
        if (link == null || !link.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            throw new Failure(UpdateError.F101);
        }
        byte[] manifest = fetch("update.json");
        verify(manifest, fetch("update.json.sig"));
        Release release = parse(manifest);
        return release.versionCode > installedCode(context) ? release : null;
    }

    private static byte[] fetch(String name) throws Failure {
        try {
            HttpURLConnection connection = open(BASE + name);
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] chunk = new byte[8192];
                for (int read; (read = input.read(chunk)) > 0; ) {
                    body.write(chunk, 0, read);
                    if (body.size() > MAX_MANIFEST) throw new Failure(UpdateError.F103);
                }
            }
            return body.toByteArray();
        } catch (IOException e) {
            throw new Failure(UpdateError.F102);
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException("HTTP");
        return connection;
    }

    private static void verify(byte[] manifest, byte[] signed) throws Failure {
        try {
            PublicKey key = KeyFactory.getInstance("EC")
                .generatePublic(new X509EncodedKeySpec(Base64.decode(PUBLIC_KEY, Base64.DEFAULT)));
            java.security.Signature check = java.security.Signature.getInstance("SHA256withECDSA");
            check.initVerify(key);
            check.update(manifest);
            if (!check.verify(signed)) throw new Failure(UpdateError.F104);
        } catch (GeneralSecurityException e) {
            throw new Failure(UpdateError.F104);
        }
    }

    private static Release parse(byte[] manifest) throws Failure {
        try {
            JSONObject json = new JSONObject(new String(manifest, StandardCharsets.UTF_8));
            Release release = new Release();
            release.versionCode = json.getInt("versionCode");
            release.versionName = json.getString("versionName");
            release.url = json.getString("url");
            release.sha256 = json.getString("sha256").toLowerCase(Locale.ROOT);
            release.size = json.getLong("size");
            JSONArray changes = json.getJSONArray("changes");
            for (int index = 0; index < changes.length(); index++) release.changes.add(changes.getString(index));
            if (!release.url.startsWith("https://") || release.size <= 0) throw new Failure(UpdateError.F103);
            return release;
        } catch (JSONException e) {
            throw new Failure(UpdateError.F103);
        }
    }

    static int installedCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    /** Refuses before the download, so a full Stick costs nothing. */
    static void checkRoom(Context context, Release release) throws Failure {
        File folder = mediaFolder(context);
        if (!folder.exists() && !folder.mkdirs()) throw new Failure(UpdateError.F114);
        long free = new StatFs(folder.getPath()).getAvailableBytes();
        if (free < release.size * ROOM_FACTOR) throw new Failure(UpdateError.F106);
    }

    static void checkIdle(Context context) throws Failure {
        if (context.getSystemService(AudioManager.class).isMusicActive()) throw new Failure(UpdateError.F117);
    }

    static File download(Context context, Release release, Progress progress) throws Failure {
        File file = shared(context, APK);
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (GeneralSecurityException e) {
            throw new Failure(UpdateError.F108);
        }
        long received = 0;
        try {
            HttpURLConnection connection = open(release.url);
            try (InputStream input = connection.getInputStream();
                 OutputStream output = new FileOutputStream(file)) {
                byte[] chunk = new byte[65536];
                for (int read; (read = input.read(chunk)) > 0; ) {
                    output.write(chunk, 0, read);
                    digest.update(chunk, 0, read);
                    received += read;
                    progress.percent((int) Math.min(PERCENT, received * PERCENT / release.size));
                }
            }
        } catch (java.io.FileNotFoundException e) {
            throw new Failure(UpdateError.F114);
        } catch (IOException e) {
            file.delete();
            throw new Failure(UpdateError.F107);
        }
        if (received != release.size) {
            file.delete();
            throw new Failure(UpdateError.F107);
        }
        if (!hex(digest).equals(release.sha256)) {
            file.delete();
            throw new Failure(UpdateError.F108);
        }
        return file;
    }

    private static String hex(MessageDigest digest) {
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", value));
        return hex.toString();
    }

    /** Everything that can be known about the new version without installing it. */
    static void inspect(Context context, File apk, Release release) throws Failure {
        PackageManager manager = context.getPackageManager();
        PackageInfo info = manager.getPackageArchiveInfo(apk.getPath(), PackageManager.GET_SIGNATURES);
        if (info == null) throw new Failure(UpdateError.F109);
        if (!context.getPackageName().equals(info.packageName)) throw new Failure(UpdateError.F110);
        if (info.versionCode != release.versionCode) throw new Failure(UpdateError.F112);
        if (info.applicationInfo != null && info.applicationInfo.minSdkVersion > Build.VERSION.SDK_INT) {
            throw new Failure(UpdateError.F113);
        }
        if (info.signatures == null) throw new Failure(UpdateError.F111);
        try {
            Signature[] installed = manager.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES).signatures;
            if (!Arrays.equals(installed, info.signatures)) throw new Failure(UpdateError.F111);
        } catch (PackageManager.NameNotFoundException e) {
            throw new Failure(UpdateError.F111);
        }
    }

    /** A second look just before starting: the file has sat on shared storage. */
    static void recheck(File apk, Release release) throws Failure {
        try (InputStream input = new FileInputStream(apk)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] chunk = new byte[65536];
            for (int read; (read = input.read(chunk)) > 0; ) digest.update(chunk, 0, read);
            if (!hex(digest).equals(release.sha256)) throw new Failure(UpdateError.F108);
        } catch (IOException | GeneralSecurityException e) {
            throw new Failure(UpdateError.F108);
        }
    }

    static String read(File file) {
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return "";
        }
    }

    static void write(File file, String text) {
        try {
            Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            file.delete();
        }
    }
}
