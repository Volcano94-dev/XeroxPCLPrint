package fr.xeroxpcl.printservice;

import java.io.IOException;
import java.util.Arrays;

/**
 * Convertit des pixels ARGB en lignes 1 bit (1 = noir).
 *
 * - mode tramage : trame "à points groupés" à 45 degrés, comme en imprimerie. Sur un laser, les
 *   pixels isolés s'étalent et bouchent les gris (une diffusion d'erreur type Floyd-Steinberg
 *   sort presque noire) ; des points groupés, eux, se reproduisent fidèlement.
 * - mode seuil   : noir/blanc pur (les gris clairs disparaissent).
 *
 * Sans dépendance Android, pour être testable sur PC.
 */
final class Halftoner {

    interface RowSink {
        void row(byte[] bits, int nbytes) throws IOException;
    }

    /** Exposants de la courbe d'éclaircissement (0 = aucun ... 3 = fort). */
    private static final double[] LIGHTEN = {1.0, 0.8, 0.65, 0.5};

    private final int width;
    private final int cropLeft;
    private final int outWidth;
    private final boolean dither;
    private final byte[] rowBits;
    private final int[] darkness = new int[256];   // luminance -> noirceur corrigée (0..255)
    private final int period;
    private final int[][] screen;                  // seuils de la trame, en noirceur
    private int rowIndex;

    /**
     * @param width    largeur des bandes reçues, en pixels (feuille entière)
     * @param cropLeft colonnes à ignorer à gauche (décalage de la page logique PCL)
     * @param outWidth colonnes à produire
     * @param dpi      résolution (fixe la finesse de la trame)
     * @param lighten  niveau d'éclaircissement des gris, de 0 à 3
     */
    Halftoner(int width, int cropLeft, int outWidth, boolean dither, int dpi, int lighten) {
        this.width = width;
        this.cropLeft = Math.max(0, cropLeft);
        this.outWidth = Math.min(outWidth, width - this.cropLeft);
        this.dither = dither;
        this.rowBits = new byte[(this.outWidth + 7) / 8];

        double exp = LIGHTEN[Math.max(0, Math.min(LIGHTEN.length - 1, lighten))];
        for (int i = 0; i < 256; i++) {
            int lum = dither ? (int) Math.round(255.0 * Math.pow(i / 255.0, exp)) : i;
            darkness[i] = lum >= 250 ? 0 : 255 - lum;   // le quasi-blanc reste blanc (pas de mouchetures)
        }

        // 600 dpi : période 10 -> trame de 85 lignes/pouce ; 300 dpi : période 6 -> 71 lignes/pouce.
        this.period = dpi >= 600 ? 10 : 6;
        this.screen = buildScreen(period);
    }

    /**
     * Trame à 45 degrés : deux points par période, qui grossissent autour de leur centre.
     * L'ordre de noircissement suit la fonction cos(2.pi.x/N) * cos(2.pi.y/N).
     */
    private static int[][] buildScreen(int n) {
        final int count = n * n;
        final double[] spot = new double[count];
        Integer[] order = new Integer[count];
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                double fx = 2 * Math.PI * (x + 0.5) / n;
                double fy = 2 * Math.PI * (y + 0.5) / n;
                // Petit terme de départage pour que l'ordre soit stable et bien réparti.
                spot[y * n + x] = Math.cos(fx) * Math.cos(fy) + 1e-6 * ((x * 7 + y * 13) % 17);
                order[y * n + x] = y * n + x;
            }
        }
        Arrays.sort(order, (a, b) -> Double.compare(spot[b], spot[a]));   // les centres de points d'abord
        int[][] t = new int[n][n];
        for (int rank = 0; rank < count; rank++) {
            int idx = order[rank];
            t[idx / n][idx % n] = (int) ((rank + 0.5) * 255.0 / count);
        }
        return t;
    }

    /** À appeler au début de chaque page. */
    void reset() {
        rowIndex = 0;
    }

    void processBand(int[] argb, int rows, RowSink sink) throws IOException {
        final int w = width;
        final int x0 = cropLeft;
        final int x1 = cropLeft + outWidth;
        final int n = period;
        for (int y = 0; y < rows; y++) {
            final int base = y * w;
            final int[] thresholds = screen[rowIndex % n];
            Arrays.fill(rowBits, (byte) 0);
            boolean any = false;
            int sx = x0 % n;
            for (int x = x0; x < x1; x++) {
                int d = darkness[luminance(argb[base + x])];
                boolean black = dither ? d > thresholds[sx] : d > 127;
                if (black) {
                    int ox = x - x0;
                    rowBits[ox >> 3] |= (byte) (0x80 >> (ox & 7));
                    any = true;
                }
                if (++sx == n) sx = 0;
            }
            rowIndex++;
            sink.row(rowBits, any ? rowBits.length : 0);
        }
    }

    private static int luminance(int argb) {
        int a = argb >>> 24;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int lum = (r * 77 + g * 151 + b * 28) >> 8;
        if (a == 255) return lum;
        return (lum * a + 255 * (255 - a)) / 255;     // pixel (semi-)transparent : composé sur blanc
    }
}
