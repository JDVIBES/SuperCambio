package com.supercambio.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class RateAlarm extends BroadcastReceiver {

    static final String CH = "tasas_diarias";
    // Modo "vigilancia": comprueba cada media hora si el BCV publicó una tasa nueva y
    // avisa solo si cambió (el aviso diario es el resumen de las 3:35 PM).
    static final String EXTRA_WATCH = "watch";

    public void onReceive(final Context ctx, Intent i) {
        final boolean watch = i != null && i.getBooleanExtra(EXTRA_WATCH, false);
        final PendingResult pr = goAsync();
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (watch) {
                        // El rearme va PRIMERO: la red puede pasar del presupuesto del
                        // broadcast (~10 s) y el sistema mata el proceso; si el rearme estaba
                        // después, la alarma exacta (one-shot) se perdía para el día siguiente.
                        scheduleWatch(ctx);
                        boolean changed = fetchLatest(ctx);
                        RateWidget.push(ctx);          // el widget se actualiza siempre
                        if (changed) push(ctx);        // el aviso solo si la tasa cambió
                    } else {
                        // El rearme va PRIMERO: la red puede pasar del presupuesto del
                        // broadcast (~10 s) y el sistema mata el proceso; si el rearme estaba
                        // después, la alarma exacta (one-shot) se perdía para el día siguiente.
                        schedule(ctx, true);
                        fetchLatest(ctx);
                        push(ctx);
                    }
                } finally {
                    // finish() SIEMPRE: si no, el sistema mata la app por timeout del broadcast.
                    if (pr != null) pr.finish();
                }
            }
        }).start();
    }

    // ===== VIGILANCIA: avisar cuando se publica una tasa nueva =====
    // Turnos cada hora (de 2:00 a 7:00 PM, hora de Venezuela) solo de lunes a viernes:
    // es la ventana en la que el BCV publica. Así el aviso llega como máximo una hora
    // después de la publicación, aunque la app esté cerrada.
    static void scheduleWatch(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent it = new Intent(ctx, RateAlarm.class);
            it.putExtra(EXTRA_WATCH, true);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 1002, it, 0x0C000000);
            long next = nextWatchSlot();
            if (next <= 0) { am.cancel(pi); return; }
            if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, next, pi);
            }
        } catch (Exception ignored) { }
    }

    static void cancelWatch(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent it = new Intent(ctx, RateAlarm.class);
            it.putExtra(EXTRA_WATCH, true);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 1002, it, 0x0C000000);
            am.cancel(pi);
        } catch (Exception ignored) { }
    }

    private static long nextWatchSlot() {
        try {
            TimeZone tz = TimeZone.getTimeZone("America/Caracas");
            long now = System.currentTimeMillis();
            Calendar cal = Calendar.getInstance(tz);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            for (int day = 0; day < 8; day++) {
                for (int h = 14; h <= 19; h++) {
                    cal.set(Calendar.HOUR_OF_DAY, h);
                    long t = cal.getTimeInMillis();
                    if (t <= now) continue;
                    int dow = cal.get(Calendar.DAY_OF_WEEK);
                    if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) break;  // hoy es fin de semana
                    return t;
                }
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    static void schedule(Context ctx, boolean enable) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent it = new Intent(ctx, RateAlarm.class);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 1001, it, 0x0C000000);
            if (enable) {
                long next = nextSlot();
                // Alarma EXACTA: desde Android 6 el sistema agrupa las alarmas repetidas y el
                // aviso de las 15:35 llegaba tarde (o se aplazaba en Doze).
                try {
                    if (Build.VERSION.SDK_INT >= 23) {
                        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi);
                    } else {
                        am.setExact(AlarmManager.RTC_WAKEUP, next, pi);
                    }
                } catch (Exception e) {
                    // Si el usuario quitó el permiso de "alarmas y recordatorios": alarma
                    // repetida (puede llegar tarde, pero el aviso llega).
                    am.setRepeating(AlarmManager.RTC_WAKEUP, next, AlarmManager.INTERVAL_DAY, pi);
                }
            } else {
                am.cancel(pi);
            }
        } catch (Exception ignored) { }
    }

    // Próximo 15:35 de Caracas (día hábil).
    private static long nextSlot() {
        TimeZone tz = TimeZone.getTimeZone("America/Caracas");
        Calendar cal = Calendar.getInstance(tz);
        cal.set(Calendar.HOUR_OF_DAY, 15);
        cal.set(Calendar.MINUTE, 35);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long now = System.currentTimeMillis();
        if (cal.getTimeInMillis() <= now) cal.add(Calendar.DAY_OF_YEAR, 1);
        // si cae en fin de semana, se pasa al lunes
        while (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
                || cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return cal.getTimeInMillis();
    }

    static void push(Context ctx) {
        try {
            SharedPreferences p = ctx.getSharedPreferences("supercambio", Context.MODE_PRIVATE);
            double usd = Double.longBitsToDouble(p.getLong("usd", 0));
            double par = Double.longBitsToDouble(p.getLong("par", 0));

            SimpleDateFormat hf = new SimpleDateFormat("HH:mm", new Locale("es", "VE"));
            hf.setTimeZone(TimeZone.getTimeZone("America/Caracas"));   // la hora es la de Venezuela
            String hora = hf.format(new Date());
            StringBuilder txt = new StringBuilder();
            if (usd > 0) txt.append("BCV: Bs. ").append(f(usd));
            if (par > 0) {
                if (txt.length() > 0) txt.append("  ·  ");
                txt.append("Paralelo: Bs. ").append(f(par));
            }
            if (txt.length() == 0) txt.append("Abre la app para actualizar las tasas");

            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            Notification.Builder nb;
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(CH, "Tasa diaria del BCV", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("Aviso diario con la tasa oficial y el paralelo");
                nm.createNotificationChannel(ch);
                nb = new Notification.Builder(ctx, CH);
            } else {
                nb = new Notification.Builder(ctx);
                nb.setPriority(Notification.PRIORITY_DEFAULT);
            }
            Intent it = new Intent(ctx, MainActivity.class);
            it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent cpi = PendingIntent.getActivity(ctx, 2002, it, 0x0C000000);
            nb.setSmallIcon(R.drawable.ic_tab_rates)
                    .setContentTitle("Super Cambio · " + hora)
                    .setContentText(txt.toString())
                    .setStyle(new Notification.BigTextStyle().bigText(txt.toString()))
                    .setContentIntent(cpi)
                    .setAutoCancel(true);
            nm.notify(2001, nb.build());
        } catch (Exception ignored) { }
        RateWidget.push(ctx);
    }

    // Devuelve true si la tasa oficial cambió (para avisar solo cuando hay novedad)
    private static boolean fetchLatest(Context ctx) {
        boolean changed = false;
        SharedPreferences p = ctx.getSharedPreferences("supercambio", Context.MODE_PRIVATE);
        SharedPreferences.Editor e = p.edit();
        try {
            // USD: oficial y paralelo
            JSONArray arr = new JSONArray(httpGetS("https://ve.dolarapi.com/v1/dolares"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String fu = o.optString("fuente", "");
                double v = o.optDouble("promedio", 0);
                long t = tsOfS(o);
                if (v <= 0) continue;
                if ("oficial".equals(fu)) {
                    // Solo si es MÁS NUEVO que lo guardado: una réplica vieja del API no
                    // puede pisar la tasa que el usuario ya tenía.
                    if (t > 0 && t <= p.getLong("tsUsd", 0)) continue;
                    double old = Double.longBitsToDouble(p.getLong("usd", 0));
                    if (old > 0 && Math.abs(old - v) > 0.00001) changed = true;
                    e.putLong("usd", Double.doubleToRawLongBits(v));
                    if (t > 0) e.putLong("tsUsd", t);
                } else if ("paralelo".equals(fu)) {
                    if (t > 0 && t <= p.getLong("tsPar", 0)) continue;
                    e.putLong("par", Double.doubleToRawLongBits(v));
                    if (t > 0) e.putLong("tsPar", t);
                }
            }
        } catch (Exception ignored) { }
        try {
            // EUR oficial: sin esto el widget "Completo" mostraba el euro VIEJO junto a la
            // hora nueva del dólar.
            JSONArray arr = new JSONArray(httpGetS("https://ve.dolarapi.com/v1/euros"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                if (!"oficial".equals(o.optString("fuente", ""))) continue;
                double v = o.optDouble("promedio", 0);
                long t = tsOfS(o);
                if (v <= 0) continue;
                if (t > 0 && t <= p.getLong("tsEur", 0)) continue;
                e.putLong("eur", Double.doubleToRawLongBits(v));
                if (t > 0) e.putLong("tsEur", t);
            }
        } catch (Exception ignored) { }
        try {
            // USDT (Binance P2P)
            JSONObject c = new JSONObject(httpGetS("https://criptoya.com/api/USDT/VES/1"));
            JSONObject bin = c.getJSONObject("binancep2p");
            double bid = bin.optDouble("bid", 0);
            long t = bin.optLong("time", 0) * 1000L;
            if (bid > 0 && (t <= 0 || t > p.getLong("tsUsdt", 0))) {
                e.putLong("usdt", Double.doubleToRawLongBits(bid));
                if (t > 0) e.putLong("tsUsdt", t);
            }
        } catch (Exception ignored) { }
        try {
            // Aunque un bloque falle, se guarda lo que sí se pudo leer.
            e.putLong("ts_last", System.currentTimeMillis());   // cuándo se obtuvo el dato
            e.apply();
        } catch (Exception ignored) { }
        return changed;
    }

    private static String httpGetS(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(4000);
        c.setReadTimeout(4000);
        c.setRequestProperty("User-Agent", "SuperCambio/2.0 (Android)");
        try {
            InputStream in = c.getInputStream();
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            // Tope total de ~8 s: el timeout de lectura es por lectura, no por respuesta
            // (una página lenta cruzaba el límite de 10 s del broadcast y mataba la app).
            long deadline = System.currentTimeMillis() + 8000L;
            while ((line = r.readLine()) != null) {
                sb.append(line);
                if (System.currentTimeMillis() > deadline) break;
            }
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    private static long tsOfS(JSONObject o) {
        try {
            // El paralelo llega en UTC y el oficial con offset -04:00: el parser de la app
            // respeta el offset (si no, el paralelo quedaba 4 h por delante).
            return MainActivity.tsOfISO(o.optString("fechaActualizacion", ""));
        } catch (Exception ignored) { }
        return 0;
    }

    private static String f(double v) {
        return String.format(new Locale("es", "VE"), "%,.2f", v);
    }
}
