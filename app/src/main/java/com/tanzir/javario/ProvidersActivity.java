package com.tanzir.javario;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;

/** Manage AI providers: add (20 presets or custom), fetch models, FREE tags, select models, on/off switches. */
public class ProvidersActivity extends Activity {
    AppThemes T;
    LinearLayout col;
    ArrayList<AiProvider> providers;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        T = AppThemes.cur(this);
        AppThemes.window(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(UI.dp(this, 6), UI.dp(this, 8), UI.dp(this, 10), UI.dp(this, 4));
        ImageView back = new ImageView(this);
        back.setImageResource(R.drawable.ic_back);
        back.setPadding(UI.dp(this, 10), UI.dp(this, 10), UI.dp(this, 10), UI.dp(this, 10));
        UI.icon(back, T.text);
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { finish(); } });
        bar.addView(back, new LinearLayout.LayoutParams(UI.dp(this, 44), UI.dp(this, 44)));
        TextView t = UI.text(this, "AI providers", 20, T.text);
        t.setTypeface(UI.medium());
        bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView add = UI.text(this, "+ Add provider", 13, 0xFFFFFFFF);
        add.setTypeface(UI.medium());
        add.setPadding(UI.dp(this, 14), UI.dp(this, 9), UI.dp(this, 14), UI.dp(this, 9));
        add.setBackground(UI.ripple(0x33FFFFFF, UI.round(this, T.accent, 18)));
        add.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { addMenu(); } });
        bar.addView(add);
        root.addView(bar);
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(UI.dp(this, 14), UI.dp(this, 6), UI.dp(this, 14), UI.dp(this, 30));
        sv.addView(col);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    void refresh() {
        providers = AiProvider.load(this);
        col.removeAllViews();
        TextView info = UI.text(this, "Add providers, fetch their models, then pick a model in the chat box. "
            + "Providers and models each have an on/off switch. The offline tutor is always available.", 12, T.sub);
        info.setPadding(UI.dp(this, 4), 0, UI.dp(this, 4), UI.dp(this, 10));
        col.addView(info);
        if (providers.isEmpty()) {
            TextView e = UI.text(this, "No providers yet.\nTap “+ Add provider”.", 14, T.sub);
            e.setGravity(Gravity.CENTER);
            e.setPadding(0, UI.dp(this, 40), 0, 0);
            col.addView(e);
        }
        for (final AiProvider p : providers) col.addView(card(p));
    }

    View card(final AiProvider p) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(UI.dp(this, 16), UI.dp(this, 12), UI.dp(this, 12), UI.dp(this, 12));
        r.setBackground(UI.ripple(UI.alpha(T.accent, 40), UI.round(this, T.surface, T.stroke, 14)));
        LinearLayout tb = new LinearLayout(this);
        tb.setOrientation(LinearLayout.VERTICAL);
        TextView n = UI.text(this, p.name, 16, T.text);
        n.setTypeface(UI.medium());
        n.setSingleLine(true);
        TextView u = UI.text(this, p.baseUrl.isEmpty() ? "(no URL)" : p.baseUrl, 11, T.sub);
        u.setSingleLine(true);
        u.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        TextView c = UI.text(this, p.models.size() + " models · " + p.freeCount() + " free · " + p.enabledCount() + " on", 12, T.accent);
        tb.addView(n);
        tb.addView(u);
        tb.addView(c);
        r.addView(tb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Switch sw = new Switch(this);
        int[][] st = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(st, new int[]{T.accent, T.sub}));
        sw.setTrackTintList(new ColorStateList(st, new int[]{UI.alpha(T.accent, 110), UI.alpha(T.sub, 70)}));
        sw.setChecked(p.enabled);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean on) {
                p.enabled = on;
                AiProvider.save(ProvidersActivity.this, providers);
            }
        });
        r.addView(sw);
        r.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { edit(p, false); } });
        r.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { menu(p); return true; }
        });
        LinearLayout w = new LinearLayout(this);
        w.setPadding(0, UI.dp(this, 4), 0, UI.dp(this, 4));
        w.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return w;
    }

    void menu(final AiProvider p) {
        Dlg.list(this, p.name, new String[]{"Edit / models", "Duplicate", "Delete"}, new Dlg.Idx() {
            @Override public void run(int i) {
                if (i == 0) edit(p, false);
                else if (i == 1) {
                    AiProvider c = p.copy();
                    c.id = AiProvider.newId();
                    c.name = p.name + " copy";
                    providers.add(c);
                    AiProvider.save(ProvidersActivity.this, providers);
                    refresh();
                } else Dlg.confirm(ProvidersActivity.this, "Delete provider?", "\"" + p.name + "\" and its models will be removed.", "Delete", new Runnable() {
                    @Override public void run() {
                        providers.remove(p);
                        AiProvider.save(ProvidersActivity.this, providers);
                        refresh();
                    }
                });
            }
        });
    }

    void addMenu() {
        String[] names = new String[AiProvider.PRESETS.length + 1];
        names[0] = "＋ Custom provider";
        for (int i = 0; i < AiProvider.PRESETS.length; i++) names[i + 1] = AiProvider.PRESETS[i].name;
        Dlg.list(this, "Add provider", names, new Dlg.Idx() {
            @Override public void run(int i) {
                AiProvider p = new AiProvider();
                p.id = AiProvider.newId();
                if (i > 0) {
                    AiProvider.Preset pr = AiProvider.PRESETS[i - 1];
                    p.name = pr.name; p.baseUrl = pr.url; p.type = pr.type;
                }
                edit(p, true);
            }
        });
    }

    // ================================================================== add / edit dialog
    void edit(final AiProvider orig, final boolean isNew) {
        final AiProvider p = orig.copy();
        final Dlg d = new Dlg(this).title(isNew ? "Add provider" : "Edit provider");
        final EditText name = Dlg.field(this, "Name (for example My OpenRouter)", p.name);
        final EditText url = Dlg.field(this, "Base URL (https://…/v1)", p.baseUrl);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        final EditText key = Dlg.field(this, "API key", p.apiKey);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText mid = Dlg.field(this, "Model ID (optional, for example gpt-4o-mini)", "");

        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(name, fieldLp());
        box.addView(url, fieldLp());
        LinearLayout keyRow = new LinearLayout(this);
        keyRow.setGravity(Gravity.CENTER_VERTICAL);
        keyRow.addView(key, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView show = UI.text(this, "Show", 12, T.accent);
        show.setPadding(UI.dp(this, 12), UI.dp(this, 8), UI.dp(this, 4), UI.dp(this, 8));
        show.setOnClickListener(new View.OnClickListener() {
            boolean vis;
            @Override public void onClick(View v) {
                vis = !vis;
                key.setInputType(InputType.TYPE_CLASS_TEXT | (vis ? InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD : InputType.TYPE_TEXT_VARIATION_PASSWORD));
                show.setText(vis ? "Hide" : "Show");
                key.setSelection(key.getText().length());
            }
        });
        keyRow.addView(show);
        box.addView(keyRow, fieldLp());
        LinearLayout midRow = new LinearLayout(this);
        midRow.setGravity(Gravity.CENTER_VERTICAL);
        midRow.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView addM = UI.text(this, "Add", 12, T.accent);
        addM.setPadding(UI.dp(this, 12), UI.dp(this, 8), UI.dp(this, 4), UI.dp(this, 8));
        midRow.addView(addM);
        box.addView(midRow, fieldLp());

        final TextView typeT = UI.text(this, "", 12, T.sub);
        typeT.setPadding(UI.dp(this, 4), UI.dp(this, 4), 0, UI.dp(this, 4));
        box.addView(typeT);
        final Runnable[] setType = new Runnable[1];
        setType[0] = new Runnable() {
            @Override public void run() {
                typeT.setText("API type: " + (p.isAnthropic() ? "Anthropic (Claude)" : "OpenAI-compatible") + "  ·  tap to change");
            }
        };
        setType[0].run();
        typeT.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { p.type = p.isAnthropic() ? "openai" : "anthropic"; setType[0].run(); }
        });

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        TextView fetch = pill("Fetch models", true);
        TextView test = pill("Test", false);
        actions.addView(fetch);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.leftMargin = UI.dp(this, 8);
        actions.addView(test, tp);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.topMargin = UI.dp(this, 4);
        box.addView(actions, ap);

        final TextView status = UI.text(this, p.models.isEmpty() ? "No models yet. Tap “Fetch models”." : "", 12, T.sub);
        status.setPadding(UI.dp(this, 4), UI.dp(this, 8), 0, UI.dp(this, 4));
        box.addView(status);

        // model tools
        LinearLayout tools = new LinearLayout(this);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        final TextView selAll = pill("All", false), selFree = pill("Free", false), selNone = pill("None", false);
        tools.addView(selAll);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.leftMargin = UI.dp(this, 6);
        tools.addView(selFree, sp);
        tools.addView(selNone, sp);
        box.addView(tools);
        final EditText search = Dlg.field(this, "Search models", "");
        LinearLayout.LayoutParams sl = fieldLp();
        sl.topMargin = UI.dp(this, 8);
        box.addView(search, sl);
        final LinearLayout modelCol = new LinearLayout(this);
        modelCol.setOrientation(LinearLayout.VERTICAL);
        box.addView(modelCol);

        final int[] limit = {40};
        final Runnable[] fill = new Runnable[1];
        fill[0] = new Runnable() {
            @Override public void run() {
                modelCol.removeAllViews();
                String f = search.getText().toString().toLowerCase(Locale.ROOT).trim();
                int shown = 0, matched = 0;
                for (final AiProvider.Model m : p.models) {
                    if (!f.isEmpty() && !m.id.toLowerCase(Locale.ROOT).contains(f)) continue;
                    matched++;
                    if (shown >= limit[0]) continue;
                    shown++;
                    LinearLayout row = new LinearLayout(ProvidersActivity.this);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(UI.dp(ProvidersActivity.this, 2), UI.dp(ProvidersActivity.this, 2), UI.dp(ProvidersActivity.this, 6), UI.dp(ProvidersActivity.this, 2));
                    final CheckBox cb = new CheckBox(ProvidersActivity.this);
                    cb.setButtonTintList(ColorStateList.valueOf(T.accent));
                    cb.setChecked(m.enabled);
                    cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override public void onCheckedChanged(CompoundButton v, boolean on) { m.enabled = on; }
                    });
                    row.addView(cb);
                    TextView id = UI.text(ProvidersActivity.this, m.id, 13, T.text);
                    id.setSingleLine(true);
                    id.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                    row.addView(id, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    if (m.free) {
                        TextView ft = UI.text(ProvidersActivity.this, "FREE", 9, 0xFFFFFFFF);
                        ft.setTypeface(UI.medium());
                        ft.setPadding(UI.dp(ProvidersActivity.this, 7), UI.dp(ProvidersActivity.this, 2), UI.dp(ProvidersActivity.this, 7), UI.dp(ProvidersActivity.this, 2));
                        ft.setBackground(UI.round(ProvidersActivity.this, 0xFF2EAD6B, 8));
                        row.addView(ft);
                    }
                    row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { cb.toggle(); } });
                    modelCol.addView(row);
                }
                if (matched > shown) {
                    TextView more = UI.text(ProvidersActivity.this, "Show 40 more (" + (matched - shown) + " left)", 12, T.accent);
                    more.setPadding(UI.dp(ProvidersActivity.this, 8), UI.dp(ProvidersActivity.this, 12), 0, UI.dp(ProvidersActivity.this, 4));
                    more.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { limit[0] += 40; fill[0].run(); } });
                    modelCol.addView(more);
                }
                if (!p.models.isEmpty())
                    status.setText(p.models.size() + " models · " + p.freeCount() + " free · " + p.enabledCount() + " selected");
            }
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { limit[0] = 40; fill[0].run(); }
            @Override public void afterTextChanged(Editable e) { }
        });
        selAll.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { bulk(p, search.getText().toString(), 0); fill[0].run(); }
        });
        selFree.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { bulk(p, search.getText().toString(), 1); fill[0].run(); }
        });
        selNone.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { bulk(p, search.getText().toString(), 2); fill[0].run(); }
        });
        addM.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String id = mid.getText().toString().trim();
                if (id.isEmpty()) return;
                addModel(p, id);
                mid.setText("");
                fill[0].run();
            }
        });

        fetch.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                collect(p, name, url, key);
                if (p.baseUrl.isEmpty()) { status.setText("Enter the base URL first."); return; }
                status.setTextColor(T.sub);
                status.setText("Fetching models…");
                final AiProvider fp = p.copy();
                new Thread(new Runnable() {
                    @Override public void run() {
                        String err = null;
                        ArrayList<AiProvider.Model> got = null;
                        try { got = AiClient.fetchModels(fp); } catch (Exception e) { err = e.getMessage() == null ? e.toString() : e.getMessage(); }
                        final String ferr = err;
                        final ArrayList<AiProvider.Model> fgot = got;
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                if (ferr != null) { status.setTextColor(0xFFFF6B6B); status.setText("Failed: " + ferr); return; }
                                // keep earlier choices, default selection: free models, or all if the list is short
                                java.util.HashSet<String> was = new java.util.HashSet<>();
                                for (AiProvider.Model o : p.models) if (o.enabled) was.add(o.id);
                                boolean hadChoice = !was.isEmpty();
                                int free = 0;
                                for (AiProvider.Model m : fgot) if (m.free) free++;
                                for (AiProvider.Model m : fgot) {
                                    if (hadChoice) m.enabled = was.contains(m.id);
                                    else m.enabled = free > 0 ? m.free : fgot.size() <= 30;
                                }
                                for (AiProvider.Model o : p.models) if (o.enabled && !contains(fgot, o.id)) fgot.add(0, o);
                                p.models = fgot;
                                limit[0] = 40;
                                status.setTextColor(T.sub);
                                fill[0].run();
                            }
                        });
                    }
                }).start();
            }
        });

        test.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                collect(p, name, url, key);
                String model = null;
                for (AiProvider.Model m : p.models) if (m.enabled) { model = m.id; break; }
                String typed = mid.getText().toString().trim();
                if (!typed.isEmpty()) model = typed;
                if (model == null) { status.setTextColor(0xFFFF6B6B); status.setText("Select a model (or type a Model ID) to test."); return; }
                status.setTextColor(T.sub);
                status.setText("Testing " + model + "…");
                final StringBuilder reply = new StringBuilder();
                ArrayList<AiClient.M> ms = new ArrayList<>();
                ms.add(new AiClient.M("user", "Reply with the single word OK."));
                AiClient.chat(ProvidersActivity.this, p.copy(), model, "", ms, false, new AiClient.Stream() {
                    @Override public void token(String t) { reply.append(t); }
                    @Override public void done() {
                        runOnUiThread(new Runnable() { @Override public void run() { status.setTextColor(0xFF2EAD6B); status.setText("✓ Works. Reply: " + reply.toString().trim()); } });
                    }
                    @Override public void error(final String m) {
                        runOnUiThread(new Runnable() { @Override public void run() { status.setTextColor(0xFFFF6B6B); status.setText("✗ " + m); } });
                    }
                });
            }
        });

        d.scroll(box, (int) (screenDp() * 0.62f));
        d.neg("Cancel", null);
        d.pos("Save", new Runnable() {
            @Override public void run() {
                collect(p, name, url, key);
                String typed = mid.getText().toString().trim();
                if (!typed.isEmpty()) addModel(p, typed);
                if (p.name.isEmpty()) p.name = "Provider";
                if (p.baseUrl.isEmpty()) { UI.toast(ProvidersActivity.this, "Base URL is required"); return; }
                if (p.baseUrl.contains("anthropic.com")) p.type = "anthropic";
                if (isNew) providers.add(p);
                else for (int i = 0; i < providers.size(); i++) if (providers.get(i).id.equals(p.id)) providers.set(i, p);
                AiProvider.save(ProvidersActivity.this, providers);
                if (p.enabled && p.enabledCount() > 0) {
                    AiProvider.Entry cur = AiProvider.selected(ProvidersActivity.this);
                    if (cur.p.isBuiltin()) for (AiProvider.Model m : p.models) if (m.enabled) { AiProvider.select(ProvidersActivity.this, p, m); break; }
                }
                refresh();
            }
        });
        Dialog dlg = d.show();
        dlg.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        fill[0].run();
    }

    float screenDp() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        return dm.heightPixels / dm.density;
    }

    LinearLayout.LayoutParams fieldLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = UI.dp(this, 8);
        return lp;
    }

    TextView pill(String t, boolean primary) {
        TextView v = UI.text(this, t, 13, primary ? 0xFFFFFFFF : T.accent);
        v.setTypeface(UI.medium());
        v.setPadding(UI.dp(this, 14), UI.dp(this, 8), UI.dp(this, 14), UI.dp(this, 8));
        v.setBackground(UI.ripple(0x22FFFFFF, UI.round(this, primary ? T.accent : UI.alpha(T.accent, 36), 16)));
        return v;
    }

    void collect(AiProvider p, EditText name, EditText url, EditText key) {
        p.name = name.getText().toString().trim();
        p.baseUrl = url.getText().toString().trim();
        p.apiKey = key.getText().toString().trim();
        if (p.baseUrl.contains("anthropic.com")) p.type = "anthropic";
    }

    static boolean contains(ArrayList<AiProvider.Model> l, String id) {
        for (AiProvider.Model m : l) if (m.id.equals(id)) return true;
        return false;
    }

    static void addModel(AiProvider p, String id) {
        for (AiProvider.Model m : p.models) if (m.id.equals(id)) { m.enabled = true; return; }
        p.models.add(0, new AiProvider.Model(id, false, true));
    }

    /** mode 0 = all, 1 = only free, 2 = none. Respects the search filter when it is not empty. */
    static void bulk(AiProvider p, String filter, int mode) {
        String f = filter.toLowerCase(Locale.ROOT).trim();
        for (AiProvider.Model m : p.models) {
            if (!f.isEmpty() && !m.id.toLowerCase(Locale.ROOT).contains(f)) continue;
            m.enabled = mode == 0 || (mode == 1 && m.free);
        }
    }
}
