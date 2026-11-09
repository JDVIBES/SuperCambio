package com.supercambio.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    public void onReceive(Context ctx, Intent i) {
        boolean on = ctx.getSharedPreferences("supercambio", Context.MODE_PRIVATE)
                .getBoolean("daily_notif", false);
        if (on) RateAlarm.schedule(ctx, true);
    }
}
