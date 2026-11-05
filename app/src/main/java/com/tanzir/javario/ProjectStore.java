package com.tanzir.javario;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ProjectStore {
    /** /storage/emulated/0/Javario */
    public static File root() {
        File r = new File(Environment.getExternalStorageDirectory(), "Javario");
        return r;
    }

    public static File exports() { return new File(root(), "Exports"); }

    public static boolean hasAccess(Context c) {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return c.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public static List<File> list() {
        ArrayList<File> out = new ArrayList<>();
        File[] ch = root().listFiles();
        if (ch != null) for (File f : ch) if (f.isDirectory() && !f.getName().startsWith(".") && !f.getName().equals("Exports")) out.add(f);
        Collections.sort(out, new Comparator<File>() {
            @Override public int compare(File a, File b) { return Long.compare(b.lastModified(), a.lastModified()); }
        });
        return out;
    }

    public static String sanitize(String n) {
        n = n.trim().replaceAll("[^A-Za-z0-9_\\- ]", "").replace(' ', '_');
        return n;
    }

    public static File create(String rawName) throws IOException {
        String name = sanitize(rawName);
        if (name.isEmpty()) throw new IOException("Use letters, numbers, _ or - in the project name");
        File dir = new File(root(), name);
        if (dir.exists()) throw new IOException("A project named \"" + name + "\" already exists");
        if (!dir.mkdirs()) throw new IOException("Cannot create folder. Allow storage access first.");
        writeTemplate(dir, name);
        return dir;
    }

    /** Default project that runs right after creation. */
    public static void writeTemplate(File dir, String name) throws IOException {
        FileUtil.write(new File(dir, "src/Main.java"),
            "public class Main {\n" +
            "    public static void main(String[] args) {\n" +
            "        System.out.println(\"Hello from Javario!\");\n" +
            "\n" +
            "        Greeter greeter = new Greeter(\"" + name + "\");\n" +
            "        System.out.println(greeter.greet());\n" +
            "\n" +
            "        int sum = 0;\n" +
            "        for (int i = 1; i <= 10; i++) {\n" +
            "            sum += i;\n" +
            "        }\n" +
            "        System.out.println(\"Sum of 1..10 = \" + sum);\n" +
            "    }\n" +
            "}\n");
        FileUtil.write(new File(dir, "src/Greeter.java"),
            "public class Greeter {\n" +
            "    private final String name;\n" +
            "\n" +
            "    public Greeter(String name) {\n" +
            "        this.name = name;\n" +
            "    }\n" +
            "\n" +
            "    public String greet() {\n" +
            "        return \"Welcome to \" + name + \" - happy coding!\";\n" +
            "    }\n" +
            "}\n");
        FileUtil.write(new File(dir, "config/app.xml"),
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<project name=\"" + name + "\">\n" +
            "    <main>Main</main>\n" +
            "    <language>Java</language>\n" +
            "</project>\n");
        FileUtil.write(new File(dir, "README.md"),
            "# " + name + "\n\nCreated with Javario.\n\n" +
            "- Put your code in `src/`\n- Jar files in `libs/` are used automatically\n- Press the run button to compile and run\n");
        new File(dir, "libs").mkdirs();
    }

    public static int countFiles(File dir) {
        ArrayList<File> a = new ArrayList<>();
        FileUtil.walk(dir, a);
        return a.size();
    }
}
