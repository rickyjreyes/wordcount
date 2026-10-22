# WordCount Lab roadmap

## Shipped in the foundation PR
- Preserve original code and sample data without edits.
- Java 17 Maven-based companion CLI: count, compare, benchmark and analyze.
- Three corrected, hand-written counters: unbalanced BST, properly rebalanced AVL, custom resizing separate-chaining hash table.
- Three sorting implementations with an identical comparator.
- Unicode-aware UTF-8 tokenization with documented differences from legacy mode.
- HashMap reference validation, collision/duplicate tests, AVL height regression, randomized sorting tests, CLI error tests.
- CI builds original code and runs companion tests.
- CSV output and top-N frequency summary.

## Next iterations (not yet implemented)
1. Benchmark harness: JMH integration, GC/allocation metrics, input-order and generated-data matrices, machine-readable environment manifests.
2. Analytics: configurable tokenization/stop words, n-grams, per-document comparisons, Zipf plot and CSV/JSON metrics.
3. Visual learning tools: traceable BST/AVL rotation events, hash bucket occupancy and collision visualization.
4. Input handling: streaming tokenization for files too large to load in memory, stdin and directory modes.
5. Exports and presentation: JSON result schema, standalone HTML dashboard with explicit provenance.
6. Extend test generators with skew, collision-adversarial and non-Latin examples.

## Guardrails
- Do not overwrite the 2017 snapshot or rewrite its authorship.
- Do not print during timed phases or report unverified benchmark values.
- Keep regression fixtures deterministic and compare frequency maps to a reference.
- No new dependency solely for cosmetic UI.
