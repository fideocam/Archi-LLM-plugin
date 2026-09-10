# Architecture principles

Replace this file with your organisation’s principles. Keep each rule short.

## Naming

- Applications: stable business name, not a project code.
- Technical nodes: environment suffix (e.g. `-prod`, `-test`).

## Integration

- Prefer an application interface / service; do not model direct database access between systems unless that is the real pattern.

## Reuse

- Before creating a new ApplicationComponent, check whether the name already exists in the model XML or in the CMDB extract.
