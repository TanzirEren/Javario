package com.tanzir.javario;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import dalvik.system.DexClassLoader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Runs the user's program in its own process (:runner).
 * System.exit(), crashes, infinite loops and out-of-memory can never take down the IDE; "Stop" just kills this process.
 */
public class RunnerService extends Service {
    public static final int MSG_RUN = 1, MSG_INPUT = 2, MSG_STOP = 3, MSG_OUT = 10, MSG_ERR = 11, MSG_DONE = 12;

    static class StdIn extends InputStream {
        final LinkedBlockingQueue<Integer> q = new LinkedBlockingQueue<>();
        void feed(String s) { for (byte b : (s + "\n").getBytes(StandardCharsets.UTF_8)) q.add(b & 0xff); }
        @Override public int read() throws IOException {
            try { return q.take(); } catch (InterruptedException e) { return -1; }
        }
        @Override public int read(byte[] b, int o, int l) throws IOException {
            if (l == 0) return 0;
            int f = read();
            if (f < 0) return -1;
            b[o] = (byte) f;
            int n = 1;
            Integer x;
            while (n < l && (x = q.poll()) != null) b[o + n++] = (byte) (int) x;
            return n;
        }
        @Override public int available() { return q.size(); }
    }

    class Sink extends OutputStream {
        final int what;
        Sink(int w) { what = w; }
        @Override public void write(int b) { write(new byte[]{(byte) b}, 0, 1); }
        @Override public void write(byte[] b, int off, int len) {
            int pos = off, end = off + len;
            while (pos < end) {
                int n = Math.min(end - pos, 30000);
                send(what, new String(b, pos, n, StandardCharsets.UTF_8), 0);
                pos += n;
            }
        }
    }

    volatile Messenger client;
    final StdIn stdin = new StdIn();
    boolean started;
    final Handler handler = new Handler(Looper.getMainLooper()) {
        @Override public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_RUN:
                    client = msg.replyTo;
                    if (!started) {
                        started = true;
                        Bundle b = msg.getData();
                        startProgram(b.getString("dex"), b.getString("main"), b.getString("jars", ""), b.getString("project", ""));
                    }
                    break;
                case MSG_INPUT:
                    stdin.feed(msg.getData().getString("t", ""));
                    break;
                case MSG_STOP:
                    Process.killProcess(Process.myPid());
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    };
    final Messenger messenger = new Messenger(handler);

    @Override public IBinder onBind(Intent i) { return messenger.getBinder(); }

    @Override public boolean onUnbind(Intent i) {
        // every run gets a fresh process, so static variables never leak between runs
        Process.killProcess(Process.myPid());
        return false;
    }

    void send(int what, String text, long num) {
        Messenger c = client;
        if (c == null) return;
        Message m = Message.obtain(null, what);
        Bundle b = new Bundle();
        b.putString("t", text);
        b.putLong("n", num);
        m.setData(b);
        try { c.send(m); } catch (RemoteException ignored) { }
    }

    void startProgram(final String dex, final String main, final String jars, final String project) {
        System.setOut(new PrintStream(new Sink(MSG_OUT), true));
        System.setErr(new PrintStream(new Sink(MSG_ERR), true));
        System.setIn(stdin);
        if (!project.isEmpty()) System.setProperty("user.dir", project);
        System.setProperty("javario.project", project);
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                System.err.print("Exception in thread \"" + t.getName() + "\" ");
                e.printStackTrace();
            }
        });
        final long t0 = System.currentTimeMillis();
        Runnable job = new Runnable() {
            @Override public void run() {
                try {
                    String path = dex + (jars.isEmpty() ? "" : java.io.File.pathSeparator + jars);
                    ClassLoader parent = Object.class.getClassLoader();
                    DexClassLoader cl;
                    try {
                        cl = new DexClassLoader(path, getCodeCacheDir().getAbsolutePath(), null, parent);
                    } catch (Throwable t) {
                        cl = new DexClassLoader(dex, getCodeCacheDir().getAbsolutePath(), null, parent);
                    }
                    Class<?> c = cl.loadClass(main);
                    Method m = c.getMethod("main", String[].class);
                    m.setAccessible(true);
                    m.invoke(null, (Object) new String[0]);
                } catch (InvocationTargetException e) {
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    System.err.print("Exception in thread \"main\" ");
                    c.printStackTrace();
                } catch (ClassNotFoundException e) {
                    System.err.println("Main class not found: " + main);
                } catch (NoSuchMethodException e) {
                    System.err.println("The class " + main + " has no 'public static void main(String[] args)' method.");
                } catch (Throwable t) {
                    t.printStackTrace();
                }
                System.out.flush();
                System.err.flush();
                send(MSG_DONE, "", System.currentTimeMillis() - t0);
            }
        };
        // big stack so deep recursion examples work
        new Thread(null, job, "main", 64L * 1024 * 1024).start();
    }
}
