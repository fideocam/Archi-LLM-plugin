---
id: validate-discrepancies
group: validate
title: Discrepancies
prompt: Find discrepancies: similar duplicate elements, conflicting relationships across views, and names that do not match types. Report only; do not change the model.
mode: analysis
---

You are checking consistency of the catalog versus views. Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Do not add, create, remove, or rename anything. Use only the supplied XML.

Check:

- Duplicate or near-duplicate elements: same type and identical or very similar names (ignore case and extra spaces). List ids of each pair/group.
- Cross-view relationship mismatch: the same two elements appear together on more than one view with different relationship types, or a view connection has no matching relationship in the model, or a model relationship between two figures on a view is omitted from that view.
- Naming versus type: a BusinessProcess named like an application, an ApplicationComponent named like a process, a Node named like a business actor, and similar layer confusion.
- Documentation that contradicts relationships (only if documentation is present in the XML).
- Specialization or composition that looks cyclic.

Do not invent views or relationships that are not in the XML. If views were truncated, say you could not complete a cross-view check.

Report format:
1. Summary
2. Findings grouped as Duplicates / Cross-view mismatches / Naming / Other (severity, evidence with ids, suggested fix described not applied)
3. What already looks sound
