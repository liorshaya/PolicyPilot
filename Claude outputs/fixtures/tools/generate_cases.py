"""Generates the 200 fixture cases of the consumer-lending demo (Document 6, "The 200-case generator").

Seeded and deterministic. Applicants are drawn from labeled strata (young, over-age, retirees near the
term limit, seniority missing, income near the minimum, ratio in the referral band, one and two credit
events, guarantor present, and the plain approvals and rejections), so that

  1. every rule of ruleset.v1.json fires at least three times across the 200 cases, and
  2. the scripted change request (change-request-1.json: minimum income to 9,000) flips exactly 12 decisions.

Both properties are asserted here by running the reference implementation, and CI re-runs this script and
diffs the committed files. Case 17 is the demo's case (refer by R-330), which the Brief opens in step 2.

Run: python3 fixtures/tools/generate_cases.py [fixtures-root]
Writes: policies/consumer-lending/cases-200.json and cases-expected.json
"""
import json, random, sys
from pathlib import Path

ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "reference"))
import reference_check as ref   # noqa: E402  (resolves the same fixtures root)

SEED = 20260922
POLICY_DIR = ROOT / "policies" / "consumer-lending"
RULESET = json.load(open(POLICY_DIR / "ruleset.v1.json", encoding="utf-8"))
CHANGE = json.load(open(POLICY_DIR / "change-request-1.json", encoding="utf-8"))
PARAS = [p.strip() for p in open(POLICY_DIR / "policy.he.md", encoding="utf-8").read().split("\n\n") if p.strip()]
CASE17 = {"age": 34, "employment_type": "salaried", "employment_months": 30, "monthly_income": 9500,
          "existing_monthly_debt": 1200, "requested_amount": 60000, "term_months": 48, "credit_events_24m": 1}
rng = random.Random(SEED)

def installment(amount, term):
    return round(amount * 0.0075 / (1 - 1.0075 ** (-term)), 2)

def plain(income_lo=9000, income_hi=30000, dti_hi=0.30, events=0, guarantor=None, age=(25, 65), employment=None):
    """A case that passes every gate with a ratio under dti_hi; the caller then perturbs one thing."""
    emp = employment or rng.choice(["salaried", "salaried", "self_employed", "retired"])
    c = {"age": rng.randint(*age), "employment_type": emp, "monthly_income": rng.randint(income_lo, income_hi),
         "requested_amount": rng.randint(10, 150) * 1000, "term_months": rng.choice([12, 24, 36, 48, 60, 72, 84]),
         "credit_events_24m": events}
    if emp == "salaried": c["employment_months"] = rng.randint(6, 240)
    if emp == "self_employed": c["employment_months"] = rng.randint(24, 240)
    if emp == "retired": c["age"] = rng.randint(66, 72)
    inst = installment(c["requested_amount"], c["term_months"])
    room = dti_hi * c["monthly_income"] - inst
    c["existing_monthly_debt"] = rng.randint(0, int(room)) if room > 0 else 0
    if room <= 0:   # the loan alone is too large for this income: shrink the request
        c["requested_amount"] = 10000; inst = installment(10000, c["term_months"]); c["existing_monthly_debt"] = max(0, int(dti_hi * c["monthly_income"] - inst) // 2)
    if guarantor is not None: c["has_guarantor"] = guarantor
    return c

def with_ratio(c, lo, hi):
    """Set existing debt so the ratio lands in [lo, hi]."""
    inst = installment(c["requested_amount"], c["term_months"])
    target = rng.uniform(lo, hi)
    c["existing_monthly_debt"] = max(0, round(target * c["monthly_income"] - inst))
    return c

STRATA = [
    # (name, count, builder, deciding rule expected)
    ("young", 5, lambda: dict(plain(), age=rng.randint(18, 20)), "R-100"),
    ("over_age", 4, lambda: dict(plain(employment=rng.choice(["salaried", "self_employed"])), age=rng.randint(71, 80)), "R-110"),
    ("retired_over_75", 4, lambda: dict(plain(employment="retired"), age=rng.randint(76, 85)), "R-115"),
    ("retiree_term_limit", 5, lambda: dict(plain(employment="retired"), age=rng.randint(73, 75), term_months=rng.choice([60, 72, 84])), "R-116"),
    ("amount_out_of_range", 4, lambda: dict(plain(), requested_amount=rng.choice([5000, 8000, 9500, 160000, 200000])), "R-120"),
    ("term_out_of_range", 4, lambda: dict(plain(employment="salaried"), term_months=rng.choice([6, 9, 11, 90, 96])), "R-130"),
    ("unemployed", 4, lambda: dict(plain(), employment_type="unemployed", employment_months=None), "R-140"),
    ("salaried_low_seniority", 4, lambda: dict(plain(employment="salaried"), employment_months=rng.randint(0, 5)), "R-150"),
    ("self_employed_low_seniority", 4, lambda: dict(plain(employment="self_employed"), employment_months=rng.randint(0, 23)), "R-160"),
    ("income_below_minimum", 6, lambda: plain(income_lo=2000, income_hi=7999), "R-170"),
    ("ratio_too_high", 8, lambda: with_ratio(plain(), 0.42, 0.70), "R-200"),
    ("two_credit_events", 8, lambda: plain(events=rng.randint(2, 4)), "R-220"),
    ("seniority_missing", 5, lambda: dict(plain(employment=rng.choice(["salaried", "self_employed"])), employment_months=None), "R-310"),
    ("ratio_in_referral_band", 8, lambda: with_ratio(plain(), 0.355, 0.395), "R-320"),
    ("one_credit_event_no_guarantor", 8, lambda: plain(events=1, guarantor=rng.choice([False, None])), "R-330"),
    ("guarantor_present", 5, lambda: plain(events=1, guarantor=True), "R-900"),
    ("income_near_minimum_approve", 6, lambda: plain(income_lo=8000, income_hi=8999), "R-900"),
    ("income_near_minimum_referral_band", 3, lambda: with_ratio(plain(income_lo=8000, income_hi=8999), 0.355, 0.395), "R-320"),
    ("income_near_minimum_one_event", 3, lambda: plain(income_lo=8000, income_hi=8999, events=1, guarantor=False), "R-330"),
    ("approve", 102, lambda: plain(), "R-900"),
]

def draw(builder, rule_id):
    for _ in range(200):
        c = {k: v for k, v in builder().items() if v is not None}
        try:
            d = ref.evaluate(RULESET, c)
        except ref.CaseInvalid:
            continue
        if d["status"] == "OK" and d["decidingRuleId"] == rule_id:
            return c
    raise RuntimeError(f"could not draw a case decided by {rule_id}")

def apply_patches(rs, patches):
    out = json.loads(json.dumps(rs))
    by_id = {r["id"]: r for r in out["rules"]}
    for p in patches:
        assert p["op"] == "replace" and p["ruleId"] in by_id, p
        by_id[p["ruleId"]].clear(); by_id[p["ruleId"]].update(p["rule"])
    return out

def main():
    cases = []
    for name, count, builder, rule_id in STRATA:
        for _ in range(count):
            cases.append({"stratum": name, "input": draw(builder, rule_id)})
    assert len(cases) == 200, len(cases)
    order = list(range(200)); rng.shuffle(order)
    cases = [cases[i] for i in order]
    # case 17 is the demo's case: refer by R-330, opened in demo step 2 and asked about in step 3
    slot = next(i for i, c in enumerate(cases) if c["stratum"] == "one_credit_event_no_guarantor")
    cases[16], cases[slot] = {"stratum": "one_credit_event_no_guarantor", "input": dict(CASE17)}, cases[16]
    for i, c in enumerate(cases): c["id"] = i + 1
    cases = [{"id": c["id"], "stratum": c["stratum"], "input": c["input"]} for c in cases]

    # property 1: every rule fires at least three times; golden expectations per case
    fired = {r["id"]: 0 for r in RULESET["rules"]}
    expected, counts = [], {"approve": 0, "reject": 0, "refer": 0}
    deciding = {}
    for c in cases:
        d = ref.evaluate(RULESET, c["input"])
        assert d["status"] == "OK", (c, d.get("errorCode"))
        for s in d["trace"]:
            if s["status"] == "fired": fired[s["ruleId"]] += 1
        counts[d["outcome"]] += 1
        deciding[d["decidingRuleId"]] = deciding.get(d["decidingRuleId"], 0) + 1
        expected.append({"id": c["id"], "outcome": d["outcome"], "decidingRuleId": d["decidingRuleId"], "derived": d["derived"],
                         "flags": [f["code"] for f in d["flags"]]})
    weak = {k: v for k, v in fired.items() if v < 3}
    assert not weak, f"rules firing fewer than three times: {weak}"
    d17 = ref.evaluate(RULESET, cases[16]["input"])
    assert cases[16]["id"] == 17 and d17["outcome"] == "refer" and d17["decidingRuleId"] == "R-330"

    # property 2: the scripted change flips exactly 12 decisions
    patches = CHANGE["expected"]["patches"]
    v2 = apply_patches(RULESET, patches)
    model_ids = {p["ruleId"] for p in patches}
    findings = ref.validate(v2, PARAS, "CHANGE_PROPOSAL", model_ids)
    assert not [f for f in findings if f["severity"] == "error"], findings
    flips = []
    for c, e in zip(cases, expected):
        d2 = ref.evaluate(v2, c["input"])
        assert d2["status"] == "OK"
        if d2["outcome"] != e["outcome"]:
            flips.append({"id": c["id"], "from": e["outcome"], "to": d2["outcome"], "decidingRuleId": d2["decidingRuleId"]})
    assert len(flips) == CHANGE["expected"]["regression"]["flips"] == 12, (len(flips), [f["id"] for f in flips])

    with open(POLICY_DIR / "cases-200.json", "w", encoding="utf-8") as f:
        json.dump({"fixtureSet": "cases-200", "rulesetId": RULESET["id"], "rulesetVersion": 1, "seed": SEED,
                   "generator": "fixtures/tools/generate_cases.py", "count": len(cases),
                   "strata": {name: count for name, count, _, _ in STRATA}, "cases": cases}, f, ensure_ascii=False, indent=2)
        f.write("\n")
    top = sorted(deciding.items(), key=lambda kv: (-kv[1], kv[0]))
    with open(POLICY_DIR / "cases-expected.json", "w", encoding="utf-8") as f:
        json.dump({"fixtureSet": "cases-200", "rulesetId": RULESET["id"], "rulesetVersion": 1,
                   "summary": {"outcomes": counts, "topDecidingRules": [{"ruleId": k, "count": v} for k, v in top[:5]]},
                   "regression": {"changeRequestId": CHANGE["id"], "flips": flips},
                   "cases": expected}, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"wrote {len(cases)} cases: {counts}; top deciding rules {top[:5]}")
    print(f"every rule fires at least three times (minimum {min(fired.values())} for {min(fired, key=fired.get)}); "
          f"the scripted change flips exactly {len(flips)} decisions: {[f['id'] for f in flips]}")

if __name__ == "__main__":
    main()
