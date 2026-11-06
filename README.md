# Javario – Learn & practice Java on Android

Java + XML Android app. Build it with GitHub Actions (no PC needed).

## Build on GitHub
1. Create a new GitHub repository.
2. Upload **everything inside this folder** (including the hidden `.github` folder) to the repo root.
3. Open the **Actions** tab → *Build Javario APK* → wait ~5 min (or press **Run workflow**).
4. Download the artifact **Javario-debug-apk** → install `app-debug.apk`.

The workflow copies `android.jar` from the SDK into the app assets. The on-device compiler needs it, so build with the provided workflow.

## How it works
- Projects are saved in `/storage/emulated/0/Javario/<Project_Name>/` (allow "All files access" on first start).
- Run = ECJ (Java → class) → D8 (class → dex) → runs in a separate process (`:console`), so Stop never closes the IDE.
- Import a library (for example `import com.google.gson.Gson;`) and press Run: the jar is downloaded from Maven Central into `libs/`. 170+ libraries are in `app/src/main/assets/libraries.txt` (edit versions there if one fails).
- Language level: Java 8 by default (Java 11 is experimental, Settings → Run).

## Main features
Projects (create/rename/duplicate/zip/delete) · glass file explorer with tree + extension icons · create/import/rename/move/export/delete files & folders · tabs · syntax highlight (Java/XML) · live red error underline · line numbers · pinch zoom · undo/redo · auto indent · auto close brackets · suggestions + snippets (sout, fori, psvm…) · symbol bar · find & replace · go to line · format code · compile check · run dialog + console with Scanner input · copy-all error dialog · 170+ libraries with auto download · Maven coordinate download · Learn (12 lessons) · Challenges (10) · Assistant (offline tutor, error explainer, quiz; optional Claude API) · 5 app themes · 10 editor themes · 34 settings.

## AI providers (Settings → Assistant → Add / manage AI providers)
20 presets (OpenRouter, OpenAI, Gemini, Groq, Claude, DeepSeek, Mistral, xAI, Together, Fireworks, Cerebras, NVIDIA, SambaNova, Hugging Face, Perplexity, Moonshot, Cohere, OmniRoute, Ollama, LM Studio) or a custom provider (name, base URL, API key, model id). Fetch models, FREE tags, All / Free / None selection, search, per-provider and per-model switches, test connection, duplicate/delete.
The IDE has an AI panel (sparkle icon left of Run) that can read the open file, selection, errors and project files, and apply code to files.

## Not tested warning
This project was generated without compiling it. If GitHub Actions shows a compile error, send me the log and I will fix it.
