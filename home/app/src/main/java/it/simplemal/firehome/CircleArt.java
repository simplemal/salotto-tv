package it.simplemal.firehome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * An app's own artwork made round for the wheel. The logo is found in the picture, as what differs
 * from the colours along its edges, then centred and sized to sit whole inside the circle: a long
 * name like Netflix's no longer touches the rim, a small one like RaiPlay's no longer gets lost.
 * The picture's edges fade into a blurred copy of itself, so no straight line crosses the circle.
 */
final class CircleArt {
    static final int PLAIN = Color.rgb(60, 64, 72);
    private static final int SAMPLE = 160;
    /** How far a colour must be from every edge colour to count as logo: a gradient stays background. */
    private static final int NEAR = 60;
    /** Past this share of logo the picture is a scene, not a logo on a background: it is kept as it was. */
    private static final float SCENE = 0.6f;
    /** The logo's corners stay inside this share of the diameter. */
    private static final float DIAGONAL = 0.76f;
    private static final float WIDEST = 0.74f;
    private static final float TALLEST = 0.48f;
    /** A tiny logo is not blown up past this, in diameters of picture width. */
    private static final float MOST = 3f;
    /** A scene is drawn this much wider than the circle, as before. */
    private static final float SCENE_WIDTH = 1.25f;
    /** The faded border of the picture, as a share of its shorter side. */
    private static final float FEATHER = 0.14f;
    private static final int EDGE_STEP = 2;
    /** How far the background's blur reaches, in parts of the sample's width: enough to fill a long name. */
    private static final int BACK_REACH = 16;
    private static final int BACK_PASSES = 3;

    private CircleArt() {}

    static Bitmap make(Drawable image, Drawable back, boolean wide, int size) {
        int width = SAMPLE;
        int height = wide ? SAMPLE * 9 / 16 : SAMPLE;
        Bitmap sample = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        paint(new Canvas(sample), image, back, width, height);
        int[] pixels = new int[width * height];
        sample.getPixels(pixels, 0, width, 0, 0, width, height);
        sample.recycle();

        int[] edge = edgeColours(pixels, width, height);
        int left = width;
        int top = height;
        int right = -1;
        int bottom = -1;
        int logo = 0;
        boolean[] mark = new boolean[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!far(pixels[y * width + x], edge)) continue;
                mark[y * width + x] = true;
                logo++;
                left = Math.min(left, x);
                right = Math.max(right, x + 1);
                top = Math.min(top, y);
                bottom = Math.max(bottom, y + 1);
            }
        }

        float pictureWidth;
        float centreX = 0.5f;
        float centreY = 0.5f;
        if (right < 0 || logo > SCENE * width * height) {
            pictureWidth = size * (wide ? SCENE_WIDTH : 1f);
            // A scene has no logo to take out: its background is the whole picture, blurred.
            mark = new boolean[width * height];
        } else {
            // Both sides in widths of the picture, which is what the scale multiplies.
            float logoWidth = (right - left) / (float) width;
            float logoHeight = (bottom - top) / (float) width;
            pictureWidth = Math.min(Math.min(DIAGONAL * size / (float) Math.hypot(logoWidth, logoHeight),
                WIDEST * size / logoWidth), Math.min(TALLEST * size / logoHeight, MOST * size));
            centreX = (left + right) / 2f / width;
            centreY = (top + bottom) / 2f / height;
        }
        float pictureHeight = pictureWidth * height / width;

        Bitmap made = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(made);
        // The background is the same picture, as tall as the circle and blurred, the user's idea after
        // Prime Video's diagonal blue showed a box. The logo is taken out first, or its colour would
        // tint the circle around the sharp one: Netflix's red, YouTube's.
        int coverWidth = Math.max(size, size * width / height);
        Bitmap soft = Bitmap.createBitmap(withoutLogo(pixels, mark, width, height), width, height, Bitmap.Config.ARGB_8888);
        Rect spread = new Rect((size - coverWidth) / 2, 0, (size + coverWidth) / 2, size);
        canvas.drawBitmap(soft, null, spread, new Paint(Paint.FILTER_BITMAP_FLAG));
        soft.recycle();

        int drawnWidth = Math.max(1, Math.round(pictureWidth));
        int drawnHeight = Math.max(1, Math.round(pictureHeight));
        Bitmap picture = Bitmap.createBitmap(drawnWidth, drawnHeight, Bitmap.Config.ARGB_8888);
        Canvas inside = new Canvas(picture);
        paint(inside, image, back, drawnWidth, drawnHeight);
        feather(inside, drawnWidth, drawnHeight);
        canvas.drawBitmap(picture, size / 2f - centreX * drawnWidth, size / 2f - centreY * drawnHeight,
            new Paint(Paint.FILTER_BITMAP_FLAG));
        picture.recycle();
        return made;
    }

    /**
     * The picture with the logo filled from around it and blurred: each pixel is the average of the
     * background pixels near it, so a gradient stays a gradient and the logo leaves no trace.
     */
    private static int[] withoutLogo(int[] pixels, boolean[] mark, int width, int height) {
        int count = width * height;
        float[][] sums = new float[4][count];
        for (int index = 0; index < count; index++) {
            if (mark[index]) continue;
            int pixel = pixels[index];
            sums[0][index] = Color.red(pixel);
            sums[1][index] = Color.green(pixel);
            sums[2][index] = Color.blue(pixel);
            sums[3][index] = 1;
        }
        int reach = Math.max(1, width / BACK_REACH);
        float[] turned = new float[count];
        for (float[] sum : sums) {
            for (int pass = 0; pass < BACK_PASSES; pass++) {
                spread(sum, turned, width, height, reach);
                spread(turned, sum, height, width, reach);
            }
        }
        int[] smooth = new int[count];
        for (int index = 0; index < count; index++) {
            float weight = sums[3][index];
            // Where no background reached, the logo covered everything: its own pixel will do.
            if (weight < 1e-4f) {
                smooth[index] = pixels[index] | 0xFF000000;
                continue;
            }
            smooth[index] = Color.rgb(Math.round(sums[0][index] / weight), Math.round(sums[1][index] / weight),
                Math.round(sums[2][index] / weight));
        }
        return smooth;
    }

    /** A moving average along each row of `from`, written to `to` turned on its side. */
    private static void spread(float[] from, float[] to, int width, int height, int reach) {
        float window = 2 * reach + 1;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            float total = 0;
            for (int x = -reach; x <= reach; x++) total += from[row + Math.max(0, Math.min(width - 1, x))];
            for (int x = 0; x < width; x++) {
                to[x * height + y] = total / window;
                total += from[row + Math.min(width - 1, x + reach + 1)] - from[row + Math.max(0, x - reach)];
            }
        }
    }

    /** A see-through picture is seen on the plain grey, as on the grid. */
    private static void paint(Canvas canvas, Drawable image, Drawable back, int width, int height) {
        canvas.drawColor(PLAIN);
        if (back != null) {
            back.setBounds(0, 0, width, height);
            back.draw(canvas);
        }
        image.setBounds(0, 0, width, height);
        image.draw(canvas);
    }

    private static int[] edgeColours(int[] pixels, int width, int height) {
        int count = 0;
        int[] edge = new int[(width + height) * 2 / EDGE_STEP + 4];
        for (int x = 0; x < width; x += EDGE_STEP) {
            edge[count++] = pixels[x];
            edge[count++] = pixels[(height - 1) * width + x];
        }
        for (int y = 0; y < height; y += EDGE_STEP) {
            edge[count++] = pixels[y * width];
            edge[count++] = pixels[y * width + width - 1];
        }
        int[] used = new int[count];
        System.arraycopy(edge, 0, used, 0, count);
        return used;
    }

    private static boolean far(int pixel, int[] edge) {
        int red = Color.red(pixel);
        int green = Color.green(pixel);
        int blue = Color.blue(pixel);
        int limit = NEAR * NEAR;
        for (int colour : edge) {
            int dr = red - Color.red(colour);
            int dg = green - Color.green(colour);
            int db = blue - Color.blue(colour);
            if (dr * dr + dg * dg + db * db <= limit) return false;
        }
        return true;
    }


    /** Clears the border little by little, so the picture melts into the background under it. */
    private static void feather(Canvas canvas, int width, int height) {
        float band = FEATHER * Math.min(width, height);
        Paint paint = new Paint();
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        int gone = Color.BLACK;
        int kept = Color.TRANSPARENT;
        paint.setShader(new LinearGradient(0, 0, band, 0, gone, kept, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, band, height, paint);
        paint.setShader(new LinearGradient(width, 0, width - band, 0, gone, kept, Shader.TileMode.CLAMP));
        canvas.drawRect(width - band, 0, width, height, paint);
        paint.setShader(new LinearGradient(0, 0, 0, band, gone, kept, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, width, band, paint);
        paint.setShader(new LinearGradient(0, height, 0, height - band, gone, kept, Shader.TileMode.CLAMP));
        canvas.drawRect(0, height - band, width, height, paint);
    }
}
