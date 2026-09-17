package fr.xeroxpcl.printservice;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.IOException;

/** Pages d'un document PDF (celui que fournit Android au service d'impression). */
final class PdfPageSource implements PageSource {

    private final ParcelFileDescriptor pfd;
    private final PdfRenderer renderer;
    private PdfRenderer.Page page;
    private final Matrix base = new Matrix();
    private final Matrix bandMatrix = new Matrix();

    PdfPageSource(File pdf) throws IOException {
        pfd = ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY);
        try {
            renderer = new PdfRenderer(pfd);
        } catch (IOException | RuntimeException e) {
            try { pfd.close(); } catch (IOException ignored) { }
            throw e;
        }
    }

    @Override
    public int pageCount() {
        return renderer.getPageCount();
    }

    @Override
    public void openPage(int index, int sheetW, int sheetH) {
        page = renderer.openPage(index);
        float pw = page.getWidth();   // en points (1/72 de pouce)
        float ph = page.getHeight();
        base.reset();
        if (pw > ph) {
            // Page paysage : on la tourne d'un quart de tour pour l'imprimer sur la feuille portrait.
            base.setRotate(90f);
            base.postTranslate(ph, 0f);
            float t = pw; pw = ph; ph = t;
        }
        float s = Math.min(sheetW / pw, sheetH / ph);
        base.postScale(s, s);
        base.postTranslate((sheetW - pw * s) / 2f, (sheetH - ph * s) / 2f);
    }

    @Override
    public void renderBand(Bitmap band, int y0) {
        band.eraseColor(Color.WHITE);
        bandMatrix.set(base);
        bandMatrix.postTranslate(0f, -y0);
        page.render(band, null, bandMatrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT);
    }

    @Override
    public void closePage() {
        if (page != null) {
            page.close();
            page = null;
        }
    }

    @Override
    public void close() {
        closePage();
        try { renderer.close(); } catch (RuntimeException ignored) { }
        try { pfd.close(); } catch (IOException ignored) { }
    }
}
