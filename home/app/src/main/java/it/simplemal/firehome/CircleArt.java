package it.simplemal.firehome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

/**
 * An app's own artwork made round for the wheel. The logo is found in the picture, as what differs
 * from the colours along its edges, then centred and sized to sit whole inside the circle: a long
 * name like Netflix's no longer touches the rim, a small one like RaiPlay's no longer gets lost.
 * The picture's edges fade into its own background, so no straight line crosses the circle.
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
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!far(pixels[y * width + x], edge)) continue;
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
        GradientDrawable fill = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{rowColour(pixels, width, 0), rowColour(pixels, width, height - 1)});
        fill.setBounds(0, 0, size, size);
        fill.draw(canvas);

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

    private static int rowColour(int[] pixels, int width, int row) {
        long red = 0;
        long green = 0;
        long blue = 0;
        for (int x = 0; x < width; x++) {
            int pixel = pixels[row * width + x];
            red += Color.red(pixel);
            green += Color.green(pixel);
            blue += Color.blue(pixel);
        }
        return Color.rgb((int) (red / width), (int) (green / width), (int) (blue / width));
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
