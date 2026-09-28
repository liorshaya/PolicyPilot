# Register: implementing the design language

2026-09-26 · Lior Shaya · Document 9 of the PolicyPilot set

Document 9 turns the Register design language (`docs/design/register.html`, v3.2) into a phased plan for `frontend/`. It follows the rules of Documents 6 and 7: the tests of a phase are listed before its code, every phase ends at a gate checked on the cloud site, and nothing in it changes a route, a validator code, a fixture or a line of the backend. The spec is the source of truth for every visual value and every word on a screen; this document is the source of truth for scope, order, files and tests.

## How to read this document

The spec has twelve sections; each phase below names the sections it implements, the product files it touches, the tests it starts with, what it builds, and the gate it ends at. A paste-ready prompt closes each phase, in the manner of `docs/agent-briefing.he.md`; its first line is the two words the CLAUDE.md section understands ("start phase N"), and the rest repeats the phase's essentials for a session that has no CLAUDE.md. One phase is one pull request; phase 2 may be two.

When the spec and this document disagree, the spec's section 12 (the review checklist) decides. When the spec and Documents 1 to 7 disagree (a word, a semantic), the numbered documents decide, the spec is corrected first and the change lands here second. When either is silent, stop and ask; do not guess a value.

## The inputs

| File | Path in the repository | What it is |
| --- | --- | --- |
| `register.html` | `docs/design/register.html` | The design language, self-contained: 01 concept and glossary, 02 tokens, 03 type and the bidi law, 04 actors, states, the seal and the provenance line, 05 controls, 06 chips and marks, 07 tables, 08 shell, overlays and states, 09 patterns, 10 composed screens, 11 the states matrix, 12 the checklist. The theme toggle at the top shows both themes. |
| `register.css` | `docs/design/register.css` | The same CSS the spec carries in its `<style>`, in six layers. Layers 1 to 5 (tokens, base, primitives, structures, patterns) are the reference implementation to port; layer 6 (`.spec-*`) styles the document itself and is never shipped. |
| `screens/*.png` | `docs/design/screens/` | The gate, Policies, Rules and Cases at 1376px, both themes, at 2×; `pattern-decide-a-case.png` for phase 5. They are what "done" looks like; a Playwright screenshot of the product is compared to them by eye at every gate. |
| this document | `docs/09-register-implementation.md` | The plan: what each phase is. |
| `register-status.md` | `docs/register-status.md` | The board: what state each phase is in. The agent that runs a phase is the only writer; the owner reads it. |
| the CLAUDE.md section | appended to `CLAUDE.md` from `docs/design/CLAUDE-register-section.md` | What "start phase N" means, so a session that receives those two words knows where to look and what to do. |

Commit all of them before phase 0, in one pull request titled "Document 9: the Register design language", so every session reads them from the clone. That commit is the "Preparation" row of the board.

## Where a section of the spec lives

The spec is one HTML file; each section is a `<section id="…">`, so an agent reads a section by its id rather than the whole file.

| Section | id in `register.html` | Section | id in `register.html` |
| --- | --- | --- | --- |
| 01 Concept, readers, glossary | `#concept` | 07 Tables | `#tables` |
| 02 Tokens | `#tokens` | 08 Shell, overlays and states | `#layout` |
| 03 Typography and the bidi law | `#type` | 09 Patterns | `#patterns` |
| 04 Actors, states, the seal, the provenance line | `#actors` | 10 Composed screens | `#screens` |
| 05 Controls | `#controls` | 11 Every surface, every state | `#states` |
| 06 Chips, tags and marks | `#chips` | 12 The checklist | `#checklist` |

`register.css` is the spec's `<style>` block as a file; its six layers are marked with a `/* ---------- Layer n */` comment each, and the pattern blocks inside layer 5 carry a one-line comment naming the pattern.

## Three rules for the whole job

1. **Documents rule the words.** Every string on a screen comes from the spec's glossary (section 01), which was taken from the code the product already ships: Approved · Declined · Manual review · Evaluation error; Matched · Did not match · Not reached · Disabled; Draft · Published · Superseded; Proposed · Approved · Rejected; Case, never Application, in the chrome. A wording change lands in the spec first, in the test second, in the component third. A test that queries by text is updated in the same pull request as the wording, with the spec's word, never loosened to a regular expression.
2. **Tokens rule the values.** No hex colour, no pixel size, no duration is written in a component's CSS or in a `style` attribute. A value the spec has and `tokens.css` lacks is added to `tokens.css` first, with its contrast pair in `tokens.test.ts`, and used second. `register.lint.test.ts` (phase 0) fails the build on a literal.
3. **The backend is not touched.** Phase 5's two additions read what exists: `POST /rulesets/{id}/versions/{no}/decide` with one `case`, and the `fields` block of a version. The provider line (FR-21) reads `GET /system/provider` once day 16 delivers the route and stays hidden until then; nothing in this plan waits for it. Phase 4 is the one exception, the owner's decision of 2026-09-28: the API serves what the spec asks of the assistant, the change request and the audit log (each tool call and the fixed sentence of the chat stream, the budget's state, each change stage's time and tokens, a change request's number, the regression's base counts, moved flags and the proposed side's trace, the audit log without a version, and a proposal's audit entry with its request, the rules the model was shown and its tokens, the owner's answer to the phase's tenth question), with Documents 2 to 5 changed first, each in a pull request of its own.

## The map: from the spec to the product

| Spec section | Product files | Notes |
| --- | --- | --- |
| 02 Tokens | `src/styles/tokens.css`, `src/styles/tokens.test.ts` | Replace the `--color-*` family with the spec's names (`--paper`, `--sheet`, `--well`, `--ink`, `--ink-2`, `--ink-3`, `--accent`, the decision triplets, `--text-*`, `--s-*`, `--r-*`, `--row-h`, `--control-h`, `--margin-w`, `--ease`, `--t-fast`, `--t-base`). The dark theme is a second block under `:root[data-theme="dark"]`, never a filter or an inversion. |
| 03 Type and the bidi law | `src/main.tsx` (fonts), `src/index.css` (base, `[lang="he"]`, `bdi`, numbers), `src/shared/i18n/direction.ts` | `@fontsource/ibm-plex-sans` 400 500 600, `@fontsource/ibm-plex-sans-hebrew` 400 500 600, `@fontsource/ibm-plex-mono` 400 500, `@fontsource/ibm-plex-serif` 400 500, `@fontsource/frank-ruhl-libre` 400 500; `@fontsource/inter` and `@fontsource/heebo` are removed. `direction.ts` keeps its contract and gains `numberToken(value, unit)` for the `<bdi dir="ltr">` isolate the law requires. |
| 04 Actors, states, the seal, the provenance line | `src/shared/ui/Actor.tsx`, `Seal.tsx`, `Provenance.tsx`, `StatusTag.tsx` (`VersionTag`) | New components; `VersionTag` keeps its props and takes the dashed · solid · struck · well grammar. |
| 05 Controls | `src/shared/ui/Button.tsx`, `Field.tsx`, `Kbd.tsx` | `Button` keeps `variant` (`primary`, `secondary`, `ghost` becomes `quiet`, `danger`) and `size`, gains the busy state that keeps its width; `Field` gains `counter` and the error glyph. |
| 06 Chips, tags and marks | `src/shared/ui/Chip.tsx`, `StatusTag.tsx` (`DecisionTag`), `Severity.tsx` | `DecisionTag` keeps its props; the words and glyphs are the spec's. `Severity` draws the mark by what the publish gate does (square, bar, triangle), not by a severity word. |
| 07 Tables | `src/shared/ui/Table.css` (new, `.table`, `.t-*`), `src/features/cases/DecisionList.tsx`, `src/features/rules/DecisionTable.tsx`, `tableModel.ts`, `cellGrammar.ts` | The base table and the decision table. `tableModel` gains the negated single leaf (`not` over one comparison renders `∉` or `≠`, read-only) and orders derived columns last (Document 3). |
| 08 Shell, overlays and states | `src/shared/layout/AppShell.tsx` (the rail), `WorkspaceHeader.tsx`, `SplitView.tsx` (sheet and margin), `src/shared/ui/States.tsx`, `Overlay.tsx` (dialog, popover, toast), `src/features/demo/GuidedPanel.tsx`, `src/shared/gate/AccessGate.tsx` | The rail's nav items stay `<button>` elements (the tests query them by role). |
| 09 Patterns | `src/features/rules/ReviewPanel.tsx`, `RuleDrawer.tsx`; `src/features/cases/TraceView.tsx`, `ExplainPanel.tsx`, `Dashboard.tsx`, `CaseForm.tsx` (new), `src/features/rules/FieldsPanel.tsx` (new); `src/features/chat/ChatScreen.tsx`; `src/features/change/ChangeScreen.tsx`, `DiffView.tsx`, `RegressionReport.tsx`; `src/features/audit/AuditScreen.tsx`, `AuditEntryView.tsx` | One pattern per component; the margin (`RuleDrawer`) replaces the drawer-over-content of today. |
| 10 Composed screens | `PoliciesScreen.tsx`, `RulesScreen.tsx`, `CasesScreen.tsx`, `ChatScreen.tsx`, `ChangeScreen.tsx`, `AuditScreen.tsx`, `App.tsx` | The screens only compose; they hold no style of their own beyond the sheet-and-margin grid. |
| 11 States | every feature's `*.test.tsx` with MSW fixtures | One test per cell of the matrix that is not yet covered. |
| 12 Checklist | `src/styles/register.lint.test.ts`, `e2e/register.spec.ts` | The "don't" column as automated checks (phase 0 and phase 6). |

## The CSS port

`register.css` is organised so that each layer lands in one place:

| Layer | Blocks | Lands in |
| --- | --- | --- |
| 1 Tokens | `:root`, `:root[data-theme="dark"]` | `src/styles/tokens.css`, replacing the file |
| 2 Base | element rules, `[lang="he"]`, `bdi`, `.mono`, `.muted`, `.num`, `.sr-only`, focus | `src/index.css` |
| 3 Primitives | `.btn`, `.field` and the inputs, `.chip`, `.tag`, `.vstatus`, `.sev`, `.actor`, `.seal`, `.prov`, `.note`, `.refusal`, `.kbd`, `.dot` | `src/shared/ui/<Component>.css`, one file per component |
| 4 Structures | `.shell`, `.rail`, `.demo`, `.ws-header`, `.sheet`, `.margin`, `.sec`, `.toolbar`, `.table` and `.t-*`, `.dialog`, `.popover`, `.toast`, `.palette`, the states | `src/shared/layout/*.css`, `src/shared/ui/Table.css`, `Overlay.css`, `States.css` |
| 5 Patterns | `.review`, `.finding*`, `.publish-box`, `.trace*`, `.step*`, `.hitmap*`, `.cmp`, `.explain*`, `.thread`, `.turn*`, `.change*`, `.udiff*`, `.matrix`, `.event*`, `.figures`, `.barlist`, `.policy`, `.para*`, `.kv`, `.gate`, `.case-form`, `.schema`, the phone rules | the feature's own `*.css` |
| 6 Spec-only | `.spec-*` | nowhere |

Class names: the components adopt the spec's names (`.btn.btn--primary`, `.table.table--decision`, `.chip.chip--para`, `.vstatus.vstatus--draft`, `.prov`, `.seal`, `.rail__link`, `.sheet__foot` and so on) and the old names (`.button`, `.shell__sidebar`, `.workspace-header`, `.drawer`) are deleted in the same pull request that replaces them. A class that has no rule in `register.css` does not exist. Where the spec's CSS uses a `style` attribute on a specimen, the product turns it into a modifier class in the same file.

Logical properties only (`inline-start`, `inline-end`, `margin-inline`, `padding-inline`), as the spec's layer 2 states; the one physical property is the `border-left` of `.he-quote`, on purpose.

## Running a phase

The board (`docs/register-status.md`) is the state; this document is the definition. "start phase N", said to an agent in the repository, means the following, and the CLAUDE.md section says the same in fewer words:

1. **Check the board.** Phase N starts only when phase N−1 is Done (phase 0 needs the Preparation row Done). Otherwise the agent says what is missing and stops. `main` is green and the working tree is clean before a branch is cut.
2. **Read, in this order.** The "Phase N" section of this document in full; the spec sections it names, by id; the screens it names; the `register.css` blocks it names; then the product files it lists, to know what is there today.
3. **Open the phase.** Branch `register/phase-N` from `main`. In the board: status In progress, the date, the branch.
4. **Tests first.** The tests named under "Tests first", written as named, failing tests, one file per component; the list is printed before any other code, as Document 6 asks. Expected values come from the spec, the glossary, the fixtures and Document 3, never from running the component.
5. **Build**, in the order the phase's "Build" list gives. Every existing test stays green; a wording the spec changed goes spec → test → component in the same pull request, and the description says so.
6. **Close the phase.** `npm test`, `npm run lint`, `npm run typecheck`, the end-to-end specs the phase names, the screens rendered beside their screenshots in both themes; every box of the phase's acceptance list ticked in the board with its evidence (the screenshot paths, the CI run, the pull request); the worklog line; the pull request titled as the phase says. Status Done only when every box is ticked and CI stages 1 to 7 are green.
7. **Blocked** is a status, not a guess: when a document is silent or two disagree, the agent writes the question under the phase in the board, sets Blocked, and stops.

"continue phase N" resumes at step 4 or 5 from the board's boxes. "status" or "phase status" means: read the board and report its table.

## The phases

| # | Phase | Spec sections | Size | Gate is checked by |
| --- | --- | --- | --- | --- |
| 0 | Foundation: fonts, tokens, base, the lint | 02, 03, 12 | half a day | every existing test green on the new tokens; the lint and the contrast test red on a planted literal |
| 1 | The shell: rail, header, sheet and margin, controls, states, the gate | 04, 05, 06, 08 | one day | the gate and the empty workspace in both themes beside `screens/screen-gate.png` |
| 2 | The tables: the case list and the decision table | 07 | one day | the Rules screen beside `screen-rules-light.png` and `-dark.png`; `e2e/rules.spec.ts` green |
| 3 | The review, the trace, the explanation, the figures | 09 (review, trace, figures) | one day | the Cases screen beside `screen-cases-*.png`; demo steps 1 and 2 through the panel |
| 4 | The assistant, the change request, the audit log | 09 (thread, change, audit) | one day | demo steps 3 and 4 through the panel; `e2e/chat.spec.ts`, `change.spec.ts` green |
| 5 | Policies, composition, Decide a case, Fields, every state, the phone | 09 (policy, case form, schema), 10, 11 | one day | the Policies screen beside `screen-policies-*.png`; the states matrix walked; the phone at 390px |
| 6 | Verification: screenshots, the audit, the don't list, the palette | 12, 08 (palette) | half a day | `e2e/register.spec.ts` green; `docs/demo/` regenerated; the README's design section |

Five and a half days of agent work. The order is the order of demo value: phases 0 to 3 carry steps 1 and 2 of the demo and are never cut; phase 4 carries steps 3 and 4; phase 5 and 6 are polish and proof. The cut order, if the calendar bites, is at the end of this document.

---

## Preparation

**Goal.** The design files are in the repository, the board exists, and every future session knows what "start phase N" means. The owner copies the bundle's `docs/` folder over the repository's `docs/` (nothing in it overwrites an existing file except `docs/README.md`, which gains one row) and then gives the prompt below.

**Files.** `docs/design/register.html`, `docs/design/register.css`, `docs/design/screens/` (eight PNGs), `docs/design/CLAUDE-register-section.md`, `docs/09-register-implementation.md`, `docs/register-status.md`, `CLAUDE.md`, `docs/README.md`.

**Build.** Append `docs/design/CLAUDE-register-section.md` to `CLAUDE.md` as its last section; add Document 9 and the board to the table in `docs/README.md`; verify the eight PNGs open and the HTML opens in a browser with both themes; set the Preparation row of the board to Done with the pull request.

**Acceptance.** The board's Preparation boxes ticked; the pull request "Document 9: the Register design language" merged with CI stages 1 to 6 green (no frontend file changes, so stage 7 does not run).

**Prompt.**

```text
start preparation

Document 9 of the PolicyPilot set has arrived: docs/09-register-implementation.md, docs/register-status.md, and docs/design/ (register.html, register.css, screens/, CLAUDE-register-section.md). Read docs/09-register-implementation.md up to and including "Running a phase", then its "Preparation" section.
Append docs/design/CLAUDE-register-section.md to CLAUDE.md as its last section, verbatim. Add Document 9 and the board to the documents table in docs/README.md. Verify the eight PNGs under docs/design/screens/ open and that docs/design/register.html renders in a browser in both themes (the toggle at its top).
Tick the Preparation boxes in docs/register-status.md, set its status to Done with the pull request link, and open the pull request "Document 9: the Register design language". Change nothing else.
```

---

## Phase 0: foundation

**Goal.** The new fonts, tokens and base rules are in, every existing test is green on them, and two automated checks make the rest of the plan safe: the contrast test knows both themes, and a lint fails the build on a literal value in component CSS.

**Read.** Spec sections 02 (Tokens), 03 (Typography and the bidi law), 12 (the checklist); `register.css` layers 1 and 2.

**Files.** `frontend/package.json`, `src/main.tsx`, `src/index.css`, `src/styles/tokens.css`, `src/styles/tokens.test.ts`, `src/styles/register.lint.test.ts` (new), `src/shared/i18n/direction.ts` and its test, `index.html` (the `data-theme` bootstrap).

**Tests first.**

- `tokens.test.ts`: every pair of the spec's section 02 at 4.5:1 for text and 3:1 for marks, in the light theme and in the dark theme; the test reads both blocks of the file (`token(name, theme)`), so a value cannot change without the test reading it. The named pairs: ink on paper and on sheet; ink-2 and ink-3 on paper, sheet, well and accent-wash; accent and accent-text on sheet and on accent-wash; each decision text on its own tint and on sheet; each decision mark against sheet at 3:1; the toast text on the toast background; the paper-on-ink primary button in the dark theme.
- `register.lint.test.ts`: scans every `src/**/*.css` except `tokens.css` and fails on a hex colour, an `rgb(`, a literal `px` font-size, a `text-transform: uppercase` outside `.seal`, a `linear-gradient`, the strings `Inter` and `Heebo`, and a `letter-spacing` outside `.seal` and `.t-band`; scans every `src/**/*.tsx` and fails on a `style={{` that sets a colour or a font size. It passes on the repository as this phase leaves it.
- `direction.test.ts`: `numberToken(150000, '₪', 'he')` renders `150,000 ₪` with a non-breaking space inside a `<bdi dir="ltr">`; `numberToken(-12)` uses U+2212; the English chrome puts `₪` before the number; a bare `<bdi>` around a number is never emitted (the function is the only way a number reaches Hebrew content).
- The theme: `App.test.tsx` gains "starts in the light theme and remembers a dark choice per browser" (the `data-theme` attribute on `<html>`, `localStorage` key `pp-theme`, light when nothing is stored).

**Build.**

1. Fonts: add the five `@fontsource` packages at the weights above, `font-display: swap` as they ship; import them in `main.tsx`; remove `inter` and `heebo` from the package and the imports.
2. `tokens.css`: layer 1 of `register.css`, verbatim, both themes. The comment at the top names the spec as its source. Keep the file's shape (one custom property per line) so `tokens.test.ts` can read it.
3. `index.css`: layer 2. The body is paper, ink, IBM Plex Sans at `--text-base`; `[lang="he"]` takes the Hebrew face and `--leading-he`; `.doc` takes Frank Ruhl Libre at `--text-doc`, never below 16px; `bdi` is `unicode-bidi: isolate`; `.mono` is Plex Mono with slashed zeros; focus is the two-ring outline of section 05.
4. `index.html`: a three-line inline script before the stylesheet applies the stored theme so the page never flashes; the default is light.
5. `direction.ts`: `numberToken` as specified; nothing else changes.
6. The old `--color-*` names stay for this phase as aliases at the bottom of `tokens.css` (`--color-text: var(--ink)` and so on), so every existing component keeps rendering; the aliases are deleted in phase 5, when the last old class is gone. `register.lint.test.ts` allows the alias block only in `tokens.css`.

**Acceptance.** `npm test`, `npm run lint`, `npm run typecheck` green; `tokens.test.ts` lists at least 40 pairs across the two themes; a planted `color: #123456` in any component CSS turns `register.lint.test.ts` red; the app renders in Plex in the browser (the Network tab shows no Google Fonts request); the light theme is the first paint; the worklog line names the phase.

**Cut line.** None; the phase is the floor.

**Prompt.**

```text
start phase 0

Phase 0 of Document 9 (docs/09-register-implementation.md): foundation.
Read docs/design/register.html sections 02, 03 and 12, and docs/design/register.css layers 1 and 2. Then read the "Phase 0" section of Document 9 in full.
Start by writing the tests named under "Tests first" as named, failing tests; print the list before any other code, then continue.
Then build the six items under "Build", in order. Keep the --color-* aliases at the bottom of tokens.css so no existing component breaks; the lint test allows them only there.
Do not touch any file under src/features/ or src/shared/ in this phase except src/shared/i18n/direction.ts.
Finish with: npm test, npm run lint, npm run typecheck green; a one-line worklog entry; the pull request titled "Register phase 0: fonts, tokens, base, the lint".
```

---

## Phase 1: the shell

**Goal.** Every screen sits in the Register's frame: the paper rail with the six screens, the workspace header with its provenance line and one primary action, the sheet and the margin, the controls, the chips and tags, the states, the overlays and the access gate. The screens' own content still looks like today until phases 2 to 5; the frame is done.

**Read.** Spec sections 04, 05, 06, 08; `register.css` layers 3 and 4; `screens/screen-gate.png`.

**Files.** `src/shared/layout/AppShell.tsx` and `.css`, `WorkspaceHeader.tsx` and `.css`, `SplitView.tsx` and `.css`, `screens.ts`; `src/shared/ui/Button.tsx`, `Field.tsx`, `StatusTag.tsx`, `States.tsx`, `Panel.tsx` (becomes `Section`), `Logo.tsx`, new `Actor.tsx`, `Seal.tsx`, `Provenance.tsx`, `Chip.tsx`, `Severity.tsx`, `Note.tsx`, `Refusal.tsx`, `Kbd.tsx`, `Overlay.tsx`; `src/features/demo/GuidedPanel.tsx` and `.css`; `src/shared/gate/AccessGate.tsx` and `.css`.

**Tests first.**

- `AppShell.test.tsx`: the rail lists the six screens as buttons in the demo's order; a count appears only beside Rules (findings to acknowledge) and Change (a proposal awaiting a decision), never beside the others; the workspace block shows the policy's name with `dir="rtl"` and `lang="he"`, the sandbox id in mono and the reset time; the provider line renders "Provider OpenAI · cloud" when `GET /system/provider` answers and nothing when it does not; the foot shows the person mark, the name recorded, Leave, Help and Theme; Help opens the legend and the shortcuts sheet in a popover; Theme toggles `data-theme` and stores it; the principle sentence is not in the rail (it lives on the gate and in the legend).
- `WorkspaceHeader.test.tsx`: the title at `--text-lg`, the version status beside it, the provenance line as one `.prov` element whose segments are children with no separator characters in the text (the separators are CSS); the header shows at most one secondary action and one primary; a disabled primary renders its reason as text beside it, in the DOM before the button.
- `Provenance.test.tsx`: the line never wraps (`white-space: nowrap` computed), overflows with `text-overflow: ellipsis`, and drops its last segments first when a `maxSegments` prop is given.
- `Seal.test.tsx`: renders the kicker in capitals as the only uppercase text in the document, the line in mono, the actor's name; `inline` renders the one-line form; the stamp class is applied on mount once and removed after `--t-seal`; `prefers-reduced-motion` skips it.
- `Button.test.tsx`: `primary` is ink on paper (paper on ink in the dark theme, asserted through the computed token), `secondary` bordered, `quiet` text-only, `danger` red text; `busy` keeps the button's width and replaces the label with the spinner; no icon renders on Run, Publish or Ask.
- `StatusTag.test.tsx`: `DecisionTag` renders Approved with a check, Declined with a cross, Manual review with the person glyph, Evaluation error in ink; `quiet` renders the dot form; `VersionTag` renders Draft dashed, Published solid, Superseded struck, "Seeded · read-only" on the well.
- `Chip.test.tsx`: an id chip is mono, a paragraph chip is the serif pill with `¶` and a non-breaking space, a tool chip is the outlined glyph; every chip that opens something is a `<button>` or an `<a>`; a chip inside Hebrew keeps 4px of air on each side.
- `Severity.test.tsx`: a conflict, an unsupported rule and a gap draw the square; an instruction in the text draws the bar; an ambiguity and a duplicate draw the triangle; the kind's word is always beside the mark.
- `States.test.tsx`: the empty state is ruled lines, one sentence and one action, no dashed frame; loading is the real header plus three still rows and one line of text, no animation; the refusal block renders the code in mono, one row per pointer and problem, and the closing sentence "Nothing was stored."
- `Overlay.test.tsx`: the dialog exists only with a question in its title and names what cannot be undone; the popover positions from its anchor; a toast confirms and offers the next step and disappears; the error toast stays until closed.
- `GuidedPanel.test.tsx` (existing, updated): collapsed to one line with "Guided demo · step n of 4"; open, it lists the four steps with the current one marked and never covers the sheet; every step's button keeps its role and name.
- `AccessGate.test.tsx` (existing, updated): the two-tone lockup, the title, the one sentence, the field in mono with `autocomplete="off"`, the primary at 36px, the three refusals in place, the honesty line, the three marks as a foot; no eyebrow, no illustration.
- `rtlSnapshot` of the gate and of the shell with a Hebrew policy name.

**Build.** Port layers 3 and 4 into the files above; adopt the spec's class names; delete `.button`, `.shell__sidebar`, `.shell__principle`, `.workspace-header`, `.panel` and their rules; `Panel` becomes `Section` (`.sec`, `.sec__title`, `.sec__side`) and every caller is updated; the rail's nav items stay `<button>` elements styled as text links; `SplitView` becomes the sheet-and-margin grid with `--margin-w` (340px, 440px when the margin holds a trace) and the drawer below 1200px; `AppShell` reads the counts it shows from the queries the screens already use (the review's blocking findings, the proposed change requests).

**Acceptance.** The gate, in both themes, beside `screen-gate.png`; every existing test green; the rail, header and margin in both themes on the cloud site; no rule of `register.css` layer 3 or 4 is missing from the product's CSS (a diff of the selectors, listed in the pull request).

**Cut line.** The palette (`⌘K`) is phase 6; the phone top bar is phase 5.

**Prompt.**

```text
start phase 1

Phase 1 of Document 9: the shell.
Read docs/design/register.html sections 04, 05, 06 and 08, docs/design/register.css layers 3 and 4, and docs/design/screens/screen-gate.png. Then read the "Phase 1" section of Document 9 in full.
List the tests named under "Tests first" as named, failing tests, one file per component; print the list before any component code, then continue.
Port layers 3 and 4 into the files named under "Files", adopting the spec's class names and deleting the old ones in the same pull request. The rail's screens stay <button> elements: the tests query them by role. Panel becomes Section. Keep every existing test green; when a test queries a word the spec changed, change the test to the spec's word in this pull request and say so in its description.
Do not change the features' own content yet (tables, review, trace, chat, change, audit): that is phases 2 to 5.
Finish with the gate rendered in both themes beside screen-gate.png, the pull request "Register phase 1: the shell", and the worklog line.
```

---

## Phase 2: the tables

**Goal.** The two tables the product is made of: the base table (the case list) and the decision table, in the Register's grammar, with the decision table lossless against the DSL.

**Read.** Spec section 07 in full, 03 (numbers), 06 (chips and marks); `register.css` `.table`, `.t-*` and the decision-table blocks; `screens/screen-rules-light.png`, `screen-rules-dark.png`.

**Files.** `src/shared/ui/Table.css` (new), `src/features/cases/DecisionList.tsx` and `.css`, `src/features/rules/DecisionTable.tsx` and `.css`, `tableModel.ts`, `cellGrammar.ts`, `RulesScreen.tsx` (the strip and the toolbar only), `Pickers.tsx`.

**Tests first.**

- `tableModel.test.ts` (existing, extended): columns list case fields in document order and derived fields last; a `not` over one `between` renders `∉ [10,000 .. 150,000]` in the field's column and is not editable; a `not` over one `eq` renders `≠ v`; a `not` over `in` renders `∉ {a, b}`; a `not` over anything else stays out of the cells and reads whole in the margin; an expression operand renders `ƒ` with the infix text.
- `DecisionTable.test.tsx` (existing, updated): two header rows, the first grouping Rule · Conditions · Source · Action, the second naming the fields in mono with the unit and the dashed "derived" tag; bands as rows with the range; the Hebrew label first and the id under it; the priority end-aligned; a gutter mark per finding on the row; an underline on the cell the finding names; the inactive rule dimmed with "inactive" beside its action and nothing struck through; the draft's editable cell as an input with the same grammar and the error bubble that names what to write ("Write the number alone; the column is in ₪."); a published or seeded version shows no input; the source column as the paragraph chip or the person mark; the action column as a decision tag or the mono action, a long code breaking at an underscore; the field header's `title` carries the field's Hebrew description, type, unit, values and paragraph; the rowheader count equals the rule count (`e2e/rules.spec.ts` relies on it).
- `DecisionTable.test.tsx`, the strip: "First hit · by priority; the first terminal decision stands", the blocking and warning counts, and "N fields to the right ›" computed from the container's width (`ResizeObserver` mocked).
- `DecisionList.test.tsx` (existing, updated): header 12px/500 on paper, sticky; rows at `--row-h`, Compact at `--row-h-compact` as a remembered choice; numbers end-aligned with one precision per column and the unit in the cell; ids in mono; the decision as the quiet tag; flags as field chips; the empty cell as the dash; the selected row with the accent bar; the footer with the count and scope and the three keyboard hints.
- `rtlSnapshot` of the decision table with the seeded rule set (existing snapshot, replaced deliberately in this pull request and reviewed line by line).

**Build.** `Table.css` from the spec's base table and decision-table blocks (frozen columns, the visible scrollbar, the band rows, the gutter, `.t-cmp`, `.t-action`, `.t-rule`, the two sticky header rows at `top: 0` and `top: 26px`); `DecisionTable` rebuilt on `tableModel` with the new columns and cells, the strip above it, the tag filter select and the version picker in the toolbar; the edit-in-cell round trip unchanged in logic, restyled; `DecisionList` on `Table.css` with the filters (case number, outcome, deciding rule), the density choice remembered in `localStorage`, the footer.

**Acceptance.** The Rules screen at 1376px in both themes beside the two screenshots (the margin is still today's drawer content until phase 3; compare the sheet); `e2e/rules.spec.ts` green; the snapshot diff reviewed; the strip's count matches the number of columns scrolled out of view at 1376px (4 for the lending policy).

**Cut line.** Compact density and the tag filter can move to phase 5.

**Prompt.**

```text
start phase 2

Phase 2 of Document 9: the tables.
Read docs/design/register.html section 07 in full, sections 03 (Numbers) and 06, the .table and .t-* blocks of docs/design/register.css, and docs/design/screens/screen-rules-light.png and screen-rules-dark.png. Then read the "Phase 2" section of Document 9 in full.
List the tests first, starting with tableModel.test.ts: the derived columns last and the negated single leaf are model changes with expected values taken from Document 3's cell grammar, not from running the code.
Build Table.css, then DecisionTable, then DecisionList. Replace the decision table's RTL snapshot deliberately and paste its diff into the pull request description.
Keep e2e/rules.spec.ts green without loosening a query.
Finish with the Rules screen rendered at 1376px in both themes beside the two screenshots, the pull request "Register phase 2: the tables", and the worklog line.
```

---

## Phase 3: the review, the trace, the explanation, the figures

**Goal.** Demo steps 1 and 2 read as the spec draws them: the review with its findings and the publish box in the Rules margin, the trace with its hit map and steps in the Cases margin, the explanation under it, and the outcome figures over the case list.

**Read.** Spec section 09, the parts "The review", "The trace", "Figures"; section 04 (the seal, the actor marks); `register.css` `.review`, `.finding*`, `.publish-box`, `.trace*`, `.step*`, `.hitmap*`, `.cmp`, `.explain*`, `.figures`, `.figure`, `.barlist`; `screens/screen-cases-light.png`, `screen-cases-dark.png`.

**Files.** `src/features/rules/ReviewPanel.tsx` and `.css`, `RuleDrawer.tsx` and `.css` (the margin's rule section), `findings.ts`; `src/features/cases/TraceView.tsx` and `.css`, `trace.ts`, `ExplainPanel.tsx` and `.css`, `Dashboard.tsx` and `.css`, `outcomes.ts`; `RulesScreen.tsx`, `CasesScreen.tsx` (the margin composition).

**Tests first.**

- `ReviewPanel.test.tsx` (existing, updated): the head "Review · 10 findings · 7 block publishing · 1 acknowledged" from the fixture; the lifecycle row with the system mark and the product's sentences ("Not reviewed yet.", "The review could not run.", "The draft was edited after its review; run the review again."); a finding as code, kind, the "Blocks publishing" ink tag when it blocks, the claim in Hebrew with `dir="rtl"`, the evidence chips on their own line, "What to do" above its Hebrew line; acknowledging opens the paper box in place: a note for an error, the three resolutions for a gap, nothing for a warning; the acknowledged state is the inline seal with the note hung beneath; the publish box lists the four gates with their facts, the reason sentence is the product's own `publishBlockers` text, and the button never hides.
- `findings.test.ts` (existing, extended): `markOf(finding)` is `square` for conflict, unsupported and gap, `bar` for injection, `triangle` for ambiguity and duplicate.
- `TraceView.test.tsx` (existing, updated): the head with Case n, the outcome tag, the provenance line "decided on v1 · engine 1.0.0 · 61 µs · time"; the hit map with one cell per rule in evaluation order, classes matched · did-not-match · not-reached · disabled and the deciding cell ringed; the legend; "Explain for an officer" and "Explain for the applicant" as buttons; Export JSON and CSV as links to `GET /decisions/{id}/export` with the Accept header; Case inputs first as two columns of `dt`/`dd`; "Decided by" with the chip, the Hebrew label and the reason under the label "Reason for the applicant"; Derived and Flags as named sections present when empty ("None: the decision came before the advisory rules."); the steps with Matched · Did not match · Not reached · Disabled and comparisons as Field · Expected · In the case · Result with "met" and "not met" beside the glyph; the collapsed footer "n rules that did not match are collapsed to their head · m not reached after the decision · Show every comparison"; the evaluation error head and strip.
- `ExplainPanel.test.tsx` (existing, updated): the model mark and the sentence "Written by a model from this trace alone; every rule and paragraph it cites was checked against the trace."; the pressed audience; the four parts; a citation chip after the sentence's punctuation; "Copy for the letter" only for the applicant; "No explanation was written; the trace below is the whole of the decision." when the API returns nothing.
- `Dashboard.test.tsx` (existing, updated): one proportional bar in evaluation order with 2px gaps; three figures with their share; "0 evaluation errors" as a footnote; "Rules that decided most often" with the dot of each rule's outcome, rows that filter the list, and "Declines only".
- `rtlSnapshot` of the trace and of the review (new).

**Build.** Port the pattern blocks; `TraceView` keeps rendering `trace_json` verbatim (Document 2, key decision 4): every word and number on the surface is the engine's, and `trace.ts` only groups; the hit map is derived from the steps' statuses; the explanation's chips use `Chip`; `Dashboard` uses the figures and the bar list with `dot--approve`, `dot--decline`, `dot--refer`; the Rules margin shows the rule (Rule chip and status, the Hebrew label, Condition, Action with terminal, Reason for the applicant, Priority and band, Source with the model's confidence, Decided in the last run, Since), the paragraph it cites with the quoted span marked, and the findings on it, as in `screen-rules-light.png`.

**Acceptance.** Demo steps 1 and 2 run through the guided panel on the cloud site and look like the spec; the Cases screen in both themes beside the two screenshots; `e2e/cases.spec.ts`, `generate.spec.ts`, `panel.spec.ts` green; the snapshots reviewed.

**Cut line.** Export links and "Copy for the letter" can move to phase 5 if the day runs out; nothing else.

**Prompt.**

```text
start phase 3

Phase 3 of Document 9: the review, the trace, the explanation, the figures.
Read docs/design/register.html section 09 ("The review", "The trace", "Figures") and section 04, the matching blocks of docs/design/register.css, and docs/design/screens/screen-cases-light.png, screen-cases-dark.png, screen-rules-light.png. Then read the "Phase 3" section of Document 9 in full.
List the tests first. The trace's expected texts come from fixtures/policies/consumer-lending/sample-decision.json and the engine's status words in the spec's glossary; never from running the component.
TraceView renders trace_json verbatim: no client-side logic may compute a value the engine did not emit.
Finish with demo steps 1 and 2 through the guided panel on the cloud site, the Cases and Rules screens rendered in both themes beside their screenshots, the pull request "Register phase 3: review, trace, explanation, figures", and the worklog line.
```

---

## Phase 4: the assistant, the change request, the audit log

**Goal.** Demo steps 3 and 4 in the Register: the thread read right to left with citations after punctuation, the change request from the field to the seal, and the append-only audit log with the compare control.

**Read.** Spec section 09, the parts "The assistant", "The change request", "Audit log"; section 03 (the bidi law, every clause); `register.css` `.thread`, `.turn*`, `.change*`, `.udiff*`, `.matrix`, `.event*`, `.he-quote`.

**Files.** `src/features/chat/ChatScreen.tsx` and `.css`, `markers.ts`, `chatReducer.ts` (no logic change), `failures.ts`; `src/features/change/ChangeScreen.tsx` and `.css`, `DiffView.tsx` and `.css`, `diffRows.ts`, `RegressionReport.tsx` and `.css`, `failures.ts`; `src/features/audit/AuditScreen.tsx` and `.css`, `AuditEntryView.tsx`.

**Tests first.**

- `ChatScreen.test.tsx` (existing, updated): the thread is a `dir="rtl"` container inside the English chrome; the person's and the model's marks sit on the reading-start side; a question is a Hebrew block, an answer a document block; a citation chip follows the sentence's punctuation and the sources strip repeats every chip; the decision chip reads "Case 17" (the spec's glossary; `markers.ts` changes its text and `e2e/chat.spec.ts` and `panel.spec.ts` are updated in this pull request); a tool call is a step line above the answer with the tool chip, what it ran on, the timing and the outcome as a real decision tag; the fixed not-covered sentence carries the system mark and the line "No source · a fixed sentence, not the model's"; a withheld answer is the amber note with the system mark; streaming shows the caret and no typing animation; the notes under the composer for RATE_LIMITED, PROVIDER_UNAVAILABLE, the indexing version and the spent budget, the composer enabled for all four and a scripted question answered from the cache while the budget is spent (the owner's answer of 2026-09-28).
- The chat's `rtlSnapshot` (existing) replaced deliberately.
- `ChangeScreen.test.tsx` (existing, updated): the request field with its counter and the base version beside the primary; the four stages with the rules considered under the first, touched ones in ink and the rest in ink-3; the proposal as patches by operation with the rationale in Hebrew, then "Considered and left unchanged"; the decision box with the note, Reject in the danger style, "Approve and publish v2" as the primary with "Approving publishes v2 in this sandbox's copy." beside it; the seal and the sentence after approval; "Rejected. Nothing was published." after rejection; the refusals as the refusal block with "What the model proposed" behind a link.
- `DiffView.test.tsx` (existing, updated): one row per changed cell, rule · field · before → after, the changed value tinted with `diff-del` and `diff-add`, the whole rule struck when removed, unchanged rules collapsed with a count, side by side as the alternative remembered per person.
- `RegressionReport.test.tsx` (existing, updated): "12 flipped · 6.0% of 200" first, the matrix with totals and the unchanged diagonal in ink-3 and the hot cells on the accent wash, flips by cause as the one-hue bar that filters the list, flags moved as a figure, each flipped case with "Both traces".
- `AuditScreen.test.tsx` (existing, updated): newest first, grouped by day; each row a provenance line with the actor's mark, the verb, the chips and the seal; the acknowledgement row reads "Finding acknowledged" with the finding's kind; the note as `.he-quote`; an approval expands to the request, the diff and the regression; the compare control with From and To and "Compare two versions"; "Export the log"; no edit or delete affordance anywhere on the screen; "Nothing recorded yet" on the ruled lines.
- `AuditEntryView` snapshot (existing) replaced deliberately.

**Build.** Port the blocks; the thread container gets `dir="rtl"` and `lang` per message from `direction.ts`; every number inside Hebrew goes through `numberToken`; `markers.ts` renders "Case n"; the change screen's stages reuse the progress pattern of section 08; the diff reuses `Table.css`; the audit's export calls `GET /audit/export` with the versions filter; the budget note reads `GET /system/budget` (the banner of Document 5).

**Acceptance.** Demo steps 3 and 4 through the guided panel on the cloud site; `e2e/chat.spec.ts`, `change.spec.ts`, `panel.spec.ts` green with the wording updated; the two snapshots reviewed; the thread checked in a browser with a Hebrew question that contains `−12` and `8,000 ₪` (the hard cases of section 03).

**Cut line.** Side by side in the diff (FR-20, Could) can wait for phase 5 or be left as the unified view with a known limitation.

**Prompt.**

```text
start phase 4

Phase 4 of Document 9: the assistant, the change request, the audit log.
Read docs/design/register.html section 09 ("The assistant", "The change request", "Audit log") and section 03 in full, and the matching blocks of docs/design/register.css. Then read the "Phase 4" section of Document 9 in full.
List the tests first. The chat's decision chip changes from "Application 17" to "Case 17": change markers.ts, the unit test, e2e/chat.spec.ts and e2e/panel.spec.ts in this pull request and say so in its description.
Every number inside Hebrew content passes through numberToken from direction.ts; a bare <bdi> around a number fails the review.
Finish with demo steps 3 and 4 through the guided panel on the cloud site, the two snapshots replaced and pasted into the pull request, the pull request "Register phase 4: assistant, change, audit", and the worklog line.
```

---

## Phase 5: Policies, composition, Decide a case, Fields, every state, the phone

**Goal.** The last screen (Policies) and the two additions the personas need (Decide a case for the officer, Fields for the analyst); every screen composed as section 10 draws it; every cell of the states matrix rendered; the phone layout; the old tokens gone.

**Read.** Spec section 09 ("Decide a case, and the field schema"), sections 10 and 11; `register.css` `.policy`, `.para*`, `.docs`, `.doc-row`, `.case-form`, `.schema`, the phone block; `screens/screen-policies-*.png`, `pattern-decide-a-case.png`.

**Files.** `src/features/policy/PoliciesScreen.tsx` and `.css`, `PolicyText.tsx`, `AddPolicyForm.tsx`, `GenerationProgress.tsx`; `src/features/cases/CaseForm.tsx` (new), `CasesScreen.tsx`; `src/features/rules/FieldsPanel.tsx` (new), `RulesScreen.tsx`; every screen's composition; `src/styles/tokens.css` (the alias block removed); `App.tsx`.

**Tests first.**

- `PoliciesScreen.test.tsx` (existing, updated): the sheet is the policy in the serif at 17px with paragraph numbers in the gutter; under each paragraph the rule chips and the finding marks that name it; the model's draft announced by the dashed note with the model's mark and "Review the draft"; the margin with the documents (the seeded one first, "Seeded"), the generation's four stages, each with its count, and the run's time (the owner's answer of 2026-09-28 to the phase's first question), and the review's summary with "n block publishing"; "Add policy" opens the form of section 05 with Paste text and Upload a file.
- `CaseForm.test.tsx` (new): one field per line in schema order from the version's `fields`; the Hebrew description first, the mono name with type, unit and domain under it; an enum as a select of its values, a boolean as a checkbox with a sentence, a derived field never shown; required fields marked, optional ones said so; the foot "Decided by v1, the published version, and recorded like any other decision." with Decide as the primary; Decide posts one `case` to `POST .../decide` and opens the trace; a `CASE_INVALID` refusal names the field under its input and stores nothing; "Decide a case" is the Cases header's secondary action.
- `FieldsPanel.test.tsx` (new): every field of the version with its name in mono, type, unit, required or derived, the Hebrew description, the values of an enum, and the paragraph chip that implied it; a field with no source and no use shows the validator's `FIELD_UNUSED` word beside it; the panel is the margin switch's Fields (Policy · Rule · Fields · JSON), which a version with no review opens on when no rule is selected, a draft keeping its review (the owner's answer of 2026-09-28 to the phase's second question).
- The states matrix: one test per cell of section 11 that no existing test covers, each with an MSW fixture, named "<surface> · <state>" (for example "Cases · empty", "Assistant · budget spent", "Change · VERSION_STATUS_CONFLICT", "Audit log · two versions the same").
- `register.lint.test.ts`: the alias block is gone and the test no longer allows it.
- Phone: `AppShell.test.tsx` gains the top bar below 720px (matchMedia mocked); Playwright `e2e/phone.spec.ts` renders the four demo screens at 390×844 and asserts no horizontal scroll and 44px rows.

**Build.** Port the policy blocks; `CaseForm` and `FieldsPanel` from `FieldSchema`; compose every screen as section 10 (the header's actions, the sheet, the margin's sections in the spec's order); walk the states matrix; the phone block of `register.css`; delete the `--color-*` aliases and every reference to them.

**Acceptance.** The Policies screen in both themes beside its screenshots; every cell of section 11 has a test; the phone spec green; `register.lint.test.ts` green with the aliases gone; the four demo steps through the panel on the cloud site, on a desktop and on a phone.

**Cut line.** In this order: the phone polish beyond "nothing overflows", then `FieldsPanel`, then `CaseForm` (both are additions to the product; each leaves a line in the README's known limitations if cut).

**Prompt.**

```text
start phase 5

Phase 5 of Document 9: Policies, composition, Decide a case, Fields, every state, the phone.
Read docs/design/register.html section 09 ("Decide a case, and the field schema"), sections 10 and 11, the matching blocks of docs/design/register.css, and docs/design/screens/screen-policies-light.png, screen-policies-dark.png and pattern-decide-a-case.png. Then read the "Phase 5" section of Document 9 in full.
List the tests first, including one test per cell of the states matrix that is not yet covered; name each "<surface> · <state>".
CaseForm and FieldsPanel read the version's fields block and call routes that exist; do not add or change a route.
Remove the --color-* alias block from tokens.css and every reference to it; register.lint.test.ts must pass without the allowance.
Finish with every screen rendered beside its screenshots, the four demo steps through the panel on desktop and on a phone, the pull request "Register phase 5: Policies, composition, states, phone", and the worklog line.
```

---

## Phase 6: verification and the don't list

**Goal.** Proof that the product is the spec: a screenshot suite in both themes, the automated audit of the spec's own checks, the "don't" column as tests, the palette, the README's design section and the regenerated demo screenshots.

**Read.** Spec sections 12 and 08 ("Go to anything").

**Files.** `e2e/register.spec.ts` (new), `e2e/palette.spec.ts` (new), `src/shared/ui/Palette.tsx` (new), `docs/demo/`, `README.md`, `docs/README.md`.

**Tests first.**

- `e2e/register.spec.ts`: for each of the seven screens (the gate, Policies, Rules, Cases, Assistant, Change, Audit log) in each theme at 1376×900: a screenshot into `docs/demo/register/<screen>-<theme>.png`; no element of the workspace wider than its container except `.table-scroll`, `.prov` and the intentional bleeds; no text under 11px and no chrome text under 12px; no text below 4.5:1 against its background (the audit of the spec, ported); no `text-transform: uppercase` outside `.seal`; no element with a gradient background; the fonts loaded are Plex and Frank Ruhl Libre only; the first paint is light.
- `e2e/palette.spec.ts`: `⌘K` opens the palette; typing `17` lists Case 17 with its decision tag and "open the trace"; `R-170` lists the rule with its Hebrew label and "open in the table"; `¶ 4` and `F-1` resolve; `Esc` closes; the arrow keys move and Enter opens.
- The four Playwright demo specs green in one run (FR-23, Document 7 day 16).

**Build.** `Palette` on the four kinds of identifier the API can resolve (case, rule, paragraph, finding, change, version) with the rows of section 08; the register spec; the README gains a section "The design language" with the principle sentence, the six principles of section 01 in one paragraph each, the readers table, and links to `docs/design/`; `docs/demo/` is regenerated from the register spec's screenshots; `docs/README.md` lists Document 9.

**Acceptance.** `e2e/register.spec.ts` and `palette.spec.ts` green in CI stage 7; `docs/demo/` shows the Register; the README section merged; the Definition of Done walk of Document 7 day 16 repeated with the design's checklist (spec section 12) and every red line either fixed or listed under known limitations.

**Cut line.** The palette first (the rail and the chips already reach everything); the README section last.

**Prompt.**

```text
start phase 6

Phase 6 of Document 9: verification and the don't list.
Read docs/design/register.html sections 12 and 08 ("Go to anything"). Then read the "Phase 6" section of Document 9 in full.
Write e2e/register.spec.ts first: the seven screens in both themes as screenshots into docs/demo/register/, plus the audit assertions listed under "Tests first". Run it before you build anything else and fix what it finds.
Then the palette, the README section, docs/demo and docs/README.md.
Finish with CI stages 1 to 7 green, the pull request "Register phase 6: verification", and the worklog line.
```

---

## Schedule and the cut order

The interview is on Monday 2026-10-05. Document 7 gives days 10 to 16 to building (to Monday 2026-09-28), Tuesday 2026-09-29 to what slipped, and days 17 to 19 (2026-09-30 to 2026-10-04) to rehearsals, the video, the secrets rotation and the freeze; the freeze rule allows a change after the tag only when a rehearsal proved it necessary. The design is five and a half days of agent work, so it does not fit before the rehearsals whole. The proposal, for the owner to confirm:

| Day | Phases | Note |
| --- | --- | --- |
| Sat 2026-09-26 | 0 and 1 | The frame changes every screen at once; the first rehearsal-quality screenshot exists at the end of the day |
| Sun 2026-09-27 | 2 | The tables; the Rules screen is the demo's first wow moment |
| Mon 2026-09-28 | 3 | Steps 1 and 2 complete in the Register |
| Tue 2026-09-29 | 4 | Steps 3 and 4; this is the slipped-items day of Document 7, spent here on purpose |
| Wed 2026-09-30 | 5 | The first rehearsal day; the design's phase 5 counts as the rehearsal's fix list, because every one of its cells is a state the rehearsal would reveal |
| Thu 2026-10-01 | 6 | The proof, the screenshots and the README, before the video is recorded |
| Fri to Sun | nothing new | Rehearsals on a machine that is not the developer's, the video, the freeze |

If a day slips, cut in this order and write the cut in the README's known limitations in the pull request that would have shipped it: the palette (phase 6); the phone beyond "nothing overflows" (phase 5); `FieldsPanel`, then `CaseForm` (phase 5); side by side in the diff (phase 4); Compact density and the tag filter (phase 2). Never phases 0 to 3: the demo's steps 1 and 2 live there, and a half-redesigned product is worse than either whole.

## What every phase keeps

- **The vocabulary** of spec section 01: one word for each thing. A test that finds a synonym on a screen is a failing test.
- **The bidi law** of section 03, all eleven clauses. The two that are easiest to break: a number inside Hebrew goes through `numberToken` (never a bare `<bdi>`), and a Hebrew value never shares a row with an English label (label above, or the `.he-quote` hung from the start edge).
- **Colour is a decision.** Green, red and amber only beside Approved, Declined and Manual review and on a changed cell in a diff; the brand blue only for selection, links and the seal; the person mark is ink.
- **Marks over words, words beside marks.** Every mark (actor, severity, hit-map cell, decision dot) carries its word or a `title`; nothing is colour alone.
- **The trace is the engine's.** `TraceView` renders `trace_json` verbatim; the explanation renders what the API returned after its own checks; the figures are the API's aggregates. Nothing on those surfaces is computed by the client.
- **One primary action per screen**, in ink on paper (paper on ink in the dark theme); a disabled primary says why beside it.
- **Motion** at 120ms and 180ms only, the seal's 320ms stamp, and the one flash of a deep link; `prefers-reduced-motion` collapses all of it.
- **Cached outputs look like any other result.** No "demo mode" badge, no "cached" word on a screen; the response cache is a talking point, not a label.

## Known tensions, decided here

| Tension | Decision |
| --- | --- |
| Document 7 lists the provider switch for day 16; the rail's provider line needs `GET /system/provider` | The line renders when the route answers and is absent until then; the spec's rail shows it so the agent builds the slot now |
| Document 3 wants an `enabled` toggle in the table's identity group; the spec shows no switch | The Rules margin's rule section carries "Enabled" as a checkbox with a sentence ("This rule runs; unchecked, the engine skips it and the table dims it"), on a draft only; the table shows the state, never the control |
| Document 3 shows derived columns with "a distinct header color"; the spec forbids colour as a status | The dashed "derived" tag on the header is the distinct header; no colour |
| The brief's demo says "top rejection reasons"; the API's aggregate is the top deciding rules | "Declines only" filters the bar list to declining rules, which is what "top rejection reasons" means in this product |
| The product says "Application 17" in the chat's chip and "Case" everywhere else | "Case", by the glossary; the chat's markers and two end-to-end tests change in phase 4 |
| Document 2 wants the analyst to confirm the field schema before publishing; no route exists for it | `FieldsPanel` shows the schema so the analyst can read it; confirmation stays a product decision outside this plan and is listed under known limitations |
