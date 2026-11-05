package com.tanzir.javario;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.EditText;
import java.util.ArrayList;
import java.util.List;

/**
 * Code editor: syntax highlight, line numbers, current-line highlight, red error squiggles,
 * undo/redo, auto indent, auto close, pinch zoom, find/replace.
 */
public class CodeEditor extends EditText {
    public interface Listener {
        void onEdited();
        void onCursor();
        void onZoom(float sp);
    }

    public static class Err {
        public final int line, start, end;
        public final String msg;
        public Err(int line, int start, int end, String msg) { this.line = line; this.start = start; this.end = end; this.msg = msg; }
    }

    public static class Edit {
        int start; String oldT, newT; boolean chain; long time;
    }

    public static class History {
        final ArrayList<Edit> undo = new ArrayList<>();
        final ArrayList<Edit> redo = new ArrayList<>();
    }

    static class FindSpan extends BackgroundColorSpan {
        FindSpan(int c) { super(c); }
    }

    private Listener listener;
    private EditorThemes th = EditorThemes.get(0);
    private String ext = "java";
    private boolean loading, applying, selfEdit, pending;
    public boolean dirty;
    public int version;
    private History hist = new History();
    private final Paint gutterBg = new Paint(), gutterTxt = new Paint(Paint.ANTI_ALIAS_FLAG), curPaint = new Paint(),
        errPaint = new Paint(Paint.ANTI_ALIAS_FLAG), sepPaint = new Paint();
    private final ArrayList<Err> errors = new ArrayList<>();
    private boolean showLines = true, hiLine = true, autoIndent = true, autoClose = true, synHL = true, pinch = true, wrap = false;
    private int tabSize = 4;
    private boolean useSpaces = true;
    private float sizeSp = 12f;
    private int gutterW, digits = 2;
    private int lastStart, lastBefore, lastCount;
    private String beforeText = "";
    private final Handler h = new Handler(Looper.getMainLooper());
    private ScaleGestureDetector sgd;
    private final Rect clip = new Rect();
    private int findStart = -1, findEnd = -1;

    public CodeEditor(Context c) { super(c); init(); }
    public CodeEditor(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        setBackground(null);
        setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
            | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        setHorizontallyScrolling(true);
        setVerticalScrollBarEnabled(true);
        setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI | android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN);
        setFocusableInTouchMode(true);
        errPaint.setStyle(Paint.Style.STROKE);
        errPaint.setColor(0xFFFF4D4F);
        errPaint.setStrokeWidth(UI.dp(getContext(), 1.4f));
        gutterTxt.setTextAlign(Paint.Align.RIGHT);
        addTextChangedListener(watcher);
        sgd = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                setSizeSp(sizeSp * d.getScaleFactor());
                return true;
            }
            @Override public void onScaleEnd(ScaleGestureDetector d) {
                if (listener != null) listener.onZoom(sizeSp);
            }
        });
    }

    public void setListener(Listener l) { listener = l; }
    public String getExt() { return ext; }

    // ------------------------------------------------------------------ settings
    public void applyPrefs() {
        Context c = getContext();
        th = EditorThemes.get(Prefs.i(c, "editor_theme"));
        sizeSp = Prefs.i(c, "font_size");
        showLines = Prefs.b(c, "line_numbers");
        hiLine = Prefs.b(c, "highlight_line");
        synHL = Prefs.b(c, "syntax_highlight");
        pinch = Prefs.b(c, "pinch_zoom");
        autoIndent = Prefs.b(c, "auto_indent");
        autoClose = Prefs.b(c, "auto_close");
        wrap = Prefs.b(c, "word_wrap");
        useSpaces = Prefs.b(c, "use_spaces");
        int[] tabs = {2, 4, 8};
        tabSize = tabs[Math.max(0, Math.min(2, Prefs.i(c, "tab_size")))];
        int ff = Prefs.i(c, "font_family");
        setTypeface(ff == 1 ? Typeface.create("sans-serif-light", Typeface.NORMAL)
            : ff == 2 ? Typeface.create("serif-monospace", Typeface.NORMAL) : Typeface.MONOSPACE);
        setHorizontallyScrolling(!wrap);
        setLineSpacing(0, Prefs.i(c, "line_spacing") / 10f);
        setTextColor(th.fg);
        setHighlightColor(th.sel);
        setHintTextColor(th.gutterFg);
        gutterBg.setColor(th.gutterBg);
        gutterTxt.setColor(th.gutterFg);
        sepPaint.setColor(UI.alpha(th.gutterFg, 60));
        curPaint.setColor(th.curLine);
        if (Build.VERSION.SDK_INT >= 29) {
            GradientDrawable cur = new GradientDrawable();
            cur.setColor(th.keyword);
            cur.setSize(UI.dp(c, 2), UI.dp(c, 20));
            setTextCursorDrawable(cur);
        }
        applySize();
        scheduleHighlight(0);
        invalidate();
    }

    public EditorThemes theme() { return th; }

    public void setSizeSp(float s) {
        sizeSp = Math.max(6f, Math.min(36f, s));
        applySize();
    }

    public float getSizeSp() { return sizeSp; }

    private void applySize() {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        gutterTxt.setTextSize(getTextSize() * 0.9f);
        gutterTxt.setTypeface(Typeface.MONOSPACE);
        applyPadding();
    }

    private void applyPadding() {
        if (showLines) gutterW = (int) (gutterTxt.measureText("0") * Math.max(2, digits) + UI.dp(getContext(), 18));
        else gutterW = 0;
        setPadding(gutterW + UI.dp(getContext(), 8), UI.dp(getContext(), 6), UI.dp(getContext(), 14), UI.dp(getContext(), 80));
    }

    public String indentUnit() {
        if (!useSpaces) return "\t";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tabSize; i++) sb.append(' ');
        return sb.toString();
    }

    // ------------------------------------------------------------------ text
    public void loadText(String text, String extension) {
        loading = true;
        ext = extension;
        errors.clear();
        clearFind();
        setText(text);
        loading = false;
        hist = new History();
        dirty = false;
        version++;
        setSelection(0);
        scheduleHighlight(0);
    }

    public History getHistory() { return hist; }
    public void setHistory(History hh) { hist = hh == null ? new History() : hh; }

    public void setErrors(List<Err> list) {
        errors.clear();
        if (list != null) errors.addAll(list);
        invalidate();
    }

    public List<Err> getErrors() { return errors; }

    public Err errorAtCursor() {
        Layout l = getLayout();
        if (l == null) return null;
        int sel = Math.max(0, getSelectionStart());
        String t = getText().toString();
        int line = 0;
        for (int i = 0; i < sel && i < t.length(); i++) if (t.charAt(i) == '\n') line++;
        for (Err e : errors) if (e.line == line) return e;
        return null;
    }

    // ------------------------------------------------------------------ watcher / undo
    private final TextWatcher watcher = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int st, int cnt, int aft) {
            if (loading || applying) return;
            beforeText = s.subSequence(st, Math.min(s.length(), st + cnt)).toString();
        }

        @Override public void onTextChanged(CharSequence s, int st, int bef, int cnt) {
            if (loading || applying) return;
            String nt = s.subSequence(st, Math.min(s.length(), st + cnt)).toString();
            record(st, beforeText, nt);
            lastStart = st; lastBefore = bef; lastCount = cnt;
            pending = true;
        }

        @Override public void afterTextChanged(Editable e) {
            if (loading || applying) return;
            dirty = true;
            version++;
            if (!selfEdit && pending) {
                pending = false;
                smartEdit(e);
            }
            scheduleHighlight(80);
            if (listener != null) listener.onEdited();
        }
    };

    private void record(int st, String oldT, String newT) {
        if (oldT.equals(newT)) return;
        long now = System.currentTimeMillis();
        hist.redo.clear();
        if (!selfEdit && !hist.undo.isEmpty()) {
            Edit last = hist.undo.get(hist.undo.size() - 1);
            if (!last.chain && last.oldT.isEmpty() && oldT.isEmpty() && newT.length() == 1
                && newT.charAt(0) != '\n' && !Character.isWhitespace(newT.charAt(0))
                && st == last.start + last.newT.length() && now - last.time < 900) {
                last.newT += newT;
                last.time = now;
                return;
            }
        }
        Edit e = new Edit();
        e.start = st; e.oldT = oldT; e.newT = newT; e.chain = selfEdit; e.time = now;
        hist.undo.add(e);
        if (hist.undo.size() > 600) hist.undo.remove(0);
    }

    public boolean canUndo() { return !hist.undo.isEmpty(); }
    public boolean canRedo() { return !hist.redo.isEmpty(); }

    private void applyEdit(Edit e, boolean undo) {
        Editable t = getText();
        int s = Math.min(e.start, t.length());
        String from = undo ? e.newT : e.oldT, to = undo ? e.oldT : e.newT;
        int en = Math.min(s + from.length(), t.length());
        t.replace(s, en, to);
        setSelection(Math.min(s + to.length(), getText().length()));
    }

    public void undo() {
        if (hist.undo.isEmpty()) return;
        applying = true;
        try {
            Edit e;
            do {
                e = hist.undo.remove(hist.undo.size() - 1);
                applyEdit(e, true);
                hist.redo.add(e);
            } while (e.chain && !hist.undo.isEmpty());
        } finally { applying = false; }
        afterApply();
    }

    public void redo() {
        if (hist.redo.isEmpty()) return;
        applying = true;
        try {
            Edit e = hist.redo.remove(hist.redo.size() - 1);
            applyEdit(e, false);
            hist.undo.add(e);
            while (!hist.redo.isEmpty() && hist.redo.get(hist.redo.size() - 1).chain) {
                e = hist.redo.remove(hist.redo.size() - 1);
                applyEdit(e, false);
                hist.undo.add(e);
            }
        } finally { applying = false; }
        afterApply();
    }

    private void afterApply() {
        dirty = true;
        version++;
        scheduleHighlight(0);
        if (listener != null) listener.onEdited();
    }

    // ------------------------------------------------------------------ auto indent / auto close
    private void smartEdit(Editable e) {
        if (lastBefore != 0 || lastCount != 1 || lastStart >= e.length()) return;
        char ch = e.charAt(lastStart);
        selfEdit = true;
        try {
            if (ch == '\n' && autoIndent) {
                int ls = lastStart;
                int lineStart = ls;
                while (lineStart > 0 && e.charAt(lineStart - 1) != '\n') lineStart--;
                int k = lineStart;
                while (k < ls && (e.charAt(k) == ' ' || e.charAt(k) == '\t')) k++;
                String ind = e.subSequence(lineStart, k).toString();
                int p = ls - 1;
                while (p >= 0 && (e.charAt(p) == ' ' || e.charAt(p) == '\t')) p--;
                char prev = p >= 0 ? e.charAt(p) : 0;
                char next = ls + 1 < e.length() ? e.charAt(ls + 1) : 0;
                boolean opener = (prev == '{' || prev == '(' || prev == '[' || (ext.equals("xml") && prev == '>' && false));
                if (opener && ((prev == '{' && next == '}') || (prev == '(' && next == ')') || (prev == '[' && next == ']'))) {
                    String mid = ind + indentUnit();
                    e.insert(ls + 1, mid + "\n" + ind);
                    setSelection(ls + 1 + mid.length());
                } else {
                    String add = ind + (opener ? indentUnit() : "");
                    if (!add.isEmpty()) e.insert(ls + 1, add);
                    setSelection(ls + 1 + add.length());
                }
            } else if (autoClose) {
                char next = lastStart + 1 < e.length() ? e.charAt(lastStart + 1) : 0;
                char prev = lastStart > 0 ? e.charAt(lastStart - 1) : ' ';
                if (ch == '(' || ch == '[' || ch == '{') {
                    char close = ch == '(' ? ')' : ch == '[' ? ']' : '}';
                    e.insert(lastStart + 1, String.valueOf(close));
                    setSelection(lastStart + 1);
                } else if (ch == '"' || ch == '\'') {
                    if (next == ch) {
                        e.delete(lastStart + 1, lastStart + 2);
                        setSelection(lastStart + 1);
                    } else if (!Character.isLetterOrDigit(prev) && prev != '\\' && !(ch == '\'' && ext.equals("xml") && false)) {
                        e.insert(lastStart + 1, String.valueOf(ch));
                        setSelection(lastStart + 1);
                    }
                } else if ((ch == ')' || ch == ']' || ch == '}') && next == ch) {
                    e.delete(lastStart + 1, lastStart + 2);
                    setSelection(lastStart + 1);
                }
            }
        } catch (Exception ignored) {
        } finally { selfEdit = false; }
    }

    // ------------------------------------------------------------------ highlight
    private final Runnable hlRun = new Runnable() {
        @Override public void run() { highlightNow(); }
    };

    private void scheduleHighlight(long delay) {
        h.removeCallbacks(hlRun);
        h.postDelayed(hlRun, delay);
    }

    private void highlightNow() {
        Editable e = getText();
        if (!synHL) {
            for (Highlighter.HL s : e.getSpans(0, e.length(), Highlighter.HL.class)) e.removeSpan(s);
            return;
        }
        int len = e.length();
        int from = 0, to = len;
        if (len > 120000) {
            Layout l = getLayout();
            if (l != null) {
                int top = getScrollY();
                int a = l.getLineForVertical(Math.max(0, top));
                int b = l.getLineForVertical(Math.max(0, top + getHeight()));
                from = l.getLineStart(Math.max(0, a - 60));
                to = l.getLineEnd(Math.min(l.getLineCount() - 1, b + 60));
            }
        }
        Highlighter.apply(e, ext, th, from, to);
    }

    @Override protected void onScrollChanged(int l, int t, int ol, int ot) {
        super.onScrollChanged(l, t, ol, ot);
        if (getText().length() > 120000) scheduleHighlight(120);
    }

    @Override protected void onSelectionChanged(int selStart, int selEnd) {
        super.onSelectionChanged(selStart, selEnd);
        if (listener != null && !loading) listener.onCursor();
    }

    // ------------------------------------------------------------------ drawing
    @Override protected void onDraw(Canvas c) {
        Layout l = getLayout();
        int pt = getExtendedPaddingTop();
        if (l != null && hiLine && l.getLineCount() > 0) {
            int sel = getSelectionStart();
            if (sel >= 0 && sel <= getText().length()) {
                int line = l.getLineForOffset(sel);
                c.drawRect(getScrollX(), l.getLineTop(line) + pt, getScrollX() + getWidth(), l.getLineBottom(line) + pt, curPaint);
            }
        }
        super.onDraw(c);
        if (l == null) return;
        drawErrors(c, l, pt);
        if (showLines) drawGutter(c, l, pt);
    }

    private void drawGutter(Canvas c, Layout l, int pt) {
        c.getClipBounds(clip);
        int total = l.getLineCount();
        int dg = Math.max(2, String.valueOf(total).length());
        if (dg != digits) { digits = dg; post(new Runnable() { @Override public void run() { applyPadding(); } }); }
        float x0 = getScrollX();
        c.drawRect(x0, clip.top, x0 + gutterW, clip.bottom, gutterBg);
        c.drawRect(x0 + gutterW - 1, clip.top, x0 + gutterW, clip.bottom, sepPaint);
        int first = l.getLineForVertical(Math.max(0, clip.top - pt));
        int last = l.getLineForVertical(Math.max(0, clip.bottom - pt));
        CharSequence t = getText();
        int logical = 0;
        if (wrap) {
            int st = l.getLineStart(first);
            for (int i = 0; i < st && i < t.length(); i++) if (t.charAt(i) == '\n') logical++;
        }
        int cur = -1;
        int sel = getSelectionStart();
        if (sel >= 0 && sel <= t.length()) cur = l.getLineForOffset(sel);
        for (int i = first; i <= last && i < total; i++) {
            int st = l.getLineStart(i);
            boolean para = i == 0 || t.charAt(st - 1) == '\n';
            if (wrap && i > first && para) logical++;
            if (!para) continue;
            int num = wrap ? logical : i;
            boolean hasErr = false;
            for (Err er : errors) if (er.line == num) { hasErr = true; break; }
            gutterTxt.setColor(hasErr ? 0xFFFF4D4F : (i == cur ? th.fg : th.gutterFg));
            c.drawText(String.valueOf(num + 1), x0 + gutterW - UI.dp(getContext(), 8), l.getLineBaseline(i) + pt, gutterTxt);
        }
    }

    private void drawErrors(Canvas c, Layout l, int pt) {
        int len = getText().length();
        int step = UI.dp(getContext(), 3);
        for (Err er : errors) {
            if (er.start >= len || er.start < 0) continue;
            int s = er.start;
            int line = l.getLineForOffset(s);
            int e2 = Math.min(Math.max(er.end, s + 1), l.getLineVisibleEnd(line));
            if (e2 <= s) e2 = Math.min(len, s + 1);
            float x1 = l.getPrimaryHorizontal(s) + getCompoundPaddingLeft();
            float x2 = l.getPrimaryHorizontal(e2) + getCompoundPaddingLeft();
            if (x2 - x1 < step * 3) x2 = x1 + step * 3;
            float y = l.getLineBaseline(line) + pt + UI.dp(getContext(), 2);
            Path p = new Path();
            p.moveTo(x1, y);
            boolean up = true;
            for (float x = x1; x < x2; x += step) {
                p.lineTo(x + step, y + (up ? -step * 0.6f : step * 0.6f));
                up = !up;
            }
            c.drawPath(p, errPaint);
        }
    }

    // ------------------------------------------------------------------ touch (pinch zoom)
    @Override public boolean onTouchEvent(MotionEvent ev) {
        if (pinch) {
            sgd.onTouchEvent(ev);
            if (sgd.isInProgress()) return true;
        }
        return super.onTouchEvent(ev);
    }

    // ------------------------------------------------------------------ helpers for IDE
    public void insertText(String s) {
        Editable e = getText();
        int st = Math.max(0, Math.min(getSelectionStart(), getSelectionEnd()));
        int en = Math.max(0, Math.max(getSelectionStart(), getSelectionEnd()));
        int ls = st;
        while (ls > 0 && e.charAt(ls - 1) != '\n') ls--;
        int k = ls;
        while (k < e.length() && k < st && (e.charAt(k) == ' ' || e.charAt(k) == '\t')) k++;
        String indent = e.subSequence(ls, k).toString();
        String t = s.replace("\t", indentUnit()).replace("\n", "\n" + indent);
        int caret = t.indexOf("$0");
        t = t.replace("$0", "");
        e.replace(st, en, t);
        setSelection(Math.min(st + (caret < 0 ? t.length() : caret), getText().length()));
    }

    public String wordBefore() {
        Editable e = getText();
        int p = getSelectionStart();
        if (p < 0) return "";
        int s = p;
        while (s > 0 && Suggest.isId(e.charAt(s - 1))) s--;
        return e.subSequence(s, p).toString();
    }

    public void deleteWordBefore() {
        Editable e = getText();
        int p = getSelectionStart();
        if (p < 0) return;
        int s = p;
        while (s > 0 && Suggest.isId(e.charAt(s - 1))) s--;
        if (s < p) e.delete(s, p);
    }

    public void goToLine(int line) {
        String t = getText().toString();
        int idx = 0;
        for (int i = 1; i < line; i++) {
            int n = t.indexOf('\n', idx);
            if (n < 0) { idx = t.length(); break; }
            idx = n + 1;
        }
        requestFocus();
        setSelection(Math.min(idx, t.length()));
    }

    public void replaceAllText(String s) {
        Editable e = getText();
        int caret = Math.min(getSelectionStart(), s.length());
        e.replace(0, e.length(), s);
        setSelection(Math.max(0, caret));
    }

    // find / replace -------------------------------------------------------
    public void clearFind() {
        Editable e = getText();
        for (FindSpan f : e.getSpans(0, e.length(), FindSpan.class)) e.removeSpan(f);
        findStart = findEnd = -1;
    }

    public boolean find(String q, boolean fwd, boolean cs) {
        clearFind();
        if (q.isEmpty()) return false;
        String t = getText().toString();
        String hay = cs ? t : t.toLowerCase();
        String n = cs ? q : q.toLowerCase();
        int idx;
        if (fwd) {
            idx = hay.indexOf(n, Math.max(0, getSelectionEnd()));
            if (idx < 0) idx = hay.indexOf(n);
        } else {
            int from = getSelectionStart() - 1;
            idx = from < 0 ? -1 : hay.lastIndexOf(n, from);
            if (idx < 0) idx = hay.lastIndexOf(n);
        }
        if (idx < 0) return false;
        findStart = idx; findEnd = idx + q.length();
        getText().setSpan(new FindSpan(0x88FFD54F), findStart, findEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        setSelection(idx);
        bringPointIntoView(idx);
        return true;
    }

    public boolean replaceCurrent(String q, String r, boolean cs) {
        if (findStart < 0) return find(q, true, cs);
        Editable e = getText();
        int s = findStart;
        int en = findEnd;
        clearFind();
        if (en <= e.length()) e.replace(s, en, r);
        setSelection(Math.min(s + r.length(), getText().length()));
        return find(q, true, cs);
    }

    public int replaceAll(String q, String r, boolean cs) {
        if (q.isEmpty()) return 0;
        clearFind();
        String t = getText().toString();
        String hay = cs ? t : t.toLowerCase();
        String n = cs ? q : q.toLowerCase();
        StringBuilder sb = new StringBuilder();
        int i = 0, count = 0;
        while (true) {
            int idx = hay.indexOf(n, i);
            if (idx < 0) break;
            sb.append(t, i, idx).append(r);
            i = idx + q.length();
            count++;
        }
        if (count == 0) return 0;
        sb.append(t.substring(i));
        replaceAllText(sb.toString());
        return count;
    }
}
