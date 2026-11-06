package com.tanzir.javario;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;

/** Tree structure file list (folders expand / collapse) with extension icons. */
public class TreeAdapter extends BaseAdapter {
    public static class Row { public File f; public int depth; public boolean dir; }

    final Context c;
    final AppThemes T;
    File root;
    public File selected;
    public final ArrayList<Row> rows = new ArrayList<>();
    final HashSet<String> open = new HashSet<>();

    public TreeAdapter(Context c) { this.c = c; T = AppThemes.cur(c); }

    public void setRoot(File r) { root = r; reload(); }

    public void reload() {
        rows.clear();
        if (root != null) add(root, 0, Prefs.b(c, "show_hidden"));
        notifyDataSetChanged();
    }

    void add(File dir, int depth, boolean hidden) {
        for (File f : FileUtil.sorted(dir)) {
            if (!hidden && f.getName().startsWith(".")) continue;
            Row r = new Row();
            r.f = f; r.depth = depth; r.dir = f.isDirectory();
            rows.add(r);
            if (r.dir && open.contains(f.getAbsolutePath())) add(f, depth + 1, hidden);
        }
    }

    public void toggle(File dir) {
        String k = dir.getAbsolutePath();
        if (!open.remove(k)) open.add(k);
        reload();
    }

    public void expand(File dir) { open.add(dir.getAbsolutePath()); reload(); }
    public boolean isOpen(File dir) { return open.contains(dir.getAbsolutePath()); }

    @Override public int getCount() { return rows.size(); }
    @Override public Object getItem(int i) { return rows.get(i); }
    @Override public long getItemId(int i) { return i; }

    @Override public View getView(int pos, View v, ViewGroup parent) {
        LinearLayout row;
        ImageView arrow, icon;
        TextView name;
        if (v == null) {
            row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            arrow = new ImageView(c);
            arrow.setImageResource(R.drawable.ic_chevron);
            icon = new ImageView(c);
            name = new TextView(c);
            name.setTextSize(14);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            row.addView(arrow, new LinearLayout.LayoutParams(UI.dp(c, 18), UI.dp(c, 18)));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(UI.dp(c, 24), UI.dp(c, 24));
            ip.leftMargin = UI.dp(c, 2); ip.rightMargin = UI.dp(c, 8);
            row.addView(icon, ip);
            row.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.setTag(new View[]{arrow, icon, name});
            v = row;
        }
        row = (LinearLayout) v;
        View[] t = (View[]) row.getTag();
        arrow = (ImageView) t[0]; icon = (ImageView) t[1]; name = (TextView) t[2];
        Row r = rows.get(pos);
        row.setMinimumHeight(UI.dp(c, 40));
        row.setPadding(UI.dp(c, 10 + r.depth * 14), 0, UI.dp(c, 10), 0);
        boolean sel = r.f.equals(selected);
        row.setBackground(sel ? UI.round(c, UI.alpha(T.accent, 40), 8) : UI.ripple(UI.alpha(T.accent, 30), null));
        name.setText(r.f.getName());
        name.setTextColor(sel ? T.accent : T.text);
        if (r.dir) {
            arrow.setVisibility(View.VISIBLE);
            arrow.setRotation(isOpen(r.f) ? 90 : 0);
            UI.tint(arrow, T.sub);
            icon.setImageResource(R.drawable.ic_folder);
            icon.setBackground(null);
            UI.tint(icon, 0xFFF2B33D);
        } else {
            arrow.setVisibility(View.INVISIBLE);
            icon.clearColorFilter();
            icon.setBackground(null);
            if (FileUtil.ext(r.f).equals("java")) icon.setImageResource(R.drawable.ic_file_java);
            else icon.setImageDrawable(new ExtIcon(FileUtil.ext(r.f)));
        }
        return v;
    }
}
