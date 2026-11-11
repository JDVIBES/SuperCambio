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

    public void onReceive(final Context ctx, Intent i) {
        final PendingResult pr = goAsync();
        new Thread(new Runnable() {
            public void run() {
                try {
                    fetchLatest(ctx);
                    push(ctx);
                } finally {
                    // finish() SIEMPRE: si no, el sistema mata la app por timeout del broadcast.
                    if (pr != null) pr.finish();
                }
            }
        }).start();
    }

    static void schedule(Context ctx, boolean enable) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent it = new Intent(ctx, RateAlarm.class);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 1001, it, 0x0C000000);
            if (enable) {
                TimeZone tz = TimeZone.getTimeZone("America/Caracas");
                Calendar cal = Calendar.getInstance(tz);
                cal.set(Calendar.HOUR_OF_DAY, 15);
                cal.set(Calendar.MINUTE, 35);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                if (cal.getTimeInMillis() <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1);
                am.setRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pi);
            } else {
                am.cancel(pi);
            }
        } catch (Exception ignored) { }
    }

    static void push(Context ctx) {
        try {
            SharedPreferences p = ctx.getSharedPreferences("supercambio", Context.MODE_PRIVATE);
            double usd = Double.longBitsToDouble(p.getLong("usd", 0));
            double par = Double.longBitsToDouble(p.getLong("par", 0));

            String hora = new SimpleDateFormat("HH:mm", new Locale("es", "VE")).format(new Date());
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

    private static void fetchLatest(Context ctx) {
        try {
            String s = httpGetS("https://ve.dolarapi.com/v1/dolares");
            JSONArray arr = new JSONArray(s);
            SharedPreferences p = ctx.getSharedPreferences("supercambio", Context.MODE_PRIVATE);
            SharedPreferences.Editor e = p.edit();
            try {
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
                        e.putLong("usd", Double.doubleToRawLongBits(v));
                        if (t > 0) e.putLong("tsUsd", t);
                    } else if ("paralelo".equals(fu)) {
                        if (t > 0 && t <= p.getLong("tsPar", 0)) continue;
                        e.putLong("par", Double.doubleToRawLongBits(v));
                        if (t > 0) e.putLong("tsPar", t);
                    }
                }
            } finally {
                // Aunque un elemento venga mal, se guarda lo que sí se pudo leer.
                e.apply();
            }
        } catch (Exception ignored) { }
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
