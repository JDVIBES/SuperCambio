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
                fetchLatest(ctx);
                push(ctx);
                if (pr != null) pr.finish();
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
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String fu = o.optString("fuente", "");
                if ("oficial".equals(fu)) {
                    double v = o.optDouble("promedio", 0);
                    long t = tsOfS(o);
                    if (v > 0) {
                        e.putLong("usd", Double.doubleToRawLongBits(v));
                        if (t > 0) e.putLong("tsUsd", t);
                    }
                } else if ("paralelo".equals(fu)) {
                    double v = o.optDouble("promedio", 0);
                    long t = tsOfS(o);
                    if (v > 0) {
                        e.putLong("par", Double.doubleToRawLongBits(v));
                        if (t > 0) e.putLong("tsPar", t);
                    }
                }
            }
            e.apply();
        } catch (Exception ignored) { }
    }

    private static String httpGetS(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "SuperCambio/2.0 (Android)");
        try {
            InputStream in = c.getInputStream();
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    private static long tsOfS(JSONObject o) {
        try {
            String f = o.optString("fechaActualizacion", "");
            if (f.length() >= 19) {
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                Date d = df.parse(f.substring(0, 19));
                if (d != null) return d.getTime();
            }
        } catch (Exception ignored) { }
        return 0;
    }

    private static String f(double v) {
        return String.format(new Locale("es", "VE"), "%,.2f", v);
    }
}
