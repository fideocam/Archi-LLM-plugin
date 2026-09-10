# Security

ArchiGPT is a local Archi plug-in that sends a compact model digest (Open Exchange XML) to **Ollama** and, when the model replies with CHANGES JSON, applies those mutations to the open ArchiMate model.

This document is the threat model and the controls in place. It is not a pentest report. The same controls were first implemented in [EaGPT / sparxgpt](https://github.com/fideocam/sparxgpt) and ported here.

## Trust boundaries

| Boundary | Who can influence it |
| --- | --- |
| Archi model | Anyone who can edit the open `.archimate` file |
| Chat prompt | The Archi user |
| Ollama URL / model name | The Archi user, plus workspace preferences and `-Darchigpt.ollamaBaseUrl` |
| Knowledge folder | The Archi user (Markdown/text/CSV injected into the prompt) |
| LLM reply | The selected Ollama model (and anyone who can poison its context) |
| Plug-in process | The OS user running Archi |

The LLM is **not** trusted. Parser, schema, and mutation-policy checks run before Archi writes.

## Controls

### Ollama HTTP client

- Only `http` and `https`. `file:`, `ftp:`, `javascript:`, and other schemes are rejected.
- Userinfo (`user:password@host`) is rejected so credentials are not stored in preferences or sent in `Host`.
- Query and fragment are stripped. If the user pastes a full `/api/chat` URL, that suffix is removed so the client still calls `/api/tags` and `/api/chat` on the origin. A reverse-proxy path such as `/ollama` is kept.
- Redirects are disabled (`setInstanceFollowRedirects(false)`).
- `HTTP_PROXY` is ignored (`Proxy.NO_PROXY`) so the model digest is not sent through a proxy.
- Model names cannot contain quotes, backslashes, or control characters (JSON injection into the request body).
- Request JSON escapes quotes, newlines, and other control characters.
- Well-known cloud metadata endpoints are blocked, including encodings:
  - `169.254.169.254` and the rest of `169.254.0.0/16`
  - IPv6-mapped `::ffff:169.254.169.254`
  - dword / hex / octal IPv4 forms of those addresses
  - `metadata.google.internal`, `metadata`, `instance-data`
  - Alibaba `100.100.100.200`
  - AWS IMDSv2 IPv6 `fd00:ec2::254`

Localhost and RFC1918 Ollama URLs remain allowed.

### Model mutations

- Replies larger than 200,000 characters are not treated as changes.
- `"elements"` in prose does not count as a change payload; the parser looks for `"elements": [` (and the other mutation keys).
- Element and relationship types must be ArchiMate 3 names (or known aliases). Unknown types never become Archi classes.
- Batch caps: 80 elements, 120 relationships, 50 removals, 256-character names, 80-character ids, diagram coordinates 0–4000.
- **Deletes from the model** (elements, relationships, whole diagrams) require an explicit Cancel / Delete confirmation (default Cancel).
- Remove-from-diagram-only is not treated as destructive.

### XML digest sent to the model

Element/relationship/diagram names are XML-escaped. Control characters are stripped so a hostile name in the Archi model cannot break out of attributes in the prompt digest.

### Company knowledge

Files are read only from the configured knowledge folder (path-traversal out of that folder is skipped). Caps: about 8 files, 8000 characters by default. Do not put credentials or personal data in the pack.

## Residual risks (accepted)

1. **Prompt injection via model content.** Names and notes in the Archi project are sent to the LLM. A planted name can try to make the model emit change JSON. Mitigation: schema + limits + delete confirmation. **Adds still apply without a second prompt** if they validate.
2. **SSRF to the LAN.** A user can point ArchiGPT at any http(s) host except the blocked metadata addresses. That is required for a networked Ollama box. Do not paste untrusted URLs into the Ollama field. Requests do not use `HTTP_PROXY`.
3. **DNS rebinding / newly registered names.** Hostname allowlisting is not used. Bind Ollama to localhost when you can.
4. **Ollama sees the model digest (and any retrieved knowledge).** Treat the local model like any other process that can read the open architecture. Do not point ArchiGPT at a public hosted LLM unless that is acceptable.
5. **Unsigned plug-in.** Only install a build you compiled or otherwise trust.

## Reporting

Open a GitHub issue on [fideocam/Archi-LLM-plugin](https://github.com/fideocam/Archi-LLM-plugin) with the word **security** in the title. Do not attach live model extracts that you cannot share.
