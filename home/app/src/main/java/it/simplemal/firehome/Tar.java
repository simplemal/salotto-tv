package it.simplemal.firehome;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Opens the plain tar archives of the backdrop collections. Nothing of Android: it is tried on the computer too. */
final class Tar {
    private static final int BLOCK = 512;

    private Tar() {}

    /** A plain tar, as pubblica-sfondi.sh makes it: only files and folders, all inside the collection's own. */
    static void unpack(File archive, File root, String pack) throws IOException {
        try (InputStream input = new FileInputStream(archive)) {
            unpack(input, root, pack);
        }
    }

    /** The same from a stream, as it arrives: no copy of the archive is kept. It stops at the end marker. */
    static void unpack(InputStream input, File root, String pack) throws IOException {
        String rootPath = root.getCanonicalPath() + File.separator;
        {
            byte[] header = new byte[BLOCK];
            while (readFully(input, header) && header[0] != 0) {
                String name = text(header, 0, 100);
                String prefix = text(header, 345, 155);
                if (!prefix.isEmpty()) name = prefix + "/" + name;
                long length = Long.parseLong(text(header, 124, 12).trim().isEmpty() ? "0" : text(header, 124, 12).trim(), 8);
                char kind = (char) header[156];
                File target = new File(root, name);
                if (!(name.equals(pack + "/") || name.startsWith(pack + "/")) || name.contains("..")
                        || !target.getCanonicalPath().startsWith(rootPath)) throw new IOException("path");
                if (kind == '5') {
                    target.mkdirs();
                } else if (kind == '0' || kind == 0) {
                    target.getParentFile().mkdirs();
                    try (OutputStream output = new FileOutputStream(target)) {
                        copy(input, output, length);
                    }
                } else {
                    skip(input, length);
                }
                long rest = (BLOCK - length % BLOCK) % BLOCK;
                skip(input, rest);
            }
        }
    }

    private static String text(byte[] header, int from, int length) {
        int end = from;
        while (end < from + length && header[end] != 0) end++;
        return new String(header, from, end - from, StandardCharsets.UTF_8);
    }

    private static boolean readFully(InputStream input, byte[] block) throws IOException {
        int done = 0;
        while (done < block.length) {
            int read = input.read(block, done, block.length - done);
            if (read < 0) return false;
            done += read;
        }
        return true;
    }

    private static void copy(InputStream input, OutputStream output, long length) throws IOException {
        byte[] chunk = new byte[65536];
        while (length > 0) {
            int read = input.read(chunk, 0, (int) Math.min(chunk.length, length));
            if (read < 0) throw new IOException("short");
            output.write(chunk, 0, read);
            length -= read;
        }
    }

    private static void skip(InputStream input, long length) throws IOException {
        while (length > 0) {
            long gone = input.skip(length);
            if (gone <= 0) {
                if (input.read() < 0) throw new IOException("short");
                gone = 1;
            }
            length -= gone;
        }
    }

}
