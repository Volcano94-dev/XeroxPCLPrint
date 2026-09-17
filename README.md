# Xerox PCL Print

Plug-in d'impression Android pour les imprimantes qui comprennent le PCL5 mais pas les formats
« sans pilote » d'Android (cas de la Xerox WorkCentre 3225). Gratuit, sans publicité, sans aucune
dépendance externe, et rien ne sort du réseau local.

Principe : Android fournit un PDF → le plug-in le convertit en image noir et blanc (300 ou 600 dpi)
→ l'encode en PCL5 → l'envoie à l'imprimante sur le port 9100 (RAW / JetDirect).

Fonctions : A4 / A5 / Letter / Legal, 300 ou 600 dpi, recto-verso, exemplaires multiples (assemblés),
pages paysage tournées automatiquement, tramage des gris et des photos.

---

## 1. Compiler l'APK

### Méthode A — GitHub, sans rien installer

1. Créez un compte sur github.com si besoin, puis un nouveau dépôt (**privé** de préférence), vide.
2. Sur la page du dépôt : « uploading an existing file », puis glissez-déposez **tout le contenu**
   du dossier `XeroxPclPrint` (y compris le dossier `.github`) et validez (« Commit changes »).
3. Onglet **Actions** : la compilation « Compiler l'APK » démarre seule (3 à 5 minutes).
   Si rien ne démarre, ouvrez-la et cliquez sur « Run workflow ».
4. Une fois la coche verte affichée, l'APK se trouve dans la rubrique **Releases** du dépôt
   (`XeroxPclPrint.apk`), téléchargeable directement depuis le navigateur du téléphone.

Si la compilation échoue (croix rouge), ouvrez l'étape en erreur et copiez-moi les dernières lignes.

### Méthode B — Android Studio

Ouvrez le dossier `XeroxPclPrint`, laissez la synchronisation Gradle se terminer, puis
*Build > Build App Bundle(s) / APK(s) > Build APK(s)*, ou lancez directement sur le téléphone en USB.

---

## 2. Installer sur le téléphone (Samsung)

1. Sur les Galaxy récents, **Auto Blocker** empêche l'installation d'APK hors Play Store :
   *Paramètres > Sécurité et confidentialité > Auto Blocker* → désactiver le temps de l'installation
   (vous pourrez le réactiver ensuite).
2. Ouvrez `XeroxPclPrint.apk`, autorisez l'installation depuis cette source, installez.
   Play Protect peut afficher un avertissement « application inconnue » : c'est normal pour une
   application compilée soi-même.

## 3. Régler et tester

1. Ouvrez l'application **Xerox PCL Print**. Vérifiez l'adresse (`10.0.0.2`) et le port (`9100`).
2. **Page de test : texte simple** → valide le réseau et le langage PCL.
3. **Page de test : graphique** → valide le rendu : le cadre doit être à 10 mm de chaque bord,
   le trait doit mesurer 100 mm, le dégradé doit être régulier.

## 4. Activer le service d'impression

Bouton « Ouvrir les réglages d'impression Android » (ou *Paramètres > Appareils connectés >
Plus de paramètres de connexion > Impression*), puis activez **Xerox PCL Print**.

Ensuite, dans n'importe quelle application : *Imprimer* → choisir
« Xerox WorkCentre 3225 (PCL) » dans la liste des imprimantes.

---

## Dépannage

| Symptôme | Piste |
|---|---|
| « Imprimante injoignable » | Imprimante éteinte ou en veille profonde, téléphone sur un autre Wi-Fi, ou adresse IP modifiée. Conseil : réservez l'adresse 10.0.0.2 à l'imprimante dans la box (bail DHCP statique). |
| La page texte sort, pas la page graphique | Dites-le-moi : il faudra ajuster l'encodage raster (résolution ou compression). |
| Impression décalée ou rognée | Mesurez le cadre de la page de test et donnez-moi les 4 distances aux bords. |
| Gris ou photos trop sombres / trop clairs | Changez « Éclaircissement des gris et des photos » dans l'application (Aucun / Léger / Moyen / Fort), puis réimprimez la page de test graphique. |
| Des pages de caractères bizarres sortent | L'imprimante n'interprète pas le flux comme du PCL : arrêtez le travail et prévenez-moi. |
| Gros documents lents | Choisissez 300 dpi dans les options de la boîte de dialogue d'impression. |

## Organisation du code (`app/src/main/java/fr/xeroxpcl/printservice`)

- `PclPrintService` — service d'impression : reçoit les travaux, orchestre conversion et envoi.
- `PclDiscoverySession` — annonce l'imprimante et ses capacités à Android.
- `PdfPageSource` / `JobRenderer` — rendu du PDF par bandes (faible consommation mémoire).
- `Halftoner` — passage en noir et blanc : trame à points groupés à 45° (adaptée aux lasers) ou seuil.
- `PclEncoder` / `Paper` — génération du PCL5 (raster compressé PackBits) et formats de papier.
- `RawSender` — envoi TCP sur le port 9100.
- `SettingsActivity` / `TestPageSource` — réglages et pages de test.

Note de sécurité : la clé de signature (`app/keystore/xeroxpcl.p12`) est incluse pour que les mises à
jour s'installent par-dessus les versions précédentes. Elle n'a rien de secret : gardez le dépôt privé
et n'utilisez pas cette clé pour autre chose.
