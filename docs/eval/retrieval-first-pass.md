# Retrieval, first live pass

Work Plan day 8: one live embedding pass over the 30 questions of `fixtures/eval/questions.json`, each
asked of its own policy, published with its expected rule set and embedded by `text-embedding-3-small`.
Written by `LiveRetrievalRecordingIT`; the vectors are in `fixtures/eval/recordings/openai/embedding/`.
Recall at 8 counts the question's `expectedChunks` among the chunks kept; a refusal question is right
to be stopped by the Threshold, but day 9's answer prompt may also refuse it.

| Question | Language | Refusal | Stopped | Best cosine | Recall at 8 | Missed | Kept |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Q-01 | he | no | no | 0.382 | 0/2 | p:7, r:R-330 | p:9, r:R-320, p:6, p:1, r:R-900, r:R-100, r:R-110, p:8 |
| Q-02 | he | no | no | 0.418 | 2/2 |  | p:9, p:7, r:R-330, r:R-320, p:6, r:R-900, p:1, r:R-100 |
| Q-03 | he | no | no | 0.486 | 2/2 |  | p:2, r:R-116, p:3, p:8, p:6, r:R-010, p:5, r:R-130 |
| Q-04 | he | yes | yes | 0.302 | n/a |  |  |
| Q-05 | en | no | no | 0.366 | 1/2 | p:7 | r:R-220, r:R-330, r:R-200, r:R-020, r:R-320, r:R-160, r:R-110, r:R-150 |
| Q-06 | he | no | no | 0.458 | 3/3 |  | p:6, r:R-200, r:R-320, r:R-020, p:4, r:R-170, p:7, p:3 |
| Q-07 | he | no | no | 0.446 | 1/2 | p:6 | r:R-320, r:R-200, r:R-130, r:R-310, r:R-160, r:R-120, r:R-140, r:R-330 |
| Q-08 | he | no | no | 0.375 | n/a |  | r:R-120, p:3, r:R-100, r:R-200, p:1, p:7, r:R-220, p:8 |
| Q-09 | en | no | no | 0.364 | 1/2 | p:8 | r:R-130, r:R-116, r:R-010, r:R-150, r:R-160, r:R-115, r:R-110, r:R-020 |
| Q-10 | he | yes | no | 0.485 | n/a |  | r:R-120, p:2, p:8, p:6, r:R-010, p:5, r:R-020, r:R-115 |
| Q-11 | he | no | no | 0.537 | 2/2 |  | r:R-170, p:4, p:6, r:R-410, r:R-020, r:R-420, p:7, p:3 |
| Q-12 | he | no | no | 0.572 | 1/2 | p:4 | r:R-420, r:R-410, r:R-020, r:R-170, p:6, r:R-010, r:R-320, r:R-200 |
| Q-13 | en | no | no | 0.294 | 1/2 | p:1 | r:R-110, r:R-100, r:R-115, r:R-410, r:R-116, r:R-020, r:R-200, r:R-010 |
| Q-14 | en | no | yes | 0.294 | 0/3 | r:R-310, r:R-320, r:R-330 |  |
| Q-15 | en | yes | yes | 0.210 | n/a |  |  |
| Q-16 | he | no | no | 0.582 | 2/2 |  | p:3, p:9, p:1, r:R-150, r:R-140, r:R-900, p:2, p:4 |
| Q-17 | he | no | no | 0.600 | 2/2 |  | p:8, r:R-330, r:R-150, p:3, p:6, r:R-130, p:2, r:R-160 |
| Q-18 | he | no | no | 0.446 | 2/2 |  | r:R-220, p:8, p:5, r:R-180, r:R-170, p:4, p:1, p:3 |
| Q-19 | he | no | no | 0.448 | 2/2 |  | r:R-010, r:R-120, p:2, r:R-110, p:3, p:7, r:R-410, r:R-900 |
| Q-20 | he | no | no | 0.525 | 2/2 |  | r:R-310, p:7, p:5, r:R-110, r:R-900, p:1, p:2, r:R-410 |
| Q-21 | he | no | no | 0.561 | 2/2 |  | r:R-020, p:4, r:R-120, p:2, p:7, p:1, p:3, p:8 |
| Q-22 | he | yes | yes | 0.303 | n/a |  |  |
| Q-23 | he | no | no | 0.615 | 2/2 |  | p:2, r:R-030, p:1, p:6, r:R-310, p:5, r:R-010, r:R-100 |
| Q-24 | he | no | no | 0.704 | 2/2 |  | p:5, r:R-010, r:R-300, r:R-020, r:R-130, r:R-120, p:1, r:R-900 |
| Q-25 | he | no | no | 0.698 | 2/2 |  | p:3, r:R-040, r:R-020, r:R-110, p:7, p:4, r:R-120, r:R-030 |
| Q-26 | he | yes | yes | 0.327 | n/a |  |  |
| Q-27 | en | no | no | 0.821 | 2/2 |  | p:2, r:R-020, r:R-030, r:R-010, p:7, p:1, r:R-410, p:4 |
| Q-28 | en | no | no | 0.633 | 2/2 |  | p:6, p:1, r:R-100, r:R-020, r:R-300, p:7, r:R-010, p:5 |
| Q-29 | en | no | no | 0.464 | 2/2 |  | p:7, p:1, p:2, p:3, r:R-100, r:R-110, p:4, r:R-120 |
| Q-30 | en | yes | no | 0.392 | n/a |  | p:1, p:3, p:7, r:R-100, r:R-900, p:5, r:R-110, p:2 |

Overall recall at 8: 38 of 48 expected chunks.

## Reading (by hand, 2026-09-22)

- **The day's gate holds.** Q-03, the demo's term question, keeps both `p:2` and `r:R-130`. Every question on the
  eleven other policies keeps all of its expected chunks.
- **Hebrew misses.** Q-07 keeps `r:R-320`, the rule it names, but not `p:6`, the paragraph that rule quotes. Q-12
  keeps `r:R-420` but not `p:4`. In both, the question names a rule or a flag, and the paragraph is reached through
  the rule's quote rather than through the question's words. Q-01 misses both of its chunks: it asks why decision 17
  was referred, which the `getDecision` tool of day 9 answers, not the corpus.
- **Cross-language questions score lower.** The English questions on the Hebrew lending policy reach 0.29 to 0.37.
  Q-14 (0.294) is stopped by the Threshold although the corpus answers it. No single threshold separates it from
  the true refusals Q-04 (0.302) and Q-22 (0.303), so lowering 0.35 would not fix it. The demo asks in Hebrew. The
  threshold stays as Document 4 sets it; the evaluation of day 11, cut in the two-week version, is where it would
  be revisited.
- **Refusals.** Four of six are stopped without a model call (Q-04, Q-15, Q-22, Q-26). Q-10 (0.485) and Q-30 (0.392)
  pass the Threshold, so the answer prompt of day 9 must refuse them with the fixed sentence.
