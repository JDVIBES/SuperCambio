<div align="center">

<img src="logo.png" width="110" alt="Logo de Super Cambio"/>

# 💱 Super Cambio

**Tasas de cambio de Venezuela, rápidas y sin anuncios.**

Dólar BCV · Dólar paralelo · Euro BCV · USDT Binance P2P

[![Descargar APK](https://img.shields.io/badge/Descargar-APK_v3.0.15-0A84FF?style=for-the-badge&logo=android&logoColor=white)](../../releases/latest)
[![Versión](https://img.shields.io/badge/versión-3.0.15-success?style=flat-square)](../../releases)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](#)
[![Sin anuncios](https://img.shields.io/badge/100%25-sin_anuncios-orange?style=flat-square)](#)

</div>

---

## ✨ Características

| | |
|---|---|
| 📊 **Tasas en vivo** | Dólar BCV, dólar paralelo, euro BCV y USDT Binance P2P, con variación diaria |
| ⏱ **Próxima tasa BCV** | Cuenta regresiva a la publicación estimada (3:30 PM, día hábil) con búsqueda automática y respaldo directo del BCV |
| 📈 **Gráficas** | Eje temporal real (se ve cuánto se mantiene cada tasa), variación del período, color por tramo, barra de navegación y exploración con pellizco |
| 🔁 **Conversor** | USD/EUR/USDT ⇄ Bs. en tiempo real, invertible, chips de montos rápidos, resultado copiable |
| 💳 **Perfiles de pago** | Pago móvil, banco, Binance y Zelle — con exportar/importar respaldo JSON |
| 📤 **Compartir con marca** | Tarjetas con el logo real para tasas, conversiones o perfiles (WhatsApp/Telegram) |
| 🧩 **4 widgets** | Mini 1×1, chico 2×1, mediano y completo 4 tasas — todo en pantalla de inicio |
| ⬇️ **Actualizaciones propias** | La app avisa al abrir si hay versión nueva, descarga con barra y cancelación, y pide permiso para instalarse sin tienda alguna |
| 🌗 **Temas y acento** | Claro, oscuro o automático con transición de pantalla completa + 5 colores de acento |
| 🌐 **Español e Inglés** | Cambiable desde la app |
| 🧊 **Vidrio líquido** | Un solo interruptor en *Acerca de* activa desenfoque real, reflejos animados y superficies de vidrio en tarjetas, campos e iconos — sin botones extra sobre la barra |
| 📌 **Aviso diario** | Notificación a las 3:35 PM con la tasa del día (activable) |
| 🤝 **Historial compartido** | Las gráficas se enriquecen entre usuarios — solo cifras, nunca datos personales. Sube la serie fusionada (nadie borra el historial de otro) y valida cada escritura |
| 🔤 **Tipografía e iconos** | Inter (equivalente libre de SF Pro) + iconos vectoriales estilo iOS en toda la interfaz |
| 🚀 **Fluida** | Soporte 120 Hz, vibración háptica, pull-to-refresh, APK de menos de 1 MB |

## 📥 Instalación

1. Descarga el último APK desde [**Releases**](../../releases/latest)
2. La app te pide el permiso de *«Fuentes desconocidas»* justo al actualizar — tienes que aceptarlo una sola vez
3. Listo — las tasas se cargan al abrir y el aviso diario llega a las 3:35 PM si lo activaste

> **Requisitos:** Android 7.0 (API 24) o superior · ~1 MB de espacio

## 🔒 Privacidad

- **Sin anuncios, sin suscripciones, sin rastreadores**
- Los perfiles de pago se guardan **solo en tu teléfono** (nada sale a internet)
- Las únicas conexiones son de **lectura** a las APIs públicas de tasas y de escritura del historial anónimo de cifras
- La actualización automática consulta Releases de este mismo repositorio

## 🛠️ Compilar desde fuente

Compilado 100% en Termux (Android) sin Android Studio. Lo más fácil es usar el script:

```bash
pkg install -y aapt apksigner zipalign d8 ecj git
git clone https://github.com/JDVIBES/SuperCambio.git && cd SuperCambio
mkdir -p out android-jar app/gen
# Descarga el android.jar de API 28 (Termux):
curl -L -o android-jar/android.jar \
  https://github.com/Termux-user-organization/android-jar/raw/main/android-28/android.jar

sh build.sh                 # compila, alinea y firma (usa la keystore del repo si existe)
sh build.sh 3.0.15 copiar   # además copia el APK a /storage/emulated/0/MT2/apks/
```

O a mano, paso a paso:

```bash
cd app
aapt package -f -m -M AndroidManifest.xml -S res -A assets -I ../android-jar/android.jar -J gen
ecj -nowarn -classpath ../libs/zxing-core-3.3.3.jar -d ../out/classes src gen
cd ../out && jar cf classes.jar -C classes .
d8 --lib ../android-jar/android.jar --min-api 24 --release --output dexdir classes.jar ../libs/zxing-core-3.3.3.jar
aapt package -f -M ../app/AndroidManifest.xml -S ../app/res -A ../app/assets \
  -I ../android-jar/android.jar -F unsigned.apk
aapt add unsigned.apk dexdir/classes.dex   # el dex va EN LA RAÍZ del APK
# Genera tu propia keystore:
keytool -genkeypair -keystore mi-keystore.keystore -alias app -keyalg RSA -validity 10000
zipalign -f 4 unsigned.apk aligned.apk
apksigner sign --ks mi-keystore.keystore --out SuperCambio.apk aligned.apk
```

### 🧪 Pruebas

`test/AuditTest.java` comprueba la lógica crítica (fechas ISO con offset, depuración de
vaivenes, fusión del historial compartido y búsqueda de bancos) contra el código real:

```bash
curl -sL -o /tmp/json.jar https://repo1.maven.org/maven2/org/json/json/20231013/json-20231013.jar
mkdir -p /tmp/tc && javac -nowarn -encoding UTF-8 -cp "/tmp/json.jar:android-jar/android.jar:libs/zxing-core-3.3.3.jar" \
  -d /tmp/tc app/src/com/supercambio/app/*.java app/gen/com/supercambio/app/R.java test/AuditTest.java
java -cp "/tmp/tc:/tmp/json.jar:android-jar/android.jar" com.supercambio.app.AuditTest
```

## 🧩 Arquitectura

- **Java 7 nativo, cero dependencias externas** — una sola `Activity` con UI programática (sin XML de layouts) + `ContentProvider` propio para compartir imágenes y actualizaciones
- **Barra flotante con blur propio**: captura por franjas + box blur encadenado + escalado GPU (~250× menos memoria que una captura completa)
- **Emojis Apple + Inter**: subset propio de Apple Emoji aplicado por spans a nivel de texto, letras siempre en Inter
- **Almacenamiento local**: `SharedPreferences` (tasas en caché, historial de gráficas, perfiles de pago)

**Fuentes de datos:** [DolarAPI Venezuela](https://ve.dolarapi.com) (BCV, paralelo, euro) · [CriptoYa](https://criptoya.com) (USDT Binance P2P)

## 👤 Créditos

Desarrollado con ❤️ para Venezuela por **Jdvibes** · © 2026

<div align="center">

**⭐ Si te sirve la app, deja una estrella al repo**

</div>
