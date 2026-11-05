package com.tanzir.javario;

import android.text.Editable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import java.util.Arrays;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Regex based syntax highlighter for Java and XML. */
public class Highlighter {
    static class HL extends ForegroundColorSpan {
        HL(int c) { super(c); }
    }

    static final HashSet<String> KW = new HashSet<>(Arrays.asList(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
        "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
        "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
        "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
        "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "var", "record",
        "yield", "sealed", "permits"));

    static final Pattern JAVA = Pattern.compile(
        "(//[^\\n]*|/\\*[\\s\\S]*?(?:\\*/|$))" +
        "|(\"\"\"[\\s\\S]*?(?:\"\"\"|$)|\"(?:\\\\.|[^\"\\\\\\n])*\"?|'(?:\\\\.|[^'\\\\\\n])*'?)" +
        "|(@[A-Za-z_]\\w*)" +
        "|(\\b0[xX][0-9a-fA-F_]+[lL]?\\b|\\b\\d[\\d_]*(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFdDlL]?\\b)" +
        "|([A-Za-z_$][\\w$]*)");

    static final Pattern XML = Pattern.compile(
        "(<!--[\\s\\S]*?(?:-->|$))" +
        "|(</?[A-Za-z_?][\\w:.-]*|<\\?[A-Za-z]+)" +
        "|([A-Za-z_][\\w:.-]*)(?=\\s*=)" +
        "|(\"[^\"\\n]*\"?|'[^'\\n]*'?)" +
        "|(/?>|\\?>)");

    public static void apply(Editable e, String ext, EditorThemes t, int from, int to) {
        int len = e.length();
        if (to > len) to = len;
        if (from < 0) from = 0;
        HL[] old = e.getSpans(from, to, HL.class);
        for (HL s : old) e.removeSpan(s);
        if (from >= to) return;
        if (ext.equals("xml") || ext.equals("html")) xml(e, t, from, to);
        else if (ext.equals("java") || ext.equals("kt") || ext.equals("js") || ext.equals("json") || ext.equals("gradle") || ext.equals("css"))
            java(e, t, from, to);
    }

    private static void span(Editable e, int color, int s, int en) {
        e.setSpan(new HL(color), s, en, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private static void java(Editable e, EditorThemes t, int from, int to) {
        Matcher m = JAVA.matcher(e);
        m.region(from, to);
        int len = e.length();
        while (m.find()) {
            if (m.group(1) != null) span(e, t.comment, m.start(1), m.end(1));
            else if (m.group(2) != null) span(e, t.string, m.start(2), m.end(2));
            else if (m.group(3) != null) span(e, t.annotation, m.start(3), m.end(3));
            else if (m.group(4) != null) span(e, t.number, m.start(4), m.end(4));
            else if (m.group(5) != null) {
                String w = m.group(5);
                if (KW.contains(w)) span(e, t.keyword, m.start(5), m.end(5));
                else if (Character.isUpperCase(w.charAt(0))) span(e, t.type, m.start(5), m.end(5));
                else {
                    int p = m.end(5);
                    while (p < len && e.charAt(p) == ' ') p++;
                    if (p < len && e.charAt(p) == '(') span(e, t.func, m.start(5), m.end(5));
                }
            }
        }
    }

    private static void xml(Editable e, EditorThemes t, int from, int to) {
        Matcher m = XML.matcher(e);
        m.region(from, to);
        while (m.find()) {
            if (m.group(1) != null) span(e, t.comment, m.start(1), m.end(1));
            else if (m.group(2) != null) span(e, t.tag, m.start(2), m.end(2));
            else if (m.group(3) != null) span(e, t.attr, m.start(3), m.end(3));
            else if (m.group(4) != null) span(e, t.string, m.start(4), m.end(4));
            else if (m.group(5) != null) span(e, t.tag, m.start(5), m.end(5));
        }
    }
}
