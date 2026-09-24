# Recordings

What a provider actually answered, so every test after the first live run is offline and deterministic
(Document 6, AI Layer Testing).

```
recordings/<provider>/<prompt>/<version>/<inputHash>.json        what RecordedGateway replays
recordings/<provider>/<prompt>/<version>/<inputHash>.runN.json   the numbered runs behind a gate's measurement
```

The numbered files are every answer of a batch as it came. The file without a number is a copy of the first run
that validated: a gate that allows one failure in ten must not leave that one failure as the answer CI replays.

Each file holds the request (prompt name, version, model, the hash of the rendered prompts, and the prompts
themselves) and the raw response. The hash is the SHA-256 of `system + U+001F + user`, which is what the gateway
looks a recording up by: a new prompt version or a new input needs one live run to create its recording, and a
test that cannot find one fails rather than answering something else.

Recordings are made by the `Live*RecordingIT` classes (authoring, review, answer, explain and change), which are
tagged `live` and never run in CI; each one's Javadoc has its command, for example:

```
OPENAI_API_KEY=... ./mvnw verify -Dtest=none -Dit.test=LiveAuthoringRecordingIT -Dlive.tag= \
    -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -Djacoco.skip=true
```

The passes of evaluation run 2 (`LiveRetrievalRecordingIT`, `LiveAuthorPassIT`, `LiveReviewRecordingIT`,
`LiveAnswerPassIT` and `LiveChangePassIT`) record the provider named by `-Dprovider` under its own profile and into
its own folder, `openai` when it is not given (Document 4: a column each). For Ollama, with the profile's models
pulled, the retrieval pass first, since the others retrieve on its vectors:

```
OLLAMA_BASE_URL=http://localhost:11434 ./mvnw verify -Dprovider=ollama -Dtest=none \
    -Dit.test=LiveRetrievalRecordingIT -Dlive.tag= \
    -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -Djacoco.skip=true
```

Hand-written adversarial answers live beside the real ones with a `"synthetic": true` marker (Document 6).

## Embeddings

What the embedding provider returned, so retrieval tests replay real vectors offline (Work Plan day 8: one live
embedding pass over the 30 questions).

```
recordings/<provider>/embedding/<model>/<corpus>.json      one file per policy corpus, questions.json and changes.json
```

Each file names its `provider`, `model`, `dimension` and `corpus`, and lists `embeddings`: every text as it was
sent, its SHA-256, and its vector as base64 of little-endian float32, which keeps every value exactly as the
provider returned it at a third of the size of decimal text. `RecordedEmbeddingGateway` looks a text up by its
SHA-256 and fails on a miss, so a change to the chunk rendering needs one live pass to record the new texts.

Recordings are made by `LiveRetrievalRecordingIT`, tagged `live` like the authoring run:

```
OPENAI_API_KEY=... ./mvnw verify -Dtest=none -Dit.test=LiveRetrievalRecordingIT -Dlive.tag= \
    -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -Djacoco.skip=true
```

It publishes each of the 13 policies the questions run on, with its expected rule set, through the real pipeline,
asks every question through the real retrieval, and writes `docs/eval/retrieval-first-pass.md`: recall at 8 per
question against `expectedChunks`, the refusals and the Hebrew misses. The change requests' vectors, `changes.json`,
are made by `LiveChangeRecordingIT`, and those of a policy no question runs on by `LiveChangePassIT` (Changes, below).

## Answers

What the provider streamed for the three scripted questions of demo step 3 (Work Plan day 9), so the chat is
replayed offline with its tool calls.

```
recordings/<provider>/answer/<version>/<inputHash>.json
```

Besides the request, each file lists `steps`, the tool calls the model made in order with their arguments as it
wrote them, and `response`, the raw text before the marker resolver. `RecordedGateway` runs the steps through the
turn's real tools, then replays the text, so the resolver, the citations and the engine behind `simulate` run as
they do live. `RecordedAnswerIT` holds the replay to each question's `expectedMarkers`, `expectedTool` and
`answerContains`.

Recordings are made by `LiveAnswerRecordingIT`, tagged `live` like the others; it asks each question in a new
session of the seeded lending version, on the recorded question vectors:

```
OPENAI_API_KEY=... ./mvnw verify -Dtest=none -Dit.test=LiveAnswerRecordingIT -Dlive.tag= \
    -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -Djacoco.skip=true
```

The thirty questions of the evaluation set were answered the same way by `LiveAnswerPassIT` (Work Plan day 11),
each of its own policy, which is what `EvalRunnerIT` scores citation accuracy from.

## Changes

What the strong model proposed for the six labeled change requests of `fixtures/eval/changes.json` (Work Plan days
12 and 13), so change correctness is scored offline.

```
recordings/<provider>/change/<version>/<inputHash>.json
```

A request's first call and each of its repairs are recorded under the hash of the prompt they answered. A repair's
prompt carries the errors and the earlier answer instead of the request, so `RecordedGateway` replays the whole
chain as the live pass received it. The runner finds a request's first call by its text, reads the candidates off
its prompt and proposes again through the change use case; `EvalRunnerIT` holds those candidates to the ones
candidate selection gives on the recorded vectors.

`change/v1` is kept to compare with: the scripted request, CR-1, was recorded on day 12 by `LiveChangeRecordingIT`,
the other five by `LiveChangePassIT`. `change/v2`, the active version, has all six from `LiveChangePassIT`. The pass
publishes each request's policy with its labeled rule set, asks only a request whose first prompt has no recording,
and embeds only a policy whose vectors have none, writing them beside the others: `consumer-lending-en.json`, the
policy of CR-3, which no question runs on. `live.budget` caps what it may spend, and a call that could cross it is
not asked:

```
OPENAI_API_KEY=... ./mvnw verify -Dtest=none -Dit.test=LiveChangePassIT -Dlive.tag= -Dlive.budget=35000 \
    -Dsurefire.failIfNoSpecifiedTests=false -Dfailsafe.failIfNoSpecifiedTests=false -Djacoco.skip=true
```
