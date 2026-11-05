package com.tanzir.javario;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

/** Powerful custom assistant: offline Java tutor + optional Claude API. */
public class AssistantActivity extends Activity {
    AppThemes T;
    LinearLayout chat;
    ScrollView sv;
    EditText input;
    String code, errors;
    final AssistantEngine engine = new AssistantEngine();
    final JSONArray history = new JSONArray();
    final Handler h = new Handler();

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        T = AppThemes.cur(this);
        AppThemes.window(this);
        code = getIntent().getStringExtra("code");
        errors = getIntent().getStringExtra("errors");
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
        LinearLayout tl = new LinearLayout(this);
        tl.setOrientation(LinearLayout.VERTICAL);
        TextView t = UI.text(this, "Java Assistant", 20, T.text);
        t.setTypeface(UI.medium());
        boolean online = Prefs.i(this, "ai_mode") == 1;
        tl.addView(t);
        tl.addView(UI.text(this, online ? "Online · Claude" : "Offline tutor", 11, T.accent));
        bar.addView(tl);
        root.addView(bar);

        sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        chat = new LinearLayout(this);
        chat.setOrientation(LinearLayout.VERTICAL);
        chat.setPadding(UI.dp(this, 12), UI.dp(this, 8), UI.dp(this, 12), UI.dp(this, 8));
        sv.addView(chat);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setPadding(UI.dp(this, 10), UI.dp(this, 4), UI.dp(this, 10), UI.dp(this, 4));
        for (final String s : new String[]{"Explain my code", "Explain error", "Give me a challenge", "Quiz me", "Java tip", "How do loops work?"}) {
            TextView c = UI.text(this, s, 13, T.accent);
            c.setPadding(UI.dp(this, 14), UI.dp(this, 7), UI.dp(this, 14), UI.dp(this, 7));
            c.setBackground(UI.ripple(UI.alpha(T.accent, 50), UI.round(this, UI.alpha(T.accent, 30), 16)));
            c.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { send(s); } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = UI.dp(this, 8);
            chips.addView(c, lp);
        }
        hs.addView(chips);
        root.addView(hs);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UI.dp(this, 10), UI.dp(this, 2), UI.dp(this, 6), UI.dp(this, 8));
        input = Dlg.field(this, "Ask anything about Java…", "");
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int a, KeyEvent e) { sendInput(); return true; }
        });
        row.addView(input, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageView send = new ImageView(this);
        send.setImageResource(R.drawable.ic_send);
        send.setPadding(UI.dp(this, 11), UI.dp(this, 11), UI.dp(this, 11), UI.dp(this, 11));
        UI.icon(send, T.accent);
        send.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { sendInput(); } });
        row.addView(send, new LinearLayout.LayoutParams(UI.dp(this, 46), UI.dp(this, 46)));
        root.addView(row);
        setContentView(root);

        bubble("Hi! I'm your Java assistant. Ask me about Java, paste an error, or tap a shortcut below.", false);
        if (errors != null && !errors.trim().isEmpty()) {
            bubble("I see build errors from your last run:", false);
            bubble(AssistantEngine.explainError(errors), false);
        }
    }

    void sendInput() {
        String s = input.getText().toString().trim();
        if (s.isEmpty()) return;
        input.setText("");
        send(s);
    }

    void send(final String q) {
        bubble(q, true);
        if (Prefs.i(this, "ai_mode") == 1) {
            final TextView wait = bubble("Thinking…", false);
            try { history.put(new JSONObject().put("role", "user").put("content", q)); } catch (Exception ignored) { }
            new Thread(new Runnable() {
                @Override public void run() {
                    String r;
                    try {
                        r = AssistantEngine.claude(AssistantActivity.this, history, code, errors);
                        history.put(new JSONObject().put("role", "assistant").put("content", r));
                    } catch (Exception e) {
                        r = "Online engine failed: " + e.getMessage() + "\n\nOffline answer:\n" + engine.offline(q, code, errors);
                        try { history.remove(history.length() - 1); } catch (Exception ignored) { }
                    }
                    final String fr = r;
                    runOnUiThread(new Runnable() { @Override public void run() { wait.setText(fr); scrollDown(); } });
                }
            }).start();
        } else {
            final String a = engine.offline(q, code, errors);
            h.postDelayed(new Runnable() { @Override public void run() { bubble(a, false); } }, 250);
        }
    }

    TextView bubble(String s, boolean me) {
        TextView t = UI.text(this, s, 14, me ? 0xFFFFFFFF : T.text);
        t.setTextIsSelectable(true);
        t.setLineSpacing(0, 1.15f);
        t.setPadding(UI.dp(this, 14), UI.dp(this, 10), UI.dp(this, 14), UI.dp(this, 10));
        t.setBackground(UI.round(this, me ? T.accent : T.surface, me ? T.accent : T.stroke, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = me ? Gravity.END : Gravity.START;
        lp.topMargin = UI.dp(this, 6);
        lp.leftMargin = me ? UI.dp(this, 50) : 0;
        lp.rightMargin = me ? 0 : UI.dp(this, 36);
        if (Prefs.b(this, "animations")) { t.setAlpha(0); t.setTranslationY(UI.dp(this, 12)); t.animate().alpha(1).translationY(0).setDuration(220).start(); }
        chat.addView(t, lp);
        scrollDown();
        return t;
    }

    void scrollDown() {
        sv.post(new Runnable() { @Override public void run() { sv.fullScroll(View.FOCUS_DOWN); } });
    }
}
