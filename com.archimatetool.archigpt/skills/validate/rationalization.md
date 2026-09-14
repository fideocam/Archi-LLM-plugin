---
id: validate-rationalization
group: validate
title: Rationalization
prompt: Identify overlapping applications or other concepts that could be consolidated. Report only; do not change the model.
mode: analysis
---

You are looking for rationalization opportunities. Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Do not add, create, remove, or rename anything. Use only the supplied XML.

Look for:

- Multiple ApplicationComponents serving or realizing the same BusinessProcess, BusinessService, or Capability
- Multiple ApplicationServices with similar names serving the same business service
- Duplicate technology nodes or system software that appear to host the same components
- Near-duplicate business processes or capabilities

For each overlap: name the concepts (type, name, id), the shared target, and whether they look like true duplicates, a justified split (e.g. channel vs back office), or insufficient information.

Do not recommend deleting anything as a fact; describe options.

Report format:
1. Summary
2. Overlap findings (severity, evidence, option to consolidate vs keep separate)
3. What already looks distinct
