package com.tanzir.javario;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Part 2: Code IDE - toolbar, tabs, glass file explorer, editor, run. */
public class IdeActivity extends Activity {
    static final int REQ_FILES = 11, REQ_FOLDER = 12;

    AppThemes T;
    File project;
    CodeEditor editor;
    TextView title, emptyHint, errStrip;
    LinearLayout tabs, sugBar, symBar, findBar, explorer;
    HorizontalScrollView tabScroll, sugScroll;
    View scrim;
    ListView treeList;
    TreeAdapter tree;
    EditText findInput, replaceInput;
    ImageView btnUndo, btnRedo;
    final ArrayList<File> openFiles = new ArrayList<>();
    final HashMap<File, CodeEditor.History> histories = new HashMap<>();
    final HashMap<File, Integer> carets = new HashMap<>();
    final HashMap<String, List<CodeEditor.Err>> compileErrs = new HashMap<>();
    File current;
    boolean explorerOpen;
    File importTarget;
    String lastErrors = "";
    int themeSeen;
    final Handler h = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        T = AppThemes.cur(this);
        themeSeen = Prefs.i(this, "app_theme");
        AppThemes.window(this);
        setContentView(R.layout.activity_ide);
        project = new File(getIntent().getStringExtra("project"));
        bind();
        style();
        tree = new TreeAdapter(this);
        tree.setRoot(project);
        treeList.setAdapter(tree);
        treeList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override public void onItemClick(AdapterView<?> a, View v, int i, long id) {
                TreeAdapter.Row r = tree.rows.get(i);
                if (r.dir) tree.toggle(r.f); else { openFile(r.f); closeExplorer(); }
            }
        });
        treeList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override public boolean onItemLongClick(AdapterView<?> a, View v, int i, long id) {
                TreeAdapter.Row r = tree.rows.get(i);
                if (r.dir) folderMenu(r.f); else fileMenu(r.f);
                return true;
            }
        });
        editor.setListener(new CodeEditor.Listener() {
            @Override public void onEdited() { edited(); }
            @Override public void onCursor() { cursorMoved(); }
            @Override public void onZoom(float sp) { Prefs.set(IdeActivity.this, "font_size", Math.round(sp)); }
        });
        buildSymbolBar();
        File main = new File(project, "src/Main.java");
        if (main.exists()) openFile(main);
        else {
            ArrayList<File> all = new ArrayList<>();
            FileUtil.walk(project, all);
            for (File f : all) if (f.getName().endsWith(".java")) { openFile(f); break; }
        }
        if (current == null) showEmpty(true);
        explorer.post(new Runnable() { @Override public void run() { explorer.setTranslationX(-explorer.getWidth()); explorer.setVisibility(View.VISIBLE); } });
    }

    void bind() {
        editor = (CodeEditor) findViewById(R.id.editor);
        title = (TextView) findViewById(R.id.title);
        emptyHint = (TextView) findViewById(R.id.emptyHint);
        errStrip = (TextView) findViewById(R.id.errStrip);
        tabs = (LinearLayout) findViewById(R.id.tabs);
        tabScroll = (HorizontalScrollView) findViewById(R.id.tabScroll);
        sugBar = (LinearLayout) findViewById(R.id.sugBar);
        sugScroll = (HorizontalScrollView) findViewById(R.id.sugScroll);
        symBar = (LinearLayout) findViewById(R.id.symBar);
        findBar = (LinearLayout) findViewById(R.id.findBar);
        explorer = (LinearLayout) findViewById(R.id.explorer);
        scrim = findViewById(R.id.scrim);
        treeList = (ListView) findViewById(R.id.tree);
        findInput = (EditText) findViewById(R.id.findInput);
        replaceInput = (EditText) findViewById(R.id.replaceInput);
        btnUndo = (ImageView) findViewById(R.id.btnUndo);
        btnRedo = (ImageView) findViewById(R.id.btnRedo);
    }

    void style() {
        View root = findViewById(R.id.main);
        root.setBackgroundColor(T.bg);
        findViewById(R.id.toolbar).setBackgroundColor(T.bg);
        title.setTextColor(T.text);
        title.setTypeface(UI.medium());
        title.setText(project.getName());
        int[] ids = {R.id.btnMenu, R.id.btnRun, R.id.btnUndo, R.id.btnRedo, R.id.btnSave, R.id.btnMore, R.id.exNew, R.id.exImport};
        for (int id : ids) UI.icon((ImageView) findViewById(id), T.text);
        UI.tint((ImageView) findViewById(R.id.btnRun), T.accent);
        click(R.id.btnMenu, new Runnable() { @Override public void run() { if (explorerOpen) closeExplorer(); else openExplorer(); } });
        click(R.id.btnRun, new Runnable() { @Override public void run() { runProject(); } });
        click(R.id.btnUndo, new Runnable() { @Override public void run() { editor.undo(); refreshUndo(); } });
        click(R.id.btnRedo, new Runnable() { @Override public void run() { editor.redo(); refreshUndo(); } });
        click(R.id.btnSave, new Runnable() { @Override public void run() { save(true); } });
        click(R.id.btnMore, new Runnable() { @Override public void run() { startActivity(new Intent(IdeActivity.this, SettingsActivity.class)); } });
        click(R.id.exNew, new Runnable() { @Override public void run() { createMenu(project); } });
        click(R.id.exImport, new Runnable() { @Override public void run() { importMenu(project); } });
        tabScroll.setBackgroundColor(T.surface);
        findBar.setBackgroundColor(T.surface);
        for (int id : new int[]{R.id.findPrev, R.id.findNext, R.id.findClose, R.id.replaceOne, R.id.replaceAll}) {
            TextView t = (TextView) findViewById(id);
            t.setTextColor(T.accent);
            t.setBackground(UI.ripple(UI.alpha(T.accent, 40), null));
        }
        for (EditText e : new EditText[]{findInput, replaceInput}) {
            e.setTextColor(T.text);
            e.setHintTextColor(T.sub);
            e.setBackground(UI.round(this, T.surface2, 10));
        }
        click(R.id.findNext, new Runnable() { @Override public void run() { doFind(true); } });
        click(R.id.findPrev, new Runnable() { @Override public void run() { doFind(false); } });
        click(R.id.findClose, new Runnable() { @Override public void run() { findBar.setVisibility(View.GONE); editor.clearFind(); } });
        click(R.id.replaceOne, new Runnable() {
            @Override public void run() {
                if (!editor.replaceCurrent(findInput.getText().toString(), replaceInput.getText().toString(), false)) UI.toast(IdeActivity.this, "No match");
            }
        });
        click(R.id.replaceAll, new Runnable() {
            @Override public void run() {
                int n = editor.replaceAll(findInput.getText().toString(), replaceInput.getText().toString(), false);
                UI.toast(IdeActivity.this, n + " replaced");
            }
        });
        findInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        findInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int a, android.view.KeyEvent e) { doFind(true); return true; }
        });
        explorer.setBackground(UI.round(this, UI.alpha(T.surface, 0xE8), T.stroke, 0));
        ((TextView) findViewById(R.id.exTitle)).setTextColor(T.text);
        ((TextView) findViewById(R.id.exTitle)).setTypeface(UI.medium());
        ((TextView) findViewById(R.id.exTitle)).setText(project.getName());
        emptyHint.setTextColor(T.sub);
        errStrip.setBackgroundColor(UI.alpha(0xFFFF4D4F, 40));
        errStrip.setTextColor(0xFFFF6B6B);
        scrim.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { closeExplorer(); } });
        applyEditorPrefs();
    }

    void click(int id, final Runnable r) {
        findViewById(id).setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { r.run(); } });
    }

    void applyEditorPrefs() {
        editor.applyPrefs();
        EditorThemes et = editor.theme();
        findViewById(R.id.editor).getParent();
        ((View) editor.getParent()).setBackgroundColor(et.bg);
        View sym = findViewById(R.id.symScroll);
        sym.setBackgroundColor(T.surface);
        sugScroll.setBackgroundColor(T.surface);
        sym.setVisibility(Prefs.b(this, "symbol_bar") ? View.VISIBLE : View.GONE);
        if (Prefs.b(this, "keep_screen_on")) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (tree != null) tree.reload();
    }

    @Override protected void onResume() {
        super.onResume();
        if (Prefs.i(this, "app_theme") != themeSeen) { recreate(); return; }
        applyEditorPrefs();
        tree.reload();
    }

    @Override protected void onPause() {
        super.onPause();
        save(false);
    }

    // ------------------------------------------------------------ explorer
    void openExplorer() {
        explorerOpen = true;
        tree.selected = current;
        tree.reload();
        hideKeyboard();
        scrim.setVisibility(View.VISIBLE);
        scrim.setAlpha(0);
        long d = Prefs.b(this, "animations") ? 230 : 0;
        scrim.animate().alpha(1).setDuration(d).start();
        explorer.animate().translationX(0).setDuration(d).start();
    }

    void closeExplorer() {
        explorerOpen = false;
        long d = Prefs.b(this, "animations") ? 200 : 0;
        scrim.animate().alpha(0).setDuration(d).withEndAction(new Runnable() { @Override public void run() { scrim.setVisibility(View.GONE); } }).start();
        explorer.animate().translationX(-explorer.getWidth()).setDuration(d).start();
    }

    @Override public void onBackPressed() {
        if (explorerOpen) { closeExplorer(); return; }
        if (findBar.getVisibility() == View.VISIBLE) { findBar.setVisibility(View.GONE); return; }
        save(false);
        super.onBackPressed();
    }

    void hideKeyboard() {
        android.view.inputmethod.InputMethodManager m = (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        m.hideSoftInputFromWindow(editor.getWindowToken(), 0);
    }

    // ------------------------------------------------------------ files / tabs
    void showEmpty(boolean e) {
        emptyHint.setVisibility(e ? View.VISIBLE : View.GONE);
        editor.setVisibility(e ? View.GONE : View.VISIBLE);
    }

    void openFile(File f) {
        if (f.isDirectory()) return;
        if (f.equals(current)) return;
        if (FileUtil.looksBinary(f)) { UI.toast(this, "This file is not text"); return; }
        save(false);
        stashState();
        try {
            String text = FileUtil.read(f);
            showEmpty(false);
            editor.loadText(text, FileUtil.ext(f));
            CodeEditor.History hh = histories.get(f);
            if (hh != null) editor.setHistory(hh);
            Integer c = carets.get(f);
            if (c != null) editor.setSelection(Math.min(c, editor.getText().length()));
            current = f;
            if (!openFiles.contains(f)) openFiles.add(f);
            editor.setErrors(compileErrs.get(f.getAbsolutePath()));
            refreshTabs();
            refreshUndo();
            cursorMoved();
            triggerLive();
        } catch (IOException e) {
            UI.toast(this, "Cannot open: " + e.getMessage());
        }
    }

    void stashState() {
        if (current != null) {
            histories.put(current, editor.getHistory());
            carets.put(current, editor.getSelectionStart());
        }
    }

    void save(boolean toast) {
        if (current == null) return;
        if (editor.dirty || toast) {
            try {
                FileUtil.write(current, editor.getText().toString());
                editor.dirty = false;
                if (toast) {
                    UI.toast(this, "Saved");
                    if (Prefs.b(this, "haptics")) editor.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                }
            } catch (IOException e) { UI.toast(this, "Save failed: " + e.getMessage()); }
            refreshTabs();
        }
    }

    void closeTab(File f) {
        if (f.equals(current)) { save(false); stashState(); }
        openFiles.remove(f);
        histories.remove(f);
        if (f.equals(current)) {
            current = null;
            if (!openFiles.isEmpty()) { File n = openFiles.get(openFiles.size() - 1); openFile(n); }
            else { showEmpty(true); editor.setErrors(null); }
        }
        refreshTabs();
    }

    boolean shownDirty;

    void refreshTabs() {
        shownDirty = editor.dirty;
        tabs.removeAllViews();
        for (final File f : openFiles) {
            boolean cur = f.equals(current);
            LinearLayout t = new LinearLayout(this);
            t.setGravity(Gravity.CENTER_VERTICAL);
            t.setPadding(UI.dp(this, 12), 0, UI.dp(this, 4), 0);
            TextView n = UI.text(this, f.getName() + (cur && editor.dirty ? " •" : ""), 13, cur ? T.accent : T.sub);
            n.setSingleLine(true);
            t.addView(n);
            TextView x = UI.text(this, "×", 17, cur ? T.accent : T.sub);
            x.setPadding(UI.dp(this, 8), 0, UI.dp(this, 8), UI.dp(this, 2));
            x.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { closeTab(f); } });
            t.addView(x);
            if (cur) t.setBackground(new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[]{
                new android.graphics.drawable.ColorDrawable(UI.alpha(T.accent, 28)), underline()}));
            t.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openFile(f); } });
            tabs.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (cur) tabScroll.post(new Runnable() { @Override public void run() { View c = null; for (int i = 0; i < tabs.getChildCount(); i++) if (openFiles.get(i).equals(current)) c = tabs.getChildAt(i); if (c != null) tabScroll.smoothScrollTo(Math.max(0, c.getLeft() - 40), 0); } });
        }
    }

    android.graphics.drawable.Drawable underline() {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(T.accent);
        return new android.graphics.drawable.InsetDrawable(g, 0, UI.dp(this, 32), 0, 0);
    }

    void refreshUndo() {
        btnUndo.setAlpha(editor.canUndo() ? 1f : 0.35f);
        btnRedo.setAlpha(editor.canRedo() ? 1f : 0.35f);
    }

    // ------------------------------------------------------------ editor callbacks
    final Runnable autoSave = new Runnable() { @Override public void run() { save(false); } };
    final Runnable liveRun = new Runnable() { @Override public void run() { live(); } };
    final Runnable sugRun = new Runnable() { @Override public void run() { updateSuggestions(); } };

    void edited() {
        refreshUndo();
        if (editor.dirty != shownDirty) refreshTabs();
        h.removeCallbacks(autoSave);
        if (Prefs.b(this, "autosave")) h.postDelayed(autoSave, Math.max(1, Prefs.i(this, "autosave_delay")) * 1000L);
        triggerLive();
        h.removeCallbacks(sugRun);
        h.postDelayed(sugRun, 60);
    }

    void cursorMoved() {
        CodeEditor.Err e = editor.errorAtCursor();
        if (e != null) { errStrip.setText("Line " + (e.line + 1) + ": " + e.msg); errStrip.setVisibility(View.VISIBLE); }
        else errStrip.setVisibility(View.GONE);
        h.removeCallbacks(sugRun);
        h.postDelayed(sugRun, 60);
    }

    void triggerLive() {
        h.removeCallbacks(liveRun);
        h.postDelayed(liveRun, 700);
    }

    void live() {
        if (current == null) return;
        if (!Prefs.b(this, "live_errors")) { editor.setErrors(null); return; }
        final String t = editor.getText().toString();
        final String ext = editor.getExt();
        final int ver = editor.version;
        new Thread(new Runnable() {
            @Override public void run() {
                final List<CodeEditor.Err> errs = LiveCheck.check(t, ext);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (ver == editor.version) { editor.setErrors(errs); cursorErr(); }
                    }
                });
            }
        }).start();
    }

    void cursorErr() {
        CodeEditor.Err e = editor.errorAtCursor();
        if (e != null) { errStrip.setText("Line " + (e.line + 1) + ": " + e.msg); errStrip.setVisibility(View.VISIBLE); }
        else errStrip.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------ suggestions / symbols
    void updateSuggestions() {
        if (current == null || !Prefs.b(this, "suggestions")) { sugScroll.setVisibility(View.GONE); return; }
        int caret = editor.getSelectionStart();
        if (caret != editor.getSelectionEnd()) { sugScroll.setVisibility(View.GONE); return; }
        ArrayList<Suggest.Item> items = Suggest.get(editor.getText().toString(), caret, !editor.getExt().equals("xml"));
        sugBar.removeAllViews();
        if (items.isEmpty()) { sugScroll.setVisibility(View.GONE); return; }
        for (final Suggest.Item it : items) {
            TextView c = UI.text(this, it.label, 13, it.kind == 0 ? T.accent : T.text);
            c.setPadding(UI.dp(this, 12), UI.dp(this, 5), UI.dp(this, 12), UI.dp(this, 5));
            c.setBackground(UI.ripple(UI.alpha(T.accent, 50), UI.round(this, T.surface2, 14)));
            c.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    editor.deleteWordBefore();
                    editor.insertText(it.insert);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = UI.dp(this, 6);
            sugBar.addView(c, lp);
        }
        sugScroll.setVisibility(View.VISIBLE);
        sugScroll.scrollTo(0, 0);
    }

    void buildSymbolBar() {
        String[] keys = {"Tab", "{", "}", "(", ")", "[", "]", ";", "\"", "'", "<", ">", "=", "+", "-", "*", "/", "&", "|", "!", "?", ":", ".", ",", "_", "\\", "%", "#"};
        TextView tools = key("Tools");
        tools.setTextColor(T.accent);
        tools.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { tools(); } });
        symBar.addView(tools);
        for (final String k : keys) {
            TextView t = key(k);
            t.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (current == null) return;
                    editor.insertText(k.equals("Tab") ? editor.indentUnit() : k);
                }
            });
            symBar.addView(t);
        }
    }

    TextView key(String s) {
        TextView t = UI.text(this, s, 15, T.text);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(UI.dp(this, 40));
        t.setPadding(UI.dp(this, 10), 0, UI.dp(this, 10), 0);
        t.setBackground(UI.ripple(UI.alpha(T.accent, 50), null));
        t.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return t;
    }

    // ------------------------------------------------------------ tools
    void tools() {
        String[] items = {"Find & Replace", "Go to line", "Format code", "Check for errors (compile)", "Libraries", "Learn & Practice",
            "Java Assistant", "Export project as ZIP", editor.getExt().isEmpty() ? "Word wrap" : (Prefs.b(this, "word_wrap") ? "Word wrap: turn off" : "Word wrap: turn on"), "Close this file"};
        Dlg.list(this, "Tools", items, new Dlg.Idx() {
            @Override public void run(int i) {
                switch (i) {
                    case 0: findBar.setVisibility(View.VISIBLE); findInput.requestFocus(); break;
                    case 1:
                        Dlg.input(IdeActivity.this, "Go to line", "Line number", "", new Dlg.Str() {
                            @Override public void run(String s) {
                                try { editor.goToLine(Integer.parseInt(s)); } catch (NumberFormatException e) { UI.toast(IdeActivity.this, "Enter a number"); }
                            }
                        });
                        break;
                    case 2:
                        if (current != null) editor.replaceAllText(Formatter.format(editor.getText().toString(), editor.getExt(), editor.indentUnit()));
                        break;
                    case 3: compileOnly(); break;
                    case 4: {
                        Intent in = new Intent(IdeActivity.this, LibrariesActivity.class);
                        in.putExtra("project", project.getAbsolutePath());
                        startActivity(in);
                        break;
                    }
                    case 5: startActivity(new Intent(IdeActivity.this, LearnActivity.class)); break;
                    case 6: {
                        Intent in = new Intent(IdeActivity.this, AssistantActivity.class);
                        String code = editor.getText().toString();
                        in.putExtra("code", code.length() > 8000 ? code.substring(0, 8000) : code);
                        in.putExtra("errors", lastErrors);
                        startActivity(in);
                        break;
                    }
                    case 7:
                        try {
                            File z = new File(ProjectStore.exports(), project.getName() + ".zip");
                            FileUtil.zip(project, z);
                            UI.toast(IdeActivity.this, "Saved: " + z.getAbsolutePath());
                        } catch (IOException e) { UI.toast(IdeActivity.this, "Export failed"); }
                        break;
                    case 8:
                        Prefs.set(IdeActivity.this, "word_wrap", !Prefs.b(IdeActivity.this, "word_wrap"));
                        editor.applyPrefs();
                        break;
                    case 9: if (current != null) closeTab(current); break;
                }
            }
        });
    }

    void doFind(boolean fwd) {
        String q = findInput.getText().toString();
        if (!editor.find(q, fwd, false)) UI.toast(this, "Not found");
    }

    // ------------------------------------------------------------ run
    void compileOnly() { runInternal(false); }
    void runProject() { runInternal(true); }

    void runInternal(final boolean launch) {
        save(false);
        if (Prefs.b(this, "haptics")) editor.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
        final TextView status = UI.text(this, "Starting…", 14, T.sub);
        final Dialog d = new Dlg(this).title(launch ? "Running" : "Checking code").view(status).show();
        d.setCancelable(false);
        new Thread(null, new Runnable() {
            @Override public void run() {
                final JavaRunner.Result r = JavaRunner.run(IdeActivity.this, project, new JavaRunner.Log() {
                    @Override public void step(final String s) {
                        runOnUiThread(new Runnable() { @Override public void run() { status.setText(s); } });
                    }
                });
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        d.dismiss();
                        if (r.ok) {
                            lastErrors = "";
                            compileErrs.clear();
                            if (current != null) editor.setErrors(null);
                            if (launch) {
                                Intent i = new Intent(IdeActivity.this, ConsoleActivity.class);
                                i.putExtra("dex", r.dex);
                                i.putExtra("main", r.main);
                                i.putExtra("name", project.getName());
                                startActivity(i);
                            } else UI.toast(IdeActivity.this, "No errors found");
                        } else showErrors(r.errors);
                    }
                });
            }
        }, "javario-compile", 128L * 1024 * 1024).start();
    }

    void showErrors(final String errors) {
        lastErrors = errors;
        compileErrs.clear();
        for (Map.Entry<String, List<Object[]>> en : JavaRunner.parseErrors(errors).entrySet()) {
            ArrayList<CodeEditor.Err> list = new ArrayList<>();
            File f = new File(en.getKey());
            String text = "";
            try { text = FileUtil.read(f); } catch (IOException ignored) { }
            for (Object[] o : en.getValue()) {
                int line = (Integer) o[0];
                int off = 0;
                for (int i = 0; i < line; i++) { int n = text.indexOf('\n', off); if (n < 0) break; off = n + 1; }
                int end = text.indexOf('\n', off);
                if (end < 0) end = text.length();
                int s = off;
                while (s < end && Character.isWhitespace(text.charAt(s))) s++;
                list.add(new CodeEditor.Err(line, s, Math.max(s + 1, end), (String) o[1]));
            }
            compileErrs.put(f.getAbsolutePath(), list);
        }
        if (current != null) { editor.setErrors(compileErrs.get(current.getAbsolutePath())); cursorErr(); }
        TextView t = UI.text(this, errors, 12, 0xFFFF7A7A);
        t.setTypeface(android.graphics.Typeface.MONOSPACE);
        t.setTextIsSelectable(true);
        t.setHorizontallyScrolling(true);
        new Dlg(this).title("Build failed").scroll(t, 320)
            .neg("Ask assistant", new Runnable() {
                @Override public void run() {
                    Intent in = new Intent(IdeActivity.this, AssistantActivity.class);
                    in.putExtra("errors", errors);
                    in.putExtra("code", editor.getText().length() > 8000 ? editor.getText().toString().substring(0, 8000) : editor.getText().toString());
                    startActivity(in);
                }
            })
            .neg("Copy all", new Runnable() {
                @Override public void run() {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("errors", errors));
                    UI.toast(IdeActivity.this, "All errors copied");
                }
            })
            .pos("Close", null).show();
    }

    // ------------------------------------------------------------ file operations
    void createMenu(final File dir) {
        Dlg.list(this, "Create in " + dir.getName(), new String[]{"New file", "New folder"}, new Dlg.Idx() {
            @Override public void run(int i) { if (i == 0) newFile(dir); else newFolder(dir); }
        });
    }

    void newFile(final File dir) {
        Dlg.input(this, "New file", "Name e.g. Utils.java", "", new Dlg.Str() {
            @Override public void run(String n) {
                if (n.isEmpty() || n.contains("/")) { UI.toast(IdeActivity.this, "Invalid name"); return; }
                File f = new File(dir, n);
                if (f.exists()) { UI.toast(IdeActivity.this, "Already exists"); return; }
                try {
                    String body = "";
                    if (n.endsWith(".java")) body = "public class " + FileUtil.baseName(n) + " {\n    \n}\n";
                    else if (n.endsWith(".xml")) body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n";
                    FileUtil.write(f, body);
                    tree.expand(dir);
                    openFile(f);
                    closeExplorer();
                } catch (IOException e) { UI.toast(IdeActivity.this, "Cannot create file"); }
            }
        });
    }

    void newFolder(final File dir) {
        Dlg.input(this, "New folder", "Folder name", "", new Dlg.Str() {
            @Override public void run(String n) {
                if (n.isEmpty()) return;
                if (!new File(dir, n).mkdirs()) UI.toast(IdeActivity.this, "Cannot create folder");
                tree.expand(dir);
                tree.reload();
            }
        });
    }

    void fileMenu(final File f) {
        Dlg.list(this, f.getName(), new String[]{"Open", "Rename", "Duplicate", "Export", "Move / change path", "Copy path", "Delete"}, new Dlg.Idx() {
            @Override public void run(int i) {
                switch (i) {
                    case 0: openFile(f); closeExplorer(); break;
                    case 1: rename(f); break;
                    case 2:
                        try { FileUtil.copyFile(f, new File(f.getParentFile(), FileUtil.uniqueName(f.getParentFile(), f.getName().contains(".") ? FileUtil.baseName(f.getName()) + "_copy." + FileUtil.ext(f) : f.getName() + "_copy"))); } catch (IOException e) { UI.toast(IdeActivity.this, "Failed"); }
                        tree.reload();
                        break;
                    case 3: export(f); break;
                    case 4: move(f); break;
                    case 5: copyPath(f); break;
                    case 6: delete(f); break;
                }
            }
        });
    }

    void folderMenu(final File f) {
        Dlg.list(this, f.getName() + "/", new String[]{"New file", "New folder", "Import here", "Rename", "Export (zip)", "Move / change path", "Copy path", tree.isOpen(f) ? "Collapse" : "Expand", "Delete"}, new Dlg.Idx() {
            @Override public void run(int i) {
                switch (i) {
                    case 0: newFile(f); break;
                    case 1: newFolder(f); break;
                    case 2: importMenu(f); break;
                    case 3: rename(f); break;
                    case 4: export(f); break;
                    case 5: move(f); break;
                    case 6: copyPath(f); break;
                    case 7: tree.toggle(f); break;
                    case 8: delete(f); break;
                }
            }
        });
    }

    void rename(final File f) {
        Dlg.input(this, "Rename", "New name", f.getName(), new Dlg.Str() {
            @Override public void run(String n) {
                File d = new File(f.getParentFile(), n);
                if (n.isEmpty() || d.exists() || !f.renameTo(d)) { UI.toast(IdeActivity.this, "Cannot rename"); return; }
                moved(f, d);
            }
        });
    }

    /** Update open tabs after a file or folder moved. */
    void moved(File from, File to) {
        stashState();
        ArrayList<File> nl = new ArrayList<>();
        File cur = current;
        for (File o : openFiles) {
            String p = o.getAbsolutePath();
            if (p.equals(from.getAbsolutePath()) || p.startsWith(from.getAbsolutePath() + "/")) {
                File n = new File(to, p.substring(from.getAbsolutePath().length()));
                nl.add(n);
                if (o.equals(cur)) cur = n;
                CodeEditor.History hh = histories.remove(o);
                if (hh != null) histories.put(n, hh);
            } else nl.add(o);
        }
        openFiles.clear();
        openFiles.addAll(nl);
        current = cur;
        refreshTabs();
        tree.reload();
    }

    void delete(final File f) {
        Runnable doIt = new Runnable() {
            @Override public void run() {
                ArrayList<File> kill = new ArrayList<>();
                for (File o : openFiles) if (o.getAbsolutePath().startsWith(f.getAbsolutePath())) kill.add(o);
                for (File o : kill) { if (o.equals(current)) { editor.dirty = false; } closeTab(o); }
                FileUtil.deleteRec(f);
                tree.reload();
            }
        };
        if (Prefs.b(this, "confirm_delete")) Dlg.confirm(this, "Delete?", "\"" + f.getName() + "\" will be deleted.", "Delete", doIt);
        else doIt.run();
    }

    void export(File f) {
        try {
            ProjectStore.exports().mkdirs();
            File dest;
            if (f.isDirectory()) { dest = new File(ProjectStore.exports(), f.getName() + ".zip"); FileUtil.zip(f, dest); }
            else { dest = new File(ProjectStore.exports(), f.getName()); FileUtil.copyFile(f, dest); }
            UI.toast(this, "Exported to " + dest.getAbsolutePath());
        } catch (IOException e) { UI.toast(this, "Export failed"); }
    }

    void move(final File f) {
        final ArrayList<File> dirs = new ArrayList<>();
        dirs.add(project);
        collectDirs(project, dirs);
        ArrayList<String> names = new ArrayList<>();
        for (File d : dirs) names.add(d.equals(project) ? "/ (project root)" : d.getAbsolutePath().substring(project.getAbsolutePath().length()));
        Dlg.list(this, "Move to", names.toArray(new String[0]), new Dlg.Idx() {
            @Override public void run(int i) {
                File d = dirs.get(i);
                if (d.getAbsolutePath().startsWith(f.getAbsolutePath())) { UI.toast(IdeActivity.this, "Cannot move into itself"); return; }
                File t = new File(d, f.getName());
                if (t.exists()) { UI.toast(IdeActivity.this, "A file with this name already exists there"); return; }
                if (FileUtil.move(f, t)) { moved(f, t); tree.expand(d); } else UI.toast(IdeActivity.this, "Move failed");
            }
        });
    }

    void collectDirs(File dir, ArrayList<File> out) {
        for (File f : FileUtil.sorted(dir)) if (f.isDirectory() && !f.getName().startsWith(".")) { out.add(f); collectDirs(f, out); }
    }

    void copyPath(File f) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("path", f.getAbsolutePath()));
        UI.toast(this, "Path copied");
    }

    // ------------------------------------------------------------ import
    void importMenu(final File dir) {
        Dlg.list(this, "Import into " + dir.getName(), new String[]{"Files (java, xml, jar, ...)", "Folder"}, new Dlg.Idx() {
            @Override public void run(int i) {
                importTarget = dir;
                if (i == 0) {
                    Intent in = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    in.addCategory(Intent.CATEGORY_OPENABLE);
                    in.setType("*/*");
                    in.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    startActivityForResult(in, REQ_FILES);
                } else startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_FOLDER);
            }
        });
    }

    @Override protected void onActivityResult(final int req, int res, final Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || importTarget == null) return;
        final File target = importTarget;
        new Thread(new Runnable() {
            @Override public void run() {
                int n = 0;
                try {
                    if (req == REQ_FILES) {
                        if (data.getClipData() != null) {
                            for (int i = 0; i < data.getClipData().getItemCount(); i++) { copyUri(data.getClipData().getItemAt(i).getUri(), target); n++; }
                        } else if (data.getData() != null) { copyUri(data.getData(), target); n++; }
                    } else if (req == REQ_FOLDER) {
                        Uri tree = data.getData();
                        String docId = DocumentsContract.getTreeDocumentId(tree);
                        String nm = queryName(DocumentsContract.buildDocumentUriUsingTree(tree, docId));
                        File d = new File(target, nm == null ? "imported" : nm);
                        d.mkdirs();
                        copyChildren(tree, docId, d);
                        n = 1;
                    }
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() { @Override public void run() { UI.toast(IdeActivity.this, "Import failed: " + e.getMessage()); } });
                }
                final int count = n;
                runOnUiThread(new Runnable() {
                    @Override public void run() { tree.expand(target); UI.toast(IdeActivity.this, "Imported " + count + " item(s)"); }
                });
            }
        }).start();
    }

    String queryName(Uri u) {
        Cursor c = null;
        try {
            c = getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception ignored) { } finally { if (c != null) c.close(); }
        return null;
    }

    void copyUri(Uri u, File dir) throws IOException {
        String name = queryName(u);
        if (name == null) name = "file_" + System.currentTimeMillis();
        InputStream in = getContentResolver().openInputStream(u);
        if (in == null) throw new IOException("Cannot read file");
        OutputStream out = new FileOutputStream(new File(dir, name));
        try { FileUtil.copy(in, out); } finally { in.close(); out.close(); }
    }

    void copyChildren(Uri tree, String parentId, File dest) throws IOException {
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId);
        Cursor c = getContentResolver().query(children, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null);
        if (c == null) return;
        try {
            while (c.moveToNext()) {
                String id = c.getString(0), name = c.getString(1), mime = c.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    File d = new File(dest, name);
                    d.mkdirs();
                    copyChildren(tree, id, d);
                } else copyUri(DocumentsContract.buildDocumentUriUsingTree(tree, id), dest);
            }
        } finally { c.close(); }
    }
}
