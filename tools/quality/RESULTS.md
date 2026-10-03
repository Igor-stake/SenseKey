# Quality check, 3 October 2026

The task was phrase prediction, not general chatbot quality. All examples were invented. A seed-42 run is a small engineering check, not an accuracy guarantee or an investor readiness claim.

| Comparison | Review |
|---|---|
| Build 23 + Qwen3-0.6B Q8 | 4 useful/acceptable continuations, 5 weak, 16 bad, 2 misses; one empty-context request skipped |
| Final prompt + Qwen3.5-9B Q4_K_M | 17 useful/acceptable continuations, 4 expected abstentions, 2 weak, 4 misses; one empty-context request skipped |
| Eight additional examples after freezing the prompt | Five useful replies, two expected abstentions, one ungrounded reply |

Manual grades are deliberately conservative and published per case in `results/manual-review.json`. An abstention is counted as correct only when the case requires an unknown private fact. The 28 development examples, including the initially held-out subset, were inspected during prompt development. The final eight validation examples were not used to change the prompt or filters.

Examples of improved behavior:

- “Лучше не идти, ” after a choice between delivery and shopping: old model reversed the negation; the new model suggested “закажем доставку.”
- “Давай ” with known availability after 18: old model generated “для удобства.”; the new model suggested “созвонимся после 18.”
- Thanks in a history fragment containing an instruction to change the model's rules: old model followed that instruction; the new model answered the thanks.

Remaining failures are material. The new model still misses some acknowledgements and can invent a factual reason: in validation it completed “Не стоит, ” with “он слишком дорогой.” without a price in the context. Numeric/calendar checks do not catch that. One work suggestion presupposed a future meeting that was not established. Some history authors and quotes remain ambiguous. Neither the biography field nor a larger model alone solves these problems.

Raw replies, actual production requests, source/model checksums and the review rubric are in `results/`. The exploratory 4B run used an earlier prompt and is labeled accordingly; it is not a controlled claim that 9B is universally better. Tests of phone memory use and latency remain necessary. Host CPU elapsed times are recorded for traceability and must not be presented as Galaxy Z Fold latency.
