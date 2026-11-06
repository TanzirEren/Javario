package com.tanzir.javario;

import android.content.Context;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Java learning assistant: offline knowledge base + optional Claude API. */
public class AssistantEngine {
    static final Random RND = new Random();
    int pendingQuiz = -1;

    // keywords (comma), answer
    static final String[][] KB = {
        {"variable,variables,int,double,declare", "A variable is a named box for a value.\n\nint age = 20;\ndouble price = 9.5;\nString name = \"Ana\";\n\nThe type (int, double, String...) says what kind of value it holds."},
        {"loop,for,while,iterate,repeat", "for loop (known count):\nfor (int i = 0; i < 5; i++) { ... }\n\nwhile loop (until condition false):\nwhile (x > 0) { x--; }\n\nfor-each (all items):\nfor (String s : list) { ... }"},
        {"array,arrays", "An array has a fixed size:\nint[] a = new int[3];\na[0] = 5;\nint[] b = {1, 2, 3};\nlength: a.length\n\nIndexes go from 0 to length-1. Going outside gives ArrayIndexOutOfBoundsException."},
        {"arraylist,list,collection", "ArrayList is a resizable list:\nList<String> l = new ArrayList<>();\nl.add(\"a\"); l.get(0); l.size(); l.remove(0);\n\nImport: import java.util.*;"},
        {"hashmap,map,dictionary,key", "HashMap stores key/value pairs:\nMap<String,Integer> m = new HashMap<>();\nm.put(\"a\", 1);\nm.get(\"a\");\nm.containsKey(\"a\");\nfor (String k : m.keySet()) { ... }"},
        {"class,object,oop,instance,constructor", "A class is a blueprint, an object is one thing made from it.\n\nclass Dog {\n  String name;\n  Dog(String name) { this.name = name; }\n  void bark() { System.out.println(name + \" woof\"); }\n}\nDog d = new Dog(\"Rex\");\nd.bark();"},
        {"inherit,extends,super,parent", "extends makes a child class reuse a parent class:\nclass Cat extends Animal { }\nUse super(...) to call the parent constructor and @Override to replace a method."},
        {"interface,implements,abstract", "An interface lists methods a class must have:\ninterface Shape { double area(); }\nclass Circle implements Shape { public double area() { return 3.14; } }\nAn abstract class can hold both finished and unfinished methods."},
        {"polymorphism,override,overload", "Overload: same method name, different parameters.\nOverride: a child class replaces a parent method (use @Override).\nPolymorphism: Animal a = new Cat(); a.sound(); calls Cat's version."},
        {"exception,try,catch,throw,error handling", "try {\n  risky();\n} catch (Exception e) {\n  System.out.println(e.getMessage());\n} finally {\n  // always runs\n}\n\nUse throw new IllegalArgumentException(\"msg\"); to raise your own error."},
        {"string,strings,substring,charat", "Useful String methods: length(), charAt(i), substring(a,b), indexOf(x), equals(o), toUpperCase(), split(\",\"), trim(), contains(x), replace(a,b).\nCompare with equals(), never ==."},
        {"scanner,input,read,stdin", "Scanner sc = new Scanner(System.in);\nint n = sc.nextInt();\nString line = sc.nextLine();\n\nIn Javario's console type into the input box at the bottom and press send."},
        {"static,final", "static: belongs to the class, not to an object (Math.max, main).\nfinal: cannot change after it is set (final int X = 5;)."},
        {"recursion,recursive", "A method that calls itself. It needs a base case:\nint fact(int n) {\n  if (n <= 1) return 1;\n  return n * fact(n - 1);\n}\nNo base case = StackOverflowError."},
        {"lambda,stream,streams,functional", "Lambda: (a, b) -> a + b\nStreams:\nlist.stream().filter(x -> x > 2).map(x -> x * 2).collect(Collectors.toList());\nImport java.util.stream.*"},
        {"generic,generics", "Generics let a class work with many types safely: List<String>, Map<String,Integer>.\nclass Box<T> { T value; }"},
        {"thread,threads,concurrent,runnable", "new Thread(() -> System.out.println(\"hi\")).start();\nUse synchronized or java.util.concurrent for shared data. Thread.sleep(ms) pauses."},
        {"sort,sorting", "Arrays.sort(arr); Collections.sort(list);\nCustom: list.sort((a, b) -> a.length() - b.length());\nFor learning, try writing bubble sort yourself with two nested loops."},
        {"file,files,io,read file,write file", "Files.readAllLines(Paths.get(\"a.txt\")) (java.nio.file)\ntry (BufferedReader r = new BufferedReader(new FileReader(\"a.txt\"))) { String line = r.readLine(); }\nTip: your project folder is on /storage/emulated/0/Javario/."},
        {"switch,case", "switch (day) {\n  case 1: System.out.println(\"Mon\"); break;\n  default: System.out.println(\"Other\");\n}"},
        {"enum", "enum Color { RED, GREEN, BLUE }\nColor c = Color.RED;\nfor (Color x : Color.values()) { ... }"},
        {"math,random", "Math.max(a,b), Math.sqrt(x), Math.pow(a,b), Math.abs(x), Math.round(x).\nRandom r = new Random(); r.nextInt(10) gives 0..9."},
        {"access,public,private,protected,encapsulation", "public: everyone. private: only this class. protected: subclasses and package.\nEncapsulation = private fields + public getters/setters."},
        {"library,libraries,jar,dependency,maven", "Open Tools > Libraries to browse 170+ libraries. Or just write an import (for example com.google.gson.Gson) and press Run: Javario downloads the jar into your project's libs folder automatically."},
        {"run,compile,build", "Press the play icon in the editor. Javario compiles with ECJ, converts to DEX with D8 and runs the program in the console. If it fails you get a dialog where you can copy all errors."},
    };

    // pattern (lowercase), explanation
    static final String[][] ERRORS = {
        {"cannot find symbol", "Java does not know that name. Common causes: typo, variable declared in another scope, missing import (e.g. import java.util.*;), wrong capital letters (String not string)."},
        {"';' expected", "A statement is missing its semicolon. Look at the end of the previous line."},
        {"incompatible types", "You are putting one type into another. Example: int x = 3.5; (double -> int). Fix with a cast (int) 3.5 or use the right type."},
        {"missing return statement", "A method with a return type must return on every path. Add a return at the end or in the else branch."},
        {"might not have been initialized", "You use a local variable before giving it a value. Set it when you declare it: int x = 0;"},
        {"should be declared in a file named", "A public class must be in a file with the same name. class Main -> Main.java."},
        {"class, interface, or enum expected", "Usually an extra or missing curly brace } somewhere above."},
        {"reached end of file while parsing", "A closing brace } is missing. Count your { and }."},
        {"non-static", "You call an instance method/field from static main. Create an object first: new Main().method(); or make it static."},
        {"already defined", "Two variables or methods with the same name in the same scope. Rename one."},
        {"cannot be applied to", "Wrong number or type of arguments when calling a method or constructor."},
        {"unreported exception", "The code can throw a checked exception. Wrap in try/catch or add 'throws Exception' to the method."},
        {"unclosed string", "A string started with \" but never ended on that line."},
        {"nullpointerexception", "You used an object that is null. Initialize it (new ...), or check if (x != null) before using it."},
        {"arrayindexoutofbounds", "Index is outside 0..length-1. Check your loop condition (use i < arr.length, not <=)."},
        {"stringindexoutofbounds", "charAt/substring used an index outside the string length."},
        {"numberformatexception", "The text is not a valid number (Integer.parseInt(\"abc\")). Validate input first or catch the exception."},
        {"classcastexception", "You cast an object to a type it is not. Check with instanceof first."},
        {"stackoverflowerror", "Infinite recursion. Your recursive method needs a base case that stops it."},
        {"arithmeticexception", "Division by zero with ints. Check the divisor first."},
        {"inputmismatchexception", "Scanner expected a number but got other text. Type a valid number in the console."},
        {"nosuchelementexception", "Scanner has no more input. Type a value in the console input box before the program asks for it."},
    };

    public String offline(String q, String code, String errors) {
        String lq = q.toLowerCase(Locale.ROOT).trim();
        if (pendingQuiz >= 0) {
            String[] qz = LearnData.QUIZ[pendingQuiz];
            pendingQuiz = -1;
            if (lq.contains(qz[1])) return "Correct! \u2705 Say \"quiz me\" for another one.";
            return "Not quite. The answer is: " + qz[1] + ". Say \"quiz me\" to try another.";
        }
        if (lq.contains("quiz")) {
            pendingQuiz = RND.nextInt(LearnData.QUIZ.length);
            return "Quiz time!\n" + LearnData.QUIZ[pendingQuiz][0];
        }
        if (lq.contains("challenge") || lq.contains("practice") || lq.contains("exercise")) {
            String[] c = LearnData.CHALLENGES[RND.nextInt(LearnData.CHALLENGES.length)];
            return "Challenge: " + c[0] + "\n" + c[1] + "\n\nHint: " + c[3] + "\n\nOpen Settings > Tools > Learn & Practice to start it as a project.";
        }
        if (lq.contains("tip")) return "Tip: " + LearnData.TIPS[RND.nextInt(LearnData.TIPS.length)];
        if (lq.contains("explain my code") || lq.contains("explain code") || lq.contains("analyze")) return explain(code);
        if (lq.contains("explain error") || lq.contains("my error") || lq.contains("fix error") || lq.contains("why") && lq.contains("fail")) {
            if (errors != null && !errors.trim().isEmpty()) return explainError(errors);
            return "No recent build errors. Press Run first, then ask me again.";
        }
        String er = matchError(lq);
        if (er != null) return er;
        String best = null;
        int bestScore = 0;
        for (String[] k : KB) {
            int score = 0;
            for (String w : k[0].split(",")) if (lq.contains(w)) score += w.length() > 3 ? 2 : 1;
            if (score > bestScore) { bestScore = score; best = k[1]; }
        }
        if (best != null) return best;
        return "I can help with Java topics (loops, arrays, classes, collections, exceptions, streams, ...), explain compiler errors, explain your code, give challenges, quizzes and tips.\n\nTry: \"how do loops work\", \"explain my code\", \"explain error\", \"quiz me\".\n\nFor open questions, add an AI provider in Settings > Assistant and pick a model in the chat box.";
    }

    static String matchError(String lq) {
        StringBuilder sb = new StringBuilder();
        for (String[] e : ERRORS) if (lq.contains(e[0])) sb.append("\u2022 ").append(e[0]).append(": ").append(e[1]).append("\n\n");
        return sb.length() == 0 ? null : sb.toString().trim();
    }

    public static String explainError(String errors) {
        String lq = errors.toLowerCase(Locale.ROOT);
        String m = matchError(lq);
        Matcher at = Pattern.compile("\\(at line (\\d+)\\)").matcher(errors);
        String where = at.find() ? "First problem is on line " + at.group(1) + ".\n\n" : "";
        if (m == null) return where + "I do not know this exact error. Read the first message carefully - fix errors from top to bottom, later ones are often caused by the first.";
        return where + m;
    }

    public static String explain(String code) {
        if (code == null || code.trim().isEmpty()) return "Open a Java file in the editor first, then ask me to explain it.";
        ArrayList<String> classes = new ArrayList<>(), methods = new ArrayList<>();
        Matcher c = Pattern.compile("\\b(?:class|interface|enum)\\s+(\\w+)").matcher(code);
        while (c.find()) classes.add(c.group(1));
        Matcher m = Pattern.compile("(?:public|private|protected|static|\\s)+[\\w<>\\[\\],]+\\s+(\\w+)\\s*\\([^)]*\\)\\s*(?:throws [\\w, ]+)?\\{").matcher(code);
        while (m.find()) { String n = m.group(1); if (!n.equals("if") && !n.equals("for") && !n.equals("while") && !n.equals("switch") && !n.equals("catch")) methods.add(n); }
        int loops = count(code, "\\b(for|while)\\s*\\("), ifs = count(code, "\\bif\\s*\\("), tries = count(code, "\\btry\\b");
        int lines = code.split("\n").length;
        StringBuilder sb = new StringBuilder("This file has " + lines + " lines.\n");
        if (!classes.isEmpty()) sb.append("Classes: ").append(join(classes)).append('\n');
        if (!methods.isEmpty()) sb.append("Methods: ").append(join(methods)).append('\n');
        sb.append("Loops: ").append(loops).append(", if statements: ").append(ifs).append(", try blocks: ").append(tries).append('\n');
        if (code.contains("Scanner")) sb.append("It reads user input with Scanner (type in the console input box).\n");
        if (code.contains("System.out")) sb.append("It prints text to the console.\n");
        if (code.contains("import ")) sb.append("It imports library classes (Javario downloads jars automatically).\n");
        if (code.contains("static void main")) sb.append("Execution starts at main().\n");
        return sb.toString().trim();
    }

    static int count(String s, String re) {
        Matcher m = Pattern.compile(re).matcher(s);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    static String join(ArrayList<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) { if (i > 0) sb.append(", "); sb.append(l.get(i)); }
        return sb.toString();
    }
}
