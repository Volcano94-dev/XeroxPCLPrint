package fr.xeroxpcl.printservice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Génère un flux PCL5e "raster" monochrome (1 bit par pixel), compression mode 2 (PackBits).
 * Classe volontairement sans aucune dépendance Android pour pouvoir être testée sur PC.
 *
 * Un travail complet envoyé à l'imprimante a la forme :
 *   jobHeader + ( copyPrefix + pages )  x nombre de copies + jobFooter
 */
final class PclEncoder implements Halftoner.RowSink {

    static final int DUPLEX_NONE = 0;
    static final int DUPLEX_LONG_EDGE = 1;
    static final int DUPLEX_SHORT_EDGE = 2;

    private static final String UEL = "\u001B%-12345X";

    private final OutputStream out;
    private final Paper paper;
    private final int dpi;
    private final int maxRows;

    private boolean rasterStarted;
    private int pendingBlankRows;
    private int rowsSeen;
    private byte[] packed = new byte[0];

    PclEncoder(OutputStream out, Paper paper, int dpi) {
        this.out = out;
        this.paper = paper;
        this.dpi = dpi;
        // On n'envoie rien dans la zone non imprimable du bas de page.
        this.maxRows = paper.heightPx(dpi) - paper.unprintableRows(dpi);
    }

    // ------------------------------------------------------------------ travail

    static byte[] jobHeader(String jobName, Paper paper, int dpi, int duplex) {
        ByteArrayOutputStream b = new ByteArrayOutputStream(256);
        ascii(b, UEL);
        ascii(b, "@PJL JOB NAME=\"" + sanitize(jobName) + "\"\r\n");
        ascii(b, "@PJL SET RESOLUTION=" + dpi + "\r\n");
        ascii(b, "@PJL ENTER LANGUAGE=PCL\r\n");
        ascii(b, "\u001BE");                          // réinitialisation
        ascii(b, "\u001B&l" + paper.pclCode + "A");   // format du papier
        ascii(b, "\u001B&l0O");                       // portrait
        ascii(b, "\u001B&l0E");                       // marge haute = 0
        ascii(b, "\u001B&l0L");                       // pas de saut de perforation
        ascii(b, "\u001B&l" + duplex + "S");          // recto / recto-verso
        ascii(b, "\u001B&l1X");                       // 1 exemplaire (les copies sont gérées par répétition)
        return b.toByteArray();
    }

    /** À envoyer avant chaque exemplaire : en recto-verso, force le départ sur un recto. */
    static byte[] copyPrefix(int duplex) {
        if (duplex == DUPLEX_NONE) return new byte[0];
        return "\u001B&a1G".getBytes(StandardCharsets.US_ASCII);
    }

    static byte[] jobFooter() {
        ByteArrayOutputStream b = new ByteArrayOutputStream(64);
        ascii(b, "\u001BE");
        ascii(b, UEL);
        ascii(b, "@PJL EOJ\r\n");
        ascii(b, UEL);
        return b.toByteArray();
    }

    // -------------------------------------------------------------------- pages

    void beginPage() {
        rasterStarted = false;
        pendingBlankRows = 0;
        rowsSeen = 0;
    }

    /** Reçoit une ligne de pixels (1 = noir, bit de poids fort à gauche). */
    @Override
    public void row(byte[] bits, int nbytes) throws IOException {
        rowsSeen++;
        if (rowsSeen > maxRows) return;

        int len = nbytes;
        while (len > 0 && bits[len - 1] == 0) len--;
        if (len == 0) {
            pendingBlankRows++;
            return;
        }
        if (!rasterStarted) startRaster();
        flushBlankRows();

        int need = len * 2 + 16;
        if (packed.length < need) packed = new byte[need];
        int n = packBits(bits, len, packed);
        ascii(out, "\u001B*b" + n + "W");
        out.write(packed, 0, n);
    }

    void endPage() throws IOException {
        if (!rasterStarted) {
            // Page entièrement blanche : on envoie quand même une ligne vide pour que la
            // feuille soit bien éjectée (important pour garder l'ordre en recto-verso).
            startRaster();
            ascii(out, "\u001B*b1W");
            out.write(0);
        }
        ascii(out, "\u001B*rC");   // fin du raster
        out.write(0x0C);          // saut de page
        rasterStarted = false;
        pendingBlankRows = 0;
    }

    private void startRaster() throws IOException {
        ascii(out, "\u001B*p0x0Y");                          // curseur en haut à gauche de la page logique
        ascii(out, "\u001B*t" + dpi + "R");                  // résolution du raster
        ascii(out, "\u001B*r0F");                            // raster dans le sens de la page
        ascii(out, "\u001B*r" + paper.logicalWidthPx(dpi) + "S"); // largeur du raster
        ascii(out, "\u001B*r1A");                            // début du raster à la position du curseur
        ascii(out, "\u001B*b2M");                            // compression PackBits
        rasterStarted = true;
    }

    private void flushBlankRows() throws IOException {
        if (pendingBlankRows == 0) return;
        byte[] blank = "\u001B*b0W".getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < pendingBlankRows; i++) out.write(blank);
        pendingBlankRows = 0;
    }

    // --------------------------------------------------------------- utilitaires

    /** Compression PackBits (TIFF) = mode 2 du PCL. Retourne le nombre d'octets écrits dans dst. */
    static int packBits(byte[] src, int len, byte[] dst) {
        int i = 0, o = 0;
        while (i < len) {
            int run = 1;
            while (i + run < len && run < 128 && src[i + run] == src[i]) run++;
            if (run >= 2) {
                dst[o++] = (byte) (1 - run);
                dst[o++] = src[i];
                i += run;
            } else {
                int start = i;
                i++;
                while (i < len && (i - start) < 128) {
                    if (i + 1 < len && src[i] == src[i + 1]) break;
                    i++;
                }
                int n = i - start;
                dst[o++] = (byte) (n - 1);
                System.arraycopy(src, start, dst, o, n);
                o += n;
            }
        }
        return o;
    }

    private static String sanitize(String s) {
        if (s == null) return "Android";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length() && sb.length() < 60; i++) {
            char c = s.charAt(i);
            if (c >= 0x20 && c < 0x7F && c != '"') sb.append(c);
            else sb.append('_');
        }
        return sb.length() == 0 ? "Android" : sb.toString();
    }

    private static void ascii(OutputStream o, String s) throws IOException {
        o.write(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static void ascii(ByteArrayOutputStream o, String s) {
        byte[] b = s.getBytes(StandardCharsets.ISO_8859_1);
        o.write(b, 0, b.length);
    }
}
