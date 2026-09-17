---
id: tech-serving-without-path
group: technology
title: Application serving with no technology path
prompt: Where some application pairs that Serving or Flow already have a CommunicationPath or network between their hosts, which serving pairs have hosts but no technology path? Report only; do not change the model.
mode: analysis
---

Reply with ANALYSIS (plain text) only. Do not output CHANGES JSON. Use only the supplied XML.

Question: which application Serving or Flow relationships have hosts but no CommunicationPath, Network, or equivalent path that this model already uses for other pairs?

First find application-to-application Serving or Flow where both ends have a realizing Node or Device, and a CommunicationPath, Network, or Path already connects those hosts (or their devices). That is the convention.

Then list other Serving or Flow pairs on the same view, or in the same folder if no view is in scope, where both applications are hosted but no such technology path exists between the hosts.

If no hosted pair has a technology path, say paths are not modelled here and stop. Do not invent a WAN, LAN, or API gateway. Do not demand a path for Serving that stays inside one Node.

Cite type, name, and id for the applications, hosts, and any path.

Report format:
1. Convention found (serving or flow that already has a technology path)
2. Hosted serving pairs with no path
3. Not applicable (if no technology path exists)
