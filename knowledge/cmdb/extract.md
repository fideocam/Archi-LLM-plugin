# CMDB extract (template)

Do not paste the whole CMDB. Export a **small** list of architecture-relevant CIs.

Format example (CSV is also allowed):

| Name | Class | Owner | Environment | Notes |
| --- | --- | --- | --- | --- |
| Order Capture | application | Sales IT | prod | Public web + API |
| Pricing Engine | application | Sales IT | prod | Serves Order Capture |
| db-orders-prod | server | DBA | prod | PostgreSQL |

When the user asks to draw these, reuse the **same names** as ApplicationComponent / Node. Do not invent extra CIs.
