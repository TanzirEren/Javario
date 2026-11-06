package com.tanzir.javario;

import android.content.Context;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** An AI provider (OpenRouter, OpenAI, Groq, custom ...) with its models. Saved as JSON in settings. */
public class AiProvider {
    public static final String BUILTIN = "builtin";

    public String id, name = "", baseUrl = "", apiKey = "", type = "openai";
    public boolean enabled = true;
    public ArrayList<Model> models = new ArrayList<>();

    public static class Model {
        public String id;
        public boolean free, enabled;
        public Model(String id, boolean free, boolean enabled) { this.id = id; this.free = free; this.enabled = enabled; }
    }

    public static class Preset {
        public final String name, url, type, hint;
        Preset(String n, String u, String t, String h) { name = n; url = u; type = t; hint = h; }
    }

    public static class Entry {
        public AiProvider p;
        public Model m;
    }

    public static final Preset[] PRESETS = {
        new Preset("OpenRouter", "https://openrouter.ai/api/v1", "openai", "Many models, some free"),
        new Preset("OpenAI (GPT)", "https://api.openai.com/v1", "openai", "GPT models"),
        new Preset("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", "openai", "Gemini models (free tier with an AI Studio key)"),
        new Preset("Groq", "https://api.groq.com/openai/v1", "openai", "Very fast open models"),
        new Preset("Anthropic (Claude)", "https://api.anthropic.com/v1", "anthropic", "Claude models"),
        new Preset("DeepSeek", "https://api.deepseek.com/v1", "openai", "DeepSeek chat and reasoner"),
        new Preset("Mistral AI", "https://api.mistral.ai/v1", "openai", "Mistral and Codestral"),
        new Preset("xAI (Grok)", "https://api.x.ai/v1", "openai", "Grok models"),
        new Preset("Together AI", "https://api.together.xyz/v1", "openai", "Open source models"),
        new Preset("Fireworks AI", "https://api.fireworks.ai/inference/v1", "openai", "Open source models"),
        new Preset("Cerebras", "https://api.cerebras.ai/v1", "openai", "Very fast inference"),
        new Preset("NVIDIA NIM", "https://integrate.api.nvidia.com/v1", "openai", "NVIDIA hosted models"),
        new Preset("SambaNova", "https://api.sambanova.ai/v1", "openai", "Fast open models"),
        new Preset("Hugging Face", "https://router.huggingface.co/v1", "openai", "Inference providers router"),
        new Preset("Perplexity", "https://api.perplexity.ai", "openai", "Search powered models (type model ids by hand)"),
        new Preset("Moonshot (Kimi)", "https://api.moonshot.ai/v1", "openai", "Kimi models"),
        new Preset("Cohere", "https://api.cohere.ai/compatibility/v1", "openai", "Command models"),
        new Preset("OmniRoute", "", "openai", "Type your OmniRoute base URL, for example https://your-host/v1"),
        new Preset("Ollama (local)", "http://localhost:11434/v1", "openai", "Models running on your device or PC (no key needed)"),
        new Preset("LM Studio (local)", "http://localhost:1234/v1", "openai", "Local server (no key needed)"),
    };

    public boolean isAnthropic() { return "anthropic".equals(type); }
    public boolean isBuiltin() { return BUILTIN.equals(id); }

    public boolean isLocal() {
        String u = baseUrl.toLowerCase();
        return u.contains("://localhost") || u.contains("://127.") || u.contains("://192.168.") || u.contains("://10.0.");
    }

    public int freeCount() { int n = 0; for (Model m : models) if (m.free) n++; return n; }
    public int enabledCount() { int n = 0; for (Model m : models) if (m.enabled) n++; return n; }

    public AiProvider copy() {
        AiProvider c = new AiProvider();
        c.id = id; c.name = name; c.baseUrl = baseUrl; c.apiKey = apiKey; c.type = type; c.enabled = enabled;
        for (Model m : models) c.models.add(new Model(m.id, m.free, m.enabled));
        return c;
    }

    JSONObject toJson() throws org.json.JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id).put("name", name).put("url", baseUrl).put("key", apiKey).put("type", type).put("on", enabled);
        JSONArray a = new JSONArray();
        for (Model m : models) a.put(new JSONObject().put("id", m.id).put("free", m.free).put("on", m.enabled));
        o.put("models", a);
        return o;
    }

    static AiProvider fromJson(JSONObject o) {
        AiProvider p = new AiProvider();
        p.id = o.optString("id", newId());
        p.name = o.optString("name");
        p.baseUrl = o.optString("url");
        p.apiKey = o.optString("key");
        p.type = o.optString("type", "openai");
        p.enabled = o.optBoolean("on", true);
        JSONArray a = o.optJSONArray("models");
        if (a != null) for (int i = 0; i < a.length(); i++) {
            JSONObject m = a.optJSONObject(i);
            if (m != null) p.models.add(new Model(m.optString("id"), m.optBoolean("free"), m.optBoolean("on", true)));
        }
        return p;
    }

    public static String newId() { return "p" + System.currentTimeMillis() + (int) (Math.random() * 1000); }

    public static ArrayList<AiProvider> load(Context c) {
        ArrayList<AiProvider> out = new ArrayList<>();
        try {
            String s = Prefs.s(c, "providers_json");
            if (s.isEmpty()) return out;
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) out.add(fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) { }
        return out;
    }

    public static void save(Context c, List<AiProvider> list) {
        try {
            JSONArray a = new JSONArray();
            for (AiProvider p : list) a.put(p.toJson());
            Prefs.set(c, "providers_json", a.toString());
        } catch (Exception ignored) { }
    }

    public static AiProvider offline() {
        AiProvider p = new AiProvider();
        p.id = BUILTIN;
        p.name = "Javario Offline";
        p.models.add(new Model("offline-tutor", true, true));
        return p;
    }

    /** All usable models: enabled providers, enabled models, plus the built-in offline tutor. */
    public static ArrayList<Entry> entries(Context c) {
        ArrayList<Entry> out = new ArrayList<>();
        for (AiProvider p : load(c)) {
            if (!p.enabled) continue;
            for (Model m : p.models) if (m.enabled) {
                Entry e = new Entry();
                e.p = p; e.m = m;
                out.add(e);
            }
        }
        AiProvider off = offline();
        Entry e = new Entry();
        e.p = off; e.m = off.models.get(0);
        out.add(e);
        return out;
    }

    public static Entry selected(Context c) {
        ArrayList<Entry> all = entries(c);
        String s = Prefs.s(c, "ai_selected");
        int bar = s.indexOf('|');
        if (bar > 0) {
            String pid = s.substring(0, bar), mid = s.substring(bar + 1);
            for (Entry e : all) if (e.p.id.equals(pid) && e.m.id.equals(mid)) return e;
        }
        return all.get(0);
    }

    public static void select(Context c, AiProvider p, Model m) {
        Prefs.set(c, "ai_selected", p.id + "|" + m.id);
    }
}
