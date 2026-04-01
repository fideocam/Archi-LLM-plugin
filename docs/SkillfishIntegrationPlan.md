# Plan: integrating ArchiGPT with Skillfish

This document is an implementation plan for using **[Skillfish](https://www.npmjs.com/package/skillfish)** (`npx skillfish`, `skillfish search`, etc.) from the **ArchiGPT** Eclipse plugin. It assumes you want **search**, optional **install**, and **injecting `SKILL.md` content** into the Ollama request (system or user wrapper).

---

## What Skillfish actually provides (facts for design)

| Aspect | Detail |
|--------|--------|
| **Search index** | CLI search targets **[skill.fish](https://skill.fish)**, not a documented public REST API you own. MCP Market is discovery/submission-related; automation goes through Skillfish. |
| **Automation** | Commands support **`--json`** and non-interactive mode (no TTY / CI). |
| **Install model** | Skills come from **GitHub** (`owner/repo`, paths, refs); installs copy material into **per-agent directories** (Cursor, Claude Code, …). **Archi / ArchiGPT is not listed** as a supported agent today. |
| **Team sync** | **`skillfish.json`** manifest + `skillfish install` / `bundle`. |
| **License** | **AGPL-3.0**. Bundling or tightly coupling Skillfish *into* distributed plugin code may trigger copyleft obligations—**get legal review** if you ship Skillfish or embed its code. **Spawning an external `skillfish` / `npx` process** the user installs separately is a common way to keep boundaries clear (still confirm with counsel for your distribution model). |
| **Runtime** | **Node.js** required (`npm` / `npx`). Archi users may not have it. |
| **Telemetry** | Anonymous aggregate installs; opt-out: **`DO_NOT_TRACK=1`** or **`CI=true`**. |

---

## Goals (pick what you actually need)

1. **Search** skills from ArchiGPT UI and show titles/descriptions (JSON from `skillfish search … --json`).
2. **One-shot use**: fetch or open the skill’s **`SKILL.md`** and append to the prompt sent to Ollama (no global install).
3. **Project manifest**: commit **`skillfish.json`** in the model repo (or a sibling repo) and **`skillfish install`** for developers who use Cursor + Archi; ArchiGPT only **reads local `SKILL.md` trees** under the project.
4. **Optional**: upstream contribution to Skillfish: **register “ArchiGPT”** as an agent with a dedicated skills directory (e.g. under user home or project), so `skillfish add` can target Archi users.

---

## Non-goals (initially)

- Replacing **`system-prompt.txt`** entirely with Skillfish-managed content (keep ArchiMate rules canonical in-repo).
- Scraping **mcpmarket.com** HTML (fragile, unclear ToS).
- Bundling a full Node runtime inside the Archi plugin (large maintenance cost) unless you explicitly decide to.

---

## Integration options (recommended order)

### Option A — **Subprocess + `--json`** (lowest coupling)

1. **Preferences**: `node` path (or `npx`), optional `skillfish` global path, timeout, max results.
2. **“Search skills…”** action: run e.g.  
   `skillfish search <query> --limit 20 --json`  
   (or `npx --yes skillfish@latest …` if you do not require global install).
3. Parse JSON; populate a SWT/JFace list or table.
4. **“Use skill”** for a result: either  
   - run `skillfish add owner/repo …` into a **known folder** ArchiGPT reads (see Option B), or  
   - resolve GitHub raw URL for `SKILL.md` **only if license/terms allow** and your UI disclaims trust—prefer install-from-repo flow Skillfish already uses.

**Pros:** No AGPL code inside your JAR; uses supported CLI contract.  
**Cons:** Node must exist; subprocess errors UX; version drift if `npx` always pulls latest.

### Option B — **Read-only: project skills directory**

1. Convention: `./skills/<name>/SKILL.md` or `.archigpt/skills/` in the **project** (or next to the `.archimate` file).
2. ArchiGPT preference: “Additional skill directories” (glob or list).
3. Optional: document **`skillfish add … --project`** so teams sync the same tree Cursor uses, **without** Skillfish knowing about Archi.

**Pros:** No network at prompt time; works offline; aligns with `skillfish.json` workflow.  
**Cons:** No in-app search unless you add Option A or a static catalog.

### Option C — **Upstream: ArchiGPT agent in Skillfish**

1. Open an issue/PR on **[knoxgraeme/skillfish](https://github.com/knoxgraeme/skillfish)** to add an **ArchiGPT** entry in agent paths (mirror Cursor-style `~/.cursor/skills/` → e.g. `~/.archigpt/skills/` or plugin workspace state dir).
2. After merge, Option A’s `skillfish add` can install directly for ArchiGPT users.

**Pros:** One toolchain for users who already use Skillfish.  
**Cons:** Depends on third-party release cycle; still Node-dependent.

---

## Phased delivery

| Phase | Scope | Exit criteria |
|-------|--------|----------------|
| **0 — Spike** | From terminal, run `skillfish search foo --json`; capture schema; measure latency; confirm corporate proxy behavior. | Documented sample JSON + failure modes (exit codes 3–4). |
| **1 — Read local skills** | Preference + scan fixed dirs for `SKILL.md`; combo to append one file to `UserMessageBuilder` or system string (with **size cap**). | Manual test: selected skill text appears in Debug “payload” view. |
| **2 — Subprocess search** | SWT dialog: query → `ProcessBuilder` → parse JSON → list; double-click copies path or id for Phase 1 dirs. | Works on macOS/Win with Node on PATH; clear error if Node missing. |
| **3 — Install path** | Wire “Install” to `skillfish add …` into project dir ArchiGPT already scans; or document manual `skillfish add` + refresh. | End-to-end: search → install → appears in skill picker. |
| **4 — Hardening** | Timeouts, rate limits, `DO_NOT_TRACK=1` in env for subprocess, version pinning (`skillfish@x.y.z`), optional legal note in README. | Release checklist complete. |

---

## Risks and mitigations

| Risk | Mitigation |
|------|------------|
| **AGPL** | Prefer **external CLI**; avoid vendoring Skillfish source into the plugin without legal sign-off. |
| **No Node on Archi machines** | Graceful degradation: Phase 1 only (local files); README prerequisite for search. |
| **Prompt injection via skills** | Treat skills as **untrusted**; show preview; max length; optional “trusted paths only”. |
| **JSON schema changes** | Pin Skillfish major version in docs; parse defensively; log raw JSON on parse failure. |
| **Search is skill.fish, not MCP Market** | Set user expectations in UI copy; MCP Market remains human browse only unless they publish an API later. |

---

## Alternatives if Skillfish is too heavy

- **Static `skills/` in repo** + no Skillfish at runtime (team uses Skillfish only for **authoring** / **submit**).
- **Your own JSON catalog** (URLs to `SKILL.md` on GitHub) and a tiny HTTP fetch in Java—no Node, but you maintain the catalog.
- **MCP server** that wraps search/install (if you already standardize on MCP for Archi tooling).

---

## References

- Skillfish npm: https://www.npmjs.com/package/skillfish  
- Repo: https://github.com/knoxgraeme/skillfish  
- Related doc in this repo: [docs/Skills.md](./Skills.md)
