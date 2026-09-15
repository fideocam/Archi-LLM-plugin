---
id: tidy-relationships-not-on-views
group: tidy
title: Relationships not on any diagram
prompt: List model relationships that do not appear as a connection on any view. Report only; do not change the model.
mode: analysis
---

Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Do not add or remove anything. Use only the supplied XML.

Question: which catalog relationships are unused on diagrams?

A relationship is on a view if some `<connection>` has `relationshipRef` equal to that relationship’s `id`. List relationships with no such connection on any view in `<viewsAndDiagrams>`.

For each: relationship type, name (if any), id, source type+name+id, target type+name+id.

If `<viewsAndDiagrams>` is missing or truncated, say you cannot complete this check. Nested figures inside groups may be omitted from the XML; mention that limit if you suspect it.

Do not suggest new views, new relationships, or enterprise improvements. This is a catalog-versus-canvas inventory only.

Report format:
1. Count
2. List (type, id, source, target)
3. Relationships that do appear on at least one view (optional short note, not a full dump)
