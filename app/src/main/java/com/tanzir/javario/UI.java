package com.tanzir.javario;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class UI {
    public static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static GradientDrawable round(Context c, int fill, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    public static GradientDrawable round(Context c, int fill, int stroke, float radiusDp) {
        GradientDrawable g = round(c, fill, radiusDp);
        g.setStroke(Math.max(1, dp(c, 0.8f)), stroke);
        return g;
    }

    public static GradientDrawable oval(int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        return g;
    }

    public static Drawable ripple(int rippleColor, Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, null);
    }

    public static TextView text(Context c, String t, float sp, int color) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(sp);
        v.setTextColor(color);
        return v;
    }

    public static void tint(ImageView v, int color) {
        v.setColorFilter(color, PorterDuff.Mode.SRC_IN);
    }

    public static void icon(ImageView v, int color) {
        tint(v, color);
        v.setBackground(ripple((color & 0x00FFFFFF) | 0x22000000, null));
        press(v);
    }

    public static void toast(Context c, String m) {
        Toast.makeText(c, m, Toast.LENGTH_SHORT).show();
    }

    /** Small "press" scale animation. */
    public static void press(final View v) {
        if (!Prefs.b(v.getContext(), "animations")) return;
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(70).start();
                else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) view.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                return false;
            }
        });
    }

    public static Typeface medium() { return Typeface.create("sans-serif-medium", Typeface.NORMAL); }

    public static int alpha(int color, int a) { return (color & 0x00FFFFFF) | (a << 24); }

    public static int mix(int a, int b, float t) {
        return Color.rgb((int) (Color.red(a) * (1 - t) + Color.red(b) * t),
                (int) (Color.green(a) * (1 - t) + Color.green(b) * t),
                (int) (Color.blue(a) * (1 - t) + Color.blue(b) * t));
    }
}
