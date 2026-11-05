package com.tanzir.javario;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.Window;

/** 5 app themes (light / dark etc.) */
public class AppThemes {
    public final String name;
    public final boolean dark;
    public final int bg, surface, surface2, text, sub, accent, stroke;

    AppThemes(String n, boolean d, String bg, String s, String s2, String t, String sub, String acc, String st) {
        name = n; dark = d;
        this.bg = Color.parseColor("#" + bg); surface = Color.parseColor("#" + s); surface2 = Color.parseColor("#" + s2);
        text = Color.parseColor("#" + t); this.sub = Color.parseColor("#" + sub);
        accent = Color.parseColor("#" + acc); stroke = Color.parseColor("#" + st);
    }

    public static final AppThemes[] ALL = {
        new AppThemes("Light", false, "F6F7FB", "FFFFFF", "EEF0F6", "1B1E27", "6B7280", "6C5CE7", "E2E5EE"),
        new AppThemes("Dark", true, "0F1117", "171A23", "1F2330", "E8EAF1", "8B93A7", "8B7CFF", "2A2F3E"),
        new AppThemes("AMOLED Black", true, "000000", "0A0A0C", "151518", "F2F2F2", "8E8E93", "00D1B2", "222226"),
        new AppThemes("Ocean Blue", true, "0A1628", "10223D", "163058", "E6F0FF", "8FA9CC", "3FA9F5", "1E3A66"),
        new AppThemes("Sunset", false, "FFF6EE", "FFFFFF", "FFE8D6", "2B1D16", "8A6F60", "F26B3A", "F3D9C5"),
    };

    public static AppThemes cur(Context c) {
        int i = Prefs.i(c, "app_theme");
        if (i < 0 || i >= ALL.length) i = 1;
        return ALL[i];
    }

    /** Colors the status bar / navigation bar / window background. */
    public static void window(Activity a) {
        AppThemes t = cur(a);
        Window w = a.getWindow();
        w.setStatusBarColor(t.bg);
        w.setNavigationBarColor(t.bg);
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(t.bg));
        int f = w.getDecorView().getSystemUiVisibility();
        if (!t.dark) f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        else f &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        w.getDecorView().setSystemUiVisibility(f);
    }

    public static void slideIn(Activity a) {
        if (Prefs.b(a, "animations")) a.overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out);
    }
}
