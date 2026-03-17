# Refactoring the System Prompt for Efficiency

This document outlines how to make the ArchiGPT system prompt **as efficient as possible** (fewer tokens, faster and cheaper inference, more room for model context) while keeping behaviour correct.

---

## 1. Current issues

- **Length:** The prompt is ~3,200+ words. It consumes a large share of the context window on every request.
- **Repetition:** The same rules appear in multiple places:
  - “Include `diagram` only when user asks for a **new** view” appears in ADD TO EXISTING, NEW DIAGRAM, CHANGES, and WRONG vs RIGHT.
  - “Remove from diagram only” vs “remove from model” is explained in full twice (REMOVE FROM DIAGRAM ONLY and in CHANGES REMOVE).
  - ID format (“id-” + 32 hex) is stated in IDENTIFIER FORMAT and again in WRONG vs RIGHT.
  - “Use supplied XML / no duplicates / no hallucinations” appears in ANALYSIS and in CHANGES.
  - “Describe the view” = whole diagram, not just selected element is in DESCRIBE THE VIEW and again in ANALYSIS.
- **Long paragraphs:** Many rules are in single long sentences. Bullets and tables are easier to scan and often use fewer tokens.
- **Redundant examples:** Multiple near-identical examples (e.g. several “remove” JSON examples). One canonical example per pattern is enough.
- **Verbose phrasing:** “When the user asks to X, you MUST respond with Y” can become “X → Y” or “If X: respond with Y.”

---

## 2. Refactoring strategy

### 2.1 One-time consolidation (deduplicate)

- **Single “routing” section:** One place that defines: user intent → response type (ANALYSIS = plain text, EXPORT = full model XML, CHANGES = JSON). Action words (add, create, remove, delete, …) → CHANGES. Describe/analyze/explain (no action) → ANALYSIS. “Return/export model” → EXPORT.
- **Single “diagram” rule:** State once: “Include `diagram` only when the user explicitly asks for a **new** view/diagram. If they ask to add to an existing view (by name or selection), omit `diagram`.” Remove this from ADD TO EXISTING, NEW DIAGRAM, and CHANGES; keep one short subsection under CHANGES.
- **Single “remove” rule:** One block: (1) Remove from **diagram only** (“from the diagram”, “from this view”) → `removeElementFromDiagramIds` / `removeRelationshipFromDiagramIds`. (2) Remove from **model** (“delete”, “remove” without “from the diagram”) → `removeElementIds` / `removeRelationshipIds`. (3) Remove **diagram** (“delete this view”, “remove the X diagram”) → `removeDiagramNames`. One short example each.
- **Single ID rule:** “All ids: `id-` + 32 hex (no hyphens). No placeholders. Same id in `elements[].id` and `diagram.nodes[].elementId`.” Remove the second occurrence in WRONG vs RIGHT.
- **Single “describe view vs element” rule:** “If user says ‘describe the view’ / ‘what is in the view’, describe the **whole diagram** (use XML to find which diagram contains the selected element). Only describe the single element if they say ‘this element’ / ‘describe this element’.” State once; remove from DESCRIBE THE VIEW and shorten ANALYSIS.

### 2.2 Replace prose with structured blocks

- **Routing table (compact):**

  | User intent | Response |
  |-------------|----------|
  | add, create, generate, make, insert, remove, delete, or “new diagram/view” | CHANGES (JSON only) |
  | describe, analyze, explain, review (no action verb) | ANALYSIS (plain text) |
  | return/export model as XML | EXPORT (full Open Exchange XML) |

- **CHANGES optional keys (one line):**  
  `elements`, `relationships`, and optionally: `diagram`, `removeElementIds`, `removeRelationshipIds`, `removeDiagramNames`, `removeElementFromDiagramIds`, `removeRelationshipFromDiagramIds`, `error`.

- **Diagram rule (bullets):**
  - New diagram with content → include `elements`, `relationships`, **and** `diagram` with `name`, `nodes` (one per element), `connections`.
  - Add to existing view → `elements` + `relationships` only; no `diagram`.
  - `diagram.nodes` only reference ids from this response’s `elements` (or existing model only if user asked to “include existing X on the new diagram”).

### 2.3 Shorten lists and examples

- **Element/relationship types:** Instead of listing every type in the prompt, write: “Use only ArchiMate 3.2 element and relationship types (see spec). Examples: BusinessActor, BusinessProcess, ApplicationComponent, Node; ServingRelationship, AssignmentRelationship, FlowRelationship. Never use type View, Diagram, or Connection.” Optionally link to the XSD or a one-line list in the repo. This saves hundreds of tokens.
- **Fragment list (process, service, etc.):** One line: “For process, service, capability, component, architecture, view: return multiple related elements + relationships (and `diagram` only if new view requested).”
- **One example per pattern:** One CHANGES example (e.g. one element + one relationship + minimal diagram). One remove-from-model example. One remove-from-diagram example. One remove-diagram example. Drop the rest.
- **WRONG vs RIGHT:** Collapse to 4–5 one-liners under “Avoid:” (e.g. “No View/Diagram in elements”; “No diagram nodes for existing model unless asked”; “Ids: id- + 32 hex only”; “No type Connection”). One “Correct:” line + single JSON example.

### 2.4 Trim intro and references

- **Intro:** “You are an ArchiMate 3.2 expert. Input: model XML (from ‘ArchiMate model (Open Exchange XML):’ to ‘--- END OF MODEL ---’), then ‘User request:’ + user text. Use the XML to answer and to avoid duplicates.”
- **Schema reference:** Keep one URL (e.g. Open Group ArchiMate XSD) instead of repeating schema names and namespaces unless necessary for export format.

### 2.5 Order by frequency / importance

- Put **routing** (when to use ANALYSIS vs CHANGES vs EXPORT) at the top so the model commits to a response type early.
- Then **CHANGES JSON shape** (structure, keys, id format) and **diagram rule** (when to include `diagram`, nodes only from this response).
- Then **remove** (from model / from diagram only / delete diagram) in one block.
- Then **ANALYSIS** (plain text, describe view = whole diagram, no hallucinations) and **EXPORT** (full model XML) in short form.
- Put “Avoid” / “Correct” and the single example at the end.

---

## 3. Suggested structure of a shortened prompt

1. **Role + input (2–3 sentences)**  
   ArchiMate 3.2 expert. Input: model XML then user request. Use XML only; no hallucination.

2. **Response routing (table or 5–6 bullets)**  
   Action words → CHANGES. Describe/analyze only → ANALYSIS. Return/export model → EXPORT.

3. **CHANGES (JSON)**  
   - Keys: `elements`, `relationships`, optional `diagram`, `remove*`, `error`.
   - Ids: `id-` + 32 hex. No View/Diagram/Connection types.
   - No duplicate elements (compare to supplied model).
   - Fragment rule: process/service/capability/component/view → multiple elements + relationships; + `diagram` only if new view.
   - Diagram: include only for new view; for “new diagram with content” include `diagram` with nodes/connections; nodes only from this response’s elements unless user asked to add existing.
   - Remove: from diagram only → `removeElementFromDiagramIds`; from model → `removeElementIds`; delete view → `removeDiagramNames`. Use selection context (id=…) or model XML for ids/names.
   - One full example (add + diagram + optional remove line).

4. **ANALYSIS**  
   Plain text. “Describe the view” = whole diagram. Only describe what is in the supplied XML.

5. **EXPORT**  
   Full Open Exchange XML when user asks; structure in one sentence or reference.

6. **Avoid (4–5 one-liners)**  
   No View/Diagram in elements; no existing-model nodes on new diagram unless asked; id- + 32 hex only; no Connection type.

7. **Reference**  
   ArchiMate 3.2 types (link or one-line summary). Optional: single JSON schema snippet if it helps.

---

## 4. Estimated savings

- **Deduplication:** Removing repeated “diagram”, “remove”, “id”, “describe view” rules can save roughly 400–800 tokens.
- **Lists:** Shortening element/relationship type list to a reference + examples saves roughly 200–400 tokens.
- **Prose → structure:** Tables and bullets instead of long paragraphs can save another 200–400 tokens.
- **One example per pattern:** Saves roughly 100–300 tokens.
- **Overall:** A refactored prompt in the 1,200–1,800 word range (from ~3,200) is realistic, i.e. roughly **40–50% fewer tokens** while preserving behaviour if the consolidated rules are kept complete and unambiguous.

---

## 5. Implementation approach

1. **Copy current prompt** to a new file (e.g. `system-prompt-refactored.txt`) and refactor there so the original remains until tests pass.
2. **Apply consolidation** (section 2.1): merge duplicate rules into single blocks.
3. **Apply structure** (section 2.2): add routing table, bullet blocks, one-line keys.
4. **Shorten lists and examples** (section 2.3): trim type list, one example per pattern, collapse WRONG vs RIGHT.
5. **Reorder** (section 2.5): routing first, then CHANGES, then ANALYSIS/EXPORT, then Avoid + example.
6. **Test** with existing tests (e.g. `ArchiMateSystemPromptTest`, `ArchiMateAnalysisUseCaseTest`) and with a small set of manual prompts (add element, add diagram, remove from diagram, describe view, return model). Adjust wording if the model mis-routes or omits required keys.
7. **Replace** the original `system-prompt.txt` (and any Java constant that loads it) with the refactored version once behaviour is validated.
8. **Optionally:** Add a unit test that asserts the prompt length (e.g. character or word count) stays below a target so future edits don’t bloat it again.

---

## 6. Summary

- **Efficiency** comes from: (1) stating each rule once, (2) using tables/bullets instead of long prose, (3) shortening type lists to a reference + examples, (4) one example per pattern, (5) ordering by importance.
- **Safety:** Keep all behavioural rules (routing, diagram vs no diagram, remove-from-diagram vs model, id format, no duplicates). Only remove repetition and verbosity.
- **Validation:** Run existing prompt-related tests and a small manual suite before and after; aim for ~40–50% token reduction with unchanged behaviour.
