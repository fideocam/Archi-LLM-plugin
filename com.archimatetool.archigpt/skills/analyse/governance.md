---
id: analyse-governance
group: analyse
title: Ownership
prompt: Who owns this application?
mode: analysis
---

Answer the user request as ANALYSIS (plain text) only. Do not output CHANGES JSON.

Look for BusinessRole or BusinessActor assigned to the selected or named application (AssignmentRelationship) and for Stakeholder associations. Cite type, name, and id. If no owner is modelled, say that ownership is not in the supplied XML; do not guess from documentation unless documentation is present and clearly states it.
