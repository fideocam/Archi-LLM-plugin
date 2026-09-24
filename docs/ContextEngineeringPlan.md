# Context engineering for ArchiGPT (issue 22)

This is an implementation plan for GitHub **[#22 Context improvement](https://github.com/fideocam/Archi-LLM-plugin/issues/22)**. It is a design note, not implemented work.

The issue names three capabilities as the MVP of **context engineering for enterprise architecture**:

1. **AGR** — Architecture Graph Retrieval  
2. **Context compression**  
3. **ADR memory** — Architecture Decision Record memory  

Those three should deliver most of the **token savings** while making analyses feel **more architecture-aware** than “dump XML and hope.” The other items in the issue (scope modes, indexing, sub-agents, routing, …) come after the MVP unless a small piece is already almost done.

Related current behaviour: the plugin already **caps XML**, **prioritises the open view**, **chunks analysis by folder/view**, and injects a **model digest** plus **one tool skill**. That is a start. It is still mostly **push the file at the model**, not **retrieve a graph, compress it, and remember decisions**.

Constraints that stay true for this plan: **JavaSE-1.8**, local **Ollama** first, **echo-gap discipline** (stop if the pattern is not in this model or in stored ADRs — do not score against an external checklist), no silent inventing of hosts, products, or views.

---

## Why this matters

Enterprise `.archimate` files are often **multi-megabyte** once views exist (`docs/languagemodel.md`). On an 8 GB local Ollama box, **`num_ctx` is the RAM**, not the skill text. Sending more XML does not make the answer better if the relevant subgraph was already in the first 4k characters of a well-chosen excerpt.

Today a typical request is:

```text
system-prompt.txt (~30 KB, every call)
  + optional tool skill (~1–2 KB)
  + optional CatalogHygiene findings
  + model digest (chunked analysis)
  + Open Exchange–style XML (budgeted from num_ctx, or N folder/view chunks)
  + user prompt
```

Problems:

- **Wrong slice:** folder/view chunks are size-based, not “everything 1–2 hops from this ApplicationComponent.”
- **Repeated bulk:** the same verbose XML tags go out on every turn and every chunk.
- **No memory of decisions:** last week’s “we standardised on PostgreSQL here” lives only in the modeller’s head or in documentation the LLM never sees.

AGR + compression + ADR memory attack those three directly.

---

## Current state (do not rebuild)

| Piece | Where | What it already does |
|-------|--------|----------------------|
| XML budget | `LlmContextBudget`, `LlmContextConfig` | Size XML from `num_ctx` minus system prompt, wrappers, reply reserve |
| View-first serialize | `ModelContextToXml` | Open/selected/named diagrams first when capped |
| Chunked analysis | `ModelContextChunkPlanner`, `ModelXmlChunker` | Up to 12 folder/view (or string-split) passes; digest on each |
| Digest | `ModelContextDigest` | Counts, diagram names, type histogram — not a graph |
| Tool skills | `PromptLibrary`, `skills/` | One skill per Tools run, 8k char cap |
| Hygiene inventory | `CatalogHygiene` | Unused elements/relationships in Java so the LLM need not scan truncated XML |
| Selection | `SelectionContextBuilder` | Folder / element / view name in the user message |
| TOON note | `docs/TOON_ARCHIMATE_BPMN.md` | Denser prompt encoding is a **compression** option, not a file format change |
| System prompt trim | `docs/SYSTEM_PROMPT_REFACTOR.md` | Token savings on the **always-on** message |

MVP should **compose with** these, not replace the importer, validator, or echo-gap skills.

---

## MVP definitions

### 1. AGR — Architecture Graph Retrieval

**Meaning here:** given the user prompt, selection, open view, and (later) ADRs, **walk the live ArchiMate graph** and emit **only the subgraph that can answer the question**.

Not a vector database in v1. The model *is* the index: EMF ids, types, names, folders, views, and relationships are already in memory.

**Retrieve, then serialize.** Do not serialize the whole estate and hope the LLM finds the slice.

Suggested retrieval policies (first ones to ship):

| Policy | Use when | Walk |
|--------|----------|------|
| **This view + catalog peers** | Open diagram / “this view” / most Tools tasks | Figures and connections on the view, plus catalog elements of the same types that the view already uses (for echo-gap peers) |
| **Ego network** | Named or selected element | The element, incident relationships, 1-hop (optionally 2-hop) neighbours, and views that already show them |
| **Named concept + overlapping views** | Prompt names a view or element | That concept, other views that contain it, shared relationships |
| **Folder subtree** | Folder selected, no diagram | That folder’s elements/rels only (today’s chunk, but as the *only* payload, not chunk 3 of 12) |

**Hard rules:**

- Always include **ids, types, names** so CHANGES JSON can round-trip.
- Never invent nodes that were not retrieved; if a hop is missing, say so.
- Cap the subgraph (e.g. N elements / M relationships / K views) and report **what was excluded**.
- Echo-gap tools should retrieve **peers on the same view or folder**, not the whole Applications folder, unless the user asked for the estate.

**Java sketch (v1, in-process):** `ArchitectureGraphRetriever` taking `IArchimateModel` + `RetrievalRequest` (seed concepts, policy, hop limit, caps) returning `RetrievedGraph`, then `ModelContextToXml` (or a compact writer) serializes **that** graph only.

Deterministic. No embedding model required. Testable with the same factory-built models as `CatalogHygieneTest`.

### 2. Context compression

**Meaning here:** after retrieval (and ADR injection), **shrink the text** the LLM actually tokenizes. Byte gzip does **not** help token cost (`docs/TOON_ARCHIMATE_BPMN.md`).

Layers, cheapest first:

| Layer | Action | Expected saving |
|-------|--------|-----------------|
| **A. Stop sending unused bulk** | AGR so XML is the subgraph | Largest; often 10× on a fat file |
| **B. Compact serialization** | Drop unused attributes; short tags or tables (`id,type,name,src,tgt`); optional TOON | Medium; same facts, fewer tokens |
| **C. System prompt** | Deduplicate per `SYSTEM_PROMPT_REFACTOR.md`; keep CHANGES schema once | Medium; every request |
| **D. Skill + digest** | Already capped; digest stays short; do not concatenate the whole Tools catalog | Small |
| **E. Optional LLM compressor** | Extra Ollama pass to summarise the subgraph | Last resort; latency and drift risk on 8 GB |

**v1 should be A + B + C.** Do not add a second local model for compression until AGR is in.

Keep **assistant output as today’s JSON**. Compress **inbound context** only.

Debug tab should show: retrieval policy, subgraph counts, uncompressed vs compressed char counts, `num_ctx`.

### 3. ADR memory

**Meaning here:** persist **architecture decisions** as first-class context, retrieve the few that match this question, and inject them **after** the subgraph and **before** the user ask.

An ADR here is not a TOGAF essay. A useful record is short and bound to model ids:

```text
id: adr-004
status: accepted
date: 2026-09-01
title: Customer master data lives in CRM, not in billing
relates: id-… (ApplicationComponent CRM), id-… (DataObject Customer)
applies-to-views: Current Mode of Operations
decision: Billing Accesses CRM’s Customer DataObject; do not duplicate a second Customer object in billing.
consequences: echo-gap “uneven data” should treat CRM as the convention for Customer.
```

**Store (v1):** Markdown (or YAML-in-Markdown) next to the `.archimate` file, e.g. `docs/adr/` or `.archigpt/adr/`. Optional later: Archi **Driver / Requirement / Constraint / Grouping** with a property `archigpt.adr=true` so decisions live in the model.

**Retrieve (v1):** keyword + related-id overlap with the AGR subgraph (and prompt tokens). Inject **top K** (e.g. 3) with a hard char cap. If none match, send nothing — do not dump the whole ADR folder.

**Write path (later in MVP, not day one):** an analysis-only tool “Propose ADR from this answer” that drafts a file; the human saves it. Do **not** auto-write decisions from every chat.

**Echo-gap interaction:** ADRs are the **company convention** when the working file has not echoed the pattern yet. That is the same idea as `docs/CompanyMetamodel.md`, but **lightweight and per-programme**, not a full reference model.

---

## How the three compose (one request)

```text
1. Seeds   = selection + open view + names in the prompt
2. AGR     = subgraph from the live model (policy + caps)
3. ADRs    = records whose relates-ids intersect that subgraph
4. Compress = compact serialize(subgraph) + short ADR block
5. Pack    = system prompt + one skill + pack + user ask
6. Budget  = if still over XML/token budget, drop lowest-priority hops / extra views, never drop seeds
```

Chunked analysis remains a **fallback** when the subgraph still cannot fit (rare if AGR is honest). Prefer **one** Ollama call with a tight pack on 8 GB machines.

---

## Phased plan

### Phase 0 — Measure (short)

- Debug already shows `num_ctx` and XML length. Add **pack accounting**: system chars, skill chars, digest chars, XML chars, user chars.
- Log (Debug only) whether the request was **full model**, **capped XML**, or **chunked**.
- Capture 2–3 real files (e.g. Tietoarkkitehtuuri-sized) as fixtures for “chars before / after AGR.”

Done when we can say “this Tools run sent X characters, Y% was XML.”

### Phase 1 — AGR v1 (core of the MVP)

- `RetrievalRequest` + policies: this-view, ego-1-hop, folder.
- Serialize retrieved elements, relationships, and only views that contain them (or the open view).
- Settings: **Context pack: Full (today) | Retrieved subgraph** defaulting to subgraph when a view or element is in scope; full file remains an escape hatch.
- Tests: factory models with two views; ego retrieval must not include the unrelated view’s unique elements.
- Tools echo-gap skills keep working: they still see **peers**, which AGR must include when the policy is this-view.

Done when a “describe this application” with an element selected no longer ships the whole Applications folder.

### Phase 2 — Compression v1

- Compact writer (tables or minified XML/JSON) behind the same `RetrievedGraph`.
- Apply the system-prompt dedupe from `SYSTEM_PROMPT_REFACTOR.md` (can ship independently).
- Optional TOON as a **second pack format** behind a Settings flag once compact XML/JSON is proven; do not block AGR on TOON.

Done when the same subgraph uses clearly fewer characters with unchanged import/analysis quality on fixtures.

### Phase 3 — ADR memory v1

- Load `*.md` from a configurable folder (default: `.archigpt/adr` beside the model or workspace).
- Match on ids present in the subgraph + title tokens.
- Inject `--- ADR MEMORY ---` block, capped.
- One analysis tool: **Relevant ADRs for this view** (report-only).
- Tests: ADR mentioning component A is injected for ego(A) and not for an unrelated component.

Done when a second session can see “we already decided X” without pasting the decision into the prompt.

### Phase 4 — Pack manager (thin)

A single `ContextPackBuilder` that applies budget: seeds first, then 1-hop, then extra views, then ADRs, then documentation fields. This is the issue’s **Context Budget Manager**, but only after 1–3 exist so there is something to budget.

---

## Later enhancements (not MVP)

Ship these only when the pack is stable. Several already have a foothold.

| Issue 22 item | Relation to MVP | Notes |
|---------------|-----------------|--------|
| **Scope-limited analysis modes** | Thin UI on AGR policies | Combos: This view / This element + hops / This folder / Whole model. Tools roles already narrow *instructions*; this narrows *data*. |
| **Repository instructions** | Like Cursor rules for a model repo | e.g. `.archigpt/INSTRUCTIONS.md` always prepended (capped). Overlaps ADR memory; keep instructions *how to analyse*, ADRs *what we decided*. |
| **Architecture indexing layer** | Speed / name search on huge models | v1 AGR walks EMF. An index (name, type, view membership) is an optimisation when `eAllContents` is too slow — measure first. |
| **Context budget manager** | Phase 4 | Exists in primitive form as `LlmContextBudget`; extend to subgraph + ADR slices. |
| **Smart retrieval policies** | Extra AGR policies | e.g. “same specialization”, “same plateau”, “serving stack of this Business Service.” Add per tool skill (echo-gap already implies a policy). |
| **Specialized sub-agents** | After pack quality | Separate Ollama calls: retriever (maybe extractive, no LLM) vs analyst vs JSON importer. On 8 GB, extra calls are expensive; AGR should stay **Java**, not a sub-agent. |
| **Automatic architecture snapshots** | ADR/history | Digest + subgraph hash after import; optional `snapshots/` for “what changed since last ask.” Complements conversational chat (`docs/CONVERSATIONAL_CHAT.md`) without storing full XML each turn. |
| **Planning + execution pattern** | Analysis then CHANGES | First call: plan (ANALYSIS). User confirms. Second: CHANGES JSON. Fits echo-gap + the recent open-view import fixes. Do not auto-apply plans. |
| **Multi-model routing** | `docs/EXTERNAL_LLM_PLAN.md` | Small local model for compression/retrieval-ranking; stronger model for CHANGES. Only after a second provider exists. Do not require two Ollama models on 8 GB for MVP. |

---

## Non-goals for MVP

- Vector embeddings or an external RAG server.
- Replacing Archi as the system of record.
- Auto-generating ADRs from every answer.
- Compressing canonical `.archimate` on disk.
- Sending the whole Tools catalog in one prompt.
- Scoring models against ISO/CIS/TOGAF checklists (echo-gap + ADRs only).

---

## Suggested first vertical slice

One user-visible path, one Settings switch, tests:

1. User has **Current Mode of Operations** open and asks to add missing Data Objects (issue 20/21 class of request) **or** runs a Technology architect echo-gap.
2. AGR policy **this-view + catalog peers of types already on the view**.
3. Compact serialize that subgraph (even if still XML).
4. If `.archigpt/adr/` exists, inject matching ADRs.
5. Debug shows pack sizes.
6. Fallback: current capped/chunked XML if retrieval returns nothing (should not happen when a view is open).

That slice proves token savings and architecture-awareness without sub-agents or cloud routing.

---

## Test plan (when implementing)

- **Retriever:** two views, shared vs unique elements; hop limits; caps and exclusion note.
- **Compression:** same graph, fewer chars; parser/importer still round-trips ids.
- **ADR:** match by id; no match → empty block; cap respected.
- **Regression:** `ArchiMateImportFlowTest`, chunked analysis tests, Tools catalog tests — full-model escape hatch still works.
- **Manual:** 8 GB Ollama, default `num_ctx` 8192–16384, open one view on a large file; request should be **one** call, not 12 chunks.

---

## Related documents

- `docs/languagemodel.md` — why enterprise XML is large and why `num_ctx` is RAM  
- `docs/TOON_ARCHIMATE_BPMN.md` — inbound compact encoding  
- `docs/SYSTEM_PROMPT_REFACTOR.md` — always-on token cost  
- `docs/CONVERSATIONAL_CHAT.md` — history vs pack; snapshots later  
- `docs/CompanyMetamodel.md` — ADRs vs a living company reference model  
- `docs/EXTERNAL_LLM_PLAN.md` — multi-model routing later  
- `docs/Skills.md` — tool skills stay one-at-a-time  
