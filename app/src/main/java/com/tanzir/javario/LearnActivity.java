package com.tanzir.javario;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.IOException;

/** Learn & Practice: 12 lessons + 10 challenges that open as ready projects. */
public class LearnActivity extends Activity {
    AppThemes T;
    LinearLayout list;
    boolean lessons = true;
    TextView tabL, tabC;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        T = AppThemes.cur(this);
        AppThemes.window(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(UI.dp(this, 6), UI.dp(this, 8), UI.dp(this, 6), UI.dp(this, 4));
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back);
        back.setPadding(UI.dp(this, 10), UI.dp(this, 10), UI.dp(this, 10), UI.dp(this, 10));
        UI.icon(back, T.text);
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });
        bar.addView(back, new LinearLayout.LayoutParams(UI.dp(this, 44), UI.dp(this, 44)));
        TextView t = UI.text(this, "Learn & Practice", 20, T.text);
        t.setTypeface(UI.medium());
        bar.addView(t);
        root.addView(bar);

        LinearLayout seg = new LinearLayout(this);
        seg.setPadding(UI.dp(this, 14), UI.dp(this, 6), UI.dp(this, 14), UI.dp(this, 8));
        tabL = tab("Lessons", true);
        tabC = tab("Challenges", false);
        seg.addView(tabL, new LinearLayout.LayoutParams(0, UI.dp(this, 40), 1));
        seg.addView(tabC, new LinearLayout.LayoutParams(0, UI.dp(this, 40), 1));
        root.addView(seg);

        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(UI.dp(this, 14), UI.dp(this, 4), UI.dp(this, 14), UI.dp(this, 24));
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        fill();
    }

    TextView tab(String s, final boolean isLessons) {
        TextView t = UI.text(this, s, 14, T.text);
        t.setGravity(Gravity.CENTER);
        t.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { lessons = isLessons; fill(); }
        });
        return t;
    }

    void fill() {
        tabL.setBackground(UI.round(this, lessons ? T.accent : T.surface2, 12));
        tabC.setBackground(UI.round(this, lessons ? T.surface2 : T.accent, 12));
        tabL.setTextColor(lessons ? 0xFFFFFFFF : T.text);
        tabC.setTextColor(lessons ? T.text : 0xFFFFFFFF);
        list.removeAllViews();
        String[][] data = lessons ? LearnData.LESSONS : LearnData.CHALLENGES;
        for (int i = 0; i < data.length; i++) {
            final int idx = i;
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.VERTICAL);
            r.setPadding(UI.dp(this, 16), UI.dp(this, 13), UI.dp(this, 16), UI.dp(this, 13));
            r.setBackground(UI.ripple(UI.alpha(T.accent, 40), UI.round(this, T.surface, T.stroke, 14)));
            r.addView(UI.text(this, data[i][0], 16, T.text));
            TextView d = UI.text(this, data[i][1], 12, T.sub);
            d.setMaxLines(2);
            d.setEllipsize(android.text.TextUtils.TruncateAt.END);
            r.addView(d);
            r.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { if (lessons) lesson(idx); else challenge(idx); }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = UI.dp(this, 8);
            list.addView(r, lp);
        }
    }

    TextView code(String s) {
        TextView c = UI.text(this, s, 12, T.text);
        c.setTypeface(Typeface.MONOSPACE);
        c.setTextIsSelectable(true);
        c.setPadding(UI.dp(this, 12), UI.dp(this, 10), UI.dp(this, 12), UI.dp(this, 10));
        c.setBackground(UI.round(this, T.surface2, 10));
        return c;
    }

    void lesson(final int i) {
        String[] l = LearnData.LESSONS[i];
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(UI.text(this, l[1], 14, T.sub));
        TextView c = code(l[2]);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UI.dp(this, 10);
        col.addView(c, lp);
        new Dlg(this).title(l[0]).scroll(col, 360).neg("Close", null)
            .pos("Try it in IDE", new Runnable() {
                @Override public void run() { openAsProject("Lesson_" + (i + 1), LearnData.LESSONS[i][2]); }
            }).show();
    }

    void challenge(final int i) {
        final String[] ch = LearnData.CHALLENGES[i];
        final LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(UI.text(this, ch[1], 14, T.text));
        final TextView extra = code("");
        extra.setVisibility(View.GONE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UI.dp(this, 10);
        col.addView(extra, lp);
        TextView hint = UI.text(this, "Show hint", 13, T.accent);
        hint.setPadding(0, UI.dp(this, 12), UI.dp(this, 16), UI.dp(this, 4));
        hint.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { extra.setText("Hint: " + ch[3]); extra.setVisibility(View.VISIBLE); }
        });
        TextView sol = UI.text(this, "Show solution", 13, T.accent);
        sol.setPadding(0, UI.dp(this, 4), UI.dp(this, 16), UI.dp(this, 4));
        sol.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { extra.setText(ch[4]); extra.setVisibility(View.VISIBLE); }
        });
        col.addView(hint);
        col.addView(sol);
        new Dlg(this).title(ch[0]).scroll(col, 360).neg("Close", null)
            .pos("Start coding", new Runnable() {
                @Override public void run() { openAsProject("Challenge_" + (i + 1), ch[2]); }
            }).show();
    }

    void openAsProject(String name, String code) {
        if (!ProjectStore.hasAccess(this)) { UI.toast(this, "Allow storage access from the home screen first"); return; }
        try {
            File dir = new File(ProjectStore.root(), name);
            if (!dir.exists()) {
                ProjectStore.create(name);
                FileUtil.write(new File(dir, "src/Main.java"), code);
            }
            Intent in = new Intent(this, IdeActivity.class);
            in.putExtra("project", dir.getAbsolutePath());
            startActivity(in);
        } catch (IOException e) { UI.toast(this, e.getMessage()); }
    }
}
