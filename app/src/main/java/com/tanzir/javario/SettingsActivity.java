package com.tanzir.javario;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** Part 3: Settings (30+ options, generated from Prefs.DEFS). */
public class SettingsActivity extends Activity {
    AppThemes T;
    LinearLayout col;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        T = AppThemes.cur(this);
        AppThemes.window(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(UI.dp(this, 6), UI.dp(this, 8), UI.dp(this, 6), UI.dp(this, 8));
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back);
        back.setPadding(UI.dp(this, 10), UI.dp(this, 10), UI.dp(this, 10), UI.dp(this, 10));
        UI.icon(back, T.text);
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });
        bar.addView(back, new LinearLayout.LayoutParams(UI.dp(this, 44), UI.dp(this, 44)));
        TextView t = UI.text(this, "Settings", 20, T.text);
        t.setTypeface(UI.medium());
        t.setPadding(UI.dp(this, 8), 0, 0, 0);
        bar.addView(t);
        root.addView(bar);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(UI.dp(this, 14), 0, UI.dp(this, 14), UI.dp(this, 30));
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        header("Tools");
        tool("Learn & Practice", "Java lessons and coding challenges", LearnActivity.class);
        tool("Java Assistant", "Ask questions, explain errors, get hints", AssistantActivity.class);
        for (final String[] d : Prefs.DEFS) {
            switch (d[1]) {
                case "header": header(d[3]); break;
                case "switch": switchRow(d); break;
                case "choice": choiceRow(d); break;
                case "seek": seekRow(d); break;
                case "text": textRow(d); break;
            }
        }
        header("About");
        card("Javario 1.0.0", "Learn, write and run Java on your phone.\nCompiler: ECJ + D8. Language: Java & XML.");
    }

    void header(String s) {
        TextView h = UI.text(this, s.toUpperCase(), 11, T.accent);
        h.setTypeface(UI.medium());
        h.setLetterSpacing(0.1f);
        h.setPadding(UI.dp(this, 8), UI.dp(this, 22), 0, UI.dp(this, 8));
        col.addView(h);
    }

    LinearLayout rowBox() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(UI.dp(this, 16), UI.dp(this, 12), UI.dp(this, 16), UI.dp(this, 12));
        r.setBackground(UI.ripple(UI.alpha(T.accent, 40), UI.round(this, T.surface, T.stroke, 14)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UI.dp(this, 6);
        col.addView(r, lp);
        return r;
    }

    LinearLayout titleBlock(String title, String desc) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.addView(UI.text(this, title, 15, T.text));
        if (desc != null && !desc.isEmpty()) b.addView(UI.text(this, desc, 12, T.sub));
        return b;
    }

    void card(String title, String desc) {
        rowBox().addView(titleBlock(title, desc));
    }

    void tool(String title, String desc, final Class<?> cls) {
        LinearLayout r = rowBox();
        r.addView(titleBlock(title, desc));
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new android.content.Intent(SettingsActivity.this, cls)); }
        });
    }

    void switchRow(final String[] d) {
        LinearLayout r = rowBox();
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout tb = titleBlock(d[3], d[4]);
        r.addView(tb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final Switch sw = new Switch(this);
        int[][] st = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(st, new int[]{T.accent, T.sub}));
        sw.setTrackTintList(new ColorStateList(st, new int[]{UI.alpha(T.accent, 110), UI.alpha(T.sub, 70)}));
        sw.setChecked(Prefs.b(this, d[0]));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) { Prefs.set(SettingsActivity.this, d[0], on); }
        });
        r.addView(sw);
        r.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { sw.toggle(); } });
    }

    void choiceRow(final String[] d) {
        final String[] opts = d[5].split(",");
        LinearLayout r = rowBox();
        r.addView(titleBlock(d[3], d[4]));
        final TextView val = UI.text(this, opts[Math.max(0, Math.min(opts.length - 1, Prefs.i(this, d[0])))], 13, T.accent);
        val.setTypeface(UI.medium());
        val.setPadding(0, UI.dp(this, 4), 0, 0);
        r.addView(val);
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Dlg.list(SettingsActivity.this, d[3], opts, new Dlg.Idx() {
                    @Override public void run(int i) {
                        Prefs.set(SettingsActivity.this, d[0], i);
                        val.setText(opts[i]);
                        if (d[0].equals("app_theme")) recreate();
                    }
                });
            }
        });
    }

    void seekRow(final String[] d) {
        String[] mm = d[5].split(":");
        final int min = Integer.parseInt(mm[0]), max = Integer.parseInt(mm[1]);
        LinearLayout r = rowBox();
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(titleBlock(d[3], d[4]), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView val = UI.text(this, String.valueOf(Prefs.i(this, d[0])), 14, T.accent);
        val.setTypeface(UI.medium());
        head.addView(val);
        r.addView(head);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(Math.max(0, Math.min(max, Prefs.i(this, d[0]))) - min);
        sb.setProgressTintList(ColorStateList.valueOf(T.accent));
        sb.setThumbTintList(ColorStateList.valueOf(T.accent));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean u) {
                val.setText(String.valueOf(p + min));
                if (u) Prefs.set(SettingsActivity.this, d[0], p + min);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        r.addView(sb);
    }

    void textRow(final String[] d) {
        LinearLayout r = rowBox();
        r.addView(titleBlock(d[3], d[4]));
        final TextView val = UI.text(this, mask(d[0], Prefs.s(this, d[0])), 13, T.accent);
        val.setPadding(0, UI.dp(this, 4), 0, 0);
        r.addView(val);
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Dlg.input(SettingsActivity.this, d[3], d[3], Prefs.s(SettingsActivity.this, d[0]), new Dlg.Str() {
                    @Override public void run(String s) {
                        Prefs.set(SettingsActivity.this, d[0], s);
                        val.setText(mask(d[0], s));
                    }
                });
            }
        });
    }

    String mask(String key, String v) {
        if (v.isEmpty()) return "Not set";
        if (key.equals("ai_key")) return "••••••" + v.substring(Math.max(0, v.length() - 4));
        return v;
    }
}
