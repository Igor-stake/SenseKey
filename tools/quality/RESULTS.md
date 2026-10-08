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

## Bonsai 2 experiment, 8 October 2026

The production prompt/parser was unchanged. Both ternary packs from
`prism-ml/Ternary-Bonsai-2-27B-gguf` were SHA-256 verified at revision
`b072e1d3b35a0a630cece372c2127528e0994386`. The official Prism CPU runtime
`prism-b10754-2459f68` loaded both. No vision projector was used.

The smaller PTQ1_0 pack did not finish the first cold validation request within
the host's 180-second HTTP timeout. No continuation or quality score was obtained.
This result must not be reported as eight failed answers.

PQ2_0 completed the same eight fixed validation cases, with seed 42, temperature
0.3 and the real production structured output. Manual review found **six useful
continuations and two appropriate abstentions**. The refusal to buy the explicitly
large wardrobe suggested looking for a smaller option, without inventing a price.
No case copied the chat or emitted reasoning. This is a small previously used
validation set, not a blinded test of model selection or broad user acceptance.

With batch/ubatch 64, the first PQ request took **165.16 s**: 954 prompt tokens,
159.93 s prompt processing and 5.15 s generation. The other seven took
**8.13-15.11 s**, reusing 910 prompt tokens. These are CPU host measurements under
an 8 GiB memory limit, with substantial memory pressure and different runtimes
from the older Qwen evaluation. They do not predict Fold performance or establish
that Bonsai is faster than Qwen.

The phone launcher uses batch/ubatch 16, limited checkpoint/cache storage,
reasoning off and a one-time static-prompt warmup. A separate run of that actual
launcher prepared the prefix and reused 910 tokens in its first synthetic
keyboard request. The request took **28.62 s** and, without a fixed seed, returned
**“Хорошо, прочитал.”** for a request to read instructions. This falsely implies
the action was already done. It is a material failure outside the eight seeded
cases: the model is a candidate, not an established Gmail-quality predictor.

In the launcher fixture, closing a subsequent socket during a new synthetic
context released its server slot within **2.81 s**. Interrupting the launcher
returned exit 130 and shut down its child server. This confirms the host startup,
prefix reuse and cancellation path, not Android/Fold lifecycle behaviour.

The Android arm64 archive was downloaded and its official SHA-256 verified.
Required non-system libraries are bundled, the ELF load segments fit their files,
and all launch flags exist in the matching release. The archive has CPU kernels
and no Vulkan library. It has **not** been executed on the user's Fold.

Traceable inputs, manifests, outputs and the conservative review are committed as
`results/bonsai2-*`. Startup uses only the production instructions, invented
examples and an empty draft/context in `warmup.json`; no phone contents are read.
Warmup is an inference optimization, not training. It moves the initial common
prompt cost out of typing, and its cache is lost on process restart.
