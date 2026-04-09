# Language models and ArchiGPT capability

This note explains how **choosing a stronger model** and **running it on better local hardware** changes what you can expect from ArchiGPT—not as a benchmark sheet, but in terms of **context**, **reasoning**, **reliability**, and **workflow** (single request vs chunked analysis).

ArchiGPT today talks to **local Ollama** over HTTP: each “Ask” is essentially **one chat completion** (system prompt + user message that includes ArchiMate XML and your question). See [`EXTERNAL_LLM_PLAN.md`](EXTERNAL_LLM_PLAN.md) for how **cloud APIs** would fit the same flow later.

---

## 1. What actually limits the tool today

Several things interact:

| Constraint | Effect on ArchiGPT |
|------------|-------------------|
| **Effective context window** | How much **system prompt + XML + your text** fits in one request. Larger models and higher `num_ctx` allow **more of the model in one shot**, which reduces reliance on **chunked analysis** (many round-trips for one question). |
| **Instruction following** | The model must respect ArchiMate rules in the system prompt: return **structured CHANGES JSON** when editing, or **plain analysis** when asked to analyse—not the other way around. Stronger models fail this less often. |
| **Long-document reasoning** | Even inside the context window, small models may **lose track** of IDs, layers, or diagram structure across tens of thousands of characters of XML. |
| **Local inference speed** | Large quantised models on CPU or a modest GPU can feel “stuck”; you may lower `num_ctx` or see timeouts (tunable via JVM properties—see `LlmContextConfig` in the source). |
| **Plugin behaviour** | For very large exports, the plugin may **split XML by folder/view** (and for analysis, call Ollama **once per chunk**). That **works around** a small context window but costs **latency** and can dilute global reasoning unless you consolidate answers yourself. **Multi-megabyte** enterprise files are **common** once many views exist—see **§2**. |

So “a more capable model” usually means: **more accurate and coherent behaviour on the same payload**, and/or **room for a larger payload**, which can **reduce chunking** and **improve holistic answers**.

---

## 2. Typical size of enterprise models (ArchiMate exchange XML)

ArchiGPT sends **serialized ArchiMate content** (Open Exchange–style XML from Archi). There is no single “enterprise standard” file size—**diagram-heavy** exports dominate disk and token use.

**What drives size**

- **Elements and relationships** (the “catalog”): relatively **compact** per item (ids, types, names, properties).
- **Views / diagrams**: usually the **largest** part—every node, connection, label, and **layout bounds** is XML. A model can be “big” because of **many detailed views**, not only because the element count is huge.
- **Documentation, organizations, metadata**: can add substantially when used heavily.

**Rough ballparks for a single monolithic** `.archimate` **or exchange export** (order-of-magnitude, not a standard):

| Situation | Typical file size (indicative) |
|-----------|--------------------------------|
| Small scope (one domain, few views) | **~100 KB – 2 MB** |
| Medium enterprise (portfolio + a fair number of views) | **~2 – 15 MB** |
| Large organization (central repository, many views, rich diagrams) | **~15 – 80+ MB** |
| Extreme (everything in one file, very diagram-heavy) | **100 MB+** is possible but less common |

**Why this matters for LLMs**

- As **text**, about **1 MB** of XML is often on the order of **~1 million characters** (mostly ASCII). A **20 MB** export is already **tens of millions of characters**—far beyond a **single** typical local context window, so ArchiGPT’s **chunking** and **XML caps** exist for **real** enterprise files, not only edge cases.
- Many organizations **split** work across **several models** (by domain, programme, or bounded context), which keeps **per-file** sizes smaller than one giant repository dump.
- **Export scope** (which layers and views you include) changes size more than **headcount** alone.

---

## 3. Hardware: M2 Pro (32 GB) vs a higher-tier Mac with lots of memory

This is **not** about Ollama “remembering” old chats (each request is stateless in the current integration). It is about **whether your machine can run the model size and context you ask for at acceptable speed**.

**Unified memory** on Apple Silicon holds **weights** and, during a request, **activations and KV cache** (roughly: memory that grows with **sequence length** and **model width**). If you push **very large `num_ctx`** or a **very large** quantised model:

- On **M2 Pro with 32 GB**, you may hit **memory pressure** or **heavy swapping**, or Ollama may **fall back to slower execution** so that large-context requests look hung. The plugin even **caps** inferred context by default because reported max context (e.g. 128K+) is often **unwise** to allocate every time on typical desktops.
- On a **newer SoC with more RAM** (e.g. your example of an **M5 Pro-class machine with a large unified memory pool**), you typically get **more headroom** to run **bigger checkpoints** (e.g. 70B-class quantisations) and/or **wider effective context** without thrashing, **and** newer generations usually improve **memory bandwidth** and **Neural Engine / GPU** throughput for local inference.

**Practical takeaway:** the same **Ollama model name** can feel like a **different tool** on a machine that can sustain **higher context and larger weights** without swap-bound latency.

---

## 4. Local models via Ollama (examples)

Ollama pulls **open-weights** (or otherwise locally runnable) checkpoints; **quality, context, and RAM use** depend on the **exact variant** (parameter count, quantisation, and family).

**Illustrative patterns** (names and availability change over time; check [ollama.com/library](https://ollama.com/library)):

| Style | Examples you might see in Ollama | What usually improves capability |
|-------|----------------------------------|----------------------------------|
| **Compact instruction-tuned** | e.g. `llama3.2`, `mistral`, smaller **Qwen** variants | Fast, low RAM; best for **small models** or tight machines. Weakest on **huge XML** and subtle ArchiMate edits. |
| **Mid-size chat models** | e.g. **Llama 3.x** medium tiers, **Qwen2.5** mid sizes, **Mistral** family | Better **JSON/XML discipline** and analysis; still may need **chunking** on big enterprise models. |
| **Large reasoning-oriented** | e.g. **DeepSeek-R1**-style, large **Qwen** / **Llama** | Stronger **multi-step** reasoning; **much** heavier RAM/GPU; best on a **high-memory** Mac if you want it local. |

**Moving from a small to a large local model** on Ollama tends to:

- Improve **CHANGES JSON** validity and **analysis** that references the right elements and views.
- Allow you to **raise** `archigpt.ollamaNumCtx` / XML caps (carefully) so **more diagram + folder XML** fits in **one** request.
- Still require **chunked analysis** for **very** large `.archimate` exports (**§2**) unless you invest in **both** a huge context model **and** enough RAM to run it.

---

## 5. Cloud APIs: OpenAI, Google Gemini, and Anthropic (examples)

These are **not** wired into ArchiGPT by default; they illustrate what people mean by “more capable model” when **local RAM is not the bottleneck**.

### OpenAI (API)

Examples of families people use for tooling:

- **GPT-4o** / **GPT-4.1**-class multimodal chat models: strong **instruction following** and **long-context** options for **single-shot** “here is my model XML” workflows.
- **Reasoning-oriented** lines (e.g. **o-series**): can be **slower and pricier**; useful when you want **deliberate** consistency on complex edits (still bounded by API **context limits** and **cost**).

**Trade-offs:** ongoing **cost**, **data handling** (your architecture XML leaves your estate), and **integration work** (see `EXTERNAL_LLM_PLAN.md`).

### Google (Gemini API)

- **Gemini 4** (and neighbouring generations in the **Gemini** flagship line): typically strong **multimodal** and **long-context** APIs for “whole artefact + instruction” workflows. For ArchiGPT-style use, that means room to ship **large ArchiMate XML** in fewer pieces and to ask for **strict structured output** (CHANGES JSON) with a model tuned for **tooling and agents**. Exact **model IDs**, **context limits**, and **pricing** change as Google ships updates—check the current [Gemini API](https://ai.google.dev/gemini-api/docs) documentation when integrating.
- **Related open weights:** Google’s **Gemma** line (e.g. **Gemma 4**) is a separate **open-model** family distilled from the same research stack; those checkpoints may show up in **Ollama** for **local** runs, while **Gemini 4** itself is aimed at **cloud** access through Google’s developer and enterprise surfaces.

**Trade-offs:** same class as other clouds: **credentials**, **billing**, **data residency / governance**, and **plugin work** (Gemini uses its own request shape; see the Google AI section in `EXTERNAL_LLM_PLAN.md`).

### Anthropic (API)

Examples:

- **Claude Sonnet** / **Opus** (and newer generations as they ship): often very strong on **long documents** and **structured output** when prompted carefully.
- **Mythos:** Anthropic positions **Mythos** as a **frontier** Claude tier—**above Opus** for the hardest **reasoning**, **coding**, and **long-context** tasks when your account and the API expose it. For ArchiGPT, that is the “if cost and policy allow, send **more XML at once** and expect **fewer dumb mistakes** on relationships and views” end of the spectrum. Availability, **pricing**, and **rate limits** are stricter than Sonnet; treat it like **premium batch** or **high-stakes review** rather than every keystroke.
- **Very large context windows** (e.g. **200k token** class on supported models, including many Claude tiers) matter because **full serialised models** are often **multi-megabyte** (see **§2**); even **200k tokens** may not hold the **largest** monolithic exports, but they still **reduce chunking** compared with typical **8k–32k** local setups.

**Trade-offs:** same as OpenAI: **keys**, **billing**, **governance**, and **client implementation** in the plugin. **Mythos** in particular adds **higher cost** and sometimes **gated access**.

---

## 6. How capability shows up in ArchiGPT features

- **Describe / analyse**  
  Stronger models produce **more accurate** summaries, impact views, and trade-off discussions, especially when the XML chunk contains **many elements** or **cross-layer** relationships. With **small** models or **tiny** context, answers may **hallucinate** missing parts or confuse **similar names**; **chunked** analysis mitigates size but asks the model to reason **per excerpt**.

- **Change requests → CHANGES JSON**  
  Reliability of **valid JSON**, correct **IDs**, and **sensible diagram operations** generally **tracks model quality**. Larger local models or frontier APIs usually **reduce** “almost right” edits that fail validation or import.

- **Full model export in the prompt**  
  The more **tokens** you can feed **once**, the more the model can relate **views** to **folder** content without you manually splitting the question. That is where **big context** (cloud or local with enough RAM) matters most. For **multi-megabyte** real-world models (**§2**), expect **chunked** analysis or **narrower exports** unless you use a **very large** context API and policy allows sending the whole file.

- **Latency and iteration**  
  **Chunked** analysis means **N** sequential Ollama calls for one question. A **faster machine** or **smaller/faster model** reduces wall-clock time; a **smarter single-pass** model reduces **N**.

---

## 7. Practical guidance

1. **Local path (Ollama):** Prefer a **model family known for instruction-following and JSON**; increase **RAM/SoC tier** if you want **larger** checkpoints or **higher `num_ctx`** without timeouts.  
2. **Tuning:** Use documented JVM properties (`archigpt.ollamaNumCtx`, `archigpt.maxXmlChars`, `archigpt.ollamaReportedCtxCap`, etc.) **together** with what your Mac can sustain—**reported** max context from `/api/show` is not always a **good** request size. Relate caps to **real file sizes** (**§2**): a **10 MB** XML payload is already **huge** in **token** terms.  
3. **Cloud path:** **OpenAI**, **Google Gemini** (e.g. **Gemini 4**), and **Anthropic** (**Sonnet** / **Opus** / **Mythos** where available) can jump **reasoning and context** ahead of what is comfortable locally, at the cost of **integration**, **money**, and **sending model data** off-device.  
4. **Model scope:** If one exchange file is **very large**, consider **smaller exports** (fewer views, split models) before chasing only a bigger LLM—**§2**.  
5. **Expectations:** No model eliminates the need for **clear prompts** and **review** of generated changes before import; stronger models mainly **raise the success rate** and **reduce second-guessing**.

---

## 8. Summary

**Moving to a more capable language model** improves ArchiGPT mainly through **better reasoning**, **better adherence** to ArchiMate output rules, and (when context and hardware allow) **more XML in one request**, which **reduces chunking** and **whole-model confusion**. **Enterprise exchange files** are often **multi-megabyte** once views are included (**§2**), so **chunking** and **context limits** are normal—not only theoretical. **Upgrading from an M2 Pro 32 GB to a much higher-memory Apple Silicon machine** is less about “Ollama remembering” and more about **actually running** larger models and **larger effective context** at usable speed—so the **same plugin** can behave closer to what you would expect from **frontier API** models (**Gemini 4**, **Claude Mythos**, and similar), still entirely **on your desk** if you use Ollama.
