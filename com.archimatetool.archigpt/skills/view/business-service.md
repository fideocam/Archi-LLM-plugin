---
id: view-business-service
group: view
title: Business diagram for a service
prompt: On this view, where some BusinessServices already have a serving path (application, process, or role), which peer services on the same view lack that path? Report only; do not propose a full EA model.
mode: analysis
---

Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Use only the supplied XML.

Scope: BusinessService elements on the selected or primary view. If none, say so and stop.

Method:

1. See what this view already models for at least one service (Serving from ApplicationService or ApplicationComponent; Realization or Assignment from a process; Assignment from a role or actor).
2. Compare the other BusinessServices on the same view to that convention.
3. A gap is a peer service on this canvas that lacks a related element the view already uses for another service — including when that related element exists in the catalog but is not on this view.

Do not score every service against a full sandwich. If no service on this view has a serving path, say the path is not modelled here and stop.

Report format:
1. Convention on this view (examples with ids)
2. Peer services missing that related path
3. Related catalog elements not placed on this view (only if they complete the convention for a peer)
