package it.simplemal.firehome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * An app as glass on the clean Home, the user's option of 2026-11-10, tried first as a picture on the
 * Mac with the real banners and backdrops. The logo, found in the app's own picture, becomes a white
 * silhouette with a soft shadow, on a clear veil of the app's colour; the edge catches the light from
 * the top left, and a weaker light where it faces the bottom right.
 *
 * Only transparency: nothing here depends on the backdrop behind, which shows through as it is, so a
 * backdrop fading into the next needs nothing made again. Drawn once, away from the main thread.
 */
final class GlassIcon {
    /** The app's picture is read at this width: the logo has to stay sharp at the focused size. */
    private static final int SAMPLE = 320;
    private static final int EDGE_STEP = 2;
    /** As CircleArt finds a logo: far from every colour along the picture's edges. */
    private static final int NEAR = 60;
    private static final int CUT_CLEAR = 25;
    private static final int CUT_WHOLE = 70;
    /** Past this share of logo the picture is a scene: there is no silhouette to take. */
    private static final float SCENE = 0.6f;
    /**
     * Apps whose picture has a glow around the logo, as NOW's O: only the brightest part is the logo,
     * found by Otsu's split. Not a rule for all: it cut HBO Max's and Apple TV's shaded letters.
     */
    private static final Set<String> GLOW = new HashSet<>(Arrays.asList("com.nowtv.it"));
    private static final int GLOW_RAMP = 20;

    /** How much of the app's colour the glass holds, at the top; lower down a little less. */
    private static final float VEIL = 0.30f;
    private static final float VEIL_BOTTOM = 0.75f;
    /** A few dp from the edge the veil thins out and darkens: the user's request of 2026-11-10. */
    private static final float EDGE_CLEAR = 0.35f;
    private static final float EDGE_DARK = 0.55f;
    private static final float EDGE_DEPTH_DP = 4.5f;
    /** A faint light over the upper half. */
    private static final float SHEEN = 0.10f;
    private static final float LOGO_WIDTH = 0.66f;
    private static final float LOGO_HEIGHT = 0.42f;
    private static final float WHITE = 0.95f;
    private static final float SHADOW = 0.35f;
    /** The bright line along the edge, and inside it the softer band that gives the glass its thickness. */
    private static final float LINE_DP = 0.5f;
    private static final float BAND_DP = 2f;
    private static final float BAND_SOFT_DP = 0.75f;
    /** The outer line as bright as it goes, the inner band left soft: the user's request of 2026-11-10. */
    private static final float LINE_LIGHT = 1f;
    private static final float BAND_LIGHT = 55 / 255f;
    /** The light comes from the top left. */
    private static final float LIGHT_X = -0.55f;
    private static final float LIGHT_Y = -0.83f;
    private static final float[] NEUTRAL = {0.85f, 0.85f, 0.88f};

    private GlassIcon() {}

    /** Null when the picture has no logo to take, a scene: the app keeps its usual squircle. */
    static Bitmap make(Drawable image, Drawable back, boolean wide, int side, float density, String pkg) {
        int width = SAMPLE;
        int height = wide ? SAMPLE * 9 / 16 : SAMPLE;
        Bitmap sample = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        CircleArt.paint(new Canvas(sample), image, back, width, height);
        int[] pixels = new int[width * height];
        sample.getPixels(pixels, 0, width, 0, 0, width, height);
        sample.recycle();

        int[] edge = edgeColours(pixels, width, height);
        float[] distance = new float[pixels.length];
        for (int index = 0; index < pixels.length; index++) distance[index] = nearest(pixels[index], edge);
        boolean glow = GLOW.contains(pkg);
        float start = glow ? otsu(distance, NEAR) : NEAR;
        float[] alpha = new float[pixels.length];
        int left = width;
        int top = height;
        int right = -1;
        int bottom = -1;
        int logo = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                float far = distance[index];
                alpha[index] = glow ? clamp((far - start) / GLOW_RAMP) : clamp((far - CUT_CLEAR) / (CUT_WHOLE - CUT_CLEAR));
                if (far <= start) continue;
                logo++;
                left = Math.min(left, x);
                right = Math.max(right, x + 1);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y + 1);
            }
        }
        if (right < 0 || logo > SCENE * width * height) return null;
        float[] colour = colour(pixels, edge, distance, start);

        // The logo, white, fitted in the middle.
        int cutWidth = right - left;
        int cutHeight = bottom - top;
        int[] cut = new int[cutWidth * cutHeight];
        for (int y = 0; y < cutHeight; y++) {
            for (int x = 0; x < cutWidth; x++) {
                cut[y * cutWidth + x] = Math.round(alpha[(top + y) * width + left + x] * 255) << 24 | 0xFFFFFF;
            }
        }
        Bitmap mark = Bitmap.createBitmap(cut, cutWidth, cutHeight, Bitmap.Config.ARGB_8888);
        float scale = Math.min(side * LOGO_WIDTH / cutWidth, side * LOGO_HEIGHT / cutHeight);
        int drawnWidth = Math.max(1, Math.round(cutWidth * scale));
        int drawnHeight = Math.max(1, Math.round(cutHeight * scale));
        int drawnLeft = (side - drawnWidth) / 2;
        int drawnTop = (side - drawnHeight) / 2;
        float[] silhouette = alphaOf(side, canvas -> canvas.drawBitmap(mark, null,
            new Rect(drawnLeft, drawnTop, drawnLeft + drawnWidth, drawnTop + drawnHeight), new Paint(Paint.FILTER_BITMAP_FLAG)));
        int drop = Math.max(1, Math.round(0.75f * density));
        float[] shadow = alphaOf(side, canvas -> canvas.drawBitmap(mark, null,
            new Rect(drawnLeft, drawnTop + drop, drawnLeft + drawnWidth, drawnTop + drop + drawnHeight), new Paint(Paint.FILTER_BITMAP_FLAG)));
        mark.recycle();
        blur(shadow, side, Math.max(1, Math.round(density)));

        // The shape, its distance from the edge, and which way the edge faces.
        float[] cover = alphaOf(side, canvas -> {
            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setColor(Color.WHITE);
            canvas.drawPath(Squircle.path(side), fill);
        });
        float[] inside = insideDistance(cover, side);
        float[] smooth = cover.clone();
        blur(smooth, side, Math.max(1, Math.round(1.5f * density)));

        float line = LINE_DP * density;
        float band = BAND_DP * density;
        float bandSoft = BAND_SOFT_DP * density;
        float depthReach = EDGE_DEPTH_DP * density;
        int[] made = new int[side * side];
        float[] pixel = new float[4];
        for (int y = 0; y < side; y++) {
            float fall = 1 - (1 - VEIL_BOTTOM) * y / (side - 1f);
            float sheen = Math.max(0, SHEEN * (1 - 2f * y / side));
            for (int x = 0; x < side; x++) {
                int index = y * side + x;
                if (cover[index] <= 0) continue;
                float d = inside[index];
                float depth = smoothstep(0, depthReach, d);
                float shade = EDGE_DARK + (1 - EDGE_DARK) * depth;
                pixel[0] = colour[0] * shade;
                pixel[1] = colour[1] * shade;
                pixel[2] = colour[2] * shade;
                pixel[3] = VEIL * fall * (EDGE_CLEAR + (1 - EDGE_CLEAR) * depth);
                over(pixel, 1, 1, 1, sheen);
                over(pixel, 0, 0, 0, shadow[index] * SHADOW);
                over(pixel, 1, 1, 1, silhouette[index] * WHITE);
                // Out of the shape is where the smoothed cover falls: the gradient points in.
                float gx = sample(smooth, side, x + 1, y) - sample(smooth, side, x - 1, y);
                float gy = sample(smooth, side, x, y + 1) - sample(smooth, side, x, y - 1);
                float length = (float) Math.hypot(gx, gy) + 1e-6f;
                float facing = (-gx / length) * LIGHT_X + (-gy / length) * LIGHT_Y;
                float lit = (float) Math.pow(Math.max(0, facing), 1.6);
                float away = (float) Math.pow(Math.max(0, -facing), 2.2);
                float edgeLine = clamp(1 + line - d);
                float edgeBand = 1 - smoothstep(band - bandSoft, band + bandSoft, d);
                float light = edgeLine * LINE_LIGHT * (0.10f + 0.90f * lit + 0.45f * away) + edgeBand * BAND_LIGHT * (lit + 0.5f * away);
                over(pixel, 1, 1, 1, clamp(light));
                float a = pixel[3] * cover[index];
                made[index] = Math.round(a * 255) << 24 | Math.round(clamp(pixel[0]) * 255) << 16
                    | Math.round(clamp(pixel[1]) * 255) << 8 | Math.round(clamp(pixel[2]) * 255);
            }
        }
        Bitmap glass = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        glass.setPixels(made, 0, side, 0, 0, side, side);
        return glass;
    }

    /** One layer over what is there, with the colours not premultiplied. */
    private static void over(float[] pixel, float red, float green, float blue, float alpha) {
        if (alpha <= 0) return;
        float under = pixel[3] * (1 - alpha);
        float total = alpha + under;
        pixel[0] = (red * alpha + pixel[0] * under) / total;
        pixel[1] = (green * alpha + pixel[1] * under) / total;
        pixel[2] = (blue * alpha + pixel[2] * under) / total;
        pixel[3] = total;
    }

    private interface Drawing { void draw(Canvas canvas); }

    /** What a drawing covers, from 0 to 1, on a square of `side`. */
    private static float[] alphaOf(int side, Drawing drawing) {
        Bitmap layer = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        drawing.draw(new Canvas(layer));
        int[] pixels = new int[side * side];
        layer.getPixels(pixels, 0, side, 0, 0, side, side);
        layer.recycle();
        float[] alpha = new float[pixels.length];
        for (int index = 0; index < pixels.length; index++) alpha[index] = (pixels[index] >>> 24) / 255f;
        return alpha;
    }

    /**
     * How far each pixel inside is from the outside, in pixels: 1 next to it. Past the picture's sides
     * counts as outside, since the shape touches them: otherwise its sides had no edge at all.
     */
    private static float[] insideDistance(float[] cover, int side) {
        final float far = 1e6f;
        final float diagonal = 1.4142f;
        float[] d = new float[cover.length];
        for (int index = 0; index < d.length; index++) d[index] = cover[index] >= 0.5f ? far : 0;
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                int index = y * side + x;
                if (d[index] == 0) continue;
                float best = d[index];
                best = Math.min(best, at(d, side, x - 1, y) + 1);
                best = Math.min(best, at(d, side, x, y - 1) + 1);
                best = Math.min(best, at(d, side, x - 1, y - 1) + diagonal);
                best = Math.min(best, at(d, side, x + 1, y - 1) + diagonal);
                d[index] = best;
            }
        }
        for (int y = side - 1; y >= 0; y--) {
            for (int x = side - 1; x >= 0; x--) {
                int index = y * side + x;
                if (d[index] == 0) continue;
                float best = d[index];
                best = Math.min(best, at(d, side, x + 1, y) + 1);
                best = Math.min(best, at(d, side, x, y + 1) + 1);
                best = Math.min(best, at(d, side, x + 1, y + 1) + diagonal);
                best = Math.min(best, at(d, side, x - 1, y + 1) + diagonal);
                d[index] = best;
            }
        }
        // The half-covered pixels along the curve sit nearer the edge than whole ones.
        for (int index = 0; index < d.length; index++) if (d[index] > 0) d[index] -= 1 - cover[index];
        return d;
    }

    private static float at(float[] d, int side, int x, int y) {
        return x < 0 || y < 0 || x >= side || y >= side ? 0 : d[y * side + x];
    }

    private static float sample(float[] values, int side, int x, int y) {
        return x < 0 || y < 0 || x >= side || y >= side ? 0 : values[y * side + x];
    }

    /** Three box passes each way, with nothing past the sides. */
    private static void blur(float[] values, int side, int reach) {
        float[] other = new float[values.length];
        for (int pass = 0; pass < 3; pass++) {
            box(values, other, side, reach);
            box(other, values, side, reach);
        }
    }

    /** A moving average along each row of `from`, written to `to` turned on its side. */
    private static void box(float[] from, float[] to, int side, int reach) {
        float window = 2 * reach + 1;
        for (int y = 0; y < side; y++) {
            int row = y * side;
            float total = 0;
            for (int x = 0; x <= reach && x < side; x++) total += from[row + x];
            for (int x = 0; x < side; x++) {
                to[x * side + y] = total / window;
                int coming = x + reach + 1;
                int going = x - reach;
                if (coming < side) total += from[row + coming];
                if (going >= 0) total -= from[row + going];
            }
        }
    }

    private static int[] edgeColours(int[] pixels, int width, int height) {
        Set<Integer> found = new HashSet<>();
        for (int x = 0; x < width; x += EDGE_STEP) {
            found.add(pixels[x] | 0xFF000000);
            found.add(pixels[(height - 1) * width + x] | 0xFF000000);
        }
        for (int y = 0; y < height; y += EDGE_STEP) {
            found.add(pixels[y * width] | 0xFF000000);
            found.add(pixels[y * width + width - 1] | 0xFF000000);
        }
        int[] edge = new int[found.size()];
        int index = 0;
        for (int colour : found) edge[index++] = colour;
        return edge;
    }

    /** The distance to the closest of the edge colours. */
    private static float nearest(int pixel, int[] edge) {
        int red = Color.red(pixel);
        int green = Color.green(pixel);
        int blue = Color.blue(pixel);
        int best = Integer.MAX_VALUE;
        for (int colour : edge) {
            int dr = red - Color.red(colour);
            int dg = green - Color.green(colour);
            int db = blue - Color.blue(colour);
            best = Math.min(best, dr * dr + dg * dg + db * db);
            if (best == 0) break;
        }
        return (float) Math.sqrt(best);
    }

    /** Otsu's split of the logo's distances: where the brightest group starts. */
    private static float otsu(float[] distance, float above) {
        final int bins = 64;
        float most = above;
        for (float d : distance) most = Math.max(most, d);
        if (most <= above) return above;
        long[] count = new long[bins];
        float step = (most - above) / bins;
        for (float d : distance) {
            if (d <= above) continue;
            count[Math.min(bins - 1, (int) ((d - above) / step))]++;
        }
        double bestSpread = 0;
        float best = above;
        for (int split = 1; split < bins; split++) {
            double w0 = 0;
            double w1 = 0;
            double s0 = 0;
            double s1 = 0;
            for (int bin = 0; bin < bins; bin++) {
                double centre = above + (bin + 0.5) * step;
                if (bin < split) { w0 += count[bin]; s0 += count[bin] * centre; }
                else { w1 += count[bin]; s1 += count[bin] * centre; }
            }
            if (w0 == 0 || w1 == 0) continue;
            double gap = s0 / w0 - s1 / w1;
            double spread = w0 * w1 * gap * gap;
            if (spread > bestSpread) {
                bestSpread = spread;
                best = above + split * step;
            }
        }
        return best;
    }

    /**
     * The app's colour: its background when that is a colour, as Prime Video's blue; otherwise the most
     * vivid part of the logo, as Netflix's red; otherwise a neutral light grey.
     */
    private static float[] colour(int[] pixels, int[] edge, float[] distance, float start) {
        float red = 0;
        float green = 0;
        float blue = 0;
        for (int colour : edge) {
            red += Color.red(colour);
            green += Color.green(colour);
            blue += Color.blue(colour);
        }
        int back = Color.rgb(Math.round(red / edge.length), Math.round(green / edge.length), Math.round(blue / edge.length));
        float[] hsv = new float[3];
        Color.colorToHSV(back, hsv);
        if (hsv[1] > 0.35f && hsv[2] > 0.25f) return rgb(back);
        final int hues = 12;
        int[] votes = new int[hues];
        float[][] sums = new float[hues][3];
        int step = Math.max(1, pixels.length / 8000);
        for (int index = 0; index < pixels.length; index += step) {
            if (distance[index] <= start) continue;
            int pixel = pixels[index];
            Color.colorToHSV(pixel, hsv);
            if (hsv[1] <= 0.4f || hsv[2] <= 0.3f) continue;
            int hue = Math.min(hues - 1, (int) (hsv[0] / 360f * hues));
            votes[hue]++;
            sums[hue][0] += Color.red(pixel);
            sums[hue][1] += Color.green(pixel);
            sums[hue][2] += Color.blue(pixel);
        }
        int top = 0;
        for (int hue = 1; hue < hues; hue++) if (votes[hue] > votes[top]) top = hue;
        if (votes[top] <= 20) return NEUTRAL.clone();
        return new float[]{sums[top][0] / votes[top] / 255f, sums[top][1] / votes[top] / 255f, sums[top][2] / votes[top] / 255f};
    }

    private static float[] rgb(int colour) {
        return new float[]{Color.red(colour) / 255f, Color.green(colour) / 255f, Color.blue(colour) / 255f};
    }

    private static float clamp(float value) { return Math.max(0, Math.min(1, value)); }

    private static float smoothstep(float from, float to, float value) {
        float t = clamp((value - from) / (to - from));
        return t * t * (3 - 2 * t);
    }

}
