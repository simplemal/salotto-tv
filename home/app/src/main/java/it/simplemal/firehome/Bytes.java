package it.simplemal.firehome;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Whole files in and out. java.nio.file came with Android 8, and the Stick 4K of 2018 has Android 7. */
final class Bytes {
    private static final int BUFFER = 64 * 1024;

    private Bytes() {}

    static byte[] read(File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            ByteArrayOutputStream all = new ByteArrayOutputStream((int) Math.max(0, Math.min(file.length(), Integer.MAX_VALUE)));
            copy(input, all);
            return all.toByteArray();
        }
    }

    /** Made or emptied first, as Files.write did. */
    static void write(File file, byte[] bytes) throws IOException {
        try (OutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
        }
    }

    static void copy(InputStream input, File target) throws IOException {
        try (OutputStream output = new FileOutputStream(target)) {
            copy(input, output);
        }
    }

    static void copy(File source, File target) throws IOException {
        try (InputStream input = new FileInputStream(source)) {
            copy(input, target);
        }
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[BUFFER];
        for (int read; (read = input.read(buffer)) > 0; ) output.write(buffer, 0, read);
    }
}
