---
id: tidy-name-vs-type
group: tidy
title: Names that do not match the ArchiMate type
prompt: Find elements whose names look like a different ArchiMate layer or type than their actual type. Report only; do not change the model.
mode: analysis
---

Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Do not rename anything. Use only the supplied XML.

Question: which element names disagree with their ArchiMate type?

Examples of a mismatch: a BusinessProcess named like an application or system; an ApplicationComponent named like a business process or role; a Node named like a business actor; a BusinessActor named like a piece of software.

Cite type, name, and id, and say why the name looks like another type. Skip names that are merely vague.

Do not propose a wider model review or extra architecture.

Report format:
1. Findings
2. None found (if true)
