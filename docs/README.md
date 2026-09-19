# PolicyPilot documents

The design and planning documents of PolicyPilot, an AI copilot over a deterministic rules engine:
the model authors rules from policy text, reviews them, answers questions and proposes changes; a
deterministic Java engine makes every decision; a person approves every policy change.

These files are Markdown exports of the living documents where the set is written and maintained
(kept outside the repository). The living documents are the source of truth: when one changes it is
re-exported here, so the exports are never edited by hand. The progress checklist and the worklog are
the two exceptions: they are maintained only here. Each file carries its export date under the title.
The documents are written in this order, each short enough to read in ten minutes and each the input
to the next.

| # | Document | What it decides | Read it when |
| --- | --- | --- | --- |
| 1 | [Project Brief](01-project-brief.md) | Scope, goals and non-goals, the three-minute demo, the 23 functional and 7 non-functional requirements, phases and timeline, deployment, definition of done, risks, glossary | First; everything else refers to it |
| 2 | [Architecture](02-architecture.md) | Modules and their boundaries, data flow, data model, API surface, model adapter design, decision records for the key choices | Before touching the code layout, the API or the database |
| 3 | [Rules DSL Specification](03-rules-dsl-specification.md) | The JSON rule format: fields, operators, expressions, actions, provenance, priority bands, the JSON Schema, static validation codes, evaluation semantics, the conformance suite, the reference implementation | Before working on `rules`, `engine` or any fixture |
| 4 | [AI Pipeline and Prompt Specification](04-ai-pipeline-and-prompts.md) | The five prompts (author, review, explain, answer, change), output contracts, the validation loop, chunking and retrieval, tools, the evaluation set and metrics | Before working on `ai`, `rag`, `chat` or the evaluation runner |
| 5 | [Security Specification](05-security-specification.md) | Threat model, every kind of injection and its control, authentication and sandbox authorization, input limits, model and tool safety, supply chain, the red-team fixtures, the pre-demo checklist | Before the front door (day 4) and before every model-facing feature |
| 6 | [Test Strategy](06-test-strategy.md) | Test levels, coverage targets by risk with mutation testing on the engine, definition of done per task, traceability matrix, fixtures and golden files, CI gates, working method | Before writing the first test, and whenever a task is declared done |
| 7 | [Work Plan](07-work-plan.md) | Day-by-day tasks with their tests over 19 working days, phase gates G0 to G4, security and evaluation schedule, requirement-to-day map, scope ladder, daily routine | Every morning |
| 8 | README and Demo Script | Setup, architecture summary, the scripted demo, known limitations, security and testing sections, talking points | Written on days 16 to 19 as the repository's top-level `README.md` |

[Progress Checklist](progress-checklist.md) is the working tracker: one box per task of the Work Plan,
ticked as it is done and never before its tests are green; a box skipped on purpose is struck through
with the scope ladder rung that cut it. Documents 1 to 7 and the fixtures built ahead of day 1 are
already ticked. Since the switch to the two-week version on 2026-09-18 it is maintained here, not
exported: it holds the new dates and the struck-through boxes, and its living document is frozen with a
pointer to this file.

## What was built before day 1

The Work Plan starts from a repository that already holds, under `fixtures/`, the Rules DSL JSON
Schema, the demo lending policy with its published rule set, the Python reference implementation and
its self-test, the conformance suite (`C-01` to `C-31`) and one invalid fixture per validator code, the
seeded 200-case generator with its expected decisions and the scripted change request, and the
evaluation set of Document 4 (18 labeled policies, 30 questions, 6 change requests). `fixtures/README.md`
describes the file shapes; `python3 fixtures/reference/reference_check.py` must end with `ALL OK`.

## Other files that land in this folder

- `worklog.md`: started on day 1, one entry per day with what shipped, what the tests prove and the gate
  proofs (Work Plan, Daily routine and tracking).
- `eval/`: the evaluation reports of days 11 and 15, one column per model provider (Document 4,
  Evaluation Set and Metrics).

## Conventions

The documents and the code are in English; the sample policy, the demo questions and the model's
free-text output are in Hebrew, with an English policy fixture as the fallback (Project Brief,
Language decision). Rule ids, validator codes, route paths, thresholds and day numbers are fixed by the
documents: a task never changes one without changing the document first and re-exporting it.
