package com.tanzir.javario;

import android.content.Context;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;
import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jdt.internal.compiler.batch.Main;

/** Compiles the project on the phone: ECJ (.java -> .class) then D8 (.class -> .dex). */
public class JavaRunner {
    public static class Result {
        public boolean ok;
        public String errors = "";
        public String dex;
        public String main;
    }

    public interface Log { void step(String s); }

    static File ensureAndroidJar(Context ctx) throws IOException {
        File dst = new File(ctx.getFilesDir(), "android.jar");
        if (dst.exists() && dst.length() > 1000000) return dst;
        InputStream in;
        try { in = ctx.getAssets().open("android.jar"); }
        catch (IOException e) { throw new IOException("android.jar is missing from the app assets. Build the app with the provided GitHub workflow."); }
        FileOutputStream out = new FileOutputStream(dst);
        try { FileUtil.copy(in, out); } finally { in.close(); out.close(); }
        return dst;
    }

    static void collect(File dir, ArrayList<File> out) {
        File[] ch = dir.listFiles();
        if (ch == null) return;
        for (File f : ch) {
            String n = f.getName();
            if (f.isDirectory()) { if (!n.startsWith(".") && !n.equals("build") && !n.equals("libs")) collect(f, out); }
            else if (n.endsWith(".java")) out.add(f);
        }
    }

    static String findMain(Context ctx, ArrayList<File> srcs) {
        String pref = Prefs.s(ctx, "main_class").trim();
        Pattern mainP = Pattern.compile("static\\s+void\\s+main\\s*\\(");
        Pattern pkgP = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
        String best = null;
        for (File f : srcs) {
            try {
                String t = FileUtil.read(f);
                if (!mainP.matcher(t).find()) continue;
                Matcher pm = pkgP.matcher(t);
                String cls = (pm.find() ? pm.group(1) + "." : "") + FileUtil.baseName(f.getName());
                if (!pref.isEmpty() && (cls.equals(pref) || f.getName().equals(pref + ".java"))) return cls;
                if (best == null || f.getName().equals("Main.java")) best = cls;
            } catch (IOException ignored) { }
        }
        return best;
    }

    public static Result run(final Context ctx, File project, final Log log) {
        CrashLog.step(ctx, "RUN: start");
        Log lg = new Log() {
            @Override public void step(String s) { CrashLog.step(ctx, "RUN: " + s); log.step(s); }
        };
        Result res = runInner(ctx, project, lg);
        CrashLog.step(ctx, res.ok ? "RUN: compiled ok" : "DONE");
        return res;
    }

    static Result runInner(Context ctx, File project, Log log) {
        Result r = new Result();
        try {
            log.step("Preparing toolchain…");
            File androidJar = ensureAndroidJar(ctx);
            ArrayList<File> srcs = new ArrayList<>();
            collect(project, srcs);
            if (srcs.isEmpty()) { r.errors = "No .java files found in this project."; return r; }
            StringBuilder all = new StringBuilder();
            for (File f : srcs) all.append(FileUtil.read(f)).append('\n');
            r.main = findMain(ctx, srcs);
            if (r.main == null) {
                r.errors = "No main method found.\n\nAdd this to one of your classes:\n\npublic static void main(String[] args) {\n    // your code\n}";
                return r;
            }
            File libs = new File(project, "libs");
            libs.mkdirs();
            if (Prefs.b(ctx, "auto_libs")) {
                log.step("Checking libraries…");
                try { LibraryManager.autoDownload(ctx, project, all.toString(), log); }
                catch (IOException e) { r.errors = "Library download failed:\n" + e.getMessage() + "\n\nCheck your internet connection or add the jar to the libs folder manually."; return r; }
            }
            ArrayList<File> jars = new ArrayList<>();
            File[] lj = libs.listFiles();
            if (lj != null) for (File j : lj) if (j.getName().endsWith(".jar")) jars.add(j);

            log.step("Compiling " + srcs.size() + " file(s)…");
            File outRoot = new File(ctx.getCacheDir(), "build/" + project.getName());
            FileUtil.deleteRec(outRoot);
            File classes = new File(outRoot, "classes");
            classes.mkdirs();
            String level = Prefs.i(ctx, "java_level") == 1 ? "11" : "1.8";
            ArrayList<String> args = new ArrayList<>();
            args.add("-source"); args.add(level);
            args.add("-target"); args.add(level);
            args.add("-encoding"); args.add("UTF-8");
            args.add("-nowarn");
            args.add("-proc:none");
            args.add("-bootclasspath"); args.add(androidJar.getAbsolutePath());
            if (!jars.isEmpty()) {
                StringBuilder cp = new StringBuilder();
                for (File j : jars) { if (cp.length() > 0) cp.append(File.pathSeparator); cp.append(j.getAbsolutePath()); }
                args.add("-classpath"); args.add(cp.toString());
            }
            args.add("-d"); args.add(classes.getAbsolutePath());
            for (File f : srcs) args.add(f.getAbsolutePath());
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            boolean ok = new Main(pw, pw, false, null, null).compile(args.toArray(new String[0]));
            pw.flush();
            if (!ok) {
                r.errors = sw.toString().trim();
                if (r.errors.isEmpty()) r.errors = "Compilation failed.";
                return r;
            }

            log.step("Converting to DEX…");
            ArrayList<File> cls = new ArrayList<>();
            FileUtil.walk(classes, cls);
            ArrayList<Path> progs = new ArrayList<>();
            for (File f : cls) if (f.getName().endsWith(".class")) progs.add(f.toPath());
            for (File j : jars) progs.add(j.toPath());
            File dexDir = new File(outRoot, "dex");
            dexDir.mkdirs();
            try {
                D8.run(D8Command.builder()
                    .addProgramFiles(progs)
                    .addLibraryFiles(androidJar.toPath())
                    .setMinApiLevel(26)
                    .setOutput(dexDir.toPath(), OutputMode.DexIndexed)
                    .build());
            } catch (CompilationFailedException e) {
                r.errors = "DEX conversion failed:\n" + e.getMessage() + (e.getCause() != null ? "\n" + e.getCause().getMessage() : "");
                return r;
            }
            File[] dx = dexDir.listFiles();
            StringBuilder dp = new StringBuilder();
            if (dx != null) for (File d : dx) if (d.getName().endsWith(".dex")) {
                d.setReadOnly(); // Android 14 refuses writable dex files
                if (dp.length() > 0) dp.append(File.pathSeparator);
                dp.append(d.getAbsolutePath());
            }
            if (dp.length() == 0) { r.errors = "No dex output was produced."; return r; }
            r.dex = dp.toString();
            r.ok = true;
            return r;
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            r.errors = "Internal error: " + t + "\n\n" + sw;
            return r;
        }
    }

    /** Parses ECJ output into file -> list of (line, message). */
    public static Map<String, List<Object[]>> parseErrors(String text) {
        HashMap<String, List<Object[]>> map = new HashMap<>();
        Pattern p = Pattern.compile("ERROR in (.+?) \\(at line (\\d+)\\)");
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            Matcher m = p.matcher(lines[i]);
            if (m.find()) {
                String msg = i + 3 < lines.length ? lines[i + 3].trim() : "Error";
                List<Object[]> l = map.get(m.group(1));
                if (l == null) { l = new ArrayList<>(); map.put(m.group(1), l); }
                l.add(new Object[]{Integer.parseInt(m.group(2)) - 1, msg});
            }
        }
        return map;
    }
}
