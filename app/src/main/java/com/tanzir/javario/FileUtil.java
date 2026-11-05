package com.tanzir.javario;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class FileUtil {
    public static String read(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            return new String(bo.toByteArray(), StandardCharsets.UTF_8);
        } finally { in.close(); }
    }

    public static void write(File f, String s) throws IOException {
        File p = f.getParentFile();
        if (p != null) p.mkdirs();
        FileOutputStream o = new FileOutputStream(f);
        try { o.write(s.getBytes(StandardCharsets.UTF_8)); } finally { o.close(); }
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) > 0) out.write(b, 0, n);
    }

    public static void copyFile(File a, File b) throws IOException {
        b.getParentFile().mkdirs();
        FileInputStream in = new FileInputStream(a);
        FileOutputStream out = new FileOutputStream(b);
        try { copy(in, out); } finally { in.close(); out.close(); }
    }

    public static void copyRec(File a, File b) throws IOException {
        if (a.isDirectory()) {
            b.mkdirs();
            File[] ch = a.listFiles();
            if (ch != null) for (File c : ch) copyRec(c, new File(b, c.getName()));
        } else copyFile(a, b);
    }

    public static void deleteRec(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] ch = f.listFiles();
            if (ch != null) for (File c : ch) deleteRec(c);
        }
        f.delete();
    }

    public static boolean move(File a, File b) {
        if (a.renameTo(b)) return true;
        try { copyRec(a, b); deleteRec(a); return true; } catch (IOException e) { return false; }
    }

    public static String ext(File f) {
        String n = f.getName();
        int i = n.lastIndexOf('.');
        return i < 0 ? "" : n.substring(i + 1).toLowerCase();
    }

    public static String baseName(String n) {
        int i = n.lastIndexOf('.');
        return i <= 0 ? n : n.substring(0, i);
    }

    public static void walk(File dir, ArrayList<File> out) {
        File[] ch = dir.listFiles();
        if (ch == null) return;
        Arrays.sort(ch);
        for (File f : ch) {
            if (f.isDirectory()) walk(f, out); else out.add(f);
        }
    }

    public static File[] sorted(File dir) {
        File[] ch = dir.listFiles();
        if (ch == null) return new File[0];
        Arrays.sort(ch, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        return ch;
    }

    public static void zip(File src, File zipFile) throws IOException {
        zipFile.getParentFile().mkdirs();
        ZipOutputStream z = new ZipOutputStream(new FileOutputStream(zipFile));
        try {
            String base = src.getParentFile().getAbsolutePath().length() + 1 > 0 ? src.getParentFile().getAbsolutePath() : "";
            ArrayList<File> all = new ArrayList<>();
            if (src.isDirectory()) walk(src, all); else all.add(src);
            for (File f : all) {
                String rel = f.getAbsolutePath().substring(base.length() + 1);
                z.putNextEntry(new ZipEntry(rel));
                FileInputStream in = new FileInputStream(f);
                try { copy(in, z); } finally { in.close(); }
                z.closeEntry();
            }
        } finally { z.close(); }
    }

    public static boolean looksBinary(File f) {
        try {
            FileInputStream in = new FileInputStream(f);
            try {
                byte[] b = new byte[1024];
                int n = in.read(b);
                for (int i = 0; i < n; i++) if (b[i] == 0) return true;
            } finally { in.close(); }
        } catch (IOException ignored) { }
        return false;
    }

    public static String uniqueName(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return name;
        String base = baseName(name), ext = name.contains(".") && name.lastIndexOf('.') > 0 ? name.substring(name.lastIndexOf('.')) : "";
        int i = 2;
        while (new File(dir, base + "_" + i + ext).exists()) i++;
        return base + "_" + i + ext;
    }
}
