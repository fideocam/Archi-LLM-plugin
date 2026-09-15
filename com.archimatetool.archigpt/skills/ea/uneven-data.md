---
id: ea-uneven-data
group: ea
title: Uneven data access
prompt: Where some applications or data objects on the same view or folder already have Access or Flow, which peers have none? Report only; do not change the model.
mode: analysis
---

Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Use only the supplied XML.

First find the convention this XML already uses, for example:

- Some ApplicationComponents Access a DataObject or BusinessObject, or
- Some DataObject/BusinessObject elements have Access or Flow.

Then list peers of that same type on the same view, or in the same folder if no view is in scope, that have no Access and no Flow.

If no data access or flow exists, say that pattern is not modelled and stop. Do not invent stores or integration.

Cite type, name, and id. Contrast each gap with an example that has Access or Flow.

Report format:
1. Convention found (examples with ids)
2. Peers with no Access or Flow
3. Not applicable (if the convention never appears)
