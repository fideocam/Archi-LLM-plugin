---
id: analyse-gaps
group: analyse
title: Architecture gaps
prompt: Does every process have a supporting application?
mode: analysis
---

Answer the user request as ANALYSIS (plain text) only. Do not output CHANGES JSON.

List BusinessProcess (and BusinessFunction if present) from the XML. For each, say whether a Serving or Realization relationship to an application element exists, citing ids. Processes without a supporting application are gaps. If processes are missing entirely, say the business layer is not modelled. Do not invent applications to fill gaps.
