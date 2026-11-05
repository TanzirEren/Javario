package com.tanzir.javario;

import android.graphics.Color;

/** 10 code editor themes (light / dark). */
public class EditorThemes {
    public final String name;
    public final boolean dark;
    public final int bg, fg, gutterBg, gutterFg, curLine, keyword, type, string, comment, number, annotation, func, tag, attr, sel;

    EditorThemes(String n, boolean d, String... c) {
        name = n; dark = d;
        int[] v = new int[c.length];
        for (int i = 0; i < c.length; i++) v[i] = Color.parseColor("#" + c[i]);
        bg = v[0]; fg = v[1]; gutterBg = v[2]; gutterFg = v[3]; curLine = v[4]; keyword = v[5]; type = v[6];
        string = v[7]; comment = v[8]; number = v[9]; annotation = v[10]; func = v[11]; tag = v[12]; attr = v[13]; sel = v[14];
    }

    // bg fg gutterBg gutterFg curLine keyword type string comment number annotation func tag attr selection
    public static final EditorThemes[] ALL = {
        new EditorThemes("Javario Dark", true, "11131A", "DDE1EC", "11131A", "4B5266", "1A1E2A", "FF6B9D", "4DD0E1", "A5D6A7", "5F6B85", "FFB74D", "FFD54F", "82AAFF", "FF6B9D", "FFB74D", "2D3550"),
        new EditorThemes("Javario Light", false, "FFFFFF", "24292F", "F6F8FA", "8C959F", "F3F6FB", "D6336C", "0B7285", "2B8A3E", "868E96", "E8590C", "B08800", "3B5BDB", "D6336C", "E8590C", "CFE0FF"),
        new EditorThemes("Monokai", true, "272822", "F8F8F2", "272822", "75715E", "3E3D32", "F92672", "66D9EF", "E6DB74", "75715E", "AE81FF", "A6E22E", "A6E22E", "F92672", "A6E22E", "49483E"),
        new EditorThemes("Dracula", true, "282A36", "F8F8F2", "282A36", "6272A4", "44475A", "FF79C6", "8BE9FD", "F1FA8C", "6272A4", "BD93F9", "50FA7B", "50FA7B", "FF79C6", "50FA7B", "44475A"),
        new EditorThemes("Solarized Dark", true, "002B36", "839496", "002B36", "586E75", "073642", "859900", "B58900", "2AA198", "586E75", "D33682", "CB4B16", "268BD2", "268BD2", "93A1A1", "0A4A5A"),
        new EditorThemes("Solarized Light", false, "FDF6E3", "657B83", "EEE8D5", "93A1A1", "EEE8D5", "859900", "B58900", "2AA198", "93A1A1", "D33682", "CB4B16", "268BD2", "268BD2", "586E75", "D5CFB7"),
        new EditorThemes("GitHub Light", false, "FFFFFF", "24292E", "FFFFFF", "959DA5", "F6F8FA", "D73A49", "6F42C1", "032F62", "6A737D", "005CC5", "22863A", "6F42C1", "22863A", "6F42C1", "C8E1FF"),
        new EditorThemes("Nord", true, "2E3440", "D8DEE9", "2E3440", "4C566A", "3B4252", "81A1C1", "8FBCBB", "A3BE8C", "616E88", "B48EAD", "D08770", "88C0D0", "81A1C1", "8FBCBB", "434C5E"),
        new EditorThemes("One Dark", true, "282C34", "ABB2BF", "282C34", "4B5263", "2C313C", "C678DD", "E5C07B", "98C379", "5C6370", "D19A66", "E5C07B", "61AFEF", "E06C75", "D19A66", "3E4451"),
        new EditorThemes("Tokyo Night", true, "1A1B26", "C0CAF5", "1A1B26", "3B4261", "24283B", "BB9AF7", "2AC3DE", "9ECE6A", "565F89", "FF9E64", "E0AF68", "7AA2F7", "F7768E", "E0AF68", "33467C"),
    };

    public static EditorThemes get(int i) {
        if (i < 0 || i >= ALL.length) i = 0;
        return ALL[i];
    }
}
