package com.supercambio.app;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ImageSpan;
import android.text.style.TypefaceSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
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
import android.widget.ProgressBar;
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
import java.io.OutputStream;
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
    private static final String BSU = "\u00A0Bs.";
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
    private int accentIdx = 0;
    private boolean updBusy = false;
    private String updTag = "", updUrl = "", updNotes = "";
    private TextView updateTitle, updateSub;
    private long updSize = 0;
    private Dialog promptDlg;
    private Dialog downloadDlg;
    private String pendingApkFile = "";
    private volatile boolean dlCancel = false;
    private static final int REQ_INSTALL = 4001;
    private static final int REQ_PICK_JSON = 5001;
    private static final int REQ_SAVE_JSON = 5002;
    private static final int REQ_SAVE_CSV = 5003;
    private boolean langEn = false;
    private boolean widgetClear = false;
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
    private ImageView liquidSweep;
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
            if (!blurActive()) { tickerOn = false; return; }
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
        boolean legacyGlass = p.getBoolean("glass_fx", false);
        blurOn = p.getBoolean("blur_on", false) || legacyGlass;
        if (legacyGlass) {
            p.edit().putBoolean("blur_on", true).remove("glass_fx").apply();
        }
        darkLogo = p.getBoolean("dark_logo", false);
        hapticsOn = p.getBoolean("haptics_on", true);
        dailyNotif = p.getBoolean("daily_notif", false);
        accentIdx = p.getInt("accent_idx", 0);
        langEn = p.getBoolean("lang_en", false);
        widgetClear = p.getBoolean("widget_clear", false);
        // Anti-flash: usar el fondo del tema guardado desde el primer frame
        try {
            getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(col("page_bg")));
        } catch (Exception ignored) { }
        updTag = p.getString("upd_tag", "");
        updUrl = p.getString("upd_url", "");
        updNotes = p.getString("upd_notes", "");
        updSize = p.getLong("upd_size", 0);
        // Si el tag guardado ya está instalado, limpiarlo para que NO salga el popup al abrir
        if (updTag.length() > 0 && !isNewerVersion(updTag, currentVersionName())) {
            updTag = "";
            SharedPreferences.Editor e0 = p.edit();
            e0.putString("upd_tag", "");
            e0.apply();
        }

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
        checkForUpdates(false);
        ui.postDelayed(new Runnable() {
            public void run() {
                if (updTag.length() > 0) updatePrompt();
            }
        }, 1400);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        nextExpanded = false;
        if (pageRates != null) pageRates.stopTicking();
        if (ui != null) { ui.removeCallbacks(blurTick); tickerOn = false; }
        dismissQuiet(promptDlg); promptDlg = null;
        dismissQuiet(downloadDlg); downloadDlg = null;
        closeSheet();
    }

    private void dismissQuiet(Dialog d) {
        if (d != null) { try { d.dismiss(); } catch (Exception ignored) { } }
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
        if (key.equals("accent")) {
            int[] ac = accentColors();
            return ac[dark ? 1 : 0];
        }
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
            if (!isDark()) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= 26) flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
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
        t.setText(brandSpan(text));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(color);
        t.setTypeface(bold ? TF_BOLD : TF_REG);
        tnum(t);
        return t;
    }

    // Números tabulares: las cifras no "bailan" al actualizarse (Inter soporta tnum)
    private void tnum(TextView t) {
        try { t.setFontFeatureSettings("tnum"); } catch (Exception ignored) { }
    }

    // Botón de icono con área táctil de 48dp (icono visual de 38dp)
    private FrameLayout iconBtn(int iconRes, String desc, View.OnClickListener click) {
        FrameLayout wrap = new FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setImageResource(iconRes);
        iv.setColorFilter(col("text_secondary"), android.graphics.PorterDuff.Mode.SRC_IN);
        iv.setBackground(inputSurface(20));
        iv.setPadding(dp(9), dp(9), dp(9), dp(9));
        FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(dp(38), dp(38), Gravity.CENTER);
        wrap.addView(iv, ilp);
        wrap.setClickable(true);
        wrap.setFocusable(true);
        wrap.setContentDescription(desc);
        wrap.setOnClickListener(click);
        pressScale(wrap);
        return wrap;
    }

    // Fondo con ripple sutil (respeta el radio de la tarjeta)
    private android.graphics.drawable.Drawable ripple(int contentColor, float radiusDp) {
        int rc = isDark() ? 0x26FFFFFF : 0x1F000000;
        try {
            return new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(rc),
                    rounded(contentColor, radiusDp),
                    rounded(0xFFFFFFFF, radiusDp));
        } catch (Exception e) {
            return rounded(contentColor, radiusDp);
        }
    }

    // Superficie de vidrio líquido: gradiente translúcido + brillo superior + hairline
    private Drawable glassBg(float radiusDp, boolean strong) {
        int alpha = strong ? (isDark() ? 198 : 210) : (isDark() ? 150 : 166);
        int top = isDark() ? Color.argb(alpha, 36, 36, 40) : Color.argb(alpha, 255, 255, 255);
        int bottom = isDark() ? Color.argb(alpha, 24, 24, 27) : Color.argb(alpha, 244, 246, 252);
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{top, bottom});
        base.setCornerRadius(dp(radiusDp));
        base.setStroke(1, isDark() ? 0x38FFFFFF : 0x80FFFFFF);
        GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{isDark() ? 0x22FFFFFF : 0x4DFFFFFF, 0x00FFFFFF});
        sheen.setCornerRadius(dp(radiusDp));
        return new LayerDrawable(new Drawable[]{base, sheen});
    }

    private Drawable surfaceBg(float radiusDp) {
        return blurOn ? glassBg(radiusDp, false) : rounded(col("card_bg"), radiusDp);
    }

    private Drawable inputSurface(float radiusDp) {
        return blurOn ? glassBg(radiusDp, true) : rounded(col("input_bg"), radiusDp);
    }

    private Drawable selectedSurface(float radiusDp) {
        return blurOn ? glassBg(radiusDp, true) : rounded(col("card_bg"), radiusDp);
    }

    private Drawable glassRipple(float radiusDp) {
        if (!blurOn) return ripple(col("card_bg"), radiusDp);
        int rc = isDark() ? 0x26FFFFFF : 0x1F000000;
        try {
            return new android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(rc),
                    glassBg(radiusDp, false),
                    rounded(0xFFFFFFFF, radiusDp));
        } catch (Exception e) {
            return glassBg(radiusDp, false);
        }
    }

    private boolean blurActive() {
        return blurOn;
    }

    // tvIos: igual que tv — letras SIEMPRE en Inter y los emojis con Apple Emoji
    // (span por rango, no cambiar el typeface del TextView entero)
    private TextView tvIos(String text, float sizeSp, boolean bold, int color) {
        return tv(text, sizeSp, bold, color);
    }

    private Drawable iconDrawable(int res, int sizeDp, int color) {
        Drawable d = getResources().getDrawable(res, getTheme()).mutate();
        d.setTint(color);
        d.setBounds(0, 0, dp(sizeDp), dp(sizeDp));
        return d;
    }

    private CharSequence iconText(int res, String text, int sizeDp, int color) {
        SpannableStringBuilder sb = new SpannableStringBuilder(" ");
        sb.setSpan(new CenteredImageSpan(iconDrawable(res, sizeDp, color)),
                0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(" ");
        sb.append(brandSpan(text));
        return sb;
    }

    private static class CenteredImageSpan extends ImageSpan {
        CenteredImageSpan(Drawable d) {
            super(d);
        }

        @Override
        public void draw(Canvas canvas, CharSequence text, int start, int end,
                         float x, int top, int y, int bottom, Paint paint) {
            Drawable d = getDrawable();
            if (d == null) {
                super.draw(canvas, text, start, end, x, top, y, bottom, paint);
                return;
            }
            Paint.FontMetricsInt fm = paint.getFontMetricsInt();
            int transY = y + (fm.ascent + fm.descent - d.getBounds().height()) / 2;
            canvas.save();
            canvas.translate(x, transY);
            d.draw(canvas);
            canvas.restore();
        }
    }

    private TextView tvIcon(int res, String text, float sizeSp, boolean bold, int color, int iconDp) {
        TextView t = tv(text, sizeSp, bold, color);
        t.setText(iconText(res, text, iconDp, color));
        return t;
    }

    // Rango de codepoints que viven en AppleEmoji.ttf (y solo ahí, no en Inter)
    private static final int[] APPLE_CPS = {
            0xFE0F, 0x20E3, 0x200D, 0x2705, 0x231B, 0x23F1, 0x23F3, 0x23F8, 0x274C,
            0x2764, 0x26A0, 0x2B07, 0x2B50, 0x1F1EA, 0x1F1FB, 0x1F389, 0x1F441,
            0x1F4B1, 0x1F4B3, 0x1F4B5, 0x1F4CA, 0x1F4CB, 0x1F4F4, 0x1F514,
            0x1F525, 0x1F64F, 0x1FA99
    };

    private boolean isApple(int cp) {
        for (int c : APPLE_CPS) if (c == cp) return true;
        return cp >= 0x1F000 && cp <= 0x1FAFF;
    }

    private static class AppleSpan extends TypefaceSpan {
        private final Typeface tf;
        AppleSpan(Typeface t) { super(""); tf = t; }
        @Override public void updateDrawState(TextPaint ds) { ds.setTypeface(tf); }
        @Override public void updateMeasureState(TextPaint ds) { ds.setTypeface(tf); }
    }

    private CharSequence brandSpan(String text) {
        if (text == null || text.length() == 0) return text;
        boolean hasEmo = false;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (isApple(cp)) { hasEmo = true; break; }
            i += Character.charCount(cp);
        }
        if (!hasEmo) return text;
        SpannableString ss = new SpannableString(text);
        int i = 0, n = text.length();
        while (i < n) {
            int cp = text.codePointAt(i);
            if (isApple(cp)) {
                int st = i;
                while (i < n) {
                    int c2 = text.codePointAt(i);
                    if (isApple(c2)) i += Character.charCount(c2); else break;
                }
                ss.setSpan(new AppleSpan(TF_EMOJI), st, i,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                i += Character.charCount(cp);
            }
        }
        return ss;
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
        e.putInt("accent_idx", accentIdx);
        e.putBoolean("lang_en", langEn);
        e.putBoolean("widget_clear", widgetClear);
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

    // true si ya pasó la hora de publicación (15:30 VET) en un día hábil
    private boolean afterBcvSlot() {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("America/Caracas"));
        int dow = c.get(Calendar.DAY_OF_WEEK);
        if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) return false;
        int h = c.get(Calendar.HOUR_OF_DAY), m = c.get(Calendar.MINUTE);
        return h > 15 || (h == 15 && m >= 30);
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

        // Reflejo líquido que recorre el vidrio al activar o cambiar de pestaña
        liquidSweep = new ImageView(this);
        liquidSweep.setScaleType(ImageView.ScaleType.FIT_XY);
        liquidSweep.setAlpha(0f);
        liquidSweep.setVisibility(View.GONE);
        GradientDrawable sweep = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x00FFFFFF, isDark() ? 0x26FFFFFF : 0x5CFFFFFF, 0x00FFFFFF});
        sweep.setCornerRadius(dp(31));
        liquidSweep.setImageDrawable(sweep);
        floatingBar.addView(liquidSweep, new FrameLayout.LayoutParams(dp(110), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_VERTICAL | Gravity.START));

        // Borde hairline de vidrio
        glassBorder = new ImageView(this);
        glassBorder.setScaleType(ImageView.ScaleType.FIT_XY);
        floatingBar.addView(glassBorder, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout barContent = new LinearLayout(this);
        barContent.setOrientation(LinearLayout.HORIZONTAL);
        floatingBar.addView(barContent, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        String[] names = new String[]{ S("Tasas", "Rates"), S("Gráficas", "Charts"), S("Conversor", "Convert"), S("Pagos", "Pay"), S("Acerca", "About") };
        int[] icons = {R.drawable.ic_tab_rates, R.drawable.ic_tab_charts, R.drawable.ic_tab_conv, R.drawable.ic_tab_pay, R.drawable.ic_tab_about};
        tabBtns = new LinearLayout[5];
        tabIcons = new ImageView[5];
        tabLabels = new TextView[5];

        for (int i = 0; i < 5; i++) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setClickable(true);
            item.setBackground(ripple(Color.TRANSPARENT, 20));
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

        if (blurOn) {
            floatingBar.post(new Runnable() {
                public void run() { playGlassSweep(); }
            });
        }
    }

    private void playGlassSweep() {
        if (liquidSweep == null || !blurOn || floatingBar == null) return;
        int w = floatingBar.getWidth();
        if (w <= 0) return;
        liquidSweep.animate().cancel();
        liquidSweep.setTranslationX(-dp(120));
        liquidSweep.setAlpha(0f);
        liquidSweep.setVisibility(View.VISIBLE);
        liquidSweep.animate().alpha(0.9f).translationX(w + dp(12))
                .setDuration(760).setInterpolator(decel)
                .withEndAction(new Runnable() {
                    public void run() { liquidSweep.setAlpha(0f); }
                }).start();
    }

    private void applyBarBackground() {
        if (blurActive()) {
            int alpha = 210;
            int glass = isDark() ? Color.argb(alpha, 28, 28, 30) : Color.argb(alpha, 255, 255, 255);
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

            liquidSweep.setVisibility(View.VISIBLE);
        } else {
            floatingBar.setBackground(rounded(col("card_bg"), 31));
            floatingBar.setClipToOutline(true);
            blurBg.setVisibility(View.GONE);
            blurBg.setImageDrawable(null);
            glassSheen.setVisibility(View.GONE);
            glassBorder.setVisibility(View.GONE);
            if (liquidSweep != null) { liquidSweep.setAlpha(0f); liquidSweep.setVisibility(View.GONE); }
            if (lastBlurBitmap != null) { lastBlurBitmap.recycle(); lastBlurBitmap = null; }
        }
    }

    // ================= MOTOR DE DESENFOQUE (estático por eventos) =================
    // El vidrio es tan denso que no necesita seguir el scroll en vivo (como iOS,
    // que solo muestrea el contexto). Se recalcula SOLO en eventos clave:
    // cambio de pestaña, temas, datos nuevos, expandir/colapsar. Cero trabajo
    // durante el scroll = cero tirones.

    private void scheduleBlur() {
        if (!blurActive() || blurPending) return;
        blurPending = true;
        pager.postDelayed(new Runnable() {
            public void run() { blurPending = false; doBlur(); }
        }, 90);
    }

    // Durante el scroll: marca pendiente y deja que el ticker capture a
    // ritmo fijo (~5 fps). Sin bitmap churn, sin capturas por frame.
    private void nudgeBlur() {
        if (!blurActive()) return;
        blurDirty = true;
        if (!tickerOn) {
            tickerOn = true;
            ui.postDelayed(blurTick, 60);
        }
    }

    private void doBlur() {
        if (!blurActive() || floatingBar == null || pager == null) return;
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
        card.setBackground(surfaceBg(22));
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
        e.setTypeface(TF_REG);
        e.setTextColor(col("text_primary"));
        e.setHintTextColor(col("text_secondary"));
        e.setTextSize(14);
        e.setBackground(inputSurface(12));
        e.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(10));
        parent.addView(e, lp);
        return e;
    }

    private EditText bankField(LinearLayout parent, String label, String val) {
        final EditText e = etField(parent, label, "");
        String code = S7BQr.codeFor(val);
        if (code != null) {
            e.setTag(code);
            e.setText(code + " · " + S7BQr.bankName(code));
        } else {
            e.setText(val == null ? "" : val);
        }
        e.setFocusable(false);
        e.setFocusableInTouchMode(false);
        e.setClickable(true);
        e.setLongClickable(false);
        e.setCursorVisible(false);
        e.setInputType(InputType.TYPE_NULL);
        e.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { showBankChooser(e); }
        });
        return e;
    }

    private void showBankChooser(final EditText target) {
        final Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(surfaceBg(22));
        int p = dp(20);
        card.setPadding(p, p, p, dp(12));
        card.addView(tv(S("Elegir banco", "Choose bank"), 18, true, col("text_primary")));
        card.addView(tv(S("Busca por nombre o código SUPEN", "Search by name or SUPEN code"), 12, false, col("text_secondary")));

        final EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        search.setTypeface(TF_REG);
        search.setTextColor(col("text_primary"));
        search.setHintTextColor(col("text_secondary"));
        search.setHint(iconText(R.drawable.ic_search, S("Buscar banco o código…", "Search bank or code…"), 16, col("text_secondary")));
        search.setBackground(inputSurface(12));
        search.setPadding(dp(12), dp(9), dp(12), dp(9));
        search.setInputType(InputType.TYPE_CLASS_TEXT);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.setMargins(0, dp(12), 0, dp(8));
        card.addView(search, slp);

        final ScrollView sv = new ScrollView(this);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        fillBankList(list, "", target, d);
        sv.addView(list);
        card.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(340)));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            public void afterTextChanged(Editable s) {
                fillBankList(list, s.toString(), target, d);
                sv.scrollTo(0, 0);
            }
        });

        d.setContentView(card, new ViewGroup.LayoutParams(dp(320), ViewGroup.LayoutParams.WRAP_CONTENT));
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.45f;
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        d.setCanceledOnTouchOutside(true);
        d.show();
    }

    private void fillBankList(final LinearLayout list, String query, final EditText target, final Dialog d) {
        list.removeAllViews();
        int shown = 0;
        for (int i = 0; i < S7BQr.BANKS.length; i++) {
            final String code = S7BQr.BANKS[i][0];
            final String name = S7BQr.BANKS[i][1];
            if (!S7BQr.bankMatches(code, name, query)) {
                continue;
            }
            shown++;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(ripple(col("input_bg"), 12));
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            TextView codeTv = tv(code, 12, true, col("accent"));
            row.addView(codeTv, new LinearLayout.LayoutParams(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(tv(name, 14, false, col("text_primary")), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    target.setTag(code);
                    target.setText(code + " · " + name);
                    d.dismiss();
                }
            });
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.setMargins(0, dp(6), 0, 0);
            list.addView(row, rlp);
        }
        if (shown == 0) {
            TextView empty = tvIcon(R.drawable.ic_search, S("Sin resultados", "No results"), 14, false, col("text_secondary"), 16);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(24), 0, dp(24));
            list.addView(empty);
        }
    }

    private void sheetActions(final LinearLayout c, final int t, final int profIdx) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (profIdx >= 0) {
            TextView del = tv(S("Eliminar", "Delete"), 14, true, col("red"));
            del.setPadding(0, dp(14), dp(16), dp(4));
            del.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { closeSheet(); confirmDelete(profIdx); }
            });
            row.addView(del);
        }
        View sp = new View(this);
        row.addView(sp, new LinearLayout.LayoutParams(0, 1, 1));
        TextView save = tv(S("Guardar", "Save"), 14, true, col("accent"));
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
                    for (int i = 0; i < fields.length; i++) {
                        String val = ins[i].getText().toString().trim();
                        Object tag = ins[i].getTag();
                        if (tag instanceof String && ((String) tag).length() > 0) {
                            val = (String) tag;
                        }
                        if (t == 0 && fields[i].equals("Banco")) {
                            String code = S7BQr.codeFor(val);
                            if (code == null) {
                                Toast.makeText(MainActivity.this, S("Elige un banco válido", "Choose a valid bank"), Toast.LENGTH_SHORT).show();
                                return;
                            }
                            val = code;
                        }
                        nv.put(fields[i], val);
                    }
                    JSONObject prof = new JSONObject();
                    prof.put("t", PAY_TYPES[t][0]);
                    prof.put("v", nv);
                    JSONArray a = loadProfiles();
                    if (profIdx >= 0 && profIdx < a.length()) a.put(profIdx, prof);
                    else a.put(prof);
                    saveProfiles(a);
                    closeSheet();
                    Toast.makeText(MainActivity.this, S("Perfil guardado", "Profile saved"), Toast.LENGTH_SHORT).show();
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
        final LinearLayout c = sheet((profIdx >= 0 ? S("Editar · ", "Edit · ") : S("Nuevo · ", "New · ")) + ptName(t));
        for (int i = 0; i < fields.length; i++) {
            final int fi = i;
            final EditText fe;
            if (t == 0 && fields[i].equals("Banco")) {
                fe = bankField(c, ptField(t, i), v.optString(fields[i], ""));
            } else {
                fe = etField(c, ptField(t, i), v.optString(fields[i], ""));
            }
            TextView cp = tv(S("⧉ Copiar", "⧉ Copy"), 12, true, col("accent"));
            cp.setPadding(dp(2), 0, 0, dp(10));
            cp.setOnClickListener(new View.OnClickListener() {
                public void onClick(View vv) { copyClip(ptField(t, fi), fe.getText().toString(), fe); }
            });
            c.addView(cp);
        }
        sheetActions(c, t, profIdx);
    }

    private void confirmDelete(final int idx) {
        final LinearLayout c = sheet(S("Eliminar perfil", "Delete profile"));
        c.addView(tv(S("¿Seguro que quieres eliminar este perfil?", "Delete this profile?"), 14, false, col("text_secondary")));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.END);
        TextView no = tv(S("Cancelar", "Cancel"), 14, true, col("text_secondary"));
        no.setPadding(dp(16), dp(14), dp(16), dp(4));
        no.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { closeSheet(); }
        });
        TextView yes = tv(S("Eliminar", "Delete"), 14, true, col("red"));
        yes.setPadding(dp(16), dp(14), 0, dp(4));
        yes.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                JSONArray a = loadProfiles();
                JSONArray b = new JSONArray();
                for (int i = 0; i < a.length(); i++) if (i != idx) b.put(a.opt(i));
                saveProfiles(b);
                closeSheet();
                Toast.makeText(MainActivity.this, S("Perfil eliminado", "Profile deleted"), Toast.LENGTH_SHORT).show();
            }
        });
        row.addView(no);
        row.addView(yes);
        c.addView(row);
    }

    private void showTypeChooser() {
        final LinearLayout c = sheet(S("Nuevo perfil", "New profile"));
        c.addView(tv(S("Elige el tipo de perfil", "Choose a profile type"), 13, false, col("text_secondary")));
        for (int i = 0; i < PAY_TYPES.length; i++) {
            final int ti = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(inputSurface(14));
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            TextView b = tv(PAY_TYPES[i][2], 13, true, Color.WHITE);
            b.setGravity(Gravity.CENTER);
            b.setBackground(rounded(col(PAY_TYPES[i][3]), 10));
            row.addView(b, new LinearLayout.LayoutParams(dp(34), dp(34)));
            TextView nm = tv(ptName(i), 15, true, col("text_primary"));
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

    private void exportProfiles() {
        try {
            Intent it = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            it.addCategory(Intent.CATEGORY_OPENABLE);
            it.setType("application/json");
            it.putExtra(Intent.EXTRA_TITLE, "perfiles_backup.json");
            startActivityForResult(it, REQ_SAVE_JSON);
        } catch (Exception e) {
            Toast.makeText(this, S("No se encontró un gestor de archivos", "No file manager found"), Toast.LENGTH_LONG).show();
        }
    }

    private void importProfiles() {
        try {
            Intent it = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            it.addCategory(Intent.CATEGORY_OPENABLE);
            it.setType("*/*");
            startActivityForResult(it, REQ_PICK_JSON);
        } catch (Exception e) {
            Toast.makeText(this, S("No se encontró un gestor de archivos", "No file manager found"), Toast.LENGTH_LONG).show();
        }
    }

    // Exporta el historial de tasas a CSV vía SAF (Excel / Google Sheets)
    private void exportHistoryCsv() {
        try {
            Intent it = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            it.addCategory(Intent.CATEGORY_OPENABLE);
            it.setType("text/csv");
            it.putExtra(Intent.EXTRA_TITLE, "historial_super_cambio.csv");
            startActivityForResult(it, REQ_SAVE_CSV);
        } catch (Exception e) {
            Toast.makeText(this, S("No se encontró un gestor de archivos", "No file manager found"), Toast.LENGTH_LONG).show();
        }
    }

    private void appendCsv(StringBuilder sb, String name, JSONArray h) {
        if (h == null) return;
        java.text.SimpleDateFormat df = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", new java.util.Locale("es", "VE"));
        df.setTimeZone(java.util.TimeZone.getTimeZone("America/Caracas"));
        try {
            for (int i = 0; i < h.length(); i++) {
                JSONObject o = h.getJSONObject(i);
                sb.append(name.replace(",", " ")).append(',')
                  .append('"').append(df.format(new java.util.Date(o.optLong("t")))).append('"').append(',')
                  .append(String.format(java.util.Locale.US, "%.4f", o.optDouble("v", 0))).append('\n');
            }
        } catch (Exception ignored) { }
    }

    private void writeHistoryCsv(Uri uri) {
        java.io.OutputStream os = null;
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("tasa,fecha,valor_bs\n");
            appendCsv(sb, S("Dólar BCV", "BCV Dollar"), histUsd);
            appendCsv(sb, S("Dólar paralelo", "Parallel dollar"), histPar);
            appendCsv(sb, S("Euro BCV", "BCV Euro"), histEur);
            appendCsv(sb, "USDT", histUsdt);
            os = getContentResolver().openOutputStream(uri);
            if (os == null) throw new Exception("stream");
            os.write(sb.toString().getBytes("UTF-8"));
            Toast.makeText(this, S("Historial exportado", "History exported"), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, S("No se pudo guardar el CSV", "Couldn't save the CSV"), Toast.LENGTH_SHORT).show();
        } finally {
            if (os != null) { try { os.close(); } catch (Exception ignored) { } }
        }
    }

    // Valida y aplica un respaldo JSON de perfiles: {"perfiles":[...]} o array crudo
    private void applyImportJson(String raw) {
        try {
            raw = raw.trim();
            if (raw.length() > 0 && raw.charAt(0) == '\uFEFF') raw = raw.substring(1);
            JSONArray a;
            JSONObject wrap = null;
            try { wrap = new JSONObject(raw); } catch (Exception ex) { }
            if (wrap != null && wrap.has("perfiles")) a = wrap.optJSONArray("perfiles");
            else a = new JSONArray(raw);
            JSONArray clean = new JSONArray();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null || !o.has("t") || !o.has("v")) continue;
                String tk = o.optString("t");
                boolean known = false;
                for (int k = 0; k < PAY_TYPES.length; k++) {
                    if (PAY_TYPES[k][0].equals(tk)) { known = true; break; }
                }
                if (known) clean.put(o);
            }
            if (clean.length() == 0) {
                Toast.makeText(this, S("Formato no válido", "Invalid format"), Toast.LENGTH_SHORT).show();
                return;
            }
            saveProfiles(clean);
            pageProfiles.refresh();
            Toast.makeText(this, clean.length() + S(" perfiles importados", " profiles imported"), Toast.LENGTH_SHORT).show();
        } catch (Exception ex) {
            Toast.makeText(this, S("Formato no válido", "Invalid format"), Toast.LENGTH_SHORT).show();
        }
    }

    private class PageProfiles {
        ScrollView root;
        LinearLayout list;
        TextView empty;
        EditText search;

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

            box.addView(tv(S("Pagos", "Pay"), 28, true, col("text_primary")));
            box.addView(tv(S("Tus perfiles de cobro, guardados solo en este teléfono.", "Your payment profiles, stored only on this phone."), 13, false, col("text_secondary")));

            Button addBtn = new Button(MainActivity.this);
            addBtn.setText(iconText(R.drawable.ic_plus, S("Agregar perfil", "Add profile"), 16, Color.WHITE));
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

            // Respaldo de perfiles
            LinearLayout bkRow = new LinearLayout(MainActivity.this);
            bkRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams bkLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bkLp.setMargins(0, dp(4), 0, dp(4));
            box.addView(bkRow, bkLp);
            TextView ex = tvIcon(R.drawable.ic_upload, S("Exportar", "Export"), 14, true, col("accent"), 17);
            ex.setGravity(Gravity.CENTER);
            ex.setBackground(inputSurface(12));
            LinearLayout.LayoutParams exLp = new LinearLayout.LayoutParams(0, dp(42), 1);
            exLp.setMargins(0, 0, dp(6), 0);
            bkRow.addView(pressScale(ex), exLp);
            ex.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { exportProfiles(); }
            });
            TextView im = tvIcon(R.drawable.ic_download, S("Importar", "Import"), 14, true, col("accent"), 17);
            im.setGravity(Gravity.CENTER);
            im.setBackground(inputSurface(12));
            LinearLayout.LayoutParams imLp = new LinearLayout.LayoutParams(0, dp(42), 1);
            imLp.setMargins(dp(6), 0, 0, 0);
            bkRow.addView(pressScale(im), imLp);
            im.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { importProfiles(); }
            });

            // Buscar/filtrar perfiles
            search = new EditText(MainActivity.this);
            search.setSingleLine(true);
            search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            search.setTypeface(TF_REG);
            search.setTextColor(col("text_primary"));
            search.setHintTextColor(col("text_secondary"));
            search.setHint(iconText(R.drawable.ic_search, S("Buscar perfiles…", "Search profiles…"), 16, col("text_secondary")));
            search.setBackground(inputSurface(14));
            search.setPadding(dp(14), dp(11), dp(14), dp(11));
            LinearLayout.LayoutParams seLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            seLp.setMargins(0, dp(12), 0, 0);
            box.addView(search, seLp);
            search.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                public void onTextChanged(CharSequence s, int a, int b, int c) { }
                public void afterTextChanged(Editable s) { refresh(); }
            });

            list = new LinearLayout(MainActivity.this);
            list.setOrientation(LinearLayout.VERTICAL);
            box.addView(list);

            empty = tvIos("", 13, false, col("text_secondary"));
            empty.setText(iconText(R.drawable.ic_card, S("Aún no tienes perfiles.\nAgrega tu Pago móvil, cuenta de banco, Binance o Zelle para tenerlos a mano.", "No profiles yet.\nAdd your mobile payment, bank account, Binance or Zelle to keep them handy."), 16, col("text_secondary")));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(40), 0, 0);
            box.addView(empty);

            refresh();
        }

        void refresh() {
            list.removeAllViews();
            JSONArray arr = loadProfiles();
            if (search != null) search.setVisibility(arr.length() == 0 ? View.GONE : View.VISIBLE);
            String q = search != null ? search.getText().toString().trim().toLowerCase() : "";
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, dp(10), 0, 0);
            int shown = 0;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                if (q.length() > 0 && !profileMatches(o, q)) continue;
                list.addView(profileCard(o, i), lp);
                shown++;
            }
            if (shown == 0 && q.length() > 0) {
                empty.setText(iconText(R.drawable.ic_search, S("Sin resultados para tu búsqueda.", "No results for your search."), 16, col("text_secondary")));
            } else {
                empty.setText(iconText(R.drawable.ic_card, S("Aún no tienes perfiles.\nAgrega tu Pago móvil, cuenta de banco, Binance o Zelle para tenerlos a mano.", "No profiles yet.\nAdd your mobile payment, bank account, Binance or Zelle to keep them handy."), 16, col("text_secondary")));
            }
            empty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
            nudgeBlur();
        }

        // Coincidencia por nombre de tipo, insignia o cualquier campo (sin mayúsculas)
        private boolean profileMatches(JSONObject o, String q) {
            int t = typeIndexOf(o.optString("t", "pmov"));
            if (ptName(t).toLowerCase().contains(q) || PAY_TYPES[t][2].toLowerCase().contains(q)) return true;
            JSONObject v = o.optJSONObject("v");
            if (v == null) return false;
            String[] fields = PAY_TYPES[t][4].split("\u0001");
            for (int i = 0; i < fields.length; i++) {
                String val = v.optString(fields[i], "");
                if (val.length() > 0 && val.toLowerCase().contains(q)) return true;
                if (t == 0 && fields[i].equals("Banco") && val.length() > 0) {
                    if (S7BQr.bankName(val).toLowerCase().contains(q)) return true;
                }
            }
            return false;
        }

        View profileCard(final JSONObject o, final int idx) {
            final int t = typeIndexOf(o.optString("t", "pmov"));
            String[] fields = PAY_TYPES[t][4].split("\u0001");
            JSONObject v = o.optJSONObject("v");
            if (v == null) v = new JSONObject();

            LinearLayout card = new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackground(glassRipple(18));
            card.setPadding(dp(16), dp(14), dp(16), dp(14));

            TextView badge = tv(PAY_TYPES[t][2], 14, true, Color.WHITE);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(rounded(col(PAY_TYPES[t][3]), 12));
            card.addView(badge, new LinearLayout.LayoutParams(dp(40), dp(40)));

            LinearLayout col = new LinearLayout(MainActivity.this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(tv(ptName(t), 15, true, col("text_primary")));
            final String[] fieldsF = fields;
            String firstVal = v.optString(fields[0], "");
            if (t == 0) firstVal = S7BQr.bankName(firstVal);
            String sub = v.optString(fields[fields.length - 1], "") + " · " + firstVal;
            final TextView st = tv(sub, 13, false, col("text_secondary"));
            st.setSingleLine(true);
            st.setEllipsize(TextUtils.TruncateAt.END);
            st.setClickable(true);
            st.setOnClickListener(new View.OnClickListener() {
                public void onClick(View vv) {
                    JSONObject vo = o.optJSONObject("v");
                    String val = (vo == null ? "" : vo.optString(fieldsF[0], ""));
                    if (val.length() > 0) copyClip(ptField(t, 0), val, st);
                }
            });
            col.addView(st);
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            clp.setMargins(dp(12), 0, dp(8), 0);
            card.addView(col, clp);

            if (t == 0) {
                View qb = iconBtn(R.drawable.ic_qr, S("Ver QR", "View QR"), new View.OnClickListener() {
                    public void onClick(View vv) { showQr(idx); }
                });
                card.addView(qb, new LinearLayout.LayoutParams(dp(44), dp(44)));
                ((LinearLayout.LayoutParams) qb.getLayoutParams()).setMargins(0, 0, dp(2), 0);
            }

            // Botón compartir: genera imagen con logo y nombre de la app
            View sh = iconBtn(R.drawable.ic_share, S("Compartir perfil", "Share profile"), new View.OnClickListener() {
                public void onClick(View vv) { shareProfile(idx); }
            });
            card.addView(sh, new LinearLayout.LayoutParams(dp(44), dp(44)));

            card.setOnClickListener(new View.OnClickListener() {
                public void onClick(View view) { showEdit(t, idx); }
            });
            card.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View view) { profileOptions(idx); return true; }
            });
            return pressScale(card);
        }
    }

    // ================= OPCIONES DE PERFIL (mover / eliminar) =================

    // Long-press en la tarjeta: mover arriba/abajo o eliminar
    private void profileOptions(final int idx) {
        JSONArray arr = loadProfiles();
        if (idx < 0 || idx >= arr.length()) return;
        JSONObject o = arr.optJSONObject(idx);
        int t = typeIndexOf(o != null ? o.optString("t", "pmov") : "pmov");
        final LinearLayout c = sheet(S("Opciones · ", "Options · ") + ptName(t));
        if (idx > 0) {
            TextView up = tvIcon(R.drawable.ic_chevron_up, S("Mover arriba", "Move up"), 15, true, col("accent"), 18);
            up.setPadding(dp(4), dp(14), dp(4), dp(14));
            up.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { closeSheet(); moveProfile(idx, -1); }
            });
            c.addView(up);
        }
        if (idx < arr.length() - 1) {
            TextView down = tvIcon(R.drawable.ic_chevron_down, S("Mover abajo", "Move down"), 15, true, col("accent"), 18);
            down.setPadding(dp(4), dp(14), dp(4), dp(14));
            down.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { closeSheet(); moveProfile(idx, 1); }
            });
            c.addView(down);
        }
        TextView del = tvIcon(R.drawable.ic_trash, S("Eliminar perfil", "Delete profile"), 15, true, col("red"), 18);
        del.setPadding(dp(4), dp(14), dp(4), dp(4));
        del.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { closeSheet(); confirmDelete(idx); }
        });
        c.addView(del);
    }

    // Intercambia el perfil idx con su vecino (dir = -1 arriba, +1 abajo)
    private void moveProfile(int idx, int dir) {
        JSONArray a = loadProfiles();
        int j = idx + dir;
        if (idx < 0 || idx >= a.length() || j < 0 || j >= a.length()) return;
        JSONObject me = a.optJSONObject(idx);
        JSONObject other = a.optJSONObject(j);
        if (me == null || other == null) return;
        try {
            a.put(idx, other);
            a.put(j, me);
            saveProfiles(a);
            Toast.makeText(this, S("Orden actualizado", "Order updated"), Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) { }
    }

    // ================= COMPARTIR PERFIL (imagen con marca) =================

    // ===== Helpers de dibujo para las imágenes compartidas =====
    // Inter real (no Roboto), centrado vertical EXACTO por FontMetrics y auto-fit de líneas largas.

    private Paint siPaint(int colr, float size, boolean bold) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(colr);
        p.setTextSize(size);
        p.setTypeface(bold ? TF_BOLD : TF_MED);
        return p;
    }

    private Paint siLight(int colr, float size) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(colr);
        p.setTextSize(size);
        p.setTypeface(TF_REG);
        return p;
    }

    private void siTypeface(Paint p, boolean bold) {
        p.setTypeface(bold ? TF_BOLD : TF_REG);
    }

    // Baseline que centra la tinta del texto en el punto vertical dado
    private float siBase(Paint p, float centerY) {
        Paint.FontMetrics fm = p.getFontMetrics();
        return centerY - (fm.ascent + fm.descent) / 2f;
    }

    // Reduce el textSize hasta que el texto quepa en max px; devuelve el tamaño final
    private float siFit(Paint p, String t, float max) {
        float w = p.measureText(t);
        if (w > max && w > 0) p.setTextSize(p.getTextSize() * max / w);
        return p.getTextSize();
    }

    private void siCardShadow(Paint p) {
        try { p.setShadowLayer(20, 0, 8, isDark() ? 0x40000000 : 0x33000000); } catch (Exception ignored) { }
    }

    // Logo real del launcher recortado con esquinas iOS; devuelve el radio usado
    private float siLogo(Canvas cv, float cx, float cy, float r) {
        cv.save();
        Path clip = new Path();
        float rad = r * 0.4474f;
        clip.addRoundRect(new RectF(cx - r, cy - r, cx + r, cy + r), rad, rad, Path.Direction.CW);
        cv.clipPath(clip);
        Drawable appIc = logoDrawable();
        appIc.setBounds((int) (cx - r), (int) (cy - r), (int) (cx + r), (int) (cy + r));
        appIc.draw(cv);
        cv.restore();
        return rad;
    }

     private void shareProfile(int idx) {
        JSONArray arr = loadProfiles();
        if (idx < 0 || idx >= arr.length()) return;
        JSONObject o = arr.optJSONObject(idx);
        if (o == null) return;
        int t = typeIndexOf(o.optString("t", "pmov"));
        String[] fields = PAY_TYPES[t][4].split("\u0001");
        JSONObject v = o.optJSONObject("v");
        if (v == null) v = new JSONObject();

        int W = 1000, pad = 56, headH = 170, rowH = 118, footH = 130;
        int H = pad + headH + 26 + fields.length * rowH + footH;

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
        siCardShadow(cardP);
        cv.drawRoundRect(pad, pad, W - pad, H - pad, 44, 44, cardP);

        // Logo real de la app, esquinas iOS (igual que en Tasas y Conversor)
        float cx = pad + 70, cy = pad + 78, r = 58;
        siLogo(cv, cx, cy, r);

        // Nombre de la app + tipo de perfil (baseline centrado en su slot)
        float tx = cx + 96;
        Paint titleP = siPaint(priCol, 50, true);
        cv.drawText("Super Cambio", tx, siBase(titleP, pad + 56), titleP);
        Paint typeP = siLight(secCol, 34);
        cv.drawText(PAY_TYPES[t][1], tx, siBase(typeP, pad + 106), typeP);

        // Separador
        Paint sepP = new Paint();
        sepP.setColor(sepCol);
        sepP.setStrokeWidth(3);
        cv.drawLine(pad + 40, pad + headH, W - pad - 40, pad + headH, sepP);

        // Filas de datos, centradas por métrica de fuente
        float y = pad + headH + 26;
        Paint lbP = siLight(secCol, 29);
        TextPaint vaP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        vaP.setColor(priCol);
        vaP.setTextSize(44);
        vaP.setFakeBoldText(false);
        siTypeface(vaP, true);
        float avail = W - 2 * pad - 80;
        for (int i = 0; i < fields.length; i++) {
            cv.drawText(ptField(t, i), pad + 40, siBase(lbP, y + 26), lbP);
            String val = v.optString(fields[i], "").trim();
            if (val.isEmpty()) val = S("No configurado", "Not set");
            CharSequence ell = TextUtils.ellipsize(val, vaP, avail, TextUtils.TruncateAt.END);
            cv.drawText(ell.toString(), pad + 40, siBase(vaP, y + 76), vaP);
            y += rowH;
        }

        // Pie con marca
        Paint footP = siLight(secCol, 30);
        footP.setTextAlign(Paint.Align.CENTER);
        cv.drawText(S("Generado con Super Cambio · © 2026 Jdvibes", "Generated with Super Cambio · © 2026 Jdvibes"), W / 2f, H - pad - 40, footP);

        try {
            File f = new File(getCacheDir(), "compartir.png");
            FileOutputStream fo = new FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fo);
            fo.close();
            bmp.recycle();

            StringBuilder txt = new StringBuilder(ptName(t)).append("\n");
            for (int i = 0; i < fields.length; i++) {
                String val = v.optString(fields[i], "");
                if (!val.isEmpty()) txt.append(ptField(t, i)).append(": ").append(val).append("\n");
            }
            txt.append("\n").append(S("Enviado con Super Cambio", "Sent with Super Cambio"));

            Uri uri = Uri.parse("content://" + ShareProvider.AUTHORITY + "/compartir.png");
            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType("image/png");
            it.putExtra(Intent.EXTRA_STREAM, uri);
            it.putExtra(Intent.EXTRA_TEXT, txt.toString());
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(it, S("Compartir perfil", "Share profile")));
        } catch (Exception e) {
            Toast.makeText(this, S("No se pudo generar la imagen", "Couldn't generate the image"), Toast.LENGTH_SHORT).show();
        }
    }

    // ================= VER/COMPARTIR QR DE PAGO MÓVIL (S7B) =================

    private void showQr(int idx) {
        JSONArray arr = loadProfiles();
        if (idx < 0 || idx >= arr.length()) return;
        JSONObject o = arr.optJSONObject(idx);
        if (o == null) return;
        int t = typeIndexOf(o.optString("t", "pmov"));
        if (t != 0) return;
        JSONObject v = o.optJSONObject("v");
        if (v == null) v = new JSONObject();

        String bankVal = v.optString("Banco", "");
        String phoneVal = v.optString("Teléfono", "");
        String idVal = v.optString("Cédula", "");
        String nameVal = v.optString("Titular", "");
        String payload;
        try {
            payload = S7BQr.encode(bankVal, phoneVal, idVal, nameVal);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null || msg.length() == 0) msg = S("Completa banco, teléfono y cédula", "Complete bank, phone and ID");
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
            showEdit(t, idx);
            return;
        }

        final Bitmap qr = makeQr(payload);
        if (qr == null) {
            Toast.makeText(this, S("No se pudo generar el QR", "Couldn't generate the QR"), Toast.LENGTH_SHORT).show();
            return;
        }

        String phone = S7BQr.normalizePhone(phoneVal);
        String id = S7BQr.normalizeId(idVal);
        String bankName = S7BQr.bankName(bankVal);
        StringBuilder txt = new StringBuilder(S("Pago móvil", "Mobile payment"));
        txt.append("\n").append(bankName);
        if (phone != null) txt.append("\n").append(phone);
        if (id != null) txt.append("\n").append(id);
        if (nameVal != null && nameVal.trim().length() > 0) txt.append("\n").append(nameVal.trim());
        txt.append("\n\n").append(S("Enviado con Super Cambio", "Sent with Super Cambio"));

        final File f = new File(getCacheDir(), "perfil_qr.png");
        try {
            FileOutputStream fo = new FileOutputStream(f);
            qr.compress(Bitmap.CompressFormat.PNG, 100, fo);
            fo.close();
        } catch (Exception e) {
            qr.recycle();
            Toast.makeText(this, S("No se pudo guardar el QR", "Couldn't save the QR"), Toast.LENGTH_SHORT).show();
            return;
        }

        final String shareText = txt.toString();
        LinearLayout c = sheet(S("QR de Pago móvil", "Mobile payment QR"));
        c.addView(tv(S("Muéstrale este código al pagador para que lo escanee.", "Show this code to the payer so they can scan it."), 12, false, col("text_secondary")));
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(qr);
        iv.setAdjustViewBounds(true);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setBackgroundColor(Color.WHITE);
        iv.setPadding(dp(6), dp(6), dp(6), dp(6));
        LinearLayout.LayoutParams ivlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300));
        ivlp.setMargins(0, dp(10), 0, dp(8));
        c.addView(iv, ivlp);
        TextView info = tv(bankName + " · " + (phone == null ? phoneVal : phone), 12, false, col("text_secondary"));
        info.setGravity(Gravity.CENTER);
        c.addView(info);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.END);
        TextView close = tv(S("Cerrar", "Close"), 14, true, col("text_secondary"));
        close.setPadding(dp(16), dp(14), dp(16), dp(4));
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) { closeSheet(); }
        });
        row.addView(close);
        TextView share = tv(S("Compartir", "Share"), 14, true, col("accent"));
        share.setPadding(dp(16), dp(14), 0, dp(4));
        share.setOnClickListener(new View.OnClickListener() {
            public void onClick(View vv) {
                closeSheet();
                shareQrFile(f, shareText);
            }
        });
        row.addView(share);
        c.addView(row);
        if (curSheet != null) {
            curSheet.setOnDismissListener(new DialogInterface.OnDismissListener() {
                public void onDismiss(DialogInterface d) { qr.recycle(); }
            });
        }
    }

    private Bitmap makeQr(String payload) {
        try {
            com.google.zxing.qrcode.QRCodeWriter w = new com.google.zxing.qrcode.QRCodeWriter();
            java.util.Map<com.google.zxing.EncodeHintType, Object> hints =
                    new java.util.HashMap<com.google.zxing.EncodeHintType, Object>();
            hints.put(com.google.zxing.EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(com.google.zxing.EncodeHintType.ERROR_CORRECTION, com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.H);
            hints.put(com.google.zxing.EncodeHintType.MARGIN, 4);
            int size = 1000;
            com.google.zxing.common.BitMatrix mx =
                    w.encode(payload, com.google.zxing.BarcodeFormat.QR_CODE, size, size, hints);
            int[] px = new int[size * size];
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    px[y * size + x] = mx.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
                }
            }
            Bitmap qr = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            qr.setPixels(px, 0, size, 0, 0, size, size);
            try {
                Bitmap logo = BitmapFactory.decodeResource(getResources(), R.drawable.suiche7b);
                if (logo != null) {
                    Canvas cv = new Canvas(qr);
                    float cx = size / 2f;
                    float cy = size / 2f;
                    float box = size * 0.29f;
                    float logoSize = size * 0.22f;
                    Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
                    bg.setColor(Color.WHITE);
                    cv.drawRoundRect(new RectF(cx - box / 2f, cy - box / 2f, cx + box / 2f, cy + box / 2f), box * 0.12f, box * 0.12f, bg);
                    Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG);
                    lp.setFilterBitmap(true);
                    cv.drawBitmap(logo, null, new RectF(cx - logoSize / 2f, cy - logoSize / 2f, cx + logoSize / 2f, cy + logoSize / 2f), lp);
                    logo.recycle();
                }
            } catch (Throwable ignored) { }
            return qr;
        } catch (Exception e) {
            return null;
        }
    }

    private void shareQrFile(File f, String txt) {
        try {
            Uri uri = Uri.parse("content://" + ShareProvider.AUTHORITY + "/" + f.getName());
            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType("image/png");
            it.putExtra(Intent.EXTRA_STREAM, uri);
            it.putExtra(Intent.EXTRA_TEXT, txt);
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(it, S("Compartir QR", "Share QR")));
        } catch (Exception e) {
            Toast.makeText(this, S("No se pudo compartir el QR", "Couldn't share the QR"), Toast.LENGTH_SHORT).show();
        }
    }

    // ================= COMPARTIR TASAS (imagen con marca) =================

    // Copiar el bloque completo de tasas (lo que se ve en pantalla) al portapapeles
    private void copyAllRates() {
        if (bcvUsd <= 0 && paralelo <= 0 && bcvEur <= 0 && usdt <= 0) {
            Toast.makeText(this, S("Aún no hay datos para copiar", "No data to copy yet"), Toast.LENGTH_SHORT).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (bcvUsd > 0) sb.append(S("Dólar BCV", "BCV Dollar")).append(": ").append(fmt(bcvUsd)).append(BSU).append("\n");
        if (paralelo > 0) sb.append(S("Dólar paralelo", "Parallel dollar")).append(": ").append(fmt(paralelo)).append(BSU).append("\n");
        if (bcvEur > 0) sb.append(S("Euro BCV", "BCV Euro")).append(": ").append(fmt(bcvEur)).append(BSU).append("\n");
        if (usdt > 0) sb.append("USDT: ").append(fmt(usdt)).append(BSU).append("\n");
        java.text.SimpleDateFormat df = new java.text.SimpleDateFormat(
                langEn ? "MMM d, HH:mm" : "d MMM, HH:mm",
                langEn ? new java.util.Locale("en", "US") : new java.util.Locale("es", "VE"));
        df.setTimeZone(java.util.TimeZone.getTimeZone("America/Caracas"));
        sb.append("\n").append(df.format(new java.util.Date()));
        copyClip("Tasas", sb.toString().trim(), null);
    }

    private void shareRates() {
        if (bcvUsd <= 0 && paralelo <= 0 && bcvEur <= 0 && usdt <= 0) {
            Toast.makeText(this, S("Aún no hay datos para compartir", "No data to share yet"), Toast.LENGTH_SHORT).show();
            return;
        }
        boolean dk = isDark();
        int bgCol = dk ? 0xFF0E0E10 : 0xFFF2F3F7;
        int cardCol = dk ? 0xFF1C1C1E : Color.WHITE;
        int priCol = dk ? 0xFFF2F2F7 : 0xFF111114;
        int secCol = dk ? 0xFF98989F : 0xFF8E8E93;

        String[][] rows = new String[4][5]; // nombre | valor | color | badge | fuente
        rows[0] = new String[]{S("Dólar BCV", "BCV Dollar"), bcvUsd > 0 ? fmt(bcvUsd) + BSU : "—", "accent", "USD", "BCV"};
        rows[1] = new String[]{S("Dólar paralelo", "Parallel dollar"), paralelo > 0 ? fmt(paralelo) + BSU : "—", "green", "USD", S("Promedio", "Average")};
        rows[2] = new String[]{S("Euro BCV", "BCV Euro"), bcvEur > 0 ? fmt(bcvEur) + BSU : "—", "purple", "EUR", "BCV"};
        rows[3] = new String[]{"USDT Binance", usdt > 0 ? fmt(usdt) + BSU : "—", "orange", "USDT", "P2P"};

        int W = 1000, pad = 56, headH = 200, rowH = 122, footH = 130;
        int H = pad + headH + 36 + rowH * 4 + footH;

        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        cv.drawColor(bgCol);

        Paint cardP = new Paint(Paint.ANTI_ALIAS_FLAG);
        cardP.setColor(cardCol);
        siCardShadow(cardP);
        cv.drawRoundRect(pad, pad, W - pad, H - pad, 44, 44, cardP);

        // Logo real, esquinas redondeadas iOS
        float cx = pad + 70, cy = pad + 78, r = 58;
        siLogo(cv, cx, cy, r);

        float tx = cx + 96;
        Paint titleP = siPaint(priCol, 50, true);
        cv.drawText("Super Cambio", tx, siBase(titleP, pad + 56), titleP);
        Paint typeP = siLight(secCol, 34);
        SimpleDateFormat df = new SimpleDateFormat("d 'de' MMMM, yyyy", new Locale("es"));
        String fecha = S("Tasas de hoy · ", "Today's rates · ") + df.format(new Date());
        siFit(typeP, fecha, W - tx - 40);
        cv.drawText(fecha, tx, siBase(typeP, pad + 106), typeP);

        Paint sepP = new Paint();
        sepP.setColor(dk ? 0x24FFFFFF : 0x14000000);
        sepP.setStrokeWidth(3);
        cv.drawLine(pad + 40, pad + headH, W - pad - 40, pad + headH, sepP);

        Paint nmP = siPaint(priCol, 36, true);
        TextPaint vaP = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        vaP.setColor(priCol);
        vaP.setTextSize(44);
        vaP.setTypeface(TF_BOLD);
        vaP.setTextAlign(Paint.Align.RIGHT);
        Paint srcP = siLight(secCol, 27);
        float nameX = pad + 160;
        float valX = W - pad - 44;
        float avail = valX - nameX - 32;

        float y = pad + headH + 36;
        for (int i = 0; i < 4; i++) {
            float rowMid = y + rowH / 2f;

            // Insignia de color con el texto centrado EXACTO
            GradientDrawable bd = new GradientDrawable();
            bd.setShape(GradientDrawable.RECTANGLE);
            bd.setCornerRadius(22);
            int c = col(rows[i][2]);
            bd.setColor(Color.argb(46, Color.red(c), Color.green(c), Color.blue(c)));
            float bx = pad + 36, by = rowMid - 48;
            bd.setBounds((int) bx, (int) by, (int) (bx + 96), (int) (by + 96));
            bd.draw(cv);
            Paint bt = siPaint(c, 29, true);
            bt.setTextAlign(Paint.Align.CENTER);
            cv.drawText(rows[i][3], bx + 48, siBase(bt, rowMid), bt);

            // Nombre + fuente a la izquierda, valor alineado verticalmente al bloque
            cv.drawText(rows[i][0], nameX, siBase(nmP, y + 40), nmP);
            cv.drawText(rows[i][4], nameX, siBase(srcP, y + 84), srcP);
            CharSequence vEll = TextUtils.ellipsize(rows[i][1], vaP, avail, TextUtils.TruncateAt.END);
            cv.drawText(vEll.toString(), valX, siBase(vaP, rowMid), vaP);

            if (i < 3) cv.drawLine(pad + 40, y + rowH - 12, W - pad - 40, y + rowH - 12, sepP);
            y += rowH;
        }

        Paint footP = siLight(secCol, 30);
        footP.setTextAlign(Paint.Align.CENTER);
        cv.drawText(S("Generado con Super Cambio · © 2026 Jdvibes", "Generated with Super Cambio · © 2026 Jdvibes"), W / 2f, H - pad - 40, footP);

        try {
            File f = new File(getCacheDir(), "tasas.png");
            FileOutputStream fo = new FileOutputStream(f);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, fo);
            fo.close();
            bmp.recycle();

            StringBuilder txt = new StringBuilder(S("Tasas de hoy en Venezuela\n", "Today's rates in Venezuela\n"));
            for (int i = 0; i < 4; i++) txt.append(rows[i][0]).append(": ").append(rows[i][1]).append("\n");
            txt.append("\n").append(S("Enviado con Super Cambio", "Sent with Super Cambio"));

            Uri uri = Uri.parse("content://" + ShareProvider.AUTHORITY + "/tasas.png");
            Intent it = new Intent(Intent.ACTION_SEND);
            it.setType("image/png");
            it.putExtra(Intent.EXTRA_STREAM, uri);
            it.putExtra(Intent.EXTRA_TEXT, txt.toString());
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(it, S("Compartir tasas", "Share rates")));
        } catch (Exception e) {
            Toast.makeText(this, S("No se pudo generar la imagen", "Couldn't generate the image"), Toast.LENGTH_SHORT).show();
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
        if (blurOn) {
            floatingBar.post(new Runnable() {
                public void run() { playGlassSweep(); }
            });
        }
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
        String cardTitle;
        String num = "";

        RateCard(LinearLayout parent, String title, String source, String badge, final String colorKey) {
            cardTitle = title;
            card = new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(surfaceBg(18));
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
            value.setClickable(true);
            value.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { copyClip(cardTitle, num, value); }
            });
            card.addView(pressScale(value), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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
            String plain = s.replace('\u00A0', ' ');
            num = plain.endsWith(" Bs.") ? plain.substring(0, plain.length() - 4) : plain;
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
        ProgressBar statusSpin;
        RateCard usd, eur, usdtc, parc;

        LinearLayout nextBox;
        TextView nextCountdown, nextStatus, datePill;
        Button nextBtn;
        ImageView dateAct;
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

            box.addView(bigTitle(S("Tasas", "Rates")));
            box.addView(tv(S("Datos en vivo de Venezuela", "Live data for Venezuela"), 15, false, col("text_secondary")));

            LinearLayout statusRow = new LinearLayout(MainActivity.this);
            statusRow.setOrientation(LinearLayout.HORIZONTAL);
            statusRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams srlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            srlp.setMargins(0, dp(10), 0, 0);
            box.addView(statusRow, srlp);

            status = tvIos("", 13, false, col("text_secondary"));
            status.setText(iconText(R.drawable.ic_hourglass, S("Sin datos todavía", "No data yet"), 14, col("text_secondary")));
            statusRow.addView(status, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            statusSpin = new ProgressBar(MainActivity.this);
            statusSpin.setIndeterminate(true);
            statusSpin.setVisibility(View.GONE);
            LinearLayout.LayoutParams splp = new LinearLayout.LayoutParams(dp(16), dp(16));
            splp.setMargins(0, 0, dp(8), 0);
            statusRow.addView(statusSpin, 0, splp);
            if (Build.VERSION.SDK_INT >= 21) {
                try {
                    statusSpin.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(col("accent")));
                } catch (Exception ignored) { }
            }

            // Copiar todas las tasas de golpe
            View copyBtn = iconBtn(R.drawable.ic_copy, S("Copiar tasas", "Copy rates"), new View.OnClickListener() {
                public void onClick(View v) { copyAllRates(); }
            });
            statusRow.addView(copyBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));
            ((LinearLayout.LayoutParams) copyBtn.getLayoutParams()).setMargins(0, 0, dp(4), 0);

            // Compartir tasas como imagen con marca
            View shareBtn = iconBtn(R.drawable.ic_share, S("Compartir tasas", "Share rates"), new View.OnClickListener() {
                public void onClick(View v) { shareRates(); }
            });
            statusRow.addView(shareBtn, new LinearLayout.LayoutParams(dp(48), dp(48)));
            ((LinearLayout.LayoutParams) shareBtn.getLayoutParams()).setMargins(0, 0, dp(4), 0);

            Button refreshBtn = new Button(MainActivity.this);
            refreshBtn.setText(S("Actualizar", "Refresh"));
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
            final TextView pullHint = tvIos(S("↓  Desliza para actualizar", "↓  Pull to refresh"), 12, false, col("accent"));
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
                            pullHint.setText(pull >= 1f ? S("↻  Suelta para actualizar", "↻  Release to refresh") : S("↓  Desliza para actualizar", "↓  Pull to refresh"));
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

            usd = new RateCard(box, S("Dólar oficial", "Official dollar"), S("BCV", "BCV"), "$", "green");
            eur = new RateCard(box, S("Euro oficial", "Official euro"), S("BCV", "BCV"), "€", "purple");
            usdtc = new RateCard(box, "USDT", "Binance P2P", "₮", "orange");
            parc = new RateCard(box, S("Dólar paralelo", "Parallel dollar"), S("Promedio de mercado", "Market average"), "≋", "accent");

            buildNextRate(usd.card);
        }

        private void buildNextRate(LinearLayout usdCard) {
            View sep = new View(MainActivity.this);
            sep.setBackgroundColor(col("separator"));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.7f)));
            slp.setMargins(0, dp(14), 0, 0);
            usdCard.addView(sep, slp);

            nextBtn = new Button(MainActivity.this);
            nextBtn.setText(S("Ver próxima tasa  ▾", "View next rate  ▾"));
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

            nextStatus = tvIos("", 12, false, col("text_secondary"));
            nextStatus.setText(iconText(R.drawable.ic_clock, S("Estimado según el horario habitual del BCV (3:30 PM, día hábil).", "Estimated from the BCV's usual schedule (3:30 PM, business day)."), 13, col("text_secondary")));
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
            datePill.setBackground(inputSurface(14));
            datePill.setPadding(dp(14), 0, dp(14), 0);
            datePill.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { fetchNextManual(); }
            });
            drow.addView(pressScale(datePill), new LinearLayout.LayoutParams(0, dp(48), 1));

            dateAct = new ImageView(MainActivity.this);
            dateAct.setImageResource(R.drawable.ic_refresh);
            dateAct.setColorFilter(col("text_secondary"), android.graphics.PorterDuff.Mode.SRC_IN);
            dateAct.setBackground(inputSurface(14));
            dateAct.setElevation(0);
            dateAct.setPadding(dp(13), dp(13), dp(13), dp(13));
            dateAct.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { fetchNextManual(); }
            });
            LinearLayout.LayoutParams dalp = new LinearLayout.LayoutParams(dp(48), dp(48));
            dalp.setMargins(dp(10), 0, 0, 0);
            drow.addView(pressScale(dateAct), dalp);
        }

        private void fetchNextManual() {
            if (autoBusy) return;
            haptic(datePill);
            netErr = null;
            datePill.setText(S("Consultando…", "Checking…"));
            datePill.setTextColor(col("accent"));
            dateAct.setEnabled(false);
            dateAct.setAlpha(0.35f);
            autoFetchNextRate(true);
        }

        private void toggleNext() {
            if (nextBox.getVisibility() == View.GONE) {
                nextExpanded = true;
                nextBox.setVisibility(View.VISIBLE);
                nextBox.setAlpha(0f);
                nextBox.setTranslationY(-dp(8));
                nextBox.animate().alpha(1f).translationY(0f).setDuration(260).setInterpolator(decel).start();
                nextBtn.setText(S("Ocultar próxima tasa  ▴", "Hide next rate  ▴"));
                updateCountdown();
                startTicking();
            } else {
                nextExpanded = false;
                celebrateUntil = 0;
                dateAct.setEnabled(true);
                dateAct.setAlpha(1f);
                nextBox.animate().alpha(0f).setDuration(200).withEndAction(
                        new Runnable() { public void run() { nextBox.setVisibility(View.GONE); nextBox.setAlpha(1f); } }
                ).start();
                nextBtn.setText(S("Ver próxima tasa  ▾", "View next rate  ▾"));
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
                nextStatus.setText(iconText(R.drawable.ic_clock, S("Actualiza para calcular la próxima publicación del BCV.", "Refresh to estimate the BCV's next publication."), 13, col("text_secondary")));
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
                nextCountdown.setText(S("¡Atento!", "Stay tuned!"));
                datePill.setText(iconText(R.drawable.ic_eye, S("Ver nueva tasa", "View new rate"), 16, col("green")));
                datePill.setTextColor(col("green"));
                nextStatus.setText(iconText(R.drawable.ic_eye, S("La nueva tasa puede estar vigente desde ahora. Toca para verla.", "The new rate may be live now. Tap to view it."), 13, col("text_secondary")));
                if (!autoBusy) autoFetchNextRate(false);
                return;
            }
            datePill.setText(dstr);
            datePill.setTextColor(col("accent"));
            dateAct.setColorFilter(col("text_secondary"), android.graphics.PorterDuff.Mode.SRC_IN);
            nextCountdown.setTextColor(col("text_primary"));
            nextCountdown.setText(countdownText(diff));
            nextStatus.setText(iconText(R.drawable.ic_clock, S("Tiempo restante para la próxima publicación (estimado 3:30 PM, día hábil).", "Time left until the next publication (est. 3:30 PM, business day)."), 13, col("text_secondary")));
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
                        if (bcv > 0 && (Math.abs(bcv - bcvUsd) > 0.00001 || afterBcvSlot())) {
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
                                pageRates.nextStatus.setText(iconText(R.drawable.ic_sparkles, S("¡Nueva tasa publicada!", "New rate published!"), 13, col("green")));
                                nextCountdown.setTextColor(col("green"));
                                nextCountdown.setText(S("¡Nueva tasa publicada!", "New rate published!"));
                                nextCountdown.setScaleX(1.15f);
                                nextCountdown.setScaleY(1.15f);
                                nextCountdown.animate().scaleX(1f).scaleY(1f).setDuration(450).setInterpolator(pop).start();
                                SimpleDateFormat f = new SimpleDateFormat("HH:mm", new Locale("es"));
                                nextStatus.setText(iconText(R.drawable.ic_check_circle, S("El BCV publicó Bs. ", "BCV published Bs. ") + fmt(fRate) + S(" · detectado a las ", " · detected at ") + f.format(new Date()) + S(" (hora del teléfono)", " (phone time)"), 13, col("green")));
                                dateAct.setEnabled(true);
                                dateAct.setAlpha(1f);
                            } else {
                                dateAct.setEnabled(true);
                                dateAct.setAlpha(1f);
                                if (netErr != null) {
                                    nextStatus.setText(iconText(R.drawable.ic_wifi_off, S("Sin conexión con el BCV · revisando cada minuto…", "Can't reach the BCV · retrying every minute…"), 13, col("text_secondary")));
                                } else {
                                    int dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
                                    if (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY) {
                                        nextStatus.setText(iconText(R.drawable.ic_calendar, S("El BCV no publica fines de semana · próxima tasa: lunes a las 3:30 PM", "BCV doesn't publish on weekends · next rate: Monday at 3:30 PM"), 13, col("text_secondary")));
                                    } else {
                                        nextStatus.setText(iconText(R.drawable.ic_clock, S("El BCV aún no publica la nueva tasa · revisando cada minuto…", "BCV hasn't published the new rate yet · checking every minute…"), 13, col("text_secondary")));
                                    }
                                }
                            }
                        }
                    });
                }
            }).start();
        }

        void apply() {
            usd.setValueAnimated(bcvUsd > 0 ? fmt(bcvUsd) + BSU : "—");
            eur.setValueAnimated(bcvEur > 0 ? fmt(bcvEur) + BSU : "—");
            usdtc.setValueAnimated(usdt > 0 ? fmt(usdt) + BSU : "—");
            parc.setValueAnimated(paralelo > 0 ? fmt(paralelo) + BSU : "—");

            SimpleDateFormat f = new SimpleDateFormat("d MMM · HH:mm 'VET'", new Locale("es"));
            usd.date.setText(bcvUsd > 0 ? (tUsd > 0 ? "Dato del " + f.format(new Date(tUsd)) : "1 USD = " + fmt(bcvUsd) + BSU) : "Esperando datos…");
            eur.date.setText(bcvEur > 0 ? (tEur > 0 ? "Dato del " + f.format(new Date(tEur)) : "1 EUR = " + fmt(bcvEur) + BSU) : "Esperando datos…");
            usdtc.date.setText(usdt > 0 ? (tUsdt > 0 ? "Binance P2P · " + f.format(new Date(tUsdt)) : "1 USDT = " + fmt(usdt) + BSU) : "Esperando datos…");
            parc.date.setText(paralelo > 0 ? (tPar > 0 ? "Dato del " + f.format(new Date(tPar)) : "1 USD = " + fmt(paralelo) + BSU) : "Esperando datos…");

            usd.setPill(prevUsd, bcvUsd);
            eur.setPill(prevEur, bcvEur);
            usdtc.setPill(prevUsdt, usdt);
            parc.setPill(prevPar, paralelo);

            if (nextExpanded) updateCountdown();
        }

        void setBusy(boolean b) {
            if (statusSpin == null) return;
            if (b) {
                if (statusSpin.getVisibility() != View.VISIBLE) {
                    statusSpin.setVisibility(View.VISIBLE);
                    statusSpin.setAlpha(0f);
                    statusSpin.animate().alpha(1f).setDuration(150).start();
                }
            } else {
                statusSpin.animate().alpha(0f).setDuration(150).withEndAction(new Runnable() {
                    public void run() { statusSpin.setVisibility(View.GONE); statusSpin.setAlpha(1f); }
                }).start();
            }
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

            box.addView(bigTitle(S("Gráficas", "Charts")));
            box.addView(tv(S("Evolución local de cada tasa", "Local history of each rate"), 15, false, col("text_secondary")));

            segWrap = new LinearLayout(MainActivity.this);
            segWrap.setOrientation(LinearLayout.HORIZONTAL);
            segWrap.setBackground(inputSurface(10));
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
            rangeWrap.setBackground(inputSurface(10));
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

            chartTitle = tv(S("Dólar oficial", "Official dollar"), 17, true, col("text_primary"));
            LinearLayout.LayoutParams ctlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ctlp.setMargins(0, dp(18), 0, 0);
            box.addView(chartTitle, ctlp);

            LinearLayout chartCard = new LinearLayout(MainActivity.this);
            chartCard.setOrientation(LinearLayout.VERTICAL);
            chartCard.setBackground(surfaceBg(18));
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

            statMin = statBlock(stats, S("Mínimo", "Min"));
            statMax = statBlock(stats, S("Máximo", "Max"));
            statAvg = statBlock(stats, S("Promedio", "Avg"));
            statVar = statBlock(stats, S("Variación", "Change"));

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
            String[] names = {S("Dólar oficial", "Official dollar"), S("Euro oficial", "Official euro"), "USDT Binance", S("Dólar paralelo", "Parallel dollar")};
            chartTitle.setText(names[sel]);
            for (int i = 0; i < 4; i++) {
                TextView sv = (TextView) segWrap.getChildAt(i);
                if (i == sel) {
                    sv.setBackground(selectedSurface(8));
                    sv.setTextColor(col("text_primary"));
                } else {
                    sv.setBackground(null);
                    sv.setTextColor(col("text_secondary"));
                }
            }

            for (int i = 0; i < 4; i++) {
                TextView sv = (TextView) rangeWrap.getChildAt(i);
                if (i == rangeSel) {
                    sv.setBackground(selectedSurface(8));
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
                statCount.setText(n == 0 ? S("Aún no hay datos para esta tasa.", "No data for this rate yet.") : S("1 punto guardado · sigue abriendo la app para acumular datos.", "1 saved point · keep opening the app to collect data."));
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
                statCount.setText(S("Sin datos en este rango todavía.", "No data in this range yet."));
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
            String[] rLbl = new String[]{ S("últimos 7 días", "last 7 days"), S("último mes", "last month"), S("últimos 3 meses", "last 3 months"), S("todo el historial", "all time") };
            statCount.setText(m + S(" puntos · ", " points · ") + rLbl[rangeSel] + S(" · los datos se registran en cada apertura.", " · data is recorded on every open."));
            chart.setData(vals, times);
            chart.startReveal();
        }
    }

    private class ChartView extends View {
        private float[] vals;
        private long[] times;
        private float progress = 1f;
        private ValueAnimator reveal;

        // ventana visible: zoom (1 = todo el historial) y desplazamiento en unidades de punto
        private float zoom = 1f, startF = 0f;
        private float sX0, sY0, gPan0, gSpan = -1f, lastUpX, scrubX, cx0;
        private boolean scrub, scrubPass, gOver, pan2;
        private int scrubIdx = -1;
        private long lastUpT = 0L;
        private ScaleGestureDetector scaleDet;

        private Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint lblB = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint scrV = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint scrD = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint hs = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);

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
            txt.setTypeface(TF_REG);
            try { txt.setFontFeatureSettings("tnum"); } catch (Exception ignored) { }
            lblB.setColor(col("text_secondary"));
            lblB.setTextSize(dp(11.5f));
            lblB.setTypeface(TF_BOLD);
            scrV.setColor(col("text_primary"));
            scrV.setTextSize(dp(12f));
            scrV.setTypeface(TF_BOLD);
            scrD.setColor(col("text_secondary"));
            scrD.setTextSize(dp(10f));
            scrD.setTypeface(TF_REG);
            try { scrD.setFontFeatureSettings("tnum"); } catch (Exception ignored) { }
            try { scrV.setFontFeatureSettings("tnum"); } catch (Exception ignored) { }
            try { lblB.setFontFeatureSettings("tnum"); } catch (Exception ignored) { }
            hs.setStyle(Paint.Style.STROKE);
            hs.setStrokeWidth(dp(1f));
            hs.setColor(col("text_secondary"));
            hs.setAlpha(70);
            halo.setStyle(Paint.Style.STROKE);
            halo.setStrokeWidth(dp(1.5f));

            scaleDet = new ScaleGestureDetector(MainActivity.this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                public boolean onScale(ScaleGestureDetector d) {
                    float span = d.getCurrentSpanX();
                    if (gSpan <= 0f) { gSpan = span; return true; }
                    if (Math.abs(span - gSpan) > 0.01f) {
                        applyZoom(d.getFocusX(), span / gSpan);
                        gSpan = span;
                    }
                    return true;
                }
            });

            setOnTouchListener(new View.OnTouchListener() {
                public boolean onTouch(View v, MotionEvent ev) {
                    ViewParent sc = v.getParent();
                    int act = ev.getActionMasked();
                    if (act == MotionEvent.ACTION_DOWN) {
                        if (progress < 1f) {
                            if (reveal != null) reveal.cancel();
                            progress = 1f;
                        }
                        scrub = false; scrubPass = false; gOver = false; pan2 = false; gSpan = -1f;
                        sX0 = ev.getX(); sY0 = ev.getY(); gPan0 = startF;
                        long now = System.currentTimeMillis();
                        if (now - lastUpT < 350L && Math.abs(ev.getX() - lastUpX) < dp(28f)) {
                            zoom = 1f; startF = 0f; scrub = false; scrubIdx = -1; gOver = true; invalidate();
                            lastUpT = 0L;
                            return true;
                        }
                        invalidate();
                        return true;
                    }
                    if (gOver) {
                        if (act == MotionEvent.ACTION_UP || act == MotionEvent.ACTION_CANCEL) gOver = false;
                        return true;
                    }
                    if (act == MotionEvent.ACTION_POINTER_DOWN) {
                        scrub = false; scrubPass = false; pan2 = true; gSpan = -1f;
                        cx0 = (ev.getX(0) + ev.getX(1)) / 2f;
                        gPan0 = startF;
                        if (sc != null) sc.requestDisallowInterceptTouchEvent(true);
                        invalidate();
                        return true;
                    }
                    if (act == MotionEvent.ACTION_POINTER_UP) {
                        // rebaseline SIEMPRE (en POINTER_UP el evento aún cuenta al dedo
                        // que sube: getPointerCount()==1 nunca ocurre aquí)
                        sX0 = ev.getX(); sY0 = ev.getY(); gPan0 = startF;
                        pan2 = false; gSpan = -1f; scrub = false; scrubPass = false;
                        invalidate();
                        return true;
                    }
                    if (act == MotionEvent.ACTION_MOVE) {
                        if (ev.getPointerCount() > 1) {
                            if (sc != null) sc.requestDisallowInterceptTouchEvent(true);
                            scaleDet.onTouchEvent(ev);
                            float cx = (ev.getX(0) + ev.getX(ev.getPointerCount() - 1)) / 2f;
                            if (pan2) {
                                startF = gPan0 - (cx - cx0) / Math.max(1f, getWidth() - pl() - pr()) * winNow();
                                clampStart();
                            }
                            invalidate();
                            return true;
                        }
                        float dx = ev.getX() - sX0, dy = ev.getY() - sY0;
                        if (!scrub && !scrubPass) {
                            int slop = ViewConfiguration.get(MainActivity.this).getScaledTouchSlop();
                            if (Math.abs(dx) > slop || Math.abs(dy) > slop) {
                                if (Math.abs(dx) >= Math.abs(dy)) {
                                    scrub = true;
                                    if (sc != null) sc.requestDisallowInterceptTouchEvent(true);
                                } else {
                                    scrubPass = true;
                                    if (sc != null) sc.requestDisallowInterceptTouchEvent(false);
                                }
                            }
                        }
                        if (scrub) {
                            scrubX = ev.getX();
                            scrubEdgeScroll();
                            updScrubIdx();
                            invalidate();
                            return true;
                        }
                        return false;
                    }
                    if (act == MotionEvent.ACTION_UP) {
                        lastUpT = System.currentTimeMillis();
                        lastUpX = ev.getX();
                        scrub = false;
                        invalidate();
                        if (sc != null) sc.requestDisallowInterceptTouchEvent(false);
                        return true;
                    }
                    if (act == MotionEvent.ACTION_CANCEL) {
                        scrub = false;
                        invalidate();
                        if (sc != null) sc.requestDisallowInterceptTouchEvent(false);
                        return true;
                    }
                    return false;
                }
            });
        }

        private float pl() { return dp(10f); }
        private float pr() { return dp(10f); }
        private float pt() { return dp(40f); }
        private float pb() { return dp(48f); }

        private void scrubEdgeScroll() {
            if (vals == null) return;
            float w = getWidth();
            float usableW = Math.max(1f, w - pl() - pr());
            float win = winNow();
            if (scrubX > w - pr()) startF += (scrubX - (w - pr())) / usableW * win * 0.55f;
            else if (scrubX < pl()) startF -= (pl() - scrubX) / usableW * win * 0.55f;
            clampStart();
        }

        private void updScrubIdx() {
            if (vals == null) { scrubIdx = -1; return; }
            float usableW = Math.max(1f, getWidth() - pl() - pr());
            float win = winNow();
            float idxF = startF + (scrubX - pl()) / usableW * win;
            int idx = Math.round(idxF);
            if (idx < 0) idx = 0;
            if (idx > vals.length - 1) idx = vals.length - 1;
            scrubIdx = idx;
        }

        private float winNow() {
            if (vals == null || vals.length < 2) return 2f;
            float t = vals.length;
            float f = t / zoom;
            if (f < 2f) { zoom = t / 2f; f = 2f; }
            if (f > t) { zoom = 1f; f = t; }
            return f;
        }

        private void clampStart() {
            if (vals == null) { startF = 0f; return; }
            float t = vals.length;
            float maxStart = Math.max(0f, t - winNow());
            if (startF < 0f) startF = 0f;
            if (startF > maxStart) startF = maxStart;
        }

        private void applyZoom(float fx, float factor) {
            if (vals == null || vals.length < 2) return;
            float t = vals.length;
            float usableW = Math.max(1f, getWidth() - pl() - pr());
            float rel = Math.min(1f, Math.max(0f, (fx - pl()) / usableW));
            float oldWin = winNow();
            float anchor = startF + rel * oldWin;
            zoom = Math.min(t / 2f, Math.max(1f, zoom * factor));
            float nw = winNow();
            startF = anchor - rel * nw;
            clampStart();
        }

        void setData(float[] v, long[] t) {
            vals = v;
            times = t;
            zoom = 1f;
            startF = 0f;
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

            float padL = pl(), padR = pr(), padT = pt(), padB = pb();
            float usableW = w - padL - padR;
            float usableH = h - padT - padB;
            float plotB = padT + usableH;

            if (vals == null || vals.length < 2) {
                txt.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(S("Recolectando datos…", "Collecting data…"), w / 2f, h / 2f - dp(8), txt);
                canvas.drawText(S("Cada apertura guarda un punto de la tasa.", "Each open saves one data point."), w / 2f, h / 2f + dp(10), txt);
                txt.setTextAlign(Paint.Align.LEFT);
                return;
            }

            int totalN = vals.length;
            float win = winNow();
            clampStart();
            int iA = (int) Math.floor(startF) - 1;
            if (iA < 0) iA = 0;
            int iB = (int) Math.ceil(startF + win) + 1;
            if (iB > totalN - 1) iB = totalN - 1;
            int m = iB - iA + 1;

            float vmin = vals[iA], vmax = vals[iA];
            for (int i = iA; i <= iB; i++) {
                if (vals[i] < vmin) vmin = vals[i];
                if (vals[i] > vmax) vmax = vals[i];
            }
            float rng = vmax - vmin;
            float mar = rng > 0f ? rng * 0.10f : Math.max(1e-6f, Math.abs(vmax) * 0.05f + 1e-6f);
            float ymin = vmin - mar, ymax = vmax + mar;
            float yr = ymax - ymin;
            if (yr <= 0f) yr = 1f;

            float[] xs = new float[m], ys = new float[m];
            int iMaxK = 0, iMinK = 0;
            for (int k = 0; k < m; k++) {
                int idx = iA + k;
                float rel = (idx - startF) / win;
                xs[k] = padL + usableW * rel;
                float norm = (vals[idx] - ymin) / yr;
                ys[k] = padT + usableH * (1f - norm);
                if (vals[idx] > vals[iA + iMaxK]) iMaxK = k;
                if (vals[idx] < vals[iA + iMinK]) iMinK = k;
            }

            boolean up = vals[iB] >= vals[iA];
            int trend = up ? col("green") : col("red");

            for (int g = 0; g <= 2; g++) {
                float gy = padT + usableH * g / 2f;
                canvas.drawLine(padL, gy, w - padR, gy, grid);
            }

            Path fp = new Path();
            fp.moveTo(xs[0], ys[0]);
            for (int i = 1; i < m; i++) fp.lineTo(xs[i], ys[i]);

            Path drawPath = fp;
            float endXLine = xs[m - 1], endYLine = ys[m - 1];
            if (progress < 1f) {
                PathMeasure pm = new PathMeasure(fp, false);
                float segLen = pm.getLength() * progress;
                Path seg = new Path();
                if (!pm.getSegment(0, segLen, seg, true)) {
                    seg.moveTo(xs[0], ys[0]);
                }
                float[] pos = new float[2];
                pm.getPosTan(segLen, pos, null);
                endXLine = pos[0];
                endYLine = pos[1];
                drawPath = seg;
            }

            // CLIP: la línea, el relleno y los puntos viven SOLO dentro del área de trazado,
            // así ningún texto en las bandas exteriores puede quedar bajo la línea
            int saveCnt = canvas.save();
            canvas.clipRect(padL, padT, w - padR, plotB);

            Path area = new Path(drawPath);
            area.lineTo(endXLine, plotB);
            area.lineTo(xs[0], plotB);
            area.close();
            int cr = Color.red(trend), cg = Color.green(trend), cb = Color.blue(trend);
            fill.setShader(new LinearGradient(0, padT, 0, plotB,
                    Color.argb(66, cr, cg, cb), Color.argb(0, cr, cg, cb), Shader.TileMode.CLAMP));
            canvas.drawPath(area, fill);
            fill.setShader(null);

            line.setColor(trend);
            canvas.drawPath(drawPath, line);

            if (scrub && scrubIdx >= iA && scrubIdx <= iB) {
                canvas.drawLine(xs[scrubIdx - iA], padT, xs[scrubIdx - iA], plotB, hs);
            }

            if (m <= 60 && progress >= 1f) {
                dot.setColor(trend);
                for (int i = 0; i < m; i++) {
                    canvas.drawCircle(xs[i], ys[i], dp(2.2f), dot);
                }
            }
            canvas.restoreToCount(saveCnt);

            // punto blanco del extremo del ventano
            int iEnd = Math.round(startF + win);
            if (iEnd > totalN - 1) iEnd = totalN - 1;
            if (iEnd < iA) iEnd = iA;
            float xEnd = padL + usableW * ((iEnd - startF) / win);
            float yEnd = padT + usableH * (1f - (vals[iEnd] - ymin) / yr);
            halo.setColor(Color.argb(70, Color.red(trend), Color.green(trend), Color.blue(trend)));
            canvas.drawCircle(xEnd, yEnd, dp(7.5f), halo);
            dot.setColor(Color.WHITE);
            canvas.drawCircle(xEnd, yEnd, dp(4.5f), dot);
            dot.setColor(trend);
            canvas.drawCircle(xEnd, yEnd, dp(3f), dot);

            // ============ etiquetas: cada una en su BANDA reservada ============
            SimpleDateFormat fdf = new SimpleDateFormat("d MMM", new Locale("es"));
            boolean sShow = scrub && scrubIdx >= 0 && scrubIdx < totalN && scrubIdx >= iA && scrubIdx <= iB;

            // fechas del ventano visible — fila inferior (siempre visibles)
            txt.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(fdf.format(new Date(times[iA])), padL, h - dp(10), txt);
            txt.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(fdf.format(new Date(times[iB])), w - padR, h - dp(10), txt);
            txt.setTextAlign(Paint.Align.LEFT);

            if (sShow) {
                // marcador del punto tocado
                float mx = xs[scrubIdx - iA], my = ys[scrubIdx - iA];
                dot.setColor(Color.WHITE);
                canvas.drawCircle(mx, my, dp(4.8f), dot);
                dot.setColor(trend);
                canvas.drawCircle(mx, my, dp(3.4f), dot);
                // burbuja en la banda superior (mín/máx ocultas: no puede pisarlas)
                String sV = fmt(vals[scrubIdx]);
                String sDt = fdf.format(new Date(times[scrubIdx]));
                float hw = Math.max(scrV.measureText(sV), scrD.measureText(sDt)) / 2f + 1f;
                float bx = Math.min(Math.max(mx, padL + hw), w - padR - hw);
                scrV.setTextAlign(Paint.Align.CENTER);
                scrD.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(sV, bx, padT - dp(26), scrV);
                canvas.drawText(sDt, bx, padT - dp(10), scrD);
            } else {
                // máximo → banda superior (la línea nunca entra aquí: clip)
                lblB.setTextAlign(Paint.Align.CENTER);
                String sMax = fmt(vals[iA + iMaxK]);
                float hwMax = lblB.measureText(sMax) / 2f + 1f;
                float xMax = Math.min(Math.max(xs[iMaxK], padL + hwMax), w - padR - hwMax);
                canvas.drawText(sMax, xMax, padT - dp(9), lblB);

                // mínimo → banda inferior
                String sMin = fmt(vals[iA + iMinK]);
                float hwMin = lblB.measureText(sMin) / 2f + 1f;
                float xMin = Math.min(Math.max(xs[iMinK], padL + hwMin), w - padR - hwMin);
                canvas.drawText(sMin, xMin, plotB + dp(13), lblB);
            }
        }
    }

    // ================= PÁGINA CONVERSOR =================

    private class PageConv {
        ScrollView root;
        LinearLayout segWrap;
        EditText input, feeInput;
        TextView result, labelFrom, labelTo, feeDetail;
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

            box.addView(bigTitle(S("Conversor", "Convert")));
            box.addView(tv(S("Calcula rápido con la tasa del día", "Quick math with today's rates"), 15, false, col("text_secondary")));

            LinearLayout card = new LinearLayout(MainActivity.this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(surfaceBg(18));
            card.setPadding(dp(18), dp(18), dp(18), dp(20));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.setMargins(0, dp(16), 0, 0);
            box.addView(pressScale(card), clp);

            segWrap = new LinearLayout(MainActivity.this);
            segWrap.setOrientation(LinearLayout.HORIZONTAL);
            segWrap.setBackground(inputSurface(10));
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

            LinearLayout colFrom = new LinearLayout(MainActivity.this);
            colFrom.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams cflp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cflp.setMargins(0, dp(20), 0, 0);
            card.addView(colFrom, cflp);

            labelFrom = tv(S("Monto en USD", "Amount in USD"), 13, false, col("text_secondary"));
            LinearLayout.LayoutParams lfLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lfLp.setMargins(dp(4), 0, 0, dp(7));
            colFrom.addView(labelFrom, lfLp);

            input = new EditText(MainActivity.this);
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            input.setBackground(inputSurface(14));
            input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
            input.setTextColor(col("text_primary"));
            input.setTypeface(TF_MED);
            input.setHint("0,00");
            input.setHintTextColor(col("text_secondary"));
            input.setPadding(dp(16), dp(15), dp(16), dp(15));
            colFrom.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            input.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                public void onTextChanged(CharSequence s, int a, int b, int c) { }
                public void afterTextChanged(Editable s) { recompute(); }
            });

            // Invertir dirección — círculo centrado entre los dos montos
            FrameLayout swapWrap = new FrameLayout(MainActivity.this);
            LinearLayout.LayoutParams swLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
            swLp.setMargins(0, dp(10), 0, dp(2));
            card.addView(swapWrap, swLp);

            int acc = col("accent");
            ImageView swapIv = new ImageView(MainActivity.this);
            swapIv.setImageResource(R.drawable.ic_swap);
            swapIv.setColorFilter(acc, android.graphics.PorterDuff.Mode.SRC_IN);
            swapIv.setPadding(dp(11), dp(11), dp(11), dp(11));
            swapIv.setBackground(blurOn ? glassBg(999, true)
                    : rounded(Color.argb(36, Color.red(acc), Color.green(acc), Color.blue(acc)), 999));
            FrameLayout.LayoutParams fsw = new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER);
            swapWrap.addView(pressScale(swapIv), fsw);
            swapIv.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    invert = !invert;
                    haptic(v);
                    v.animate().rotationBy(180f).setDuration(300).setInterpolator(decel).start();
                    recompute();
                }
            });

            LinearLayout colTo = new LinearLayout(MainActivity.this);
            colTo.setOrientation(LinearLayout.VERTICAL);
            card.addView(colTo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            labelTo = tv(S("Resultado en Bs.", "Result in Bs."), 13, false, col("text_secondary"));
            LinearLayout.LayoutParams ltLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ltLp.setMargins(dp(4), 0, 0, dp(7));
            colTo.addView(labelTo, ltLp);

            result = tv("—", 26, true, col("text_primary"));
            result.setBackground(inputSurface(14));
            result.setPadding(dp(16), dp(16), dp(16), dp(16));
            result.setClickable(true);
            result.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    String t = result.getText().toString().replace('\u00A0', ' ');
                    if (t.endsWith(" Bs.")) t = t.substring(0, t.length() - 4);
                    copyClip("Resultado", t, result);
                }
            });
            colTo.addView(pressScale(result), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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
                c.setBackground(rounded(Color.argb(26, Color.red(col("accent")), Color.green(col("accent")), Color.blue(col("accent"))), 16));
                LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(0, dp(38), 1);
                clp2.setMargins(dp(4), 0, dp(4), 0);
                chips.addView(pressScale(c), clp2);
                c.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        input.setText(cv);
                        recompute();
                        hideKeyboard(v);
                    }
                });
            }

            // Comisión % opcional: se descuenta del resultado y se muestra desglosada
            LinearLayout feeRow = new LinearLayout(MainActivity.this);
            feeRow.setOrientation(LinearLayout.HORIZONTAL);
            feeRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams frLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            frLp.setMargins(0, dp(12), 0, 0);
            card.addView(feeRow, frLp);

            TextView feeLbl = tv(S("Comisión %", "Fee %"), 13, false, col("text_secondary"));
            feeLbl.setPadding(dp(4), 0, dp(10), 0);
            feeRow.addView(feeLbl, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            feeInput = new EditText(MainActivity.this);
            feeInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            feeInput.setBackground(inputSurface(14));
            feeInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            feeInput.setTextColor(col("text_primary"));
            feeInput.setTypeface(TF_MED);
            feeInput.setHint("0");
            feeInput.setHintTextColor(col("text_secondary"));
            feeInput.setPadding(dp(12), dp(9), dp(12), dp(9));
            feeInput.setText(getSharedPreferences(PREFS, MODE_PRIVATE).getString("conv_fee", ""));
            feeRow.addView(feeInput, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

            TextView feeTip = tv(S("del resultado", "of result"), 12, false, col("text_secondary"));
            feeTip.setPadding(dp(10), 0, dp(4), 0);
            feeRow.addView(feeTip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            feeDetail = tv("", 12, false, col("text_secondary"));
            feeDetail.setVisibility(View.GONE);
            LinearLayout.LayoutParams fdLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            fdLp.setMargins(dp(4), dp(8), 0, 0);
            card.addView(feeDetail, fdLp);

            feeInput.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
                public void onTextChanged(CharSequence s, int a, int b, int c) { }
                public void afterTextChanged(Editable s) {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("conv_fee", s.toString()).apply();
                    recompute();
                }
            });

            // Compartir el resultado de la conversión
            Button convShare = new Button(MainActivity.this);
            convShare.setText(S("Compartir resultado", "Share result"));
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

            TextView note = tv(S("USD y EUR usan la tasa oficial del BCV. USDT usa Binance P2P.", "USD and EUR use the official BCV rate. USDT uses Binance P2P."), 12, false, col("text_secondary"));
            note.setPadding(0, dp(14), 0, 0);
            card.addView(note);

            // Estado inicial del carrusel USD/EUR/USDT (sin esto ningún modo aparece seleccionado al abrir la app)
            styleSeg();
            recompute();
        }

        private void styleSeg() {
            for (int i = 0; i < 3; i++) {
                TextView sv = (TextView) segWrap.getChildAt(i);
                if (i == mode) {
                    sv.setBackground(selectedSurface(9));
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
            String[] fromNames = new String[]{ S("Monto en USD", "Amount in USD"), S("Monto en EUR", "Amount in EUR"), S("Monto en USDT", "Amount in USDT") };
            double rate = rateSel();
            String sym = mode == 1 ? "EUR" : mode == 0 ? "USD" : "USDT";
            double amt = parseAmount(input.getText().toString());
            double fee = feePct();
            boolean hasFee = fee > 0 && rate > 0 && amt > 0;
            if (invert) {
                labelFade(labelFrom, S("Monto en Bs.", "Amount in Bs."));
                labelFade(labelTo, S("Resultado en ", "Result in ") + sym);
                if (rate > 0 && amt > 0) {
                    double res = amt / rate;
                    if (hasFee) {
                        double feeAmt = res * fee / 100.0;
                        setResultAnimated(fmt(res - feeAmt));
                    } else {
                        setResultAnimated(fmt(res));
                    }
                } else {
                    setResultAnimated("—");
                }
            } else {
                labelFade(labelFrom, fromNames[mode]);
                labelFade(labelTo, S("Resultado en Bs.", "Result in Bs."));
                if (rate > 0 && amt > 0) {
                    double res = amt * rate;
                    if (hasFee) {
                        double feeAmt = res * fee / 100.0;
                        setResultAnimated(fmt(res - feeAmt) + BSU);
                    } else {
                        setResultAnimated(fmt(res) + BSU);
                    }
                } else {
                    setResultAnimated("—");
                }
            }
            if (hasFee && feeDetail != null) {
                double res = invert ? amt / rate : amt * rate;
                double feeAmt = res * fee / 100.0;
                String unit = invert ? " " + sym : BSU;
                feeDetail.setText(S("Comisión ", "Fee ") + fmt(fee) + "%: −" + fmt(feeAmt) + unit
                        + S(" · Bruto: ", " · Gross: ") + fmt(res) + unit);
                feeDetail.setVisibility(View.VISIBLE);
            } else if (feeDetail != null) {
                feeDetail.setText("");
                feeDetail.setVisibility(View.GONE);
            }
        }

        // Comisión escrita por el usuario (0–99, admite coma decimal)
        private double feePct() {
            try {
                double f = Double.parseDouble(feeInput.getText().toString().trim().replace(",", "."));
                if (f < 0) return 0;
                if (f > 99) return 99;
                return f;
            } catch (Exception e) {
                return 0;
            }
        }

        void shareConv() {
            double rate = rateSel();
            double amt = parseAmount(input.getText().toString());
            if (rate <= 0 || amt <= 0) {
                Toast.makeText(MainActivity.this, S("Escribe un monto para compartir", "Type an amount to share"), Toast.LENGTH_SHORT).show();
                return;
            }
            String sym = mode == 1 ? "EUR" : mode == 0 ? "USD" : "USDT";
            double fee = feePct();
            boolean hasFee = fee > 0;
            double resVal = invert ? amt / rate : amt * rate;
            double feeAmt = hasFee ? resVal * fee / 100.0 : 0;
            double net = resVal - feeAmt;
            String unit = invert ? " " + sym : BSU;
            String montoTxt = invert ? fmt(amt) + BSU : fmt(amt) + " " + sym;
            String resTxt = fmt(net) + unit;
            String tasaTxt = S("Tasa: ", "Rate: ") + fmt(rate) + S(" Bs. por ", " Bs. per ") + sym
                    + " · " + (mode == 2 ? "Binance P2P" : "BCV");
            String feeTxt = hasFee
                    ? S("Comisión ", "Fee ") + fmt(fee) + "%: −" + fmt(feeAmt) + unit + S(" · Bruto: ", " · Gross: ") + fmt(resVal) + unit
                    : null;

            boolean dk = isDark();
            int bgCol = dk ? 0xFF0E0E10 : 0xFFF2F3F7;
            int cardCol = dk ? 0xFF1C1C1E : Color.WHITE;
            int priCol = dk ? 0xFFF2F2F7 : 0xFF111114;
            int secCol = dk ? 0xFF98989F : 0xFF8E8E93;
            int accCol = col("accent");

            int W = 1000, pad = 56, headH = 200, footH = 130;
            int bodyH = hasFee ? 450 : 380;
            int H = pad + headH + bodyH + footH;
            float maxW = W - 2 * pad - 40;

            Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bmp);
            cv.drawColor(bgCol);

            Paint cardP = new Paint(Paint.ANTI_ALIAS_FLAG);
            cardP.setColor(cardCol);
            siCardShadow(cardP);
            cv.drawRoundRect(pad, pad, W - pad, H - pad, 44, 44, cardP);

            float cx = pad + 70, cy = pad + 78, r = 58;
            siLogo(cv, cx, cy, r);

            float tx = cx + 96;
            Paint titleP = siPaint(priCol, 50, true);
            cv.drawText("Super Cambio", tx, siBase(titleP, pad + 56), titleP);
            Paint subP = siLight(secCol, 34);
            cv.drawText(S("Conversor de moneda", "Currency converter"), tx, siBase(subP, pad + 106), subP);

            Paint sepP = new Paint();
            sepP.setColor(dk ? 0x24FFFFFF : 0x14000000);
            sepP.setStrokeWidth(3);
            cv.drawLine(pad + 40, pad + headH, W - pad - 40, pad + headH, sepP);

            float y0 = pad + headH;
            Paint montoP = siPaint(priCol, 68, true);
            montoP.setTextAlign(Paint.Align.CENTER);
            Paint arrowP = siPaint(accCol, 50, true);
            arrowP.setTextAlign(Paint.Align.CENTER);
            Paint resP = siPaint(priCol, 68, true);
            resP.setTextAlign(Paint.Align.CENTER);
            Paint tasaP = siLight(secCol, 32);
            tasaP.setTextAlign(Paint.Align.CENTER);

            // Auto-fit: montos muy largos se escalan sin salirse de la tarjeta
            siFit(montoP, montoTxt, maxW);
            siFit(resP, resTxt, maxW);
            siFit(tasaP, tasaTxt, maxW);

            cv.drawText(montoTxt, W / 2f, siBase(montoP, y0 + 90), montoP);
            cv.drawText("↓", W / 2f, siBase(arrowP, y0 + 176), arrowP);
            cv.drawText(resTxt, W / 2f, siBase(resP, y0 + 264), resP);
            if (hasFee) {
                Paint feeP = siLight(secCol, 30);
                feeP.setTextAlign(Paint.Align.CENTER);
                siFit(feeP, feeTxt, maxW);
                cv.drawText(feeTxt, W / 2f, siBase(feeP, y0 + 336), feeP);
                cv.drawText(tasaTxt, W / 2f, siBase(tasaP, y0 + 416), tasaP);
            } else {
                cv.drawText(tasaTxt, W / 2f, siBase(tasaP, y0 + 352), tasaP);
            }

            Paint footP = siLight(secCol, 30);
            footP.setTextAlign(Paint.Align.CENTER);
            cv.drawText(S("Generado con Super Cambio · © 2026 Jdvibes", "Generated with Super Cambio · © 2026 Jdvibes"), W / 2f, H - pad - 40, footP);

            try {
                File f = new File(getCacheDir(), "conversion.png");
                FileOutputStream fo = new FileOutputStream(f);
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fo);
                fo.close();
                bmp.recycle();

                String txt = montoTxt + " = " + resTxt + "\n"
                        + (hasFee ? feeTxt + "\n" : "")
                        + tasaTxt + "\n\n" + S("Enviado con Super Cambio", "Sent with Super Cambio");
                Uri uri = Uri.parse("content://" + ShareProvider.AUTHORITY + "/conversion.png");
                Intent it = new Intent(Intent.ACTION_SEND);
                it.setType("image/png");
                it.putExtra(Intent.EXTRA_STREAM, uri);
                it.putExtra(Intent.EXTRA_TEXT, txt);
                it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(it, S("Compartir conversión", "Share conversion")));
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, S("No se pudo generar la imagen", "Couldn't generate the image"), Toast.LENGTH_SHORT).show();
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

        box.addView(bigTitle(S("Acerca de", "About")));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(Gravity.CENTER_HORIZONTAL);
        head.setBackground(surfaceBg(18));
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

        head.addView(tv(S("Versión ", "Version ") + currentVersionName() + " · Android", 13, false, col("text_secondary")));
        TextView tagline = tv("Tasas de cambio de Venezuela,\nsin anuncios y sin suscripciones.", 14, false, col("text_secondary"));
        tagline.setGravity(Gravity.CENTER);
        tagline.setPadding(0, dp(6), 0, 0);
        head.addView(tagline);

        // Apariencia
        box.addView(sectionLabel(S("Apariencia", "Appearance")));
        LinearLayout themeCard = card(box);

        rowRadio(themeCard, S("Automático (sistema)", "Automatic (system)"), themeMode == 0, 0);
        rowSep(themeCard);
        rowRadio(themeCard, S("Claro", "Light"), themeMode == 1, 1);
        rowSep(themeCard);
        rowRadio(themeCard, S("Oscuro", "Dark"), themeMode == 2, 2);

        // Color de acento
        rowSep(themeCard);
        rowBase(themeCard, S("Color de acento", "Accent color"), null, false);
        LinearLayout circRow = new LinearLayout(this);
        circRow.setPadding(0, dp(2), 0, dp(10));
        themeCard.addView(circRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < 5; i++) {
            final int ai = i;
            FrameLayout circle = new FrameLayout(this);
            GradientDrawable cdg = new GradientDrawable();
            cdg.setShape(GradientDrawable.OVAL);
            int[] cc = accentColorsFor(ai);
            cdg.setColor(cc[isDark() ? 1 : 0]);
            circle.setBackground(cdg);
            if (accentIdx == i) {
                ImageView chk = new ImageView(this);
                chk.setImageResource(R.drawable.ic_check);
                chk.setColorFilter(Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN);
                FrameLayout.LayoutParams chkLp = new FrameLayout.LayoutParams(dp(18), dp(18));
                chkLp.gravity = Gravity.CENTER;
                circle.addView(chk, chkLp);
            }
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(dp(38), dp(38));
            clp.setMargins(0, 0, dp(12), 0);
            circRow.addView(pressScale(circle), clp);
            circle.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    if (accentIdx == ai) return;
                    accentIdx = ai;
                    saveAll();
                    rebuildUiAnimated();
                    Toast.makeText(MainActivity.this, S("Color actualizado", "Color updated"), Toast.LENGTH_SHORT).show();
                }
            });
        }



        rowSep(themeCard);
        LinearLayout blurRow = new LinearLayout(this);
        blurRow.setOrientation(LinearLayout.HORIZONTAL);
        blurRow.setGravity(Gravity.CENTER_VERTICAL);
        blurRow.setPadding(0, dp(12), 0, dp(12));
        themeCard.addView(blurRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout blurCol = new LinearLayout(this);
        blurCol.setOrientation(LinearLayout.VERTICAL);
        blurRow.addView(blurCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        blurCol.addView(tv(S("Vidrio líquido", "Liquid glass"), 15, false, col("text_primary")));
        blurCol.addView(tv(S("Desenfoque, reflejos y objetos de vidrio", "Blur, reflections and glass objects"), 13, false, col("text_secondary")));

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
                rebuildUiAnimated();
                Toast.makeText(MainActivity.this, checked ? S("Vidrio líquido activado", "Liquid glass on") : S("Vidrio líquido desactivado", "Liquid glass off"), Toast.LENGTH_SHORT).show();
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
        logoCol.addView(tv(S("Logo oscuro de la app", "Dark app logo"), 15, false, col("text_primary")));
        logoCol.addView(tv(S("Cambia el ícono del launcher y el logo interno", "Changes the launcher icon and inner logo"), 13, false, col("text_secondary")));

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
                Toast.makeText(MainActivity.this, checked ? S("Logo oscuro activado", "Dark logo on") : S("Logo claro activado", "Light logo on"), Toast.LENGTH_SHORT).show();
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
        notifCol.addView(tv(S("Aviso diario de la tasa", "Daily rate alert"), 15, false, col("text_primary")));
        notifCol.addView(tv(S("Todos los días a las 3:35 PM, hora de Venezuela", "Every day at 3:35 PM, Venezuela time"), 13, false, col("text_secondary")));
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
                Toast.makeText(MainActivity.this, checked ? S("Aviso diario activado", "Daily alert on") : S("Aviso diario desactivado", "Daily alert off"), Toast.LENGTH_SHORT).show();
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
        hapCol.addView(tv(S("Vibración al tocar", "Haptic feedback"), 15, false, col("text_primary")));
        hapCol.addView(tv(S("Respuesta táctil sutil en botones y pestañas", "Subtle touch response on buttons and tabs"), 13, false, col("text_secondary")));
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
                Toast.makeText(MainActivity.this, checked ? S("Vibración activada", "Haptics on") : S("Vibración desactivada", "Haptics off"), Toast.LENGTH_SHORT).show();
            }
        });
        hapRow.addView(hapSwitch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Widget transparente (se funde con el fondo de pantalla)
        rowSep(themeCard);
        LinearLayout wgRow = new LinearLayout(this);
        wgRow.setOrientation(LinearLayout.HORIZONTAL);
        wgRow.setGravity(Gravity.CENTER_VERTICAL);
        wgRow.setPadding(0, dp(12), 0, dp(12));
        themeCard.addView(wgRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout wgCol = new LinearLayout(this);
        wgCol.setOrientation(LinearLayout.VERTICAL);
        wgRow.addView(wgCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        wgCol.addView(tv(S("Widget transparente", "Transparent widget"), 15, false, col("text_primary")));
        wgCol.addView(tv(S("Fondo traslúcido que se funde con tu pantalla", "Translucent background that blends in"), 13, false, col("text_secondary")));
        Switch wgSwitch = new Switch(this);
        wgSwitch.setChecked(widgetClear);
        try {
            wgSwitch.setThumbTintList(android.content.res.ColorStateList.valueOf(col("accent")));
            int[][] wstates = new int[][]{ new int[]{android.R.attr.state_checked}, new int[]{} };
            int[] wcolors = new int[]{ col("accent"), col("separator") };
            wgSwitch.setTrackTintList(new android.content.res.ColorStateList(wstates, wcolors));
        } catch (Exception ignored) { }
        wgSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                widgetClear = checked;
                saveAll();
                RateWidget.push(MainActivity.this);
                Toast.makeText(MainActivity.this, checked ? S("Widget transparente activado", "Transparent widget on") : S("Widget transparente desactivado", "Transparent widget off"), Toast.LENGTH_SHORT).show();
            }
        });
        wgRow.addView(wgSwitch, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Idioma — sección dedicada
        box.addView(sectionLabel(S("Idioma", "Language")));
        LinearLayout langCard = card(box);
        rowLang(langCard, S("Español", "Español"), !langEn, false);
        rowSep(langCard);
        rowLang(langCard, S("English", "English"), langEn, true);

        // Créditos
        box.addView(sectionLabel(S("Actualizaciones", "Updates")));
        LinearLayout updCard = card(box);
        LinearLayout updRow = rowBase(updCard,
                updTag.length() > 0 ? S("Nueva versión disponible: ", "New version available: ") + updTag : S("Buscar actualizaciones", "Check for updates"),
                updTag.length() > 0 ? S("Toca para descargar e instalar", "Tap to download and install") : S("Versión actual: ", "Current version: ") + currentVersionName(),
                false);
        try {
            ViewGroup rowCol = (ViewGroup) updRow.getChildAt(0);
            updateTitle = (TextView) rowCol.getChildAt(0);
            if (rowCol.getChildCount() > 1) updateSub = (TextView) rowCol.getChildAt(1);
        } catch (Exception ignored) { }
        paintUpdateRow();
        if (updTag.length() > 0 && updateTitle != null) {
            updateTitle.setTextColor(col("green"));
        }
        updRow.setClickable(true);
        pressScale(updRow);
        updRow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                haptic(v);
                if (updTag.length() > 0) downloadScreen();
                else checkForUpdates(true);
            }
        });

        box.addView(sectionLabel(S("Créditos", "Credits")));
        LinearLayout credCard = card(box);
        rowInfo(credCard, S("Desarrollo y diseño", "Development & design"), "Jdvibes", true);
        rowSep(credCard);
        rowInfo(credCard, S("Creación original", "Original creation"), S("App hecha de cero por Jdvibes", "App built from scratch by Jdvibes"), false);
        rowSep(credCard);
        rowInfo(credCard, S("Inspirada en", "Inspired by"), S("La utilidad de las apps de tasas en Venezuela", "The usefulness of rate apps in Venezuela"), false);

        box.addView(sectionLabel(S("Fuentes de datos", "Data sources")));
        LinearLayout dataCard = card(box);
        rowLink(dataCard, S("Dólar y Euro oficiales (BCV)", "Official Dollar & Euro (BCV)"), "ve.dolarapi.com", "https://ve.dolarapi.com");
        rowSep(dataCard);
        rowLink(dataCard, "USDT Binance P2P", "criptoya.com", "https://criptoya.com");
        rowSep(dataCard);
        rowInfo(dataCard, S("Paralelo", "Parallel dollar"), S("Promedio de mercado (dolarAPI)", "Market average (dolarAPI)"), false);

        box.addView(sectionLabel(S("Datos y respaldo", "Data & backup")));
        LinearLayout csvCard = card(box);
        LinearLayout csvRow = rowBase(csvCard,
                S("Exportar historial (CSV)", "Export history (CSV)"),
                S("Abre tus tasas guardadas en Excel o Google Sheets", "Open your saved rates in Excel or Google Sheets"),
                false);
        csvRow.setClickable(true);
        pressScale(csvRow);
        csvRow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { haptic(v); exportHistoryCsv(); }
        });

        box.addView(sectionLabel(S("Privacidad y rendimiento", "Privacy & performance")));
        LinearLayout privCard = card(box);
        rowCheck(privCard, S("Sin anuncios", "No ads"));
        rowCheck(privCard, S("Sin suscripciones ni compras", "No subscriptions or purchases"));
        rowCheck(privCard, S("Sin seguimiento ni analíticas", "No tracking or analytics"));
        rowCheck(privCard, S("Historial guardado solo en tu dispositivo", "History stored only on your device"));
        rowCheck(privCard, S("Historial compartido: solo cifras, sin datos personales", "Shared history: numbers only, no personal data"));
        rowCheck(privCard, S("Animaciones fluidas · hasta 120 Hz", "Smooth animations · up to 120 Hz"));

        TextView footer = tvIos("© 2026 Jdvibes · Super Cambio\nHecho con cariño para Venezuela", 12, false, col("text_secondary"));
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
            paintUpdateBadge();
        } catch (Throwable t) {
            buildUi();
            currentTab = -1;
            selectTab(0);
            updateAllPages();
        }
    }

    private TextView sectionLabel(String s) {
        TextView t = tv(s.toUpperCase(), 12, true, col("text_secondary"));
        try { t.setLetterSpacing(0.08f); } catch (Exception ignored) { }
        t.setPadding(dp(14), dp(22), 0, dp(6));
        return t;
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(surfaceBg(18));
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

    private void rowLang(LinearLayout card, String title, boolean selected, final boolean en) {
        LinearLayout row = rowBase(card, title, null, false);
        row.addView(radioDot(selected), new LinearLayout.LayoutParams(dp(22), dp(22)));
        row.setClickable(true);
        pressScale(row);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { setLang(en); }
        });
    }

    private void setLang(boolean en) {
        if (langEn == en) return;
        langEn = en;
        saveAll();
        rebuildUiAnimated();
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
        ImageView chev = new ImageView(this);
        chev.setImageResource(R.drawable.ic_chevron_right);
        chev.setColorFilter(col("text_secondary"), android.graphics.PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams chLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        chLp.gravity = Gravity.CENTER_VERTICAL;
        chLp.setMargins(dp(8), 0, 0, 0);
        row.addView(chev, chLp);
        row.setClickable(true);
        pressScale(row);
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, S("No se pudo abrir el enlace", "Couldn't open the link"), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void rowCheck(LinearLayout card, String title) {
        LinearLayout row = rowBase(card, title, null, false);
        ImageView check = new ImageView(this);
        check.setImageResource(R.drawable.ic_check_circle);
        check.setColorFilter(col("green"), android.graphics.PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams chkLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        chkLp.gravity = Gravity.CENTER_VERTICAL;
        row.addView(check, chkLp);
    }

    // ================= RED Y REFRESCO =================

    private void refresh() {
        pageRates.status.setText(brandSpan(S("Actualizando…", "Updating…")));
        pageRates.setBusy(true);
        new Thread(new Runnable() {
            public void run() {
                double nUsd = 0, nEur = 0, nUsdt = 0, nPar = 0;
                long tsUsd = 0, tsEur = 0, tsUsdt = 0, tsPar = 0;
                try {
                    JSONArray arr = new JSONArray(httpGet(API_USD));
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        String fuente = o.optString("fuente", "");
                        if ("oficial".equals(fuente)) { nUsd = o.getDouble("promedio"); tsUsd = tsOf(o); }
                        else if ("paralelo".equals(fuente)) { nPar = o.getDouble("promedio"); tsPar = tsOf(o); }
                    }
                } catch (Exception ignored) { }
                try {
                    JSONArray arr = new JSONArray(httpGet(API_EUR));
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        if ("oficial".equals(o.optString("fuente", ""))) { nEur = o.getDouble("promedio"); tsEur = tsOf(o); }
                    }
                } catch (Exception ignored) { }
                try {
                    JSONObject c = new JSONObject(httpGet(API_USDT));
                    JSONObject bin = c.getJSONObject("binancep2p");
                    double bid = bin.optDouble("bid", 0);
                    if (bid > 0) { nUsdt = bid; tsUsdt = bin.optLong("time", 0) * 1000L; }
                } catch (Exception ignored) { }

                final double fUsd = nUsd, fEur = nEur, fUsdt = nUsdt, fPar = nPar;
                final long fTsUsd = tsUsd, fTsEur = tsEur, fTsUsdt = tsUsdt, fTsPar = tsPar;
                final boolean ok = nUsd > 0 || nEur > 0 || nUsdt > 0 || nPar > 0;
                ui.post(new Runnable() {
                    public void run() {
                        // Aplicar valores NUEVOS en el hilo de UI (antes se mutaban en el
                        // hilo de fondo: carrera con gráficas/estado y puntos fantasma)
                        if (fUsd > 0) {
                            if (fUsd != bcvUsd) prevUsd = bcvUsd;
                            if (prevUsd == 0) prevUsd = fUsd;
                            bcvUsd = fUsd;
                            tUsd = fTsUsd > 0 ? fTsUsd : tUsd;
                        }
                        if (fEur > 0) {
                            if (fEur != bcvEur) prevEur = bcvEur;
                            if (prevEur == 0) prevEur = fEur;
                            bcvEur = fEur;
                            tEur = fTsEur > 0 ? fTsEur : tEur;
                        }
                        if (fUsdt > 0) {
                            if (fUsdt != usdt) prevUsdt = usdt;
                            if (prevUsdt == 0) prevUsdt = fUsdt;
                            usdt = fUsdt;
                            tUsdt = fTsUsdt > 0 ? fTsUsdt : tUsdt;
                        }
                        if (fPar > 0) {
                            if (fPar != paralelo) prevPar = paralelo;
                            if (prevPar == 0) prevPar = fPar;
                            paralelo = fPar;
                            tPar = fTsPar > 0 ? fTsPar : tPar;
                        }
                        if (ok) {
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
                        updateAllPages();
                        SimpleDateFormat f = new SimpleDateFormat("d MMM, HH:mm", new Locale("es"));
                        if (ok) pageRates.status.setText(TextUtils.concat(iconText(R.drawable.ic_check_circle, S("Actualizado: ", "Updated: "), 14, col("green")), f.format(new Date())));
                        else pageRates.status.setText(iconText(R.drawable.ic_wifi_off, S("Sin conexión · mostrando últimos datos", "Offline · showing last data"), 14, col("text_secondary")));
                        pageRates.setBusy(false);
                        syncSharedHist(false);
                    }
                });
            }
        }).start();
    }

    // ===== Actualizaciones desde GitHub =====

    private void updatePrompt() {
        if (updTag.length() == 0 || updUrl.length() == 0) return;
        if (!isNewerVersion(updTag, currentVersionName())) return;
        if (promptDlg != null && promptDlg.isShowing()) return;
        if (downloadDlg != null && downloadDlg.isShowing()) return;
        final Dialog d = new Dialog(this);
        promptDlg = d;
        final LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        int p0 = dp(22);
        c.setPadding(p0, p0, p0, p0);
        c.setBackground(rounded(col("card_bg"), 26));
        c.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView iv = new ImageView(this);
        boolean showLogo = false;
        try {
            iv.setImageBitmap(logoBitmap(dp(52)));
            showLogo = true;
        } catch (Exception e) { }
        LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(dp(52), dp(52));
        ivLp.setMargins(0, dp(4), 0, dp(12));
        if (!showLogo) ivLp.height = 0;
        c.addView(iv, ivLp);

        TextView t = tvIcon(R.drawable.ic_download, S("Actualización disponible", "Update available"), 18, true, col("text_primary"), 20);
        t.setGravity(Gravity.CENTER);
        c.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView sub = tv(S("Super Cambio ", "Super Cambio ") + currentVersionName() + " → " + updTag
                + (updSize > 0 ? " · " + fmtMB(updSize) : ""), 13, false, col("text_secondary"));
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.setMargins(0, dp(8), 0, 0);
        c.addView(sub, subLp);

        if (updNotes != null && updNotes.length() > 0) {
            ScrollView ns = new ScrollView(this);
            ns.setVerticalScrollBarEnabled(false);
            TextView notes = tv(updNotes, 12, false, col("text_secondary"));
            ns.addView(notes, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams nsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150));
            nsLp.setMargins(0, dp(14), 0, 0);
            c.addView(ns, nsLp);
        }

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        int bp = dp(16);
        btns.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams btnsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnsLp.setMargins(0, dp(18), 0, 0);
        btns.addView(btnNo(d), lpBtn(2, 0, 0, 0));
        btns.addView(btnDownload(d), lpBtn(3, dp(10), 0, 0));
        c.addView(btns, btnsLp);

        d.setContentView(c, new ViewGroup.LayoutParams(dp(330), ViewGroup.LayoutParams.WRAP_CONTENT));
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.45f;
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        d.show();
    }

    private TextView btnNo(final Dialog d) {
        TextView b = tv(S("Ahora no", "Not now"), 14, true, col("text_secondary"));
        b.setGravity(Gravity.CENTER);
        b.setBackground(rounded(col("input_bg"), 23));
        b.setMinHeight(dp(46));
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setSingleLine(true);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                haptic(v);
                d.dismiss();
            }
        });
        return b;
    }

    private TextView btnDownload(final Dialog d) {
        TextView b = tvIcon(R.drawable.ic_download, S("Descargar", "Download"), 14, true, Color.WHITE, 17);
        b.setGravity(Gravity.CENTER);
        b.setBackground(rounded(col("accent"), 23));
        b.setMinHeight(dp(46));
        b.setPadding(dp(10), 0, dp(10), 0);
        b.setSingleLine(true);
        b.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                haptic(v);
                d.dismiss();
                downloadScreen();
            }
        });
        return b;
    }

    private LinearLayout.LayoutParams lpBtn(int weight, int l, int t, int r) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(46), weight);
        lp.setMargins(l, t, r, 0);
        return lp;
    }

    private android.graphics.Bitmap logoBitmap(int size) {
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas cv = new android.graphics.Canvas(bmp);
        float radius = size * 0.2237f;
        RectF rf = new RectF(0, 0, size, size);
        android.graphics.Path path = new android.graphics.Path();
        path.addRoundRect(rf, radius, radius, android.graphics.Path.Direction.CW);
        cv.save();
        cv.clipPath(path);
        Drawable ld = logoDrawable();
        ld.setBounds(0, 0, size, size);
        ld.draw(cv);
        cv.restore();
        return bmp;
    }

    private void downloadScreen() {
        if (updUrl == null || updUrl.length() == 0 || updTag == null || updTag.length() == 0) {
            Toast.makeText(this, S("No hay descarga disponible", "No download available"), Toast.LENGTH_SHORT).show();
            return;
        }
        final String fileName = "actualizar-" + updTag + ".apk";
        final Dialog d = new Dialog(this);
        downloadDlg = d;
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        int p0 = dp(20);
        c.setPadding(p0, p0, p0, p0);
        c.setBackground(rounded(col("card_bg"), 24));

        TextView t = tvIcon(R.drawable.ic_download, S("Descargar Super Cambio ", "Download Super Cambio ") + updTag, 18, true, col("text_primary"), 20);
        c.addView(t);
        c.addView(tv(S("Se descargará la versión ", "Version ") + updTag + S(" desde GitHub", " will be downloaded from GitHub"), 13, false, col("text_secondary")),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        try { pb.getProgressDrawable().setColorFilter(col("accent"), android.graphics.PorterDuff.Mode.SRC_IN); } catch (Exception ignored) { }
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pbLp.setMargins(0, dp(14), 0, 0);
        c.addView(pb, pbLp);

        final TextView pct = tv(S("Conectando…", "Connecting…"), 13, true, col("text_secondary"));
        pct.setGravity(Gravity.CENTER);
        c.addView(pct, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (updNotes != null && updNotes.length() > 0) {
            TextView nl = tv(S("Novedades:", "What's new:"), 13, true, col("text_primary"));
            LinearLayout.LayoutParams nlLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlLp.setMargins(0, dp(12), 0, 0);
            c.addView(nl, nlLp);
            ScrollView ns = new ScrollView(this);
            ns.setVerticalScrollBarEnabled(false);
            TextView notes = tv(updNotes, 12, false, col("text_secondary"));
            ns.addView(notes, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams nsLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(190));
            nsLp.setMargins(0, dp(4), 0, 0);
            c.addView(ns, nsLp);
        }

        TextView cancel = tv(S("Cancelar", "Cancel"), 14, true, col("red"));
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(0, dp(14), 0, 0);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                haptic(v);
                dlCancel = true;
                d.dismiss();
                Toast.makeText(MainActivity.this, S("Descarga cancelada", "Download canceled"), Toast.LENGTH_SHORT).show();
            }
        });
        c.addView(cancel, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        d.setContentView(c, new ViewGroup.LayoutParams(dp(330), ViewGroup.LayoutParams.WRAP_CONTENT));
        d.setCancelable(false); // solo se cierra con el botón Cancelar (evita tocar fuera por accidente)
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.45f;
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        d.setOnDismissListener(new Dialog.OnDismissListener() {
            public void onDismiss(DialogInterface di) {
                if (downloadDlg == d) downloadDlg = null;
            }
        });
        d.show();
        dlCancel = false;

        new Thread(new Runnable() {
            public void run() {
                boolean ok = false;
                File out = new File(getCacheDir(), fileName);
                try {
                    HttpURLConnection conn = (HttpURLConnection) new URL(updUrl).openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setRequestProperty("User-Agent", "SuperCambio/" + currentVersionName() + " (Android)");
                    conn.connect();
                    long len = conn.getContentLength();
                    InputStream in = conn.getInputStream();
                    FileOutputStream fo = new FileOutputStream(out);
                    byte[] buf = new byte[8192];
                    long total = 0;
                    int n;
                    while ((n = in.read(buf)) > 0 && !dlCancel) {
                        fo.write(buf, 0, n);
                        total += n;
                        if (len > 0) {
                            final int pc = (int) (total * 100 / len);
                            final String st = fmtMB(total) + S(" de ", " of ") + fmtMB(len);
                            ui.post(new Runnable() {
                                public void run() { pb.setProgress(pc); pct.setText(pc + "% · " + st); }
                            });
                        } else {
                            final String st = fmtMB(total);
                            ui.post(new Runnable() {
                                public void run() { pct.setText(st); }
                            });
                        }
                    }
                    fo.close();
                    in.close();
                    conn.disconnect();
                    ok = !dlCancel && total > 0;
                } catch (Exception ignored) { }
                if (!ok) out.delete();
                final boolean fOk = ok;
                final boolean fCanceled = dlCancel;
                ui.post(new Runnable() {
                    public void run() {
                        if (!fOk) {
                            if (!fCanceled) {
                                pct.setText(iconText(R.drawable.ic_x_circle, S("Error de descarga · revisa tu conexión", "Download failed · check your connection"), 14, col("red")));
                                Toast.makeText(MainActivity.this, S("No se pudo descargar", "Couldn't download"), Toast.LENGTH_LONG).show();
                            }
                            return;
                        }
                        pct.setText(iconText(R.drawable.ic_check_circle, S("Descarga completa · preparando instalación…", "Download complete · preparing install…"), 14, col("green")));
                        installApk(fileName);
                        d.dismiss();
                    }
                });
            }
        }).start();
    }

    private void installApk(final String fileName) {
        File f = new File(getCacheDir(), fileName);
        if (!f.exists() || f.length() < 1024) {
            Toast.makeText(this, S("El archivo de instalación no es válido", "Installer file is invalid"), Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            boolean can = false;
            try { can = getPackageManager().canRequestPackageInstalls(); } catch (Exception ignored) { }
            if (!can) {
                pendingApkFile = fileName;
                try {
                    Intent it = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + getPackageName()));
                    startActivityForResult(it, REQ_INSTALL);
                    return;
                } catch (Exception e) {
                    pendingApkFile = "";
                    Toast.makeText(this, S("Activa \"Fuentes desconocidas\" para esta app en Ajustes", "Enable \"Unknown sources\" for this app in Settings"), Toast.LENGTH_LONG).show();
                }
            }
        }
        launchInstall(fileName);
    }

    private void launchInstall(String fileName) {
        try {
            Intent it = new Intent(Intent.ACTION_VIEW);
            it.setDataAndType(Uri.parse("content://" + ShareProvider.AUTHORITY + "/" + fileName),
                    "application/vnd.android.package-archive");
            it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(it);
        } catch (Exception e) {
            Toast.makeText(this, S("No se pudo abrir el instalador", "Couldn't open the installer"), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_INSTALL && pendingApkFile.length() > 0) {
            final String f = pendingApkFile;
            pendingApkFile = "";
            if (Build.VERSION.SDK_INT >= 26) {
                boolean can = false;
                try { can = getPackageManager().canRequestPackageInstalls(); } catch (Exception ignored) { }
                if (can) {
                    launchInstall(f);
                    return;
                }
            }
            Toast.makeText(this, S("Permiso de instalación denegado", "Install permission denied"), Toast.LENGTH_LONG).show();
        }
        if (requestCode == REQ_PICK_JSON && resultCode == RESULT_OK && data != null && data.getData() != null) {
            InputStream in = null;
            try {
                in = getContentResolver().openInputStream(data.getData());
                InputStreamReader rd = new InputStreamReader(in, StandardCharsets.UTF_8);
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[8192];
                int n;
                while ((n = rd.read(buf)) > 0) sb.append(buf, 0, n);
                applyImportJson(sb.toString());
            } catch (Exception e) {
                Toast.makeText(this, S("No se pudo leer el archivo", "Couldn't read the file"), Toast.LENGTH_SHORT).show();
            } finally {
                if (in != null) { try { in.close(); } catch (Exception ignored) { } }
            }
        }
        if (requestCode == REQ_SAVE_JSON && resultCode == RESULT_OK && data != null && data.getData() != null) {
            OutputStream out = null;
            try {
                String json = "{\"app\":\"supercambio\",\"v\":1,\"perfiles\":" + loadProfiles().toString() + "}";
                out = getContentResolver().openOutputStream(data.getData());
                out.write(json.getBytes(StandardCharsets.UTF_8));
                out.flush();
                Toast.makeText(this, S("Respaldo guardado", "Backup saved"), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, S("No se pudo guardar", "Couldn't save"), Toast.LENGTH_SHORT).show();
            } finally {
                if (out != null) { try { out.close(); } catch (Exception ignored) { } }
            }
        }
        if (requestCode == REQ_SAVE_CSV && resultCode == RESULT_OK && data != null && data.getData() != null) {
            writeHistoryCsv(data.getData());
        }
    }

    private String fmtMB(long bytes) {
        double mb = bytes / 1048576.0;
        if (mb < 1.0) return String.format(new Locale("es", "VE"), "%.0f KB", bytes / 1024.0);
        return String.format(new Locale("es", "VE"), "%.1f MB", mb);
    }

    private String currentVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "0.0.0";
        }
    }

    private boolean isNewerVersion(String remote, String cur) {
        try {
            String[] a = remote.replace("v", "").replace("V", "").trim().split("\\.");
            String[] b = cur.replace("v", "").replace("V", "").trim().split("\\.");
            int n = Math.max(a.length, b.length);
            for (int i = 0; i < n; i++) {
                int x = i < a.length ? Integer.parseInt(a[i].trim()) : 0;
                int y = i < b.length ? Integer.parseInt(b[i].trim()) : 0;
                if (x != y) return x > y;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private void checkForUpdates(final boolean manual) {
        SharedPreferences p = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (updBusy || (!manual && now - p.getLong("upd_last", 0) < 600000L)) return;
        updBusy = true;
        new Thread(new Runnable() {
            public void run() {
                String tag = "", url = "", notes = "";
                long size = 0;
                boolean fetchOk = false;
                try {
                    JSONObject r = new JSONObject(httpGet("https://api.github.com/repos/JDVIBES/SuperCambio/releases/latest"));
                    tag = r.optString("tag_name", "").replaceFirst("^v", "");
                    notes = r.optString("body", "");
                    JSONArray assets = r.optJSONArray("assets");
                    if (assets != null && assets.length() > 0) {
                        url = assets.getJSONObject(0).optString("browser_download_url", "");
                        size = assets.getJSONObject(0).optLong("size", 0);
                    }
                    fetchOk = true;
                } catch (Exception ignored) { }
                final String fTag = tag, fUrl = url, fNotes = notes;
                final long fSize = size;
                final boolean fOk = fetchOk;
                final boolean isNew = fTag.length() > 0 && isNewerVersion(fTag, currentVersionName());
                ui.post(new Runnable() {
                    public void run() {
                        updBusy = false;
                        if (!fOk) {
                            // No tocar prefs ni el estado: la verificación FALLÓ
                            if (manual) Toast.makeText(MainActivity.this,
                                    S("Sin conexión · no se pudo buscar actualizaciones", "Offline · couldn't check for updates"),
                                    Toast.LENGTH_LONG).show();
                            return;
                        }
                        SharedPreferences.Editor e = getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
                        e.putLong("upd_last", System.currentTimeMillis());
                        e.putString("upd_tag", isNew ? fTag : "");
                        e.putString("upd_url", fUrl);
                        e.putString("upd_notes", fNotes);
                        e.putLong("upd_size", fSize);
                        e.apply();
                        updTag = isNew ? fTag : "";
                        updUrl = fUrl;
                        updNotes = fNotes;
                        updSize = fSize;
                        paintUpdateRow();
                        paintUpdateBadge();
                        if (isNew) {
                            if (manual) {
                                Toast.makeText(MainActivity.this,
                                        S("Nueva versión disponible: ", "New version available: ") + fTag,
                                        Toast.LENGTH_LONG).show();
                            } else {
                                updatePrompt();
                            }
                        } else if (manual) {
                            Toast.makeText(MainActivity.this,
                                    S("Ya tienes la última versión", "You're up to date"),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void paintUpdateBadge() {
        try {
            if (tabLabels != null && tabLabels[4] != null) {
                tabLabels[4].setText(S("Acerca", "About") + (updTag.length() > 0 ? " •" : ""));
            }
        } catch (Exception ignored) { }
    }

    private void paintUpdateRow() {
        try {
            if (updateTitle == null || updateSub == null) return;
            if (updTag.length() > 0) {
                updateTitle.setText(TextUtils.concat(iconText(R.drawable.ic_download, S("Nueva versión disponible: ", "New version available: "), 15, col("green")), updTag));
                updateTitle.setTextColor(col("green"));
                updateSub.setText(S("Toca para descargar e instalar", "Tap to download and install"));
            } else {
                updateTitle.setText(iconText(R.drawable.ic_refresh, S("Buscar actualizaciones", "Check for updates"), 15, col("text_primary")));
                updateTitle.setTextColor(col("text_primary"));
                updateSub.setText(S("Versión actual: ", "Current version: ") + currentVersionName());
            }
        } catch (Exception ignored) { }
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
                    // Fusionar SIN mutar los arrays vivos (los aplica la UI)
                    final JSONArray mUsd = mergeHist(remote.optJSONArray("usd"), histUsd);
                    final JSONArray mEur = mergeHist(remote.optJSONArray("eur"), histEur);
                    final JSONArray mUsdt = mergeHist(remote.optJSONArray("usdt"), histUsdt);
                    final JSONArray mPar = mergeHist(remote.optJSONArray("par"), histPar);
                    boolean changed = mUsd != null | mEur != null | mUsdt != null | mPar != null;
                    boolean upload = localNewPoints(remote.optJSONArray("usd"), histUsd)
                            || localNewPoints(remote.optJSONArray("eur"), histEur)
                            || localNewPoints(remote.optJSONArray("usdt"), histUsdt)
                            || localNewPoints(remote.optJSONArray("par"), histPar);
                    if (changed) {
                        ui.post(new Runnable() {
                            public void run() {
                                if (mUsd != null) histUsd = mUsd;
                                if (mEur != null) histEur = mEur;
                                if (mUsdt != null) histUsdt = mUsdt;
                                if (mPar != null) histPar = mPar;
                                saveAll();
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

    // Devuelve el array fusionado (local gana conflictos; remoto añade puntos nuevos)
    // o null si no hay cambios — NO muta el array local.
    private JSONArray mergeHist(JSONArray remote, JSONArray local) {
        if (remote == null || local == null) return null;
        JSONArray res = null;
        try {
            java.util.TreeMap<Long, Double> map = new java.util.TreeMap<Long, Double>();
            for (int i = 0; i < local.length(); i++) {
                JSONObject o = local.getJSONObject(i);
                map.put(o.getLong("t"), o.getDouble("v"));
            }
            boolean added = false;
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
                res = arr;
            }
        } catch (Exception ignored) { }
        return res;
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
        c.setRequestProperty("User-Agent", "SuperCambio/" + currentVersionName() + " (Android)");
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

    private String S(String es, String en) {
        return langEn ? en : es;
    }

    private int[] accentColorsFor(int idx) {
        switch (idx) {
            case 1: return new int[]{ 0xFF34C759, 0xFF30D158 };
            case 2: return new int[]{ 0xFFAF52DE, 0xFFBF5AF2 };
            case 3: return new int[]{ 0xFFFF9500, 0xFFFF9F0A };
            case 4: return new int[]{ 0xFFFF2D55, 0xFFFF375F };
            default: return new int[]{ resColor(R.color.light_accent), resColor(R.color.dark_accent) };
        }
    }

    private int[] accentColors() {
        return accentColorsFor(accentIdx);
    }

    private String ptName(int t) {
        String base = PAY_TYPES[t][1];
        if (t == 0) return S("Pago móvil", "Mobile payment");
        if (t == 1) return S("Cuenta de banco", "Bank account");
        return base;
    }

    private String ptField(int t, int i) {
        String base = PAY_TYPES[t][4].split("\u0001")[i];
        if (base.equals("Banco")) return S("Banco", "Bank");
        if (base.equals("Teléfono")) return S("Teléfono", "Phone");
        if (base.equals("Cédula")) return S("Cédula", "ID number");
        if (base.equals("Titular")) return S("Titular", "Holder");
        if (base.equals("Número de cuenta")) return S("Número de cuenta", "Account number");
        if (base.equals("Tipo")) return S("Tipo", "Type");
        if (base.equals("Red")) return S("Red", "Network");
        if (base.equals("Correo o teléfono")) return S("Correo o teléfono", "Email or phone");
        return base;
    }

    private void rebuildUiAnimated() {
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
            currentTab = -1;
            selectTab(keepTab < 0 ? 0 : keepTab);
            updateAllPages();
            blurAfterLayout();
        } catch (Throwable tt) {
            buildUi();
            currentTab = -1;
            selectTab(0);
            updateAllPages();
        }
    }

    private void copyClip(String label, String text, View v) {
        if (text == null || text.length() == 0 || text.equals("—")) return;
        try {
            android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText(label, text));
                haptic(v);
                Toast.makeText(this, S("Copiado: ", "Copied: ") + text, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception ignored) { }
    }

    private void haptic(View v) {
        if (hapticsOn && v != null) {
            try { v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); } catch (Exception ignored) { }
        }
    }

    private String fmt(double v) {
        NumberFormat f = NumberFormat.getNumberInstance(new Locale("es", "VE"));
        double a = Math.abs(v);
        if (a > 0 && a < 1) {
            f.setMaximumFractionDigits(4);
            f.setMinimumFractionDigits(4);
        } else {
            f.setMaximumFractionDigits(2);
            f.setMinimumFractionDigits(2);
        }
        return f.format(v);
    }

    private double parseAmount(String s) {
        try {
            String t = s.replace('\u00A0', ' ').trim();
            if (t.length() == 0) return 0;
            // Acepta "899,30" y "1.054,60" (miles con punto + decimal con coma)
            if (t.indexOf(',') >= 0) t = t.replace(".", "").replace(',', '.');
            return Double.parseDouble(t);
        } catch (Exception e) { return 0; }
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }
}
