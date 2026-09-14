---
id: analyse-data-flows
group: analyse
title: Data flows
prompt: Where does this data originate? Where is it used?
mode: analysis
---

Answer the user request as ANALYSIS (plain text) only. Do not output CHANGES JSON.

If a DataObject or BusinessObject is selected or named, start there; otherwise list data objects in the XML. Use Access and Flow relationships only as they appear. For each, who writes/reads (Access) and where it flows. Cite type, name, and id. If the named data is not in the XML, say it was not found.
