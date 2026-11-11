package com.supercambio.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class RateWidget extends AppWidgetProvider {

    public void onUpdate(Context ctx, AppWidgetManager awm, int[] ids) {
        push(ctx);
    }

    static void push(Context ctx) {
        try {
            SharedPreferences p = ctx.getSharedPreferences("supercambio", Context.MODE_PRIVATE);
            double usd = Double.longBitsToDouble(p.getLong("usd", 0));
            double eur = Double.longBitsToDouble(p.getLong("eur", 0));
            double par = Double.longBitsToDouble(p.getLong("par", 0));
            double usdt = Double.longBitsToDouble(p.getLong("usdt", 0));
            long ts = p.getLong("tsUsd", 0);
            String t = ts > 0
                    ? new SimpleDateFormat("d MMM · HH:mm", new Locale("es", "VE")).format(new Date(ts))
                    : "—";

            AppWidgetManager awm = AppWidgetManager.getInstance(ctx);
            Intent it = new Intent(ctx, MainActivity.class);
            it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(ctx, 3001, it, 0x0C000000);

            boolean clear = p.getBoolean("widget_clear", false);
            int bg = clear ? R.drawable.widget_bg_clear : R.drawable.widget_bg;

            // Mediano: BCV / Paralelo / Euro
            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_rates);
            rv.setInt(R.id.widget_root, "setBackgroundResource", bg);
            rv.setTextViewText(R.id.w_usd, usd > 0 ? f(usd) : "—");
            rv.setTextViewText(R.id.w_par, par > 0 ? f(par) : "—");
            rv.setTextViewText(R.id.w_eur, eur > 0 ? f(eur) : "—");
            rv.setTextViewText(R.id.w_time, "Actualizado: " + t);
            rv.setOnClickPendingIntent(R.id.widget_root, pi);
            awm.updateAppWidget(new ComponentName(ctx, RateWidget.class), rv);

            // Mini 1x1: solo BCV
            RemoteViews sv = new RemoteViews(ctx.getPackageName(), R.layout.widget_single);
            sv.setInt(R.id.ws_root, "setBackgroundResource", bg);
            sv.setTextViewText(R.id.ws_val, usd > 0 ? f(usd) : "—");
            sv.setTextViewText(R.id.ws_time, t);
            sv.setOnClickPendingIntent(R.id.ws_root, pi);
            awm.updateAppWidget(new ComponentName(ctx, WidgetSingle.class), sv);

            // Chico 2x1: BCV + Paralelo
            RemoteViews mv = new RemoteViews(ctx.getPackageName(), R.layout.widget_small);
            mv.setInt(R.id.wm_root, "setBackgroundResource", bg);
            mv.setTextViewText(R.id.wm_usd, usd > 0 ? f(usd) : "—");
            mv.setTextViewText(R.id.wm_par, par > 0 ? f(par) : "—");
            mv.setTextViewText(R.id.wm_time, "Actualizado: " + t);
            mv.setOnClickPendingIntent(R.id.wm_root, pi);
            awm.updateAppWidget(new ComponentName(ctx, WidgetSmall.class), mv);

            // Completo: las 4 tasas
            RemoteViews lv = new RemoteViews(ctx.getPackageName(), R.layout.widget_large);
            lv.setInt(R.id.wl_root, "setBackgroundResource", bg);
            lv.setTextViewText(R.id.wl_usd, usd > 0 ? f(usd) : "—");
            lv.setTextViewText(R.id.wl_par, par > 0 ? f(par) : "—");
            lv.setTextViewText(R.id.wl_usdt, usdt > 0 ? f(usdt) : "—");
            lv.setTextViewText(R.id.wl_eur, eur > 0 ? f(eur) : "—");
            lv.setTextViewText(R.id.wl_time, "Actualizado: " + t);
            lv.setOnClickPendingIntent(R.id.wl_root, pi);
            awm.updateAppWidget(new ComponentName(ctx, WidgetLarge.class), lv);
        } catch (Exception ignored) { }
    }

    private static String f(double v) {
        return String.format(new Locale("es", "VE"), "%,.2f", v);
    }
}
