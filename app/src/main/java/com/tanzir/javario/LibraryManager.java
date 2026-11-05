package com.tanzir.javario;

import android.content.Context;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Library catalog (assets/libraries.txt) + automatic download from Maven Central. */
public class LibraryManager {
    public static class Lib {
        public String name, coords, prefix, cat, desc;
        public String[] all() { return coords.trim().split("\\s+"); }
        public String main() { return all()[0]; }
    }

    private static ArrayList<Lib> cache;

    public static synchronized ArrayList<Lib> catalog(Context c) {
        if (cache != null) return cache;
        ArrayList<Lib> out = new ArrayList<>();
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("libraries.txt"), "UTF-8"));
            String ln;
            while ((ln = r.readLine()) != null) {
                if (ln.trim().isEmpty() || ln.startsWith("#")) continue;
                String[] p = ln.split("\\|");
                if (p.length < 5) continue;
                Lib l = new Lib();
                l.name = p[0]; l.coords = p[1]; l.prefix = p[2]; l.cat = p[3]; l.desc = p[4];
                out.add(l);
            }
            r.close();
        } catch (IOException ignored) { }
        cache = out;
        return out;
    }

    /** "group:artifact:version" -> file name artifact-version.jar */
    public static String jarName(String coord) {
        String[] p = coord.split(":");
        return p[1] + "-" + p[2] + ".jar";
    }

    public static boolean isInstalled(File project, Lib l) {
        for (String c : l.all()) if (!new File(project, "libs/" + jarName(c)).exists()) return false;
        return true;
    }

    public static void download(String coord, File libsDir) throws IOException {
        String[] p = coord.split(":");
        if (p.length < 3) throw new IOException("Bad coordinate: " + coord);
        File dest = new File(libsDir, jarName(coord));
        if (dest.exists() && dest.length() > 0) return;
        libsDir.mkdirs();
        String url = "https://repo1.maven.org/maven2/" + p[0].replace('.', '/') + "/" + p[1] + "/" + p[2] + "/" + jarName(coord);
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(15000);
        h.setReadTimeout(30000);
        h.setInstanceFollowRedirects(true);
        if (h.getResponseCode() != 200) throw new IOException("Download failed (" + h.getResponseCode() + "): " + url);
        File tmp = new File(libsDir, jarName(coord) + ".part");
        InputStream in = h.getInputStream();
        FileOutputStream out = new FileOutputStream(tmp);
        try { FileUtil.copy(in, out); } finally { in.close(); out.close(); }
        if (!tmp.renameTo(dest)) throw new IOException("Cannot save " + dest.getName());
    }

    public static void install(Lib l, File project) throws IOException {
        File libs = new File(project, "libs");
        for (String c : l.all()) download(c, libs);
    }

    public static void remove(Lib l, File project) {
        for (String c : l.all()) new File(project, "libs/" + jarName(c)).delete();
    }

    /** Finds imports in the source and downloads any library that matches. */
    public static void autoDownload(Context ctx, File project, String source, JavaRunner.Log log) throws IOException {
        Matcher m = Pattern.compile("(?m)^\\s*import\\s+(?:static\\s+)?([\\w.]+)").matcher(source);
        ArrayList<String> imports = new ArrayList<>();
        while (m.find()) imports.add(m.group(1));
        for (Lib l : catalog(ctx)) {
            boolean used = false;
            for (String i : imports) if (i.startsWith(l.prefix + ".") || i.equals(l.prefix)) { used = true; break; }
            if (!used || isInstalled(project, l)) continue;
            log.step("Downloading " + l.name + "…");
            install(l, project);
        }
    }
}
