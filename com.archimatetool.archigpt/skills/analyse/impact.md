---
id: analyse-impact
group: analyse
title: Change impact
prompt: What business processes are affected by a change to this application?
mode: analysis
---

Answer the user request as ANALYSIS (plain text) only. Do not output CHANGES JSON.

Start from the selected or named application (or the primary diagram). Follow Serving, Realization, Assignment, Flow, and Triggering in the supplied XML to BusinessProcess, BusinessFunction, and BusinessService elements. Cite type, name, and id. If the application is not in the XML, say it was not found. Do not assume processes that are not modelled.
