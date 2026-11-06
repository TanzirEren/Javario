package com.tanzir.javario;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.Base64;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * The chat UI used by the Java Assistant page and by the AI panel inside the IDE.
 * Expanding chat box, model picker, attachments, reply-to-selection, Markdown answers, streaming, history.
 */
public class ChatView extends LinearLayout {
    public interface Bridge {
        File project();
        String currentName();
        String currentText();
        String selectedText();
        String projectTree();
        String lastErrors();
        List<File> files();
        void insert(String code);
        void replaceSelection(String code);
        void replaceCurrent(String code);
        void applyToFile(String relPath, String content);
        void newFile(String name, String content);
    }

    public static class Att {
        public String name, mime = "text/plain", text = "", b64;
        public boolean image;
    }

    public static class Msg {
        public String role, text, model;
        public String reasoning = "";
        public long start;
        public ArrayList<Att> atts = new ArrayList<>();
        public Msg(String r, String t) { role = r; text = t; }
    }

    class Holder {
        LinearLayout root, body, actions;
        Msg msg;
        boolean error;
    }

    public static final int REQ_FILE = 701, REQ_IMAGE = 702;
    static final String SYSTEM = "You are Javario Assistant, an expert Java tutor and coding assistant inside an Android Java IDE. "
        + "The user reads on a phone: be concise, friendly and use Markdown. Projects use Java (Java 8 syntax by default) and XML only; "
        + "there is no AWT/Swing. Give small, correct, runnable code. When you give the complete content of a file, write a line "
        + "`File: relative/path/Name.java` right before its code block so the user can apply it with one tap.";

    final Activity act;
    final Bridge bridge;
    final String key;
    final AppThemes T;
    final Handler h = new Handler(Looper.getMainLooper());
    final ScrollView sv;
    final LinearLayout list;
    final EditText input;
    final TextView modelChip;
    final ImageView sendBtn;
    final LinearLayout attBar, replyRow, quickBar;
    final HorizontalScrollView attScroll, quickScroll;
    TextView replyText;
    final ArrayList<Msg> history = new ArrayList<>();
    final ArrayList<Att> atts = new ArrayList<>();
    final AssistantEngine engine = new AssistantEngine();
    String quote;
    boolean busy, useCurrent;
    AiClient.Handle handle;
    Holder active;
    float fontSp;
    String staticCode = "", staticErrors = "";
    View welcome;
    boolean renderPending;

    public ChatView(Activity a, Bridge b, String key) {
        super(a);
        act = a; bridge = b; this.key = key;
        T = AppThemes.cur(a);
        fontSp = Prefs.i(a, "ai_font");
        useCurrent = Prefs.b(a, "ai_include_code");
        setOrientation(VERTICAL);
        setClipChildren(false);

        sv = new ScrollView(a);
        sv.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(a);
        list.setOrientation(VERTICAL);
        list.setPadding(UI.dp(a, 12), UI.dp(a, 8), UI.dp(a, 12), UI.dp(a, 12));
        sv.addView(list);
        addView(sv, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        quickScroll = new HorizontalScrollView(a);
        quickScroll.setHorizontalScrollBarEnabled(false);
        quickBar = new LinearLayout(a);
        quickBar.setPadding(UI.dp(a, 10), UI.dp(a, 4), UI.dp(a, 10), UI.dp(a, 4));
        quickScroll.addView(quickBar);
        addView(quickScroll);
        buildQuick();

        // ---- chat box (rounded, slim border, shadow, grows with text)
        LinearLayout card = new LinearLayout(a);
        card.setOrientation(VERTICAL);
        card.setPadding(UI.dp(a, 14), UI.dp(a, 10), UI.dp(a, 10), UI.dp(a, 10));
        card.setBackground(UI.round(a, T.surface, T.stroke, 22));
        card.setElevation(UI.dp(a, 5));
        LayoutParams cp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMargins(UI.dp(a, 10), UI.dp(a, 4), UI.dp(a, 10), UI.dp(a, 10));
        addView(card, cp);

        replyRow = new LinearLayout(a);
        replyRow.setGravity(Gravity.CENTER_VERTICAL);
        replyRow.setVisibility(GONE);
        replyRow.setPadding(UI.dp(a, 10), UI.dp(a, 6), UI.dp(a, 6), UI.dp(a, 6));
        replyRow.setBackground(UI.round(a, T.surface2, 12));
        ImageView ri = new ImageView(a);
        ri.setImageResource(R.drawable.ic_reply);
        UI.tint(ri, T.accent);
        replyRow.addView(ri, new LayoutParams(UI.dp(a, 16), UI.dp(a, 16)));
        replyText = UI.text(a, "", 12, T.sub);
        replyText.setMaxLines(2);
        replyText.setEllipsize(TextUtils.TruncateAt.END);
        replyText.setPadding(UI.dp(a, 8), 0, UI.dp(a, 8), 0);
        replyRow.addView(replyText, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView rx = UI.text(a, "×", 18, T.sub);
        rx.setPadding(UI.dp(a, 8), 0, UI.dp(a, 8), 0);
        rx.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { setReply(null); } });
        replyRow.addView(rx);
        LayoutParams rp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.bottomMargin = UI.dp(a, 6);
        card.addView(replyRow, rp);

        attScroll = new HorizontalScrollView(a);
        attScroll.setHorizontalScrollBarEnabled(false);
        attBar = new LinearLayout(a);
        attScroll.addView(attBar);
        LayoutParams ap = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.bottomMargin = UI.dp(a, 4);
        card.addView(attScroll, ap);

        input = new EditText(a);
        input.setHint("Ask anything about Java…");
        input.setHintTextColor(T.sub);
        input.setTextColor(T.text);
        input.setTextSize(14);
        input.setBackground(null);
        input.setPadding(0, UI.dp(a, 4), UI.dp(a, 4), UI.dp(a, 4));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMinLines(1);
        input.setMaxLines(8);
        input.setGravity(Gravity.TOP | Gravity.START);
        card.addView(input, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a1, int b1, int c1) { }
            @Override public void onTextChanged(CharSequence s, int a1, int b1, int c1) { updateSend(); }
            @Override public void afterTextChanged(Editable e) { }
        });

        LinearLayout bottom = new LinearLayout(a);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setPadding(0, UI.dp(a, 6), 0, 0);
        ImageView plus = new ImageView(a);
        plus.setImageResource(R.drawable.ic_add);
        plus.setPadding(UI.dp(a, 8), UI.dp(a, 8), UI.dp(a, 8), UI.dp(a, 8));
        plus.setBackground(UI.ripple(UI.alpha(T.accent, 40), UI.oval(T.surface2)));
        UI.tint(plus, T.text);
        UI.press(plus);
        plus.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { attachMenu(); } });
        bottom.addView(plus, new LayoutParams(UI.dp(a, 36), UI.dp(a, 36)));

        modelChip = UI.text(a, "", 12, T.text);
        modelChip.setSingleLine(true);
        modelChip.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        modelChip.setMaxWidth(UI.dp(a, 170));
        modelChip.setTypeface(UI.medium());
        modelChip.setPadding(UI.dp(a, 12), UI.dp(a, 7), UI.dp(a, 12), UI.dp(a, 7));
        modelChip.setBackground(UI.ripple(UI.alpha(T.accent, 40), UI.round(a, T.surface2, 18)));
        modelChip.setOnClickListener(new OnClickListener() { @Override public void onClick(View v) { pickModel(); } });
        LayoutParams mp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mp.leftMargin = UI.dp(a, 8);
        bottom.addView(modelChip, mp);
        bottom.addView(new View(a), new LayoutParams(0, 1, 1));

        sendBtn = new ImageView(a);
        sendBtn.setImageResource(R.drawable.ic_arrow_up);
        sendBtn.setPadding(UI.dp(a, 9), UI.dp(a, 9), UI.dp(a, 9), UI.dp(a, 9));
        UI.tint(sendBtn, 0xFFFFFFFF);
        UI.press(sendBtn);
        sendBtn.setOnClickListener(new OnClickListener() {
            @Override public void onClick(View v) {
                if (busy) stop(); else { String t = input.getText().toString(); send(t); }
            }
        });
        bottom.addView(sendBtn, new LayoutParams(UI.dp(a, 40), UI.dp(a, 40)));
        card.addView(bottom);

        refreshModel();
        refreshAttBar();
        updateSend();
        loadHistory();
        if (history.isEmpty()) showWelcome();
    }

    // ================================================================== small helpers
    void updateSend() {
        boolean can = busy || input.getText().toString().trim().length() > 0 || !atts.isEmpty();
        sendBtn.setImageResource(busy ? R.drawable.ic_stop : R.drawable.ic_arrow_up);
        sendBtn.setBackground(UI.oval(can ? T.accent : UI.alpha(T.accent, 110)));
    }

    public void setStaticContext(String code, String errors) {
        staticCode = code == null ? "" : code;
        staticErrors = errors == null ? "" : errors;
    }

    public void refreshModel() {
        AiProvider.Entry e = AiProvider.selected(act);
        modelChip.setText(e.p.isBuiltin() ? "Offline tutor" : e.m.id);
    }

    void toast(String s) { UI.toast(act, s); }

    void copy(String s) {
        ClipboardManager cm = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("chat", s));
        toast("Copied");
    }

    boolean nearBottom() {
        View c = sv.getChildAt(0);
        if (c == null) return true;
        return sv.getScrollY() + sv.getHeight() >= c.getHeight() - UI.dp(act, 160);
    }

    void scrollDown() {
        sv.post(new Runnable() { @Override public void run() { sv.fullScroll(View.FOCUS_DOWN); } });
    }

    void showWelcome() {
        LinearLayout w = new LinearLayout(act);
        w.setOrientation(VERTICAL);
        w.setGravity(Gravity.CENTER_HORIZONTAL);
        w.setPadding(UI.dp(act, 20), UI.dp(act, 50), UI.dp(act, 20), UI.dp(act, 20));
        ImageView ic = new ImageView(act);
        ic.setImageResource(R.drawable.ic_ai);
        UI.tint(ic, T.accent);
        w.addView(ic, new LayoutParams(UI.dp(act, 40), UI.dp(act, 40)));
        TextView t = UI.text(act, bridge != null ? "What should we build or fix?" : "What do you want to learn?", 18, T.text);
        t.setTypeface(UI.medium());
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, UI.dp(act, 12), 0, UI.dp(act, 4));
        w.addView(t);
        TextView s = UI.text(act, "Ask questions, attach files, find bugs, write code.\nPick a model below, or add providers in Settings.", 12, T.sub);
        s.setGravity(Gravity.CENTER);
        w.addView(s);
        welcome = w;
        list.addView(w);
    }

    void removeWelcome() {
        if (welcome != null) { list.removeView(welcome); welcome = null; }
    }

    // ================================================================== quick actions
    void buildQuick() {
        quickBar.removeAllViews();
        String[][] q;
        if (bridge != null) q = new String[][]{
            {"Find bugs", "Review the current file for bugs, logic errors and bad practices. List each problem with the line number and show the fix."},
            {"Fix build errors", "Fix the build errors. Explain the cause in one sentence each and give the corrected code."},
            {"Explain code", "Explain what this code does, step by step, in simple words."},
            {"Write code…", "@Write Java code that "},
            {"Optimize", "Improve and optimize this code. Keep the behavior. Show the new version."},
            {"Add comments", "Add short helpful comments to this code and return the full file."},
            {"Generate tests", "Write simple test code (a main method with checks) for this code."},
            {"New feature…", "@Add this feature to my project: "},
        };
        else q = new String[][]{
            {"Explain error", "Explain my last error and how to fix it."},
            {"Challenge", "Give me a Java practice challenge for my level."},
            {"Quiz me", "Quiz me about Java."},
            {"How do loops work?", "How do loops work in Java?"},
            {"Explain my code", "Explain my code"},
            {"Java tip", "Give me a Java tip."},
        };
        for (final String[] it : q) {
            TextView c = UI.text(act, it[0], 12, T.accent);
            c.setPadding(UI.dp(act, 12), UI.dp(act, 6), UI.dp(act, 12), UI.dp(act, 6));
            c.setBackground(UI.ripple(UI.alpha(T.accent, 50), UI.round(act, UI.alpha(T.accent, 28), 16)));
            c.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) {
                    if (busy) return;
                    if (it[1].startsWith("@")) {
                        input.setText(it[1].substring(1));
                        input.setSelection(input.getText().length());
                        input.requestFocus();
                    } else send(it[1]);
                }
            });
            LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = UI.dp(act, 6);
            quickBar.addView(c, lp);
        }
    }

    /** Sends a message from outside (for example "fix these build errors"). */
    public void autoSend(String text, String attachName, String attachText) {
        if (busy) return;
        if (attachText != null && !attachText.isEmpty()) {
            Att a = new Att();
            a.name = attachName; a.text = attachText;
            atts.add(a);
        }
        send(text);
    }

    // ================================================================== reply / quote
    void setReply(String q) {
        quote = (q == null || q.trim().isEmpty()) ? null : q.trim();
        replyRow.setVisibility(quote == null ? GONE : VISIBLE);
        if (quote != null) { replyText.setText(quote.replace("\n", " ")); input.requestFocus(); }
    }

    ActionMode.Callback replyCallback(final TextView tv) {
        return new ActionMode.Callback() {
            @Override public boolean onCreateActionMode(ActionMode m, Menu menu) {
                menu.add(0, 9001, 0, "Reply");
                return true;
            }
            @Override public boolean onPrepareActionMode(ActionMode m, Menu menu) { return true; }
            @Override public boolean onActionItemClicked(ActionMode m, MenuItem item) {
                if (item.getItemId() == 9001) {
                    int s = Math.max(0, Math.min(tv.getSelectionStart(), tv.getSelectionEnd()));
                    int e = Math.max(tv.getSelectionStart(), tv.getSelectionEnd());
                    if (e > s) setReply(tv.getText().subSequence(s, e).toString());
                    m.finish();
                    return true;
                }
                return false;
            }
            @Override public void onDestroyActionMode(ActionMode m) { }
        };
    }

    TextView mdText(String md) {
        TextView tv = new TextView(act);
        tv.setTextSize(fontSp);
        tv.setTextColor(T.text);
        tv.setLineSpacing(0, 1.2f);
        tv.setText(Markdown.render(act, md, T));
        tv.setTextIsSelectable(true);
        tv.setCustomSelectionActionModeCallback(replyCallback(tv));
        tv.setHighlightColor(UI.alpha(T.accent, 70));
        if (md.contains("](")) tv.setMovementMethod(LinkMovementMethod.getInstance());
        return tv;
    }

    // ================================================================== message views
    View userView(Msg m) {
        LinearLayout wrap = new LinearLayout(act);
        wrap.setGravity(Gravity.END);
        LinearLayout bubble = new LinearLayout(act);
        bubble.setOrientation(VERTICAL);
        bubble.setPadding(UI.dp(act, 12), UI.dp(act, 8), UI.dp(act, 12), UI.dp(act, 8));
        bubble.setBackground(UI.round(act, UI.alpha(T.accent, 36), 16));
        if (!m.atts.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Att a : m.atts) sb.append(a.image ? "🖼 " : "📎 ").append(a.name).append("   ");
            TextView at = UI.text(act, sb.toString().trim(), 11, T.accent);
            at.setPadding(0, 0, 0, UI.dp(act, 4));
            bubble.addView(at);
        }
        bubble.addView(mdText(m.text));
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = UI.dp(act, 44);
        wrap.addView(bubble, lp);
        LayoutParams wp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wp.topMargin = UI.dp(act, 10);
        wrap.setLayoutParams(wp);
        if (Prefs.b(act, "animations")) {
            wrap.setAlpha(0); wrap.setTranslationY(UI.dp(act, 10));
            wrap.animate().alpha(1).translationY(0).setDuration(200).start();
        }
        return wrap;
    }

    Holder assistantView(Msg m) {
        Holder hd = new Holder();
        hd.msg = m;
        hd.root = new LinearLayout(act);
        hd.root.setOrientation(VERTICAL);
        LayoutParams wp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wp.topMargin = UI.dp(act, 12);
        hd.root.setLayoutParams(wp);
        hd.body = new LinearLayout(act);
        hd.body.setOrientation(VERTICAL);
        hd.root.addView(hd.body);
        hd.actions = new LinearLayout(act);
        hd.actions.setGravity(Gravity.CENTER_VERTICAL);
        hd.root.addView(hd.actions);
        return hd;
    }

    void render(Holder hd) {
        hd.body.removeAllViews();
        String txt = hd.msg.text;
        if (txt.isEmpty() && !hd.error) {
            long secs = hd.msg.start == 0 ? 0 : (System.currentTimeMillis() - hd.msg.start) / 1000;
            TextView t = UI.text(act, "Thinking… " + secs + "s", fontSp, T.sub);
            hd.body.addView(t);
            String rs = hd.msg.reasoning;
            if (!rs.isEmpty()) {
                String tail = rs.length() > 350 ? "…" + rs.substring(rs.length() - 350) : rs;
                TextView r = UI.text(act, tail.trim(), Math.max(10, fontSp - 2), UI.alpha(T.sub, 200));
                r.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.ITALIC));
                r.setPadding(UI.dp(act, 8), UI.dp(act, 4), 0, 0);
                hd.body.addView(r);
            }
            return;
        }
        ArrayList<Markdown.Block> bl = Markdown.split(txt);
        for (Markdown.Block b : bl) {
            if (b.code) hd.body.addView(codeView(b), blockParams());
            else hd.body.addView(mdText(b.text), blockParams());
        }
    }

    LayoutParams blockParams() {
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = UI.dp(act, 4);
        lp.bottomMargin = UI.dp(act, 4);
        return lp;
    }

    View codeView(final Markdown.Block b) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(VERTICAL);
        box.setBackground(UI.round(act, T.dark ? 0xFF0B0D12 : 0xFFF1F2F6, T.stroke, 12));
        LinearLayout head = new LinearLayout(act);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(UI.dp(act, 12), UI.dp(act, 2), UI.dp(act, 4), UI.dp(act, 2));
        String label = b.path != null ? b.path : (b.lang.isEmpty() ? "code" : b.lang);
        TextView lang = UI.text(act, label, 11, T.sub);
        lang.setSingleLine(true);
        lang.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        head.addView(lang, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        head.addView(smallBtn("Copy", new Runnable() { @Override public void run() { copy(b.text); } }));
        if (bridge != null) head.addView(smallBtn(b.path != null ? "Apply" : "Use", new Runnable() { @Override public void run() { applyMenu(b); } }));
        box.addView(head);
        HorizontalScrollView hs = new HorizontalScrollView(act);
        hs.setHorizontalScrollBarEnabled(false);
        TextView code = new TextView(act);
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextSize(Math.max(10, fontSp - 2));
        code.setTextColor(T.text);
        code.setText(b.text);
        code.setTextIsSelectable(true);
        code.setCustomSelectionActionModeCallback(replyCallback(code));
        code.setPadding(UI.dp(act, 12), UI.dp(act, 2), UI.dp(act, 12), UI.dp(act, 10));
        hs.addView(code);
        box.addView(hs);
        return box;
    }

    TextView smallBtn(String t, final Runnable r) {
        TextView v = UI.text(act, t, 12, T.accent);
        v.setTypeface(UI.medium());
        v.setPadding(UI.dp(act, 10), UI.dp(act, 8), UI.dp(act, 10), UI.dp(act, 8));
        v.setBackground(UI.ripple(UI.alpha(T.accent, 40), null));
        v.setOnClickListener(new OnClickListener() { @Override public void onClick(View x) { r.run(); } });
        return v;
    }

    void applyMenu(final Markdown.Block b) {
        final ArrayList<String> items = new ArrayList<>();
        final ArrayList<Integer> ids = new ArrayList<>();
        if (b.path != null) { items.add("Apply to " + b.path); ids.add(0); }
        items.add("Replace whole current file"); ids.add(1);
        if (bridge.selectedText() != null && !bridge.selectedText().isEmpty()) { items.add("Replace selected code"); ids.add(2); }
        items.add("Insert at cursor"); ids.add(3);
        items.add("Save as new file…"); ids.add(4);
        Dlg.list(act, "Use this code", items.toArray(new String[0]), new Dlg.Idx() {
            @Override public void run(int i) {
                final int id = ids.get(i);
                Runnable go = new Runnable() {
                    @Override public void run() {
                        if (id == 0) bridge.applyToFile(b.path, b.text);
                        else if (id == 1) bridge.replaceCurrent(b.text);
                        else if (id == 2) bridge.replaceSelection(b.text);
                        else if (id == 3) bridge.insert(b.text);
                        else {
                            String def = "NewFile.java";
                            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:public\\s+)?(?:class|interface|enum)\\s+(\\w+)").matcher(b.text);
                            if (m.find()) def = m.group(1) + ".java";
                            Dlg.input(act, "Save as new file", "File name", b.path != null ? b.path.substring(b.path.lastIndexOf('/') + 1) : def, new Dlg.Str() {
                                @Override public void run(String n) { if (!n.isEmpty()) bridge.newFile(n, b.text); }
                            });
                        }
                    }
                };
                if ((id == 0 || id == 1 || id == 2) && Prefs.b(act, "ai_confirm_apply"))
                    Dlg.confirm(act, "Change your code?", "This changes " + (id == 0 ? b.path : id == 1 ? "the whole current file" : "the selected code") + ". You can undo it with the undo button.", "Apply", go);
                else go.run();
            }
        });
    }

    void addActions(final Holder hd) {
        hd.actions.removeAllViews();
        hd.actions.addView(iconAct(R.drawable.ic_copy, new Runnable() { @Override public void run() { copy(hd.msg.text); } }));
        hd.actions.addView(iconAct(R.drawable.ic_reply, new Runnable() { @Override public void run() { setReply(hd.msg.text.length() > 400 ? hd.msg.text.substring(0, 400) + "…" : hd.msg.text); } }));
        if (!history.isEmpty() && history.get(history.size() - 1) == hd.msg)
            hd.actions.addView(iconAct(R.drawable.ic_refresh, new Runnable() { @Override public void run() { regenerate(); } }));
        if (hd.msg.model != null) {
            TextView m = UI.text(act, hd.msg.model, 10, T.sub);
            m.setSingleLine(true);
            m.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            m.setPadding(UI.dp(act, 8), 0, 0, 0);
            hd.actions.addView(m, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
    }

    ImageView iconAct(int res, final Runnable r) {
        ImageView v = new ImageView(act);
        v.setImageResource(res);
        v.setPadding(UI.dp(act, 7), UI.dp(act, 7), UI.dp(act, 7), UI.dp(act, 7));
        UI.icon(v, T.sub);
        v.setLayoutParams(new LayoutParams(UI.dp(act, 30), UI.dp(act, 30)));
        v.setOnClickListener(new OnClickListener() { @Override public void onClick(View x) { r.run(); } });
        return v;
    }

    // ================================================================== sending
    public void send(String raw) {
        if (busy) return;
        String text = raw == null ? "" : raw.trim();
        ArrayList<Att> sendAtts = new ArrayList<>(atts);
        if (text.isEmpty() && sendAtts.isEmpty()) return;
        if (bridge != null) {
            if (useCurrent && bridge.currentName() != null && !bridge.currentName().isEmpty()) {
                Att a = new Att();
                a.name = bridge.currentName();
                String t = bridge.currentText();
                a.text = t.length() > 14000 ? t.substring(0, 14000) + "\n// … file truncated for speed" : t;
                sendAtts.add(0, a);
            }
            String sel = bridge.selectedText();
            if (sel != null && !sel.trim().isEmpty()) {
                Att a = new Att();
                a.name = "selected code";
                a.text = sel.length() > 8000 ? sel.substring(0, 8000) : sel;
                sendAtts.add(a);
            }
        }
        if (text.isEmpty()) text = "Please look at the attached " + (sendAtts.size() > 1 ? "items" : "item") + ".";
        String full = text;
        if (quote != null) full = "> " + quote.replace("\n", "\n> ") + "\n\n" + text;
        Msg um = new Msg("user", full);
        um.atts = sendAtts;
        history.add(um);
        removeWelcome();
        list.addView(userView(um));
        input.setText("");
        atts.clear();
        setReply(null);
        refreshAttBar();
        scrollDown();
        request();
    }

    void stop() {
        if (handle != null) handle.cancel();
        if (active != null) {
            Holder hd = active;
            if (hd.msg.text.isEmpty()) hd.msg.text = "_(stopped)_";
            finish(hd, null);
        }
    }

    void regenerate() {
        if (busy || history.isEmpty()) return;
        if (history.get(history.size() - 1).role.equals("assistant")) {
            history.remove(history.size() - 1);
            if (list.getChildCount() > 0) list.removeViewAt(list.getChildCount() - 1);
        }
        if (history.isEmpty()) return;
        request();
    }

    String systemPrompt() {
        StringBuilder sb = new StringBuilder(SYSTEM);
        String custom = Prefs.s(act, "ai_system").trim();
        if (!custom.isEmpty()) sb.append("\n\nUser instructions: ").append(custom);
        if (bridge != null) {
            if (Prefs.b(act, "ai_include_tree")) {
                String tree = bridge.projectTree();
                if (tree != null && !tree.isEmpty()) sb.append("\n\nProject files:\n").append(tree);
            }
            String er = bridge.lastErrors();
            boolean hasErrAtt = false;
            if (!history.isEmpty()) for (Att a : history.get(history.size() - 1).atts) if (a.name != null && a.name.equals("build errors")) hasErrAtt = true;
            if (!hasErrAtt && er != null && !er.trim().isEmpty()) sb.append("\n\nLatest build / editor errors:\n").append(er.length() > 2000 ? er.substring(0, 2000) : er);
        } else if (!staticErrors.isEmpty()) {
            sb.append("\n\nLatest build errors:\n").append(staticErrors.length() > 3500 ? staticErrors.substring(0, 3500) : staticErrors);
        }
        if (bridge == null && !staticCode.isEmpty()) sb.append("\n\nUser's current file:\n```java\n").append(staticCode).append("\n```");
        return sb.toString();
    }

    void request() {
        final AiProvider.Entry sel = AiProvider.selected(act);
        final Msg am = new Msg("assistant", "");
        final Holder hd = assistantView(am);
        active = hd;
        list.addView(hd.root);
        render(hd);
        busy = true;
        updateSend();
        scrollDown();
        if (sel.p.isBuiltin()) {
            final String q = history.get(history.size() - 1).text;
            h.postDelayed(new Runnable() {
                @Override public void run() {
                    if (!busy) return;
                    String code = bridge != null ? bridge.currentText() : staticCode;
                    String err = bridge != null ? bridge.lastErrors() : staticErrors;
                    am.text = engine.offline(q.replaceAll("(?m)^> .*$", "").trim().isEmpty() ? q : q, code, err);
                    finish(hd, null);
                }
            }, 350);
            return;
        }
        ArrayList<AiClient.M> ms = new ArrayList<>();
        int max = Math.max(2, Prefs.i(act, "ai_history"));
        int from = Math.max(0, history.size() - max);
        while (from < history.size() && !history.get(from).role.equals("user")) from++;
        for (int i = from; i < history.size(); i++) {
            Msg mm = history.get(i);
            boolean last = i == history.size() - 1;
            StringBuilder t = new StringBuilder(mm.text);
            AiClient.M m = new AiClient.M(mm.role, "");
            for (Att a : mm.atts) {
                if (a.image) {
                    if (last && a.b64 != null) m.images.add(new String[]{a.mime, a.b64});
                    else t.append("\n\n[image attached: ").append(a.name).append("]");
                } else if (last) t.append("\n\n[Attached: ").append(a.name).append("]\n```\n").append(a.text).append("\n```");
                else t.append("\n\n[attached: ").append(a.name).append("]");
            }
            m.text = t.toString();
            ms.add(m);
        }
        am.model = sel.m.id;
        am.start = System.currentTimeMillis();
        tick(hd);
        handle = AiClient.chat(act, sel.p, sel.m.id, systemPrompt(), ms, Prefs.b(act, "ai_stream"), new AiClient.Stream() {
            @Override public void token(final String t) {
                h.post(new Runnable() {
                    @Override public void run() {
                        if (!busy || active != hd) return;
                        am.text += t;
                        schedRender(hd);
                    }
                });
            }
            @Override public void reasoning(final String t) {
                h.post(new Runnable() {
                    @Override public void run() {
                        if (!busy || active != hd) return;
                        am.reasoning += t;
                        if (am.text.isEmpty()) schedRender(hd);
                    }
                });
            }
            @Override public void done() {
                h.post(new Runnable() { @Override public void run() { if (busy && active == hd) finish(hd, null); } });
            }
            @Override public void error(final String msg) {
                h.post(new Runnable() { @Override public void run() { if (busy && active == hd) finish(hd, msg); } });
            }
        });
    }

    /** Shows how long the model has been thinking (updates every second until the first answer text). */
    void tick(final Holder hd) {
        h.postDelayed(new Runnable() {
            @Override public void run() {
                if (!busy || active != hd || !hd.msg.text.isEmpty()) return;
                render(hd);
                tick(hd);
            }
        }, 1000);
    }

    void schedRender(final Holder hd) {
        if (renderPending) return;
        renderPending = true;
        h.postDelayed(new Runnable() {
            @Override public void run() {
                renderPending = false;
                boolean nb = nearBottom();
                render(hd);
                if (nb) scrollDown();
            }
        }, 110);
    }

    void finish(final Holder hd, String error) {
        busy = false;
        active = null;
        handle = null;
        updateSend();
        if (error != null) {
            hd.error = true;
            render(hd);
            TextView e = UI.text(act, "⚠ " + error, fontSp - 1, 0xFFFF6B6B);
            e.setTextIsSelectable(true);
            e.setPadding(0, UI.dp(act, 6), 0, UI.dp(act, 4));
            hd.body.addView(e);
            hd.actions.removeAllViews();
            hd.actions.addView(smallBtn("Retry", new Runnable() {
                @Override public void run() {
                    if (busy) return;
                    list.removeView(hd.root);
                    request();
                }
            }));
            hd.actions.addView(smallBtn("Change model", new Runnable() { @Override public void run() { pickModel(); } }));
            scrollDown();
            return;
        }
        if (hd.msg.text.trim().isEmpty() && !hd.msg.reasoning.trim().isEmpty()) {
            hd.msg.text = "_The model only returned its thinking (it may have run out of tokens). Raise “Max reply length” in Settings or pick another model._\n\n" + hd.msg.reasoning;
        }
        if (hd.msg.text.trim().isEmpty()) {
            finish(hd, "The model returned an empty reply. The free model may be busy. Tap Retry or change the model.");
            return;
        }
        history.add(hd.msg);
        render(hd);
        addActions(hd);
        saveHistory();
        scrollDown();
    }

    // ================================================================== attachments
    void refreshAttBar() {
        attBar.removeAllViews();
        if (bridge != null) {
            String n = bridge.currentName();
            if (n != null && !n.isEmpty()) {
                final TextView c = UI.text(act, (useCurrent ? "● " : "○ ") + n, 11, useCurrent ? T.accent : T.sub);
                c.setPadding(UI.dp(act, 10), UI.dp(act, 5), UI.dp(act, 10), UI.dp(act, 5));
                c.setBackground(UI.round(act, useCurrent ? UI.alpha(T.accent, 36) : T.surface2, 14));
                c.setOnClickListener(new OnClickListener() {
                    @Override public void onClick(View v) { useCurrent = !useCurrent; refreshAttBar(); }
                });
                addChip(c);
            }
        }
        for (final Att a : atts) {
            TextView c = UI.text(act, (a.image ? "🖼 " : "📎 ") + a.name + "  ×", 11, T.text);
            c.setPadding(UI.dp(act, 10), UI.dp(act, 5), UI.dp(act, 10), UI.dp(act, 5));
            c.setBackground(UI.round(act, T.surface2, 14));
            c.setSingleLine(true);
            c.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            c.setMaxWidth(UI.dp(act, 200));
            c.setOnClickListener(new OnClickListener() {
                @Override public void onClick(View v) { atts.remove(a); refreshAttBar(); updateSend(); }
            });
            addChip(c);
        }
        attScroll.setVisibility(attBar.getChildCount() == 0 ? GONE : VISIBLE);
    }

    void addChip(View c) {
        LayoutParams lp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = UI.dp(act, 6);
        attBar.addView(c, lp);
    }

    void addAtt(String name, String text) {
        Att a = new Att();
        a.name = name; a.text = text.length() > 20000 ? text.substring(0, 20000) + "\n… truncated" : text;
        atts.add(a);
        refreshAttBar();
        updateSend();
    }

    void attachMenu() {
        final ArrayList<String> items = new ArrayList<>();
        items.add("File from device");
        items.add("Image from device");
        if (bridge != null) {
            items.add("Project file…");
            items.add("Current file");
            items.add("Selected code");
            items.add("Build errors");
            items.add("Project structure");
        }
        Dlg.list(act, "Add to chat", items.toArray(new String[0]), new Dlg.Idx() {
            @Override public void run(int i) {
                if (i == 0) pickDevice("*/*", REQ_FILE);
                else if (i == 1) pickDevice("image/*", REQ_IMAGE);
                else if (i == 2) pickProjectFile();
                else if (i == 3) { useCurrent = true; refreshAttBar(); toast("Current file will be sent"); }
                else if (i == 4) {
                    String s = bridge.selectedText();
                    if (s == null || s.isEmpty()) toast("Select some code in the editor first");
                    else addAtt("selected code", s);
                } else if (i == 5) {
                    String e = bridge.lastErrors();
                    if (e == null || e.trim().isEmpty()) toast("No errors right now"); else addAtt("build errors", e);
                } else if (i == 6) addAtt("project structure", bridge.projectTree());
            }
        });
    }

    void pickDevice(String type, int req) {
        Intent in = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        in.addCategory(Intent.CATEGORY_OPENABLE);
        in.setType(type);
        try { act.startActivityForResult(in, req); } catch (Exception e) { toast("Cannot open file picker"); }
    }

    void pickProjectFile() {
        final List<File> files = bridge.files();
        if (files.isEmpty()) { toast("No files in project"); return; }
        String base = bridge.project().getAbsolutePath();
        String[] names = new String[files.size()];
        for (int i = 0; i < names.length; i++) names[i] = files.get(i).getAbsolutePath().substring(base.length() + 1);
        Dlg.list(act, "Project file", names, new Dlg.Idx() {
            @Override public void run(int i) {
                File f = files.get(i);
                try {
                    if (FileUtil.looksBinary(f)) { toast("Not a text file"); return; }
                    addAtt(f.getName(), FileUtil.read(f));
                } catch (Exception e) { toast("Cannot read file"); }
            }
        });
    }

    /** Call from the host activity's onActivityResult. */
    public boolean handleResult(int req, int res, Intent data) {
        if ((req != REQ_FILE && req != REQ_IMAGE) || res != Activity.RESULT_OK || data == null || data.getData() == null) return req == REQ_FILE || req == REQ_IMAGE;
        Uri u = data.getData();
        String name = queryName(u);
        try {
            if (req == REQ_IMAGE) {
                InputStream in = act.getContentResolver().openInputStream(u);
                Bitmap bm = decodeScaled(u, 1280);
                if (in != null) in.close();
                if (bm == null) { toast("Cannot read image"); return true; }
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                bm.compress(Bitmap.CompressFormat.JPEG, 82, bo);
                Att a = new Att();
                a.name = name == null ? "image.jpg" : name;
                a.image = true; a.mime = "image/jpeg";
                a.b64 = Base64.encodeToString(bo.toByteArray(), Base64.NO_WRAP);
                atts.add(a);
            } else {
                InputStream in = act.getContentResolver().openInputStream(u);
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n, total = 0;
                while (in != null && (n = in.read(buf)) > 0 && total < 120000) { bo.write(buf, 0, n); total += n; }
                if (in != null) in.close();
                byte[] bytes = bo.toByteArray();
                for (int i = 0; i < Math.min(bytes.length, 1024); i++) if (bytes[i] == 0) { toast("This is not a text file"); return true; }
                addAtt(name == null ? "file.txt" : name, new String(bytes, "UTF-8"));
                return true;
            }
        } catch (Exception e) { toast("Cannot read: " + e.getMessage()); }
        refreshAttBar();
        updateSend();
        return true;
    }

    Bitmap decodeScaled(Uri u, int max) throws Exception {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        InputStream in = act.getContentResolver().openInputStream(u);
        BitmapFactory.decodeStream(in, null, o);
        if (in != null) in.close();
        int s = 1;
        while (o.outWidth / s > max * 2 || o.outHeight / s > max * 2) s *= 2;
        BitmapFactory.Options o2 = new BitmapFactory.Options();
        o2.inSampleSize = s;
        in = act.getContentResolver().openInputStream(u);
        Bitmap bm = BitmapFactory.decodeStream(in, null, o2);
        if (in != null) in.close();
        if (bm == null) return null;
        int w = bm.getWidth(), hh = bm.getHeight();
        float sc = Math.min(1f, (float) max / Math.max(w, hh));
        if (sc < 1f) bm = Bitmap.createScaledBitmap(bm, Math.round(w * sc), Math.round(hh * sc), true);
        return bm;
    }

    String queryName(Uri u) {
        Cursor c = null;
        try {
            c = act.getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { } finally { if (c != null) c.close(); }
        return null;
    }

    // ================================================================== model picker
    void pickModel() {
        final ArrayList<AiProvider.Entry> all = AiProvider.entries(act);
        final AiProvider.Entry cur = AiProvider.selected(act);
        final Dlg d = new Dlg(act).title("Choose model");
        final EditText q = Dlg.field(act, "Search models", "");
        d.view(q);
        final LinearLayout col = new LinearLayout(act);
        col.setOrientation(VERTICAL);
        d.scroll(col, 380);
        d.neg("Providers", new Runnable() { @Override public void run() { act.startActivity(new Intent(act, ProvidersActivity.class)); } });
        d.pos("Close", null);
        final Dialog[] dd = new Dialog[1];
        final Runnable[] fill = new Runnable[1];
        fill[0] = new Runnable() {
            @Override public void run() {
                col.removeAllViews();
                String f = q.getText().toString().toLowerCase().trim();
                String lastP = null;
                int shown = 0;
                for (final AiProvider.Entry e : all) {
                    if (!f.isEmpty() && !(e.m.id + " " + e.p.name).toLowerCase().contains(f)) continue;
                    if (f.isEmpty() && shown >= 150) break;
                    shown++;
                    if (!e.p.name.equals(lastP)) {
                        lastP = e.p.name;
                        TextView ph = UI.text(act, e.p.name.toUpperCase(), 10, T.accent);
                        ph.setTypeface(UI.medium());
                        ph.setPadding(UI.dp(act, 6), UI.dp(act, 12), 0, UI.dp(act, 4));
                        col.addView(ph);
                    }
                    LinearLayout row = new LinearLayout(act);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(UI.dp(act, 8), UI.dp(act, 10), UI.dp(act, 8), UI.dp(act, 10));
                    boolean sel = e.p.id.equals(cur.p.id) && e.m.id.equals(cur.m.id);
                    row.setBackground(sel ? UI.round(act, UI.alpha(T.accent, 36), 10) : UI.ripple(UI.alpha(T.accent, 40), null));
                    TextView id = UI.text(act, e.p.isBuiltin() ? "Offline tutor (no internet)" : e.m.id, 13, sel ? T.accent : T.text);
                    id.setSingleLine(true);
                    id.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                    row.addView(id, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    if (e.m.free && !e.p.isBuiltin()) row.addView(freeTag());
                    row.setOnClickListener(new OnClickListener() {
                        @Override public void onClick(View v) {
                            AiProvider.select(act, e.p, e.m);
                            refreshModel();
                            dd[0].dismiss();
                        }
                    });
                    col.addView(row);
                }
                if (shown == 0) col.addView(UI.text(act, "No models. Add a provider in Settings > Assistant.", 13, T.sub));
            }
        };
        q.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a1, int b1, int c1) { }
            @Override public void onTextChanged(CharSequence s, int a1, int b1, int c1) { fill[0].run(); }
            @Override public void afterTextChanged(Editable e) { }
        });
        dd[0] = d.show();
        fill[0].run();
    }

    TextView freeTag() {
        TextView t = UI.text(act, "FREE", 9, 0xFFFFFFFF);
        t.setTypeface(UI.medium());
        t.setPadding(UI.dp(act, 7), UI.dp(act, 2), UI.dp(act, 7), UI.dp(act, 2));
        t.setBackground(UI.round(act, 0xFF2EAD6B, 8));
        return t;
    }

    // ================================================================== history
    File histFile() { return new File(act.getFilesDir(), "chat_" + key.replaceAll("[^A-Za-z0-9_]", "_") + ".json"); }

    void saveHistory() {
        if (!Prefs.b(act, "ai_save_chats")) return;
        try {
            JSONArray a = new JSONArray();
            int from = Math.max(0, history.size() - 60);
            for (int i = from; i < history.size(); i++) {
                Msg m = history.get(i);
                JSONObject o = new JSONObject().put("r", m.role).put("t", m.text);
                if (m.model != null) o.put("m", m.model);
                JSONArray names = new JSONArray();
                for (Att at : m.atts) names.put(at.name);
                o.put("a", names);
                a.put(o);
            }
            FileUtil.write(histFile(), a.toString());
        } catch (Exception ignored) { }
    }

    void loadHistory() {
        if (!Prefs.b(act, "ai_save_chats") || !histFile().exists()) return;
        try {
            JSONArray a = new JSONArray(FileUtil.read(histFile()));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Msg m = new Msg(o.getString("r"), o.getString("t"));
                m.model = o.has("m") ? o.getString("m") : null;
                JSONArray names = o.optJSONArray("a");
                if (names != null) for (int k = 0; k < names.length(); k++) {
                    Att at = new Att();
                    at.name = names.getString(k);
                    m.atts.add(at);
                }
                history.add(m);
            }
            for (int i = 0; i < history.size(); i++) {
                Msg m = history.get(i);
                if (m.role.equals("user")) list.addView(userView(m));
                else {
                    Holder hd = assistantView(m);
                    list.addView(hd.root);
                    render(hd);
                    if (i == history.size() - 1) addActions(hd); else addCopyOnly(hd);
                }
            }
            scrollDown();
        } catch (Exception ignored) { }
    }

    void addCopyOnly(final Holder hd) {
        hd.actions.addView(iconAct(R.drawable.ic_copy, new Runnable() { @Override public void run() { copy(hd.msg.text); } }));
        hd.actions.addView(iconAct(R.drawable.ic_reply, new Runnable() { @Override public void run() { setReply(hd.msg.text.length() > 400 ? hd.msg.text.substring(0, 400) + "…" : hd.msg.text); } }));
    }

    public void newChat() {
        if (busy) stop();
        history.clear();
        histFile().delete();
        list.removeAllViews();
        atts.clear();
        setReply(null);
        refreshAttBar();
        showWelcome();
    }

    public void onResume() { refreshModel(); }
}
