---
id: tech-security-zones
group: technology
title: Peers missing a security control this model already uses
prompt: Where some nodes, interfaces, or paths on this view or folder already sit behind a Constraint, grouping, distinct CommunicationPath, or Requirement labelled as security, which peers have no such control? Report only; do not change the model.
mode: analysis
---

Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Use only the supplied XML.

Question: which technology elements lack a security control that this model already uses for peers?

First find what this view or folder already models as a security control for at least one Node, Device, SystemSoftware, CommunicationPath, or ApplicationInterface. Count as a control only what is in the XML, for example:

- Constraint or Requirement whose name or documentation is clearly security (access, trust, zone, encryption, firewall)
- Grouping or Location used as a zone that some nodes sit in and others on the same view do not
- A CommunicationPath or Serving that some interfaces use and peer interfaces do not

That is the convention. Then list peers of the same type on the same view (or folder if no view is in scope) that have no equivalent control.

If no security control is modelled, say so and stop. Do not recommend ISO, CIS, zero-trust, MFA, or a firewall topology that the file does not already show. Do not invent specializations.

Cite type, name, and id. Contrast each gap with an example that already has the control.

Report format:
1. Convention found (the control and examples with ids)
2. Peers missing that control
3. Not applicable (if no security control appears)
