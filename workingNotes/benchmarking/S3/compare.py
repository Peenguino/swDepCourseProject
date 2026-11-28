import json, sys
W = sys.argv[1]  # workingNotes/benchmarking
def load(s):
    out = {}
    for part in ("library", "backend"):
        for d in json.load(open(f"{W}/{s}/{part}.json")):
            name = d["benchmark"].rsplit(".", 2)
            key = (name[-2] + "." + name[-1], ",".join(f"{k}={v}" for k, v in d.get("params", {}).items()))
            out[key] = d
    return out
s1, s2, s3 = load("S1"), load("S2"), load("S3")
print("| Benchmark | Param | Unita' | S1 | S2 | S3 (AMBER) | S3 vs S2 | dentro errore S2? | iter. totali per fork | halt |")
print("|---|---|---|---|---|---|---|---|---|---|")
tot_iters = 0; forks = 0; halted_first = 0
for key in s2:
    a, b, c = s1[key]["primaryMetric"], s2[key]["primaryMetric"], s3[key]["primaryMetric"]
    delta = (c["score"] - b["score"]) / b["score"] * 100
    lo2, hi2 = b["score"] - b["scoreError"], b["score"] + b["scoreError"]
    lo3, hi3 = c["score"] - c["scoreError"], c["score"] + c["scoreError"]
    overlap = "si'" if lo3 <= hi2 and lo2 <= hi3 else "no"
    wi = s3[key].get("warmupIterations")
    per_fork = [w[0] + 100 for w in wi] if isinstance(wi, list) else []
    halts = ["halt" if n <= 500 else "no halt" for n in per_fork]
    tot_iters += sum(per_fork); forks += len(per_fork); halted_first += sum(1 for n in per_fork if n == 100)
    f = lambda m: f"{m['score']:.3f} ± {m['scoreError']:.3f}"
    print(f"| {key[0]} | {key[1]} | {c['scoreUnit']} | {f(a)} | {f(b)} | {f(c)} | {delta:+.1f}% | {overlap} | {'/'.join(map(str, per_fork))} | {', '.join(sorted(set(halts)))} |")
print(f"\nfork totali: {forks}; iterazioni totali: {tot_iters}; media per fork: {tot_iters / max(forks, 1):.1f}; halt alla 100a: {halted_first}")
