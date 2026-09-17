---
id: tidy-relationships-not-on-views
group: tidy
title: Relationships not drawn on any view
prompt: List model relationships that do not appear as a connection on any view. Report only; do not change the model.
mode: analysis
---

Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Do not add or remove anything. Use only the supplied XML.

Question: which catalog relationships are unused on diagrams?

ArchiGPT may include a PLUGIN FINDINGS section computed from the live model (including connections stored on figures, not only in XML). Trust that list. Do not re-derive unused relationships from truncated or chunked XML.

If PLUGIN FINDINGS are absent: a relationship is on a view if some `<connection>` has `relationshipRef` equal to that relationship’s `id`. If `<viewsAndDiagrams>` is missing or truncated, say you cannot complete this check.

For each unused relationship: type, name (if any), id, source type+name+id, target type+name+id.

Do not suggest new views, new relationships, or enterprise improvements. This is a catalog-versus-canvas inventory only.

Report format:
1. Count (use PLUGIN FINDINGS when present)
2. List
3. Short note only — do not dump relationships that *are* on a view
