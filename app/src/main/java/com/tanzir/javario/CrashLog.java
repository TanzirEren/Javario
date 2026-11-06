package com.tanzir.javario;

import android.content.Context;
import android.os.Process;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Saves crashes and the last "step" reached, so a crash can be shown inside the app next time. */
public class CrashLog {
    static File crashFile(Context c) { return new File(c.getFilesDir(), "crash.log"); }
    static File stepFile(Context c) { return new File(c.getFilesDir(), "step.txt"); }

    public static void install(final Context app) {
        final Thread.UncaughtExceptionHandler old = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                try {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    String last = "";
                    try { last = FileUtil.read(stepFile(app)); } catch (Exception ignored) { }
                    String txt = time() + "  process pid " + Process.myPid() + "  thread " + t.getName() + "\nLast step: " + last + "\n\n" + sw;
                    FileUtil.write(crashFile(app), txt);
                    try { FileUtil.write(new File(ProjectStore.root(), "crash.log"), txt); } catch (Exception ignored) { }
                } catch (Throwable ignored) { }
                if (old != null) old.uncaughtException(t, e);
            }
        });
    }

    static String time() { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()); }

    /** Remember what the app is doing (also survives if the process is killed). */
    public static void step(Context c, String s) {
        try { FileUtil.write(stepFile(c), time() + " " + s); } catch (Throwable ignored) { }
    }

    /** Text to show on next launch, or null. */
    public static String pending(Context c) {
        try {
            if (crashFile(c).exists()) return FileUtil.read(crashFile(c));
            if (stepFile(c).exists()) {
                String s = FileUtil.read(stepFile(c));
                if (!s.trim().isEmpty() && !s.trim().endsWith("DONE")) return "The app was stopped while running.\nLast step: " + s;
            }
        } catch (Throwable ignored) { }
        return null;
    }

    public static void clear(Context c) {
        crashFile(c).delete();
        step(c, "DONE");
    }
}
