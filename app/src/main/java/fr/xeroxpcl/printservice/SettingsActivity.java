package fr.xeroxpcl.printservice;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/** Écran de réglages : adresse de l'imprimante et pages de test. */
public class SettingsActivity extends Activity {

    private EditText nameField;
    private EditText ipField;
    private EditText portField;
    private CheckBox ditherBox;
    private Spinner lightenSpinner;
    private TextView statusView;
    private Button[] buttons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int pad = dp(16);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);

        root.addView(label("Nom affiché dans le menu Imprimer"));
        nameField = field(Prefs.name(this), InputType.TYPE_CLASS_TEXT);
        root.addView(nameField);

        root.addView(label("Adresse IP de l'imprimante"));
        ipField = field(Prefs.ip(this), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(ipField);

        root.addView(label("Port (RAW / JetDirect)"));
        portField = field(String.valueOf(Prefs.port(this)), InputType.TYPE_CLASS_NUMBER);
        root.addView(portField);

        ditherBox = new CheckBox(this);
        ditherBox.setText("Tramage des gris et des photos (recommandé)");
        ditherBox.setChecked(Prefs.dither(this));
        root.addView(ditherBox);

        root.addView(label("Éclaircissement des gris et des photos"));
        lightenSpinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,
                new String[]{"Aucun", "Léger", "Moyen", "Fort"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        lightenSpinner.setAdapter(adapter);
        lightenSpinner.setSelection(Math.max(0, Math.min(3, Prefs.lighten(this))));
        root.addView(lightenSpinner);

        Button save = button("Enregistrer", v -> {
            if (saveFields()) setStatus("Réglages enregistrés.");
        });
        Button testText = button("Page de test : texte simple", v -> runTest(false));
        Button testGraphic = button("Page de test : graphique (600 dpi)", v -> runTest(true));
        Button openSettings = button("Ouvrir les réglages d'impression Android", v -> {
            try {
                startActivity(new Intent(Settings.ACTION_PRINT_SETTINGS));
            } catch (RuntimeException e) {
                setStatus("Ouvrez : Paramètres > Appareils connectés > Plus de paramètres de connexion > Impression");
            }
        });
        buttons = new Button[]{save, testText, testGraphic, openSettings};
        for (Button b : buttons) root.addView(b);

        statusView = new TextView(this);
        statusView.setPadding(0, pad, 0, pad);
        statusView.setTextIsSelectable(true);
        statusView.setText("Activez ensuite « " + getString(R.string.app_name)
                + " » dans les réglages d'impression Android, puis imprimez depuis n'importe quelle application.");
        root.addView(statusView);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private boolean saveFields() {
        String ip = ipField.getText().toString().trim();
        String name = nameField.getText().toString().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().toString().trim());
        } catch (NumberFormatException e) {
            port = -1;
        }
        if (ip.isEmpty()) {
            setStatus("Adresse IP manquante.");
            return false;
        }
        if (port < 1 || port > 65535) {
            setStatus("Port invalide (9100 en général).");
            return false;
        }
        if (name.isEmpty()) name = Prefs.DEFAULT_NAME;
        Prefs.save(this, name, ip, port, ditherBox.isChecked(), lightenSpinner.getSelectedItemPosition());
        return true;
    }

    private void runTest(final boolean graphic) {
        if (!saveFields()) return;
        final String ip = Prefs.ip(this);
        final int port = Prefs.port(this);
        final boolean dither = Prefs.dither(this);
        final int lighten = Prefs.lighten(this);
        final File cacheDir = getCacheDir();
        setBusy(true);
        setStatus(graphic ? "Préparation de la page de test graphique…" : "Envoi de la page de test…");

        new Thread(() -> {
            String result;
            File body = null;
            try {
                AtomicBoolean cancelled = new AtomicBoolean(false);
                RawSender sender = new RawSender();
                if (graphic) {
                    int dpi = 600;
                    Paper paper = Paper.A4;
                    body = File.createTempFile("test", ".pcl", cacheDir);
                    PageSource src = new TestPageSource(dpi, ip + ":" + port);
                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(body), 64 * 1024)) {
                        JobRenderer.render(src, paper, dpi, dither, lighten, out, cancelled, null);
                    }
                    long size = body.length();
                    sender.send(ip, port,
                            PclEncoder.jobHeader("Page de test", paper, dpi, PclEncoder.DUPLEX_NONE),
                            PclEncoder.copyPrefix(PclEncoder.DUPLEX_NONE),
                            body, 1, PclEncoder.jobFooter(), cancelled);
                    result = "Page de test graphique envoyée (" + (size / 1024) + " Ko). Elle doit sortir dans quelques secondes.";
                } else {
                    sender.send(ip, port, textTestPage(ip, port), new byte[0], null, 1, new byte[0], cancelled);
                    result = "Page de test texte envoyée. Elle doit sortir dans quelques secondes.";
                }
            } catch (Throwable t) {
                result = "Échec : " + PclPrintService.describe(t, ip, port);
            } finally {
                if (body != null) body.delete();
            }
            final String message = result;
            runOnUiThread(() -> {
                setBusy(false);
                setStatus(message);
            });
        }, "xeroxpcl-test").start();
    }

    /** Page de test minimale en PCL "texte" : valide le réseau et le langage PCL, sans rendu graphique. */
    private static byte[] textTestPage(String ip, int port) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        String esc = "\u001B";
        String s = esc + "%-12345X@PJL ENTER LANGUAGE=PCL\r\n"
                + esc + "E" + esc + "&l26A" + esc + "&l0O"
                + "\r\n\r\n\r\n"
                + "        Xerox PCL Print - page de test (texte)\r\n\r\n"
                + "        Connexion RAW OK vers " + ip + ":" + port + "\r\n"
                + "        Le langage PCL5 est bien interprete par l'imprimante.\r\n"
                + "\f"
                + esc + "E" + esc + "%-12345X";
        byte[] bytes = s.getBytes(StandardCharsets.US_ASCII);
        b.write(bytes, 0, bytes.length);
        return b.toByteArray();
    }

    // ------------------------------------------------------------- interface

    private void setBusy(boolean busy) {
        for (Button b : buttons) b.setEnabled(!busy);
    }

    private void setStatus(String text) {
        statusView.setText(text);
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setPadding(0, dp(12), 0, 0);
        return t;
    }

    private EditText field(String value, int inputType) {
        EditText e = new EditText(this);
        e.setInputType(inputType);
        e.setSingleLine(true);
        e.setText(value);
        e.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }

    private Button button(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        b.setLayoutParams(lp);
        return b;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }
}
