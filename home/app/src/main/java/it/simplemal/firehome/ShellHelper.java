package it.simplemal.firehome;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Looper;
import android.util.Base64;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

/**
 * Runs as the shell user through app_process, from Salotto's own APK, for the two things an app
 * may not do: seeing the screen, stopping another app and going back to the old version. One command per
 * line on stdin, "shot", "stop PACKAGE", "rollback APK VERDICT" or "run COMMAND", the command in base64, one
 * reply per line. Ends when its stream closes.
 */
public final class ShellHelper {
    private static final int WIDTH = 480;
    private static final int HEIGHT = 270;
    private static final int QUALITY = 70;
    private static final int USER = 0;
    private static final String SCRIPT = "/data/local/tmp/salotto-rollback.sh";
    /**
     * Installing ends Salotto and this process with it, so the work is left to a detached script.
     * Android's installer only goes forward: going back takes the shell. It reports in update-state.
     */
    private static final String[] ROLLBACK = {
        "P=it.simplemal.firehome",
        "A=\"$1\"",
        "V=\"$2\"",
        "S=\"$(dirname \"$A\")/update-state\"",
        "B=/data/local/tmp/salotto-old.apk",
        "say() { echo \"$1\" > \"$S\"; }",
        "trap 'rm -f \"$0\"' EXIT",
        "say rollback",
        "cp \"$A\" $B",
        "if pm install -r -d $B > /dev/null 2>&1; then say \"rolled-back $V\"; else say \"rollback-failed $V\"; fi",
        "rm -f $B",
        "am start -n $P/.UpdateActivity --ez after true > /dev/null 2>&1",
    };

    private ShellHelper() {}

    /** A small JPEG of the screen in base64: Fire OS keeps no snapshots of its tasks. */
    private static String shot() throws Exception {
        Method screenshot = Class.forName("android.view.SurfaceControl")
            .getMethod("screenshot", Rect.class, int.class, int.class, int.class);
        Bitmap frame = (Bitmap) screenshot.invoke(null, new Rect(), WIDTH, HEIGHT, 0);
        if (frame == null) return "none";
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        Bitmap copy = frame.copy(Bitmap.Config.ARGB_8888, false);
        copy.compress(Bitmap.CompressFormat.JPEG, QUALITY, jpeg);
        // This process allocates so little that the GC would rarely come to free them.
        copy.recycle();
        frame.recycle();
        return Base64.encodeToString(jpeg.toByteArray(), Base64.NO_WRAP);
    }

    private static void stop(String pkg) throws Exception {
        Object manager = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
        manager.getClass().getMethod("forceStopPackage", String.class, int.class).invoke(manager, pkg, USER);
    }

    private static void rollback(String apk, String verdict) throws Exception {
        try (java.io.PrintWriter script = new java.io.PrintWriter(SCRIPT)) {
            for (String line : ROLLBACK) script.println(line);
        }
        new ProcessBuilder("sh", "-c", "setsid nohup sh " + SCRIPT + " '" + apk + "' " + verdict + " > /dev/null 2>&1 &").start().waitFor();
    }

    /** One line of base64 back, so that any output fits the protocol. */
    private static String run(String encoded) throws Exception {
        String command = new String(Base64.decode(encoded, Base64.DEFAULT), java.nio.charset.StandardCharsets.UTF_8);
        Process process = new ProcessBuilder("sh", "-c", command).redirectErrorStream(true).start();
        process.getOutputStream().close();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (java.io.InputStream input = process.getInputStream()) {
            byte[] chunk = new byte[8192];
            for (int read; (read = input.read(chunk)) > 0; ) output.write(chunk, 0, read);
        }
        process.waitFor();
        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
    }

    public static void main(String[] args) throws Exception {
        Looper.prepareMainLooper();
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("ready");
        try {
            serve(input);
        } catch (java.io.IOException e) {
            // Salotto went away, as it does when it is updated: the same as the stream closing.
        }
    }

    private static void serve(BufferedReader input) throws Exception {
        for (String line; (line = input.readLine()) != null; ) {
            String[] parts = line.trim().split(" ");
            try {
                if (parts[0].equals("shot")) {
                    System.out.println(shot());
                } else if (parts[0].equals("stop") && parts.length == 2) {
                    stop(parts[1]);
                    System.out.println("ok");
                } else if (parts[0].equals("run") && parts.length == 2) {
                    System.out.println(run(parts[1]));
                } else if (parts[0].equals("rollback") && parts.length == 3) {
                    rollback(parts[1], parts[2]);
                    System.out.println("ok");
                } else {
                    System.out.println("none");
                }
            } catch (ReflectiveOperationException | RuntimeException | java.io.IOException | InterruptedException e) {
                System.out.println("none");
            }
        }
    }
}
