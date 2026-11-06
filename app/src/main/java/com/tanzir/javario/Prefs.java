package com.tanzir.javario;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashMap;

/** Settings store + the full list of settings shown on the Settings page. */
public class Prefs {
    // key, type, default, title, description, extra(options or min:max)
    public static final String[][] DEFS = {
        {"#", "header", "", "General", "", ""},
        {"app_theme", "choice", "1", "App theme", "Light, Dark, AMOLED, Ocean or Sunset", "Light,Dark,AMOLED Black,Ocean Blue,Sunset"},
        {"animations", "switch", "1", "Animations", "Screen, dialog and button animations", ""},
        {"haptics", "switch", "1", "Haptic feedback", "Small vibration on run and save", ""},
        {"keep_screen_on", "switch", "0", "Keep screen on in editor", "Screen will not sleep while coding", ""},
        {"confirm_delete", "switch", "1", "Confirm before delete", "Ask before deleting files and projects", ""},
        {"show_hidden", "switch", "0", "Show hidden files", "Show files that start with a dot", ""},

        {"#", "header", "", "Code editor", "", ""},
        {"editor_theme", "choice", "0", "Editor theme", "10 light and dark code themes", "Javario Dark,Javario Light,Monokai,Dracula,Solarized Dark,Solarized Light,GitHub Light,Nord,One Dark,Tokyo Night"},
        {"font_size", "seek", "12", "Font size", "Also changes when you pinch with two fingers (6-30)", "6:30"},
        {"font_family", "choice", "0", "Code font", "Typeface used in the editor", "Monospace,Sans mono (light),Serif mono"},
        {"line_spacing", "seek", "12", "Line spacing", "x0.1 (10 = 1.0, 20 = 2.0)", "10:20"},
        {"line_numbers", "switch", "1", "Line numbers", "Show the gutter with line numbers", ""},
        {"word_wrap", "switch", "0", "Word wrap", "Wrap long lines instead of scrolling sideways", ""},
        {"highlight_line", "switch", "1", "Highlight current line", "", ""},
        {"syntax_highlight", "switch", "1", "Syntax highlighting", "Colors for Java and XML", ""},
        {"pinch_zoom", "switch", "1", "Pinch to zoom", "Two-finger zoom like MT Manager", ""},
        {"tab_size", "choice", "1", "Tab size", "Spaces per indent", "2,4,8"},
        {"use_spaces", "switch", "1", "Use spaces for tab", "Turn off to insert real tab characters", ""},
        {"auto_indent", "switch", "1", "Auto indent", "Keep indentation on new lines", ""},
        {"auto_close", "switch", "1", "Auto close brackets & quotes", "( [ { \" ' are closed automatically", ""},
        {"live_errors", "switch", "1", "Live error check", "Red underline for syntax mistakes while typing", ""},
        {"suggestions", "switch", "1", "Code suggestions", "Keywords, snippets, methods and variables", ""},
        {"symbol_bar", "switch", "1", "Symbol bar", "Quick keys above the keyboard", ""},
        {"autosave", "switch", "1", "Auto save", "Save the file automatically after you stop typing", ""},
        {"autosave_delay", "seek", "3", "Auto save delay (seconds)", "", "1:30"},

        {"#", "header", "", "Run & console", "", ""},
        {"java_level", "choice", "0", "Java language level", "Java 11 is experimental on-device", "Java 8,Java 11"},
        {"main_class", "text", "", "Main class (optional)", "Leave empty to detect Main automatically", ""},
        {"auto_libs", "switch", "1", "Auto download libraries", "Download jars for imports found in your code", ""},
        {"run_save_all", "switch", "1", "Save before run", "", ""},
        {"console_font", "seek", "12", "Console font size", "", "8:22"},
        {"console_autoscroll", "switch", "1", "Console auto scroll", "", ""},
        {"show_timing", "switch", "1", "Show run time", "Print how long the program ran", ""},

        {"#", "header", "", "Assistant", "", ""},
        {"ai_stream", "switch", "1", "Stream replies", "Show the answer while it is being written", ""},
        {"ai_temperature", "seek", "7", "Creativity", "x0.1 - lower is more exact, higher is more creative", "0:20"},
        {"ai_max_tokens", "seek", "1500", "Max reply length (tokens)", "", "256:4096"},
        {"ai_history", "seek", "12", "Chat memory (messages)", "How many earlier messages the model can see", "2:40"},
        {"ai_font", "seek", "13", "Chat text size", "Small text like ChatGPT / Claude", "10:18"},
        {"ai_include_code", "switch", "1", "Send current file in IDE chat", "The open file is attached to your messages (can be turned off in the chat)", ""},
        {"ai_include_tree", "switch", "1", "Send project structure", "Lets the AI know your file names", ""},
        {"ai_auto_bug", "switch", "1", "Auto bug detection", "After a failed run, the AI panel opens and analyses the errors", ""},
        {"ai_confirm_apply", "switch", "1", "Confirm before AI changes code", "You can always undo", ""},
        {"ai_save_chats", "switch", "1", "Save chat history", "", ""},
        {"ai_system", "text", "", "Custom instructions", "Extra rules added to every AI request", ""},
    };

    private static HashMap<String, String[]> map;

    public static String[] def(String k) {
        if (map == null) {
            map = new HashMap<>();
            for (String[] d : DEFS) if (!d[0].equals("#")) map.put(d[0], d);
        }
        return map.get(k);
    }

    static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("javario", Context.MODE_PRIVATE);
    }

    public static boolean b(Context c, String k) {
        String[] d = def(k);
        return sp(c).getBoolean(k, d != null && d[2].equals("1"));
    }

    public static int i(Context c, String k) {
        String[] d = def(k);
        int dv = 0;
        try { dv = Integer.parseInt(d[2]); } catch (Exception ignored) { }
        try { return sp(c).getInt(k, dv); } catch (ClassCastException e) { return dv; }
    }

    public static String s(Context c, String k) {
        String[] d = def(k);
        return sp(c).getString(k, d == null ? "" : d[2]);
    }

    public static void set(Context c, String k, Object v) {
        SharedPreferences.Editor e = sp(c).edit();
        if (v instanceof Boolean) e.putBoolean(k, (Boolean) v);
        else if (v instanceof Integer) e.putInt(k, (Integer) v);
        else e.putString(k, String.valueOf(v));
        e.apply();
    }
}
