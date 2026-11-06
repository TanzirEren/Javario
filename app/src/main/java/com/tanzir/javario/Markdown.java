package com.tanzir.javario;

import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.View;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small Markdown renderer (headings, bold, italic, code, lists, quotes, links, tables, code blocks). */
public class Markdown {
    public static class Block {
        public boolean code;
        public String lang = "", text = "", path;
    }

    static final Pattern HEAD = Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$");
    static final Pattern BULLET = Pattern.compile("^(\\s*)[-*+]\\s+(.*)$");
    static final Pattern NUM = Pattern.compile("^(\\s*)(\\d+)[.)]\\s+(.*)$");
    static final Pattern QUOTE = Pattern.compile("^\\s*>\\s?(.*)$");
    static final Pattern HR = Pattern.compile("^\\s*([-*_])(\\s*\\1){2,}\\s*$");
    static final Pattern TABLE_SEP = Pattern.compile("^\\s*\\|?[\\s:|-]*-[\\s:|-]*\\|?\\s*$");
    static final Pattern INLINE = Pattern.compile(
        "(\\*\\*(.+?)\\*\\*)|(`([^`\\n]+)`)|(\\[([^\\]\\n]+)\\]\\(([^)\\s]+)\\))|(~~(.+?)~~)|(\\*(?!\\s)([^*\\n]+?)\\*)");
    static final Pattern PATH_HINT = Pattern.compile("(?i)(?:file|path|filename)\\s*[:：]\\s*[`*\"']*([\\w./\\-]+\\.\\w{1,6})");
    static final Pattern PATH_ONLY = Pattern.compile("^[`*\\s]*([\\w./\\-]+/[\\w.\\-]+\\.\\w{1,6}|[\\w\\-]+\\.(?:java|xml))[`*:\\s]*$");

    /** Splits a reply into normal text blocks and fenced code blocks. */
    public static ArrayList<Block> split(String md) {
        ArrayList<Block> out = new ArrayList<>();
        String[] lines = md.split("\n", -1);
        StringBuilder text = new StringBuilder();
        StringBuilder code = null;
        String lang = "";
        String fence = "";
        for (String ln : lines) {
            String t = ln.trim();
            if (code == null) {
                if (t.startsWith("```") || t.startsWith("~~~")) {
                    fence = t.substring(0, 3);
                    lang = t.substring(3).trim();
                    flushText(out, text);
                    code = new StringBuilder();
                    continue;
                }
                text.append(ln).append('\n');
            } else {
                if (t.startsWith(fence) && t.replace(fence.charAt(0) + "", "").isEmpty()) {
                    Block b = new Block();
                    b.code = true; b.lang = lang;
                    b.text = trimEnd(code.toString());
                    b.path = pathHint(out, b);
                    out.add(b);
                    code = null;
                    continue;
                }
                code.append(ln).append('\n');
            }
        }
        if (code != null) {
            Block b = new Block();
            b.code = true; b.lang = lang; b.text = trimEnd(code.toString());
            b.path = pathHint(out, b);
            out.add(b);
        } else flushText(out, text);
        return out;
    }

    static void flushText(ArrayList<Block> out, StringBuilder text) {
        String s = trimEnd(text.toString());
        text.setLength(0);
        if (s.trim().isEmpty()) return;
        Block b = new Block();
        b.text = s;
        out.add(b);
    }

    static String trimEnd(String s) {
        int e = s.length();
        while (e > 0 && (s.charAt(e - 1) == '\n' || s.charAt(e - 1) == '\r')) e--;
        return s.substring(0, e);
    }

    /** Looks for "File: src/Main.java" just before a code block (or on its first line). */
    static String pathHint(ArrayList<Block> before, Block code) {
        if (!before.isEmpty()) {
            Block p = before.get(before.size() - 1);
            if (!p.code) {
                String[] ls = p.text.split("\n");
                for (int i = ls.length - 1; i >= 0 && i >= ls.length - 2; i--) {
                    String l = ls[i].trim();
                    if (l.isEmpty()) continue;
                    Matcher m = PATH_HINT.matcher(l);
                    if (m.find()) return m.group(1);
                    Matcher o = PATH_ONLY.matcher(l);
                    if (o.matches()) return o.group(1);
                    break;
                }
            }
        }
        String first = code.text.split("\n", 2)[0].trim();
        Matcher m = Pattern.compile("(?i)^(?://|#|<!--)\\s*(?:file|path)\\s*:\\s*([\\w./\\-]+\\.\\w{1,6})").matcher(first);
        if (m.find()) return m.group(1);
        return null;
    }

    /** Renders one text block (no code fences) into a styled string. */
    public static CharSequence render(Context c, String text, AppThemes T) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        String[] lines = text.split("\n", -1);
        boolean lastBlank = true;
        for (String l : lines) {
            if (l.trim().isEmpty()) {
                if (!lastBlank) out.append('\n');
                lastBlank = true;
                continue;
            }
            lastBlank = false;
            int start = out.length();
            Matcher m;
            if (HR.matcher(l).matches()) {
                out.append("────────────────");
                out.setSpan(new ForegroundColorSpan(T.stroke), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if ((m = HEAD.matcher(l)).matches()) {
                int lv = m.group(1).length();
                out.append(inline(m.group(2), T));
                out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new RelativeSizeSpan(lv == 1 ? 1.28f : lv == 2 ? 1.18f : lv == 3 ? 1.1f : 1.03f), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if ((m = QUOTE.matcher(l)).matches()) {
                out.append(inline(m.group(1), T));
                out.setSpan(new QuoteSpan(T.accent), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new ForegroundColorSpan(T.sub), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if ((m = BULLET.matcher(l)).matches()) {
                out.append(spaces(m.group(1).length() / 2 * 2 + 1)).append("•  ").append(inline(m.group(2), T));
            } else if ((m = NUM.matcher(l)).matches()) {
                out.append(spaces(m.group(1).length() / 2 * 2 + 1)).append(m.group(2)).append(".  ").append(inline(m.group(3), T));
            } else if (l.trim().startsWith("|")) {
                if (TABLE_SEP.matcher(l).matches()) { lastBlank = true; continue; }
                String row = l.trim();
                if (row.startsWith("|")) row = row.substring(1);
                if (row.endsWith("|")) row = row.substring(0, row.length() - 1);
                out.append(row.replace("|", "  │  ").trim());
                out.setSpan(new TypefaceSpan("monospace"), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new RelativeSizeSpan(0.92f), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                out.append(inline(l, T));
            }
            out.append('\n');
        }
        int e = out.length();
        while (e > 0 && out.charAt(e - 1) == '\n') e--;
        if (e < out.length()) out.delete(e, out.length());
        return out;
    }

    static String spaces(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(' ');
        return sb.toString();
    }

    static SpannableStringBuilder inline(String s, final AppThemes T) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        Matcher m = INLINE.matcher(s);
        int last = 0;
        while (m.find()) {
            out.append(s, last, m.start());
            int st = out.length();
            if (m.group(1) != null) {
                out.append(inline(m.group(2), T));
                out.setSpan(new StyleSpan(Typeface.BOLD), st, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.group(3) != null) {
                out.append(m.group(4));
                out.setSpan(new TypefaceSpan("monospace"), st, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new BackgroundColorSpan(UI.alpha(T.accent, 36)), st, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                out.setSpan(new RelativeSizeSpan(0.92f), st, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.group(5) != null) {
                out.append(m.group(6));
                final String url = m.group(7);
                out.setSpan(new ClickableSpan() {
                    @Override public void onClick(View v) {
                        try {
                            v.getContext().startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)));
                        } catch (Exception ignored) { }
                    }
                    @Override public void updateDrawState(TextPaint ds) { ds.setColor(T.accent); ds.setUnderlineText(true); }
                }, st, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.group(8) != null) {
                out.append(m.group(9));
                out.setSpan(new StrikethroughSpan(), st, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (m.group(10) != null) {
                out.append(m.group(11));
                out.setSpan(new StyleSpan(Typeface.ITALIC), st, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            last = m.end();
        }
        out.append(s, last, s.length());
        return out;
    }
}
