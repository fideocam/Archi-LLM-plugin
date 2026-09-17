---
id: tidy-duplicate-names
group: tidy
title: Duplicate names in the catalog
prompt: Find elements of the same ArchiMate type that share the same or very similar names. List each group with ids. Report only; do not change the model.
mode: analysis
---

Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Do not add, create, remove, or merge anything. Use only the supplied XML.

Question: which catalog elements look like duplicates because of the name?

Rules:

- Group by ArchiMate type, then by name ignoring case and extra spaces.
- Exact matches first. Then near-duplicates (abbreviation, hyphen vs space, singular vs plural) of the same type.
- Different types with the same name are not duplicates here (use the name-versus-type tool for that).
- Cite type, name, and id for every member of a group.
- Do not invent elements. If the XML is truncated, say the scan is incomplete.
- Do not propose a wider architecture review.

Report format:
1. Exact-name groups
2. Near-duplicate groups
3. None found (if true)
