package it.simplemal.firehome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Cipher;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509TrustManager;

/**
 * The smallest adb client: reaches this device's own adbd and keeps one shell stream open.
 * adbd shows its approval dialog only to connections from another machine, so the first time
 * goes through a computer, with distro/tools/autorizza-adb.py listening behind SETUP_PORT.
 * Fire OS drops connections from the TV itself on the classic port: they go to the wireless
 * debugging port instead, which changes every time and speaks TLS, with this key as certificate.
 */
final class AdbClient implements Closeable {
    private static final String HOST = "127.0.0.1";
    private static final int SETUP_PORT = 5560;
    private static final int LOWEST_PORT = 30000;
    private static final int HIGHEST_PORT = 65000;
    private static final int SCAN_THREADS = 32;
    private static final int PROBE_CONNECT_MS = 40;
    private static final int PROBE_REPLY_MS = 250;
    private static final int PROBE_CLOSE_MS = 200;
    private static final int CONNECT_MS = 3000;
    private static final int CONFIRM_MS = 300000;
    private static final int REPLY_MS = 10000;
    private static final int HEADER = 24;
    private static final int LOCAL_ID = 1;
    private static final int CNXN = 0x4e584e43;
    private static final int AUTH = 0x48545541;
    private static final int OPEN = 0x4e45504f;
    private static final int OKAY = 0x59414b4f;
    private static final int CLSE = 0x45534c43;
    private static final int WRTE = 0x45545257;
    private static final int STLS = 0x534c5453;
    private static final int VERSION = 0x01000000;
    private static final int TLS_VERSION = 0x01000001;
    private static final int MAX_DATA = 256 * 1024;
    private static final int AUTH_TOKEN = 1;
    private static final int AUTH_SIGNATURE = 2;
    private static final int AUTH_PUBLIC_KEY = 3;
    private static final int KEY_BITS = 2048;
    private static final int KEY_WORDS = KEY_BITS / 32;
    private static final String KEY_FOLDER = "adb";
    private static final String KEY_NAME = "Salotto";
    private static final String PREFS = "adb";
    private static final String LAST_PORT = "port";
    private static final String PORT_PROPERTY = "service.adb.tls.port";
    /** adbd sends a raw SHA-1 digest to sign, so the DigestInfo prefix goes in by hand. */
    private static final byte[] SHA1_PREFIX = {0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03,
        0x02, 0x1a, 0x05, 0x00, 0x04, 0x14};

    private static final byte[] SHA256_RSA = {0x2a, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xf7, 0x0d, 0x01, 0x01, 0x0b};
    private static final byte[] COMMON_NAME = {0x55, 0x04, 0x03};
    private static final String NOT_BEFORE = "200101000000Z";
    private static final String NOT_AFTER = "491231235959Z";

    private static final class Message {
        int command;
        int first;
        byte[] data;
    }

    private final Socket socket = new Socket();
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
    private DataInputStream input;
    private OutputStream output;
    private int remoteId;

    private AdbClient() {}

    /** While the setup relay runs this blocks until someone answers on the TV. */
    static AdbClient connect(Context context) throws IOException, GeneralSecurityException {
        KeyPair keys = keys(context);
        SharedPreferences saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            return openTls(keys, saved);
        } catch (IOException e) {
            approve(keys);
            return openTls(keys, saved);
        }
    }

    /** Only answers while the relay on the computer runs. */
    private static void approve(KeyPair keys) throws IOException, GeneralSecurityException {
        AdbClient client = new AdbClient();
        try {
            client.handshake(keys, SETUP_PORT);
        } finally {
            client.close();
        }
    }

    private static AdbClient openTls(KeyPair keys, SharedPreferences saved) throws IOException, GeneralSecurityException {
        int last = saved.getInt(LAST_PORT, 0);
        int port = last != 0 && speaksTls(last) ? last : findPort();
        waitForProbeToClose();
        AdbClient client = new AdbClient();
        try {
            client.handshakeTls(keys, port);
            if (port != last) saved.edit().putInt(LAST_PORT, port).apply();
            return client;
        } catch (IOException | GeneralSecurityException e) {
            client.close();
            throw e;
        }
    }

    /** adbd names a connection after its socket, and drops a new one while the probe that had the name is still closing. */
    private static void waitForProbeToClose() throws IOException {
        try {
            Thread.sleep(PROBE_CLOSE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted", e);
        }
    }

    /** The scan touches 35000 ports, so the port adbd publishes comes first, if an app may read it. */
    private static int findPort() throws IOException {
        int published = publishedPort();
        if (published != 0 && speaksTls(published)) return published;
        // Android keeps the port from apps, and one by one this range takes the best part of a minute.
        AtomicInteger next = new AtomicInteger(LOWEST_PORT);
        AtomicInteger found = new AtomicInteger();
        List<Thread> workers = new ArrayList<>();
        for (int count = 0; count < SCAN_THREADS; count++) {
            Thread worker = new Thread(() -> {
                while (found.get() == 0) {
                    int port = next.getAndIncrement();
                    if (port > HIGHEST_PORT) return;
                    if (speaksTls(port)) found.compareAndSet(0, port);
                }
            }, "adb-port");
            worker.start();
            workers.add(worker);
        }
        try {
            for (Thread worker : workers) worker.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (found.get() == 0) throw new IOException("Wireless debugging is off");
        return found.get();
    }

    private static int publishedPort() {
        try {
            Process process = new ProcessBuilder("getprop", PORT_PROPERTY).start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                return line == null || line.isEmpty() ? 0 : Integer.parseInt(line.trim());
            } finally {
                process.destroy();
            }
        } catch (IOException | NumberFormatException e) {
            return 0;
        }
    }

    /** Only the wireless debugging port answers a CNXN with STLS. */
    private static boolean speaksTls(int port) {
        AdbClient probe = new AdbClient();
        try {
            probe.socket.connect(new InetSocketAddress(HOST, port), PROBE_CONNECT_MS);
            probe.socket.setSoTimeout(PROBE_REPLY_MS);
            probe.input = new DataInputStream(probe.socket.getInputStream());
            probe.output = probe.socket.getOutputStream();
            probe.send(CNXN, TLS_VERSION, MAX_DATA, text("host::"));
            return probe.read().command == STLS;
        } catch (IOException e) {
            return false;
        } finally {
            probe.close();
        }
    }

    private void handshakeTls(KeyPair keys, int port) throws IOException, GeneralSecurityException {
        socket.connect(new InetSocketAddress(HOST, port), CONNECT_MS);
        socket.setSoTimeout(REPLY_MS);
        input = new DataInputStream(socket.getInputStream());
        output = socket.getOutputStream();
        send(CNXN, TLS_VERSION, MAX_DATA, text("host::"));
        if (read().command != STLS) throw new IOException("Unexpected reply");
        send(STLS, VERSION, 0, new byte[0]);
        SSLSocket secure = (SSLSocket) tlsContext(keys).getSocketFactory().createSocket(socket, HOST, port, true);
        secure.setUseClientMode(true);
        secure.startHandshake();
        input = new DataInputStream(secure.getInputStream());
        output = secure.getOutputStream();
        if (read().command != CNXN) throw new IOException("Key not approved");
    }

    private void handshake(KeyPair keys, int port) throws IOException, GeneralSecurityException {
        socket.connect(new InetSocketAddress(HOST, port), CONNECT_MS);
        socket.setSoTimeout(CONFIRM_MS);
        input = new DataInputStream(socket.getInputStream());
        output = socket.getOutputStream();
        send(CNXN, VERSION, MAX_DATA, text("host::"));
        boolean signed = false;
        while (true) {
            Message message = read();
            if (message.command == CNXN) break;
            if (message.command != AUTH || message.first != AUTH_TOKEN) throw new IOException("Unexpected reply");
            if (signed) {
                send(AUTH, AUTH_PUBLIC_KEY, 0, publicKey((RSAPublicKey) keys.getPublic()));
            } else {
                send(AUTH, AUTH_SIGNATURE, 0, sign(keys.getPrivate(), message.data));
                signed = true;
            }
        }
        socket.setSoTimeout(REPLY_MS);
    }

    void openShell(String command) throws IOException {
        send(OPEN, LOCAL_ID, 0, text("shell:" + command));
        while (true) {
            Message message = read();
            if (message.command == OKAY) {
                remoteId = message.first;
                return;
            }
            if (message.command == CLSE) throw new IOException("Shell refused");
        }
    }

    /** For a stream that may stay silent for hours, like a log. */
    void waitForever() throws IOException {
        socket.setSoTimeout(0);
    }

    void writeLine(String line) throws IOException {
        send(WRTE, LOCAL_ID, remoteId, (line + "\n").getBytes(StandardCharsets.UTF_8));
    }

    /** The stream's next line. Its pieces are acknowledged as they come. */
    String readLine() throws IOException {
        while (true) {
            byte[] bytes = pending.toByteArray();
            for (int index = 0; index < bytes.length; index++) {
                if (bytes[index] != '\n') continue;
                pending.reset();
                pending.write(bytes, index + 1, bytes.length - index - 1);
                return new String(bytes, 0, index, StandardCharsets.UTF_8).trim();
            }
            Message message = read();
            if (message.command == WRTE) {
                pending.write(message.data);
                send(OKAY, LOCAL_ID, remoteId, new byte[0]);
            } else if (message.command == CLSE) {
                throw new EOFException("Shell closed");
            }
        }
    }

    @Override public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            // Nothing is left to release.
        }
    }

    private static byte[] text(String value) {
        return (value + "\0").getBytes(StandardCharsets.UTF_8);
    }

    private void send(int command, int first, int second, byte[] data) throws IOException {
        int sum = 0;
        for (byte value : data) sum += value & 0xff;
        ByteBuffer header = ByteBuffer.allocate(HEADER).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(command).putInt(first).putInt(second).putInt(data.length).putInt(sum).putInt(~command);
        output.write(header.array());
        output.write(data);
        output.flush();
    }

    private Message read() throws IOException {
        byte[] bytes = new byte[HEADER];
        input.readFully(bytes);
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        Message message = new Message();
        message.command = header.getInt();
        message.first = header.getInt();
        header.getInt();
        int length = header.getInt();
        if (length < 0 || length > MAX_DATA) throw new IOException("Bad message");
        message.data = new byte[length];
        input.readFully(message.data);
        return message;
    }

    private static byte[] sign(PrivateKey key, byte[] token) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        cipher.update(SHA1_PREFIX);
        return cipher.doFinal(token);
    }

    /** adbd checks only the certificate's key, against the approved ones. Its own is unsigned. */
    private static SSLContext tlsContext(KeyPair keys) throws GeneralSecurityException {
        X509Certificate certificate = certificate(keys);
        X509ExtendedKeyManager identity = new X509ExtendedKeyManager() {
            @Override public String[] getClientAliases(String type, Principal[] issuers) { return new String[]{KEY_NAME}; }
            @Override public String chooseClientAlias(String[] types, Principal[] issuers, java.net.Socket socket) { return KEY_NAME; }
            @Override public String[] getServerAliases(String type, Principal[] issuers) { return null; }
            @Override public String chooseServerAlias(String type, Principal[] issuers, java.net.Socket socket) { return null; }
            @Override public X509Certificate[] getCertificateChain(String alias) { return new X509Certificate[]{certificate}; }
            @Override public PrivateKey getPrivateKey(String alias) { return keys.getPrivate(); }
        };
        TrustManager anyServer = new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] chain, String type) { }
            @Override public void checkServerTrusted(X509Certificate[] chain, String type) { }
            @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        };
        SSLContext context = SSLContext.getInstance("TLSv1.3");
        context.init(new javax.net.ssl.KeyManager[]{identity}, new TrustManager[]{anyServer}, null);
        return context;
    }

    /** One DER value: its tag, its length, and its parts one after another. */
    private static byte[] der(int tag, byte[]... parts) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (byte[] part : parts) body.write(part, 0, part.length);
        ByteArrayOutputStream value = new ByteArrayOutputStream();
        value.write(tag);
        int length = body.size();
        if (length < 0x80) {
            value.write(length);
        } else if (length < 0x100) {
            value.write(0x81);
            value.write(length);
        } else {
            value.write(0x82);
            value.write(length >> 8);
            value.write(length);
        }
        value.write(body.toByteArray(), 0, length);
        return value.toByteArray();
    }

    /** Android has no public way to make one, so the few fields needed are written by hand. */
    private static X509Certificate certificate(KeyPair keys) throws GeneralSecurityException {
        byte[] algorithm = der(0x30, der(0x06, SHA256_RSA), new byte[]{0x05, 0x00});
        byte[] name = der(0x30, der(0x31, der(0x30, der(0x06, COMMON_NAME),
            der(0x0c, KEY_NAME.getBytes(StandardCharsets.UTF_8)))));
        byte[] validity = der(0x30, der(0x17, NOT_BEFORE.getBytes(StandardCharsets.US_ASCII)),
            der(0x17, NOT_AFTER.getBytes(StandardCharsets.US_ASCII)));
        byte[] body = der(0x30, der(0xa0, der(0x02, new byte[]{2})), der(0x02, new byte[]{1}), algorithm,
            name, validity, name, keys.getPublic().getEncoded());
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keys.getPrivate());
        signature.update(body);
        byte[] signed = der(0x30, body, algorithm, der(0x03, new byte[]{0}, signature.sign()));
        return (X509Certificate) CertificateFactory.getInstance("X.509")
            .generateCertificate(new java.io.ByteArrayInputStream(signed));
    }

    private static void putWords(ByteBuffer buffer, BigInteger value) {
        for (int word = 0; word < KEY_WORDS; word++) buffer.putInt(value.shiftRight(32 * word).intValue());
    }

    /** adb's own format: the modulus and two values that speed up its Montgomery arithmetic. */
    private static byte[] publicKey(RSAPublicKey key) {
        BigInteger modulus = key.getModulus();
        BigInteger word = BigInteger.ONE.shiftLeft(32);
        BigInteger inverse = word.subtract(modulus.mod(word).modInverse(word));
        BigInteger squared = BigInteger.ONE.shiftLeft(2 * KEY_BITS).mod(modulus);
        ByteBuffer buffer = ByteBuffer.allocate(4 + 4 + 2 * KEY_BITS / 8 + 4).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(KEY_WORDS).putInt(inverse.intValue());
        putWords(buffer, modulus);
        putWords(buffer, squared);
        buffer.putInt(key.getPublicExponent().intValue());
        return text(Base64.encodeToString(buffer.array(), Base64.NO_WRAP) + " " + KEY_NAME);
    }

    private static KeyPair keys(Context context) throws IOException, GeneralSecurityException {
        File folder = new File(context.getFilesDir(), KEY_FOLDER);
        File privateFile = new File(folder, "private");
        File publicFile = new File(folder, "public");
        KeyFactory factory = KeyFactory.getInstance("RSA");
        if (privateFile.isFile() && publicFile.isFile()) {
            return new KeyPair(
                factory.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(publicFile.toPath()))),
                factory.generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(privateFile.toPath()))));
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(KEY_BITS);
        KeyPair pair = generator.generateKeyPair();
        folder.mkdirs();
        Files.write(privateFile.toPath(), pair.getPrivate().getEncoded());
        Files.write(publicFile.toPath(), pair.getPublic().getEncoded());
        return pair;
    }
}
