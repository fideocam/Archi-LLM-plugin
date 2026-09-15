---
id: view-system-architecture
group: view
title: Tech architecture for a system
prompt: On this view, where some applications already show interfaces, flows, data access, or a host, which peer systems on the same view lack that related element? Report only; do not propose a full EA model.
mode: analysis
---

Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Use only the supplied XML.

Scope: ApplicationComponent, ApplicationService, or Node elements on the selected or primary view. If none, say so and stop.

Method:

1. See what this view already models for at least one system (interface or application service, Flow/Serving to a peer, Access to data, Realization by Node/SystemSoftware/Device).
2. Compare the other systems on the same view to that convention.
3. A gap is a peer on this canvas that lacks a related element the view already uses for another system — including when that related element exists in the catalog but is not on this view.

Do not score every system against a full technology architecture. If this view never shows interfaces, flows, data, or hosts, say so and stop.

Report format:
1. Convention on this view (examples with ids)
2. Peer systems missing that related element
3. Related catalog elements not placed on this view (only if they complete the convention for a peer)
