package fr.xeroxpcl.printservice;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/** Page de test graphique : vérifie la géométrie (cadre à 10 mm) et le tramage (dégradé). */
final class TestPageSource implements PageSource {

    private final int dpi;
    private final String target;
    private int w, h;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    TestPageSource(int dpi, String target) {
        this.dpi = dpi;
        this.target = target;
    }

    @Override public int pageCount() { return 1; }

    @Override public void openPage(int index, int sheetW, int sheetH) {
        w = sheetW;
        h = sheetH;
    }

    @Override public void renderBand(Bitmap band, int y0) {
        Canvas c = new Canvas(band);
        c.drawColor(Color.WHITE);
        c.translate(0f, -y0);
        draw(c);
    }

    private void draw(Canvas c) {
        float mm = dpi / 25.4f;
        float pt = dpi / 72f;

        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, 0.3f * mm));
        c.drawRect(10 * mm, 10 * mm, w - 10 * mm, h - 10 * mm, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(18 * pt);
        c.drawText("Xerox PCL Print - page de test", 20 * mm, 30 * mm, paint);

        paint.setTextSize(11 * pt);
        c.drawText("Imprimante : " + target + "   -   " + dpi + " dpi", 20 * mm, 40 * mm, paint);
        c.drawText("Le cadre doit se trouver a 10 mm de chaque bord de la feuille.", 20 * mm, 48 * mm, paint);
        c.drawText("Le trait ci-dessous doit mesurer exactement 100 mm.", 20 * mm, 56 * mm, paint);

        paint.setStrokeWidth(Math.max(2f, 0.3f * mm));
        c.drawLine(20 * mm, 62 * mm, 120 * mm, 62 * mm, paint);
        c.drawLine(20 * mm, 60 * mm, 20 * mm, 64 * mm, paint);
        c.drawLine(120 * mm, 60 * mm, 120 * mm, 64 * mm, paint);

        float[] sizes = {6f, 8f, 10f, 12f};
        float y = 75 * mm;
        for (float s : sizes) {
            paint.setTextSize(s * pt);
            c.drawText("Texte en corps " + (int) s + " : Portez ce vieux whisky au juge blond qui fume. 0123456789",
                    20 * mm, y, paint);
            y += (s * 0.6f + 3f) * mm;
        }

        paint.setTextSize(10 * pt);
        paint.setColor(0xFF999999);
        c.drawText("Texte gris clair : il doit rester lisible si le tramage est active.", 20 * mm, y + 4 * mm, paint);

        // Dégradé du noir au blanc
        float gx = 20 * mm, gy = 115 * mm, gw = 170 * mm, gh = 25 * mm;
        int steps = (int) gw;
        for (int i = 0; i < steps; i++) {
            int v = 255 * i / Math.max(1, steps - 1);
            paint.setColor(Color.rgb(v, v, v));
            c.drawRect(gx + i, gy, gx + i + 1, gy + gh, paint);
        }

        // Pavés de gris
        int[] grays = {0, 64, 128, 192, 230};
        for (int i = 0; i < grays.length; i++) {
            paint.setColor(Color.rgb(grays[i], grays[i], grays[i]));
            c.drawRect((20 + i * 34) * mm, 150 * mm, (20 + i * 34 + 30) * mm, 180 * mm, paint);
        }

        paint.setColor(Color.BLACK);
        paint.setTextSize(10 * pt);
        c.drawText("Si cette page est correcte, le plug-in fonctionne.", 20 * mm, 195 * mm, paint);
    }

    @Override public void closePage() { }

    @Override public void close() { }
}
