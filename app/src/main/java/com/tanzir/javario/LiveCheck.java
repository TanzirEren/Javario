package com.tanzir.javario;

import android.util.Xml;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

/** Fast "live error" checker (no compiler needed): brackets, strings, missing semicolons, XML syntax. */
public class LiveCheck {
    static final HashSet<String> CONTROL = new HashSet<>(Arrays.asList(
        "if", "for", "while", "else", "do", "try", "catch", "finally", "switch", "case", "default", "class",
        "interface", "enum", "import", "package", "synchronized"));

    public static List<CodeEditor.Err> check(String text, String ext) {
        try {
            if (ext.equals("java")) return java(text);
            if (ext.equals("xml")) return xml(text);
        } catch (Throwable ignored) { }
        return new ArrayList<>();
    }

    static int lineOf(String t, int off) {
        int l = 0;
        for (int i = 0; i < off && i < t.length(); i++) if (t.charAt(i) == '\n') l++;
        return l;
    }

    static List<CodeEditor.Err> java(String t) {
        ArrayList<CodeEditor.Err> errs = new ArrayList<>();
        int n = t.length();
        char[] m = t.toCharArray();
        // mask comments and strings with spaces
        int i = 0;
        while (i < n) {
            char c = m[i];
            if (c == '/' && i + 1 < n && m[i + 1] == '/') {
                while (i < n && m[i] != '\n') m[i++] = ' ';
            } else if (c == '/' && i + 1 < n && m[i + 1] == '*') {
                m[i++] = ' '; m[i++] = ' ';
                while (i < n && !(m[i] == '*' && i + 1 < n && m[i + 1] == '/')) { if (m[i] != '\n') m[i] = ' '; i++; }
                if (i < n) { m[i++] = ' '; if (i < n) m[i++] = ' '; }
            } else if (c == '"' && i + 2 < n && m[i + 1] == '"' && m[i + 2] == '"') {
                m[i++] = ' '; m[i++] = ' '; m[i++] = ' ';
                while (i < n && !(m[i] == '"' && i + 2 < n && m[i + 1] == '"' && m[i + 2] == '"')) { if (m[i] != '\n') m[i] = ' '; i++; }
                for (int k = 0; k < 3 && i < n; k++) m[i++] = ' ';
            } else if (c == '"' || c == '\'') {
                int start = i;
                char q = c;
                m[i++] = ' ';
                boolean closed = false;
                while (i < n && m[i] != '\n') {
                    if (m[i] == '\\' && i + 1 < n && m[i + 1] != '\n') { m[i++] = ' '; m[i++] = ' '; continue; }
                    if (m[i] == q) { m[i++] = ' '; closed = true; break; }
                    m[i++] = ' ';
                }
                if (!closed) {
                    int end = i;
                    errs.add(new CodeEditor.Err(lineOf(t, start), start, Math.max(start + 1, end),
                        q == '"' ? "Unclosed string literal" : "Unclosed character literal"));
                }
            } else i++;
        }
        // brackets
        int[] stack = new int[n + 1];
        int sp = 0;
        for (int k = 0; k < n; k++) {
            char c = m[k];
            if (c == '(' || c == '[' || c == '{') stack[sp++] = k;
            else if (c == ')' || c == ']' || c == '}') {
                if (sp == 0) { errs.add(new CodeEditor.Err(lineOf(t, k), k, k + 1, "Unmatched '" + c + "'")); }
                else {
                    char o = m[stack[sp - 1]];
                    if ((o == '(' && c == ')') || (o == '[' && c == ']') || (o == '{' && c == '}')) sp--;
                    else { errs.add(new CodeEditor.Err(lineOf(t, k), k, k + 1, "Expected closing for '" + o + "' but found '" + c + "'")); sp--; }
                }
                if (errs.size() > 25) return errs;
            }
        }
        while (sp > 0 && errs.size() < 30) {
            int k = stack[--sp];
            errs.add(new CodeEditor.Err(lineOf(t, k), k, k + 1, "Unclosed '" + m[k] + "'"));
        }
        // missing semicolons (conservative)
        String[] lines = new String(m).split("\n", -1);
        int off = 0, depth = 0;
        for (int li = 0; li < lines.length && errs.size() < 30; li++) {
            String ln = lines[li];
            String tr = ln.trim();
            int lineOff = off;
            off += ln.length() + 1;
            if (depth >= 2 && !tr.isEmpty() && needsSemi(tr, nextNonEmpty(lines, li + 1))) {
                int rt = ln.length();
                while (rt > 0 && Character.isWhitespace(ln.charAt(rt - 1))) rt--;
                int end = lineOff + rt;
                errs.add(new CodeEditor.Err(li, Math.max(lineOff, end - 1), end, "';' expected"));
            }
            for (int k = 0; k < ln.length(); k++) {
                char c = ln.charAt(k);
                if (c == '{') depth++; else if (c == '}') depth--;
            }
        }
        return errs;
    }

    static String nextNonEmpty(String[] lines, int from) {
        for (int i = from; i < lines.length; i++) {
            String s = lines[i].trim();
            if (!s.isEmpty()) return s;
        }
        return "";
    }

    static boolean needsSemi(String t, String next) {
        char last = t.charAt(t.length() - 1);
        if ("{};,(:>+-*/=&|?.[<%^!~@".indexOf(last) >= 0 && !(t.endsWith("++") || t.endsWith("--"))) return false;
        if (t.charAt(0) == '}' || t.charAt(0) == '@' || t.charAt(0) == '.') return false;
        String first = t.split("[^A-Za-z]", 2)[0];
        if (CONTROL.contains(first)) return false;
        if (!next.isEmpty()) {
            char nc = next.charAt(0);
            if (".+-*/?:&|)]{=,<>%^".indexOf(nc) >= 0) return false;
            if (next.startsWith("else") || next.startsWith("catch") || next.startsWith("finally") || next.startsWith("while")) {
                if (!first.equals("return") && !first.equals("break")) return false;
            }
        }
        if (t.matches("[A-Z_][A-Z0-9_]*(\\(.*\\))?")) return false;
        if (first.equals("return") || first.equals("break") || first.equals("continue") || first.equals("throw") || first.equals("yield")) return true;
        if (t.contains("=")) return true;
        if (last == ')') return !(first.equals("public") || first.equals("private") || first.equals("protected") || first.equals("static") || first.equals("abstract") || first.equals("final") && !t.contains("="));
        return t.matches("^[\\w.<>\\[\\],? ]+\\s+[A-Za-z_]\\w*$") && !first.equals("public") && !first.equals("private") && !first.equals("protected") && !first.equals("static") && !first.equals("abstract");
    }

    static List<CodeEditor.Err> xml(String t) {
        ArrayList<CodeEditor.Err> errs = new ArrayList<>();
        if (t.trim().isEmpty()) return errs;
        try {
            XmlPullParser p = Xml.newPullParser();
            p.setInput(new StringReader(t));
            while (p.next() != XmlPullParser.END_DOCUMENT) { }
        } catch (XmlPullParserException e) {
            int line = Math.max(1, e.getLineNumber()) - 1;
            int col = Math.max(0, e.getColumnNumber() - 1);
            int off = 0, l = 0;
            while (l < line && off < t.length()) {
                int nl = t.indexOf('\n', off);
                if (nl < 0) break;
                off = nl + 1;
                l++;
            }
            int start = Math.min(t.length(), off + col);
            if (start > off) start = Math.max(off, start - 1);
            String msg = e.getMessage() == null ? "XML error" : e.getMessage();
            int cut = msg.indexOf(" (position");
            if (cut > 0) msg = msg.substring(0, cut);
            errs.add(new CodeEditor.Err(l, start, Math.min(t.length(), start + 1), msg));
        } catch (Exception ignored) { }
        return errs;
    }
}
