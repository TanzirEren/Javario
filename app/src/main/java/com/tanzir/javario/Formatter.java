package com.tanzir.javario;

/** Simple code re-indenter for Java and XML. */
public class Formatter {
    public static String format(String src, String ext, String unit) {
        if (ext.equals("xml")) return xml(src, unit);
        return java(src, unit);
    }

    static String java(String src, String unit) {
        String[] lines = src.split("\n", -1);
        StringBuilder out = new StringBuilder();
        int depth = 0;
        boolean inBlock = false;
        for (int li = 0; li < lines.length; li++) {
            String raw = lines[li];
            String t = raw.trim();
            if (t.isEmpty()) { if (li < lines.length - 1) out.append('\n'); continue; }
            // strip strings / comments to count braces
            StringBuilder code = new StringBuilder();
            boolean inStr = false, inChr = false;
            for (int i = 0; i < t.length(); i++) {
                char c = t.charAt(i);
                if (inBlock) { if (c == '*' && i + 1 < t.length() && t.charAt(i + 1) == '/') { inBlock = false; i++; } continue; }
                if (inStr) { if (c == '\\') i++; else if (c == '"') inStr = false; continue; }
                if (inChr) { if (c == '\\') i++; else if (c == '\'') inChr = false; continue; }
                if (c == '/' && i + 1 < t.length() && t.charAt(i + 1) == '/') break;
                if (c == '/' && i + 1 < t.length() && t.charAt(i + 1) == '*') { inBlock = true; i++; continue; }
                if (c == '"') { inStr = true; continue; }
                if (c == '\'') { inChr = true; continue; }
                code.append(c);
            }
            String cs = code.toString();
            int lead = 0;
            while (lead < cs.length() && (cs.charAt(lead) == '}' || cs.charAt(lead) == ')' || cs.charAt(lead) == ']' || cs.charAt(lead) == ' ')) {
                if (cs.charAt(lead) != ' ') lead++; else break;
            }
            int d = Math.max(0, depth - lead);
            if (t.startsWith("case ") || t.startsWith("default:") || t.startsWith("default ")) d = Math.max(0, depth - 0);
            if (t.startsWith("*") && !t.startsWith("*/")) { /* javadoc continuation */ }
            for (int k = 0; k < d; k++) out.append(unit);
            if (t.startsWith("*")) out.append(' ');
            out.append(t);
            if (li < lines.length - 1) out.append('\n');
            int opens = 0, closes = 0;
            for (int i = 0; i < cs.length(); i++) {
                char c = cs.charAt(i);
                if (c == '{' || c == '(' || c == '[') opens++;
                else if (c == '}' || c == ')' || c == ']') closes++;
            }
            depth = Math.max(0, depth + opens - closes);
        }
        return out.toString();
    }

    static String xml(String src, String unit) {
        String[] lines = src.split("\n", -1);
        StringBuilder out = new StringBuilder();
        int depth = 0;
        for (int li = 0; li < lines.length; li++) {
            String t = lines[li].trim();
            if (t.isEmpty()) { if (li < lines.length - 1) out.append('\n'); continue; }
            boolean closing = t.startsWith("</");
            if (closing) depth = Math.max(0, depth - 1);
            for (int k = 0; k < depth; k++) out.append(unit);
            out.append(t);
            if (li < lines.length - 1) out.append('\n');
            if (!closing && t.startsWith("<") && !t.startsWith("<?") && !t.startsWith("<!")) {
                boolean selfClose = t.endsWith("/>");
                boolean sameLineClose = t.contains("</");
                boolean unfinished = !t.contains(">");
                if (!selfClose && !sameLineClose && !unfinished) depth++;
                else if (unfinished) depth++;
            } else if (!closing && t.endsWith("/>") && depth > 0) {
                depth = Math.max(0, depth - 1);
            } else if (!closing && t.endsWith(">") && depth > 0 && !t.startsWith("<")) {
                depth = Math.max(0, depth - 1);
            }
        }
        return out.toString();
    }
}
