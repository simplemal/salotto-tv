package it.simplemal.firehome;

import android.content.Context;
import android.graphics.BitmapFactory;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The web page a phone opens from the QR code, and nothing else: it lives only while Carica is on
 * screen. Every address carries a code drawn anew each time, so another device on the network
 * cannot send anything without having read the screen.
 */
final class UploadServer {
    private static final String CODE_LETTERS = "abcdefghijkmnpqrstuvwxyz23456789";
    private static final int CODE_LENGTH = 10;
    /** A phone's photo is resized on the phone to 4K at most: this is far above any of those. */
    private static final int MOST_BYTES = 30 * 1024 * 1024;
    private static final int WAIT_MS = 30000;
    private static final int LINE_BYTES = 8192;
    private static final int CHUNK = 65536;
    private static final String PAGE = "web/carica.html";
    private static final String LOGO = "web/logo-nome.svg";

    private final Context context;
    private final Consumer<File> received;
    private final String code;
    private final ServerSocket socket;
    private final String address;

    /** Null address when the TV has no network to be reached on. */
    UploadServer(Context context, Consumer<File> received) throws IOException {
        this.context = context.getApplicationContext();
        this.received = received;
        SecureRandom random = new SecureRandom();
        StringBuilder drawn = new StringBuilder();
        for (int index = 0; index < CODE_LENGTH; index++) drawn.append(CODE_LETTERS.charAt(random.nextInt(CODE_LETTERS.length())));
        code = drawn.toString();
        String host = localAddress();
        socket = new ServerSocket(0);
        address = host == null ? null : "http://" + host + ":" + socket.getLocalPort() + "/" + code + "/";
        Thread listening = new Thread(this::listen, "salotto-upload");
        listening.setDaemon(true);
        listening.start();
    }

    String address() { return address; }

    void stop() {
        try {
            socket.close();
        } catch (IOException e) {
            // Closed already.
        }
    }

    /** The TV's own address on the home network, by Wi-Fi or by cable. */
    private static String localAddress() throws IOException {
        for (NetworkInterface card : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!card.isUp() || card.isLoopback()) continue;
            for (InetAddress candidate : Collections.list(card.getInetAddresses())) {
                if (candidate instanceof Inet4Address && candidate.isSiteLocalAddress()) return candidate.getHostAddress();
            }
        }
        return null;
    }

    private void listen() {
        while (!socket.isClosed()) {
            try {
                Socket phone = socket.accept();
                Background.run(() -> answer(phone));
            } catch (IOException e) {
                return;
            }
        }
    }

    private void answer(Socket phone) {
        try (Socket open = phone) {
            open.setSoTimeout(WAIT_MS);
            InputStream input = new BufferedInputStream(open.getInputStream());
            OutputStream output = open.getOutputStream();
            String[] request = line(input).split(" ");
            long length = 0;
            for (String header = line(input); !header.isEmpty(); header = line(input)) {
                int colon = header.indexOf(':');
                if (colon > 0 && header.substring(0, colon).trim().equalsIgnoreCase("Content-Length")) {
                    length = Long.parseLong(header.substring(colon + 1).trim());
                }
            }
            String path = request.length > 1 ? request[1] : "";
            if (request[0].equals("GET") && path.equals("/" + code + "/")) {
                reply(output, "200 OK", "text/html; charset=utf-8", page());
            } else if (request[0].equals("POST") && path.equals("/" + code + "/carica") && length > 0 && length <= MOST_BYTES) {
                File saved = save(input, length);
                reply(output, saved != null ? "200 OK" : "415 Unsupported Media Type", "text/plain", new byte[0]);
                if (saved != null) received.accept(saved);
            } else {
                reply(output, "404 Not Found", "text/plain", new byte[0]);
            }
        } catch (IOException | NumberFormatException e) {
            // The phone went away: it can send again.
        }
    }

    private static String line(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int read = input.read(); read != -1 && read != '\n'; read = input.read()) {
            if (read != '\r') bytes.write(read);
            if (bytes.size() > LINE_BYTES) throw new IOException("line too long");
        }
        return bytes.toString(StandardCharsets.UTF_8.name());
    }

    private static void reply(OutputStream output, String status, String type, byte[] body) throws IOException {
        String head = "HTTP/1.1 " + status + "\r\nContent-Type: " + type + "\r\nContent-Length: " + body.length
            + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n";
        output.write(head.getBytes(StandardCharsets.UTF_8));
        output.write(body);
        output.flush();
    }

    /** Kept only if Android can read it as a picture, under a name that sorts by arrival. */
    private File save(InputStream input, long length) throws IOException {
        File folder = Look.personalFolder(context);
        if (!folder.isDirectory() && !folder.mkdirs()) return null;
        File part = new File(folder, "." + code + "-" + Thread.currentThread().getId());
        try (OutputStream file = new FileOutputStream(part)) {
            byte[] chunk = new byte[CHUNK];
            long left = length;
            while (left > 0) {
                int read = input.read(chunk, 0, (int) Math.min(chunk.length, left));
                if (read == -1) throw new IOException("cut short");
                file.write(chunk, 0, read);
                left -= read;
            }
        } catch (IOException e) {
            part.delete();
            throw e;
        }
        BitmapFactory.Options size = new BitmapFactory.Options();
        size.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(part.getPath(), size);
        if (size.outWidth <= 0 || size.outHeight <= 0) {
            part.delete();
            return null;
        }
        return place(folder, part);
    }

    private static synchronized File place(File folder, File part) {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        File target = new File(folder, "foto-" + stamp + ".jpg");
        for (int count = 2; target.exists(); count++) target = new File(folder, "foto-" + stamp + "-" + count + ".jpg");
        return part.renameTo(target) ? target : null;
    }

    /** The page in the TV's language, with the logo inside it: the phone fetches nothing else. */
    private byte[] page() throws IOException {
        String page = asset(PAGE)
            .replace("{{lang}}", Locale.getDefault().getLanguage())
            .replace("{{logo}}", asset(LOGO))
            .replace("{{title}}", html(context.getString(R.string.upload_web_title)))
            .replace("{{pick}}", html(context.getString(R.string.upload_web_pick)))
            .replace("{{sending}}", html(context.getString(R.string.upload_web_sending)))
            .replace("{{sent}}", html(context.getString(R.string.upload_web_sent)))
            .replace("{{failed}}", html(context.getString(R.string.upload_web_failed)));
        return page.getBytes(StandardCharsets.UTF_8);
    }

    private String asset(String name) throws IOException {
        try (InputStream input = context.getAssets().open(name)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] chunk = new byte[CHUNK];
            for (int read; (read = input.read(chunk)) > 0; ) bytes.write(chunk, 0, read);
            return bytes.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String html(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
