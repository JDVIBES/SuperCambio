package com.supercambio.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootReceiver extends BroadcastReceiver {
    public void onReceive(Context ctx, Intent i) {
        SharedPreferences sp = ctx.getSharedPreferences("supercambio", Context.MODE_PRIVATE);
        if (sp.getBoolean("daily_notif", false)) RateAlarm.schedule(ctx, true);
        if (sp.getBoolean("watch_on", false)) RateAlarm.scheduleWatch(ctx);
    }
}
