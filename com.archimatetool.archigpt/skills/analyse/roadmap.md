---
id: analyse-roadmap
group: analyse
title: Current vs target
prompt: What is the current vs target state? Which components are being removed?
mode: analysis
---

Answer the user request as ANALYSIS (plain text) only. Do not output CHANGES JSON.

Use Plateau, Gap, WorkPackage, and related associations in the supplied XML. Describe current versus target as modelled. Mention components associated with a baseline plateau but not the target, or with a Gap, as candidates for removal only if the XML shows that. If no migration elements exist, say a roadmap is not modelled; do not invent one.
