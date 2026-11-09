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
            long ts = p.getLong("tsUsd", 0);

            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_rates);
            rv.setTextViewText(R.id.w_usd, usd > 0 ? f(usd) : "—");
            rv.setTextViewText(R.id.w_par, par > 0 ? f(par) : "—");
            rv.setTextViewText(R.id.w_eur, eur > 0 ? f(eur) : "—");
            String t = ts > 0
                    ? new SimpleDateFormat("d MMM · HH:mm", new Locale("es", "VE")).format(new Date(ts))
                    : "—";
            rv.setTextViewText(R.id.w_time, "Actualizado: " + t);

            Intent it = new Intent(ctx, MainActivity.class);
            it.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(ctx, 3001, it, 0x0C000000);
            rv.setOnClickPendingIntent(R.id.widget_root, pi);

            AppWidgetManager.getInstance(ctx).updateAppWidget(new ComponentName(ctx, RateWidget.class), rv);
        } catch (Exception ignored) { }
    }

    private static String f(double v) {
        return String.format(new Locale("es", "VE"), "%,.2f", v);
    }
}
