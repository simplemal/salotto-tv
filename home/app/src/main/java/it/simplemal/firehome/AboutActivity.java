package it.simplemal.firehome;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Text to read and nothing to press, like the regulatory pages of a device: the arrows scroll it. */
public final class AboutActivity extends CardActivity {
    private static final int WIDTH = 720;
    private static final int LOGO = 72;
    private static final int STEP = 120;
    private static final int BODY_SP = 13;
    private static final int SMALL_SP = 12;
    private static final int SOFT = Color.rgb(190, 190, 190);

    private LinearLayout text;
    private ScrollView scroll;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout root = new FrameLayout(this);
        Look.show(this, root::setBackground);
        text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(0, dp(40), 0, dp(48));
        write();
        scroll = new ScrollView(this);
        scroll.setFocusable(true);
        scroll.setDefaultFocusHighlightEnabled(false);
        scroll.setScrollbarFadingEnabled(false);
        scroll.setVerticalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(40));
        scroll.addView(text, new FrameLayout.LayoutParams(dp(WIDTH), -2, Gravity.CENTER_HORIZONTAL));
        root.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        scroll.requestFocus();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code != KeyEvent.KEYCODE_DPAD_DOWN && code != KeyEvent.KEYCODE_DPAD_UP) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            scroll.smoothScrollBy(0, code == KeyEvent.KEYCODE_DPAD_DOWN ? dp(STEP) : -dp(STEP));
        }
        return true;
    }

    private void write() {
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_salotto);
        text.addView(logo, new LinearLayout.LayoutParams(dp(LOGO), dp(LOGO)));
        TextView name = label("Salotto", 26);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.addView(name);
        paragraph("Versione " + BuildVersion.full(this));
        paragraph("Launcher per Fire TV, di simplemal.");
        paragraph("https://github.com/" + Fire.UPDATES_REPO);

        section("Progetto indipendente");
        paragraph("Salotto non è affiliato, sponsorizzato né approvato da Apple, Amazon o dagli altri marchi citati.");

        section("Obiettivi e funzionalità");
        lead("Più pulito.", "La Home di Fire TV mette pubblicità, contenuti consigliati e video automatici davanti alle "
            + "tue app. Salotto mostra solo le tue app, lo sfondo, l'orario e il meteo. Niente pubblicità e niente offerte.");
        lead("Meno tracciamento.", "L'installazione spegne i pacchetti di pubblicità, telemetria e Alexa, limita "
            + "l'identificativo pubblicitario e ferma l'invio dei dati d'uso. Se un aggiornamento di Fire OS li riaccende, "
            + "Salotto te lo dice e puoi rispegnerli. Salotto si collega solo a Open-Meteo per il meteo e a GitHub per gli aggiornamenti.");
        lead("Con programmi di altri.", "FireStick Fixer porta il tasto Home su Salotto. AirPlay Server riceve da iPhone, "
            + "iPad e Mac. Aurora Store installa app Android, anche senza account.");
        lead("Solo in Salotto.", "Sfondi illustrati o fotografici che seguono il meteo e l'ora della tua città. Centro di controllo su "
            + "qualsiasi app, tenendo premuto Home: Wi-Fi, Bluetooth, timer, luminosità e filtro luce blu. Task manager "
            + "tenendo premuto Indietro. Una pagina con gli standard audio e video di Stick e TV. Dialoghi più chiari e "
            + "volume uniforme a portata di mano: sono filtri di Fire OS, raccolti in un posto solo. Aggiornamenti "
            + "firmati e controllati prima di installarli.");

        section("Licenza");
        paragraph("PolyForm Noncommercial 1.0.0. Salotto si può usare, studiare, modificare e derivare gratis per scopi "
            + "non commerciali. Per venderlo serve il permesso dell'autore.");

        section("Marchi");
        paragraph("Tutti i marchi appartengono ai rispettivi proprietari. Fire TV, Alexa, Prime Video e Amazon sono marchi "
            + "di Amazon. Android e Google Play sono marchi di Google. AirPlay, AirPods, HomePod e Apple TV sono marchi "
            + "di Apple. Netflix, Dolby, Dolby Vision, Dolby Atmos, HDMI, Bluetooth, Wi-Fi e gli altri nomi citati "
            + "appartengono ai loro proprietari. Questo progetto non è affiliato a nessuno di loro, né approvato da loro.");

        section("Software usato");
        credit("Material Icons", "Apache 2.0", "Icone delle Impostazioni", "github.com/google/material-design-icons");
        credit("Open-Meteo", "CC BY 4.0", "Dati meteo, servizio online", "open-meteo.com");
        credit("Android Open Source Project", "Apache 2.0", "Piattaforma e compilazione", "source.android.com");
        credit("Gradle e Android Gradle Plugin", "Apache 2.0", "Compilazione", "gradle.org");
        credit("Pillow", "HPND", "Riduzione delle illustrazioni meteo, solo in compilazione", "python-pillow.org");

        section("Programmi non inclusi");
        paragraph("L'installazione li scarica dai progetti originali, senza modificarli.");
        credit("FireStick Fixer", "GPL-3.0", "Porta il tasto Home su Salotto", "github.com/Hornet34/firestick-fixer");
        credit("AirPlay Server", "GPL-3.0", "Riceve da iPhone, iPad e Mac", "github.com/jqssun/android-airplay-server");
        credit("Aurora Store", "GPL-3.0", "Installa app Android, anche senza account",
            "f-droid.org/packages/com.aurora.store");
    }

    private void section(String name) {
        TextView title = label(name, 16);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(0, dp(24), 0, dp(2));
        text.addView(title);
    }

    private void lead(String first, String words) {
        SpannableString whole = new SpannableString(first + " " + words);
        whole.setSpan(new ForegroundColorSpan(Color.WHITE), 0, first.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        paragraph(whole);
    }

    private void paragraph(CharSequence words) {
        TextView view = label("", BODY_SP);
        view.setText(words);
        view.setTextColor(SOFT);
        view.setLineSpacing(0, 1.2f);
        view.setPadding(0, dp(4), 0, 0);
        text.addView(view);
    }

    private void credit(String name, String licence, String use, String address) {
        LinearLayout line = new LinearLayout(this);
        line.setPadding(0, dp(10), 0, 0);
        line.addView(label(name, BODY_SP), new LinearLayout.LayoutParams(0, -2, 1));
        TextView terms = label(licence, BODY_SP);
        terms.setTextColor(SOFT);
        line.addView(terms);
        text.addView(line);
        TextView detail = label(use + "  ·  " + address, SMALL_SP);
        detail.setTextColor(SOFT);
        text.addView(detail);
    }
}
