package it.simplemal.firehome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * The squircle of the clean Home's apps, the shape of iOS's icons, drawn exactly as the Squircle
 * library does it (github.com/neobeppe/Squircle): a square whose sides run straight into corners
 * made of two curves each, from points measured on a side of 256.
 */
final class Squircle {
    private static final float SIDE = 256f;
    private static final float START = 87.15f;
    private static final float P0 = 219.33f;
    private static final float P1 = 198.35f;
    private static final float P2 = 206.63f;
    private static final float P3 = 251.73f;
    private static final float P4 = 234.35f;
    private static final float P5 = 246.24f;

    private Squircle() {}

    /** The outline of a square of `side`, from (0, 0). */
    static Path path(float side) {
        float k = side / SIDE;
        float s = side;
        float start = START * k;
        float c0 = P0 * k;
        float c1 = P1 * k;
        float c2 = P2 * k;
        float c3 = P3 * k;
        float c4 = P4 * k;
        float c5 = P5 * k;
        Path path = new Path();
        path.moveTo(start, 0);
        path.lineTo(s - start, 0);
        path.cubicTo(c1, 0, c2, 0, c0, s - c3);
        path.cubicTo(c4, s - c5, c5, s - c4, c3, s - c0);
        path.cubicTo(s, s - c2, s, s - c1, s, start);
        path.lineTo(s, s - start);
        path.cubicTo(s, c1, s, c2, c3, c0);
        path.cubicTo(c5, c4, c4, c5, c0, c3);
        path.cubicTo(c2, s, c1, s, s - start, s);
        path.lineTo(start, s);
        path.cubicTo(s - c1, s, s - c2, s, s - c0, c3);
        path.cubicTo(s - c4, c5, s - c5, c4, s - c3, c0);
        path.cubicTo(0, c2, 0, c1, 0, s - start);
        path.lineTo(0, start);
        path.cubicTo(0, s - c1, 0, s - c2, s - c3, s - c0);
        path.cubicTo(s - c5, s - c4, s - c4, s - c5, s - c0, s - c3);
        path.cubicTo(s - c2, 0, s - c1, 0, start, 0);
        path.close();
        return path;
    }

    /**
     * A square picture cut to the squircle; the corners are left clear. The shape is drawn smooth
     * first and the picture only where it is: a clip has no half pixels, and its steps showed along
     * the curves once the focus made the icon larger (2026-11-09).
     */
    static Bitmap cut(Bitmap square) {
        int side = Math.min(square.getWidth(), square.getHeight());
        Bitmap made = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(made);
        canvas.drawPath(path(side), new Paint(Paint.ANTI_ALIAS_FLAG));
        Paint picture = new Paint(Paint.FILTER_BITMAP_FLAG);
        picture.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(square, 0, 0, picture);
        return made;
    }

    /** A line along the squircle, inside its edge: the focus ring, or the thin light of glass at rest. */
    static final class Edge extends Drawable {
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float width;
        private Path shape;
        private int shapeSide = -1;

        Edge(int colour, float width) {
            this.width = width;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(width);
            line.setColor(colour);
        }

        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            int side = Math.min(bounds.width(), bounds.height());
            if (side <= 0) return;
            if (side != shapeSide) {
                // Half the line inside: drawn on the edge itself, its outer half would be cut.
                float inner = side - width;
                shape = path(inner);
                shape.offset(width / 2, width / 2);
                shapeSide = side;
            }
            canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.drawPath(shape, line);
            canvas.restore();
        }

        @Override public void setAlpha(int alpha) { line.setAlpha(alpha); }

        @Override public void setColorFilter(ColorFilter filter) { line.setColorFilter(filter); }

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
