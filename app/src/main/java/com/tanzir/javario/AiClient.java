package com.tanzir.javario;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

/** Talks to OpenAI-compatible and Anthropic APIs: list models, chat (streaming), test. */
public class AiClient {
    public interface Stream {
        void token(String t);
        void done();
        void error(String msg);
    }

    public static class Handle {
        volatile boolean cancelled;
        volatile HttpURLConnection conn;
        public void cancel() {
            cancelled = true;
            try { if (conn != null) conn.disconnect(); } catch (Exception ignored) { }
        }
    }

    /** One chat message. images = {mime, base64}. */
    public static class M {
        public String role, text;
        public final ArrayList<String[]> images = new ArrayList<>();
        public M(String r, String t) { role = r; text = t; }
    }

    static class ApiException extends Exception {
        final int code;
        final String body;
        ApiException(int code, String body) { super(extractMessage(code, body)); this.code = code; this.body = body; }
    }

    static String extractMessage(int code, String body) {
        try {
            JSONObject o = new JSONObject(body);
            Object e = o.opt("error");
            if (e instanceof JSONObject) {
                String m = ((JSONObject) e).optString("message");
                if (!m.isEmpty()) return "HTTP " + code + ": " + m;
            } else if (e instanceof String) return "HTTP " + code + ": " + e;
            String m = o.optString("message");
            if (!m.isEmpty()) return "HTTP " + code + ": " + m;
        } catch (Exception ignored) { }
        String b = body == null ? "" : body.trim();
        if (b.length() > 300) b = b.substring(0, 300) + "…";
        return "HTTP " + code + (b.isEmpty() ? "" : ": " + b);
    }

    static String base(AiProvider p) {
        String b = p.baseUrl.trim();
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        return b;
    }

    static HttpURLConnection open(AiProvider p, String url, String method) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(20000);
        c.setReadTimeout(90000);
        c.setRequestProperty("Accept", "application/json");
        String key = p.apiKey.trim();
        if (p.isAnthropic()) {
            c.setRequestProperty("x-api-key", key);
            c.setRequestProperty("anthropic-version", "2023-06-01");
        } else if (!key.isEmpty()) {
            c.setRequestProperty("Authorization", "Bearer " + key);
        }
        if (p.baseUrl.contains("openrouter.ai")) {
            c.setRequestProperty("HTTP-Referer", "https://javario.app");
            c.setRequestProperty("X-Title", "Javario");
        }
        return c;
    }

    static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[8192];
        int n;
        while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
        r.close();
        return sb.toString();
    }

    // ------------------------------------------------------------------ models
    static boolean zero(Object v) {
        if (v == null || v == JSONObject.NULL) return false;
        try { return Double.parseDouble(String.valueOf(v)) == 0.0; } catch (Exception e) { return false; }
    }

    /** Blocking. Returns the models of a provider (free ones are flagged). */
    public static ArrayList<AiProvider.Model> fetchModels(AiProvider p) throws Exception {
        String url = base(p) + "/models" + (p.isAnthropic() ? "?limit=1000" : "");
        HttpURLConnection c = open(p, url, "GET");
        int code = c.getResponseCode();
        String txt = readAll(code < 400 ? c.getInputStream() : c.getErrorStream());
        if (code >= 400) throw new ApiException(code, txt);
        Object root = new JSONTokener(txt).nextValue();
        JSONArray arr = null;
        if (root instanceof JSONArray) arr = (JSONArray) root;
        else if (root instanceof JSONObject) {
            arr = ((JSONObject) root).optJSONArray("data");
            if (arr == null) arr = ((JSONObject) root).optJSONArray("models");
        }
        if (arr == null) throw new Exception("Unexpected response from /models");
        ArrayList<AiProvider.Model> out = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        for (int i = 0; i < arr.length(); i++) {
            Object it = arr.get(i);
            String id;
            boolean free = p.isLocal();
            if (it instanceof JSONObject) {
                JSONObject o = (JSONObject) it;
                id = o.optString("id", "");
                if (id.isEmpty()) id = o.optString("name", "");
                if (id.startsWith("models/")) id = id.substring(7);
                if (id.endsWith(":free")) free = true;
                if (o.optBoolean("is_free", false)) free = true;
                JSONObject pr = o.optJSONObject("pricing");
                if (pr != null) {
                    if (pr.has("prompt") && pr.has("completion") && zero(pr.opt("prompt")) && zero(pr.opt("completion"))) free = true;
                    if (pr.has("input") && pr.has("output") && zero(pr.opt("input")) && zero(pr.opt("output"))) free = true;
                }
            } else id = String.valueOf(it);
            if (id.isEmpty() || !seen.add(id)) continue;
            out.add(new AiProvider.Model(id, free, false));
        }
        Collections.sort(out, new Comparator<AiProvider.Model>() {
            @Override public int compare(AiProvider.Model a, AiProvider.Model b) {
                if (a.free != b.free) return a.free ? -1 : 1;
                return a.id.compareToIgnoreCase(b.id);
            }
        });
        return out;
    }

    // ------------------------------------------------------------------ chat
    public static Handle chat(final Context ctx, final AiProvider p, final String model, final String system,
                              final List<M> msgs, final boolean stream, final Stream cb) {
        final Handle h = new Handle();
        final float temp = Prefs.i(ctx, "ai_temperature") / 10f;
        final int maxTok = Prefs.i(ctx, "ai_max_tokens");
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    if (p.isAnthropic()) anthropic(p, model, system, msgs, stream, temp, maxTok, h, cb);
                    else openai(p, model, system, msgs, stream, temp, maxTok, h, cb);
                    if (!h.cancelled) cb.done();
                } catch (Exception e) {
                    if (!h.cancelled) cb.error(e.getMessage() == null ? e.toString() : e.getMessage());
                }
            }
        }, "javario-ai").start();
        return h;
    }

    static void post(HttpURLConnection c, JSONObject body) throws Exception {
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        OutputStream o = c.getOutputStream();
        o.write(body.toString().getBytes("UTF-8"));
        o.close();
    }

    static void openai(AiProvider p, String model, String system, List<M> msgs, boolean stream, float temp, int maxTok,
                       Handle h, Stream cb) throws Exception {
        boolean useTemp = true, useCompletionTokens = false;
        for (int attempt = 0; attempt < 3; attempt++) {
            JSONObject body = new JSONObject();
            body.put("model", model);
            JSONArray ms = new JSONArray();
            if (system != null && !system.isEmpty()) ms.put(new JSONObject().put("role", "system").put("content", system));
            for (M m : msgs) {
                if (m.images.isEmpty()) ms.put(new JSONObject().put("role", m.role).put("content", m.text));
                else {
                    JSONArray parts = new JSONArray();
                    parts.put(new JSONObject().put("type", "text").put("text", m.text));
                    for (String[] im : m.images) {
                        parts.put(new JSONObject().put("type", "image_url")
                            .put("image_url", new JSONObject().put("url", "data:" + im[0] + ";base64," + im[1])));
                    }
                    ms.put(new JSONObject().put("role", m.role).put("content", parts));
                }
            }
            body.put("messages", ms);
            if (useTemp) body.put("temperature", (double) temp);
            body.put(useCompletionTokens ? "max_completion_tokens" : "max_tokens", maxTok);
            body.put("stream", stream);
            HttpURLConnection c = open(p, base(p) + "/chat/completions", "POST");
            if (stream) c.setRequestProperty("Accept", "text/event-stream");
            h.conn = c;
            post(c, body);
            int code = c.getResponseCode();
            if (code >= 400) {
                String err = readAll(c.getErrorStream());
                String low = err.toLowerCase();
                if (code == 400 && attempt < 2) {
                    if (low.contains("max_completion_tokens") && !useCompletionTokens) { useCompletionTokens = true; continue; }
                    if (low.contains("temperature") && useTemp) { useTemp = false; continue; }
                }
                throw new ApiException(code, err);
            }
            String ct = c.getContentType() == null ? "" : c.getContentType();
            if (!stream || ct.contains("application/json")) {
                String txt = readAll(c.getInputStream());
                JSONObject j = new JSONObject(txt);
                JSONArray ch = j.optJSONArray("choices");
                if (ch == null || ch.length() == 0) throw new Exception("Empty reply from model");
                JSONObject msg = ch.getJSONObject(0).optJSONObject("message");
                if (msg != null && !msg.isNull("content")) {
                    Object cont = msg.get("content");
                    if (cont instanceof JSONArray) {
                        JSONArray ca = (JSONArray) cont;
                        for (int i = 0; i < ca.length(); i++) cb.token(ca.getJSONObject(i).optString("text", ""));
                    } else cb.token(String.valueOf(cont));
                }
                return;
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                if (h.cancelled) break;
                if (!line.startsWith("data:")) continue;
                String d = line.substring(5).trim();
                if (d.isEmpty()) continue;
                if (d.equals("[DONE]")) break;
                JSONObject j;
                try { j = new JSONObject(d); } catch (Exception e) { continue; }
                if (j.has("error") && !j.isNull("error")) {
                    Object eo = j.get("error");
                    throw new Exception(eo instanceof JSONObject ? ((JSONObject) eo).optString("message", eo.toString()) : String.valueOf(eo));
                }
                JSONArray ch = j.optJSONArray("choices");
                if (ch == null || ch.length() == 0) continue;
                JSONObject delta = ch.getJSONObject(0).optJSONObject("delta");
                if (delta != null && !delta.isNull("content")) {
                    String t = delta.optString("content", "");
                    if (!t.isEmpty()) cb.token(t);
                }
            }
            r.close();
            return;
        }
    }

    static void anthropic(AiProvider p, String model, String system, List<M> msgs, boolean stream, float temp, int maxTok,
                          Handle h, Stream cb) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("max_tokens", maxTok);
        if (system != null && !system.isEmpty()) body.put("system", system);
        body.put("temperature", (double) Math.min(1f, temp));
        body.put("stream", stream);
        JSONArray ms = new JSONArray();
        String lastRole = null;
        for (M m : msgs) {
            if (m.role.equals(lastRole) && ms.length() > 0 && m.images.isEmpty()) {
                JSONObject prev = ms.getJSONObject(ms.length() - 1);
                if (prev.get("content") instanceof String) {
                    prev.put("content", prev.getString("content") + "\n\n" + m.text);
                    continue;
                }
            }
            JSONObject o = new JSONObject().put("role", m.role);
            if (m.images.isEmpty()) o.put("content", m.text);
            else {
                JSONArray parts = new JSONArray();
                for (String[] im : m.images) {
                    parts.put(new JSONObject().put("type", "image").put("source",
                        new JSONObject().put("type", "base64").put("media_type", im[0]).put("data", im[1])));
                }
                parts.put(new JSONObject().put("type", "text").put("text", m.text));
                o.put("content", parts);
            }
            ms.put(o);
            lastRole = m.role;
        }
        body.put("messages", ms);
        HttpURLConnection c = open(p, base(p) + "/messages", "POST");
        if (stream) c.setRequestProperty("Accept", "text/event-stream");
        h.conn = c;
        post(c, body);
        int code = c.getResponseCode();
        if (code >= 400) throw new ApiException(code, readAll(c.getErrorStream()));
        String ct = c.getContentType() == null ? "" : c.getContentType();
        if (!stream || ct.contains("application/json")) {
            JSONObject j = new JSONObject(readAll(c.getInputStream()));
            JSONArray content = j.optJSONArray("content");
            if (content != null) for (int i = 0; i < content.length(); i++) {
                JSONObject b = content.getJSONObject(i);
                if ("text".equals(b.optString("type"))) cb.token(b.optString("text", ""));
            }
            return;
        }
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        String line;
        while ((line = r.readLine()) != null) {
            if (h.cancelled) break;
            if (!line.startsWith("data:")) continue;
            String d = line.substring(5).trim();
            if (d.isEmpty()) continue;
            JSONObject j;
            try { j = new JSONObject(d); } catch (Exception e) { continue; }
            String type = j.optString("type");
            if (type.equals("error")) {
                JSONObject eo = j.optJSONObject("error");
                throw new Exception(eo != null ? eo.optString("message", "API error") : "API error");
            }
            if (type.equals("content_block_delta")) {
                JSONObject delta = j.optJSONObject("delta");
                if (delta != null && "text_delta".equals(delta.optString("type"))) cb.token(delta.optString("text", ""));
            } else if (type.equals("message_stop")) break;
        }
        r.close();
    }
}
