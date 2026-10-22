#!/usr/bin/env python3
"""Frozen prospective holdout audit. Python standard library only.

No threshold fitting, trial filtering, or case-level optimistic method selection.
Report all 210 matched cases and all 6,930 individual raw observations.
"""
import csv
import math
import pathlib
import statistics
import sys
from collections import defaultdict

SHAPES = ("random", "sorted", "reverse", "nearly", "few-counts", "equal", "runs",
          "zipf", "sparse", "unicode", "prefix", "staircase", "alternating", "clusters")
SIZES = (512, 2048, 8192)
SEEDS = (101, 4099, 65537, 9001, 20261)
METHODS = ("adaptive-v1", "hybrid", "merge", "freqbucket", "jdk-native",
           "rca", "pdq", "sparsebucket", "parallel", "radix", "wct")
FIXED = tuple(x for x in METHODS if x not in ("adaptive-v1", "hybrid"))
EXPECTED_CASES = {(shape, n, seed) for shape in SHAPES for n in SIZES for seed in SEEDS}

def geo(numbers):
    if not numbers or any(x <= 0 for x in numbers):
        raise ValueError("Ratios/times must be strictly positive")
    return math.exp(statistics.fmean(math.log(x) for x in numbers))

def percentile(values, p):
    if not values:
        raise ValueError("No samples")
    values = sorted(values)
    pos = p * (len(values)-1)
    lo = math.floor(pos)
    hi = math.ceil(pos)
    return values[lo] + (values[hi]-values[lo])*(pos-lo)

def evaluate(rows):
    """A parsed summary row corresponds to a *case*, not independent timing trials."""
    cases = defaultdict(dict)
    for row in rows:
        key = (row["dataset"], int(row["n"]), int(row["seed"]))
        name = row["method"]
        if key not in EXPECTED_CASES or name not in METHODS:
            raise ValueError("Unexpected holdout case/method: " + str((key, name)))
        if name in cases[key]:
            raise ValueError("Duplicate method in case: " + str((key, name)))
        if row["status"] != "PASS" or int(row["samples"]) != 3:
            raise ValueError("Missing PASS or three observations: " + str((key, name)))
        t = float(row["median_ns"])
        if t <= 0 or not math.isfinite(t) or not row["implementation"]:
            raise ValueError("Invalid timing or unattributed implementation")
        cases[key][name] = dict(time=t, path=row["implementation"],
                                input=row["input_sha256"], expected=row["oracle_sha256"])
    if set(cases) != EXPECTED_CASES:
        raise ValueError(f"Missing holdout cases: {len(EXPECTED_CASES-set(cases))}")
    for key, methods in cases.items():
        if set(methods) != set(METHODS):
            raise ValueError(f"Missing methods: {key}: {sorted(set(METHODS)-set(methods))}")
        if len({v["input"] for v in methods.values()}) != 1 or len({v["expected"] for v in methods.values()}) != 1:
            raise ValueError("Mismatched input/oracle fingerprint across methods: " + str(key))
    ordered = sorted(cases)
    fixed_geo = {name:geo([cases[key][name]["time"] for key in ordered]) for name in FIXED}
    best = min(FIXED, key=lambda name:fixed_geo[name])
    adaptive = "adaptive-v1"
    prior = "hybrid"
    adaptive_old_ratio = geo([cases[key][adaptive]["time"]/cases[key][prior]["time"] for key in ordered])
    wins_prior = sum(cases[key][adaptive]["time"] < cases[key][prior]["time"] for key in ordered)
    best_ratio = geo([cases[key][adaptive]["time"]/cases[key][best]["time"] for key in ordered])
    hindsight_regret = [cases[key][adaptive]["time"]/min(cases[key][name]["time"] for name in FIXED) for key in ordered]
    p90 = percentile(hindsight_regret, .9)
    fallback = sum("merge(sparse-frequency-range)" in cases[key]["freqbucket"]["path"] for key in ordered)
    adaptive_routes = defaultdict(int)
    for key in ordered:
        adaptive_routes[cases[key][adaptive]["path"]] += 1
    scores = {
        "case_count": len(ordered), "best_fixed": best, "fixed_geomeans_ns": fixed_geo,
        "geomean_adaptive_over_hybrid": adaptive_old_ratio,
        "adaptive_wins_vs_hybrid": wins_prior,
        "win_share": wins_prior/len(ordered),
        "geomean_adaptive_over_best_fixed": best_ratio,
        "p90_regret_vs_case_oracle": p90,
        "histogram_fallback_cases": fallback, "adaptive_routes": dict(adaptive_routes),
        "P0": True,
        "P1": adaptive_old_ratio < .95 and wins_prior/len(ordered) >= .60,
        "P2": best_ratio < .95,
        "P3": p90 <= 1.50
    }
    scores["pass"] = all(scores[x] for x in ("P0","P1","P2","P3"))
    return cases, ordered, scores

def check_samples(path, cases):
    count = 0
    seen = defaultdict(set)
    with path.open(newline="", encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            key = (row["dataset"], int(row["n"]), int(row["seed"]))
            method = row["method"]
            if key not in cases or method not in cases[key]:
                raise ValueError("Unexpected raw observation")
            index = int(row["iteration"])
            if index not in (0,1,2) or index in seen[key,method]:
                raise ValueError("Duplicate/invalid iteration: " + str((key,method,index)))
            if row["input_sha256"] != cases[key][method]["input"] or row["oracle_sha256"] != cases[key][method]["expected"]:
                raise ValueError("Raw fingerprint mismatch")
            if row["implementation"] != cases[key][method]["path"] or int(row["nanoseconds"]) <= 0:
                raise ValueError("Unattributed/invalid raw observation")
            seen[key,method].add(index)
            count += 1
    for key in cases:
        for method in METHODS:
            if seen[key,method] != {0,1,2}:
                raise ValueError("Incomplete observations: " + str((key,method)))
    if count != 6930:
        raise ValueError(f"Unexpected raw observation count: {count}")
    return count

def write_report(directory, cases, ordered, scores, count):
    with (directory/"cases.csv").open("w",newline="",encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(("shape","n","seed","adaptive_ns","hybrid_ns","best_fixed_ns","best_fixed_method",
                    "ratio_vs_hybrid","regret_vs_case_oracle","adaptive_route","freqbucket_route"))
        for key in ordered:
            m = cases[key]
            single = min(FIXED,key=lambda name:m[name]["time"])
            w.writerow((*key, m["adaptive-v1"]["time"],m["hybrid"]["time"],m[scores["best_fixed"]]["time"],
                        scores["best_fixed"],m["adaptive-v1"]["time"]/m["hybrid"]["time"],
                        m["adaptive-v1"]["time"]/m[single]["time"],m["adaptive-v1"]["path"],m["freqbucket"]["path"]))
    with (directory/"by_shape.csv").open("w",newline="",encoding="utf-8") as fh:
        w=csv.writer(fh)
        w.writerow(("shape","cases","geomean_adaptive_over_hybrid","adaptive_wins_vs_hybrid",
                    "geomean_adaptive_over_best_fixed"))
        for shape in SHAPES:
            keys=[key for key in ordered if key[0]==shape]
            w.writerow((shape,len(keys),
                geo([cases[k]["adaptive-v1"]["time"]/cases[k]["hybrid"]["time"] for k in keys]),
                sum(cases[k]["adaptive-v1"]["time"]<cases[k]["hybrid"]["time"] for k in keys),
                geo([cases[k]["adaptive-v1"]["time"]/cases[k][scores["best_fixed"]]["time"] for k in keys])))
    text = [
        "ADAPTIVE V1 PROSPECTIVE HOLDOUT (frozen criteria; shared-CI screening, not a general theorem)",
        f"cases={scores['case_count']} raw_observations={count} exact_oracle_and_fingerprint_gate=PASS",
        f"best_fixed_method={scores['best_fixed']}",
        "fixed_geomean_times_ns="+",".join(f"{k}:{v:.3f}" for k,v in scores["fixed_geomeans_ns"].items()),
        f"P1: adaptive/hybrid geomean={scores['geomean_adaptive_over_hybrid']:.6f} (must <0.95); "
        f"wins={scores['adaptive_wins_vs_hybrid']}/210 share={scores['win_share']:.6f} (must >=0.60); "
        f"PASS={scores['P1']}",
        f"P2: adaptive/best_fixed geomean={scores['geomean_adaptive_over_best_fixed']:.6f} "
        f"(must <0.95); PASS={scores['P2']}",
        f"P3: 90th-percentile regret to hindsight per-case fixed-method minimum="
        f"{scores['p90_regret_vs_case_oracle']:.6f} (must <=1.50); PASS={scores['P3']}",
        f"freqbucket_sparse_merge_fallback_cases={scores['histogram_fallback_cases']}",
        "adaptive_dispatches="+",".join(f"{k}:{v}" for k,v in sorted(scores["adaptive_routes"].items())),
        "OVERALL="+("PASS" if scores["pass"] else "FAIL"),
        "Caveat: Three iterations/case, shared runner, no independent host; metric-unit is case not iteration.",
        "No threshold retuning on this holdout. See by_shape.csv and cases.csv, including negative results."
    ]
    (directory/"verdict.txt").write_text("\n".join(text)+"\n",encoding="utf-8")

def main(argv):
    if len(argv)!=2:
        raise SystemExit("usage: python3 holdout_report.py PATH_TO_HOLDOUT_DIRECTORY")
    directory=pathlib.Path(argv[1])
    with (directory/"summary.csv").open(newline="",encoding="utf-8") as fh:
        cases,ordered,scores=evaluate(list(csv.DictReader(fh)))
    count=check_samples(directory/"samples.csv",cases)
    write_report(directory,cases,ordered,scores,count)
    print((directory/"verdict.txt").read_text(),end="")
    return 0 if scores["P0"] else 1  # Scientific FAIL must not hide evidence by deleting artifacts.

if __name__=="__main__":
    try: sys.exit(main(sys.argv))
    except (ValueError,OSError,KeyError,OverflowError) as e:
        print("INVALID HOLDOUT DATA: "+str(e),file=sys.stderr)
        sys.exit(2)
