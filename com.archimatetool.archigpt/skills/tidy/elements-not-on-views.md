---
id: tidy-elements-not-on-views
group: tidy
title: Elements not on any diagram
prompt: List catalog elements that do not appear as a node on any view. Report only; do not change the model.
mode: analysis
---

Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Do not add or remove anything. Use only the supplied XML.

Question: which catalog elements are never placed on a diagram?

ArchiGPT may include a PLUGIN FINDINGS section computed from the live model (including nested group figures). Trust that list. Do not re-derive unused elements from truncated or chunked XML.

If PLUGIN FINDINGS are absent: an element is on a view if some `<node>` has `elementRef` equal to that element’s `id`. If views are missing or truncated, say you cannot complete this check.

Cite type, name, and id.

Do not recommend a full architecture, new diagrams, or extra layers. This is a placement inventory only.

Report format:
1. Count (use PLUGIN FINDINGS when present)
2. List (type, name, id)
