# Adaptive sorting v1: frozen prospective holdout protocol

Status: **FROZEN BEFORE HOLDOUT EXECUTION.** This is an experiment in workload-aware algorithm selection, not a new complexity theorem, WCT-PDE result, or a demonstrated general speedup. Do **not** change the selector thresholds after seeing holdout numbers; any revisions become v2 and need a *new* untouched holdout.

## Prior exploratory inputs (development, never reused as holdout)

Existing screening used seeds **2017, 42, 73** and 8 input shapes at sizes 128/2048/8192; previous short JMH smoke used random/8192/2017. These results motivate the model and are not independent evidence for adaptive-v1. The old `hybrid` method remains unchanged as a comparator.

## Fixed selector source and branches

`AdaptiveSelector.java` contains the frozen deterministic v1 rule. It sees only the record array, never the dataset label or seed:

1. Clone (performed by the common `ResearchSorts.sort` API). A single full-array pass collects integer min/max and uses the exact total comparator to determine whether input is already correctly ordered, or **strictly** reverse ordered. Inspection costs and comparison counts are included in timing.
2. Return the already-ordered clone unchanged when sorted. For **strict** reverse ordering, reverse the clone (strictness avoids reversing equal-key identities).
3. For remaining cases: if `n >= 512` and `16 <= count range <= min(1_000_000, 2*n)`, delegate to the existing fully attributed `freqbucket` algorithm; otherwise delegate to the existing handwritten `merge`.
4. Report the actual route (`adaptive-v1->already-ordered`, `adaptive-v1->strict-reverse`, `adaptive-v1->freqbucket->...`, or `adaptive-v1->merge`). The selector is not allowed to perform oracle lookups, cache sorted outputs, consult dataset names, tune after evaluation or silently switch implementations.

Feature inspection itself is O(n). General sorting fallback O(n log n); dense-bucket branch O(n+R+Σ n_c log n_c) with O(n+R) auxiliary space. These are conventional techniques combined into an **engineering hypothesis**, not proven new optimality.

## Unseen prospective test grid (single frozen pass)

- **Unseen seeds:** `101,4099,65537,9001,20261`, disjoint from the completed 2017/42/73 development screen.
- **Sizes:** `512,2048,8192`.
- **Shapes:** `random,sorted,reverse,nearly,few-counts,equal,runs,zipf,sparse,unicode,prefix,staircase,alternating,clusters`. The last three are new test families, fixed before evaluation: staircase repeats each increasing count eight times at spacing five; alternating switches between two widely separated counts; clusters combine many counts 1–96 with occasional million-scale outliers. Names and code cannot be accessed by the selector.
- **Compared public API methods:** `adaptive-v1,hybrid,merge,freqbucket,jdk-native,rca,pdq,sparsebucket,parallel,radix,wct` — eleven controls, all same exact total ordering. Every method receives identical immutable per-case input.
- **Primary dataset:** 14 shapes × 3 sizes × 5 seeds = **210 matched cases**; 11 methods × 3 measured iterations = **6,930 raw measurements**. Per-case 1 warmup; randomized method order per measured iteration. Exact oracle checking every result, input SHA-256 and implementation/fallback attribution archived. Timings include clone, adaptive inspection, allocations and counted operations, but exclude data generation, validation and file output. No within-holdout retuning.
- **Secondary JMH smoke:** random, 8192, seed 101, adaptive-v1 vs hybrid vs Merge vs Frequency Bucket vs native JDK; one fork/short measurements **only checks that JMH executes** and cannot replace multi-fork independent replication. Cross-host conclusions remain OPEN.

## Primary metrics and fixed decision gate (measured public API, not algorithm-only)

For each matched case take its three-iteration median. Calculate ratios using paired medians and use log ratios for geometric means:

- **P1 (adaptive beats earlier hybrid):** geometric mean `T_adaptive/T_hybrid < 0.95` AND adaptive faster in **at least 60%** of the 210 cases (ties not wins).
- **P2 (competitive beyond one weak comparator):** geometric mean `T_adaptive/T_best_fixed < 0.95` where `best_fixed` is the **single comparator (not adaptive, not hybrid)** having minimum geometric mean time over the entire holdout. Choosing this after observation is conservative for adaptive; report its identity, all fixed-method rankings, and every shape/size stratum.
- **P3 (avoid severe regressions):** 90th percentile of `T_adaptive / min(T_all_fixed_methods_same_case)` <= 1.50. The per-case retrospective minimum is an unreachable hindsight lower bound, not a deployable competitor; only use it to measure regret.
- **P0 (safety):** zero correctness failures or missing data, and all implementation/fallback strings present. Any failure means **NO PASS**. Report all three primary metrics, not just whichever favorable. P1–P3 jointly required to claim v1 passed this specific screening, which itself is not a proof of generalized performance.

Preserve failures and per-shape/size deltas. Do not present multiple correlated iterations as independent dataset samples; n=210 cases is the unit of paired analysis. Timing on shared GitHub Actions runner and heterogeneous instrumentation remains a caveat. Independent repeat / JMH extended are necessary for scientific claims.

## Run, raw evidence and reproducibility

The [prospective workflow](../.github/workflows/adaptive-holdout.yml) runs the frozen holdout once on its branch and after merging on master, archives `samples.csv`, `summary.csv`, selector/environment and `verdict.txt`; [holdout report generator](../modern/tools/holdout_report.py) is a pure-standard-library analyzer with test coverage. Treat any reruns on changed hardware as replication, not new untouched holdout. Do not modify the threshold after reading the first pass.
