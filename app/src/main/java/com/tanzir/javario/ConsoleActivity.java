package com.tanzir.javario;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import dalvik.system.DexClassLoader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

/** Console page. Runs in its own process (:console) so Stop / System.exit never closes the IDE. */
public class ConsoleActivity extends Activity {
    static class LineInput extends InputStream {
        final LinkedBlockingQueue<Integer> q = new LinkedBlockingQueue<>();
        void feed(String s) { for (byte b : (s + "\n").getBytes(StandardCharsets.UTF_8)) q.add(b & 0xff); }
        @Override public int read() throws IOException {
            try { return q.take(); } catch (InterruptedException e) { return -1; }
        }
        @Override public int read(byte[] b, int o, int l) throws IOException {
            if (l == 0) return 0;
            int f = read();
            if (f < 0) return -1;
            b[o] = (byte) f;
            int n = 1;
            Integer x;
            while (n < l && (x = q.poll()) != null) b[o + n++] = (byte) (int) x;
            return n;
        }
        @Override public int available() { return q.size(); }
    }

    class Sink extends OutputStream {
        final boolean err;
        Sink(boolean e) { err = e; }
        @Override public void write(int b) { write(new byte[]{(byte) b}, 0, 1); }
        @Override public void write(byte[] b, int off, int len) { post(new String(b, off, len, StandardCharsets.UTF_8), err); }
    }

    static class Seg { String s; boolean err; Seg(String s, boolean e) { this.s = s; err = e; } }

    AppThemes T;
    TextView out;
    ScrollView sv;
    EditText in;
    final LineInput stdin = new LineInput();
    final SpannableStringBuilder sb = new SpannableStringBuilder();
    final ArrayList<Seg> queue = new ArrayList<>();
    final Handler h = new Handler(Looper.getMainLooper());
    boolean scheduled, autoScroll, finished;
    float fontSp;
    TextView status;
    long t0;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        T = AppThemes.cur(this);
        AppThemes.window(this);
        autoScroll = Prefs.b(this, "console_autoscroll");
        fontSp = Prefs.i(this, "console_font");
        final String name = getIntent().getStringExtra("name");
        build(name == null ? "Console" : name);
        startRun();
    }

    void build(String name) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(UI.dp(this, 4), UI.dp(this, 4), UI.dp(this, 4), UI.dp(this, 4));
        bar.addView(iconBtn(R.drawable.ic_back, new Runnable() { @Override public void run() { finish(); } }));
        LinearLayout tl = new LinearLayout(this);
        tl.setOrientation(LinearLayout.VERTICAL);
        tl.setPadding(UI.dp(this, 6), 0, 0, 0);
        TextView t = UI.text(this, "Console · " + name, 15, T.text);
        t.setSingleLine(true);
        t.setTypeface(UI.medium());
        status = UI.text(this, "Running…", 11, T.accent);
        tl.addView(t);
        tl.addView(status);
        bar.addView(tl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(iconBtn(R.drawable.ic_stop, new Runnable() { @Override public void run() { stop(); } }));
        bar.addView(iconBtn(R.drawable.ic_copy, new Runnable() { @Override public void run() { copy(); } }));
        bar.addView(iconBtn(R.drawable.ic_delete, new Runnable() { @Override public void run() { sb.clear(); out.setText(sb); } }));
        bar.addView(iconBtn(R.drawable.ic_more, new Runnable() { @Override public void run() { options(); } }));
        root.addView(bar);

        sv = new ScrollView(this);
        out = new TextView(this);
        out.setTypeface(Typeface.MONOSPACE);
        out.setTextSize(fontSp);
        out.setTextColor(T.text);
        out.setTextIsSelectable(true);
        out.setPadding(UI.dp(this, 14), UI.dp(this, 10), UI.dp(this, 14), UI.dp(this, 20));
        sv.addView(out);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        sp.setMargins(UI.dp(this, 10), UI.dp(this, 4), UI.dp(this, 10), UI.dp(this, 6));
        sv.setBackground(UI.round(this, T.surface, T.stroke, 14));
        root.addView(sv, sp);

        LinearLayout inRow = new LinearLayout(this);
        inRow.setGravity(Gravity.CENTER_VERTICAL);
        inRow.setPadding(UI.dp(this, 10), 0, UI.dp(this, 6), UI.dp(this, 8));
        in = Dlg.field(this, "Type input for Scanner / System.in", "");
        in.setImeOptions(EditorInfo.IME_ACTION_SEND);
        in.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int a, KeyEvent e) { sendInput(); return true; }
        });
        inRow.addView(in, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        inRow.addView(iconBtn(R.drawable.ic_send, new Runnable() { @Override public void run() { sendInput(); } }));
        root.addView(inRow);
        setContentView(root);
    }

    View iconBtn(int res, final Runnable r) {
        ImageView v = new ImageView(this);
        v.setImageResource(res);
        v.setPadding(UI.dp(this, 10), UI.dp(this, 10), UI.dp(this, 10), UI.dp(this, 10));
        UI.icon(v, T.text);
        v.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View x) { r.run(); } });
        v.setLayoutParams(new LinearLayout.LayoutParams(UI.dp(this, 42), UI.dp(this, 42)));
        return v;
    }

    void sendInput() {
        String s = in.getText().toString();
        in.setText("");
        post("> " + s + "\n", false);
        stdin.feed(s);
    }

    void post(String s, boolean err) {
        synchronized (queue) {
            queue.add(new Seg(s, err));
            if (scheduled) return;
            scheduled = true;
        }
        h.postDelayed(flush, 40);
    }

    final Runnable flush = new Runnable() {
        @Override public void run() {
            ArrayList<Seg> segs;
            synchronized (queue) { segs = new ArrayList<>(queue); queue.clear(); scheduled = false; }
            for (Seg s : segs) {
                int st = sb.length();
                sb.append(s.s);
                if (s.err) sb.setSpan(new ForegroundColorSpan(0xFFFF5C5C), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                else if (s.s.startsWith("> ")) sb.setSpan(new ForegroundColorSpan(T.accent), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (sb.length() > 300000) sb.delete(0, sb.length() - 250000);
            out.setText(sb);
            if (autoScroll) sv.post(new Runnable() { @Override public void run() { sv.fullScroll(View.FOCUS_DOWN); } });
        }
    };

    void startRun() {
        final String dex = getIntent().getStringExtra("dex");
        final String main = getIntent().getStringExtra("main");
        System.setOut(new PrintStream(new Sink(false), true));
        System.setErr(new PrintStream(new Sink(true), true));
        System.setIn(stdin);
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) { e.printStackTrace(); }
        });
        t0 = System.currentTimeMillis();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    DexClassLoader cl = new DexClassLoader(dex, getCodeCacheDir().getAbsolutePath(), null, ConsoleActivity.class.getClassLoader());
                    Class<?> c = cl.loadClass(main);
                    Method m = c.getMethod("main", String[].class);
                    m.invoke(null, (Object) new String[0]);
                } catch (InvocationTargetException e) {
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    c.printStackTrace();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                final long ms = System.currentTimeMillis() - t0;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        finished = true;
                        status.setText("Finished");
                        if (Prefs.b(ConsoleActivity.this, "show_timing")) post("\n[Process finished in " + ms + " ms]\n", false);
                    }
                });
            }
        }, "javario-main").start();
    }

    void stop() {
        if (finished) { finish(); return; }
        post("\n[Stopped]\n", true);
        h.postDelayed(new Runnable() { @Override public void run() { Process.killProcess(Process.myPid()); } }, 150);
    }

    void copy() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("console", sb.toString()));
        UI.toast(this, "Console copied");
    }

    void options() {
        Dlg.list(this, "Console", new String[]{"Share output", "Bigger text", "Smaller text", autoScroll ? "Turn auto scroll off" : "Turn auto scroll on", "Select all & copy"}, new Dlg.Idx() {
            @Override public void run(int i) {
                if (i == 0) {
                    Intent s = new Intent(Intent.ACTION_SEND);
                    s.setType("text/plain");
                    s.putExtra(Intent.EXTRA_TEXT, sb.toString());
                    startActivity(Intent.createChooser(s, "Share output"));
                } else if (i == 1) { fontSp = Math.min(30, fontSp + 1); out.setTextSize(fontSp); }
                else if (i == 2) { fontSp = Math.max(7, fontSp - 1); out.setTextSize(fontSp); }
                else if (i == 3) autoScroll = !autoScroll;
                else copy();
            }
        });
    }
}
