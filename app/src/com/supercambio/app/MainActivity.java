package com.supercambio.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends Activity {

    private static final String PREFS = "supercambio";
    private static final String SYNC_KEY = "sc7a9f2d4c8e6b1a3";
    private static final String SYNC_READ = "https://textdb.online/" + SYNC_KEY + "/data.txt";
    private static final String SYNC_WRITE = "https://textdb.online/update";
    private boolean syncBusy = false;
    private long lastSyncTry = 0;
    private static final String API_USD = "https://ve.dolarapi.com/v1/dolares";
    private static final String API_EUR = "https://ve.dolarapi.com/v1/euros";
    private static final String API_USDT = "https://criptoya.com/api/USDT/VES/1";
    private static final int MAX_POINTS = 500;

    private Handler ui;
    private Runnable nextTick;
    private boolean nextExpanded = false;

    private int themeMode = 0; // 0=sistema 1=claro 2=oscuro
    private boolean blurOn = false;
    private boolean darkLogo = false;
    private boolean hapticsOn = true;
    private boolean dailyNotif = false;

    private double bcvUsd, bcvEur, usdt, paralelo;
    private double prevUsd, prevEur, prevUsdt, prevPar;
    private long tUsd, tEur, tUsdt, tPar;

    private JSONArray histUsd, histEur, histUsdt, histPar;

    private FrameLayout pager;
    private FrameLayout rootF;
    private LinearLayout[] tabBtns;
    private ImageView[] tabIcons;
    private TextView[] tabLabels;
    private int currentTab = -1;

    private FrameLayout floatingBar;
    private ImageView blurBg;
    private ImageView glassSheen;
    private ImageView glassBorder;

    private PageRates pageRates;
    private PageCharts pageCharts;
    private PageConv pageConv;
    private PageProfiles pageProfiles;
    private View aboutPage;
    private ImageView aboutLogo;

    private DecelerateInterpolator decel;
    private OvershootInterpolator pop;
    private boolean blurDirty = false;
    private Bitmap stripBuf;
    private Bitmap lastBlurBitmap;
    private boolean blurPending = false;
    private boolean tickerOn = false;
    private final Runnable blurTick = new Runnable() {
        public void run() {
            if (!blurOn) { tickerOn = false; return; }
            if (blurDirty) {
                blurDirty = false;
                doBlur();
            }
            ui.postDelayed(this, 190);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initFonts();
        ui = new Handler(Looper.getMainLooper());
        decel = new DecelerateInterpolator(1.4f);
        pop = new OvershootInterpolator();

        SharedPreferences p = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        themeMode = p.getInt("theme_mode", 0);
        blurOn = p.getBoolean("blur_on", false);
        darkLogo = p.getBoolean("dark_logo", false);
        hapticsOn = p.getBoolean("haptics_on", true);
        dailyNotif = p.getBoolean("daily_notif", false);

        // Pantallas de alta frecuencia (hasta 120 Hz, mejor esfuerzo)
        try {
            Window w = getWindow();
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.preferredRefreshRate = 120f;
            w.setAttributes(lp);
        } catch (Exception ignored) { }

        loadPrefs();
        buildUi();
        selectTab(0);
        updateAllPages();
        if (dailyNotif) RateAlarm.schedule(this, true);
        refresh();
        syncSharedHist(false);
    }

    private boolean isDark() {
        if (themeMode == 2) return true;
        if (themeMode == 1) return false;
        int m = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return m == Configuration.UI_MODE_NIGHT_YES;
    }

    private int col(String key) {
        boolean dark = isDark();
        if (key.equals("page_bg")) return resColor(dark ? R.color.dark_page_bg : R.color.light_page_bg);
        if (key.equals("card_bg")) return resColor(dark ? R.color.dark_card_bg : R.color.light_card_bg);
        if (key.equals("text_primary")) return resColor(dark ? R.color.dark_text_primary : R.color.light_text_primary);
        if (key.equals("text_secondary")) return resColor(dark ? R.color.dark_text_secondary : R.color.light_text_secondary);
        if (key.equals("accent")) return resColor(dark ? R.color.dark_accent : R.color.light_accent);
        if (key.equals("green")) return resColor(dark ? R.color.dark_green : R.color.light_green);
        if (key.equals("orange")) return resColor(dark ? R.color.dark_orange : R.color.light_orange);
        if (key.equals("purple")) return resColor(dark ? R.color.dark_purple : R.color.light_purple);
        if (key.equals("red")) return resColor(dark ? R.color.dark_red : R.color.light_red);
        if (key.equals("separator")) return resColor(dark ? R.color.dark_separator : R.color.light_separator);
        if (key.equals("input_bg")) return resColor(dark ? R.color.dark_input_bg : R.color.light_input_bg);
        return Color.GRAY;
    }

    private int resColor(int id) {
        if (Build.VERSION.SDK_INT >= 23) return getResources().getColor(id, getTheme());
        return getResources().getColor(id);
    }

    private void applyStatusBar() {
        try {
            Window w = getWindow();
            w.setStatusBarColor(col("page_bg"));
            w.setNavigationBarColor(col("page_bg"));
            View deco = w.getDecorView();
            int flags = deco.getSystemUiVisibility();
            if (!isDark()) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            deco.setSystemUiVisibility(flags);
        } catch (Exception ignored) { }
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private GradientDrawable rounded(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private static Typeface TF_REG, TF_MED, TF_BOLD, TF_EMOJI;

    private void initFonts() {
        try {
            TF_REG = Typeface.createFromAsset(getAssets(), "fonts/Inter-Regular.ttf");
            TF_MED = Typeface.createFromAsset(getAssets(), "fonts/Inter-Medium.ttf");
            TF_BOLD = Typeface.createFromAsset(getAssets(), "fonts/Inter-Bold.ttf");
        } catch (Exception e) {
            TF_REG = Typeface.create("sans-serif", Typeface.NORMAL);
            TF_MED = Typeface.create("sans-serif", Typeface.NORMAL);
            TF_BOLD = Typeface.create("sans-serif", Typeface.BOLD);
        }
        try {
            TF_EMOJI = Typeface.createFromAsset(getAssets(), "fonts/AppleEmoji.ttf");
        } catch (Exception e) {
            TF_EMOJI = TF_REG;
        }
    }

    private TextView tv(String text, float sizeSp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(color);
        t.setTypeface(bold ? TF_BOLD : TF_REG);
        return t;
    }

    private TextView tvIos(String text, float sizeSp, boolean bold, int color) {
        TextView t = tv(text, sizeSp, bold, color);
        t.setTypeface(TF_EMOJI);
        return t;
    }

    private TextView bigTitle(String text) {
        TextView t = tv(text, 34, true, col("text_primary"));
        t.setPadding(0, dp(8), 0, dp(2));
        return t;
    }

    private View pressScale(View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).setInterpolator(decel).start();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(160).setInterpolator(decel).start();
                        break;
                }
                return false;
            }
        });
        return v;
    }

    // ================= PREFERENCIAS E HISTORIAL =================

    private void loadPrefs() {
        SharedPreferences p = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        bcvUsd = Double.longBitsToDouble(p.getLong("usd", 0));
        bcvEur = Double.longBitsToDouble(p.getLong("eur", 0));
        usdt = Double.longBitsToDouble(p.getLong("usdt", 0));
        paralelo = Double.longBitsToDouble(p.getLong("par", 0));
        prevUsd = Double.longBitsToDouble(p.getLong("prevUsd", 0));
        prevEur = Double.longBitsToDouble(p.getLong("prevEur", 0));
        prevUsdt = Double.longBitsToDouble(p.getLong("prevUsdt", 0));
        prevPar = Double.longBitsToDouble(p.getLong("prevPar", 0));
        tUsd = p.getLong("tsUsd", 0);
        tEur = p.getLong("tsEur", 0);
        tUsdt = p.getLong("tsUsdt", 0);
        tPar = p.getLong("tsPar", 0);
        try {
            histUsd = new JSONArray(p.getString("hist_usd", "[]"));
            histEur = new JSONArray(p.getString("hist_eur", "[]"));
            histUsdt = new JSONArray(p.getString("hist_usdt", "[]"));
            histPar = new JSONArray(p.getString("hist_par", "[]"));
        } catch (Exception e) {
            histUsd = new JSONArray();
            histEur = new JSONArray();
            histUsdt = new JSONArray();
            histPar = new JSONArray();
        }
    }

    private void saveAll() {
        SharedPreferences p = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor e = p.edit();
        e.putInt("theme_mode", themeMode);
        e.putBoolean("blur_on", blurOn);
        e.putBoolean("dark_logo", darkLogo);
        e.putBoolean("haptics_on", hapticsOn);
        e.putBoolean("daily_notif", dailyNotif);
        e.putLong("usd", Double.doubleToRawLongBits(bcvUsd));
        e.putLong("eur", Double.doubleToRawLongBits(bcvEur));
        e.putLong("usdt", Double.doubleToRawLongBits(usdt));
        e.putLong("par", Double.doubleToRawLongBits(paralelo));
        e.putLong("prevUsd", Double.doubleToRawLongBits(prevUsd));
        e.putLong("prevEur", Double.doubleToRawLongBits(prevEur));
        e.putLong("prevUsdt", Double.doubleToRawLongBits(prevUsdt));
        e.putLong("prevPar", Double.doubleToRawLongBits(prevPar));
        e.putLong("tsUsd", tUsd);
        e.putLong("tsEur", tEur);
        e.putLong("tsUsdt", tUsdt);
        e.putLong("tsPar", tPar);
        try {
            e.putString("hist_usd", histUsd.toString());
            e.putString("hist_eur", histEur.toString());
            e.putString("hist_usdt", histUsdt.toString());
            e.putString("hist_par", histPar.toString());
        } catch (Exception ignored) { }
        SimpleDateFormat f = new SimpleDateFormat("d MMM, HH:mm", new Locale("es"));
        e.putString("ts", f.format(new Date()));
        e.apply();
    }

    private void appendHist(JSONArray arr, double v, long t) {
        try {
            JSONObject o = new JSONObject();
            o.put("t", t);
            o.put("v", v);
            arr.put(o);
            while (arr.length() > MAX_POINTS) arr.remove(0);
        } catch (Exception ignored) { }
    }

    private void seedHistIfEmpty(JSONArray arr, double v, long t) {
        if (v > 0 && arr.length() == 0) appendHist(arr, v, t);
    }

    private long tsOf(JSONObject o) {
        String s = o.optString("fechaActualizacion", "");
        if (s.length() == 0) return 0;
        try {
            SimpleDateFormat p = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", new Locale("es"));
            p.setTimeZone(TimeZone.getTimeZone("America/Caracas"));
            Date d = p.parse(s.substring(0, Math.min(19, s.length())));
            return d != null ? d.getTime() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // ================= PRÓXIMA TASA BCV =================

    private long computeNextPublish(long lastTs) {
        if (lastTs <= 0) return 0;
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("America/Caracas"));
        c.setTimeInMillis(lastTs);
        c.set(Calendar.HOUR_OF_DAY, 15);
        c.set(Calendar.MINUTE, 30);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        long now = System.currentTimeMillis();
        while (true) {
            int dow = c.get(Calendar.DAY_OF_WEEK);
            if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) {
                c.add(Calendar.DAY_OF_MONTH, 1);
                continue;
            }
            if (c.getTimeInMillis() <= now) {
                c.add(Calendar.DAY_OF_MONTH, 1);
                continue;
            }
            return c.getTimeInMillis();
        }
    }

    private String countdownText(long diff) {
        long d = diff / 86400000L;
        long h = (diff % 86400000L) / 3600000L;
        long m = (diff % 3600000L) / 60000L;
        long s = (diff % 60000L) / 1000L;
        if (d > 0) return String.format(Locale.US, "%dd %02dh %02dm %02ds", d, h, m, s);
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }

    // ================= UI RAÍZ (barra flotante) =================

    private void buildUi() {
        rootF = new FrameLayout(this);
        rootF.setBackgroundColor(col("page_bg"));

        pager = new FrameLayout(this);
        rootF.addView(pager, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        pageRates = new PageRates();
        pageCharts = new PageCharts();
        pageConv = new PageConv();
        pageProfiles = new PageProfiles();
        aboutPage = buildAboutPage();
        pager.addView(pageRates.root);
        pager.addView(pageCharts.root);
        pager.addView(pageConv.root);
        pager.addView(pageProfiles.root);
        pager.addView(aboutPage);

        buildFloatingBar(rootF);
        setContentView(rootF);
        applyStatusBar();
    }

    private void buildFloatingBar(FrameLayout rootF) {
        floatingBar = new FrameLayout(this);
        floatingBar.setElevation(dp(8));

        blurBg = new ImageView(this);
        blurBg.setScaleType(ImageView.ScaleType.FIT_XY);
        floatingBar.addView(blurBg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Brillo superior del vidrio (luz que entra desde arriba, estilo iOS)
        glassSheen = new ImageView(this);
        glassSheen.setScaleType(ImageView.ScaleType.FIT_XY);
        floatingBar.addView(glassSheen, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Borde hairline de vidrio
        glassBorder = new ImageView(this);
        glassBorder.setScaleType(ImageView.ScaleType.FIT_XY);
        floatingBar.addView(glassBorder, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout barContent = new LinearLayout(this);
        barContent.setOrientation(LinearLayout.HORIZONTAL);
        floatingBar.addView(barContent, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        String[] names = {"Tasas", "Gráficas", "Conversor", "Pagos", "Acerca"};
        int[] icons = {R.drawable.ic_tab_rates, R.drawable.ic_tab_charts, R.drawable.ic_tab_conv, R.drawable.ic_tab_pay, R.drawable.ic_tab_about};
        tabBtns = new LinearLayout[5];
        tabIcons = new ImageView[5];
        tabLabels = new TextView[5];

        for (int i = 0; i < 5; i++) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setClickable(true);
            final int idx = i;
            item.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { selectTab(idx); }
            });
            pressScale(item);

            ImageView ic = new ImageView(this);
            ic.setImageResource(icons[i]);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(24), dp(24));
            ilp.setMargins(0, dp(4), 0, 0);
            item.addView(ic, ilp);

            TextView lb = tv(names[i], 10, false, col("text_secondary"));
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.setMargins(0, dp(2), 0, dp(6));
            item.addView(lb, llp);

            tabBtns[i] = item;
            tabIcons[i] = ic;
            tabLabels[i] = lb;
            barContent.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }

        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(62));
        blp.setMargins(dp(16), 0, dp(16), dp(14));
        blp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        rootF.addView(floatingBar, blp);

        applyBarBackground();
    }

    private void applyBarBackground() {
        if (blurOn) {
            int glass = isDark() ? Color.argb(200, 28, 28, 30) : Color.argb(200, 255, 255, 255);
            floatingBar.setBackground(rounded(glass, 31));
            floatingBar.setClipToOutline(true);
            blurBg.setVisibility(View.VISIBLE);
            scheduleBlur();

            // Brillo superior (sheen): luz difusa entrando desde arriba
            GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{isDark() ? 0x1FFFFFFF : 0x30FFFFFF, 0x00FFFFFF});
            sheen.setCornerRadius(dp(31));
            glassSheen.setImageDrawable(sheen);
            glassSheen.setVisibility(View.VISIBLE);

            // Borde hairline de vidrio
            GradientDrawable border = new GradientDrawable();
            border.setShape(GradientDrawable.RECTANGLE);
            border.setCornerRadius(dp(31));
            border.setColor(Color.TRANSPARENT);
            border.setStroke(1, isDark() ? 0x2EFFFFFF : 0x59FFFFFF);
            glassBorder.setImageDrawable(border);
            glassBorder.setVisibility(View.VISIBLE);
        } else {
            floatingBar.setBackground(rounded(col("card_bg"), 31));
            floatingBar.setClipToOutline(true);
            blurBg.setVisibility(View.GONE);
            blurBg.setImageDrawable(null);
            glassSheen.setVisibility(View.GONE);
            glassBorder.setVisibility(View.GONE);
            if (lastBlurBitmap != null) { lastBlurBitmap.recycle(); lastBlurBitmap = null; }
        }
    }

    // ================= MOTOR DE DESENFOQUE (estático por eventos) =================
    // El vidrio es tan denso que no necesita seguir el scroll en vivo (como iOS,
    // que solo muestrea el contexto). Se recalcula SOLO en eventos clave:
    // cambio de pestaña, temas, datos nuevos, expandir/colapsar. Cero trabajo
    // durante el scroll = cero tirones.

    private void scheduleBlur() {
        if (!blurOn || blurPending) return;
        blurPending = true;
        pager.postDelayed(new Runnable() {
            public void run() { blurPending = false; doBlur(); }
        }, 90);
    }

    // Durante el scroll: marca pendiente y deja que el ticker capture a
    // ritmo fijo (~5 fps). Sin bitmap churn, sin capturas por frame.
    private void nudgeBlur() {
        if (!blurOn) return;
        blurDirty = true;
        if (!tickerOn) {
            tickerOn = true;
            ui.postDelayed(blurTick, 60);
        }
    }

    private void doBlur() {
        if (!blurOn || floatingBar == null || pager == null) return;
        try {
            int pw = pager.getWidth(), ph = pager.getHeight();
            int bw = floatingBar.getWidth(), bh = floatingBar.getHeight();
            if (pw == 0 || ph == 0 || bw == 0 || bh == 0) return;

            int[] bloc = new int[2];
            int[] ploc = new int[2];
            floatingBar.getLocationOnScreen(bloc);
            pager.getLocationOnScreen(ploc);
            int bx = bloc[0] - ploc[0];
            int by = bloc[1] - ploc[1];

            // 1) Capturar solo la franja, a 35% de resolución
            float scale = 0.35f;
            int sw = Math.max(2, Math.round(bw * scale));
            int sh = Math.max(2, Math.round(bh * scale));
            if (stripBuf == null || stripBuf.getWidth() != sw || stripBuf.getHeight() != sh) {
                if (stripBuf != null) stripBuf.recycle();
                stripBuf = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
            }
            stripBuf.eraseColor(Color.TRANSPARENT);
            Canvas c = new Canvas(stripBuf);
            c.scale(scale, scale);
            c.translate(-bx, -by);
            pager.draw(c);

            // 2) Cadena de reducciones (blur acumulativo)
            Bitmap cur = stripBuf;
            for (int i = 0; i < 2; i++) {
                int nw = Math.max(2, cur.getWidth() / 2);
                int nh = Math.max(2, cur.getHeight() / 2);
                cur = Bitmap.createScaledBitmap(cur, nw, nh, true);
            }

            // 3) Box blur más fuerte (radio 4, 2 pasadas) sobre ~1 KB
            boxBlur(cur, 4, 2);

            // 4) La GPU estira el resultado con filtrado bilineal
            if (lastBlurBitmap != null && lastBlurBitmap != cur) lastBlurBitmap.recycle();
            blurBg.setImageBitmap(cur);
            blurBg.invalidate();
            lastBlurBitmap = cur;
        } catch (Throwable t) {
            blurOn = false;
            applyBarBackground();
        }
    }

    private void blurAfterLayout() {
        scheduleBlur();
        pager.postDelayed(new Runnable() {
            public void run() { scheduleBlur(); }
        }, 320);
    }

    private void boxBlur(Bitmap b, int r, int passes) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        int[] tmp = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int p = 0; p < passes; p++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int ar = 0, ag = 0, ab = 0, aa = 0, n = 0;
                    for (int k = -r; k <= r; k++) {
                        int xx = x + k;
                        if (xx < 0 || xx >= w) continue;
                        int cpx = px[y * w + xx];
                        aa += Color.alpha(cpx); ar += Color.red(cpx); ag += Color.green(cpx); ab += Color.blue(cpx);
                        n++;
                    }
                    tmp[y * w + x] = Color.argb(aa / n, ar / n, ag / n, ab / n);
                }
            }
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int ar = 0, ag = 0, ab = 0, aa = 0, n = 0;
                    for (int k = -r; k <= r; k++) {
                        int yy = y + k;
                        if (yy < 0 || yy >= h) continue;
                        int cpx = tmp[yy * w + x];
                        aa += Color.alpha(cpx); ar += Color.red(cpx); ag += Color.green(cpx); ab += Color.blue(cpx);
                        n++;
                    }
                    px[y * w + x] = Color.argb(aa / n, ar / n, ag / n, ab / n);
                }
            }
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
    }

    // ================= PAGOS (perfiles) =================

    // key | nombre | insignia | color | campos
    private static final String[][] PAY_TYPES = {
        {"pmov", "Pago móvil", "PM", "accent", "Banco\u0001Teléfono\u0001Cédula\u0001Titular"},
        {"bank", "Cuenta de banco", "CB", "purple", "Banco\u0001Número de cuenta\u0001Tipo\u0001Titular\u0001Cédula"},
        {"bin", "Binance", "BN", "orange", "Binance ID / Pay ID\u0001Red\u0001Titular"},
        {"zel", "Zelle", "ZL", "green", "Correo o teléfono\u0001Titular"}
    };

    private int typeIndexOf(String key) {
        for (int i = 0; i < PAY_TYPES.length; i++) {
            if (PAY_TYPES[i][0].equals(key)) return i;
        }
        return 0;
    }

    private JSONArray loadProfiles() {
        try {
            return new JSONArray(getSharedPreferences(PREFS, MODE_PRIVATE).getString("pay_profiles", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void saveProfiles(JSONArray a) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("pay_profiles", a.toString()).apply();
        if (pageProfiles != null) pageProfiles.refresh();
    }

    private Dialog curSheet;

    private LinearLayout sheet(String title) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        curSheet = d;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(col("card_bg"), 22));
        int p = dp(20);
        card.setPadding(p, p, p, p);
        TextView t = tv(title, 18, true, col("text_primary"));
        card.addView(t);
        card.addView(tv("", 1, false, col("text_secondary")), new LinearLayout.LayoutParams(1, dp(6)));
        d.setContentView(card, new ViewGroup.LayoutParams(dp(320), ViewGroup.LayoutParams.WRAP_CONTENT));
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.45f;
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        d.show();
        return card;
    }

    private void closeSheet() {
        if (curSheet != null) {
            try { curSheet.dismiss(); } catch (Exception ignored) {}
            curSheet = null;
        }
    }

    private EditText etField(LinearLayout parent, String label, String val) {
        TextView lb = tv(label, 11, true, col("text_secondary"));
        lb.setAllCaps(true);
        parent.addView(lb);
        EditText e = new EditText(this);
        e.setText(val);
        e.setSingleLine(true);
        e.setTextColor(col("text_primary"));
        e.setHintTextColor(col("text_secondary"));
        e.setTextSize(14);
        e.setBackground(rounded(col("input_bg"), 12));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(10));
        parent.addView(e, lp);
        return e;
    }

    private void sheetActions(final LinearLayout c, final int t, final int profIdx) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (profIdx >= 0) {
            TextView del = tv("Eliminar", 14, true, col("red"));
            del.setPadding(0, dp(14), dp(16), dp(4));
            del.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { closeSheet(); confirmDelete(profIdx); }
            });
            row.addView(del);
        }
        View sp = new View(this);
        row.addView(sp, new LinearLayout.LayoutParams(0, 1, 1));
        TextView save = tv("Guardar", 14, true, col("accent"));
        save.setPadding(dp(16), dp(14), 0, dp(4));
        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String[] fields = PAY_TYPES[t][4].split("\u0001");
                EditText[] ins = new EditText[fields.length];
                int n = 0;
                for (int i = c.getChildCount() - 1; i >= 0 && n < fields.length; i--) {
                    View ch = c.getChildAt(i);
                    if (ch instanceof EditText) ins[fields.length - 1 - n++] = (EditText) ch;
                }
                try {
                    JSONObject nv = new JSONObject();
                    for (int i = 0; i < fields.length; i++) nv.put(fields[i], ins[i].getText().toString().trim());
                    JSONObject prof = new JSONObject();
                    prof.put("t", PAY_TYPES[t][0]);
                    prof.put("v", nv);
                    JSONArray a = loadProfiles();
                    if (profIdx >= 0 && profIdx < a.length()) a.put(profIdx, prof);
                    else a.put(prof);
                    saveProfiles(a);
                    closeSheet();
                    Toast.makeText(MainActivity.this, "Perfil guardado", Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {}
            }
        });
        row.addView(save);
        c.addView(row);
    }

    private void showEdit(final int t, final int profIdx) {
        String[] fields = PAY_TYPES[t][4].split("\u0001");
        JSONArray arr = loadProfiles();
        JSONObject v = new JSONObject();
        if (profIdx >= 0 && profIdx < arr.length()) {
            JSONObject ex = arr.optJSONObject(profIdx);
            if (ex != null) {
                JSONObject tmp = ex.optJSONObject("v");
                if (tmp != null) v = tmp;
            }
        }
        final LinearLayout c = sheet((profIdx >= 0 ? "Editar · " : "Nuevo · ") + PAY_TYPES[t][1]);
        for (int i = 0; i < fields.length; i++) etField(c, fields[i], v.optString(fields[i], ""));
        sheetActions(c, t, profIdx);
    }

    private void confirmDelete(final int idx) {
        final LinearLayout c = sheet("Eliminar perfil");
        c.addView(tv("¿Seguro que quieres eliminar este perfil?", 14, false, col("text_secondary")));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.END);
        TextView no = tv("Cancelar", 14, true, col("text_secondary"));
        no.setPadding(dp(16), dp(14), dp(16), dp(4));
        no.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { closeSheet(); }
        });
        TextView yes = tv("Eliminar", 14, true, col("red"));
        yes.setPadding(dp(16), dp(14), 0, dp(4));
        yes.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                JSONArray a = loadProfiles();
                JSONArray b = new JSONArray();
                for (int i = 0; i < a.length(); i++) if (i != idx) b.put(a.opt(i));
                saveProfiles(b);
                closeSheet();
                Toast.makeText(MainActivity.this, "Perfil eliminado", Toast.LENGTH_SHORT).show();
            }
        });
        row.addView(no);
        row.addView(yes);
        c.addView(row);
    }

    private void showTypeChooser() {
        final LinearLayout c = sheet("Nuevo perfil");
        c.addView(tv("Elige el tipo de perfil", 13, false, col("text_secondary")));
        for (int i = 0; i < PAY_TYPES.length; i++) {
            final int ti = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(rounded(col("input_bg"), 14));
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            TextView b = tv(PAY_TYPES[i][2], 13, true, Color.WHITE);
            b.setGravity(Gravity.CENTER);
            b.setBackground(rounded(col(PAY_TYPES[i][3]), 10));
            row.addView(b, new LinearLayout.LayoutParams(dp(34), dp(34)));
            TextView nm = tv(PAY_TYPES[i][1], 15, true, col("text_primary"));
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            nlp.setMargins(dp(12), 0, 0, 0);
            row.addView(nm, nlp);
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { closeSheet(); showEdit(ti, -1); }
            });
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.setMargins(0, dp(8), 0, 0);
            c.addView(row, rlp);
        }
    }

    private class PageProfiles {
        ScrollView root;
        LinearLayout list;
        TextView empty;

        PageProfiles() {
            root = new ScrollView(MainActivity.this);
            root.setOverScrollMode(View.OVER_SCROLL_NEVER);
            if (Build.VERSION.SDK_INT >= 23) {
                root.setOnScrollChangeListener(new View.OnScrollChangeListener() {
                    public void onScrollChange(View v, int sx, int sy, int osx, int osy) { nudgeBlur(); }
                });
            }
            LinearLayout box = new LinearLayout(MainActivity.this);
            box.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(20);
            box.setPadding(pad, dp(8), pad, dp(110));
            root.addView(box, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            box.addView(tv("Pagos", 28, true, col("text_primary")));
            box.addView(tv("Tus perfiles de cobro, guardados solo en este teléfono.", 13, false, col("text_secondary")));

            Button addBtn = new Button(MainActivity.this);
            addBtn.setText("＋ Agregar perfil");
            addBtn.setTextColor(Color.WHITE);
            addBtn.setTextSize(15);
            addBtn.setTypeface(TF_MED);
            addBtn.setBackground(rounded(col("accent"), 999));
            addBtn.setAllCaps(false);
            addBtn.setElevation(0);
            addBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { showTypeChooser(); }
            });
            LinearLayout.LayoutParams ablp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
            ablp.setMargins(0, dp(16), 0, dp(8));
            box.addView(pressScale(addBtn), ablp);

            list = new LinearLayout(MainActivity.this);
            list.setOrientation(LinearLayout.VERTICAL);
            box.addView(list);

            empty = tvIos("💳 Aún no tienes perfiles.\nAgrega tu Pago móvil, cuenta de banco, Binance o Zelle para tenerlos a mano.", 13, false, col("text_secondary"));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(40), 0, 0);
            box.addView(empty);

            refresh();
        }

        void refresh() {
            list.removeAllViews();
            JSONArray arr = loadProfiles();
            empty.setVisibility(arr.length() == 0 ? View.VISIBLE : View.GONE);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(10), 0, 0);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) list.addView(profileCard(o, i), lp);
            }
            nudgeBlur();
        }

        View profileCard(final JSONObject o, final int idx) {
            final int t = typeIndexOf(o.optString("t", "pmov"));
            String[] fields = PAY_TYPES[t][4].split("\u0001");
            JSONObject v = o.optJSONObject("v");
            if (v == null) v = new JSONObject();

            LinearLayout card = new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(rounded(col("card_bg"), 18));
            card.setPadding(dp(16), dp(14), dp(16), dp(14));

            TextView badge = tv(PAY_TYPES[t][2], 14, true, Color.WHITE);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(rounded(col(PAY_TYPES[t][3]), 12));
            card.addView(badge, new LinearLayout.LayoutParams(dp(40), dp(40)));

            LinearLayout col = new LinearLayout(MainActivity.this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(tv(PAY_TYPES[t][1], 15, true, col("text_primary")));
            String sub = v.optString(fields[fields.length - 1], "") + " · " + v.optString(fields[0], "");
            TextView st = tv(sub, 13, false, col("text_secondary"));
            st.setSingleLine(true);
            st.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(st);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            clp.setMargins(dp(12), 0, dp(8), 0);
            card.addView(col, clp);

            // Botón compartir: genera imagen con logo y nombre de la app
            ImageView sh = new ImageView(MainActivity.this);
            sh.setImageResource(R.drawable.ic_share);
            sh.setColorFilter(col("text_secondary"), android.graphics.PorterDuff.Mode.SRC_IN);
            sh.setBackground(rounded(col("input_bg"), 20));
            sh.setPadding(dp(9), dp(9), dp(9), dp(9));
            sh.setOnClickListener(new View.OnClickListener() {
                public void onClick(View vv) { shareProfile(idx); }
            });
            card.addView(sh, new LinearLayout.LayoutParams(dp(38), dp(38)));

            card.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) { showEdit(t, idx); }
            });
            card.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View view) { confirmDelete(idx); return true; }
            });
            return pressScale(card);
        }
    }

    // ================= COMPARTIR PERFIL (imagen con marca) =================

    private void shareProfile(int idx) {
        JSONArray arr = loadProfiles();
        if (idx < 0 || idx >= arr.length()) return;
        JSONObject o = arr.optJSONObject(idx);
        if (o == null) return;
        int t = typeIndexOf(o.optString("t", "pmov"));
        String[] fields = PAY_TYPES[t][4].split("\u0001");
        JSONObject v = o.optJSONObject("v");
        if (v == null) v = new JSONObject();

        int W = 1000, pad = 56, headH = 170, rowH = 112, footH = 130;
        int H = pad + headH + fields.length * rowH + footH;

        // Paleta según el tema de la app
        boolean dk = isDark();
        int bgCol = dk ? 0xFF0E0E10 : 0xFFF2F3F7;
        int cardCol = dk ? 0xFF1C1C1E : Color.WHITE;
        int priCol = dk ? 0xFFF2F2F7 : 0xFF111114;
        int secCol = dk ? 0xFF98989F : 0xFF8E8E93;
        int sepCol = dk ? 0x24FFFFFF : 0x14000000;

        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        cv.drawColor(bgCol);

        Paint cardP = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardP.setColor(cardCol);
        cv.drawRoundRect(pad, pad, W - pad, H - pad, 44, 44, cardP);

        // Logo real de la app, recortado en círculo
        float cx = pad + 70, cy = pad + 78, r = 58;
        cv.save();
        Path clip = new Path();
        clip.addCircle(cx, cy, r, Path.Direction.CW);
        cv.clipPath(clip);
        Drawable appIc = logoDrawable();
        appIc.setBounds((int) (cx - r), (int) (cy - r), (int) (cx + r), (int) (cy + r));
        appIc.draw(cv);
        cv.restore();

        // Nombre de la app + tipo de perfil
        float tx = cx + 96;
        Paint titleP = new Paint(Paint.ANTI_ALIAS_FLAG);
        titleP.setColor(priCol);
        titleP.setTextSize(52);
        titleP.setFakeBoldText(true);
        cv.drawText("Super Cambio", tx, pad + 62, titleP);
        Paint typeP = new Paint(Paint.ANTI_ALIAS_FLAG);
        typeP.setColor(secCol);
        typeP.setTextSize(36);
        cv.drawText(PAY_TYPES[t][1], tx, pad + 112, typeP);

        // Separador
        Paint sepP = new Paint();
        sepP.setColor(sepCol);
        cv.drawLine(pad + 40, pad + headH, W - pad - 40, pad + headH, sepP);

        // Filas de datos
        float y = pad + headH + 34;
        Paint lbP = new Paint(Paint.ANTI_ALIAS_FLAG);
        lbP.setColor(secCol);
        lbP.setTextSize(30);
        TextPaint vaP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        vaP.setColor(priCol);
        vaP.setTextSize(46);
        vaP.setFakeBoldText(true);
        float avail = W - 2 * pad - 60;
        for (int i = 0; i < fields.length; i++) {
            cv.drawText(fields[i], pad + 40, y, lbP);
            String val = v.optString(fields[i], "").isEmpty() ? "—" : v.optString(fields[i], "");
            CharSequence ell = TextUtils.ellipsize(val, vaP, avail, TextUtils.TruncateAt.END);
            cv.drawText(ell.toString(), pad + 40, y + 52, vaP);
            y += rowH;
        }

        // Pie con marca
        Paint footP = new Paint(Paint.ANTI_ALIAS_FLAG);
        footP.setColor(secCol);
        footP.setTextSize(32);
        footP.setTextAlign(Paint.Align.CENTER);
        cv.drawText("Generado con Super Cambio · © 2026 Jdvibes", W / 2f, H - pad - 36, footP);

        try {
            File f = new File(getCacheDir(), "compartir.png");
            FileOutputStream fo = new FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fo);
            fo.close();
            bmp.recycle();

            StringBuilder txt = new StringBuilder(PAY_TYPES[t][1]).append("\n");
            for (int i = 0; i < fields.length; i++) {
                String val = v.optString(fields[i], "");
                if (!val.isEmpty()) txt.append(fields[i]).append(": ").append(val).append("\n");
            }
            txt.append("\nEnviado con Super Cambio");

            Uri uri = Uri.parse("content://" + ShareProvider.AUTHORITY + "/compartir.png");
            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType("image/png");
            it.putExtra(Intent.EXTRA_STREAM, uri);
            it.putExtra(Intent.EXTRA_TEXT, txt.toString());
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(it, "Compartir perfil"));
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo generar la imagen", Toast.LENGTH_SHORT).show();
        }
    }

    // ================= COMPARTIR TASAS (imagen con marca) =================

    private void shareRates() {
        if (bcvUsd <= 0 && paralelo <= 0 && bcvEur <= 0 && usdt <= 0) {
            Toast.makeText(this, "Aún no hay datos para compartir", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean dk = isDark();
        int bgCol = dk ? 0xFF0E0E10 : 0xFFF2F3F7;
        int cardCol = dk ? 0xFF1C1C1E : Color.WHITE;
        int priCol = dk ? 0xFFF2F2F7 : 0xFF111114;
        int secCol = dk ? 0xFF98989F : 0xFF8E8E93;

        String[][] rows = new String[4][5]; // nombre | valor | color | badge | fuente
        rows[0] = new String[]{"Dólar BCV", fmt(bcvUsd) + " Bs.", "accent", "USD", "BCV"};
        rows[1] = new String[]{"Dólar paralelo", fmt(paralelo) + " Bs.", "green", "USD", "Promedio"};
        rows[2] = new String[]{"Euro BCV", fmt(bcvEur) + " Bs.", "purple", "EUR", "BCV"};
        rows[3] = new String[]{"USDT Binance", fmt(usdt) + " Bs.", "orange", "USDT", "P2P"};

        int W = 1000, pad = 56, headH = 200, rowH = 122, footH = 130;
        int H = pad + headH + rowH * 4 + footH;

        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        cv.drawColor(bgCol);

        Paint cardP = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardP.setColor(cardCol);
        cv.drawRoundRect(pad, pad, W - pad, H - pad, 44, 44, cardP);

        // Logo real, esquinas redondeadas iOS
        float cx = pad + 70, cy = pad + 78, r = 58;
        cv.save();
        Path clip = new Path();
        float rad = r * 0.4474f;
        clip.addRoundRect(new RectF(cx - r, cy - r, cx + r, cy + r), rad, rad, Path.Direction.CW);
        cv.clipPath(clip);
        Drawable appIc = logoDrawable();
        appIc.setBounds((int) (cx - r), (int) (cy - r), (int) (cx + r), (int) (cy + r));
        appIc.draw(cv);
        cv.restore();

        float tx = cx + 96;
        Paint titleP = new Paint(Paint.ANTI_ALIAS_FLAG);
        titleP.setColor(priCol);
        titleP.setTextSize(52);
        titleP.setFakeBoldText(true);
        cv.drawText("Super Cambio", tx, pad + 62, titleP);
        Paint typeP = new Paint(Paint.ANTI_ALIAS_FLAG);
        typeP.setColor(secCol);
        typeP.setTextSize(36);
        SimpleDateFormat df = new SimpleDateFormat("d 'de' MMM, yyyy", new Locale("es"));
        cv.drawText("Tasas de hoy · " + df.format(new Date()), tx, pad + 112, typeP);

        Paint sepP = new Paint();
        sepP.setColor(dk ? 0x24FFFFFF : 0x14000000);
        cv.drawLine(pad + 40, pad + headH, W - pad - 40, pad + headH, sepP);

        Paint nmP = new Paint(Paint.ANTI_ALIAS_FLAG);
        nmP.setColor(priCol);
        nmP.setTextSize(38);
        nmP.setFakeBoldText(true);
        Paint vaP = new Paint(Paint.ANTI_ALIAS_FLAG);
        vaP.setColor(priCol);
        vaP.setTextSize(46);
        vaP.setFakeBoldText(true);
        vaP.setTextAlign(Paint.Align.RIGHT);
        Paint srcP = new Paint(Paint.ANTI_ALIAS_FLAG);
        srcP.setColor(secCol);
        srcP.setTextSize(28);
        Paint bgP = new Paint(Paint.ANTI_ALIAS_FLAG);

        float y = pad + headH + 46;
        for (int i = 0; i < 4; i++) {
            // Insignia de color
            GradientDrawable bd = new GradientDrawable();
            bd.setShape(GradientDrawable.RECTANGLE);
            bd.setCornerRadius(dp(11));
            int c = col(rows[i][2]);
            bd.setColor(Color.argb(46, Color.red(c), Color.green(c), Color.blue(c)));
            float bx = pad + 36, by = y - 14;
            bd.setBounds((int) bx, (int) by, (int) (bx + 96), (int) (by + 96));
            bd.draw(cv);
            Paint bt = new Paint(Paint.ANTI_ALIAS_FLAG);
            bt.setColor(c);
            bt.setTextSize(30);
            bt.setFakeBoldText(true);
            bt.setTextAlign(Paint.Align.CENTER);
            cv.drawText(rows[i][3], bx + 48, by + 60, bt);

            cv.drawText(rows[i][0], pad + 160, y + 22, nmP);
            cv.drawText(rows[i][4], pad + 160, y + 62, srcP);
            cv.drawText(rows[i][1], W - pad - 44, y + 30, vaP);
            if (i < 3) cv.drawLine(pad + 40, y + 92, W - pad - 40, y + 92, sepP);
            y += rowH;
        }

        Paint footP = new Paint(Paint.ANTI_ALIAS_FLAG);
        footP.setColor(secCol);
        footP.setTextSize(32);
        footP.setTextAlign(Paint.Align.CENTER);
        cv.drawText("Generado con Super Cambio · © 2026 Jdvibes", W / 2f, H - pad - 36, footP);

        try {
            File f = new File(getCacheDir(), "tasas.png");
            FileOutputStream fo = new FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fo);
            fo.close();
            bmp.recycle();

            StringBuilder txt = new StringBuilder("Tasas de hoy en Venezuela\n");
            for (int i = 0; i < 4; i++) txt.append(rows[i][0]).append(": ").append(rows[i][1]).append("\n");
            txt.append("\nEnviado con Super Cambio");

            Uri uri = Uri.parse("content://" + ShareProvider.AUTHORITY + "/tasas.png");
            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType("image/png");
            it.putExtra(Intent.EXTRA_STREAM, uri);
            it.putExtra(Intent.EXTRA_TEXT, txt.toString());
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(it, "Compartir tasas"));
        } catch (Exception e) {
            Toast.makeText(this, "No se pudo generar la imagen", Toast.LENGTH_SHORT).show();
        }
    }

    // ================= TABS =================

    private void selectTab(int i) {
        if (i == currentTab) return;
        currentTab = i;
        haptic(pager.getChildAt(i));
        for (int k = 0; k < 5; k++) {
            pager.getChildAt(k).setVisibility(k == i ? View.VISIBLE : View.GONE);
            boolean on = (k == i);
            int c = on ? col("accent") : col("text_secondary");
            tabIcons[k].setColorFilter(c, android.graphics.PorterDuff.Mode.SRC_IN);
            tabLabels[k].setTextColor(c);
            tabLabels[k].setTypeface(on ? TF_BOLD : TF_REG);
        }
        View pg = pager.getChildAt(i);
        pg.setAlpha(0f);
        pg.setTranslationY(dp(14));
        pg.animate().alpha(1f).translationY(0f).setDuration(260).setInterpolator(decel).start();
        if (i == 1 && pageCharts != null) pageCharts.redraw();
        blurAfterLayout();
    }

    private void updateAllPages() {
        pageRates.apply();
        pageCharts.redraw();
        pageConv.recompute();
        if (pageProfiles != null) pageProfiles.refresh();
        RateWidget.push(this);
        scheduleBlur();
    }

    // ================= PÁGINA TASAS =================

    private class RateCard {
        LinearLayout card;
        TextView value, date, pill;

        RateCard(LinearLayout parent, String title, String source, String badge, final String colorKey) {
            card = new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(rounded(col("card_bg"), 18));
            card.setPadding(dp(18), dp(16), dp(18), dp(18));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(12), 0, 0);
            parent.addView(pressScale(card), lp);

            LinearLayout head = new LinearLayout(MainActivity.this);
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            card.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView b = tv(badge, 18, true, Color.WHITE);
            b.setGravity(Gravity.CENTER);
            b.setBackground(rounded(col(colorKey), 12));
            head.addView(b, new LinearLayout.LayoutParams(dp(40), dp(40)));

            LinearLayout titleCol = new LinearLayout(MainActivity.this);
            titleCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tclp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            tclp.setMargins(dp(12), 0, dp(8), 0);
            head.addView(titleCol, tclp);

            titleCol.addView(tv(title, 17, true, col("text_primary")));
            titleCol.addView(tv(source, 13, false, col("text_secondary")));

            pill = tv("", 12, true, Color.WHITE);
            pill.setGravity(Gravity.CENTER);
            pill.setSingleLine(true);
            pill.setIncludeFontPadding(false);
            pill.setPadding(dp(11), 0, dp(11), 0);
            pill.setVisibility(View.GONE);
            head.addView(pill, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26)));

            value = tv("—", 40, true, col("text_primary"));
            value.setPadding(0, dp(10), 0, 0);
            card.addView(value, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            date = tv("", 13, false, col("text_secondary"));
            card.addView(date, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        private void setTextAnimated(TextView t, String s) {
            boolean changed = !t.getText().toString().equals(s);
            t.setText(s);
            if (changed) {
                t.setAlpha(0.2f);
                t.setTranslationY(dp(5));
                t.animate().alpha(1f).translationY(0f).setDuration(300).setInterpolator(decel).start();
            }
        }

        void setValueAnimated(String s) {
            setTextAnimated(value, s);
        }

        void setPill(double prevV, double v) {
            if (prevV <= 0 || v <= 0) {
                if (pill.getVisibility() == View.VISIBLE) {
                    pill.animate().alpha(0f).setDuration(180).withEndAction(
                            new Runnable() { public void run() { pill.setVisibility(View.GONE); pill.setAlpha(1f); } }
                    ).start();
                }
                return;
            }
            double pct = (v - prevV) / prevV * 100.0;
            if (Math.abs(pct) < 0.0001) pct = 0;
            boolean up = pct >= 0;
            int c = up ? col("green") : col("red");
            GradientDrawable pg = rounded(Color.argb(38, Color.red(c), Color.green(c), Color.blue(c)), 13);
            pill.setBackground(pg);
            pill.setTextColor(c);
            String fmt = Math.abs(pct) >= 1000 ? "%.0f %%" : "%.1f %%";
            pill.setText((up ? "▲ " : "▼ ") + String.format(new Locale("es", "VE"), fmt, Math.abs(pct)));
            boolean wasGone = pill.getVisibility() != View.VISIBLE;
            pill.setVisibility(View.VISIBLE);
            if (wasGone) {
                pill.setScaleX(0.5f);
                pill.setScaleY(0.5f);
                pill.animate().scaleX(1f).scaleY(1f).setDuration(340).setInterpolator(pop).start();
            }
        }
    }

    private class PageRates {
        ScrollView root;
        TextView status;
        RateCard usd, eur, usdtc, parc;

        LinearLayout nextBox;
        TextView nextCountdown, nextStatus, datePill;
        Button nextBtn, dateAct;
        String netErr;
        boolean autoBusy = false;
        long lastAutoTry = 0;
        long celebrateUntil = 0;

        PageRates() {
            root = new ScrollView(MainActivity.this);
            root.setOverScrollMode(View.OVER_SCROLL_NEVER);
            if (Build.VERSION.SDK_INT >= 23) {
                root.setOnScrollChangeListener(new View.OnScrollChangeListener() {
                    public void onScrollChange(View v, int sx, int sy, int osx, int osy) { nudgeBlur(); }
                });
            }
            LinearLayout box = new LinearLayout(MainActivity.this);
            box.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(20);
            box.setPadding(pad, dp(8), pad, dp(110));
            root.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            box.addView(bigTitle("Tasas"));
            box.addView(tv("Datos en vivo de Venezuela", 15, false, col("text_secondary")));

            LinearLayout statusRow = new LinearLayout(MainActivity.this);
            statusRow.setOrientation(LinearLayout.HORIZONTAL);
            statusRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams srlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            srlp.setMargins(0, dp(10), 0, 0);
            box.addView(statusRow, srlp);

            status = tvIos("⏳ Sin datos todavía", 13, false, col("text_secondary"));
            statusRow.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            // Compartir tasas como imagen con marca
            ImageView shareBtn = new ImageView(MainActivity.this);
            shareBtn.setImageResource(R.drawable.ic_share);
            shareBtn.setColorFilter(col("text_secondary"), android.graphics.PorterDuff.Mode.SRC_IN);
            shareBtn.setBackground(rounded(col("input_bg"), 20));
            shareBtn.setPadding(dp(9), dp(9), dp(9), dp(9));
            shareBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { shareRates(); }
            });
            statusRow.addView(shareBtn, new LinearLayout.LayoutParams(dp(38), dp(38)));
            ((LinearLayout.LayoutParams) shareBtn.getLayoutParams()).setMargins(0, 0, dp(10), 0);

            Button refreshBtn = new Button(MainActivity.this);
            refreshBtn.setText("Actualizar");
            refreshBtn.setAllCaps(false);
            refreshBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            refreshBtn.setTextColor(col("accent"));
            refreshBtn.setBackgroundColor(Color.TRANSPARENT);
            refreshBtn.setTypeface(TF_MED);
            refreshBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { haptic(v); refresh(); hideKeyboard(v); }
            });
            statusRow.addView(refreshBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // Pull-to-refresh (sin librerías de soporte)
            final TextView pullHint = tvIos("↓  Desliza para actualizar", 12, false, col("accent"));
            pullHint.setGravity(Gravity.CENTER);
            pullHint.setVisibility(View.GONE);
            LinearLayout.LayoutParams phlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            box.addView(pullHint, 0, phlp);
            root.setOnTouchListener(new View.OnTouchListener() {
                float downY = 0;
                float pull = 0;
                boolean armed = false;
                public boolean onTouch(View v, MotionEvent ev) {
                    int a = ev.getActionMasked();
                    if (a == MotionEvent.ACTION_DOWN) {
                        downY = ev.getY();
                        armed = root.getScrollY() <= 2;
                    } else if (a == MotionEvent.ACTION_MOVE && armed) {
                        float dy = ev.getY() - downY;
                        if (dy > dp(24)) {
                            pull = Math.min(1f, (dy - dp(24)) / dp(80));
                            pullHint.setVisibility(View.VISIBLE);
                            pullHint.setAlpha(pull);
                            pullHint.setText(pull >= 1f ? "↻  Suelta para actualizar" : "↓  Desliza para actualizar");
                        }
                    } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                        final boolean fire = armed && pull >= 1f;
                        pull = 0;
                        pullHint.animate().alpha(0f).setDuration(180).withEndAction(new Runnable() {
                            public void run() {
                                pullHint.setVisibility(View.GONE);
                                if (fire) { haptic(root); refresh(); }
                            }
                        }).start();
                    }
                    return false;
                }
            });

            usd = new RateCard(box, "Dólar oficial", "BCV", "$", "green");
            eur = new RateCard(box, "Euro oficial", "BCV", "€", "purple");
            usdtc = new RateCard(box, "USDT", "Binance P2P", "₮", "orange");
            parc = new RateCard(box, "Dólar paralelo", "Promedio de mercado", "≋", "accent");

            buildNextRate(usd.card);
        }

        private void buildNextRate(LinearLayout usdCard) {
            View sep = new View(MainActivity.this);
            sep.setBackgroundColor(col("separator"));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.7f)));
            slp.setMargins(0, dp(14), 0, 0);
            usdCard.addView(sep, slp);

            nextBtn = new Button(MainActivity.this);
            nextBtn.setText("Ver próxima tasa  ▾");
            nextBtn.setAllCaps(false);
            nextBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            nextBtn.setTextColor(col("accent"));
            nextBtn.setBackgroundColor(Color.TRANSPARENT);
            nextBtn.setGravity(Gravity.CENTER);
            nextBtn.setTypeface(TF_MED);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.setMargins(0, dp(4), 0, 0);
            usdCard.addView(nextBtn, blp);
            nextBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { toggleNext(); }
            });

            nextBox = new LinearLayout(MainActivity.this);
            nextBox.setOrientation(LinearLayout.VERTICAL);
            nextBox.setGravity(Gravity.CENTER_HORIZONTAL);
            nextBox.setVisibility(View.GONE);
            LinearLayout.LayoutParams nblp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nblp.setMargins(0, dp(10), 0, 0);
            usdCard.addView(nextBox, nblp);

            nextCountdown = tv("—", 30, true, col("text_primary"));
            nextCountdown.setGravity(Gravity.CENTER);
            nextBox.addView(nextCountdown, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            nextStatus = tvIos("⏱ Estimado según el horario habitual del BCV (3:30 PM, día hábil).",
                    12, false, col("text_secondary"));
            nextStatus.setGravity(Gravity.CENTER);
            nextStatus.setPadding(0, dp(6), 0, 0);
            nextBox.addView(nextStatus, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // Fila estilo iOS: [fecha de la próxima publicación] [acción]
            LinearLayout drow = new LinearLayout(MainActivity.this);
            drow.setOrientation(LinearLayout.HORIZONTAL);
            drow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams drlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            drlp.setMargins(0, dp(14), 0, 0);
            nextBox.addView(drow, drlp);

            datePill = tv("—", 15, true, col("accent"));
            datePill.setGravity(Gravity.CENTER);
            datePill.setBackground(rounded(col("input_bg"), 14));
            datePill.setPadding(dp(14), 0, dp(14), 0);
            datePill.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { fetchNextManual(); }
            });
            drow.addView(pressScale(datePill), new LinearLayout.LayoutParams(0, dp(48), 1));

            dateAct = new Button(MainActivity.this);
            dateAct.setText("⟳");
            dateAct.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            dateAct.setTextColor(col("text_secondary"));
            dateAct.setTypeface(TF_MED);
            dateAct.setBackground(rounded(col("input_bg"), 14));
            dateAct.setAllCaps(false);
            dateAct.setElevation(0);
            dateAct.setPadding(0, 0, 0, 0);
            dateAct.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { fetchNextManual(); }
            });
            drow.addView(pressScale(dateAct), new LinearLayout.LayoutParams(dp(48), dp(48)));
        }

        private void fetchNextManual() {
            if (autoBusy) return;
            haptic(datePill);
            netErr = null;
            datePill.setText("Consultando…");
            datePill.setTextColor(col("accent"));
            dateAct.setEnabled(false);
            dateAct.setText("…");
            autoFetchNextRate(true);
        }

        private void toggleNext() {
            if (nextBox.getVisibility() == View.GONE) {
                nextExpanded = true;
                nextBox.setVisibility(View.VISIBLE);
                nextBox.setAlpha(0f);
                nextBox.setTranslationY(-dp(8));
                nextBox.animate().alpha(1f).translationY(0f).setDuration(260).setInterpolator(decel).start();
                nextBtn.setText("Ocultar próxima tasa  ▴");
                updateCountdown();
                startTicking();
            } else {
                nextExpanded = false;
                celebrateUntil = 0;
                dateAct.setEnabled(true);
                dateAct.setText("⟳");
                nextBox.animate().alpha(0f).setDuration(200).withEndAction(
                        new Runnable() { public void run() { nextBox.setVisibility(View.GONE); nextBox.setAlpha(1f); } }
                ).start();
                nextBtn.setText("Ver próxima tasa  ▾");
                stopTicking();
            }
        }

        private void startTicking() {
            stopTicking();
            nextTick = new Runnable() {
                public void run() {
                    if (!nextExpanded) return;
                    if (System.currentTimeMillis() < celebrateUntil) {
                        ui.postDelayed(this, 1000);
                        return;
                    }
                    updateCountdown();
                    ui.postDelayed(this, 1000);
                }
            };
            ui.postDelayed(nextTick, 1000);
        }

        private void stopTicking() {
            if (nextTick != null) { ui.removeCallbacks(nextTick); nextTick = null; }
        }

        private void updateCountdown() {
            long last = tUsd;
            if (last <= 0) {
                nextCountdown.setText("—");
                nextCountdown.setTextColor(col("text_primary"));
                datePill.setText("—");
                nextStatus.setText("Actualiza para calcular la próxima publicación del BCV.");
                return;
            }
            long next = computeNextPublish(last);
            long now = System.currentTimeMillis();
            long diff = next - now;
            SimpleDateFormat df = new SimpleDateFormat("EEEE, dd/MM/yy", new Locale("es", "VE"));
            String dstr = df.format(new Date(next));
            dstr = Character.toUpperCase(dstr.charAt(0)) + dstr.substring(1);
            if (diff <= 0) {
                nextCountdown.setTextColor(col("text_primary"));
                nextCountdown.setText("¡Atento!");
                datePill.setText("👁  Ver nueva tasa");
                datePill.setTextColor(col("green"));
                nextStatus.setText("La nueva tasa puede estar vigente desde ahora. Toca para verla.");
                if (!autoBusy) autoFetchNextRate(false);
                return;
            }
            datePill.setText(dstr);
            datePill.setTextColor(col("accent"));
            dateAct.setTextColor(col("text_secondary"));
            nextCountdown.setTextColor(col("text_primary"));
            nextCountdown.setText(countdownText(diff));
            nextStatus.setText("Tiempo restante para la próxima publicación (estimado 3:30 PM, día hábil).");
        }

        private void autoFetchNextRate(boolean force) {
            long now = System.currentTimeMillis();
            if (autoBusy || (!force && now - lastAutoTry < 60000)) return;
            autoBusy = true;
            lastAutoTry = now;
            new Thread(new Runnable() {
                public void run() {
                    double newRate = 0;
                    long newTs = 0;
                    try {
                        JSONArray arr = new JSONArray(httpGet(API_USD));
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            if ("oficial".equals(o.optString("fuente", ""))) {
                                newRate = o.getDouble("promedio");
                                newTs = tsOf(o);
                            }
                        }
                    } catch (Exception e) { netErr = String.valueOf(e.getMessage()); }
                    if (newRate <= 0 || newTs <= tUsd) {
                        double bcv = fetchBcvSite();
                        long nowMs = System.currentTimeMillis();
                        if (bcv > 0 && (Math.abs(bcv - bcvUsd) > 0.00001 || computeNextPublish(tUsd) <= nowMs)) {
                            newRate = bcv;
                            newTs = nowMs;
                            netErr = null;
                        }
                    }
                    final double fRate = newRate;
                    final long fTs = newTs;
                    ui.post(new Runnable() {
                        public void run() {
                            autoBusy = false;
                            netErr = null;
                            if (!nextExpanded) return;
                            if (fRate > 0 && fTs > tUsd) {
                                prevUsd = bcvUsd;
                                bcvUsd = fRate;
                                tUsd = fTs;
                                seedHistIfEmpty(histUsd, bcvUsd, tUsd);
                                appendHist(histUsd, bcvUsd, System.currentTimeMillis());
                                saveAll();
                                updateAllPages();
                                celebrateUntil = System.currentTimeMillis() + 10000;
                                haptic(nextCountdown);
                                pageRates.nextStatus.setText("🎉 ¡Nueva tasa publicada!");
                                nextCountdown.setTextColor(col("green"));
                                nextCountdown.setText("¡Nueva tasa publicada!");
                                nextCountdown.setScaleX(1.15f);
                                nextCountdown.setScaleY(1.15f);
                                nextCountdown.animate().scaleX(1f).scaleY(1f).setDuration(450).setInterpolator(pop).start();
                                SimpleDateFormat f = new SimpleDateFormat("HH:mm", new Locale("es"));
                                nextStatus.setText("El BCV publicó Bs. " + fmt(fRate) + " · detectado a las " + f.format(new Date()) + " (hora del teléfono)");
                                dateAct.setEnabled(true);
                                dateAct.setText("⟳");
                            } else {
                                dateAct.setEnabled(true);
                                dateAct.setText("⟳");
                                if (netErr != null) {
                                    nextStatus.setText("Sin conexión con el BCV · revisando cada minuto…");
                                } else {
                                    int dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
                                    if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) {
                                        nextStatus.setText("El BCV no publica fines de semana · próxima tasa: lunes a las 3:30 PM");
                                    } else {
                                        nextStatus.setText("El BCV aún no publica la nueva tasa · revisando cada minuto…");
                                    }
                                }
                            }
                        }
                    });
                }
            }).start();
        }

        void apply() {
            usd.setValueAnimated(bcvUsd > 0 ? fmt(bcvUsd) + " Bs." : "—");
            eur.setValueAnimated(bcvEur > 0 ? fmt(bcvEur) + " Bs." : "—");
            usdtc.setValueAnimated(usdt > 0 ? fmt(usdt) + " Bs." : "—");
            parc.setValueAnimated(paralelo > 0 ? fmt(paralelo) + " Bs." : "—");

            SimpleDateFormat f = new SimpleDateFormat("d MMM · HH:mm 'VET'", new Locale("es"));
            usd.date.setText(bcvUsd > 0 ? (tUsd > 0 ? "Dato del " + f.format(new Date(tUsd)) : "1 USD = " + fmt(bcvUsd) + " Bs.") : "Esperando datos…");
            eur.date.setText(bcvEur > 0 ? (tEur > 0 ? "Dato del " + f.format(new Date(tEur)) : "1 EUR = " + fmt(bcvEur) + " Bs.") : "Esperando datos…");
            usdtc.date.setText(usdt > 0 ? (tUsdt > 0 ? "Binance P2P · " + f.format(new Date(tUsdt)) : "1 USDT = " + fmt(usdt) + " Bs.") : "Esperando datos…");
            parc.date.setText(paralelo > 0 ? (tPar > 0 ? "Dato del " + f.format(new Date(tPar)) : "1 USD = " + fmt(paralelo) + " Bs.") : "Esperando datos…");

            usd.setPill(prevUsd, bcvUsd);
            eur.setPill(prevEur, bcvEur);
            usdtc.setPill(prevUsdt, usdt);
            parc.setPill(prevPar, paralelo);

            if (nextExpanded) updateCountdown();
        }
    }

    // ================= PÁGINA GRÁFICAS =================

    private class PageCharts {
        ScrollView root;
        ChartView chart;
        LinearLayout segWrap;
        TextView chartTitle, statMin, statMax, statAvg, statVar, statCount;
        LinearLayout rangeWrap;
        int sel = 0;
        int rangeSel = 1;

        PageCharts() {
            root = new ScrollView(MainActivity.this);
            root.setOverScrollMode(View.OVER_SCROLL_NEVER);
            if (Build.VERSION.SDK_INT >= 23) {
                root.setOnScrollChangeListener(new View.OnScrollChangeListener() {
                    public void onScrollChange(View v, int sx, int sy, int osx, int osy) { nudgeBlur(); }
                });
            }
            LinearLayout box = new LinearLayout(MainActivity.this);
            box.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(20);
            box.setPadding(pad, dp(8), pad, dp(110));
            root.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            box.addView(bigTitle("Gráficas"));
            box.addView(tv("Evolución local de cada tasa", 15, false, col("text_secondary")));

            segWrap = new LinearLayout(MainActivity.this);
            segWrap.setOrientation(LinearLayout.HORIZONTAL);
            segWrap.setBackground(rounded(col("input_bg"), 10));
            segWrap.setPadding(dp(3), dp(3), dp(3), dp(3));
            LinearLayout.LayoutParams segLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
            segLp.setMargins(0, dp(16), 0, 0);
            box.addView(segWrap, segLp);
            String[] segNames = {"USD", "EUR", "USDT", "Par."};
            for (int i = 0; i < 4; i++) {
                final int idx = i;
                TextView s = tv(segNames[i], 14, true, col("text_secondary"));
                s.setGravity(Gravity.CENTER);
                s.setId(2000 + i);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
                segWrap.addView(s, slp);
                pressScale(s);
                s.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { sel = idx; redraw(); hideKeyboard(v); }
                });
            }

            // Rango de tiempo
            rangeWrap = new LinearLayout(MainActivity.this);
            rangeWrap.setOrientation(LinearLayout.HORIZONTAL);
            rangeWrap.setBackground(rounded(col("input_bg"), 10));
            rangeWrap.setPadding(dp(3), dp(3), dp(3), dp(3));
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34));
            rLp.setMargins(0, dp(10), 0, 0);
            box.addView(rangeWrap, rLp);
            String[] rNames = {"7 d", "1 m", "3 m", "Todo"};
            for (int i = 0; i < 4; i++) {
                final int ri = i;
                TextView s = tv(rNames[i], 13, true, col("text_secondary"));
                s.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
                rangeWrap.addView(s, slp);
                pressScale(s);
                s.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { rangeSel = ri; redraw(); hideKeyboard(v); }
                });
            }

            chartTitle = tv("Dólar oficial", 17, true, col("text_primary"));
            LinearLayout.LayoutParams ctlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ctlp.setMargins(0, dp(18), 0, 0);
            box.addView(chartTitle, ctlp);

            LinearLayout chartCard = new LinearLayout(MainActivity.this);
            chartCard.setOrientation(LinearLayout.VERTICAL);
            chartCard.setBackground(rounded(col("card_bg"), 18));
            int cpad = dp(14);
            chartCard.setPadding(cpad, cpad, cpad, cpad);
            LinearLayout.LayoutParams cclp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cclp.setMargins(0, dp(8), 0, 0);
            box.addView(chartCard, cclp);

            chart = new ChartView();
            chartCard.addView(chart, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));

            LinearLayout stats = new LinearLayout(MainActivity.this);
            stats.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams stlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            stlp.setMargins(0, dp(14), 0, 0);
            box.addView(stats, stlp);

            statMin = statBlock(stats, "Mínimo");
            statMax = statBlock(stats, "Máximo");
            statAvg = statBlock(stats, "Promedio");
            statVar = statBlock(stats, "Variación");

            statCount = tv("", 12, false, col("text_secondary"));
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.setMargins(0, dp(16), 0, 0);
            box.addView(statCount, nlp);
        }

        private TextView statBlock(LinearLayout parent, String label) {
            LinearLayout col = new LinearLayout(MainActivity.this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            col.setPadding(dp(4), 0, dp(4), 0);
            parent.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            col.addView(tv(label, 12, false, col("text_secondary")));
            TextView v = tv("—", 15, true, col("text_primary"));
            col.addView(v);
            return v;
        }

        private JSONArray histSel() {
            if (sel == 0) return histUsd;
            if (sel == 1) return histEur;
            if (sel == 2) return histUsdt;
            return histPar;
        }

        private void redraw() {
            String[] names = {"Dólar oficial", "Euro oficial", "USDT Binance", "Dólar paralelo"};
            chartTitle.setText(names[sel]);
            for (int i = 0; i < 4; i++) {
                TextView sv = (TextView) segWrap.getChildAt(i);
                if (i == sel) {
                    sv.setBackground(rounded(col("card_bg"), 8));
                    sv.setTextColor(col("text_primary"));
                } else {
                    sv.setBackground(null);
                    sv.setTextColor(col("text_secondary"));
                }
            }

            for (int i = 0; i < 4; i++) {
                TextView sv = (TextView) rangeWrap.getChildAt(i);
                if (i == rangeSel) {
                    sv.setBackground(rounded(col("card_bg"), 8));
                    sv.setTextColor(col("text_primary"));
                } else {
                    sv.setBackground(null);
                    sv.setTextColor(col("text_secondary"));
                }
            }

            JSONArray arr = histSel();
            int n = arr != null ? arr.length() : 0;
            if (n < 2) {
                statMin.setText("—"); statMax.setText("—"); statAvg.setText("—"); statVar.setText("—");
                statCount.setText(n == 0 ? "Aún no hay datos para esta tasa." : "1 punto guardado · sigue abriendo la app para acumular datos.");
                chart.setData(null, null);
                chart.invalidate();
                return;
            }

            long cut = 0;
            if (rangeSel == 0) cut = System.currentTimeMillis() - 7L * 86400000L;
            else if (rangeSel == 1) cut = System.currentTimeMillis() - 30L * 86400000L;
            else if (rangeSel == 2) cut = System.currentTimeMillis() - 90L * 86400000L;
            java.util.ArrayList<Float> fv = new java.util.ArrayList<Float>();
            java.util.ArrayList<Long> ft = new java.util.ArrayList<Long>();
            try {
                for (int i = 0; i < n; i++) {
                    JSONObject o = arr.getJSONObject(i);
                    long t = o.getLong("t");
                    if (cut > 0 && t < cut) continue;
                    fv.add((float) o.getDouble("v"));
                    ft.add(t);
                }
            } catch (Exception e) {
                chart.setData(null, null);
                chart.invalidate();
                return;
            }
            int m = fv.size();
            if (m < 2) {
                statMin.setText("—"); statMax.setText("—"); statAvg.setText("—"); statVar.setText("—");
                statCount.setText("Sin datos en este rango todavía.");
                chart.setData(null, null);
                chart.invalidate();
                return;
            }
            float[] vals = new float[m];
            long[] times = new long[m];
            for (int i = 0; i < m; i++) {
                vals[i] = fv.get(i);
                times[i] = ft.get(i);
            }

            double min = vals[0], max = vals[0], sum = 0;
            for (int i = 0; i < m; i++) {
                if (vals[i] < min) min = vals[i];
                if (vals[i] > max) max = vals[i];
                sum += vals[i];
            }
            double avg = sum / m;
            double var = (vals[m - 2] != 0) ? (vals[m - 1] - vals[m - 2]) / vals[m - 2] * 100.0 : 0;

            statMin.setText(fmt(min));
            statMax.setText(fmt(max));
            statAvg.setText(fmt(avg));
            statVar.setText((var >= 0 ? "▲ " : "▼ ") + String.format(new Locale("es", "VE"), "%.1f %%", Math.abs(var)));
            statVar.setTextColor(var >= 0 ? col("green") : col("red"));
            String[] rLbl = {"últimos 7 días", "último mes", "últimos 3 meses", "todo el historial"};
            statCount.setText(m + " puntos · " + rLbl[rangeSel] + " · los datos se registran en cada apertura.");
            chart.setData(vals, times);
            chart.startReveal();
        }
    }

    private class ChartView extends View {
        private float[] vals;
        private long[] times;
        private float progress = 1f;
        private ValueAnimator reveal;

        private Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);

        ChartView() {
            super(MainActivity.this);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(dp(2.4f));
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setStrokeJoin(Paint.Join.ROUND);
            fill.setStyle(Paint.Style.FILL);
            grid.setStyle(Paint.Style.STROKE);
            grid.setStrokeWidth(dp(0.8f));
            grid.setColor(col("separator"));
            grid.setPathEffect(new DashPathEffect(new float[]{dp(4), dp(6)}, 0));
            txt.setColor(col("text_secondary"));
            txt.setTextSize(dp(10.5f));
        }

        void setData(float[] v, long[] t) {
            vals = v;
            times = t;
        }

        void startReveal() {
            if (reveal != null) reveal.cancel();
            progress = 0f;
            reveal = ValueAnimator.ofFloat(0f, 1f);
            reveal.setDuration(800);
            reveal.setInterpolator(decel);
            reveal.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator a) {
                    progress = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            reveal.start();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();
            if (w == 0 || h == 0) return;

            if (vals == null || vals.length < 2) {
                txt.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("Recolectando datos…", w / 2f, h / 2f - dp(8), txt);
                canvas.drawText("Cada apertura guarda un punto de la tasa.", w / 2f, h / 2f + dp(10), txt);
                txt.setTextAlign(Paint.Align.LEFT);
                return;
            }

            int n = vals.length;
            float min = vals[0], max = vals[0];
            int iMax = 0, iMin = 0;
            for (int i = 0; i < n; i++) {
                if (vals[i] < min) { min = vals[i]; iMin = i; }
                if (vals[i] > max) { max = vals[i]; iMax = i; }
            }
            boolean flat = (max - min) <= 0;
            float range = flat ? 1 : (max - min);

            float padL = dp(10), padR = dp(10), padT = dp(26), padB = dp(30);
            float usableW = w - padL - padR;
            float usableH = h - padT - padB;

            float[] xs = new float[n];
            float[] ys = new float[n];
            for (int i = 0; i < n; i++) {
                xs[i] = padL + usableW * (n == 1 ? 0 : (i / (float) (n - 1)));
                float norm = flat ? 0.5f : (vals[i] - min) / range;
                ys[i] = padT + usableH * (1 - norm);
            }

            boolean up = vals[n - 1] >= vals[0];
            int trend = up ? col("green") : col("red");

            for (int g = 0; g <= 2; g++) {
                float gy = padT + usableH * g / 2f;
                canvas.drawLine(padL, gy, w - padR, gy, grid);
            }

            Path fp = new Path();
            fp.moveTo(xs[0], ys[0]);
            for (int i = 1; i < n; i++) fp.lineTo(xs[i], ys[i]);

            Path drawPath = fp;
            float endX = xs[n - 1], endY = ys[n - 1];
            if (progress < 1f) {
                PathMeasure pm = new PathMeasure(fp, false);
                float segLen = pm.getLength() * progress;
                Path seg = new Path();
                if (!pm.getSegment(0, segLen, seg, true)) {
                    seg.moveTo(xs[0], ys[0]);
                }
                float[] pos = new float[2];
                pm.getPosTan(segLen, pos, null);
                endX = pos[0];
                endY = pos[1];
                drawPath = seg;
            }

            Path area = new Path(drawPath);
            area.lineTo(endX, padT + usableH);
            area.lineTo(xs[0], padT + usableH);
            area.close();
            int cr = Color.red(trend), cg = Color.green(trend), cb = Color.blue(trend);
            fill.setShader(new LinearGradient(0, padT, 0, padT + usableH,
                    Color.argb(66, cr, cg, cb), Color.argb(0, cr, cg, cb), Shader.TileMode.CLAMP));
            canvas.drawPath(area, fill);
            fill.setShader(null);

            line.setColor(trend);
            canvas.drawPath(drawPath, line);

            if (n <= 60 && progress >= 1f) {
                dot.setColor(trend);
                for (int i = 0; i < n; i++) {
                    canvas.drawCircle(xs[i], ys[i], dp(2.2f), dot);
                }
            }
            dot.setColor(Color.WHITE);
            canvas.drawCircle(endX, endY, dp(4.5f), dot);
            dot.setColor(trend);
            canvas.drawCircle(endX, endY, dp(3f), dot);

            float clampXmax = Math.min(Math.max(xs[iMax], padL + dp(22)), w - padR - dp(22));
            float clampXmin = Math.min(Math.max(xs[iMin], padL + dp(22)), w - padR - dp(22));
            float yMaxLbl = ys[iMax] - dp(9);
            if (yMaxLbl < padT + dp(4)) yMaxLbl = ys[iMax] + dp(17);
            float yMinLbl = ys[iMin] + dp(17);
            if (yMinLbl > padT + usableH + dp(6)) yMinLbl = ys[iMin] - dp(9);

            txt.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(fmt(max), clampXmax, yMaxLbl, txt);
            canvas.drawText(fmt(min), clampXmin, yMinLbl, txt);

            SimpleDateFormat f = new SimpleDateFormat("d MMM", new Locale("es"));
            txt.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(f.format(new Date(times[0])), padL, h - dp(8), txt);
            txt.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(f.format(new Date(times[n - 1])), w - padR, h - dp(8), txt);
            txt.setTextAlign(Paint.Align.LEFT);
        }
    }

    // ================= PÁGINA CONVERSOR =================

    private class PageConv {
        ScrollView root;
        LinearLayout segWrap;
        EditText input;
        TextView result, labelFrom, labelTo;
        int mode = 0;
        boolean invert = false;

        PageConv() {
            root = new ScrollView(MainActivity.this);
            root.setOverScrollMode(View.OVER_SCROLL_NEVER);
            if (Build.VERSION.SDK_INT >= 23) {
                root.setOnScrollChangeListener(new View.OnScrollChangeListener() {
                    public void onScrollChange(View v, int sx, int sy, int osx, int osy) { nudgeBlur(); }
                });
            }
            LinearLayout box = new LinearLayout(MainActivity.this);
            box.setOrientation(LinearLayout.VERTICAL);
            int pad = dp(20);
            box.setPadding(pad, dp(8), pad, dp(110));
            root.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            box.addView(bigTitle("Conversor"));
            box.addView(tv("Calcula rápido con la tasa del día", 15, false, col("text_secondary")));

            LinearLayout card = new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(rounded(col("card_bg"), 18));
            card.setPadding(dp(18), dp(18), dp(18), dp(20));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.setMargins(0, dp(16), 0, 0);
            box.addView(pressScale(card), clp);

            segWrap = new LinearLayout(MainActivity.this);
            segWrap.setOrientation(LinearLayout.HORIZONTAL);
            segWrap.setBackground(rounded(col("input_bg"), 10));
            segWrap.setPadding(dp(3), dp(3), dp(3), dp(3));
            card.addView(segWrap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
            String[] segNames = {"USD", "EUR", "USDT"};
            for (int i = 0; i < 3; i++) {
                final int idx = i;
                TextView s = tv(segNames[i], 14, true, col("text_secondary"));
                s.setGravity(Gravity.CENTER);
                LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
                segWrap.addView(s, slp);
                s.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        mode = idx;
                        styleSeg();
                        v.setScaleX(0.9f);
                        v.setScaleY(0.9f);
                        v.animate().scaleX(1f).scaleY(1f).setDuration(280).setInterpolator(pop).start();
                        recompute();
                        hideKeyboard(v);
                    }
                });
            }

            LinearLayout row = new LinearLayout(MainActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.setMargins(0, dp(18), 0, 0);
            card.addView(row, rlp);

            LinearLayout colFrom = new LinearLayout(MainActivity.this);
            colFrom.setOrientation(LinearLayout.VERTICAL);
            row.addView(colFrom, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            labelFrom = tv("Monto en USD", 13, false, col("text_secondary"));
            colFrom.addView(labelFrom);

            input = new EditText(MainActivity.this);
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            input.setBackground(rounded(col("input_bg"), 12));
            input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            input.setTextColor(col("text_primary"));
            input.setHint("0,00");
            input.setHintTextColor(col("text_secondary"));
            input.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ilp.setMargins(0, dp(8), 0, 0);
            colFrom.addView(input, ilp);

            input.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                public void onTextChanged(CharSequence s, int a, int b, int c) { }
                public void afterTextChanged(Editable s) { recompute(); }
            });

            Button swap = new Button(MainActivity.this);
            swap.setText("⇅");
            swap.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            swap.setTextColor(col("accent"));
            swap.setBackgroundColor(Color.TRANSPARENT);
            swap.setPadding(dp(4), 0, dp(4), 0);
            swap.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    invert = !invert;
                    v.animate().rotationBy(180f).setDuration(300).setInterpolator(decel).start();
                    recompute();
                }
            });
            row.addView(swap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout colTo = new LinearLayout(MainActivity.this);
            colTo.setOrientation(LinearLayout.VERTICAL);
            row.addView(colTo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            labelTo = tv("Resultado en Bs.", 13, false, col("text_secondary"));
            colTo.addView(labelTo);

            result = tv("—", 24, true, col("text_primary"));
            result.setBackground(rounded(col("input_bg"), 12));
            result.setPadding(dp(14), dp(16), dp(14), dp(16));
            LinearLayout.LayoutParams rslp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rslp.setMargins(0, dp(8), 0, 0);
            colTo.addView(result, rslp);

            LinearLayout chips = new LinearLayout(MainActivity.this);
            chips.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams chlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            chlp.setMargins(0, dp(18), 0, 0);
            card.addView(chips, chlp);
            String[] chipVals = {"1", "5", "10", "20", "50", "100"};
            for (int i = 0; i < chipVals.length; i++) {
                final String cv = chipVals[i];
                TextView c = tv(cv, 14, true, col("accent"));
                c.setGravity(Gravity.CENTER);
                c.setBackground(rounded(Color.argb(26, Color.red(col("accent")), Color.green(col("accent")), Color.blue(col("accent"))), 14));
                LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(0, dp(34), 1);
                clp2.setMargins(dp(3), 0, dp(3), 0);
                chips.addView(pressScale(c), clp2);
                c.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        input.setText(cv);
                        recompute();
                        hideKeyboard(v);
                    }
                });
            }

            // Compartir el resultado de la conversión
            Button convShare = new Button(MainActivity.this);
            convShare.setText("Compartir resultado");
            convShare.setTextColor(Color.WHITE);
            convShare.setTextSize(15);
            convShare.setTypeface(TF_MED);
            convShare.setBackground(rounded(col("accent"), 999));
            convShare.setAllCaps(false);
            convShare.setElevation(0);
            convShare.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { shareConv(); }
            });
            LinearLayout.LayoutParams cslp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
            cslp.setMargins(0, dp(16), 0, 0);
            card.addView(pressScale(convShare), cslp);

            TextView note = tv("USD y EUR usan la tasa oficial del BCV. USDT usa Binance P2P.", 12, false, col("text_secondary"));
            note.setPadding(0, dp(14), 0, 0);
            card.addView(note);
        }

        private void styleSeg() {
            for (int i = 0; i < 3; i++) {
                TextView sv = (TextView) segWrap.getChildAt(i);
                if (i == mode) {
                    sv.setBackground(rounded(col("card_bg"), 9));
                    sv.setTextColor(col("text_primary"));
                } else {
                    sv.setBackground(null);
                    sv.setTextColor(col("text_secondary"));
                }
            }
        }

        private double rateSel() {
            if (mode == 0) return bcvUsd;
            if (mode == 1) return bcvEur;
            return usdt;
        }

        private void setResultAnimated(String s) {
            boolean changed = !result.getText().toString().equals(s);
            result.setText(s);
            if (changed) {
                result.setAlpha(0.55f);
                result.setScaleX(0.985f);
                result.animate().alpha(1f).scaleX(1f).setDuration(150).setInterpolator(decel).start();
            }
        }

        private void labelFade(TextView t, String s) {
            boolean changed = !t.getText().toString().equals(s);
            t.setText(s);
            if (changed) {
                t.setAlpha(0.3f);
                t.animate().alpha(1f).setDuration(180).setInterpolator(decel).start();
            }
        }

         void recompute() {
            String[] fromNames = {"Monto en USD", "Monto en EUR", "Monto en USDT"};
            double rate = rateSel();
            String sym = mode == 1 ? "EUR" : mode == 0 ? "USD" : "USDT";
            double amt = parseAmount(input.getText().toString());
            if (invert) {
                labelFade(labelFrom, "Monto en Bs.");
                labelFade(labelTo, "Resultado en " + sym);
                if (rate > 0 && amt > 0) setResultAnimated(fmt(amt / rate));
                else setResultAnimated("—");
            } else {
                labelFade(labelFrom, fromNames[mode]);
                labelFade(labelTo, "Resultado en Bs.");
                if (rate > 0 && amt > 0) setResultAnimated(fmt(amt * rate) + " Bs.");
                else setResultAnimated("—");
            }
        }

        void shareConv() {
            double rate = rateSel();
            double amt = parseAmount(input.getText().toString());
            if (rate <= 0 || amt <= 0) {
                Toast.makeText(MainActivity.this, "Escribe un monto para compartir", Toast.LENGTH_SHORT).show();
                return;
            }
            String sym = mode == 1 ? "EUR" : mode == 0 ? "USD" : "USDT";
            String montoTxt = fmt(amt) + " " + sym;
            String resTxt = invert ? fmt(amt / rate) + " " + sym : fmt(amt * rate) + " Bs.";
            String tasaTxt = "Tasa: " + fmt(rate) + " Bs. por " + sym
                    + " · " + (mode == 2 ? "Binance P2P" : "BCV");

            boolean dk = isDark();
            int bgCol = dk ? 0xFF0E0E10 : 0xFFF2F3F7;
            int cardCol = dk ? 0xFF1C1C1E : Color.WHITE;
            int priCol = dk ? 0xFFF2F2F7 : 0xFF111114;
            int secCol = dk ? 0xFF98989F : 0xFF8E8E93;
            int accCol = col("accent");

            int W = 1000, pad = 56, headH = 200, footH = 130;
            int H = pad + headH + 340 + footH;

            Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            cv.drawColor(bgCol);

            Paint cardP = new Paint(Paint.ANTI_ALIAS_FLAG);
            cardP.setColor(cardCol);
            cv.drawRoundRect(pad, pad, W - pad, H - pad, 44, 44, cardP);

            float cx = pad + 70, cy = pad + 78, r = 58;
            cv.save();
            Path clip = new Path();
            float rad = r * 0.4474f;
            clip.addRoundRect(new RectF(cx - r, cy - r, cx + r, cy + r), rad, rad, Path.Direction.CW);
            cv.clipPath(clip);
            Drawable appIc = getResources().getDrawable(R.mipmap.ic_launcher);
            appIc.setBounds((int) (cx - r), (int) (cy - r), (int) (cx + r), (int) (cy + r));
            appIc.draw(cv);
            cv.restore();

            float tx = cx + 96;
            Paint titleP = new Paint(Paint.ANTI_ALIAS_FLAG);
            titleP.setColor(priCol);
            titleP.setTextSize(52);
            titleP.setFakeBoldText(true);
            cv.drawText("Super Cambio", tx, pad + 62, titleP);
            Paint subP = new Paint(Paint.ANTI_ALIAS_FLAG);
            subP.setColor(secCol);
            subP.setTextSize(36);
            cv.drawText("Conversor de moneda", tx, pad + 112, subP);

            Paint sepP = new Paint();
            sepP.setColor(dk ? 0x24FFFFFF : 0x14000000);
            cv.drawLine(pad + 40, pad + headH, W - pad - 40, pad + headH, sepP);

            float y = pad + headH + 30;
            Paint bigP = new Paint(Paint.ANTI_ALIAS_FLAG);
            bigP.setColor(priCol);
            bigP.setTextSize(72);
            bigP.setFakeBoldText(true);
            bigP.setTextAlign(Paint.Align.CENTER);
            Paint arrowP = new Paint(Paint.ANTI_ALIAS_FLAG);
            arrowP.setColor(accCol);
            arrowP.setTextSize(52);
            arrowP.setTextAlign(Paint.Align.CENTER);
            cv.drawText(montoTxt, W / 2f, y + 80, bigP);
            cv.drawText("↓", W / 2f, y + 160, arrowP);
            cv.drawText(resTxt, W / 2f, y + 250, bigP);

            Paint tasaP = new Paint(Paint.ANTI_ALIAS_FLAG);
            tasaP.setColor(secCol);
            tasaP.setTextSize(34);
            tasaP.setTextAlign(Paint.Align.CENTER);
            cv.drawText(tasaTxt, W / 2f, y + 310, tasaP);

            Paint footP = new Paint(Paint.ANTI_ALIAS_FLAG);
            footP.setColor(secCol);
            footP.setTextSize(32);
            footP.setTextAlign(Paint.Align.CENTER);
            cv.drawText("Generado con Super Cambio · © 2026 Jdvibes", W / 2f, H - pad - 36, footP);

            try {
                File f = new File(getCacheDir(), "conversion.png");
                FileOutputStream fo = new FileOutputStream(f);
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fo);
                fo.close();
                bmp.recycle();

                String txt = montoTxt + " = " + resTxt + "\n" + tasaTxt + "\n\nEnviado con Super Cambio";
                Uri uri = Uri.parse("content://" + ShareProvider.AUTHORITY + "/conversion.png");
                Intent it = new Intent(Intent.ACTION_SEND);
                it.setType("image/png");
                it.putExtra(Intent.EXTRA_STREAM, uri);
                it.putExtra(Intent.EXTRA_TEXT, txt);
                it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(it, "Compartir conversión"));
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "No se pudo generar la imagen", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ================= PÁGINA ACERCA DE =================

    private View buildAboutPage() {
        ScrollView root = new ScrollView(this);
        root.setOverScrollMode(View.OVER_SCROLL_NEVER);
            if (Build.VERSION.SDK_INT >= 23) {
                root.setOnScrollChangeListener(new View.OnScrollChangeListener() {
                    public void onScrollChange(View v, int sx, int sy, int osx, int osy) { nudgeBlur(); }
                });
            }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        box.setPadding(pad, dp(8), pad, dp(110));
        root.addView(box, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        box.addView(bigTitle("Acerca de"));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        head.setBackground(rounded(col("card_bg"), 18));
        head.setPadding(dp(18), dp(26), dp(18), dp(24));
        box.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Logo real de la app, cuadrado con esquinas redondeadas estilo iOS
        aboutLogo = new ImageView(this);
        aboutLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        paintAboutLogo();
        head.addView(aboutLogo, new LinearLayout.LayoutParams(dp(72), dp(72)));

        TextView name = tv("Super Cambio", 24, true, col("text_primary"));
        name.setPadding(0, dp(12), 0, 0);
        head.addView(name);

        head.addView(tv("Versión 1.9.9 · Android", 13, false, col("text_secondary")));
        TextView tagline = tv("Tasas de cambio de Venezuela,\nsin anuncios y sin suscripciones.", 14, false, col("text_secondary"));
        tagline.setGravity(Gravity.CENTER);
        tagline.setPadding(0, dp(6), 0, 0);
        head.addView(tagline);

        // Apariencia
        box.addView(sectionLabel("Apariencia"));
        LinearLayout themeCard = card(box);

        rowRadio(themeCard, "Automático (sistema)", themeMode == 0, 0);
        rowSep(themeCard);
        rowRadio(themeCard, "Claro", themeMode == 1, 1);
        rowSep(themeCard);
        rowRadio(themeCard, "Oscuro", themeMode == 2, 2);

        rowSep(themeCard);
        LinearLayout blurRow = new LinearLayout(this);
        blurRow.setOrientation(LinearLayout.HORIZONTAL);
        blurRow.setGravity(Gravity.CENTER_VERTICAL);
        blurRow.setPadding(0, dp(12), 0, dp(12));
        themeCard.addView(blurRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout blurCol = new LinearLayout(this);
        blurCol.setOrientation(LinearLayout.VERTICAL);
        blurRow.addView(blurCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        blurCol.addView(tv("Desenfoque en la barra", 15, false, col("text_primary")));
        blurCol.addView(tv("Vidrio esmerilado bajo los íconos", 13, false, col("text_secondary")));

        Switch blurSwitch = new Switch(this);
        blurSwitch.setChecked(blurOn);
        try {
            blurSwitch.setThumbTintList(android.content.res.ColorStateList.valueOf(col("accent")));
            int[][] states = new int[][]{ new int[]{android.R.attr.state_checked}, new int[]{} };
            int[] colors = new int[]{ col("accent"), col("separator") };
            blurSwitch.setTrackTintList(new android.content.res.ColorStateList(states, colors));
        } catch (Exception ignored) { }
        blurSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                blurOn = checked;
                saveAll();
                applyBarBackground();
                blurAfterLayout();
                Toast.makeText(MainActivity.this, checked ? "Desenfoque activado" : "Desenfoque desactivado", Toast.LENGTH_SHORT).show();
            }
        });
        blurRow.addView(blurSwitch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Logo oscuro de la app
        LinearLayout logoRow = new LinearLayout(this);
        logoRow.setOrientation(LinearLayout.HORIZONTAL);
        logoRow.setGravity(Gravity.CENTER_VERTICAL);
        logoRow.setPadding(0, dp(12), 0, dp(12));
        themeCard.addView(logoRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout logoCol = new LinearLayout(this);
        logoCol.setOrientation(LinearLayout.VERTICAL);
        logoRow.addView(logoCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        logoCol.addView(tv("Logo oscuro de la app", 15, false, col("text_primary")));
        logoCol.addView(tv("Cambia el ícono del launcher y el logo interno", 13, false, col("text_secondary")));

        Switch darkLogoSwitch = new Switch(this);
        darkLogoSwitch.setChecked(darkLogo);
        try {
            darkLogoSwitch.setThumbTintList(android.content.res.ColorStateList.valueOf(col("accent")));
            int[][] dstates = new int[][]{ new int[]{android.R.attr.state_checked}, new int[]{} };
            int[] dcolors = new int[]{ col("accent"), col("separator") };
            darkLogoSwitch.setTrackTintList(new android.content.res.ColorStateList(dstates, dcolors));
        } catch (Exception ignored) { }
        darkLogoSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                darkLogo = checked;
                saveAll();
                applyDarkLauncher();
                paintAboutLogo();
                Toast.makeText(MainActivity.this, checked ? "Logo oscuro activado" : "Logo claro activado", Toast.LENGTH_SHORT).show();
            }
        });
        logoRow.addView(darkLogoSwitch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Aviso diario de la tasa
        LinearLayout notifRow = new LinearLayout(this);
        notifRow.setOrientation(LinearLayout.HORIZONTAL);
        notifRow.setGravity(Gravity.CENTER_VERTICAL);
        notifRow.setPadding(0, dp(12), 0, dp(12));
        themeCard.addView(notifRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout notifCol = new LinearLayout(this);
        notifCol.setOrientation(LinearLayout.VERTICAL);
        notifRow.addView(notifCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        notifCol.addView(tv("Aviso diario de la tasa", 15, false, col("text_primary")));
        notifCol.addView(tv("Todos los días a las 3:35 PM, hora de Venezuela", 13, false, col("text_secondary")));
        Switch notifSwitch = new Switch(this);
        notifSwitch.setChecked(dailyNotif);
        try {
            notifSwitch.setThumbTintList(android.content.res.ColorStateList.valueOf(col("accent")));
            int[][] nstates = new int[][]{ new int[]{android.R.attr.state_checked}, new int[]{} };
            int[] ncolors = new int[]{ col("accent"), col("separator") };
            notifSwitch.setTrackTintList(new android.content.res.ColorStateList(nstates, ncolors));
        } catch (Exception ignored) { }
        notifSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                dailyNotif = checked;
                saveAll();
                RateAlarm.schedule(MainActivity.this, checked);
                Toast.makeText(MainActivity.this, checked ? "Aviso diario activado" : "Aviso diario desactivado", Toast.LENGTH_SHORT).show();
            }
        });
        notifRow.addView(notifSwitch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Vibración al tocar
        LinearLayout hapRow = new LinearLayout(this);
        hapRow.setOrientation(LinearLayout.HORIZONTAL);
        hapRow.setGravity(Gravity.CENTER_VERTICAL);
        hapRow.setPadding(0, dp(12), 0, dp(12));
        themeCard.addView(hapRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout hapCol = new LinearLayout(this);
        hapCol.setOrientation(LinearLayout.VERTICAL);
        hapRow.addView(hapCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        hapCol.addView(tv("Vibración al tocar", 15, false, col("text_primary")));
        hapCol.addView(tv("Respuesta táctil sutil en botones y pestañas", 13, false, col("text_secondary")));
        Switch hapSwitch = new Switch(this);
        hapSwitch.setChecked(hapticsOn);
        try {
            hapSwitch.setThumbTintList(android.content.res.ColorStateList.valueOf(col("accent")));
            int[][] hstates = new int[][]{ new int[]{android.R.attr.state_checked}, new int[]{} };
            int[] hcolors = new int[]{ col("accent"), col("separator") };
            hapSwitch.setTrackTintList(new android.content.res.ColorStateList(hstates, hcolors));
        } catch (Exception ignored) { }
        hapSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                hapticsOn = checked;
                saveAll();
                Toast.makeText(MainActivity.this, checked ? "Vibración activada" : "Vibración desactivada", Toast.LENGTH_SHORT).show();
            }
        });
        hapRow.addView(hapSwitch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Créditos
        box.addView(sectionLabel("Créditos"));
        LinearLayout credCard = card(box);
        rowInfo(credCard, "Desarrollo y diseño", "Jdvibes", true);
        rowSep(credCard);
        rowInfo(credCard, "Creación original", "App hecha de cero por Jdvibes", false);
        rowSep(credCard);
        rowInfo(credCard, "Inspirada en", "La utilidad de las apps de tasas 🇻🇪", false);

        box.addView(sectionLabel("Fuentes de datos"));
        LinearLayout dataCard = card(box);
        rowLink(dataCard, "Dólar y Euro oficiales (BCV)", "ve.dolarapi.com", "https://ve.dolarapi.com");
        rowSep(dataCard);
        rowLink(dataCard, "USDT Binance P2P", "criptoya.com", "https://criptoya.com");
        rowSep(dataCard);
        rowInfo(dataCard, "Paralelo", "Promedio de mercado (dolarAPI)", false);

        box.addView(sectionLabel("Privacidad y rendimiento"));
        LinearLayout privCard = card(box);
        rowCheck(privCard, "Sin anuncios");
        rowCheck(privCard, "Sin suscripciones ni compras");
        rowCheck(privCard, "Sin seguimiento ni analíticas");
        rowCheck(privCard, "Historial guardado solo en tu dispositivo");
        rowCheck(privCard, "Historial compartido: solo cifras, sin datos personales");
        rowCheck(privCard, "Animaciones fluidas · hasta 120 Hz");

        TextView footer = tvIos("© 2026 Jdvibes · Super Cambio\nHecho con ❤️ para Venezuela 🇻🇪", 12, false, col("text_secondary"));
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams flp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flp.setMargins(0, dp(22), 0, 0);
        box.addView(footer, flp);

        return root;
    }

    private void setThemeMode(int mode) {
        if (themeMode == mode) return;
        themeMode = mode;
        saveAll();
        applyStatusBar();

        // Animación de transición: capturar el tema viejo, reconstruir con el
        // nuevo y hacer crossfade de toda la pantalla.
        try {
            final int keepTab = currentTab;
            final Bitmap snap = Bitmap.createBitmap(rootF.getWidth(), rootF.getHeight(), Bitmap.Config.ARGB_8888);
            rootF.draw(new Canvas(snap));

            buildUi();

            final ImageView overlay = new ImageView(this);
            overlay.setImageBitmap(snap);
            overlay.setScaleType(ImageView.ScaleType.FIT_XY);
            rootF.addView(overlay, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            overlay.animate().alpha(0f).setDuration(420).setInterpolator(decel).withEndAction(new Runnable() {
                public void run() {
                    rootF.removeView(overlay);
                    overlay.setImageDrawable(null);
                    snap.recycle();
                }
            }).start();

            // forzar que selectTab oculte las demás páginas y conserve la pestaña
            currentTab = -1;
            selectTab(keepTab < 0 ? 0 : keepTab);
            updateAllPages();
            blurAfterLayout();
        } catch (Throwable t) {
            buildUi();
            currentTab = -1;
            selectTab(0);
            updateAllPages();
        }
    }

    private TextView sectionLabel(String s) {
        TextView t = tv(s.toUpperCase(), 13, true, col("text_secondary"));
        t.setPadding(dp(14), dp(22), 0, dp(6));
        return t;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(rounded(col("card_bg"), 18));
        c.setPadding(dp(18), dp(4), dp(18), dp(4));
        parent.addView(c, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return c;
    }

    private void rowSep(LinearLayout card) {
        View v = new View(this);
        v.setBackgroundColor(col("separator"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.7f)));
        card.addView(v, lp);
    }

    private LinearLayout rowBase(LinearLayout card, String title, String sub, boolean boldSub) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(14));
        card.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        col.addView(tv(title, 15, false, col("text_primary")));
        if (sub != null && sub.length() > 0) {
            TextView s = tv(sub, 13, false, boldSub ? col("accent") : col("text_secondary"));
            if (boldSub) s.setTypeface(TF_MED);
            col.addView(s);
        }
        return row;
    }

    private void paintAboutLogo() {
        try {
            int d = dp(72);
            float inset = d * 0.06f;
            float iw = d - inset * 2;
            float rad = iw * 0.2237f; // radio de esquina iOS
            Bitmap lb = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888);
            Canvas lc = new Canvas(lb);
            RectF ir = new RectF(inset, inset, inset + iw, inset + iw);

            // Sombra suave para despegarlo del fondo
            Paint shP = new Paint(Paint.ANTI_ALIAS_FLAG);
            shP.setShadowLayer(d * 0.05f, 0, d * 0.035f, isDark() ? 0x66000000 : 0x2E000000);
            shP.setColor(Color.BLACK);
            lc.drawRoundRect(ir, rad, rad, shP);

            // Ícono (el reflejo de vidrio viene horneado en el drawable)
            Path clip = new Path();
            clip.addRoundRect(ir, rad, rad, Path.Direction.CW);
            lc.save();
            lc.clipPath(clip);
            Drawable appIc = logoDrawable();
            appIc.setBounds((int) inset, (int) inset, (int) (inset + iw), (int) (inset + iw));
            appIc.draw(lc);
            lc.restore();

            // Borde hairline con reflejo (más brillante arriba, como vidrio)
            Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(Math.max(2, dp(1)));
            border.setShader(new LinearGradient(0, inset, 0, inset + iw,
                    new int[]{0x8CFFFFFF, 0x26FFFFFF, 0x14FFFFFF},
                    new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            lc.drawRoundRect(ir, rad, rad, border);

            aboutLogo.setImageBitmap(lb);
        } catch (Exception e) {
            aboutLogo.setImageResource(darkLogo ? R.drawable.ic_launcher_dark : R.mipmap.ic_launcher);
        }
    }

    private Drawable logoDrawable() {
        return getResources().getDrawable(darkLogo ? R.drawable.ic_launcher_dark : R.mipmap.ic_launcher);
    }

    // Cambia el ícono del launcher usando activity-alias (sin matar la app)
    private void applyDarkLauncher() {
        try {
            PackageManager pm = getPackageManager();
            pm.setComponentEnabledSetting(
                    new ComponentName(this, "com.supercambio.app.LauncherDark"),
                    darkLogo ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                             : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(
                    new ComponentName(this, "com.supercambio.app.LauncherLight"),
                    darkLogo ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                             : PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (Exception ignored) { }
    }

    private void rowRadio(LinearLayout card, String title, boolean selected, final int mode) {
        LinearLayout row = rowBase(card, title, null, false);
        row.addView(radioDot(selected), new LinearLayout.LayoutParams(dp(22), dp(22)));
        row.setClickable(true);
        pressScale(row);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { setThemeMode(mode); }
        });
    }

    private View radioDot(boolean selected) {
        FrameLayout f = new FrameLayout(this);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(dp(2), selected ? col("accent") : col("text_secondary"));
        View ringV = new View(this);
        ringV.setBackground(ring);
        f.addView(ringV, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        if (selected) {
            View dot = new View(this);
            GradientDrawable dg = new GradientDrawable();
            dg.setShape(GradientDrawable.OVAL);
            dg.setColor(col("accent"));
            dot.setBackground(dg);
            f.addView(dot, new FrameLayout.LayoutParams(dp(10), dp(10), Gravity.CENTER));
        }
        return f;
    }

    private void rowInfo(LinearLayout card, String title, String sub, boolean boldSub) {
        rowBase(card, title, sub, boldSub);
    }

    private void rowLink(LinearLayout card, String title, String sub, final String url) {
        LinearLayout row = rowBase(card, title, sub, true);
        TextView chev = tv("›", 22, false, col("text_secondary"));
        row.addView(chev);
        row.setClickable(true);
        pressScale(row);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "No se pudo abrir el enlace", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void rowCheck(LinearLayout card, String title) {
        LinearLayout row = rowBase(card, title, null, false);
        TextView check = tv("✓", 16, true, col("green"));
        row.addView(check);
    }

    // ================= RED Y REFRESCO =================

    private void refresh() {
        pageRates.status.setText("Actualizando…");
        new Thread(new Runnable() {
            public void run() {
                long nUsd = 0, nEur = 0, nUsdt = 0, nPar = 0;
                try {
                    JSONArray arr = new JSONArray(httpGet(API_USD));
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        String fuente = o.optString("fuente", "");
                        if ("oficial".equals(fuente)) { bcvUsd = o.getDouble("promedio"); nUsd = tsOf(o); }
                        else if ("paralelo".equals(fuente)) { paralelo = o.getDouble("promedio"); nPar = tsOf(o); }
                    }
                } catch (Exception ignored) { }
                try {
                    JSONArray arr = new JSONArray(httpGet(API_EUR));
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        if ("oficial".equals(o.optString("fuente", ""))) { bcvEur = o.getDouble("promedio"); nEur = tsOf(o); }
                    }
                } catch (Exception ignored) { }
                try {
                    JSONObject c = new JSONObject(httpGet(API_USDT));
                    JSONObject bin = c.getJSONObject("binancep2p");
                    double bid = bin.optDouble("bid", 0);
                    if (bid > 0) { usdt = bid; nUsdt = bin.optLong("time", 0) * 1000L; }
                } catch (Exception ignored) { }

                final boolean ok = bcvUsd > 0 || bcvEur > 0 || usdt > 0 || paralelo > 0;
                if (ok) {
                    if (nUsd > 0 && prevUsd == 0) prevUsd = bcvUsd;
                    if (nEur > 0 && prevEur == 0) prevEur = bcvEur;
                    if (nUsdt > 0 && prevUsdt == 0) prevUsdt = usdt;
                    if (nPar > 0 && prevPar == 0) prevPar = paralelo;
                    tUsd = nUsd > 0 ? nUsd : tUsd;
                    tEur = nEur > 0 ? nEur : tEur;
                    tUsdt = nUsdt > 0 ? nUsdt : tUsdt;
                    tPar = nPar > 0 ? nPar : tPar;
                    seedHistIfEmpty(histUsd, bcvUsd, tUsd);
                    seedHistIfEmpty(histEur, bcvEur, tEur);
                    seedHistIfEmpty(histUsdt, usdt, tUsdt);
                    seedHistIfEmpty(histPar, paralelo, tPar);
                    appendHist(histUsd, bcvUsd, System.currentTimeMillis());
                    appendHist(histEur, bcvEur, System.currentTimeMillis());
                    appendHist(histUsdt, usdt, System.currentTimeMillis());
                    appendHist(histPar, paralelo, System.currentTimeMillis());
                    saveAll();
                }

                ui.post(new Runnable() {
                    public void run() {
                        updateAllPages();
                        SimpleDateFormat f = new SimpleDateFormat("d MMM, HH:mm", new Locale("es"));
                        if (ok) pageRates.status.setText("✅ Actualizado: " + f.format(new Date()));
                        else pageRates.status.setText("📴 Sin conexión · mostrando últimos datos");
                        syncSharedHist(false);
                    }
                });
            }
        }).start();
    }

    // ===== Historial compartido entre usuarios =====
    // Solo se comparten los puntos donde la tasa VARÍA (cambia de valor).
    // Nube pública: textdb.online · sin cuentas · sin datos personales.

    private void syncSharedHist(boolean force) {
        long now = System.currentTimeMillis();
        if (syncBusy || (!force && now - lastSyncTry < 900000L)) return;
        syncBusy = true;
        lastSyncTry = now;
        new Thread(new Runnable() {
            public void run() {
                try {
                    String raw = httpGet(SYNC_READ);
                    JSONObject remote = null;
                    try { remote = new JSONObject(raw); } catch (Exception e) { remote = new JSONObject(); }
                    boolean changed = mergeHist(remote.optJSONArray("usd"), histUsd)
                            | mergeHist(remote.optJSONArray("eur"), histEur)
                            | mergeHist(remote.optJSONArray("usdt"), histUsdt)
                            | mergeHist(remote.optJSONArray("par"), histPar);
                    boolean upload = localNewPoints(remote.optJSONArray("usd"), histUsd)
                            || localNewPoints(remote.optJSONArray("eur"), histEur)
                            || localNewPoints(remote.optJSONArray("usdt"), histUsdt)
                            || localNewPoints(remote.optJSONArray("par"), histPar);
                    if (changed) {
                        saveAll();
                        ui.post(new Runnable() {
                            public void run() {
                                if (currentTab == 1 && pageCharts != null) pageCharts.redraw();
                                RateWidget.push(MainActivity.this);
                            }
                        });
                    }
                    if (upload) {
                        JSONObject out = new JSONObject();
                        out.put("usd", variationHist(histUsd));
                        out.put("eur", variationHist(histEur));
                        out.put("usdt", variationHist(histUsdt));
                        out.put("par", variationHist(histPar));
                        String body = "key=" + SYNC_KEY + "&value=" + java.net.URLEncoder.encode(out.toString(), "UTF-8");
                        httpFormPost(SYNC_WRITE, body);
                    }
                } catch (Exception ignored) { }
                syncBusy = false;
            }
        }).start();
    }

    private boolean mergeHist(JSONArray remote, JSONArray local) {
        if (remote == null || local == null) return false;
        boolean added = false;
        try {
            java.util.TreeMap<Long, Double> map = new java.util.TreeMap<Long, Double>();
            for (int i = 0; i < local.length(); i++) {
                JSONObject o = local.getJSONObject(i);
                map.put(o.getLong("t"), o.getDouble("v"));
            }
            for (int i = 0; i < remote.length(); i++) {
                JSONObject o = remote.getJSONObject(i);
                long t = o.getLong("t");
                double v = o.getDouble("v");
                if (v > 0 && !map.containsKey(t)) {
                    map.put(t, v);
                    added = true;
                }
            }
            if (added) {
                int n = map.size();
                int skip = Math.max(0, n - 500);
                JSONArray arr = new JSONArray();
                int idx = 0;
                for (java.util.Map.Entry<Long, Double> en : map.entrySet()) {
                    if (idx++ < skip) continue;
                    JSONObject np = new JSONObject();
                    np.put("t", en.getKey());
                    np.put("v", en.getValue().doubleValue());
                    arr.put(np);
                }
                while (local.length() > 0) local.remove(0);
                for (int i = 0; i < arr.length(); i++) local.put(arr.getJSONObject(i));
            }
        } catch (Exception ignored) { }
        return added;
    }

    private boolean localNewPoints(JSONArray remote, JSONArray local) {
        try {
            if (local == null || local.length() == 0) return false;
            if (remote == null || remote.length() == 0) return true;
            java.util.HashSet<Long> seen = new java.util.HashSet<Long>();
            for (int i = 0; i < remote.length(); i++) seen.add(remote.getJSONObject(i).getLong("t"));
            double prev = Double.NaN;
            for (int i = 0; i < local.length(); i++) {
                JSONObject o = local.getJSONObject(i);
                double v = o.getDouble("v");
                if (i == 0 || v != prev) {
                    if (!seen.contains(o.getLong("t"))) return true;
                }
                prev = v;
            }
        } catch (Exception ignored) { }
        return false;
    }

    private JSONArray variationHist(JSONArray h) {
        JSONArray out = new JSONArray();
        try {
            double prev = Double.NaN;
            for (int i = 0; i < h.length(); i++) {
                JSONObject o = h.getJSONObject(i);
                double v = o.getDouble("v");
                if (i == 0 || v != prev) {
                    JSONObject np = new JSONObject();
                    np.put("t", o.getLong("t"));
                    np.put("v", v);
                    out.put(np);
                }
                prev = v;
            }
        } catch (Exception ignored) { }
        return out;
    }

    private String httpFormPost(String urlStr, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try {
            java.io.OutputStream os = c.getOutputStream();
            os.write(body.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();
        } finally {
            try { c.getInputStream(); } catch (Exception ignored) { }
            c.disconnect();
        }
        return "";
    }

    private String httpGet(String urlStr) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "SuperCambio/1.4 (Android)");
        c.setRequestProperty("Accept", "application/json");
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

    private double fetchBcvSite() {
        try {
            String html = httpGet("https://www.bcv.org.ve/");
            String txt = html.replaceAll("<[^>]*>", " ");
            int i = txt.indexOf("Referencia");
            if (i < 0) return 0;
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("([0-9]{1,3}(?:\\.[0-9]{3})*,[0-9]{5,})").matcher(txt.substring(i, Math.min(txt.length(), i + 500)));
            if (!m.find()) return 0;
            double v = Double.parseDouble(m.group(1).replace(".", "").replace(",", "."));
            return (v > 1 && v < 1000000) ? v : 0;
        } catch (Exception e) { return 0; }
    }

    private void haptic(View v) {
        if (hapticsOn && v != null) {
            try { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); } catch (Exception ignored) { }
        }
    }

    private String fmt(double v) {
        NumberFormat f = NumberFormat.getNumberInstance(new Locale("es", "VE"));
        f.setMaximumFractionDigits(4);
        f.setMinimumFractionDigits(2);
        return f.format(v);
    }

    private double parseAmount(String s) {
        try { return Double.parseDouble(s.trim().replace(",", ".")); } catch (Exception e) { return 0; }
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }
}
