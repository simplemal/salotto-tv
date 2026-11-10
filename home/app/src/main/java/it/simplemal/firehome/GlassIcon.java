package it.simplemal.firehome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * An app as glass on the clean Home, the user's option of 2026-11-10, tried first as a picture on the
 * Mac with the real banners and backdrops. The logo, found in the app's own picture, becomes a white
 * silhouette with a soft shadow on clear glass, the app's colour only as a frame of a few dp inside
 * the edge; a faint line along the edge catches the light from the top left, and a weaker light where
 * it faces the bottom right.
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
     * A logo found whole has most of its pixels inside its strokes: every app tried has 70% or more.
     * Below this only its rim was found.
     */
    private static final float SOLID = 0.3f;
    /**
     * Apps whose picture has a glow around the logo, as NOW's O: only the brightest part is the logo,
     * found by Otsu's split. Not a rule for all: it cut HBO Max's and Apple TV's shaded letters.
     */
    private static final Set<String> GLOW = new HashSet<>(Arrays.asList("com.nowtv.it"));
    private static final int GLOW_RAMP = 20;
    /**
     * A logo of more colours keeps them as layers, the user's request of 2026-11-10: each colour group
     * a different white, the lightest whole, the others clearer, so Aurora's A stays on its basket and
     * Plex's X apart from its letters. A logo of one colour stays one white.
     */
    private static final float[] LAYERS = {1f, 0.65f, 0.44f, 0.32f};
    /** A colour group smaller than this share of the logo is not a layer. */
    private static final float LAYER_SHARE = 0.06f;
    /** Groups closer than this are one colour. */
    private static final int LAYER_APART = 70;
    private static final int LAYER_SAMPLES = 6000;

    /**
     * The app's colour as a frame, the user's design of 2026-11-10: strongest just inside the edge,
     * then quickly clearer towards the middle, so it shows only for a few dp round the glass.
     */
    private static final float VEIL_FRAME = 0.55f;
    private static final float VEIL_MIDDLE = 0.24f;
    private static final float FRAME_DP = 5f;
    /** A faint light over the upper half. */
    private static final float SHEEN = 0.10f;
    private static final float LOGO_WIDTH = 0.66f;
    private static final float LOGO_HEIGHT = 0.42f;
    private static final float WHITE = 0.95f;
    private static final float SHADOW = 0.35f;
    /**
     * The light along the edge: thin, drawn as a smooth stroke so its inner side has no steps, and
     * only just visible, the user's request of 2026-11-10.
     */
    private static final float LINE_DP = 0.75f;
    private static final float LINE_LIGHT = 0.45f;
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
        boolean glow = GLOW.contains(pkg);
        Found found = find(pixels, width, height, edge, glow, false);
        // Only the rim of a logo found: a colour of the logo is also along the edges, as the white band
        // under Amazon miniTV's pink (2026-11-10). Then only the colour most of the edge has is
        // background, and what that leaves touching the edges, the band, goes with it.
        if (found != null && found.core < SOLID) {
            edge = mostOfEdge(edge);
            found = find(pixels, width, height, edge, glow, true);
        }
        if (found == null || found.core < SOLID) return null;
        float[] alpha = found.alpha;
        float[] distance = found.distance;
        float start = found.start;
        int left = found.left;
        int top = found.top;
        int right = found.right;
        int bottom = found.bottom;
        float[] colour = colour(pixels, edge, distance, start);
        layer(pixels, alpha, found.mark, width, height, left, top, right, bottom);

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
        float lineWidth = LINE_DP * density;
        float[] stroke = alphaOf(side, canvas -> {
            Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(lineWidth);
            line.setColor(Color.WHITE);
            Path edgePath = Squircle.path(side - lineWidth);
            edgePath.offset(lineWidth / 2, lineWidth / 2);
            canvas.drawPath(edgePath, line);
        });
        float frame = FRAME_DP * density;
        int[] made = new int[side * side];
        float[] pixel = new float[4];
        for (int y = 0; y < side; y++) {
            float fall = 1 - 0.15f * y / (side - 1f);
            float sheen = Math.max(0, SHEEN * (1 - 2f * y / side));
            for (int x = 0; x < side; x++) {
                int index = y * side + x;
                if (cover[index] <= 0) continue;
                float toMiddle = smoothstep(0, frame, inside[index]);
                pixel[0] = colour[0];
                pixel[1] = colour[1];
                pixel[2] = colour[2];
                pixel[3] = (VEIL_FRAME + (VEIL_MIDDLE - VEIL_FRAME) * toMiddle) * fall;
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
                over(pixel, 1, 1, 1, clamp(stroke[index] * LINE_LIGHT * (0.10f + 0.90f * lit + 0.45f * away)));
                float a = pixel[3] * cover[index];
                made[index] = Math.round(a * 255) << 24 | Math.round(clamp(pixel[0]) * 255) << 16
                    | Math.round(clamp(pixel[1]) * 255) << 8 | Math.round(clamp(pixel[2]) * 255);
            }
        }
        Bitmap glass = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        glass.setPixels(made, 0, side, 0, 0, side, side);
        return glass;
    }

    /** The logo found in a picture: how much of each pixel is logo, where it is, how solid. */
    private static final class Found {
        float[] alpha;
        float[] distance;
        float start;
        int left;
        int top;
        int right;
        int bottom;
        /** The share of the logo whose pixels and their four neighbours are whole: low for a bare rim. */
        float core;
        boolean[] mark;
    }

    /** Null for no logo, or a scene. `dropEdges`: logo touching the picture's edges is background. */
    private static Found find(int[] pixels, int width, int height, int[] edge, boolean glow, boolean dropEdges) {
        Found found = new Found();
        found.distance = new float[pixels.length];
        for (int index = 0; index < pixels.length; index++) found.distance[index] = nearest(pixels[index], edge);
        found.start = glow ? otsu(found.distance, NEAR) : NEAR;
        found.alpha = new float[pixels.length];
        boolean[] mark = new boolean[pixels.length];
        for (int index = 0; index < pixels.length; index++) {
            float far = found.distance[index];
            found.alpha[index] = glow ? clamp((far - found.start) / GLOW_RAMP) : clamp((far - CUT_CLEAR) / (CUT_WHOLE - CUT_CLEAR));
            mark[index] = far > found.start;
        }
        if (dropEdges) {
            dropTouchingEdges(found, mark, width, height);
            dropCrumbs(found, mark, width, height);
        }
        found.left = width;
        found.top = height;
        found.right = -1;
        found.bottom = -1;
        int logo = 0;
        int core = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (!mark[index]) continue;
                logo++;
                found.left = Math.min(found.left, x);
                found.right = Math.max(found.right, x + 1);
                found.top = Math.min(found.top, y);
                found.bottom = Math.max(found.bottom, y + 1);
                if (x > 0 && y > 0 && x < width - 1 && y < height - 1 && whole(found.alpha, index)
                    && whole(found.alpha, index - 1) && whole(found.alpha, index + 1)
                    && whole(found.alpha, index - width) && whole(found.alpha, index + width)) core++;
            }
        }
        if (found.right < 0 || logo > SCENE * width * height) return null;
        found.core = core / (float) logo;
        found.mark = mark;
        return found;
    }

    private static boolean whole(float[] alpha, int index) { return alpha[index] >= 0.95f; }

    /**
     * Clears what touches the picture's edges and all joined to it, its soft rim too, in all eight
     * directions: through hard pixels only, bits of the band's lettering (miniTV's FREE) stayed as dots.
     */
    private static void dropTouchingEdges(Found found, boolean[] mark, int width, int height) {
        boolean[] open = new boolean[mark.length];
        for (int index = 0; index < open.length; index++) open[index] = found.alpha[index] > 0;
        int[] queue = new int[mark.length];
        int tail = 0;
        for (int x = 0; x < width; x++) {
            tail = seed(open, x, queue, tail);
            tail = seed(open, (height - 1) * width + x, queue, tail);
        }
        for (int y = 0; y < height; y++) {
            tail = seed(open, y * width, queue, tail);
            tail = seed(open, y * width + width - 1, queue, tail);
        }
        int head = 0;
        while (head < tail) {
            int index = queue[head++];
            found.alpha[index] = 0;
            mark[index] = false;
            int x = index % width;
            int y = index / width;
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx;
                    int ny = y + dy;
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                    tail = seed(open, ny * width + nx, queue, tail);
                }
            }
        }
    }

    /**
     * Below this share of the logo, a separate bit is a crumb left by what went, not a part of it.
     * Measured on miniTV at the sample's size: the crumb 45 pixels, each i's dot 94, the logo 7317.
     */
    private static final float CRUMB = 0.01f;

    /** Clears the tiny separate bits: a dot of miniTV's FREE stayed under the logo. An i's dot is far larger. */
    private static void dropCrumbs(Found found, boolean[] mark, int width, int height) {
        int total = 0;
        for (boolean marked : mark) if (marked) total++;
        int smallest = Math.max(1, Math.round(total * CRUMB));
        boolean[] seen = new boolean[mark.length];
        int[] queue = new int[mark.length];
        for (int first = 0; first < mark.length; first++) {
            if (!mark[first] || seen[first]) continue;
            int tail = 0;
            seen[first] = true;
            queue[tail++] = first;
            for (int head = 0; head < tail; head++) {
                int index = queue[head];
                int x = index % width;
                int y = index / width;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int nx = x + dx;
                        int ny = y + dy;
                        if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                        int next = ny * width + nx;
                        if (!mark[next] || seen[next]) continue;
                        seen[next] = true;
                        queue[tail++] = next;
                    }
                }
            }
            if (tail >= smallest) continue;
            for (int at = 0; at < tail; at++) {
                mark[queue[at]] = false;
                found.alpha[queue[at]] = 0;
            }
        }
    }

    private static int seed(boolean[] open, int index, int[] queue, int tail) {
        if (!open[index]) return tail;
        open[index] = false;
        queue[tail] = index;
        return tail + 1;
    }

    /** The edge colours near the one most of the edge is close to. */
    private static int[] mostOfEdge(int[] edge) {
        int best = 0;
        int bestCount = -1;
        for (int i = 0; i < edge.length; i++) {
            int count = 0;
            for (int other : edge) if (squared(edge[i], other) <= NEAR * NEAR) count++;
            if (count > bestCount) {
                bestCount = count;
                best = i;
            }
        }
        int[] kept = new int[bestCount];
        int at = 0;
        for (int colour : edge) if (squared(edge[best], colour) <= NEAR * NEAR) kept[at++] = colour;
        return kept;
    }

    private static int squared(int a, int b) {
        int dr = Color.red(a) - Color.red(b);
        int dg = Color.green(a) - Color.green(b);
        int db = Color.blue(a) - Color.blue(b);
        return dr * dr + dg * dg + db * db;
    }

    /**
     * Gives each colour group of the logo its own whiteness. The groups are found only inside the
     * strokes: along their soft rims the colours are mixed with the background, and made false groups.
     */
    private static void layer(int[] pixels, float[] alpha, boolean[] mark, int width, int height,
            int left, int top, int right, int bottom) {
        int[] inner = new int[pixels.length];
        int count = 0;
        for (int y = Math.max(1, top); y < Math.min(height - 1, bottom); y++) {
            for (int x = Math.max(1, left); x < Math.min(width - 1, right); x++) {
                int index = y * width + x;
                if (mark[index] && whole(alpha, index) && whole(alpha, index - 1) && whole(alpha, index + 1)
                    && whole(alpha, index - width) && whole(alpha, index + width)) inner[count++] = pixels[index];
            }
        }
        if (count < LAYERS.length) return;
        int step = Math.max(1, count / LAYER_SAMPLES);
        int used = 0;
        for (int at = 0; at < count; at += step) inner[used++] = inner[at];
        // Four starting colours, from dark to light, then a few rounds of k-means.
        Integer[] order = new Integer[used];
        for (int at = 0; at < used; at++) order[at] = at;
        final int[] picked = inner;
        Arrays.sort(order, (a, b) -> Float.compare(luminance(picked[a]), luminance(picked[b])));
        float[][] centre = new float[LAYERS.length][];
        float[] quantile = {0.1f, 0.4f, 0.7f, 0.95f};
        for (int k = 0; k < centre.length; k++) centre[k] = rgbOf(inner[order[Math.round(quantile[k] * (used - 1))]]);
        int[] group = new int[used];
        for (int round = 0; round < 10; round++) {
            float[][] sum = new float[centre.length][3];
            int[] size = new int[centre.length];
            for (int at = 0; at < used; at++) {
                group[at] = closest(centre, inner[at]);
                float[] c = rgbOf(inner[at]);
                for (int channel = 0; channel < 3; channel++) sum[group[at]][channel] += c[channel];
                size[group[at]]++;
            }
            for (int k = 0; k < centre.length; k++) {
                if (size[k] == 0) continue;
                for (int channel = 0; channel < 3; channel++) centre[k][channel] = sum[k][channel] / size[k];
            }
        }
        int[] size = new int[centre.length];
        for (int at = 0; at < used; at++) size[group[at]]++;
        // Close groups are one colour, tiny ones are not layers; the rest, lightest first.
        java.util.List<float[]> kept = new java.util.ArrayList<>();
        for (int k = 0; k < centre.length; k++) {
            if (size[k] < LAYER_SHARE * used) continue;
            boolean merged = false;
            for (float[] other : kept) {
                if (apart(other, centre[k]) < LAYER_APART * LAYER_APART) {
                    merged = true;
                    break;
                }
            }
            if (!merged) kept.add(centre[k]);
        }
        if (kept.size() < 2) return;
        kept.sort((a, b) -> Float.compare(luminance(b), luminance(a)));
        float[][] layers = kept.toArray(new float[0][]);
        for (int y = top; y < bottom; y++) {
            for (int x = left; x < right; x++) {
                int index = y * width + x;
                if (alpha[index] <= 0) continue;
                alpha[index] *= LAYERS[Math.min(LAYERS.length - 1, closest(layers, pixels[index]))];
            }
        }
    }

    private static int closest(float[][] centres, int pixel) {
        float[] c = rgbOf(pixel);
        int best = 0;
        float bestApart = Float.MAX_VALUE;
        for (int k = 0; k < centres.length; k++) {
            float away = apart(centres[k], c);
            if (away < bestApart) {
                bestApart = away;
                best = k;
            }
        }
        return best;
    }

    private static float apart(float[] a, float[] b) {
        float dr = a[0] - b[0];
        float dg = a[1] - b[1];
        float db = a[2] - b[2];
        return dr * dr + dg * dg + db * db;
    }

    private static float[] rgbOf(int pixel) { return new float[]{Color.red(pixel), Color.green(pixel), Color.blue(pixel)}; }

    private static float luminance(int pixel) { return luminance(rgbOf(pixel)); }

    private static float luminance(float[] c) { return 0.299f * c[0] + 0.587f * c[1] + 0.114f * c[2]; }

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
