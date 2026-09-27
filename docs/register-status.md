# Register: the board

The state of every phase of Document 9 (`docs/09-register-implementation.md`). The agent that runs a phase is the only writer of this file: it sets In progress when it cuts the branch, ticks a box only after the tests behind it are green, and sets Done only when every box of the phase is ticked and CI stages 1 to 7 are green. The owner reads it; "status" said to an agent means this table, reported as it stands.

States: **Not started** · **In progress** (branch, date) · **Blocked** (the question is written under the phase) · **Done** (pull request, date, evidence) · **Cut** (the reason and the README's known-limitation line).

| # | Phase | Status | Branch | Pull request | Started | Finished |
| --- | --- | --- | --- | --- | --- | --- |
| P | Preparation: the design files committed | Done | `register/preparation` | [#158](https://github.com/liorshaya/PolicyPilot/pull/158) | 2026-09-27 | 2026-09-27 |
| 0 | Foundation: fonts, tokens, base, the lint | Not started |  |  |  |  |
| 1 | The shell | Not started |  |  |  |  |
| 2 | The tables | Not started |  |  |  |  |
| 3 | The review, the trace, the explanation, the figures | Not started |  |  |  |  |
| 4 | The assistant, the change request, the audit log | Not started |  |  |  |  |
| 5 | Policies, composition, Decide a case, Fields, every state, the phone | Not started |  |  |  |  |
| 6 | Verification and the don't list | Not started |  |  |  |  |

A phase starts only when the one above it is Done. Cuts follow the order at the end of Document 9 and nowhere else.

---

## P · Preparation

Status: Done

- [x] `docs/design/register.html`, `docs/design/register.css`, `docs/design/screens/` (eight PNGs) committed unchanged
- [x] `docs/09-register-implementation.md` and this file committed
- [x] The Register section appended to `CLAUDE.md` (from `docs/design/CLAUDE-register-section.md`)
- [x] `docs/README.md` lists Document 9 and the board
- [x] Pull request "Document 9: the Register design language" merged, CI green

Evidence:

- The bundle's 13 files, byte for byte (the sha256 of each against the zip); nothing in `docs/` overwritten or deleted, nothing outside it.
- `CLAUDE.md` ends with the section after one blank line; its last 33 lines are `docs/design/CLAUDE-register-section.md` unchanged. `docs/README.md` has row 9: Document 9, linking the spec and the board.
- Opened on 2026-09-27: the eight PNGs decode in full (every chunk's CRC, the pixels inflated to their exact size) and in Chromium, 2722 px wide, each the screen its name says. `docs/design/register.html` in Chromium (Playwright 1.63.0, from the file, 1376×900): the twelve sections render with no page error; the toggle goes light → dark → light, `--paper` `#f5f4ef` ↔ `#111417` and `--ink` `#142c43` ↔ `#e7e9ec` as the spec's two token blocks set them, `aria-pressed` and the label following, and the choice survives a reload.
- Pull request [#158](https://github.com/liorshaya/PolicyPilot/pull/158): CI stages 1 to 7 green on run [36347254361](https://github.com/liorshaya/PolicyPilot/actions/runs/36347254361). The last commit, which ticks the fifth box and sets Done, is merged only once its own run is green too.

## 0 · Foundation

Status: Not started

- [ ] The five `@fontsource` packages installed at the spec's weights; `inter` and `heebo` removed; `main.tsx` imports only the new ones
- [ ] `tokens.css` is layer 1 of `register.css`, both themes, with the `--color-*` aliases at the bottom
- [ ] `tokens.test.ts` reads both themes and holds at least 40 pairs, every one green
- [ ] `register.lint.test.ts` exists, passes, and turns red on a planted `color: #123456` in a component CSS file
- [ ] `index.css` is layer 2: paper, ink, Plex, `[lang="he"]`, `.doc`, `bdi`, `.mono`, focus
- [ ] `index.html` applies the stored theme before the first paint; the default is light
- [ ] `numberToken` in `direction.ts` with its tests (non-breaking space, U+2212, the English order, never a bare `bdi`)
- [ ] `App.test.tsx`: light by default, dark remembered per browser
- [ ] `npm test`, `npm run lint`, `npm run typecheck` green; no Google Fonts request in the browser
- [ ] Worklog line; pull request "Register phase 0: fonts, tokens, base, the lint" merged

Evidence:

## 1 · The shell

Status: Not started

- [ ] Layers 3 and 4 of `register.css` ported into the files Document 9 names; the old classes (`.button`, `.shell__sidebar`, `.shell__principle`, `.workspace-header`, `.panel`, `.drawer`) deleted
- [ ] `Panel` renamed `Section`; every caller updated
- [ ] The rail: six screens as `<button>` elements, counts only beside Rules and Change, the workspace block (Hebrew name, sandbox, reset time, provider line when the route answers), the guided demo strip, the foot (person mark, Leave, Help, Theme)
- [ ] Help opens the legend and the shortcuts sheet; Theme toggles `data-theme` and stores it
- [ ] The workspace header: title, version status, one `.prov` line with CSS separators, at most one secondary action, the reason beside a disabled primary, the primary at 36px
- [ ] `SplitView` is the sheet-and-margin grid (`--margin-w`, 440px for a trace) with the drawer below 1200px
- [ ] `Button`, `Field`, `DecisionTag`, `VersionTag`, `Chip`, `Actor`, `Seal`, `Provenance`, `Severity`, `Note`, `Refusal`, `Kbd`, `Overlay`, `States` built with the tests Document 9 names, all green
- [ ] The access gate matches `docs/design/screens/screen-gate.png` in both themes (paths of the two product screenshots in the evidence)
- [ ] `rtlSnapshot` of the gate and of the shell with a Hebrew policy name, reviewed
- [ ] Every existing test green; every wording change listed in the pull request description
- [ ] The selector diff of layers 3 and 4 against the product's CSS listed in the pull request; nothing missing
- [ ] Worklog line; pull request "Register phase 1: the shell" merged

Evidence:

## 2 · The tables

Status: Not started

- [ ] `tableModel`: derived columns last; a `not` over one `between`, `eq` or `in` renders `∉ […]`, `≠ v`, `∉ {…}` read-only; any other `not` stays out of the cells; tests with values from Document 3
- [ ] `Table.css` from the spec's base-table and decision-table blocks: two sticky header rows (0 and 26px), frozen Rule and Action columns, the visible scrollbar, bands, gutter, `.t-cmp`, `.t-action`, `.t-rule`
- [ ] `DecisionTable`: two header rows, bands, Hebrew label first with the id under it, priority end-aligned, gutter marks and cell underlines, the inactive rule dimmed with "inactive" and nothing struck, the draft's cell input with the bubble "Write the number alone; the column is in ₪.", no input on a published or seeded version, source chip or person mark, action tag or mono code breaking at an underscore, the field header's `title`
- [ ] The strip: "First hit · by priority; the first terminal decision stands", the block and warn counts, "N fields to the right ›" from the container's width
- [ ] The toolbar: the version picker and the tag filter
- [ ] `DecisionList`: sticky 12px header, `--row-h` and Compact remembered, end-aligned numbers with one precision per column, mono ids, quiet decision tags, flag chips, the dash for an empty cell, the accent bar on the selected row, the footer with count, scope and the three keyboard hints, the three filters
- [ ] The decision table's `rtlSnapshot` replaced deliberately; the diff pasted into the pull request and reviewed line by line
- [ ] `e2e/rules.spec.ts` green without a loosened query
- [ ] The Rules screen at 1376px in both themes beside `screen-rules-light.png` and `screen-rules-dark.png` (the sheet; the margin is phase 3)
- [ ] Worklog line; pull request "Register phase 2: the tables" merged

Evidence:

## 3 · The review, the trace, the explanation, the figures

Status: Not started

- [ ] `findings.ts`: `markOf` gives square for conflict, unsupported and gap, bar for injection, triangle for ambiguity and duplicate
- [ ] `ReviewPanel`: the head with the counts, the lifecycle row with the product's sentences, findings with code, kind, "Blocks publishing" when it blocks, the Hebrew claim, evidence chips, "What to do" above its line; the acknowledgement box in place (note for an error, three resolutions for a gap, nothing for a warning); the inline seal when acknowledged; the publish box with the four gates and `publishBlockers`' sentence; the button never hides
- [ ] `TraceView` renders `trace_json` verbatim: the head (Case n, outcome tag, provenance line, hit map with legend and the ringed deciding cell, the two Explain buttons, Export JSON and CSV), Case inputs first, "Decided by" with chip, label and "Reason for the applicant", Derived and Flags present when empty, the steps with the engine's words and Field · Expected · In the case · Result, the collapsed footer, the evaluation error head and strip
- [ ] `ExplainPanel`: the model mark and the fixed sentence, the pressed audience, the four parts, chips after punctuation, "Copy for the letter" for the applicant only, the sentence when nothing was written
- [ ] `Dashboard`: the proportional bar, three figures with shares, "0 evaluation errors" as a footnote, the bar list with outcome dots, rows that filter, "Declines only"
- [ ] The Rules margin: the rule section (chip and status, label, Condition, Action with terminal, Reason for the applicant, Priority and band, Source with confidence, Decided in the last run, Since), the cited paragraph with the quoted span, the findings on the rule
- [ ] `rtlSnapshot` of the trace and of the review, reviewed
- [ ] Demo steps 1 and 2 through the guided panel on the cloud site
- [ ] `e2e/cases.spec.ts`, `generate.spec.ts`, `panel.spec.ts` green
- [ ] The Cases screen in both themes beside `screen-cases-light.png` and `screen-cases-dark.png`; the Rules screen beside its two, margin included
- [ ] Worklog line; pull request "Register phase 3: review, trace, explanation, figures" merged

Evidence:

## 4 · The assistant, the change request, the audit log

Status: Not started

- [ ] `ChatScreen`: the `dir="rtl"` thread, marks on the reading-start side, question and answer blocks, citation chips after punctuation with the sources strip, the tool-call step line, the fixed sentence with the system mark and "No source · a fixed sentence, not the model's", the withheld answer note, the caret while streaming, the notes under the composer (RATE_LIMITED, PROVIDER_UNAVAILABLE, indexing, budget; the composer disabled only for the budget)
- [ ] `markers.ts` renders "Case n"; `e2e/chat.spec.ts` and `e2e/panel.spec.ts` updated to the word in the same pull request
- [ ] Every number inside Hebrew content passes through `numberToken`; no bare `bdi` around a number anywhere in `src/features/chat`
- [ ] `ChangeScreen`: the request field with its counter and the base version beside the primary, the four stages with the rules considered, the patches with rationale and "Considered and left unchanged", the decision box (note, Reject in danger, "Approve and publish v2" with its sentence), the seal and the sentence after approval, "Rejected. Nothing was published.", the refusal block with "What the model proposed"
- [ ] `DiffView`: one row per changed cell with `diff-del` and `diff-add`, the removed rule struck, unchanged rules collapsed with a count, side by side remembered per person (or cut, with the README line)
- [ ] `RegressionReport`: "12 flipped · 6.0% of 200" first, the matrix with totals, the diagonal in ink-3 and the hot cells on the accent wash, flips by cause as the bar that filters, flags moved, "Both traces" per flipped case
- [ ] `AuditScreen`: newest first by day, provenance rows with the actor's mark, verb, chips and seal, "Finding acknowledged" with the kind, `.he-quote` notes, the expandable approval, the compare control, "Export the log", no edit or delete affordance, "Nothing recorded yet" on ruled lines
- [ ] The chat's `rtlSnapshot` and the `AuditEntryView` snapshot replaced deliberately, pasted into the pull request, reviewed
- [ ] The thread checked in a browser with a Hebrew question containing `−12` and `8,000 ₪`
- [ ] Demo steps 3 and 4 through the guided panel on the cloud site; `e2e/chat.spec.ts`, `change.spec.ts`, `panel.spec.ts` green
- [ ] Worklog line; pull request "Register phase 4: assistant, change, audit" merged

Evidence:

## 5 · Policies, composition, Decide a case, Fields, every state, the phone

Status: Not started

- [ ] `PoliciesScreen`: the policy in the serif at 17px with paragraph numbers, rule chips and finding marks under each paragraph, the model's dashed note with "Review the draft", the margin with the documents, the generation's four stages, the review summary; "Add policy" opens the form with Paste text and Upload a file
- [ ] `CaseForm`: from the version's `fields`, Hebrew description first, mono name with type, unit and domain, enum as a select, boolean as a checkbox with a sentence, derived fields absent, required and optional marked, the foot sentence and Decide as the primary; Decide posts one `case` and opens the trace; `CASE_INVALID` names the field under its input; "Decide a case" is the Cases header's secondary action (or cut, with the README line)
- [ ] `FieldsPanel`: every field with name, type, unit, required or derived, the Hebrew description, enum values, the paragraph chip; `FIELD_UNUSED` beside an unused field; the Rules margin's default section (or cut, with the README line)
- [ ] Every screen composed as section 10: header actions, sheet, margin sections in the spec's order
- [ ] One test per cell of the states matrix (section 11) not covered before, each named "<surface> · <state>", each with its MSW fixture, all green
- [ ] The phone: the top bar below 720px, the screens row, the margin as the next section; `e2e/phone.spec.ts` at 390×844 with no horizontal scroll and 44px rows
- [ ] The `--color-*` alias block removed from `tokens.css` with every reference; `register.lint.test.ts` green without the allowance
- [ ] The Policies screen in both themes beside `screen-policies-light.png` and `screen-policies-dark.png`; the four demo steps through the panel on a desktop and on a phone
- [ ] Worklog line; pull request "Register phase 5: Policies, composition, states, phone" merged

Evidence:

## 6 · Verification and the don't list

Status: Not started

- [ ] `e2e/register.spec.ts`: seven screens × two themes at 1376×900 as screenshots into `docs/demo/register/`; the overflow, size, contrast, uppercase, gradient and font assertions; the first paint light
- [ ] `Palette` with `e2e/palette.spec.ts` (or cut, with the README line)
- [ ] The four demo specs green in one Playwright run
- [ ] `docs/demo/` regenerated from the Register; `docs/README.md` lists Document 9; the README's section "The design language" merged
- [ ] The Definition of Done walk of Document 7 day 16 repeated with spec section 12; every red line fixed or listed under known limitations
- [ ] CI stages 1 to 7 green; worklog line; pull request "Register phase 6: verification" merged

Evidence:

---

## Cuts

| Item | Phase | Cut on | Reason | README known-limitation line |
| --- | --- | --- | --- | --- |
|  |  |  |  |  |

## Questions for the owner

Written by the agent when a phase is Blocked; answered here by the owner, then the phase resumes.

| Date | Phase | Question | Answer |
| --- | --- | --- | --- |
|  |  |  |  |
