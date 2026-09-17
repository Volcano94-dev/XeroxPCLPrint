package fr.xeroxpcl.printservice;

import android.content.Context;
import android.content.SharedPreferences;

/** Réglages du plug-in (adresse de l'imprimante, etc.). */
final class Prefs {
    static final String DEFAULT_IP = "10.0.0.2";
    static final int DEFAULT_PORT = 9100;
    static final int DEFAULT_LIGHTEN = 2;   // "Moyen" : calibré sur une WorkCentre 3225
    static final String DEFAULT_NAME = "Xerox WorkCentre 3225 (PCL)";

    private static final String FILE = "settings";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static String ip(Context c)       { return sp(c).getString("ip", DEFAULT_IP); }
    static int port(Context c)        { return sp(c).getInt("port", DEFAULT_PORT); }
    static String name(Context c)     { return sp(c).getString("name", DEFAULT_NAME); }
    static boolean dither(Context c)  { return sp(c).getBoolean("dither", true); }
    /** Éclaircissement des gris : 0 = aucun, 1 = léger, 2 = moyen, 3 = fort. */
    static int lighten(Context c)     { return sp(c).getInt("lighten", DEFAULT_LIGHTEN); }

    static void save(Context c, String name, String ip, int port, boolean dither, int lighten) {
        sp(c).edit()
                .putString("name", name)
                .putString("ip", ip)
                .putInt("port", port)
                .putBoolean("dither", dither)
                .putInt("lighten", lighten)
                .apply();
    }
}
