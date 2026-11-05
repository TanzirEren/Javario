package com.tanzir.javario;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Code suggestions: keywords, types, snippets, members and words from the file. */
public class Suggest {
    public static class Item {
        public final String label, insert;
        public final int kind; // 0 snippet, 1 keyword, 2 type, 3 member, 4 word
        Item(String l, String i, int k) { label = l; insert = i; kind = k; }
    }

    static final String[] KEYWORDS = {
        "abstract", "boolean", "break", "byte", "case", "catch", "char", "class", "continue", "default", "do", "double",
        "else", "enum", "extends", "final", "finally", "float", "for", "if", "implements", "import", "instanceof", "int",
        "interface", "long", "new", "null", "package", "private", "protected", "public", "return", "short", "static",
        "super", "switch", "this", "throw", "throws", "try", "void", "while", "true", "false", "var"};

    static final String[] TYPES = {
        "String", "Integer", "Double", "Long", "Boolean", "Character", "Object", "Math", "System", "Scanner", "Random",
        "List", "ArrayList", "LinkedList", "Map", "HashMap", "TreeMap", "LinkedHashMap", "Set", "HashSet", "TreeSet",
        "Arrays", "Collections", "Optional", "StringBuilder", "Thread", "Runnable", "Exception", "RuntimeException",
        "IOException", "File", "Iterator", "Comparable", "Comparator", "Stream", "Collectors", "LocalDate", "Objects",
        "Queue", "Deque", "ArrayDeque", "PriorityQueue", "Stack", "Override", "FunctionalInterface"};

    static final String[][] SNIPS = {
        {"sout", "System.out.println($0);"},
        {"soutf", "System.out.printf(\"$0%n\");"},
        {"psvm", "public static void main(String[] args) {\n\t$0\n}"},
        {"main", "public static void main(String[] args) {\n\t$0\n}"},
        {"fori", "for (int i = 0; i < $0; i++) {\n\t\n}"},
        {"foreach", "for (Object item : $0) {\n\t\n}"},
        {"while", "while ($0) {\n\t\n}"},
        {"if", "if ($0) {\n\t\n}"},
        {"ifelse", "if ($0) {\n\t\n} else {\n\t\n}"},
        {"switch", "switch ($0) {\n\tcase 1:\n\t\tbreak;\n\tdefault:\n\t\tbreak;\n}"},
        {"try", "try {\n\t$0\n} catch (Exception e) {\n\te.printStackTrace();\n}"},
        {"class", "public class $0 {\n\t\n}"},
        {"iface", "public interface $0 {\n\t\n}"},
        {"enum", "public enum $0 {\n\tA, B, C\n}"},
        {"meth", "public static void $0() {\n\t\n}"},
        {"scan", "Scanner sc = new Scanner(System.in);$0"},
        {"list", "List<String> list = new ArrayList<>();$0"},
        {"map", "Map<String, Integer> map = new HashMap<>();$0"},
        {"sb", "StringBuilder sb = new StringBuilder();$0"},
        {"rand", "Random rnd = new Random();$0"},
        {"thread", "new Thread(() -> {\n\t$0\n}).start();"},
        {"sleep", "try {\n\tThread.sleep($0);\n} catch (InterruptedException e) {\n\tThread.currentThread().interrupt();\n}"},
        {"lambda", "($0) -> "},
        {"ctor", "public $0() {\n\t\n}"},
        {"tostring", "@Override\npublic String toString() {\n\treturn $0;\n}"},
    };

    static final Map<String, String[]> MEMBERS = new HashMap<>();
    static {
        MEMBERS.put("System.out", new String[]{"println(", "print(", "printf(", "flush("});
        MEMBERS.put("System", new String[]{"out", "in", "err", "currentTimeMillis(", "nanoTime(", "exit(", "getProperty(", "lineSeparator("});
        MEMBERS.put("Math", new String[]{"max(", "min(", "abs(", "sqrt(", "pow(", "random(", "floor(", "ceil(", "round(", "PI", "E", "sin(", "cos(", "log(", "floorMod("});
        MEMBERS.put("String", new String[]{"valueOf(", "format(", "join("});
        MEMBERS.put("Integer", new String[]{"parseInt(", "valueOf(", "toString(", "MAX_VALUE", "MIN_VALUE", "compare(", "toBinaryString(", "toHexString(", "sum("});
        MEMBERS.put("Double", new String[]{"parseDouble(", "valueOf(", "toString(", "compare(", "MAX_VALUE", "isNaN("});
        MEMBERS.put("Long", new String[]{"parseLong(", "valueOf(", "MAX_VALUE", "MIN_VALUE"});
        MEMBERS.put("Character", new String[]{"isDigit(", "isLetter(", "isUpperCase(", "isLowerCase(", "toUpperCase(", "toLowerCase(", "isWhitespace(", "getNumericValue("});
        MEMBERS.put("Arrays", new String[]{"toString(", "sort(", "asList(", "fill(", "copyOf(", "copyOfRange(", "stream(", "equals(", "deepToString(", "binarySearch("});
        MEMBERS.put("Collections", new String[]{"sort(", "reverse(", "shuffle(", "max(", "min(", "unmodifiableList(", "emptyList(", "swap(", "frequency("});
        MEMBERS.put("List", new String[]{"of(", "copyOf("});
        MEMBERS.put("Thread", new String[]{"sleep(", "currentThread("});
        MEMBERS.put("Objects", new String[]{"equals(", "hash(", "requireNonNull(", "isNull(", "nonNull(", "toString("});
        MEMBERS.put("#String", new String[]{"length(", "charAt(", "substring(", "indexOf(", "lastIndexOf(", "equals(", "equalsIgnoreCase(", "contains(", "split(", "trim(", "toUpperCase(", "toLowerCase(", "replace(", "startsWith(", "endsWith(", "isEmpty(", "toCharArray(", "compareTo(", "matches(", "strip(", "repeat(", "isBlank("});
        MEMBERS.put("#List", new String[]{"add(", "get(", "size(", "remove(", "set(", "contains(", "isEmpty(", "clear(", "indexOf(", "forEach(", "stream(", "sort(", "addAll(", "subList(", "iterator("});
        MEMBERS.put("#Map", new String[]{"put(", "get(", "getOrDefault(", "containsKey(", "containsValue(", "remove(", "keySet(", "values(", "entrySet(", "size(", "isEmpty(", "putIfAbsent(", "forEach(", "clear(", "merge("});
        MEMBERS.put("#Set", new String[]{"add(", "contains(", "remove(", "size(", "isEmpty(", "clear(", "addAll(", "stream(", "iterator("});
        MEMBERS.put("#StringBuilder", new String[]{"append(", "insert(", "reverse(", "toString(", "length(", "deleteCharAt(", "setLength(", "charAt(", "delete(", "replace(", "indexOf("});
        MEMBERS.put("#Scanner", new String[]{"nextInt(", "nextLine(", "next(", "nextDouble(", "nextLong(", "hasNext(", "hasNextInt(", "hasNextLine(", "close("});
        MEMBERS.put("#Random", new String[]{"nextInt(", "nextDouble(", "nextBoolean(", "nextLong("});
        MEMBERS.put("#Optional", new String[]{"isPresent(", "get(", "orElse(", "ifPresent(", "map(", "isEmpty("});
        MEMBERS.put("#Object", new String[]{"toString(", "equals(", "hashCode(", "getClass("});
    }

    static boolean isId(char c) { return Character.isLetterOrDigit(c) || c == '_' || c == '$'; }

    static String alias(String type) {
        switch (type) {
            case "ArrayList": case "LinkedList": case "List": case "Collection": case "Queue": case "Deque": case "ArrayDeque": case "Stack": case "PriorityQueue": return "#List";
            case "HashMap": case "TreeMap": case "LinkedHashMap": case "Map": return "#Map";
            case "HashSet": case "TreeSet": case "Set": case "LinkedHashSet": return "#Set";
            case "String": return "#String";
            case "StringBuilder": case "StringBuffer": return "#StringBuilder";
            case "Scanner": return "#Scanner";
            case "Random": return "#Random";
            case "Optional": return "#Optional";
            default: return "#Object";
        }
    }

    public static ArrayList<Item> get(String text, int caret, boolean java) {
        ArrayList<Item> out = new ArrayList<>();
        if (caret < 0 || caret > text.length()) return out;
        int ws = caret;
        while (ws > 0 && isId(text.charAt(ws - 1))) ws--;
        String word = text.substring(ws, caret);
        String lw = word.toLowerCase(Locale.ROOT);

        if (ws > 0 && text.charAt(ws - 1) == '.' && java) {
            int e = ws - 1;
            int s = e;
            if (s > 0 && text.charAt(s - 1) == '"') {
                add(out, MEMBERS.get("#String"), lw);
                return out;
            }
            while (s > 0 && (isId(text.charAt(s - 1)) || text.charAt(s - 1) == '.')) s--;
            String token = text.substring(s, e);
            String[] ms = MEMBERS.get(token);
            if (ms == null && !token.isEmpty()) {
                Matcher m = Pattern.compile("\\b([A-Z]\\w*)(?:<[^;(){}=]*>)?(?:\\[\\])*\\s+" + Pattern.quote(token) + "\\b").matcher(text);
                if (m.find()) ms = MEMBERS.get(alias(m.group(1)));
            }
            if (ms == null && token.equals("this")) {
                addWords(out, text, lw, word);
                return out;
            }
            if (ms == null) ms = MEMBERS.get("#Object");
            add(out, ms, lw);
            return out;
        }

        if (word.length() < 1) return out;
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        if (java) {
            for (String[] s : SNIPS) if (s[0].startsWith(lw) && !s[0].equals(word)) {
                out.add(new Item("\u26A1 " + s[0], s[1], 0));
                seen.add(s[0]);
            }
            for (String k : KEYWORDS) if (k.startsWith(lw) && !k.equals(word) && seen.add(k)) out.add(new Item(k, k, 1));
            for (String k : TYPES) if (k.toLowerCase(Locale.ROOT).startsWith(lw) && !k.equals(word) && seen.add(k)) out.add(new Item(k, k, 2));
        }
        addWords(out, text, lw, word);
        if (out.size() > 14) return new ArrayList<>(out.subList(0, 14));
        return out;
    }

    static void add(ArrayList<Item> out, String[] ms, String lw) {
        for (String m : ms) {
            if (!m.toLowerCase(Locale.ROOT).startsWith(lw)) continue;
            if (m.endsWith("(")) out.add(new Item(m.substring(0, m.length() - 1) + "()", m + "$0)", 3));
            else out.add(new Item(m, m, 3));
        }
    }

    static void addWords(ArrayList<Item> out, String text, String lw, String word) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Item i : out) seen.add(i.insert);
        Matcher m = Pattern.compile("[A-Za-z_]\\w{2,}").matcher(text);
        int guard = 0;
        while (m.find() && guard++ < 4000) {
            String w = m.group();
            if (w.equals(word) || !w.toLowerCase(Locale.ROOT).startsWith(lw)) continue;
            if (seen.add(w)) out.add(new Item(w, w, 4));
            if (out.size() > 20) return;
        }
    }
}
