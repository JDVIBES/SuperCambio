Super Cambio — Proyecto completo
© 2026 Jdvibes
Versión actual: 1.8.1 (versionCode 22)

Estructura
==========
app/
  AndroidManifest.xml      Manifest (paquete com.supercambio.app, API 24-28)
  src/.../MainActivity.java Toda la app en una sola Activity (Java 7)
  src/.../ShareProvider.java ContentProvider para compartir PNGs
  res/                     Recursos (layouts programáticos, solo drawables/valores)
  assets/fonts/            Inter Regular/Medium/Bold (SF Pro) + AppleEmoji.ttf (subconjunto)
  gen/                     Clases R generadas por aapt
out/
  supercambio.keystore     Llave de firma (alias: supercambio, pass: supercambio123)
  SuperCambio-1.8.1.apk    APK firmado listo para instalar
fonts/                    Descargas originales de fuentes
android-jar/android.jar   (No incluido) API 28 de Termux, se puede descargar de github.com/Termux

Compilar en Termux
==================
pkg install -y aapt apksigner dx ecj  # ecj es un wrapper que usa dalvikvm

cd ~/supercambio/app
aapt package -f -m -M AndroidManifest.xml -S res -A assets -I ../android-jar/android.jar -J gen
ecj -nowarn -d ../out/classes src gen
cd ../out
dx --dex --min-sdk-version=24 --output=classes.dex classes
aapt package -f -M ../app/AndroidManifest.xml -S ../app/res -A ../app/assets -I ../android-jar/android.jar -F unsigned.apk
aapt add unsigned.apk classes.dex
apksigner sign --ks supercambio.keystore --ks-pass pass:supercambio123 \
  --key-pass pass:supercambio123 --out SuperCambio-X.apk unsigned.apk
apksigner verify SuperCambio-X.apk

Notas
=====
- ecj funciona con Java 7: sin lambdas ni referencias a métodos
- Para publicar una actualización, usar SIEMPRE la misma keystore
- Datos: ve.dolarapi.com, criptoya.com — solo lectura, sin claves
- Los perfiles de pago se guardan solo en el teléfono (SharedPreferences)
