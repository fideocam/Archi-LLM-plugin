---
id: analyse-dependencies
group: analyse
title: Dependencies
prompt: Which systems depend on this application?
mode: analysis
---

Answer the user request as ANALYSIS (plain text) only. Do not output CHANGES JSON.

If a selection or primary diagram names an application, start there; otherwise identify ApplicationComponents in the XML. Walk Serving, Used-by (ServingRelationship), Access, Flow, Realization, and Assignment only as they appear. List dependents and dependencies with type, name, and id. If the named application is not in the XML, say it was not found.
