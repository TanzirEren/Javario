package com.tanzir.javario;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Java Assistant page: the shared ChatView full screen. */
public class AssistantActivity extends Activity {
    AppThemes T;
    ChatView chat;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        T = AppThemes.cur(this);
        AppThemes.window(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(UI.dp(this, 6), UI.dp(this, 8), UI.dp(this, 6), UI.dp(this, 4));
        bar.addView(btn(R.drawable.ic_back, new Runnable() { @Override public void run() { finish(); } }));
        TextView t = UI.text(this, "Java Assistant", 20, T.text);
        t.setTypeface(UI.medium());
        bar.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        bar.addView(btn(R.drawable.ic_chat_new, new Runnable() { @Override public void run() { chat.newChat(); } }));
        bar.addView(btn(R.drawable.ic_settings, new Runnable() {
            @Override public void run() { startActivity(new Intent(AssistantActivity.this, ProvidersActivity.class)); }
        }));
        root.addView(bar);
        chat = new ChatView(this, null, "assistant");
        chat.setStaticContext(getIntent().getStringExtra("code"), getIntent().getStringExtra("errors"));
        root.addView(chat, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        String errors = getIntent().getStringExtra("errors");
        if (errors != null && !errors.trim().isEmpty()) chat.autoSend("Explain these build errors and how to fix them.", "build errors", errors);
    }

    ImageView btn(int res, final Runnable r) {
        ImageView v = new ImageView(this);
        v.setImageResource(res);
        v.setPadding(UI.dp(this, 10), UI.dp(this, 10), UI.dp(this, 10), UI.dp(this, 10));
        UI.icon(v, T.text);
        v.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View x) { r.run(); } });
        v.setLayoutParams(new LinearLayout.LayoutParams(UI.dp(this, 44), UI.dp(this, 44)));
        return v;
    }

    @Override protected void onResume() {
        super.onResume();
        if (chat != null) chat.onResume();
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        chat.handleResult(req, res, data);
    }
}
