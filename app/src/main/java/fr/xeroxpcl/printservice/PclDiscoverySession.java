package fr.xeroxpcl.printservice;

import android.print.PrintAttributes;
import android.print.PrinterCapabilitiesInfo;
import android.print.PrinterId;
import android.print.PrinterInfo;
import android.printservice.PrinterDiscoverySession;

import java.util.Collections;
import java.util.List;

/**
 * "Découverte" des imprimantes : il n'y en a qu'une, celle dont l'adresse est saisie
 * dans les réglages. On l'annonce à Android avec ses capacités.
 */
final class PclDiscoverySession extends PrinterDiscoverySession {

    private final PclPrintService service;

    PclDiscoverySession(PclPrintService service) {
        this.service = service;
    }

    @Override
    public void onStartPrinterDiscovery(List<PrinterId> priorityList) {
        publish();
    }

    @Override
    public void onStopPrinterDiscovery() { }

    @Override
    public void onValidatePrinters(List<PrinterId> printerIds) {
        publish();
    }

    @Override
    public void onStartPrinterStateTracking(PrinterId printerId) {
        publish();
    }

    @Override
    public void onStopPrinterStateTracking(PrinterId printerId) { }

    @Override
    public void onDestroy() { }

    private void publish() {
        PrinterId id = service.generatePrinterId(PclPrintService.PRINTER_LOCAL_ID);

        // Marges minimales : on prend le cas le plus large (format Letter) pour tous les formats.
        Paper ref = Paper.LETTER;
        PrinterCapabilitiesInfo caps = new PrinterCapabilitiesInfo.Builder(id)
                .addMediaSize(PrintAttributes.MediaSize.ISO_A4, true)
                .addMediaSize(PrintAttributes.MediaSize.ISO_A5, false)
                .addMediaSize(PrintAttributes.MediaSize.NA_LETTER, false)
                .addMediaSize(PrintAttributes.MediaSize.NA_LEGAL, false)
                .addResolution(new PrintAttributes.Resolution("r600", "600 dpi", 600, 600), true)
                .addResolution(new PrintAttributes.Resolution("r300", "300 dpi", 300, 300), false)
                .setColorModes(PrintAttributes.COLOR_MODE_MONOCHROME, PrintAttributes.COLOR_MODE_MONOCHROME)
                .setDuplexModes(PrintAttributes.DUPLEX_MODE_NONE
                                | PrintAttributes.DUPLEX_MODE_LONG_EDGE
                                | PrintAttributes.DUPLEX_MODE_SHORT_EDGE,
                        PrintAttributes.DUPLEX_MODE_NONE)
                .setMinMargins(new PrintAttributes.Margins(
                        ref.sideMarginMils(), ref.topBottomMarginMils(),
                        ref.sideMarginMils(), ref.topBottomMarginMils()))
                .build();

        PrinterInfo info = new PrinterInfo.Builder(id, Prefs.name(service), PrinterInfo.STATUS_IDLE)
                .setDescription(Prefs.ip(service) + ":" + Prefs.port(service))
                .setCapabilities(caps)
                .build();

        addPrinters(Collections.singletonList(info));
    }
}
