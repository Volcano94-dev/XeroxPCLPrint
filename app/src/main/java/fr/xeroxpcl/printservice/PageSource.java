package fr.xeroxpcl.printservice;

import android.graphics.Bitmap;

import java.io.IOException;

/** Source de pages à imprimer, rendues par bandes horizontales pour limiter la mémoire. */
interface PageSource {
    int pageCount();

    /** Prépare la page : elle sera rendue sur une feuille de sheetW x sheetH pixels. */
    void openPage(int index, int sheetW, int sheetH) throws IOException;

    /** Dessine dans band la portion de la feuille qui commence à la ligne y0. */
    void renderBand(Bitmap band, int y0);

    void closePage();

    void close();
}
