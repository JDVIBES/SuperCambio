<div align="center">

# 💱 Super Cambio

**Tasas de cambio de Venezuela, rápidas y sin anuncios.**

Dólar BCV · Dólar paralelo · Euro BCV · USDT Binance P2P

[![Descargar APK](https://img.shields.io/badge/Descargar-APK_v1.8.1-0A84FF?style=for-the-badge&logo=android&logoColor=white)](../../releases/latest)
[![Versión](https://img.shields.io/badge/versión-1.8.1-success?style=flat-square)](../../releases)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](#)
[![Sin anuncios](https://img.shields.io/badge/100%25-sin_anuncios-orange?style=flat-square)](#)

</div>

---

## ✨ Características

| | |
|---|---|
| 📊 **Tasas en vivo** | Dólar BCV, dólar paralelo, euro BCV y USDT Binance P2P, con variación diaria |
| 📈 **Gráficas** | Historial acumulado de cada tasa con animación de trazado |
| 🔁 **Conversor** | Cálculo en tiempo real (USD/EUR/USDT ⇄ Bs.) con la tasa del día, invertible |
| 💳 **Perfiles de pago** | Guarda tu Pago móvil, cuenta de banco, Binance y Zelle — solo en tu teléfono |
| 📤 **Compartir con marca** | Genera una tarjeta con logo y comparte tasas, conversiones o perfiles por WhatsApp/Telegram |
| ⏱ **Próxima tasa BCV** | Cuenta regresiva a la publicación estimada (3:30 PM, día hábil) con búsqueda automática |
| 🌗 **Temas** | Claro, oscuro o automático con transición animada de toda la pantalla |
| 🧊 **Vidrio esmerilado** | Barra flotante con desenfoque real estilo iPhone (activable en *Acerca de*) |
| 🔤 **Tipografía iOS** | Inter (equivalente libre de SF Pro) + emojis Apple |
| 🚀 **Fluida** | Soporte 120 Hz, animaciones suaves, APK de menos de 1 MB |

## 📥 Instalación

1. Descarga el último APK desde [**Releases**](../../releases/latest)
2. Activa *"Instalar apps de fuentes desconocidas"* para tu navegador o administrador de archivos
3. Abre el APK e instala
4. Listo — las tasas se cargan al abrir

> **Requisitos:** Android 7.0 (API 24) o superior · ~1 MB de espacio

## 🔒 Privacidad

- **Sin anuncios, sin suscripciones, sin rastreadores**
- Los perfiles de pago se guardan **solo en tu teléfono** (nada sale a internet)
- Las únicas conexiones son de lectura a las APIs públicas de tasas

## 🛠️ Compilar desde fuente

Compilado 100% en Termux (Android) sin Android Studio:

```bash
pkg install -y aapt apksigner dx ecj git

git clone https://github.com/AidynOtaku/SuperCambio.git && cd SuperCambio
mkdir -p out android-jar app/gen
# Descarga el android.jar de API 28 (Termux):
curl -L -o android-jar/android.jar \
  https://github.com/Termux-user-organization/android-jar/raw/main/android-28/android.jar

cd app
aapt package -f -m -M AndroidManifest.xml -S res -A assets -I ../android-jar/android.jar -J gen
ecj -nowarn -d ../out/classes src gen
cd ../out
dx --dex --min-sdk-version=24 --output=classes.dex classes
aapt package -f -M ../app/AndroidManifest.xml -S ../app/res -A ../app/assets \
  -I ../android-jar/android.jar -F unsigned.apk
aapt add unsigned.apk classes.dex
# Genera tu propia keystore:
keytool -genkeypair -keystore mi-keystore.keystore -alias app -keyalg RSA -validity 10000
apksigner sign --ks mi-keystore.keystore --out SuperCambio.apk unsigned.apk
```

## 🧩 Arquitectura

- **Java 7 nativo, cero dependencias externas** — una sola `Activity` con UI programática (sin XML de layouts)
- **Barra flotante con blur propio**: captura por franjas + box blur encadenado + escalado GPU (~250× menos memoria que una captura completa)
- **Compartir imágenes**: `ContentProvider` propio para servir PNGs sin librerías de soporte
- **Almacenamiento local**: `SharedPreferences` (tasas en caché, historial de gráficas, perfiles de pago)

**Fuentes de datos:** [DolarAPI Venezuela](https://ve.dolarapi.com) (BCV, paralelo, euro) · [CriptoYa](https://criptoya.com) (USDT Binance P2P)

## 👤 Créditos

Desarrollado con ❤️ para Venezuela por **Jdvibes** · © 2026

<div align="center">

**⭐ Si te sirve la app, deja una estrella al repo**

</div>
