# The language model belongs inside the tool you already use

I have now wired a local language model into Archi, Blender, Inkscape, Chrome, and Keynote. There is also Bondie, an iPhone app that talks to the same Ollama server from my pocket. None of that started as a product catalogue. It started from a simple irritation: the work lived in one window, the model lived in another, and I was the copy-paste bridge between them.

That tax is easy to ignore until you notice how much of a workday it eats. You dump a model, a scene, a slide deck, or a form into a chat. You wait. You get prose back. Then you re-interpret that prose by hand into the tool that actually owns the file. Confidential architecture XML, a client illustration, or a CV has already left the building. And you never quite trusted the result, because the chat never saw the real selection, the real layers, or the real constraints of the host application.

The interesting change is not “AI does my job.” It is that the blank-canvas tax goes away, and the model finally sits where the work already is.

## What a workday looks like with the model in the tool

In Archi I used to translate a sentence from a workshop into boxes and lines. That translation is the job, and it is still the job. What changed is the first twenty minutes. I can open a view, select a folder or an application component, and ask: *what business processes use this?* or *add a business actor called Customer to this diagram.* The plugin sends a digest of the model and the current selection to Ollama, and either I get an analysis I can argue with, or structured changes the plugin applies for me to review. I stay in Archi. The architecture does not have to be pasted into a website.

The same rhythm shows up in the other tools, because the work is the same shape even when the domain is not.

In Blender the question is rarely “write me a 3D tutorial.” It is “the scene already has these objects; add a cylinder here, cut a hole, prepare this for print.” A general chat will invent geometry that looks plausible and is painful to manufacture. A scene digest plus a small allowlist of operations is slower to build and much harder to regret.

In Inkscape it is the illustration that is already on the canvas: a rectangle that should become a label, a layer that should match the current style, a path that should move with the selection. The model sees a document digest, not a blank SVG file.

In Chrome it is the form in front of me. ChromeGPT classifies the field from the label and placeholder, optionally grounds the answer in text I keep locally (a CV, a profile, a set of stock replies), and asks before it inserts anything. Filling the same procurement portal for the tenth time is no longer a copy-paste exercise from a notes app.

In Keynote it is the deck that is already open. Apple does not give Keynote a plugin SDK, so the companion app sits beside it, reads a digest of the slides, and drives allowlisted edits through JavaScript for Automation. “Add a closing slide with three takeaways from this deck” is a different task from “write me a presentation from scratch.”

On the phone, Bondie is the same Ollama host without the desk. I am not running a 70B model on the iPhone. I am on the same Wi-Fi as the machine that is. The conversation continues in a meeting hallway or on the train, against models that never left the house.

The common shift is this: you stop treating the language model as a separate destination. You treat it as a colleague who can see the current file, the current selection, and the rules of the tool — and who is not allowed to do anything the tool itself cannot undo.

## Why this is becoming ordinary, not exotic

A few years ago, “run the model locally” meant a workstation you defended in a budget meeting. That bar keeps dropping. Consumer laptops gained more unified memory, better NPUs and GPUs, and the models themselves got smaller for a given quality. Ollama made the last mile boring: pull a model, listen on port 11434, answer HTTP.

When capacity is scarce, local inference is a hobby. When capacity is cheap enough, it becomes a habit. A mid-range laptop can already run a mid-size instruction-tuned model at a speed that is useful for editing a diagram or filling a form. A household or a small office can share one capable host on the LAN. The phone does not need to be the computer; it only needs to reach the computer.

That is the quiet part. Cloud APIs will stay excellent for the hardest reasoning and the largest context windows. They also mean keys, invoices, and a policy discussion every time you send an enterprise architecture model off-site. Local-first is not a moral pose. It is the path that keeps getting cheaper as the machines on our desks get faster. More people can use it without asking anyone for an API budget.

## How to extend the tool you already have

You do not need the vendor to ship “AI” for this. You need an extension point, a digest, and a short leash.

**1. Put a local model on the network.**
Install [Ollama](https://ollama.com), pull something you can actually run (`llama3.2` is a reasonable starting point), and keep it on `localhost`. If a phone or another PC should share it, bind to the LAN (`OLLAMA_HOST=0.0.0.0`) and point clients at `http://192.168.x.x:11434`. Chrome extensions need an extra allowlist (`OLLAMA_ORIGINS`) because browsers send an Origin header Ollama otherwise rejects.

**2. Snapshot the work, not the universe.**
The model cannot see your GUI. You have to tell it what is open. In Archi that is ArchiMate XML (often truncated or chunked, because real enterprise files are huge). In Blender it is a scene digest. In Inkscape, selected SVG. In Keynote, a slide inventory. In Chrome, the field’s label, name, and placeholder. Selection matters more than dumping the whole file. “This element” and “this view” are what make the answer usable.

**3. Write the rules of the tool as a system prompt.**
A general model will happily return Markdown, the wrong ArchiMate type, or Python you should never `eval`. The system prompt is where you say: analysis is plain text; edits are a JSON object; only these operations exist; use the ids from the digest; do not invent layers that the host cannot create.

**4. Allowlist the hands, not the mouth.**
This is the piece most “just call the LLM” demos skip. The model may talk as much as it wants. It may only *change* the document through operations you implemented: add actor, move object, insert rectangle, fill this input, add a slide. Unknown `op` values are ignored. That is how BlenderGPT, InkscapeGPT, KeynoteGPT, and ArchiGPT stay boring in the good way. Free-form code from a model is an incident waiting for a Tuesday.

**5. Apply through the native API, with undo.**
Use the plugin SDK if the tool has one (Eclipse for Archi, Python for Blender and Inkscape, a Chrome extension). If it does not — Keynote is the example — sit beside the app and drive it with whatever automation surface exists (JXA, AppleScript, COM, a CLI). Apply edits on the thread the host expects, in one undo step if you can. Then show the user what happened.

**6. Keep a human in the loop.**
Ask before inserting into a web form. Report what was added or removed in a model. Offer Stop. None of these tools are a substitute for knowing ArchiMate, printability, or whether that slide should exist. Stronger local models raise the success rate. They do not remove review.

If your daily tool is none of the above, the recipe does not change. Excel, a helpdesk console, a CAD package, a CMS: find the extension or automation hook, emit a digest, constrain the output, apply with undo. The first version can be a side panel that only *answers* questions about the current file. Making it *edit* the file is a second, stricter project.

## What does not change

Best practices still matter. A fluent answer that names the wrong application component is worse than a blank view, because it looks finished. Small models still confuse similar names and wander off the schema. Large enterprise models still do not fit in one context window. You still have to decide what “good” means in your domain.

The point of putting the model in the tool is not to skip that judgement. It is to spend the judgement on the architecture, the illustration, or the deck — not on ferrying text between windows.

## Where this is going

I expect the next few years of hardware to make this ordinary. Not because every app vendor will ship a perfect assistant, but because enough of us can now run a useful model next to the file, and because a plugin is a weekend of work if you already know the host API.

The long-term picture is not a single omniscient chat. It is a desk (and a phone) where Archi, Blender, Inkscape, the browser, Keynote, and Bondie all ask the same local server what to do next — and only do the things those tools were already able to do.

---

Tools mentioned:

- [ArchiGPT](https://github.com/fideocam/Archi-LLM-plugin) — ArchiMate modelling in Archi
- [BlenderGPT](https://github.com/fideocam/BlenderGPT) — Blender add-on, scene digest and JSON edits
- [InkscapeGPT](https://github.com/fideocam/Inkscape-Ollama-extension) — Inkscape extension for SVG
- [ChromeGPT](https://github.com/fideocam/ChromeGPT) — Chrome extension for form fields and local chat
- [KeynoteGPT](https://github.com/fideocam/KeynoteGPT) — macOS companion for Apple Keynote
- Bondie — iPhone app against the same local/LAN Ollama server
