package fr.xeroxpcl.printservice;

import android.graphics.Bitmap;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/** Transforme toutes les pages d'une source en flux PCL (corps du travail, sans en-tête ni pied). */
final class JobRenderer {

    interface Progress {
        void onPage(int pageIndex, int pageCount);
    }

    private JobRenderer() {}

    static int render(PageSource src, Paper paper, int dpi, boolean dither, int lighten,
                      OutputStream body, AtomicBoolean cancelled, Progress progress) throws IOException {
        final int sheetW = paper.widthPx(dpi);
        final int sheetH = paper.heightPx(dpi);
        // Bandes d'environ 2,5 millions de pixels (~10 Mo) : une page A4 à 600 dpi en ferait 140 Mo d'un bloc.
        final int bandH = Math.max(64, Math.min(sheetH, 2_500_000 / sheetW));

        Bitmap band = Bitmap.createBitmap(sheetW, bandH, Bitmap.Config.ARGB_8888);
        try {
            int[] pixels = new int[sheetW * bandH];
            PclEncoder enc = new PclEncoder(body, paper, dpi);
            Halftoner halftoner = new Halftoner(sheetW, paper.leftOffsetPx(dpi), paper.logicalWidthPx(dpi), dither, dpi, lighten);

            int count = src.pageCount();
            for (int p = 0; p < count; p++) {
                if (cancelled.get()) throw new IOException("Impression annulée");
                if (progress != null) progress.onPage(p, count);
                src.openPage(p, sheetW, sheetH);
                try {
                    enc.beginPage();
                    halftoner.reset();
                    for (int y0 = 0; y0 < sheetH; y0 += bandH) {
                        if (cancelled.get()) throw new IOException("Impression annulée");
                        int rows = Math.min(bandH, sheetH - y0);
                        src.renderBand(band, y0);
                        band.getPixels(pixels, 0, sheetW, 0, 0, sheetW, rows);
                        halftoner.processBand(pixels, rows, enc);
                    }
                    enc.endPage();
                } finally {
                    src.closePage();
                }
            }
            body.flush();
            return count;
        } finally {
            band.recycle();
        }
    }
}
