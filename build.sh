#!/data/data/com.termux/files/usr/bin/sh
# Build de Super Cambio 100% en Termux (sin Android Studio).
#   sh build.sh            → compila, alinea y firma (usa la keystore del repo si existe)
#   sh build.sh <version>  → idem, y además copia el APK a /storage/emulated/0/MT2/apks/
#
# Herramientas (pkg install -y aapt apksigner zipalign d8 ecj git):
#   aapt      → empaqueta recursos y genera R.java
#   ecj       → compila el Java (el wrapper de Termux fija el nivel Java 7)
#   d8        → convierte las clases en classes.dex (si no está, se usa dx)
#   zipalign  → alinea el APK (4 bytes) ANTES de firmar
#   apksigner → firma con la keystore
set -e
cd "$(dirname "$0")"

VERSION="${1:-$(grep -o 'versionName=\"[^\"]*\"' app/AndroidManifest.xml | cut -d'"' -f2)}"
MANIFEST_VERSION="$(grep -o 'versionName=\"[^\"]*\"' app/AndroidManifest.xml | cut -d'"' -f2)"
KS="out/supercambio.keystore"
KS_PASS="pass:supercambio123"
KEY_PASS="pass:supercambio123"
ALIAS="supercambio"

echo "==> Super Cambio $VERSION"
rm -rf app/gen out/classes out/classes.jar out/dexdir
mkdir -p app/gen out/classes out/dexdir

echo "==> aapt (recursos + R.java)"
( cd app && aapt package -f -m -M AndroidManifest.xml -S res -A assets -I ../android-jar/android.jar -J gen )

echo "==> ecj (compilar)"
( cd app && ecj -nowarn -classpath ../libs/zxing-core-3.3.3.jar -d ../out/classes src gen )

echo "==> dex"
( cd out && jar cf classes.jar -C classes . )
if command -v d8 >/dev/null 2>&1; then
    ( cd out && d8 --lib ../android-jar/android.jar --min-api 24 --release --output dexdir classes.jar ../libs/zxing-core-3.3.3.jar )
    mv out/dexdir/classes.dex out/classes.dex
else
    echo "    (d8 no está: se usa dx)"
    ( cd out && dx --dex --min-sdk-version=24 --output=classes.dex classes ../libs/zxing-core-3.3.3.jar )
fi

echo "==> aapt (APK)"
( cd out && aapt package -f -M ../app/AndroidManifest.xml -S ../app/res -A ../app/assets \
    -I ../android-jar/android.jar -F unsigned.apk && aapt add unsigned.apk classes.dex )

# El dex tiene que estar EN LA RAÍZ del APK: si no, la app no arranca.
if ! unzip -l out/unsigned.apk | grep -q " classes.dex$"; then
    echo "    ERROR: classes.dex no está en la raíz del APK"; exit 1
fi

echo "==> zipalign + firma"
( cd out && zipalign -f 4 unsigned.apk aligned.apk )
if [ -f "$KS" ]; then
    apksigner sign --ks "$KS" --ks-pass "$KS_PASS" --key-pass "$KEY_PASS" \
        --ks-key-alias "$ALIAS" --out "out/SuperCambio-$VERSION.apk" out/aligned.apk
    apksigner verify --print-certs "out/SuperCambio-$VERSION.apk" | head -2
    echo "    APK: out/SuperCambio-$VERSION.apk"
    if [ "$VERSION" != "$MANIFEST_VERSION" ]; then
        echo "    AVISO: pediste $VERSION pero el manifest dice $MANIFEST_VERSION"
    fi
else
    echo "    NO hay keystore en $KS: se deja out/aligned.apk SIN firmar (no se puede publicar así)"
fi

if [ -n "$2" ] && [ -f "out/SuperCambio-$VERSION.apk" ]; then
    mkdir -p /storage/emulated/0/MT2/apks/
    cp "out/SuperCambio-$VERSION.apk" /storage/emulated/0/MT2/apks/
    echo "==> copiado a /storage/emulated/0/MT2/apks/SuperCambio-$VERSION.apk"
fi
echo "==> listo"
