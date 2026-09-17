package fr.xeroxpcl.printservice;

/**
 * Formats de papier gérés, avec leurs caractéristiques PCL5.
 * En PCL, la "page logique" est plus étroite que la feuille : elle commence à
 * leftOffset300 points (à 300 dpi) du bord gauche, et on ne peut rien imprimer à sa gauche.
 */
enum Paper {
    A4("ISO_A4", 26, 8268, 11693, 71),
    A5("ISO_A5", 25, 5827, 8268, 71),
    LETTER("NA_LETTER", 2, 8500, 11000, 75),
    LEGAL("NA_LEGAL", 3, 8500, 14000, 75);

    /** Zone non imprimable en haut et en bas, en points à 300 dpi (1/6 de pouce). */
    private static final int UNPRINTABLE_300 = 50;

    final String androidId;
    final int pclCode;
    final int widthMils;
    final int heightMils;
    final int leftOffset300;

    Paper(String androidId, int pclCode, int widthMils, int heightMils, int leftOffset300) {
        this.androidId = androidId;
        this.pclCode = pclCode;
        this.widthMils = widthMils;
        this.heightMils = heightMils;
        this.leftOffset300 = leftOffset300;
    }

    int widthPx(int dpi)        { return Math.round(widthMils * dpi / 1000f); }
    int heightPx(int dpi)       { return Math.round(heightMils * dpi / 1000f); }
    int leftOffsetPx(int dpi)   { return leftOffset300 * dpi / 300; }
    int logicalWidthPx(int dpi) { return widthPx(dpi) - 2 * leftOffsetPx(dpi); }
    int unprintableRows(int dpi){ return UNPRINTABLE_300 * dpi / 300; }

    /** Marges minimales annoncées à Android, en millièmes de pouce. */
    int sideMarginMils()   { return leftOffset300 * 1000 / 300 + 4; }
    int topBottomMarginMils() { return UNPRINTABLE_300 * 1000 / 300 + 4; }

    static Paper fromAndroidId(String id) {
        if (id != null) {
            for (Paper p : values()) if (p.androidId.equals(id)) return p;
        }
        return A4;
    }
}
