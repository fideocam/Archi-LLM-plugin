---
id: analyse-rationalization
group: analyse
title: Overlapping applications
prompt: Are there multiple applications supporting the same process?
mode: analysis
---

Answer the user request as ANALYSIS (plain text) only. Do not output CHANGES JSON.

Group ApplicationComponents (and ApplicationServices) by the BusinessProcess, BusinessService, or Capability they serve or realize. List cases where more than one application supports the same process. Cite type, name, and id. If there is no overlap, say so. Do not recommend deletions; describe the overlap.
