# Token-Oriented Object Notation (TOON) and modeling files (ArchiMate & BPMN)

This note explains **TOON** (Token-Oriented Object Notation), how it differs from the verbose XML formats used by **ArchiMate** and **BPMN** tools, and what adopting TOON *between* an editor plugin and an LLM would imply—without changing how `.archimate` / `.bpmn` files are stored on disk.

---

## 1. What TOON is

**TOON** is a compact, human-readable encoding of the **same logical data as JSON** (objects, arrays, primitives), designed for **LLM prompts** where token count matters.

- **Official project:** [TOON — Token-Oriented Object Notation](https://toonformat.dev/)  
- **Specification & ecosystem:** [toon-format/spec](https://github.com/toon-format/spec) and related implementations (TypeScript, Python, Go, Rust, .NET, etc.).

### 1.1 Design ideas that matter for modeling tools

| Aspect | Relevance |
|--------|-----------|
| **JSON-equivalent model** | You can represent any JSON-serializable view of a model; round-trip converters exist for JSON ↔ TOON. |
| **Tabular arrays** | Uniform arrays of objects with the same fields collapse to a **table**: field names declared once (`{field1,field2,...}`), then one row per line—similar to CSV but schema-aware. |
| **Explicit lengths `[N]`** | Arrays carry a declared length, which helps models parse reliably. |
| **Indentation over braces** | Less punctuation than JSON; fewer tokens for nested structure in many cases. |

Published benchmarks (see [toonformat.dev](https://toonformat.dev/)) report **lower token usage than JSON** on mixed-structure workloads, with competitive task accuracy—exact numbers depend on model and payload shape.

### 1.2 When TOON helps most

TOON tends to shine when the payload is dominated by **many homogeneous records** (e.g. hundreds of elements with the same columns). It helps less when the payload is **deeply nested, irregular, or mostly unique keys**—there, JSON or domain-specific XML might be similar or preferable.

### 1.3 Byte compression (gzip, zip, etc.) is not a substitute

Tools often ask whether they can **compress** JSON or XML before sending it to the LLM. For typical **chat completion** APIs, the answer for **token cost** is **no**:

- **gzip / deflate / zip** reduce **bytes** (disk, HTTP payload). The model, however, consumes **text tokens** from the **decompressed** string you place in `messages[].content`. After you decompress, the token count is essentially the same as for uncompressed pretty-printed text of the same data (minor differences only if the tokenizer treats rare byte sequences differently—which is not how you save money).
- **HTTP Content-Encoding** saves bandwidth between your client and the provider; it does **not** reduce the number of tokens charged for the prompt.
- Encoding binary compressed data as **base64** in the prompt **increases** size and usually **increases** tokens versus sending plain text.

What **does** lower token usage for the same information is changing the **actual text** of the prompt: **minification** (drop whitespace), **shorter or fewer keys**, **truncation and prioritization**, or a **denser encoding** such as TOON or hand-rolled tables—not wrapping the same verbose XML/JSON in a byte compressor.

---

## 2. “Between the plugin and the LLM”

ArchiGPT today builds **user context** largely from a custom **XML** serialization of the open ArchiMate model (`ModelContextToXml`), not from the raw `.archimate` file bytes. Conceptually:

```text
Editor model (in memory)  →  prompt text (XML + digest + user ask)  →  LLM
                                    ↑
                         TOON could live here as an alternative or supplement
```

Important separation:

| Layer | Typical format | Role |
|-------|----------------|------|
| **Persistence** | ArchiMate Open Exchange / Archi `.archimate`, BPMN 2.0 XML `.bpmn` | Canonical file format; interchange; tool ecosystem. |
| **LLM context** | XML, JSON, TOON, prose, etc. | Whatever compresses well and the model follows reliably. |
| **Structured LLM output** (e.g. import) | JSON / XML shaped to your parser | Must match `ArchiMateLLMResult` and validators in this plugin unless you extend them. |

**TOON does not replace** `.archimate` or `.bpmn` storage. It is a **transcoding target for prompts** (and optionally for model replies, if you add a TOON → result parser).

---

## 3. ArchiMate files and TOON

### 3.1 What an ArchiMate file “is”

In practice you see:

- **Archi** projects: `.archimate` (XML-based, aligns with ArchiMate concepts, folders, views).
- **Interchange:** The Open Group **ArchiMate Model Exchange Format** (XML namespaces, `archimateModel`, elements, relationships, views/diagrams).

Those formats are **verbose**: repeated tags, namespaces, and attribute names cost tokens when pasted wholesale into prompts.

### 3.2 Mapping ArchiMate → JSON-shaped view → TOON

A practical approach is a **lossy or lossless projection** to JSON first, then **encode as TOON** for the user message:

1. **Elements** — tabular: `id`, `type`, `name`, `folder` or `layer`, optional `documentation`.
2. **Relationships** — tabular: `id`, `type`, `name`, `source`, `target`.
3. **Views / diagrams** — either separate tables (`viewId`, `elementRef`, `x`, `y`, `w`, `h`) and connection rows, or nested objects if the TOON/JSON mix stays readable.
4. **Digest** — keep a short summary object (counts, layer breakdown) as today’s plain-text digest, or fold it into the same TOON document as a small header object.

**Effects:**

- **Token savings:** Often large for big models, especially if XML attribute names and closing tags dominated the prompt.
- **Semantic fidelity:** You may **omit** diagram layout, styling, or rarely used attributes to save tokens; that is a **product choice** (analysis vs. “exact canvas replay”).
- **Model competence:** Models see **less standard** ArchiMate XML; you should document the TOON/JSON field meanings in the system prompt and, if needed, give short examples. For **import**, keeping the assistant’s **output** in the existing JSON/XML contract avoids rewriting `ArchiMateLLMResultParser` and the importer.

### 3.3 ArchiGPT-specific note

Current code paths assume **XML context** for analysis and capped serialization. Introducing TOON would mean:

- A serializer: `IArchimateModel` → JSON view → TOON string (likely via an official or embedded TOON library, or a minimal encoder if you only support a fixed schema).
- Prompt and system prompt updates so the model knows how to read TOON.
- Optional: dual mode (XML | TOON) behind a preference or heuristic (e.g. TOON only above a size threshold).

---

## 4. BPMN files and TOON

### 4.1 What a BPMN file “is”

**BPMN 2.0** interchange is usually **XML** (`definitions`, `process`, `task`, `sequenceFlow`, `gateway`, events, `bpmnDiagram` / DI layout in the same or companion file). Examples: Camunda, Activiti, many editors export `.bpmn` / `.bpmn20.xml`.

Same story as ArchiMate: **excellent for tools**, **heavy for LLM context** when inlined verbatim.

### 4.2 Mapping BPMN → TOON

Typical projections:

| Table / object group | Example columns or keys |
|----------------------|-------------------------|
| **Participants & processes** | `id`, `name`, `isExecutable` |
| **Flow nodes** | `id`, `type` (e.g. `userTask`, `exclusiveGateway`), `name`, `processRef` |
| **Sequence flows** | `id`, `name`, `sourceRef`, `targetRef` |
| **Messages / signals** (if needed) | `id`, `name` |
| **Diagram layout** (optional) | `bpmnElement`, `waypoint` lists—or omit for token savings |

Encode the result as JSON, then TOON, using **tabular arrays** for long lists of tasks and flows.

### 4.3 ArchiMate vs BPMN in one pipeline

If a product ever combined **both** (e.g. high-level capability map in ArchiMate and process detail in BPMN):

- Use **separate top-level keys** in one JSON document (`archiMate`, `bpmn`) before TOON encoding, or two TOON sections in one prompt.
- Relationships across notations (e.g. “process X realizes capability Y”) need an explicit **crosswalk table** (`archiId`, `bpmnId`, `relationType`)—not implied by either file alone.

This plugin today is **ArchiMate-centric**; BPMN appears here only as a **parallel example** of how TOON would apply to another XML-heavy modeling standard.

---

## 5. Risks and mitigations

| Risk | Mitigation |
|------|------------|
| Model misreads custom tables | Keep schema stable; use `{fields}` headers; include 1–2 short examples in the system prompt; validate lengths `[N]`. |
| Loss of detail when slimming | Document what is dropped; offer “full XML” or “TOON compact” modes. |
| Two formats for import/export | Prefer TOON **only for inbound context**; keep structured **assistant output** in the format your importer already expects unless you invest in a full TOON reply parser. |
| Maintenance | Depend on the [official TOON spec](https://github.com/toon-format/spec) and a maintained encoder/decoder rather than inventing a one-off “TOON-like” syntax. |

---

## 6. Summary

- **TOON** is a **token-oriented, JSON-equivalent** notation with **tabular arrays** and explicit structure—well suited to **LLM prompts**, not a replacement for ArchiMate or BPMN **file** formats. It addresses **token count in the prompt text**, which **byte-level compression (gzip, etc.) does not** for standard chat APIs (see §1.3).
- **ArchiMate** (`.archimate` / exchange XML) and **BPMN** (`.bpmn` XML) stay as they are on disk; you **project** selected facts into JSON and then **TOON** for the LLM.
- **ArchiMate:** natural fit for element/relationship/view tables plus a digest.
- **BPMN:** natural fit for process, node, and sequence-flow tables; layout optional.
- For **ArchiGPT**, TOON would primarily **replace or complement XML in the user message**, while structured **apply-to-model** responses can remain JSON/XML until you deliberately extend the stack.

---

## References

- [TOON — Token-Oriented Object Notation](https://toonformat.dev/)  
- [toon-format/spec (GitHub)](https://github.com/toon-format/spec)  
- [ArchiMate Exchange Format](https://www.opengroup.org/xsd/archimate) (overview via Open Group)  
- [BPMN 2.0 specification](https://www.omg.org/spec/BPMN/) (OMG)
