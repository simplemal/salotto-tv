package it.simplemal.firehome;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.MetricAffectingSpan;

/** The name is always set in Space Grotesk, even in the middle of a sentence. */
final class Brand {
    static final String NAME = "Salotto";

    private static Typeface face;

    private Brand() {}

    private static final class Face extends MetricAffectingSpan {
        @Override public void updateDrawState(TextPaint paint) { paint.setTypeface(face); }
        @Override public void updateMeasureState(TextPaint paint) { paint.setTypeface(face); }
    }

    static Typeface face(Context context) {
        if (face == null) face = context.getResources().getFont(R.font.space_grotesk);
        return face;
    }

    static CharSequence mark(Context context, String text) {
        int at = text == null ? -1 : text.indexOf(NAME);
        if (at < 0) return text;
        face(context);
        Spannable marked = new SpannableString(text);
        for (; at >= 0; at = text.indexOf(NAME, at + 1)) {
            marked.setSpan(new Face(), at, at + NAME.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return marked;
    }
}
