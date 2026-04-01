# Agent skills: using them and implementing them

This document explains what **agent skills** are in practice, how tools like **Cursor** and **Claude**-family products use them, and how you could wire skills into workflows that touch **Archi** and this **ArchiGPT** plugin.

For a phased plan to integrate the **Skillfish** CLI (search on skill.fish, `skillfish.json`, Node subprocess from ArchiGPT), see **[SkillfishIntegrationPlan.md](./SkillfishIntegrationPlan.md)**.

---

## What a skill is

A **skill** is a reusable block of **instructions, constraints, and workflows** that an AI agent loads when a task matches it. It is not executable code by itself; it is **documentation for the model** (and sometimes for humans), usually stored as Markdown.

Compared to a one-off chat message, a skill is:

- **Persistent** — checked in to the repo or installed in a skills directory  
- **Scoped** — “use this when the user asks for X”  
- **Versioned** — you can review diffs like any other doc  

Skills complement:

- **System prompts** (global behavior, e.g. ArchiGPT’s `system-prompt.txt`)  
- **Rules** (Cursor `.cursor/rules`, always-on or glob-scoped hints)  
- **MCP tools** (machine-callable APIs the agent invokes)  

---

## How to use skills (you, as a developer)

### In Cursor

1. **Built-in / marketplace skills**  
   Cursor can surface skills from configured locations (see [Cursor docs on Agent Skills](https://docs.cursor.com) for the current layout; paths have evolved between `~/.cursor/skills`, project skills, and bundled packs).

2. **Project-local skills**  
   Add a skill folder the tool recognizes, typically containing a `SKILL.md` (name may vary by version) with:
   - **When to apply** the skill (triggers, keywords, file types)  
   - **Steps** or checklists  
   - **Do / don’t** lists  
   - **Examples** (inputs and expected outputs)

3. **Rules instead of or alongside skills**  
   For always-on conventions, `.cursor/rules` or `AGENTS.md` at the repo root are often simpler. Use skills when the behavior should activate **only for certain tasks**.

### With Claude (Claude.ai, Claude Code, API)

1. **Chat / project instructions**  
   Paste a short skill summary into **Custom instructions** or a **Project** system field so every conversation in that project inherits it.

2. **Claude Code / IDE integrations**  
   Products in the Claude ecosystem may load `SKILL.md`-style files from a repository or config directory (naming and paths depend on the product version—check Anthropic’s current documentation for “skills” or “plugins” for Claude Code).

3. **API**  
   There is no separate “skills API.” You send skill content as part of:
   - the **system** message, or  
   - an initial **user** turn (“Follow this skill: …”), or  
   - a **tool result** / RAG retrieval step that injects the skill text when relevant.

### With ArchiGPT / Ollama (this repo)

ArchiGPT sends a **fixed system prompt** from `com.archimatetool.archigpt/system-prompt.txt` plus the model XML and user request. It does **not** today load external skill files automatically. To “use a skill” with ArchiGPT you can:

1. **Edit the system prompt** — merge skill content into `system-prompt.txt` (good for behaviors that must always apply to ArchiMate JSON/XML).  
2. **Put skill text in the user message** — prepend “Follow these rules: …” in the ArchiGPT prompt box (good for experiments without redeploying the plugin).  
3. **Implement skill loading** — see [Implementing skills in this plugin](#implementing-skills-in-this-plugin-archigpt) below.

---

## How to implement a skill (format and content)

A minimal skill file should be readable by both humans and models.

### Suggested `SKILL.md` structure

```markdown
---
name: my-skill-name
description: One line—when the agent should read this skill.
---

# My skill title

## When to use
- User asks for …
- Files matching `**/*.archimate` …

## Instructions
1. …
2. …

## Constraints
- Do not …

## Examples
**User:** …  
**Expected:** …
```

Frontmatter (`---` blocks) is optional; some tools ignore it, others use it for discovery.

### Good practices

- **One primary purpose** per skill; split large topics into multiple skills.  
- **Explicit triggers** (“USE WHEN: …”) so the model knows when to apply it.  
- **Copy-pasteable examples** (especially for ArchiMate JSON shapes ArchiGPT expects).  
- **Keep skills in-repo** under something like `skills/my-skill/SKILL.md` so Cursor and code review see the same source of truth.

---

## Implementing skills in this plugin (ArchiGPT)

Today, behavior is centralized in **`system-prompt.txt`**. To add first-class skills:

### Option A — Concatenate skill files at build time

1. Add `com.archimatetool.archigpt/skills/*.md` (or a submodule path).  
2. In the Maven build, concatenate them into a generated resource, or keep them as separate bundle entries.  
3. At runtime, read all `*.md` under `skills/` and append to the system prompt string before calling Ollama (with a **size cap** so the context window is not blown).  
4. Optionally support **one active skill** via a preference in the ArchiGPT view (dropdown or text field).

### Option B — User-selectable skill in the UI

1. Add a **combo box or text field** “Skill preset: (none) | Recruitment diagram | Import hygiene | …”.  
2. Map each preset to a bundled `SKILL.md` resource or a file path the user configures.  
3. Append the selected skill body to the system prompt (or to the user message wrapper in `UserMessageBuilder`).

### Option C — Keep skills outside the plugin

Document in this repo: “Copy `skills/foo/SKILL.md` into Cursor rules or prepend to ArchiGPT prompt.” No Java changes; still valuable for consistency across the team.

### Option D — MCP vs skills

If you need **live data** (e.g. query Archi’s model via an API), use **MCP servers** or Archi scripting, not a Markdown skill. Skills teach *how* to interpret and respond; MCP *fetches* facts.

---

## Quick reference

| Mechanism            | Best for                                      |
|---------------------|-------------------------------------------------|
| Cursor skills/rules | IDE coding, refactors, repo-specific workflows |
| Claude project text | Long conversations, product/docs writing       |
| ArchiGPT system prompt | ArchiMate JSON/XML, diagram semantics        |
| MCP                 | Callable tools, live systems                   |
| Skill markdown in repo | Portable, reviewable, tool-agnostic        |

---

## Related files in this repository

- `com.archimatetool.archigpt/system-prompt.txt` — LLM system instructions for ArchiGPT  
- `docs/EXTERNAL_LLM_PLAN.md`, `docs/CONVERSATIONAL_CHAT.md` — broader LLM integration notes  

For Cursor-specific skill authoring steps, follow Cursor’s official **create skill** / **Agent Skills** documentation for your installed version, since install paths and discovery rules change over time.
