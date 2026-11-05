package com.tanzir.javario;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Small themed dialog builder used everywhere in the app. */
public class Dlg {
    public interface Str { void run(String s); }
    public interface Idx { void run(int i); }

    final Context c;
    final AppThemes T;
    final Dialog d;
    final LinearLayout box;
    final LinearLayout buttons;
    boolean hasButtons;

    public Dlg(Context c) {
        this.c = c;
        T = AppThemes.cur(c);
        d = new Dialog(c);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = UI.dp(c, 20);
        box.setPadding(p, p, p, UI.dp(c, 10));
        box.setBackground(UI.round(c, T.surface, T.stroke, 20));
        buttons = new LinearLayout(c);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);
        d.setContentView(box);
        Window w = d.getWindow();
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        if (Prefs.b(c, "animations")) w.getAttributes().windowAnimations = R.style.DialogAnim;
    }

    public Dlg title(String t) {
        TextView v = UI.text(c, t, 18, T.text);
        v.setTypeface(UI.medium());
        v.setPadding(0, 0, 0, UI.dp(c, 10));
        box.addView(v);
        return this;
    }

    public Dlg msg(String m) {
        TextView v = UI.text(c, m, 14, T.sub);
        v.setLineSpacing(0, 1.15f);
        v.setPadding(0, 0, 0, UI.dp(c, 8));
        box.addView(v);
        return this;
    }

    public Dlg view(View v) {
        box.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return this;
    }

    public Dlg scroll(View v, int maxHeightDp) {
        ScrollView sv = new ScrollView(c);
        sv.addView(v);
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UI.dp(c, maxHeightDp)));
        return this;
    }

    private Dlg button(String label, final Runnable r, boolean primary) {
        TextView b = UI.text(c, label, 14, primary ? T.accent : T.sub);
        b.setTypeface(UI.medium());
        b.setPadding(UI.dp(c, 16), UI.dp(c, 12), UI.dp(c, 16), UI.dp(c, 12));
        b.setBackground(UI.ripple(UI.alpha(T.accent, 40), null));
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                d.dismiss();
                if (r != null) r.run();
            }
        });
        buttons.addView(b);
        hasButtons = true;
        return this;
    }

    public Dlg pos(String l, Runnable r) { return button(l, r, true); }
    public Dlg neg(String l, Runnable r) { return button(l, r, false); }

    public Dialog show() {
        if (hasButtons) box.addView(buttons);
        d.show();
        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        d.getWindow().setLayout((int) (dm.widthPixels * 0.9f), WindowManager.LayoutParams.WRAP_CONTENT);
        return d;
    }

    public Dialog dialog() { return d; }

    // ---------- helpers ----------

    public static EditText field(Context c, String hint, String def) {
        AppThemes T = AppThemes.cur(c);
        EditText e = new EditText(c);
        e.setHint(hint);
        e.setText(def);
        e.setSingleLine(true);
        e.setTextColor(T.text);
        e.setHintTextColor(T.sub);
        e.setTextSize(15);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setPadding(UI.dp(c, 12), UI.dp(c, 10), UI.dp(c, 12), UI.dp(c, 10));
        e.setBackground(UI.round(c, T.surface2, 12));
        e.setSelection(e.getText().length());
        return e;
    }

    public static void input(Context c, String title, String hint, String def, final Str cb) {
        final EditText e = field(c, hint, def);
        Dlg dlg = new Dlg(c).title(title).view(e);
        dlg.neg("Cancel", null).pos("OK", new Runnable() {
            @Override public void run() { cb.run(e.getText().toString().trim()); }
        });
        dlg.d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        dlg.show();
        e.requestFocus();
    }

    public static void confirm(Context c, String title, String msg, String ok, final Runnable yes) {
        new Dlg(c).title(title).msg(msg).neg("Cancel", null).pos(ok, yes).show();
    }

    public static void list(Context c, String title, String[] items, final Idx cb) {
        final Dlg dlg = new Dlg(c);
        if (title != null) dlg.title(title);
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView t = UI.text(c, items[i], 15, dlg.T.text);
            t.setPadding(UI.dp(c, 8), UI.dp(c, 13), UI.dp(c, 8), UI.dp(c, 13));
            t.setBackground(UI.ripple(UI.alpha(dlg.T.accent, 40), null));
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { dlg.d.dismiss(); cb.run(idx); }
            });
            col.addView(t);
        }
        dlg.scroll(col, Math.min(items.length * 48 + 8, 380));
        dlg.show();
    }
}
