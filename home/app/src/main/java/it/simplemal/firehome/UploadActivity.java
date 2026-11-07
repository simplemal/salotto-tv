package it.simplemal.firehome;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import io.nayuki.qrcodegen.QrCode;
import java.io.File;
import java.io.IOException;

/**
 * Carica: a QR code the phone reads to open Salotto's page, where it sends its photos. They go
 * straight into Personale; the page stays until Close, and with it the little web server.
 */
public final class UploadActivity extends CardActivity {
    private static final int WIDTH = 720;
    private static final int TITLE_SP = 22;
    private static final int TEXT_SP = 15;
    private static final int SMALL_SP = 13;
    private static final int CODE_SIZE = 230;
    /** The white margin a reader needs around the squares, in squares. */
    private static final int QUIET = 4;
    private static final int GAP = 18;
    private static final int BUTTON_SP = 16;
    private static final int BUTTON_WIDTH = 170;
    private static final float SOFT = 0.7f;

    private UploadServer server;
    private TextView count;
    private int received;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        root.setBackground(Look.plainBackdrop());
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.addView(centered(label(getString(R.string.upload_title), TITLE_SP)));
        ImageView code = new ImageView(this);
        LinearLayout.LayoutParams codePlace = new LinearLayout.LayoutParams(dp(CODE_SIZE), dp(CODE_SIZE));
        codePlace.setMargins(0, dp(GAP), 0, dp(GAP));
        page.addView(code, codePlace);
        TextView text = centered(label(getString(R.string.upload_text), TEXT_SP));
        page.addView(text, new LinearLayout.LayoutParams(dp(WIDTH), -2));
        TextView address = centered(label("", SMALL_SP));
        address.setAlpha(SOFT);
        address.setPadding(0, dp(6), 0, 0);
        page.addView(address);
        count = centered(label("", TEXT_SP));
        count.setPadding(0, dp(GAP), 0, 0);
        page.addView(count);
        TextView close = button(getString(R.string.card_close), BUTTON_SP, this::finish);
        LinearLayout.LayoutParams closePlace = new LinearLayout.LayoutParams(dp(BUTTON_WIDTH), -2);
        closePlace.topMargin = dp(GAP);
        page.addView(close, closePlace);
        root.addView(page, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        setContentView(root);
        close.requestFocus();

        try {
            server = new UploadServer(this, this::arrived);
        } catch (IOException e) {
            server = null;
        }
        String link = server == null ? null : server.address();
        if (link == null) {
            code.setVisibility(View.GONE);
            text.setText(getString(R.string.upload_no_network));
            return;
        }
        code.setImageBitmap(picture(QrCode.encodeText(link, QrCode.Ecc.MEDIUM), dp(CODE_SIZE)));
        address.setText(link);
    }

    /** Whole pixels a square, enlarged without smoothing, so the edges stay sharp for the phone. */
    private static Bitmap picture(QrCode qr, int pixels) {
        int side = qr.size + 2 * QUIET;
        Bitmap picture = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        picture.eraseColor(Color.WHITE);
        for (int y = 0; y < qr.size; y++) {
            for (int x = 0; x < qr.size; x++) {
                if (qr.getModule(x, y)) picture.setPixel(x + QUIET, y + QUIET, Color.BLACK);
            }
        }
        int scale = Math.max(1, pixels / side);
        return Bitmap.createScaledBitmap(picture, side * scale, side * scale, false);
    }

    private TextView centered(TextView view) {
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private void arrived(File file) {
        runOnUiThread(() -> {
            received++;
            count.setText(getResources().getQuantityString(R.plurals.upload_received, received, received));
            setResult(RESULT_OK);
        });
    }

    /** Away from the page, the server goes with it: it lives only while the code is on screen. */
    @Override protected void onStop() {
        super.onStop();
        if (!isFinishing()) finish();
    }

    @Override protected void onDestroy() {
        if (server != null) server.stop();
        super.onDestroy();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
