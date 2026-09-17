package fr.xeroxpcl.printservice;

import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.print.PrintAttributes;
import android.print.PrintJobId;
import android.print.PrintJobInfo;
import android.printservice.PrintDocument;
import android.printservice.PrintJob;
import android.printservice.PrintService;
import android.printservice.PrinterDiscoverySession;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service d'impression Android : reçoit le PDF produit par n'importe quelle application,
 * le convertit en PCL5 monochrome et l'envoie à l'imprimante sur le port 9100.
 */
public class PclPrintService extends PrintService {

    static final String TAG = "XeroxPclPrint";
    static final String PRINTER_LOCAL_ID = "xerox-pcl-raw";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<PrintJobId, Task> tasks = new HashMap<>();

    @Override
    protected PrinterDiscoverySession onCreatePrinterDiscoverySession() {
        return new PclDiscoverySession(this);
    }

    @Override
    protected void onPrintJobQueued(PrintJob printJob) {
        if (printJob.isQueued()) {
            printJob.start();
        }
        if (!printJob.isStarted()) return;

        PrintJobInfo info = printJob.getInfo();
        PrintDocument document = printJob.getDocument();
        ParcelFileDescriptor data = document != null ? document.getData() : null;
        if (data == null) {
            printJob.fail("Document introuvable");
            return;
        }

        Task task = new Task(printJob, info, data);
        tasks.put(printJob.getId(), task);
        executor.execute(task);
    }

    @Override
    protected void onRequestCancelPrintJob(PrintJob printJob) {
        Task task = tasks.remove(printJob.getId());
        if (task != null) task.cancel();
        if (printJob.isQueued() || printJob.isStarted()) {
            printJob.cancel();
        }
    }

    @Override
    public void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }

    // ------------------------------------------------------------------------

    private final class Task implements Runnable {
        private final PrintJob job;
        private final ParcelFileDescriptor data;
        private final String label;
        private final int copies;
        private final Paper paper;
        private final int dpi;
        private final int duplex;
        private final String ip;
        private final int port;
        private final boolean dither;
        private final int lighten;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final RawSender sender = new RawSender();

        Task(PrintJob job, PrintJobInfo info, ParcelFileDescriptor data) {
            this.job = job;
            this.data = data;
            this.label = info.getLabel();
            this.copies = Math.max(1, info.getCopies());

            PrintAttributes attrs = info.getAttributes();
            PrintAttributes.MediaSize media = attrs.getMediaSize();
            this.paper = Paper.fromAndroidId(media != null ? media.getId() : null);

            PrintAttributes.Resolution res = attrs.getResolution();
            this.dpi = (res != null && res.getHorizontalDpi() <= 300) ? 300 : 600;

            switch (attrs.getDuplexMode()) {
                case PrintAttributes.DUPLEX_MODE_LONG_EDGE:
                    this.duplex = PclEncoder.DUPLEX_LONG_EDGE;
                    break;
                case PrintAttributes.DUPLEX_MODE_SHORT_EDGE:
                    this.duplex = PclEncoder.DUPLEX_SHORT_EDGE;
                    break;
                default:
                    this.duplex = PclEncoder.DUPLEX_NONE;
            }

            this.ip = Prefs.ip(PclPrintService.this);
            this.port = Prefs.port(PclPrintService.this);
            this.dither = Prefs.dither(PclPrintService.this);
            this.lighten = Prefs.lighten(PclPrintService.this);
        }

        void cancel() {
            cancelled.set(true);
            sender.abort();
        }

        @Override
        public void run() {
            File pdf = null;
            File body = null;
            String error = null;
            try {
                File dir = getCacheDir();
                pdf = File.createTempFile("job", ".pdf", dir);
                body = File.createTempFile("job", ".pcl", dir);

                // 1. Copie locale du PDF (PdfRenderer exige un fichier à accès direct).
                try (InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(data);
                     OutputStream out = new FileOutputStream(pdf)) {
                    byte[] buf = new byte[32 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                }

                // 2. Conversion PDF -> PCL dans un fichier temporaire.
                PageSource source = new PdfPageSource(pdf);
                try (OutputStream out = new BufferedOutputStream(new FileOutputStream(body), 64 * 1024)) {
                    JobRenderer.render(source, paper, dpi, dither, lighten, out, cancelled,
                            (index, count) -> status("Préparation de la page " + (index + 1) + "/" + count,
                                    0.8f * index / Math.max(1, count)));
                } finally {
                    source.close();
                }

                // 3. Envoi à l'imprimante.
                status("Envoi à l'imprimante…", 0.85f);
                sender.send(ip, port,
                        PclEncoder.jobHeader(label, paper, dpi, duplex),
                        PclEncoder.copyPrefix(duplex),
                        body, copies,
                        PclEncoder.jobFooter(),
                        cancelled);
            } catch (Throwable t) {
                Log.e(TAG, "Echec de l'impression", t);
                error = describe(t, ip, port);
            } finally {
                try { data.close(); } catch (IOException ignored) { }
                if (pdf != null) pdf.delete();
                if (body != null) body.delete();
            }

            final String result = error;
            main.post(() -> {
                tasks.remove(job.getId());
                if (cancelled.get()) return;      // déjà annulé côté Android
                try {
                    if (result == null) job.complete();
                    else job.fail(result);
                } catch (RuntimeException e) {
                    Log.w(TAG, "Impossible de mettre à jour l'état du travail", e);
                }
            });
        }

        private void status(final String text, final float progress) {
            main.post(() -> {
                if (cancelled.get()) return;
                try {
                    job.setStatus(text);
                    job.setProgress(progress);
                } catch (RuntimeException ignored) {
                    // Le travail n'est plus dans l'état "en cours" : sans importance.
                }
            });
        }
    }

    static String describe(Throwable t, String ip, int port) {
        if (t instanceof SocketTimeoutException || t instanceof ConnectException
                || t instanceof NoRouteToHostException) {
            return "Imprimante injoignable (" + ip + ":" + port + "). Est-elle allumée et sur le même Wi-Fi ?";
        }
        if (t instanceof UnknownHostException) {
            return "Adresse d'imprimante invalide : " + ip;
        }
        if (t instanceof SecurityException) {
            return "PDF protégé par mot de passe : impression impossible";
        }
        if (t instanceof OutOfMemoryError) {
            return "Mémoire insuffisante : réessayez en 300 dpi";
        }
        String m = t.getMessage();
        return (m != null && !m.isEmpty()) ? m : t.getClass().getSimpleName();
    }
}
