## Register: the design language (Document 9)

The interface is being rebuilt on the Register design language. Its files: `docs/design/register.html` (the spec,
twelve sections, each a `<section id>`: concept, tokens, type, actors, controls, chips, tables, layout, patterns,
screens, states, checklist), `docs/design/register.css` (the spec's CSS in six layers; layers 1 to 5 are ported,
layer 6 never ships), `docs/design/screens/` (what done looks like, both themes), `docs/09-register-implementation.md`
(the plan: seven phases, 0 to 6, each with goal, what to read, files, tests first, build, acceptance, cut line and a
prompt) and `docs/register-status.md` (the board: the state of every phase, written only by the agent running it).

Three rules hold for every phase: the spec's glossary rules the words (a wording change goes spec → test → component
in one pull request); `tokens.css` rules the values (no hex, no pixel size, no duration in a component; the lint test
fails the build on one); the backend is not touched.

**"start phase N"** means, in this order, with no further instruction needed:

1. Read `docs/register-status.md`. Phase N starts only when phase N−1 is Done (phase 0 needs the Preparation row
   Done). Otherwise say what is missing and stop. `main` is green and the tree is clean before a branch is cut.
2. Read the "Phase N" section of `docs/09-register-implementation.md` in full, then the spec sections it names (by
   id), the screens it names, the `register.css` blocks it names, then the product files it lists.
3. Branch `register/phase-N` from `main`. In the board: status In progress, the date, the branch.
4. Write the tests named under "Tests first" as named, failing tests; print the list; then red, green, refactor.
   Expected values come from the spec, the glossary, the fixtures and Document 3, never from running the component.
5. Build in the order of the phase's "Build" list. Every existing test stays green; a test that queries a word the
   spec changed is updated to the spec's word in the same pull request, and the description says so.
6. Close: `npm test`, `npm run lint`, `npm run typecheck`, the end-to-end specs the phase names, the screens rendered
   in both themes beside their screenshots; tick every acceptance box in the board with its evidence (screenshot
   paths, CI run, pull request); the worklog line; the pull request titled as the phase says. Status Done only when
   every box is ticked and CI stages 1 to 7 are green.
7. When a document is silent or two disagree, write the question under the phase in the board, set Blocked, stop.

**"continue phase N"** resumes from the board's unticked boxes. **"status"** means: read the board and report its
table. A cut is allowed only in the order Document 9 gives, is written in the board's Cuts table and in the README's
known limitations in the pull request that would have shipped the item, and is never a phase 0 to 3 item.
