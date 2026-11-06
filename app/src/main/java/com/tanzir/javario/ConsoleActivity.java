package com.tanzir.javario;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
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

/** Console page. The program itself runs in RunnerService (separate process). */
public class ConsoleActivity extends Activity {
    AppThemes T;
    TextView out, status;
    ScrollView sv;
    EditText in;
    final SpannableStringBuilder sb = new SpannableStringBuilder();
    final Handler h = new Handler(Looper.getMainLooper());
    boolean autoScroll, finished, stopping, bound;
    float fontSp;
    Messenger service;
    String dex, main, jars, project;
    long t0;

    final Handler replyHandler = new Handler(Looper.getMainLooper()) {
        @Override public void handleMessage(Message m) {
            String t = m.getData().getString("t", "");
            if (m.what == RunnerService.MSG_OUT) add(t, false);
            else if (m.what == RunnerService.MSG_ERR) add(t, true);
            else if (m.what == RunnerService.MSG_DONE) {
                finished = true;
                status.setText("Finished");
                if (Prefs.b(ConsoleActivity.this, "show_timing")) add("\n[Process finished in " + m.getData().getLong("n") + " ms]\n", false);
            }
        }
    };
    final Messenger reply = new Messenger(replyHandler);

    final ServiceConnection conn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            service = new Messenger(b);
            Message m = Message.obtain(null, RunnerService.MSG_RUN);
            Bundle d = new Bundle();
            d.putString("dex", dex); d.putString("main", main); d.putString("jars", jars); d.putString("project", project);
            m.setData(d);
            m.replyTo = reply;
            try { service.send(m); } catch (RemoteException e) { add("Cannot start the runner: " + e + "\n", true); }
        }
        @Override public void onServiceDisconnected(ComponentName n) {
            service = null;
            if (!finished) {
                finished = true;
                status.setText(stopping ? "Stopped" : "Ended");
                add(stopping ? "\n[Stopped]\n" : "\n[Program ended: System.exit() was called, or it crashed]\n", true);
            }
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        T = AppThemes.cur(this);
        AppThemes.window(this);
        autoScroll = Prefs.b(this, "console_autoscroll");
        fontSp = Prefs.i(this, "console_font");
        dex = getIntent().getStringExtra("dex");
        main = getIntent().getStringExtra("main");
        jars = getIntent().getStringExtra("jars");
        project = getIntent().getStringExtra("projectDir");
        if (jars == null) jars = "";
        if (project == null) project = "";
        String name = getIntent().getStringExtra("name");
        build(name == null ? "Console" : name);
        start();
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
        bar.addView(iconBtn(R.drawable.ic_refresh, new Runnable() { @Override public void run() { restart(); } }));
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

    void start() {
        finished = false;
        stopping = false;
        t0 = System.currentTimeMillis();
        status.setText("Running…");
        Intent i = new Intent(this, RunnerService.class);
        bound = bindService(i, conn, Context.BIND_AUTO_CREATE);
        if (!bound) add("Cannot start the runner service.\n", true);
    }

    void restart() {
        killRunner();
        sb.clear();
        out.setText(sb);
        h.postDelayed(new Runnable() { @Override public void run() { start(); } }, 400);
    }

    void killRunner() {
        if (service != null) {
            try { service.send(Message.obtain(null, RunnerService.MSG_STOP)); } catch (RemoteException ignored) { }
        }
        stopping = true;
        if (bound) {
            try { unbindService(conn); } catch (Exception ignored) { }
            bound = false;
        }
        service = null;
    }

    void stop() {
        if (finished) { finish(); return; }
        killRunner();
        finished = true;
        status.setText("Stopped");
        add("\n[Stopped]\n", true);
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        killRunner();
    }

    void sendInput() {
        String s = in.getText().toString();
        in.setText("");
        add("> " + s + "\n", false);
        if (service == null) return;
        Message m = Message.obtain(null, RunnerService.MSG_INPUT);
        Bundle b = new Bundle();
        b.putString("t", s);
        m.setData(b);
        try { service.send(m); } catch (RemoteException ignored) { }
    }

    void add(String s, boolean err) {
        int st = sb.length();
        sb.append(s);
        if (err) sb.setSpan(new ForegroundColorSpan(0xFFFF5C5C), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        else if (s.startsWith("> ")) sb.setSpan(new ForegroundColorSpan(T.accent), st, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (sb.length() > 300000) sb.delete(0, sb.length() - 250000);
        h.removeCallbacks(flush);
        h.postDelayed(flush, 40);
    }

    final Runnable flush = new Runnable() {
        @Override public void run() {
            out.setText(sb);
            if (autoScroll) sv.post(new Runnable() { @Override public void run() { sv.fullScroll(View.FOCUS_DOWN); } });
        }
    };

    void copy() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("console", sb.toString()));
        UI.toast(this, "Console copied");
    }

    void options() {
        Dlg.list(this, "Console", new String[]{"Share output", "Bigger text", "Smaller text", autoScroll ? "Turn auto scroll off" : "Turn auto scroll on", "Copy all"}, new Dlg.Idx() {
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
