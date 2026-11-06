package com.tanzir.javario;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Part 1: Home - project list, FAB (new project), settings icon. */
public class MainActivity extends Activity {
    AppThemes T;
    ListView list;
    View empty;
    final ArrayList<File> projects = new ArrayList<>();
    BaseAdapter adapter;
    boolean askedPerm;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        T = AppThemes.cur(this);
        AppThemes.window(this);
        setContentView(R.layout.activity_main);
        ((TextView) findViewById(R.id.appTitle)).setTextColor(T.text);
        ((TextView) findViewById(R.id.appTitle)).setTypeface(UI.medium());
        ((TextView) findViewById(R.id.appSub)).setTextColor(T.sub);
        ((TextView) findViewById(R.id.sectionLabel)).setTextColor(T.sub);
        ((TextView) findViewById(R.id.emptyTitle)).setTextColor(T.text);
        ((TextView) findViewById(R.id.emptyText)).setTextColor(T.sub);
        TextView learn = (TextView) findViewById(R.id.emptyLearn);
        learn.setTextColor(0xFFFFFFFF);
        learn.setBackground(UI.round(this, T.accent, 22));
        learn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, LearnActivity.class)); }
        });
        ImageView set = (ImageView) findViewById(R.id.btnSettings);
        UI.icon(set, T.text);
        set.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, SettingsActivity.class)); }
        });
        ImageView fab = (ImageView) findViewById(R.id.fab);
        fab.setBackground(UI.ripple(0x33FFFFFF, UI.oval(T.accent)));
        UI.tint(fab, 0xFFFFFFFF);
        UI.press(fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { newProject(); }
        });
        empty = findViewById(R.id.empty);
        list = (ListView) findViewById(R.id.list);
        if (Prefs.b(this, "animations")) list.setLayoutAnimation(AnimationUtils.loadLayoutAnimation(this, R.anim.list_layout));
        adapter = new BaseAdapter() {
            @Override public int getCount() { return projects.size(); }
            @Override public Object getItem(int i) { return projects.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View v, ViewGroup p) { return row(projects.get(i)); }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> a, View v, int i, long id) { openProject(projects.get(i)); }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> a, View v, int i, long id) { projectMenu(projects.get(i)); return true; }
        });
    }

    View row(File f) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        int pd = UI.dp(this, 14);
        r.setPadding(pd, pd, pd, pd);
        r.setBackground(UI.ripple(UI.alpha(T.accent, 40), UI.round(this, T.surface, T.stroke, 16)));
        ImageView ic = new ImageView(this);
        ic.setImageResource(R.drawable.ic_folder);
        ic.setPadding(UI.dp(this, 9), UI.dp(this, 9), UI.dp(this, 9), UI.dp(this, 9));
        ic.setBackground(UI.round(this, UI.alpha(T.accent, 40), 12));
        UI.tint(ic, T.accent);
        r.addView(ic, new LinearLayout.LayoutParams(UI.dp(this, 42), UI.dp(this, 42)));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        cp.leftMargin = UI.dp(this, 14);
        TextView n = UI.text(this, f.getName(), 16, T.text);
        n.setSingleLine(true);
        TextView s = UI.text(this, ProjectStore.countFiles(f) + " files · " + DateUtils.getRelativeTimeSpanString(f.lastModified(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS), 12, T.sub);
        col.addView(n);
        col.addView(s);
        r.addView(col, cp);
        ImageView go = new ImageView(this);
        go.setImageResource(R.drawable.ic_chevron);
        UI.tint(go, T.sub);
        r.addView(go, new LinearLayout.LayoutParams(UI.dp(this, 22), UI.dp(this, 22)));
        LinearLayout wrap = new LinearLayout(this);
        wrap.setPadding(0, UI.dp(this, 5), 0, UI.dp(this, 5));
        wrap.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    @Override protected void onResume() {
        super.onResume();
        if (AppThemes.cur(this) != T) { recreate(); return; }
        if (!ProjectStore.hasAccess(this)) {
            if (!askedPerm) { askedPerm = true; askPermission(); }
        } else {
            ProjectStore.root().mkdirs();
        }
        refresh();
        showCrashIfAny();
    }

    boolean crashShown;

    void showCrashIfAny() {
        final String c = CrashLog.pending(this);
        if (c == null || crashShown) return;
        crashShown = true;
        TextView t = UI.text(this, c, 11, 0xFFFF7A7A);
        t.setTypeface(android.graphics.Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        new Dlg(this).title("Javario stopped last time").msg("Copy this report and send it to the developer.")
            .scroll(t, 300)
            .neg("Copy", new Runnable() {
                @Override public void run() {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", c));
                    UI.toast(MainActivity.this, "Copied");
                    CrashLog.clear(MainActivity.this);
                }
            })
            .pos("Close", new Runnable() { @Override public void run() { CrashLog.clear(MainActivity.this); } }).show();
    }

    void askPermission() {
        new Dlg(this).title("Allow storage access")
            .msg("Javario saves every project in\n/storage/emulated/0/Javario/<Project_Name>\n\nPlease allow file access so your code is kept on your phone.")
            .neg("Later", null)
            .pos("Allow", new Runnable() {
                @Override public void run() {
                    if (Build.VERSION.SDK_INT >= 30) {
                        try {
                            Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName()));
                            startActivity(i);
                        } catch (Exception e) {
                            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                        }
                    } else {
                        requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE}, 7);
                    }
                }
            }).show();
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        super.onRequestPermissionsResult(code, p, r);
        refresh();
    }

    void refresh() {
        projects.clear();
        projects.addAll(ProjectStore.list());
        adapter.notifyDataSetChanged();
        empty.setVisibility(projects.isEmpty() ? View.VISIBLE : View.GONE);
    }

    void openProject(File f) {
        Intent i = new Intent(this, IdeActivity.class);
        i.putExtra("project", f.getAbsolutePath());
        startActivity(i);
    }

    void newProject() {
        if (!ProjectStore.hasAccess(this)) { askPermission(); return; }
        Dlg.input(this, "New project", "Project name", "", new Dlg.Str() {
            @Override public void run(String name) {
                try {
                    File f = ProjectStore.create(name);
                    refresh();
                    openProject(f);
                } catch (IOException e) {
                    UI.toast(MainActivity.this, e.getMessage());
                }
            }
        });
    }

    void projectMenu(final File f) {
        Dlg.list(this, f.getName(), new String[]{"Open", "Rename", "Duplicate", "Export as ZIP", "Delete"}, new Dlg.Idx() {
            @Override public void run(int i) {
                if (i == 0) openProject(f);
                else if (i == 1) Dlg.input(MainActivity.this, "Rename project", "Name", f.getName(), new Dlg.Str() {
                    @Override public void run(String n) {
                        n = ProjectStore.sanitize(n);
                        File d = new File(f.getParentFile(), n);
                        if (n.isEmpty() || d.exists() || !f.renameTo(d)) UI.toast(MainActivity.this, "Cannot rename");
                        refresh();
                    }
                });
                else if (i == 2) {
                    try {
                        FileUtil.copyRec(f, new File(f.getParentFile(), FileUtil.uniqueName(f.getParentFile(), f.getName() + "_copy")));
                    } catch (IOException e) { UI.toast(MainActivity.this, "Copy failed"); }
                    refresh();
                } else if (i == 3) {
                    try {
                        File z = new File(ProjectStore.exports(), f.getName() + ".zip");
                        FileUtil.zip(f, z);
                        UI.toast(MainActivity.this, "Saved: " + z.getAbsolutePath());
                    } catch (IOException e) { UI.toast(MainActivity.this, "Export failed"); }
                } else Dlg.confirm(MainActivity.this, "Delete project?", "\"" + f.getName() + "\" and all its files will be deleted.", "Delete", new Runnable() {
                    @Override public void run() { FileUtil.deleteRec(f); refresh(); }
                });
            }
        });
    }
}
