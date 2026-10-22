# Experimental sorting competition (2026)

This is a **falsifiable algorithm experiment**, not an assertion that WCT physics, RCA theory, Prime Engine, or WaveLock have already discovered a novel or faster sorting algorithm. The 2017 coursework and the validated modern application remain intact. Experimental implementations live under `modern/src/main/java/org/wordcountlab/experimental/` and require Java 17.

## Run

```sh
mvn -B -f modern/pom.xml verify
java -cp modern/target/wordcount-lab-1.0.0.jar org.wordcountlab.experimental.ExperimentLab --dataset random --size 2048 --seed 2017 --runs 7 --warmup 2
java -cp modern/target/wordcount-lab-1.0.0.jar org.wordcountlab.experimental.ExperimentLab --dataset sorted --size 2048 --runs 7 --warmup 2
java -cp modern/target/wordcount-lab-1.0.0.jar org.wordcountlab.experimental.ExperimentLab --dataset reverse --size 2048 --runs 7 --warmup 2
java -cp modern/target/wordcount-lab-1.0.0.jar org.wordcountlab.experimental.ExperimentLab --input sample_text/hamlet.txt --runs 7 --warmup 2
java -cp modern/target/wordcount-lab-1.0.0.jar org.wordcountlab.experimental.ExperimentLab --dataset runs --size 1024 --methods rca,wct,prime,hybrid --runs 5
```

Datasets: `random`, `sorted`, `reverse`, `nearly`, `few-counts` (1–4 counts), `equal` (all counts equal; words break ties), `runs` (local sorted runs of up to 64). Generator seed is recorded. For file input, the app counts with the existing modern UTF-8 tokenizer, then shuffles distinct frequencies with the recorded seed so accidental Map iteration order does not favor a candidate.

- All algorithms use **frequency descending, word ascending**.
- The independent JDK comparator sort is the exact reference oracle.
- Every result must match that full output array, which also establishes multiset preservation under this total order. Any disagreement aborts the report.
- Sorting/copying and, for crypto mode, hashing are timed. Dataset generation, file decoding, warmups, verification and printing are excluded. All methods have comparison counters; JDK internal writes are not instrumented (N/A).
- Fixed seed, common inputs, warmups, repeated runs, shuffled run ordering, medians and relative times against JDK are reported. These timings are *exploratory*, not statistically rigorous or JMH-grade.
- Quadratic insertion control is visibly skipped for more than 4096 items. Max distinct items 100,000; maximum measured runs 30. No silent fallback is allowed.
- Real word-count data comprises **unique word-frequency records**. Reported N is the distinct record count, not total raw tokens.

## Candidate definitions, controls, limitations

| Method | Concrete operation | Provenance / classification |
| --- | --- | --- |
| `jdk` | JDK comparator `Arrays.sort` on objects | Established library baseline |
| `merge` | Handwritten stable top-down merge | Established algorithm baseline |
| `insertion` | Handwritten adjacent shifts | Established quadratic algorithm baseline |
| `wct` | Compute discrete adjacent order-gradient signs and changes (a curvature proxy); split into bounded blocks at defect thresholds, sort each block, k-way heap merge the blocks | **WCT-inspired heuristic**; *not* WCT PDE evolution, confinement theorem, or a novel proven sorting complexity |
| `rca` | Detect ordered/reverse-ordered natural runs, reverse strict descending runs, and pairwise merge neighboring runs until only one remains | **RCA-inspired monotone run-count feedback**; is a natural mergesort, not a newly proven RCA sorting theorem |
| `prime` | Pick a median-of-three pivot at prime-stride sample positions coprime to partition length, do 3-way partitioning, insertion-sort small partitions, and heap-sort at introspective depth cap | **Prime Engine-inspired pivot schedule**; essentially an introsort variant, not a claim of surpassing standard comparison-sort bounds |
| `crypto` | Canonically length-prefix each word and count, domain-separate and SHA-256 hash input and output around handwritten merge | **Cryptographic audit wrapper**, not an independent sorting algorithm or a signature; full correctness still requires checking against the oracle |
| `hybrid` | Measure adjacent inversions and discrete sign changes; select the RCA natural-run sort only for a sufficiently ordered input, otherwise prime-introsort; report chosen branch | **Explicit dispatcher**, not a fifth independent algorithm |

The audit fingerprint is a deterministic digest and does **not** establish source authenticity, timestamp, secret possession, encryption, resistance to falsified inputs, or a cryptographic proof of correct sorting. No experimental WaveLock cryptographic primitive is relied on for security.

### Mathematical test obligations

Define total-order inversion count for an input permutation `a` as

`V(a) = #{(i,j): i < j and compare(a[i],a[j]) > 0}`.

The exact sorted result is characterized by `V(a)=0`. The implementations do **not** repeatedly compute this quadratic potential during timing. It serves as an independent correctness interpretation; the actual oracle test checks the complete sorted array. Natural-run merging decreases the number of runs in each round, yielding `O(n log r)` comparisons for `r` initial runs and worst-case `O(n log n)`. WCT block sorting followed by heap merging has a comparison bound of `O(n log n)`, but can have substantial heap/allocations overhead. Prime-introsort uses a heap fallback for guarded comparison complexity; its on-demand prime-stride selection adds additional computation not captured by comparison counts. None violates the comparison-model `Ω(n log n)` worst-case lower bound for sorting arbitrary distinct keys.

The first hosted screening, including negative results, is archived in [EXPERIMENTAL_RESULTS_2026-10-22.md](EXPERIMENTAL_RESULTS_2026-10-22.md). It is not a performance claim.

## Competition / falsification criteria

1. **Correctness gate:** exhaustive small permutations, generated adversarial input shapes, fixed-seed random tests, same JDK oracle and cryptographic transcript integrity checks.
2. **Baseline:** compare JDK, handwritten merge and (for small N) insertion before claiming any speed advantage.
3. **Competitive performance:** compare medians and ratios across several scales and input distributions; report unfavorable results and include prep, hash, allocations and traversal overhead in future JMH follow-up.
4. **Attribution:** distinguish *new algorithm*, *heuristic variation*, *dispatch strategy*, *audit property* and *established sorting baseline*. Improvement in a restricted dataset is not a new general complexity class.
5. **Negative result:** a consistently slower correct algorithm remains a useful falsification; do not change the comparator, exclude costly work selectively or suppress failures.

No benchmark results are frozen in this document. Hosted CI includes a small smoke matrix to verify execution, not to establish performance superiority.
