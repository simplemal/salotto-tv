package it.simplemal.firehome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** The switch of an on/off row. Light on a dark row, dark when the row has the focus. */
final class ToggleView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF track = new RectF();
    private final boolean on;
    private boolean focused;

    ToggleView(Context context, boolean on) {
        super(context);
        this.on = on;
    }

    void setRowFocused(boolean value) {
        focused = value;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        float height = getHeight();
        float width = getWidth();
        track.set(0, 0, width, height);
        int strong = focused ? Color.BLACK : Color.WHITE;
        int weak = focused ? Color.argb(46, 0, 0, 0) : Color.argb(38, 0, 0, 0);
        paint.setColor(on ? strong : weak);
        canvas.drawRoundRect(track, height / 2, height / 2, paint);
        float inset = height * 0.14f;
        float radius = height / 2 - inset;
        paint.setColor(on ? (focused ? Color.WHITE : Color.BLACK) : strong);
        canvas.drawCircle(on ? width - inset - radius : inset + radius, height / 2, radius, paint);
    }
}
