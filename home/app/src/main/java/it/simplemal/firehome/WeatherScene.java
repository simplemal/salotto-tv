package it.simplemal.firehome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.Xml;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/**
 * One weather scene, from the SVG files in assets/meteo. Android has no SVG renderer, and the
 * files use only paths, circles, gradients and a blur, so each group is drawn once into a bitmap
 * at half the screen's resolution. A scene enters in three seconds and then stays still: moving it
 * only places those bitmaps. Rain and snow are three tiled sheets that slide, slow down and stop.
 * The lightning of the drawing is left out, because it only makes sense as a flash.
 */
final class WeatherScene implements WeatherView.Scene {
    /** How long a scene moves each time the Home comes back. */
    static final float ENTRY_S = 3;

    private static final float CANVAS = 3840;
    private static final float RESOLUTION = 0.5f;
    private static final float MOVE_X = 0.051f;
    private static final float MOVE_Y = 0.0235f;
    private static final float SCROLL_Y = 0.018f;
    private static final float RAIN_DRIFT = -0.24f;
    private static final float SNOW_DRIFT = -0.12f;
    private static final float BLIZZARD_DRIFT = -0.65f;
    private static final float BRAKE_S = 1;
    private static final float CLOUD_DELAY_S = 0.25f;
    /** Sun, moon and stars come down too, but far less than the clouds: that is the depth. */
    private static final float CELESTIAL_TRAVEL = 0.14f;
    private static final int SHEETS = 3;
    private static final float[] SHEET_SPEED = {0.75f, 1f, 1.25f};
    private static final float[] SHEET_SWAY = {0.003f, 0.004f, 0.005f};
    private static final float[] SHEET_SWAY_S = {9, 13, 17};
    private static final float TAU = 6.2831855f;

    private static final class Layer {
        Bitmap image;
        float parallax;
        float delay;
        int from;
        float away;
    }

    /** Drops of similar speed, drawn once on a tile that repeats in both directions. */
    private static final class Sheet {
        final Bitmap mask;
        final Canvas canvas;
        final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        final Matrix slide = new Matrix();
        BitmapShader shader;
        float fall;
        float drift;
        float sway;
        float swayPeriod;
        float swayOffset;

        Sheet(int width, int height) {
            mask = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);
            canvas = new Canvas(mask);
            stroke.setStrokeCap(Paint.Cap.ROUND);
        }
    }

    private final Paint image = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF place = new RectF();
    private final Sheet[] sheets = new Sheet[SHEETS];
    private final String name;
    private final int width;
    private final int height;
    private final float marginX;
    private final float marginY;
    private final float scale;
    private Bitmap sky;
    private Layer celestial;
    private Layer distant;
    private Layer main;
    private Layer fog;
    private float speed;
    private boolean snow;
    private boolean blizzard;
    private boolean falling;
    private int dropCount;

    private WeatherScene(String name, int width, int height) {
        this.name = name;
        this.width = width;
        this.height = height;
        marginX = MOVE_X * width;
        marginY = MOVE_Y * height;
        scale = Math.max(width + 2 * marginX, height + 2 * marginY) / CANVAS;
    }

    /** Null if the scene cannot be read: the Home then keeps its plain background. */
    static WeatherScene load(Context context, String name, int width, int height) {
        WeatherScene scene = new WeatherScene(name, width, height);
        try {
            scene.readNumbers(context);
            scene.readDrawing(context);
            scene.finish();
            return scene;
        } catch (IOException | XmlPullParserException | RuntimeException e) {
            return null;
        }
    }

    /** The scene must not be drawn after this: its bitmaps go at once instead of waiting for the GC. */
    @Override public void release() {
        if (sky != null) sky.recycle();
        for (Layer layer : new Layer[]{celestial, distant, main, fog}) {
            if (layer != null) layer.image.recycle();
        }
        for (Sheet sheet : sheets) {
            if (sheet != null) sheet.mask.recycle();
        }
    }

    private void readNumbers(Context context) throws IOException {
        String[] parts = name.split("/");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("meteo/parametri.csv"), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) {
                String[] row = line.split(",");
                if (!row[0].equals(parts[0]) || !row[1].equals(parts[1])) continue;
                speed = Float.parseFloat(row[2]) / 100 * height;
                snow = parts[1].contains("snow");
                blizzard = parts[1].contains("heavy_snow");
                return;
            }
        }
        throw new IOException("Scena assente");
    }

    private void readDrawing(Context context) throws IOException, XmlPullParserException {
        String file = "meteo/" + name + ".svg";
        Map<String, Gradient> gradients = new HashMap<>();
        Map<String, Float> blurs = new HashMap<>();
        try (InputStream input = context.getAssets().open(file)) {
            readDefinitions(input, gradients, blurs);
        }
        try (InputStream input = context.getAssets().open(file)) {
            new Painter(gradients, blurs).run(input);
        }
    }

    private static final class Gradient {
        boolean radial;
        float x1, y1, x2 = 1, y2;
        final List<Integer> colors = new ArrayList<>();
        final List<Float> stops = new ArrayList<>();
    }

    private static float number(XmlPullParser parser, String name, float fallback) {
        String value = parser.getAttributeValue(null, name);
        return value == null ? fallback : Float.parseFloat(value);
    }

    private static int color(String value) {
        if (value.length() == 4) {
            char r = value.charAt(1);
            char g = value.charAt(2);
            char b = value.charAt(3);
            return Color.parseColor("#" + r + r + g + g + b + b);
        }
        return Color.parseColor(value);
    }

    private static int withAlpha(int color, float opacity) {
        return Color.argb(Math.round(Color.alpha(color) * opacity), Color.red(color), Color.green(color), Color.blue(color));
    }

    private static void readDefinitions(InputStream input, Map<String, Gradient> gradients, Map<String, Float> blurs)
            throws IOException, XmlPullParserException {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(input, "UTF-8");
        Gradient current = null;
        String filter = null;
        for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
            if (event != XmlPullParser.START_TAG) continue;
            switch (parser.getName()) {
                case "linearGradient":
                case "radialGradient":
                    current = new Gradient();
                    current.radial = parser.getName().startsWith("r");
                    current.x1 = number(parser, "x1", 0);
                    current.y1 = number(parser, "y1", 0);
                    current.x2 = number(parser, "x2", 1);
                    current.y2 = number(parser, "y2", 0);
                    gradients.put(parser.getAttributeValue(null, "id"), current);
                    break;
                case "stop":
                    current.colors.add(withAlpha(color(parser.getAttributeValue(null, "stop-color")),
                        number(parser, "stop-opacity", 1)));
                    current.stops.add(number(parser, "offset", 0));
                    break;
                case "filter":
                    filter = parser.getAttributeValue(null, "id");
                    break;
                case "feGaussianBlur":
                    blurs.put(filter, number(parser, "stdDeviation", 0));
                    break;
                default:
                    break;
            }
        }
    }

    /** What a group passes down to its children. */
    private static final class Style {
        String fill = "#000";
        String filter;
        boolean round;
        Style copy() {
            Style style = new Style();
            style.fill = fill;
            style.filter = filter;
            style.round = round;
            return style;
        }
    }

    /** Draws the file in one pass, each top group into its own bitmap. */
    private final class Painter {
        private final Map<String, Gradient> gradients;
        private final Map<String, Float> blurs;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Deque<Style> styles = new ArrayDeque<>();
        private final Deque<Integer> saves = new ArrayDeque<>();
        private final Matrix place = new Matrix();
        private Canvas canvas;
        private boolean particles;
        private int depth;

        Painter(Map<String, Gradient> gradients, Map<String, Float> blurs) {
            this.gradients = gradients;
            this.blurs = blurs;
            place.setScale(scale, scale);
            place.postTranslate(-marginX, (height - CANVAS * scale) / 2);
        }

        void run(InputStream input) throws IOException, XmlPullParserException {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(input, "UTF-8");
            styles.push(new Style());
            for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
                if (event == XmlPullParser.START_TAG) start(parser);
                else if (event == XmlPullParser.END_TAG && parser.getName().equals("g")) end();
            }
        }

        private Bitmap blank() {
            return Bitmap.createBitmap(Math.round((width + 2 * marginX) * RESOLUTION),
                Math.round((height + 2 * marginY) * RESOLUTION), Bitmap.Config.ARGB_8888);
        }

        /** Fog sits low on the square drawing: it stays on the bottom edge, not in the centre. */
        private Canvas surface(Bitmap bitmap, boolean toBottom) {
            Canvas target = new Canvas(bitmap);
            target.scale(RESOLUTION, RESOLUTION);
            target.translate(marginX, marginY);
            if (toBottom) {
                Matrix low = new Matrix();
                low.setScale(scale, scale);
                low.postTranslate(-marginX, height + marginY - CANVAS * scale);
                target.concat(low);
            } else {
                target.concat(place);
            }
            return target;
        }

        private Layer layer(float parallax, float delay, int from) {
            Layer layer = new Layer();
            layer.image = blank();
            layer.parallax = parallax;
            layer.delay = delay;
            layer.from = from;
            return layer;
        }

        private void openGroup(String id) {
            particles = false;
            switch (id) {
                case "layer_0_sky":
                    sky = blank();
                    canvas = surface(sky, false);
                    break;
                case "layer_1_celestial":
                    celestial = layer(0.10f, 0, -1);
                    celestial.away = CELESTIAL_TRAVEL * height;
                    canvas = surface(celestial.image, false);
                    break;
                case "layer_2_distant_clouds":
                    distant = layer(0.30f, 0, -1);
                    canvas = surface(distant.image, false);
                    break;
                case "layer_4_main_clouds":
                    main = layer(0.70f, CLOUD_DELAY_S, -1);
                    canvas = surface(main.image, false);
                    break;
                case "layer_5_fog":
                    fog = layer(1.00f, CLOUD_DELAY_S, 1);
                    canvas = surface(fog.image, true);
                    break;
                case "layer_5_precipitation":
                    particles = true;
                    canvas = null;
                    break;
                default:
                    canvas = null;
                    break;
            }
        }

        private void start(XmlPullParser parser) {
            String tag = parser.getName();
            if (tag.equals("g")) {
                if (depth == 0) openGroup(parser.getAttributeValue(null, "id"));
                depth++;
                group(parser);
                return;
            }
            if (depth == 0) return;
            Style style = styles.peek();
            float opacity = number(parser, "opacity", 1);
            Path shape;
            switch (tag) {
                case "path":
                    if (particles) {
                        addDrop(parser, opacity);
                        return;
                    }
                    shape = PathReader.read(parser.getAttributeValue(null, "d"));
                    break;
                case "circle":
                    if (particles) {
                        addFlake(parser, opacity);
                        return;
                    }
                    shape = new Path();
                    shape.addCircle(number(parser, "cx", 0), number(parser, "cy", 0), number(parser, "r", 0), Path.Direction.CW);
                    break;
                case "ellipse":
                    float cx = number(parser, "cx", 0);
                    float cy = number(parser, "cy", 0);
                    float rx = number(parser, "rx", 0);
                    float ry = number(parser, "ry", 0);
                    shape = new Path();
                    shape.addOval(new RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW);
                    break;
                case "rect":
                    shape = new Path();
                    shape.addRect(0, 0, number(parser, "width", 0), number(parser, "height", 0), Path.Direction.CW);
                    break;
                default:
                    return;
            }
            if (canvas != null) paint(parser, shape, style, opacity);
        }

        private void group(XmlPullParser parser) {
            Style style = styles.peek().copy();
            String fill = parser.getAttributeValue(null, "fill");
            if (fill != null) style.fill = fill;
            if ("round".equals(parser.getAttributeValue(null, "stroke-linecap"))) style.round = true;
            String filter = parser.getAttributeValue(null, "filter");
            if (filter != null) style.filter = filter;
            styles.push(style);
            if (canvas == null) {
                saves.push(-1);
                return;
            }
            float opacity = number(parser, "opacity", 1);
            int save = opacity < 1 ? canvas.saveLayerAlpha(null, Math.round(opacity * 255)) : canvas.save();
            saves.push(save);
            String transform = parser.getAttributeValue(null, "transform");
            if (transform != null) canvas.concat(PathReader.transform(transform));
        }

        private void end() {
            styles.pop();
            int save = saves.pop();
            if (canvas != null && save >= 0) canvas.restoreToCount(save);
            depth--;
        }

        private void paint(XmlPullParser parser, Path shape, Style inherited, float opacity) {
            String fill = parser.getAttributeValue(null, "fill");
            if (fill == null) fill = inherited.fill;
            String stroke = parser.getAttributeValue(null, "stroke");
            String filter = parser.getAttributeValue(null, "filter");
            if (filter == null) filter = inherited.filter;
            paint.reset();
            paint.setAntiAlias(true);
            paint.setMaskFilter(blur(filter));
            if (!fill.equals("none")) {
                paint.setStyle(Paint.Style.FILL);
                apply(fill, shape, opacity);
                canvas.drawPath(shape, paint);
            }
            if (stroke != null && !stroke.equals("none")) {
                paint.setShader(null);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(number(parser, "stroke-width", 1));
                paint.setStrokeCap(inherited.round || "round".equals(parser.getAttributeValue(null, "stroke-linecap"))
                    ? Paint.Cap.ROUND : Paint.Cap.BUTT);
                paint.setStrokeJoin(Paint.Join.ROUND);
                apply(stroke, shape, opacity);
                canvas.drawPath(shape, paint);
            }
        }

        private BlurMaskFilter blur(String filter) {
            if (filter == null) return null;
            Float deviation = blurs.get(filter.substring(5, filter.length() - 1));
            if (deviation == null || deviation <= 0) return null;
            return new BlurMaskFilter(deviation * scale * RESOLUTION * 1.73f, BlurMaskFilter.Blur.NORMAL);
        }

        private void apply(String value, Path shape, float opacity) {
            if (!value.startsWith("url")) {
                paint.setShader(null);
                paint.setColor(withAlpha(color(value), opacity));
                return;
            }
            Gradient gradient = gradients.get(value.substring(5, value.length() - 1));
            paint.setColor(withAlpha(Color.BLACK, opacity));
            RectF box = new RectF();
            shape.computeBounds(box, true);
            int[] colors = new int[gradient.colors.size()];
            float[] stops = new float[colors.length];
            for (int index = 0; index < colors.length; index++) {
                colors[index] = gradient.colors.get(index);
                stops[index] = gradient.stops.get(index);
            }
            Shader shader = gradient.radial
                ? new RadialGradient(0.5f, 0.5f, 0.5f, colors, stops, Shader.TileMode.CLAMP)
                : new LinearGradient(gradient.x1, gradient.y1, gradient.x2, gradient.y2, colors, stops, Shader.TileMode.CLAMP);
            Matrix toBox = new Matrix();
            toBox.setScale(box.width(), box.height());
            toBox.postTranslate(box.left, box.top);
            shader.setLocalMatrix(toBox);
            paint.setShader(shader);
        }

        private Sheet sheet() {
            int index = dropCount++ % SHEETS;
            if (sheets[index] == null) {
                sheets[index] = new Sheet(Math.round(width * RESOLUTION), Math.round(height * RESOLUTION));
                sheets[index].canvas.scale(RESOLUTION, RESOLUTION);
            }
            falling = true;
            return sheets[index];
        }

        /** On every side, so that a drop crossing the edge of the tile comes back whole. */
        private void stamp(Sheet sheet, float x, float y, float dx, float dy) {
            float tileX = ((x % width) + width) % width;
            float tileY = ((y % height) + height) % height;
            for (int across = -1; across <= 1; across++) {
                for (int down = -1; down <= 1; down++) {
                    float left = tileX + across * width;
                    float top = tileY + down * height;
                    if (dx == 0 && dy == 0) sheet.canvas.drawPoint(left, top, sheet.stroke);
                    else sheet.canvas.drawLine(left, top, left + dx, top + dy, sheet.stroke);
                }
            }
        }

        private boolean visible(float y) { return y >= 0 && y < height; }

        /** Every drop in the files is one short line, "M x y l dx dy". */
        private void addDrop(XmlPullParser parser, float opacity) {
            float[] line = PathReader.line(parser.getAttributeValue(null, "d"));
            if (line == null) return;
            float[] start = {line[0], line[1]};
            place.mapPoints(start);
            if (!visible(start[1])) return;
            Sheet sheet = sheet();
            sheet.stroke.setStrokeWidth(number(parser, "stroke-width", 1) * scale);
            sheet.stroke.setAlpha(Math.round(opacity * 255));
            sheet.paint.setColor(color(parser.getAttributeValue(null, "stroke")));
            stamp(sheet, start[0], start[1], line[2] * scale, line[3] * scale);
        }

        private void addFlake(XmlPullParser parser, float opacity) {
            float[] center = {number(parser, "cx", 0), number(parser, "cy", 0)};
            place.mapPoints(center);
            if (!visible(center[1])) return;
            Sheet sheet = sheet();
            sheet.stroke.setStrokeWidth(2 * number(parser, "r", 0) * scale);
            sheet.stroke.setAlpha(Math.round(opacity * 255));
            String fill = parser.getAttributeValue(null, "fill");
            sheet.paint.setColor(color(fill != null ? fill : styles.peek().fill));
            stamp(sheet, center[0], center[1], 0, 0);
        }
    }

    private static float hash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            long value = (digest[0] & 255L) << 24 | (digest[1] & 255L) << 16 | (digest[2] & 255L) << 8 | digest[3] & 255L;
            return value / 4294967296f;
        } catch (NoSuchAlgorithmException e) {
            return 0;
        }
    }

    private static float eased(float u) {
        return u * u * u * (u * (6 * u - 15) + 10);
    }

    private static float clamp(float x) { return Math.min(1, Math.max(0, x)); }

    /** How far a group has to start to be truly off screen, whatever its shape. */
    private void measure(Layer layer) {
        if (layer == null) return;
        Bitmap image = layer.image;
        int first = image.getHeight();
        int last = 0;
        for (int y = 0; y < image.getHeight(); y += 4) {
            for (int x = 0; x < image.getWidth(); x += 16) {
                if (Color.alpha(image.getPixel(x, y)) > 0) {
                    first = Math.min(first, y);
                    last = y;
                    break;
                }
            }
        }
        float top = first / RESOLUTION - marginY;
        float bottom = last / RESOLUTION - marginY;
        layer.away = layer.from < 0 ? bottom + 0.02f * height : height - top + 0.02f * height;
    }

    private void finish() {
        // The sky fills its bitmap: saying so spares the GPU blending it over nothing.
        if (sky != null) sky.setHasAlpha(false);
        measure(distant);
        measure(main);
        measure(fog);
        float drift = blizzard ? BLIZZARD_DRIFT : snow ? SNOW_DRIFT : RAIN_DRIFT;
        for (int index = 0; index < SHEETS; index++) {
            Sheet sheet = sheets[index];
            if (sheet == null) continue;
            sheet.fall = speed * SHEET_SPEED[index];
            sheet.drift = sheet.fall * drift;
            sheet.sway = snow ? width * SHEET_SWAY[index] : 0;
            sheet.swayPeriod = SHEET_SWAY_S[index];
            sheet.swayOffset = TAU * hash(name + "/sheet/" + index);
            sheet.shader = new BitmapShader(sheet.mask, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
            sheet.paint.setShader(sheet.shader);
        }
    }

    private void drawLayer(Canvas canvas, Layer layer, float t, float scroll, float alpha) {
        if (layer == null) return;
        float y = -layer.parallax * SCROLL_Y * height * scroll;
        y += layer.from * layer.away * (1 - eased(clamp((t - layer.delay) / (ENTRY_S - layer.delay))));
        drawImage(canvas, layer.image, 0, y, alpha);
    }

    private void drawImage(Canvas canvas, Bitmap bitmap, float x, float y, float alpha) {
        image.setAlpha(Math.round(alpha * 255));
        place.set(x - marginX, y - marginY, x + width + marginX, y + height + marginY);
        canvas.drawBitmap(bitmap, null, place, image);
    }

    private static float turn(double value, float size) {
        double left = value % size;
        return (float) (left < 0 ? left + size : left);
    }

    /** Rain arrives with the clouds that bring it, not after them. */
    private void drawSheets(Canvas canvas, double local, float fade) {
        if (fade <= 0) return;
        for (Sheet sheet : sheets) {
            if (sheet == null) continue;
            double sway = sheet.sway == 0 ? 0 : sheet.sway * Math.sin(TAU * local / sheet.swayPeriod + sheet.swayOffset);
            sheet.slide.setScale(1 / RESOLUTION, 1 / RESOLUTION);
            sheet.slide.postTranslate(turn(sheet.drift * local + sway, width), turn(sheet.fall * local, height));
            sheet.shader.setLocalMatrix(sheet.slide);
            sheet.paint.setAlpha(Math.round(255 * fade));
            canvas.drawRect(0, 0, width, height, sheet.paint);
        }
    }

    /** How far the drops have fallen: they keep their speed, then brake over the last second. */
    private static float fallen(float t) {
        float cruise = ENTRY_S - BRAKE_S;
        if (t <= cruise) return t;
        float braking = Math.min(t, ENTRY_S) - cruise;
        return cruise + braking - braking * braking / (2 * BRAKE_S);
    }

    /** Time since the Home came back; from ENTRY_S on, every frame is the same. */
    @Override public boolean moves() { return true; }

    @Override public void draw(Canvas canvas, double time, float scroll, float alpha) {
        float t = (float) Math.min(time, ENTRY_S);
        drawImage(canvas, sky, 0, 0, alpha);
        drawLayer(canvas, celestial, t, scroll, alpha * eased(clamp(2 * t / ENTRY_S)));
        drawLayer(canvas, distant, t, scroll, alpha);
        drawLayer(canvas, main, t, scroll, alpha);
        drawLayer(canvas, fog, t, scroll, alpha * eased(clamp(t / ENTRY_S)));
        if (falling) drawSheets(canvas, fallen(t), eased(clamp((t - CLOUD_DELAY_S) / (ENTRY_S - CLOUD_DELAY_S))) * alpha);
    }
}
