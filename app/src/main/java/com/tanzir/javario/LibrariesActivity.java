package com.tanzir.javario;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

/** 170+ libraries with one tap download into <project>/libs. */
public class LibrariesActivity extends Activity {
    AppThemes T;
    File project;
    ArrayList<LibraryManager.Lib> all, shown = new ArrayList<>();
    BaseAdapter adapter;
    TextView count;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        T = AppThemes.cur(this);
        AppThemes.window(this);
        String p = getIntent().getStringExtra("project");
        project = p == null ? null : new File(p);
        all = LibraryManager.catalog(this);
        shown.addAll(all);
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
        TextView t = UI.text(this, "Libraries", 20, T.text);
        t.setTypeface(UI.medium());
        bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView add = UI.text(this, "+ Maven", 13, T.accent);
        add.setPadding(UI.dp(this, 12), UI.dp(this, 8), UI.dp(this, 12), UI.dp(this, 8));
        add.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { custom(); } });
        bar.addView(add);
        root.addView(bar);
        EditText q = Dlg.field(this, "Search " + all.size() + " libraries", "");
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qp.setMargins(UI.dp(this, 14), UI.dp(this, 4), UI.dp(this, 14), UI.dp(this, 8));
        root.addView(q, qp);
        q.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable e) { }
        });
        ListView list = new ListView(this);
        list.setDivider(null);
        list.setPadding(UI.dp(this, 14), 0, UI.dp(this, 14), UI.dp(this, 20));
        list.setClipToPadding(false);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int i) { return shown.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup pr) { return row(shown.get(i)); }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> a, View v, int i, long id) { detail(shown.get(i)); }
        });
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    void filter(String s) {
        s = s.toLowerCase(Locale.ROOT).trim();
        shown.clear();
        for (LibraryManager.Lib l : all)
            if (s.isEmpty() || (l.name + " " + l.cat + " " + l.desc + " " + l.prefix).toLowerCase(Locale.ROOT).contains(s)) shown.add(l);
        adapter.notifyDataSetChanged();
    }

    View row(LibraryManager.Lib l) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(UI.dp(this, 14), UI.dp(this, 11), UI.dp(this, 14), UI.dp(this, 11));
        r.setBackground(UI.ripple(UI.alpha(T.accent, 40), UI.round(this, T.surface, T.stroke, 14)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(UI.text(this, l.name, 15, T.text));
        TextView d = UI.text(this, l.desc, 12, T.sub);
        d.setSingleLine(true);
        col.addView(d);
        TextView c = UI.text(this, l.cat + " · import " + l.prefix + ".*", 11, T.accent);
        c.setSingleLine(true);
        col.addView(c);
        r.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        boolean inst = project != null && LibraryManager.isInstalled(project, l);
        TextView st = UI.text(this, inst ? "Added" : "Get", 12, inst ? 0xFFFFFFFF : T.accent);
        st.setPadding(UI.dp(this, 12), UI.dp(this, 5), UI.dp(this, 12), UI.dp(this, 5));
        st.setBackground(inst ? UI.round(this, T.accent, 14) : UI.round(this, UI.alpha(T.accent, 40), 14));
        r.addView(st);
        LinearLayout w = new LinearLayout(this);
        w.setPadding(0, UI.dp(this, 4), 0, UI.dp(this, 4));
        w.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return w;
    }

    void detail(final LibraryManager.Lib l) {
        if (project == null) { UI.toast(this, "Open a project first"); return; }
        boolean inst = LibraryManager.isInstalled(project, l);
        StringBuilder sb = new StringBuilder(l.desc + "\n\nimport " + l.prefix + ".*;\n\nMaven:\n");
        for (String c : l.all()) sb.append(c).append('\n');
        sb.append("\nLibraries are also downloaded automatically when you import them and press Run.");
        Dlg d = new Dlg(this).title(l.name).msg(sb.toString()).neg("Close", null);
        if (inst) d.pos("Remove", new Runnable() {
            @Override public void run() { LibraryManager.remove(l, project); adapter.notifyDataSetChanged(); }
        });
        else d.pos("Add to project", new Runnable() { @Override public void run() { install(l); } });
        d.show();
    }

    void install(final LibraryManager.Lib l) {
        UI.toast(this, "Downloading " + l.name + "…");
        new Thread(new Runnable() {
            @Override public void run() {
                String err = null;
                try { LibraryManager.install(l, project); } catch (IOException e) { err = e.getMessage(); }
                final String msg = err == null ? l.name + " added to libs/" : "Failed: " + err;
                runOnUiThread(new Runnable() { @Override public void run() { UI.toast(LibrariesActivity.this, msg); adapter.notifyDataSetChanged(); } });
            }
        }).start();
    }

    void custom() {
        if (project == null) { UI.toast(this, "Open a project first"); return; }
        Dlg.input(this, "Add from Maven Central", "group:artifact:version", "", new Dlg.Str() {
            @Override public void run(final String c) {
                if (c.split(":").length != 3) { UI.toast(LibrariesActivity.this, "Use group:artifact:version"); return; }
                UI.toast(LibrariesActivity.this, "Downloading…");
                new Thread(new Runnable() {
                    @Override public void run() {
                        String err = null;
                        try { LibraryManager.download(c, new File(project, "libs")); } catch (IOException e) { err = e.getMessage(); }
                        final String msg = err == null ? "Added " + c : "Failed: " + err;
                        runOnUiThread(new Runnable() { @Override public void run() { UI.toast(LibrariesActivity.this, msg); } });
                    }
                }).start();
            }
        });
    }
}
