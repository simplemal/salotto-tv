package it.simplemal.firehome;

import android.graphics.Bitmap;
import android.graphics.Color;
import java.util.SplittableRandom;

/**
 * A true blur of a whole picture, for what stays on screen behind a page. Android 11 has none of
 * its own for a view, and shrinking and enlarging a picture leaves steps along every edge: three
 * passes of a moving average come out as a Gaussian, with nothing left for the enlarging to show.
 */
final class Blur {
    private static final int WIDTH = 240;
    private static final int HEIGHT = 135;
    private static final int SHOWN_WIDTH = 960;
    private static final int SHOWN_HEIGHT = 540;
    private static final int PASSES = 3;
    private static final int NOISE_SIZE = 256;
    private static float[] noise;

    private Blur() {}

    /**
     * Blurred, darkened by `veil` and enlarged, all in one go. Darkened any later, by a view on top,
     * a smooth picture is left with so few tones that the screen shows them as bands, like the
     * lines of a map, and nothing laid over it can take them away. Here each pixel is worked out
     * with its fractions, and a little noise decides which way it rounds: the bands never form.
     */
    static Bitmap veiled(Bitmap picture, int radius, int veil) {
        int[] small = blurred(picture, radius);
        float[] dither = noise();
        float opacity = Color.alpha(veil) / 255f;
        float keep = 1 - opacity;
        float redOver = Color.red(veil) * opacity;
        float greenOver = Color.green(veil) * opacity;
        float blueOver = Color.blue(veil) * opacity;
        float acrossStep = (WIDTH - 1f) / (SHOWN_WIDTH - 1);
        float downStep = (HEIGHT - 1f) / (SHOWN_HEIGHT - 1);
        int[] shown = new int[SHOWN_WIDTH * SHOWN_HEIGHT];
        for (int y = 0; y < SHOWN_HEIGHT; y++) {
            float down = y * downStep;
            int above = (int) down;
            int below = Math.min(above + 1, HEIGHT - 1);
            float low = down - above;
            for (int x = 0; x < SHOWN_WIDTH; x++) {
                float across = x * acrossStep;
                int left = (int) across;
                int right = Math.min(left + 1, WIDTH - 1);
                float along = across - left;
                int a = small[above * WIDTH + left];
                int b = small[above * WIDTH + right];
                int c = small[below * WIDTH + left];
                int d = small[below * WIDTH + right];
                float grain = dither[(y & NOISE_SIZE - 1) * NOISE_SIZE + (x & NOISE_SIZE - 1)];
                int red = rounded(mix(a >> 16 & 0xFF, b >> 16 & 0xFF, c >> 16 & 0xFF, d >> 16 & 0xFF, along, low) * keep + redOver + grain);
                int green = rounded(mix(a >> 8 & 0xFF, b >> 8 & 0xFF, c >> 8 & 0xFF, d >> 8 & 0xFF, along, low) * keep + greenOver + grain);
                int blue = rounded(mix(a & 0xFF, b & 0xFF, c & 0xFF, d & 0xFF, along, low) * keep + blueOver + grain);
                shown[y * SHOWN_WIDTH + x] = 0xFF000000 | red << 16 | green << 8 | blue;
            }
        }
        return Bitmap.createBitmap(shown, SHOWN_WIDTH, SHOWN_HEIGHT, Bitmap.Config.ARGB_8888);
    }

    private static float mix(int a, int b, int c, int d, float along, float low) {
        float top = a + (b - a) * along;
        float bottom = c + (d - c) * along;
        return top + (bottom - top) * low;
    }

    private static int rounded(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }

    /** Between one tone below and one above, most often near none: enough to hide any step. */
    private static float[] noise() {
        if (noise == null) {
            float[] values = new float[NOISE_SIZE * NOISE_SIZE];
            SplittableRandom random = new SplittableRandom();
            for (int index = 0; index < values.length; index++) {
                values[index] = (float) (random.nextDouble() + random.nextDouble() - 1);
            }
            noise = values;
        }
        return noise;
    }

    private static int[] blurred(Bitmap picture, int radius) {
        int[] pixels = new int[WIDTH * HEIGHT];
        int[] turned = new int[WIDTH * HEIGHT];
        Bitmap.createScaledBitmap(picture, WIDTH, HEIGHT, true).getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT);
        for (int pass = 0; pass < PASSES; pass++) {
            average(pixels, turned, WIDTH, HEIGHT, radius);
            average(turned, pixels, HEIGHT, WIDTH, radius);
        }
        return pixels;
    }

    /** Along each row of `from`, written to `to` turned on its side: the next call does the columns. */
    private static void average(int[] from, int[] to, int width, int height, int radius) {
        int window = 2 * radius + 1;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            int red = 0;
            int green = 0;
            int blue = 0;
            for (int x = -radius; x <= radius; x++) {
                int pixel = from[row + Math.max(0, Math.min(width - 1, x))];
                red += pixel >> 16 & 0xFF;
                green += pixel >> 8 & 0xFF;
                blue += pixel & 0xFF;
            }
            for (int x = 0; x < width; x++) {
                to[x * height + y] = 0xFF000000 | red / window << 16 | green / window << 8 | blue / window;
                int coming = from[row + Math.min(width - 1, x + radius + 1)];
                int leaving = from[row + Math.max(0, x - radius)];
                red += (coming >> 16 & 0xFF) - (leaving >> 16 & 0xFF);
                green += (coming >> 8 & 0xFF) - (leaving >> 8 & 0xFF);
                blue += (coming & 0xFF) - (leaving & 0xFF);
            }
        }
    }
}
