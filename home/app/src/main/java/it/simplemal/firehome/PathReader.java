package it.simplemal.firehome;

import android.graphics.Matrix;
import android.graphics.Path;

/** The part of SVG geometry that the weather scenes use. */
final class PathReader {
    private PathReader() {}

    private static final class Numbers {
        final String text;
        int at;

        Numbers(String text) { this.text = text; }

        private boolean numberStart(char c) {
            return c == '-' || c == '.' || c >= '0' && c <= '9';
        }

        boolean hasNumber() {
            while (at < text.length() && (text.charAt(at) == ' ' || text.charAt(at) == ',')) at++;
            return at < text.length() && numberStart(text.charAt(at));
        }

        float next() {
            hasNumber();
            int start = at;
            if (text.charAt(at) == '-') at++;
            boolean dot = false;
            while (at < text.length()) {
                char c = text.charAt(at);
                if (c == '.' && !dot) dot = true;
                else if (c < '0' || c > '9') break;
                at++;
            }
            return Float.parseFloat(text.substring(start, at));
        }
    }

    static Path read(String data) {
        Path path = new Path();
        Numbers numbers = new Numbers(data);
        float x = 0, y = 0, startX = 0, startY = 0, controlX = 0, controlY = 0;
        char command = 0;
        while (true) {
            if (!numbers.hasNumber()) {
                while (numbers.at < data.length() && data.charAt(numbers.at) == ' ') numbers.at++;
                if (numbers.at >= data.length()) return path;
                command = data.charAt(numbers.at++);
            }
            switch (command) {
                case 'M':
                    x = startX = numbers.next();
                    y = startY = numbers.next();
                    path.moveTo(x, y);
                    command = 'L';
                    break;
                case 'L':
                    x = numbers.next();
                    y = numbers.next();
                    path.lineTo(x, y);
                    break;
                case 'l':
                    x += numbers.next();
                    y += numbers.next();
                    path.lineTo(x, y);
                    break;
                case 'H':
                    x = numbers.next();
                    path.lineTo(x, y);
                    break;
                case 'h':
                    x += numbers.next();
                    path.lineTo(x, y);
                    break;
                case 'V':
                    y = numbers.next();
                    path.lineTo(x, y);
                    break;
                case 'v':
                    y += numbers.next();
                    path.lineTo(x, y);
                    break;
                case 'C': {
                    float x1 = numbers.next(), y1 = numbers.next();
                    controlX = numbers.next();
                    controlY = numbers.next();
                    x = numbers.next();
                    y = numbers.next();
                    path.cubicTo(x1, y1, controlX, controlY, x, y);
                    break;
                }
                case 'S': {
                    float x1 = 2 * x - controlX, y1 = 2 * y - controlY;
                    controlX = numbers.next();
                    controlY = numbers.next();
                    x = numbers.next();
                    y = numbers.next();
                    path.cubicTo(x1, y1, controlX, controlY, x, y);
                    break;
                }
                case 'Z':
                case 'z':
                    path.close();
                    x = startX;
                    y = startY;
                    break;
                default:
                    return path;
            }
        }
    }

    /** "Mx yldx dy" as {x, y, dx, dy}; null for anything else. */
    static float[] line(String data) {
        int bend = data.indexOf('l');
        if (!data.startsWith("M") || bend < 0) return null;
        Numbers start = new Numbers(data.substring(1, bend));
        Numbers end = new Numbers(data.substring(bend + 1));
        return new float[]{start.next(), start.next(), end.next(), end.next()};
    }

    /** Translate and scale, in the order written. */
    static Matrix transform(String text) {
        Matrix matrix = new Matrix();
        int at = 0;
        while (true) {
            int open = text.indexOf('(', at);
            if (open < 0) return matrix;
            int close = text.indexOf(')', open);
            String name = text.substring(at, open).trim();
            Numbers numbers = new Numbers(text.substring(open + 1, close));
            float a = numbers.next();
            float b = numbers.hasNumber() ? numbers.next() : name.equals("scale") ? a : 0;
            if (name.equals("scale")) matrix.preScale(a, b);
            else matrix.preTranslate(a, b);
            at = close + 1;
        }
    }
}
