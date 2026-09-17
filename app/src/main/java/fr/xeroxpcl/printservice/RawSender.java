package fr.xeroxpcl.printservice;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Envoi "brut" (RAW / JetDirect, port 9100) d'un travail à l'imprimante. */
final class RawSender {

    private static final int CONNECT_TIMEOUT_MS = 8000;

    private volatile Socket socket;

    /** Interrompt un envoi en cours (appelé depuis un autre thread). */
    void abort() {
        Socket s = socket;
        if (s != null) {
            try { s.close(); } catch (IOException ignored) { }
        }
    }

    /**
     * Envoie : header, puis (copyPrefix + body) pour chaque exemplaire, puis footer.
     * body peut être null (page de test texte : tout est dans header).
     */
    void send(String host, int port, byte[] header, byte[] copyPrefix, File body, int copies,
              byte[] footer, AtomicBoolean cancelled) throws IOException {
        Socket s = new Socket();
        socket = s;
        try {
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            s.setTcpNoDelay(true);
            OutputStream out = new BufferedOutputStream(s.getOutputStream(), 32 * 1024);
            out.write(header);
            if (body != null) {
                byte[] buf = new byte[32 * 1024];
                for (int c = 0; c < Math.max(1, copies); c++) {
                    out.write(copyPrefix);
                    try (InputStream in = new FileInputStream(body)) {
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            if (cancelled.get()) throw new IOException("Impression annulée");
                            out.write(buf, 0, n);
                        }
                    }
                }
            }
            out.write(footer);
            out.flush();

            // Fermeture propre : on signale la fin d'envoi puis on laisse l'imprimante finir de lire.
            // (Fermer brutalement avec des données non lues peut faire perdre la fin du travail.)
            s.shutdownOutput();
            s.setSoTimeout(3000);
            InputStream back = s.getInputStream();
            byte[] junk = new byte[1024];
            long deadline = System.currentTimeMillis() + 15000;
            try {
                while (System.currentTimeMillis() < deadline && back.read(junk) >= 0) { /* on ignore */ }
            } catch (SocketTimeoutException ignored) {
                // L'imprimante garde la connexion ouverte : ce n'est pas une erreur.
            } catch (IOException ignored) {
                // Connexion fermée par l'imprimante après réception : normal.
            }
        } finally {
            socket = null;
            try { s.close(); } catch (IOException ignored) { }
        }
    }
}
