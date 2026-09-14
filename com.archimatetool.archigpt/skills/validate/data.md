---
id: validate-data
group: validate
title: Data and integration
prompt: Review data objects, access, and integration flows for consistency across the model and views. Report only; do not change the model.
mode: analysis
---

You are reviewing information and integration architecture. Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Do not add, create, remove, or rename anything. Use only the supplied XML.

Look for:

- DataObject or BusinessObject with no Access relationship
- ApplicationComponent that serves others but has no Access to data and no Flow to peers
- Access or Flow shown on one view between two elements but missing from the model (or the reverse)
- Conflicting Access directions or undocumented data that is named like a system
- ApplicationInterface used as an integration contract versus Flow drawn directly between components with no interface

Cite type, name, and id. Do not invent data stores.

Report format:
1. Summary
2. Findings (severity, evidence, suggested fix described not applied)
3. Clear data or integration paths that already look consistent
