"""Reference implementation of the PolicyPilot Rules DSL 1.0 semantics (Document 3).

Not the product engine (that is Java); this script exists so that every number and every
claim in the specification is produced by code, and so the conformance cases can be
checked before the Java engine exists.

Run: python3 fixtures/reference/reference_check.py [fixtures-root]

Paths are resolved from the fixtures root (Document 6, "Fixtures layout"); the default root is the
parent of this file's directory, so the script runs from any working directory:
  <root>/schemas/ruleset-1.0.schema.json              the DSL 1.0 schema
  <root>/policies/consumer-lending/ruleset.v1.json    the published example rule set
  <root>/policies/consumer-lending/policy.he.md       the Hebrew policy text (paragraphs separated by blank lines)
  <root>/policies/consumer-lending/sample-decision.json   golden decision for case 17, written by the self-test
"""
import json, re, sys, unicodedata, copy
from pathlib import Path
from decimal import Decimal, ROUND_HALF_EVEN, getcontext
from jsonschema import Draft202012Validator

getcontext().prec = 40
D = Decimal
SCALE = D("0.000000000001")  # 12 places

# ----------------------------------------------------------------------------- paths
ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else Path(__file__).resolve().parent.parent
SCHEMA_PATH = ROOT / "schemas" / "ruleset-1.0.schema.json"
POLICY_DIR = ROOT / "policies" / "consumer-lending"
RULESET_PATH = POLICY_DIR / "ruleset.v1.json"
POLICY_TEXT_PATH = POLICY_DIR / "policy.he.md"
SAMPLE_DECISION_PATH = POLICY_DIR / "sample-decision.json"

# ----------------------------------------------------------------------------- schema
schema = json.load(open(SCHEMA_PATH, encoding="utf-8"))
Draft202012Validator.check_schema(schema)
V = Draft202012Validator(schema)

def schema_errors(doc):
    return sorted(V.iter_errors(doc), key=lambda e: list(e.path))

# ----------------------------------------------------------------------------- provenance / semantic / structural validation
def norm(s):
    s = unicodedata.normalize("NFKD", s)
    s = "".join(ch for ch in s if not unicodedata.category(ch).startswith("M"))
    s = s.lower()
    s = re.sub(r"[\"'“”‘’«»,.;:!?()\[\]{}\-–—/%]", "", s)
    return re.sub(r"\s+", " ", s).strip()

def refs_in_expr(e, acc):
    if isinstance(e, dict):
        if "field" in e and "fn" not in e: acc.add(e["field"])
        for a in e.get("args", []): refs_in_expr(a, acc)

def refs_in_cond(c, acc):
    if "field" in c and "op" in c:
        acc.add(c["field"]); refs_in_expr(c.get("value"), acc)
    for k in ("all", "any"):
        for x in c.get(k, []): refs_in_cond(x, acc)
    if "not" in c: refs_in_cond(c["not"], acc)

def cond_excludes_zero(c, field):
    """True when the condition (top level or inside a top-level all) requires field > 0."""
    leaves = [c] if ("field" in c and "op" in c) else c.get("all", [])
    for l in leaves:
        if l.get("field") == field and l.get("op") == "gt" and isinstance(l.get("value"), (int, float)) and l["value"] >= 0:
            return True
        if l.get("field") == field and l.get("op") == "gte" and isinstance(l.get("value"), (int, float)) and l["value"] > 0:
            return True
    return False

def divisors(e, acc):
    if isinstance(e, dict) and "fn" in e:
        if e["fn"] == "div":
            d = e["args"][1]
            if isinstance(d, dict) and "field" in d: acc.add(d["field"])
        for a in e["args"]: divisors(a, acc)

def validate(rs, paras, context="AUTHORING", model_rule_ids=None):
    """context: AUTHORING (model draft: every rule is the model's), CHANGE_PROPOSAL (only the patched
    rules, model_rule_ids, are the model's; untouched rules keep their provenance), PUBLISH, ANALYST_EDIT.
    Returns list of findings {code, severity, path, message}."""
    findings = []
    errs = schema_errors(rs)
    for e in errs:
        findings.append({"code": "DSL_SCHEMA", "severity": "error", "path": "/" + "/".join(str(p) for p in e.path), "message": e.message[:160]})
    if errs:
        return findings
    fields = {f["name"]: f for f in rs["fields"]}
    for i, r in enumerate(rs["rules"]):
        p = r["provenance"]
        path = f"/rules/{i}/provenance"
        if p["kind"] == "quoted":
            if not (1 <= p["paragraph"] <= len(paras)):
                findings.append({"code": "PROVENANCE_PARAGRAPH_MISSING", "severity": "error", "path": path, "message": r["id"]})
            elif norm(p["quote"]) not in norm(paras[p["paragraph"] - 1]):
                findings.append({"code": "PROVENANCE_QUOTE_MISMATCH", "severity": "error", "path": path, "message": r["id"]})
        elif p["kind"] == "analyst" and (context == "AUTHORING" or (context == "CHANGE_PROPOSAL" and r["id"] in (model_rule_ids or set()))):
            findings.append({"code": "PROVENANCE_ANALYST_FROM_MODEL", "severity": "error", "path": path, "message": r["id"]})
        elif p["kind"] == "pending" and context != "CHANGE_PROPOSAL":
            code = "PROVENANCE_PENDING_AT_PUBLISH" if context == "PUBLISH" else "PROVENANCE_PENDING_FROM_MODEL"
            findings.append({"code": code, "severity": "error", "path": path, "message": r["id"]})
    for f in rs["fields"]:
        s = f.get("source")
        if s and norm(s["quote"]) not in norm(paras[s["paragraph"] - 1]):
            findings.append({"code": "PROVENANCE_QUOTE_MISMATCH", "severity": "error", "path": "/fields", "message": f["name"]})
    rules = sorted([r for r in rs["rules"] if r.get("enabled", True)], key=lambda r: (r["priority"], r["id"]))
    setters = {}
    for r in rules:
        for a in r["actions"]:
            if a["type"] == "set":
                if not fields[a["field"]].get("derived"):
                    findings.append({"code": "DERIVED_WRITE_ONLY", "severity": "error", "path": "/rules", "message": r["id"]})
                setters.setdefault(a["field"], r["priority"])
    for r in rules:
        acc = set(); refs_in_cond(r["condition"], acc)
        for a in r["actions"]:
            if a["type"] == "set": refs_in_expr(a["value"], acc)
        for name in acc:
            if name not in fields:
                findings.append({"code": "FIELD_UNKNOWN", "severity": "error", "path": "/rules", "message": f"{r['id']} {name}"}); continue
            if fields[name].get("derived") and not any(a["type"] == "set" and a["field"] == name for a in r["actions"]):
                if setters.get(name, 10**9) >= r["priority"]:
                    findings.append({"code": "DERIVED_ORDER", "severity": "error", "path": "/rules", "message": f"{r['id']} reads {name}"})
        # DIVISION_BY_UNGUARDED_FIELD
        divs = set()
        for a in r["actions"]:
            if a["type"] == "set": divisors(a["value"], divs)
        for dname in divs:
            fd = fields.get(dname, {})
            safe = ("exclusiveMinimum" in fd and fd["exclusiveMinimum"] >= 0) or ("minimum" in fd and fd["minimum"] > 0)
            if not safe and not cond_excludes_zero(r["condition"], dname):
                findings.append({"code": "DIVISION_BY_UNGUARDED_FIELD", "severity": "warning", "path": "/rules",
                                 "message": f"{r['id']} divides by {dname}, which may be 0 and is not excluded by the rule's condition"})
    # REFER_PRECEDES_REJECT and CANDIDATE_NEVER_WINS
    def decides(r, outcome, terminal):
        return any(a["type"] == "decide" and a["outcome"] == outcome and a.get("terminal", True) == terminal for a in r["actions"])
    last_reject = max([r["priority"] for r in rules if decides(r, "reject", True)], default=None)
    for r in rules:
        if decides(r, "refer", True) and last_reject is not None and r["priority"] < last_reject and "precedence_intended" not in r.get("tags", []):
            findings.append({"code": "REFER_PRECEDES_REJECT", "severity": "warning", "path": "/rules",
                             "message": f"terminal refer {r['id']} at {r['priority']} precedes a terminal reject at {last_reject}"})
    always_terminal = [r["priority"] for r in rules if "always" in r["condition"] and any(a["type"] == "decide" and a.get("terminal", True) for a in r["actions"])]
    for r in rules:
        if any(a["type"] == "decide" and a.get("terminal", True) is False for a in r["actions"]) and any(p > r["priority"] for p in always_terminal):
            findings.append({"code": "CANDIDATE_NEVER_WINS", "severity": "warning", "path": "/rules", "message": r["id"]})
    return findings

# ----------------------------------------------------------------------------- engine
class CaseInvalid(Exception):
    def __init__(self, problems): super().__init__("CASE_INVALID"); self.problems = problems

class EvalError(Exception):
    def __init__(self, code, detail=""): super().__init__(code); self.code = code; self.detail = detail

def fmt(x):
    if isinstance(x, D): return float(x.quantize(SCALE, rounding=ROUND_HALF_EVEN).normalize())
    if isinstance(x, list): return [fmt(i) for i in x]
    return x

def validate_case(rs, raw):
    problems, case = [], {}
    for f in rs["fields"]:
        n, t = f["name"], f["type"]
        if f.get("derived"):
            if n in raw: problems.append({"code": "CASE_DERIVED_SUPPLIED", "field": n})
            continue
        if n not in raw:
            if f.get("required"): problems.append({"code": "CASE_REQUIRED_MISSING", "field": n})
            elif "default" in f: case[n] = f["default"]
            continue
        v = raw[n]
        ok = True
        if t in ("number", "integer"):
            ok = isinstance(v, (int, float)) and not isinstance(v, bool) and (t == "number" or float(v).is_integer())
            if ok:
                v = D(str(v))
                if "minimum" in f and v < D(str(f["minimum"])): problems.append({"code": "CASE_OUT_OF_RANGE", "field": n, "value": raw[n]}); continue
                if "maximum" in f and v > D(str(f["maximum"])): problems.append({"code": "CASE_OUT_OF_RANGE", "field": n, "value": raw[n]}); continue
                if "exclusiveMinimum" in f and v <= D(str(f["exclusiveMinimum"])): problems.append({"code": "CASE_OUT_OF_RANGE", "field": n, "value": raw[n]}); continue
                if "exclusiveMaximum" in f and v >= D(str(f["exclusiveMaximum"])): problems.append({"code": "CASE_OUT_OF_RANGE", "field": n, "value": raw[n]}); continue
        elif t == "boolean": ok = isinstance(v, bool)
        elif t == "enum": ok = isinstance(v, str) and v in f["values"]
        elif t == "string": ok = isinstance(v, str)
        elif t == "date": ok = isinstance(v, str) and re.fullmatch(r"\d{4}-\d{2}-\d{2}", v) is not None
        if not ok: problems.append({"code": "CASE_TYPE_MISMATCH", "field": n, "value": raw[n]}); continue
        case[n] = v
    if problems: raise CaseInvalid(problems)
    return case

def ev_expr(e, case, fields):
    if isinstance(e, bool): raise EvalError("EXPR_TYPE_MISMATCH")
    if isinstance(e, (int, float)): return D(str(e))
    if "field" in e and "fn" not in e:
        v = case.get(e["field"])
        if v is None:
            raise EvalError("EVAL_DERIVED_ABSENT" if fields[e["field"]].get("derived") else "EVAL_ABSENT", e["field"])
        return v
    fn, args = e["fn"], [ev_expr(a, case, fields) for a in e["args"]]
    if fn == "add": return sum(args, D(0))
    if fn == "sub": return args[0] - args[1]
    if fn == "mul":
        r = D(1)
        for a in args: r *= a
        return r
    if fn == "div":
        if args[1] == 0: raise EvalError("EVAL_DIV_ZERO")
        return (args[0] / args[1]).quantize(SCALE, rounding=ROUND_HALF_EVEN)
    if fn == "min": return min(args)
    if fn == "max": return max(args)
    if fn == "abs": return abs(args[0])
    if fn == "round": return args[0].quantize(D(1).scaleb(-int(args[1])), rounding=ROUND_HALF_EVEN)
    if fn == "pow":
        try: return args[0] ** args[1]
        except Exception: raise EvalError("EVAL_NON_FINITE")
    raise EvalError("EXPR_UNKNOWN_FN", fn)

def expr_text(e):
    if isinstance(e, (int, float)): return str(e)
    if "field" in e and "fn" not in e: return e["field"]
    sym = {"add": "+", "sub": "-", "mul": "*", "div": "/"}
    if e["fn"] in sym: return "(" + (" " + sym[e["fn"]] + " ").join(expr_text(a) for a in e["args"]) + ")"
    return e["fn"] + "(" + ", ".join(expr_text(a) for a in e["args"]) + ")"

def compare(c, case, fields, comps):
    f, op = c["field"], c["op"]
    actual = case.get(f)
    if actual is None and fields[f].get("derived") and op not in ("present", "absent"):
        raise EvalError("EVAL_DERIVED_ABSENT", f)
    v = c.get("value"); expected = None
    if op not in ("present", "absent"):
        if isinstance(v, dict): expected = ev_expr(v, case, fields)
        elif isinstance(v, list): expected = [D(str(x)) if isinstance(x, (int, float)) and not isinstance(x, bool) else x for x in v]
        elif isinstance(v, bool): expected = v
        elif isinstance(v, (int, float)): expected = D(str(v))
        else: expected = v
    if op == "present": res = actual is not None
    elif op == "absent": res = actual is None
    elif actual is None: res = False
    elif op == "eq": res = actual == expected
    elif op == "ne": res = actual != expected
    elif op == "lt": res = actual < expected
    elif op == "lte": res = actual <= expected
    elif op == "gt": res = actual > expected
    elif op == "gte": res = actual >= expected
    elif op == "between": res = expected[0] <= actual <= expected[1]
    elif op == "in": res = actual in expected
    elif op == "not_in": res = actual not in expected
    elif op == "matches": res = re.fullmatch(v, actual[:2000]) is not None
    entry = {"field": f, "op": op, "actual": fmt(actual), "result": res}
    if op not in ("present", "absent"):
        entry["expected"] = fmt(expected) if not isinstance(v, dict) else {"value": fmt(expected), "text": expr_text(v)}
    comps.append(entry)
    return res

def ev_cond(c, case, fields, comps):
    if "always" in c: return True
    if "all" in c: return all([ev_cond(x, case, fields, comps) for x in c["all"]])   # no short circuit
    if "any" in c: return any([ev_cond(x, case, fields, comps) for x in c["any"]])
    if "not" in c: return not ev_cond(c["not"], case, fields, comps)
    return compare(c, case, fields, comps)

def evaluate(rs, raw):
    fields = {f["name"]: f for f in rs["fields"]}
    case = validate_case(rs, raw)
    trace, outcome, deciding, reason, flags, cands = [], None, None, None, [], []
    stopped = False
    for r in sorted(rs["rules"], key=lambda r: (r["priority"], r["id"])):
        step = {"ruleId": r["id"], "label": r["label"], "priority": r["priority"]}
        if not r.get("enabled", True): step["status"] = "disabled"; trace.append(step); continue
        if stopped: step["status"] = "skipped"; trace.append(step); continue
        comps = []
        try:
            fired = ev_cond(r["condition"], case, fields, comps)
            step["comparisons"] = comps
            step["status"] = "fired" if fired else "not_fired"
            if fired:
                acts = []
                for a in r["actions"]:
                    if a["type"] == "set":
                        old = case.get(a["field"])
                        new = ev_expr(a["value"], case, fields) if isinstance(a["value"], dict) else a["value"]
                        case[a["field"]] = new
                        acts.append({"type": "set", "field": a["field"], "from": fmt(old), "to": fmt(new)})
                    elif a["type"] == "flag":
                        flags.append({"code": a["code"], "message": a["message"], "ruleId": r["id"]}); acts.append({"type": "flag", "code": a["code"]})
                    elif a["type"] == "decide":
                        acts.append({"type": "decide", "outcome": a["outcome"], "terminal": a.get("terminal", True)})
                        if a.get("terminal", True): outcome, deciding, reason, stopped = a["outcome"], r["id"], a["reason"], True
                        else: cands.append((a["outcome"], r["id"], a["reason"]))
                step["actions"] = acts
        except EvalError as e:
            step["status"] = "error"; step["comparisons"] = comps; step["error"] = {"code": e.code, "detail": e.detail}
            step["provenance"] = r["provenance"]; trace.append(step)
            return {"status": "ERROR", "errorCode": e.code, "errorRuleId": r["id"], "trace": trace,
                    "derived": {f["name"]: fmt(case.get(f["name"])) for f in rs["fields"] if f.get("derived")}}
        step["provenance"] = r["provenance"]
        trace.append(step)
    terminal = outcome is not None
    if outcome is None:
        sev = {"reject": 3, "refer": 2, "approve": 1}
        if cands: outcome, deciding, reason = max(cands, key=lambda c: sev[c[0]])
        else: outcome, reason = rs["defaults"]["outcome"], rs["defaults"]["reason"]
    derived = {f["name"]: fmt(case.get(f["name"])) for f in rs["fields"] if f.get("derived")}
    return {"status": "OK", "outcome": outcome, "reason": reason, "decidingRuleId": deciding, "terminal": terminal,
            "derived": derived, "flags": flags, "candidates": [{"outcome": c[0], "ruleId": c[1]} for c in cands], "trace": trace}

def simulate(rs, base_raw, overrides):
    """What-if: same version, base input plus overrides; never persisted."""
    d = evaluate(rs, dict(base_raw, **overrides))
    d["simulation"] = True; d["overrides"] = overrides
    return d

# ----------------------------------------------------------------------------- self-test
if __name__ == "__main__":
    rs = json.load(open(RULESET_PATH, encoding="utf-8"))
    paras = [p.strip() for p in open(POLICY_TEXT_PATH, encoding="utf-8").read().split("\n\n") if p.strip()]
    print("fixtures root:", ROOT)
    print("schema OK;", len(paras), "paragraphs;", len(rs["rules"]), "rules")
    fnd = validate(rs, paras, "PUBLISH")
    for x in fnd: print("  finding:", x)
    assert not [x for x in fnd if x["severity"] == "error"], "published example must have no errors"
    assert not fnd, "published example is expected to be clean of warnings too"
    print("example validates: no errors, no warnings")

    # the old ordering (terminal refer at 210 before reject at 220) must warn
    old = copy.deepcopy(rs)
    r = next(x for x in old["rules"] if x["id"] == "R-320"); r["priority"] = 210
    codes = [x["code"] for x in validate(old, paras, "PUBLISH")]
    assert "REFER_PRECEDES_REJECT" in codes, codes
    # unguarded division must warn
    old = copy.deepcopy(rs)
    next(x for x in old["rules"] if x["id"] == "R-020")["condition"] = {"always": True}
    assert "DIVISION_BY_UNGUARDED_FIELD" in [x["code"] for x in validate(old, paras, "PUBLISH")]
    # non-terminal candidate before terminal always must warn
    old = copy.deepcopy(rs)
    next(x for x in old["rules"] if x["id"] == "R-320")["actions"][0]["terminal"] = False
    assert "CANDIDATE_NEVER_WINS" in [x["code"] for x in validate(old, paras, "PUBLISH")]
    print("new static checks OK")

    # provenance contexts
    prop = copy.deepcopy(rs)
    r170 = next(x for x in prop["rules"] if x["id"] == "R-170")
    r170["condition"]["value"] = 9000
    r170["provenance"] = {"kind": "pending", "changeRequestId": "cr-0042", "rationale": "threshold raised per request"}
    assert not validate(prop, paras, "CHANGE_PROPOSAL", {"R-170"}), validate(prop, paras, "CHANGE_PROPOSAL", {"R-170"})
    assert "PROVENANCE_PENDING_AT_PUBLISH" in [x["code"] for x in validate(prop, paras, "PUBLISH")]
    # a model patch that claims analyst provenance is rejected; untouched analyst rules (R-310, R-410) are fine
    bad_prop = copy.deepcopy(prop)
    next(x for x in bad_prop["rules"] if x["id"] == "R-170")["provenance"] = {"kind": "analyst", "actor": "demo-analyst", "note": "claimed by the model"}
    assert "PROVENANCE_ANALYST_FROM_MODEL" in [x["code"] for x in validate(bad_prop, paras, "CHANGE_PROPOSAL", {"R-170"})]
    assert "PROVENANCE_ANALYST_FROM_MODEL" in [x["code"] for x in validate(rs, paras, "AUTHORING")]  # a draft may not contain analyst rules at all
    # approval converts pending -> analyst
    r170["provenance"] = {"kind": "analyst", "actor": "demo-analyst", "note": "Change request cr-0042: raise minimum income to 9,000. " + "threshold raised per request", "changeRequestId": "cr-0042"}
    assert not validate(prop, paras, "PUBLISH")
    print("provenance contexts OK")

    base = {"age": 34, "employment_type": "salaried", "employment_months": 30, "monthly_income": 9500,
            "existing_monthly_debt": 1200, "requested_amount": 60000, "term_months": 48, "credit_events_24m": 1}
    d = evaluate(rs, base)
    assert d["outcome"] == "refer" and d["decidingRuleId"] == "R-330", d
    with open(SAMPLE_DECISION_PATH, "w", encoding="utf-8") as f:
        json.dump(d, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("case 17:", d["outcome"], d["decidingRuleId"], d["derived"], [s["ruleId"] for s in d["trace"] if s["status"] == "skipped"])
    assert json.dumps(d, sort_keys=True) == json.dumps(evaluate(rs, base), sort_keys=True)

    cases = {
      "C-27 dti37 + 2 events": (dict(base, credit_events_24m=2, existing_monthly_debt=2020), "reject", "R-220"),
      "dti37 + 0 events": (dict(base, credit_events_24m=0, existing_monthly_debt=2020), "refer", "R-320"),
      "income 0 (business reject)": (dict(base, monthly_income=0), "reject", "R-170"),
      "approve": ({"age": 40, "employment_type": "salaried", "employment_months": 30, "monthly_income": 15000, "existing_monthly_debt": 500, "requested_amount": 50000, "term_months": 36, "credit_events_24m": 0}, "approve", "R-900"),
      "reject_age": (dict(base, age=19, credit_events_24m=0), "reject", "R-100"),
      "reject_dti": ({"age": 45, "employment_type": "self_employed", "employment_months": 60, "monthly_income": 8000, "existing_monthly_debt": 2500, "requested_amount": 60000, "term_months": 48, "credit_events_24m": 0}, "reject", "R-200"),
      "retired_ok": ({"age": 72, "employment_type": "retired", "monthly_income": 10000, "existing_monthly_debt": 0, "requested_amount": 30000, "term_months": 36, "credit_events_24m": 0}, "approve", "R-900"),
      "retired_term": ({"age": 74, "employment_type": "retired", "monthly_income": 10000, "existing_monthly_debt": 0, "requested_amount": 30000, "term_months": 60, "credit_events_24m": 0}, "reject", "R-116"),
      "seniority_absent": ({"age": 30, "employment_type": "salaried", "monthly_income": 12000, "existing_monthly_debt": 0, "requested_amount": 30000, "term_months": 24, "credit_events_24m": 0}, "refer", "R-310"),
      "flag_income": ({"age": 30, "employment_type": "salaried", "employment_months": 24, "monthly_income": 8500, "existing_monthly_debt": 0, "requested_amount": 20000, "term_months": 36, "credit_events_24m": 0}, "approve", "R-900"),
    }
    for name, (c, exp_out, exp_rule) in cases.items():
        r = evaluate(rs, c)
        assert r["status"] == "OK" and r["outcome"] == exp_out and r["decidingRuleId"] == exp_rule, (name, r.get("outcome"), r.get("decidingRuleId"), r.get("errorCode"))
        if exp_out == "approve":
            assert "STABLE_INCOME_MANUAL_CHECK" in [f["code"] for f in r["flags"]], name
        print(f"  {name:28s} -> {r['outcome']:8s} by {r['decidingRuleId']}  dti={r['derived']['debt_to_income']} flags={[f['code'] for f in r['flags']]}")

    # case errors
    for name, c, code in [("term 0", dict(base, term_months=0), "CASE_OUT_OF_RANGE"),
                          ("negative debt", dict(base, existing_monthly_debt=-5000), "CASE_OUT_OF_RANGE"),
                          ("negative income", dict(base, monthly_income=-9000), "CASE_OUT_OF_RANGE"),
                          ("age as string", dict(base, age="34"), "CASE_TYPE_MISMATCH"),
                          ("derived supplied", dict(base, debt_to_income=0.1), "CASE_DERIVED_SUPPLIED")]:
        try:
            evaluate(rs, c); assert False, name
        except CaseInvalid as e:
            assert e.problems[0]["code"] == code, (name, e.problems)
    print("case validation OK")

    # C-28: a guard that fails followed by a reader -> EVAL_DERIVED_ABSENT (synthetic rule set)
    syn = copy.deepcopy(rs)
    syn["rules"] = [x for x in syn["rules"] if x["id"] in ("R-010", "R-020", "R-200", "R-900")]
    r = evaluate(syn, dict(base, monthly_income=0))
    assert r["status"] == "ERROR" and r["errorCode"] == "EVAL_DERIVED_ABSENT" and r["errorRuleId"] == "R-200", r
    # C-19: division by zero is an ERROR with partial trace
    syn2 = copy.deepcopy(rs)
    syn2["fields"] = [f for f in syn2["fields"]]
    next(f for f in syn2["fields"] if f["name"] == "term_months").pop("minimum")
    r = evaluate(syn2, dict(base, term_months=0))
    assert r["status"] == "ERROR" and r["errorCode"] == "EVAL_DIV_ZERO" and r["errorRuleId"] == "R-010" and len(r["trace"]) == 1
    print("evaluation errors OK")

    # C-29: simulation for the chat counterfactual
    s = simulate(rs, base, {"has_guarantor": True})
    assert s["simulation"] and s["outcome"] == "approve" and s["decidingRuleId"] == "R-900"
    assert evaluate(rs, base)["decidingRuleId"] == "R-330"   # original unchanged
    print("simulation OK:", s["outcome"], s["decidingRuleId"], [f["code"] for f in s["flags"]])

    # negative schema tests
    bad = [
      ("unknown key", lambda x: x.__setitem__("extra", 1)),
      ("between 3 items", lambda x: next(r for r in x["rules"] if r["id"] == "R-120")["condition"]["not"].__setitem__("value", [1, 2, 3])),
      ("present with value", lambda x: next(r for r in x["rules"] if r["id"] == "R-310")["condition"]["all"][1].__setitem__("value", 1)),
      ("bad rule id", lambda x: x["rules"][0].__setitem__("id", "RULE1")),
      ("derived required", lambda x: next(f for f in x["fields"] if f["name"] == "monthly_installment").__setitem__("required", True)),
      ("enum without values", lambda x: next(f for f in x["fields"] if f["name"] == "employment_type").pop("values")),
      ("empty all", lambda x: x["rules"][3].__setitem__("condition", {"all": []})),
      ("analyst without note", lambda x: next(r for r in x["rules"] if r["id"] == "R-310")["provenance"].pop("note")),
      ("dsl version", lambda x: x.__setitem__("dslVersion", "2.0")),
      ("uppercase field", lambda x: x["fields"][0].__setitem__("name", "Age")),
      ("pending without rationale", lambda x: x["rules"][0].__setitem__("provenance", {"kind": "pending", "changeRequestId": "cr-1"})),
      ("matches pattern too long", lambda x: x["rules"][3].__setitem__("condition", {"field": "employment_type", "op": "matches", "value": "a" * 201})),
      ("matches with non-string", lambda x: x["rules"][3].__setitem__("condition", {"field": "employment_type", "op": "matches", "value": 5})),
    ]
    for name, mut in bad:
        x = copy.deepcopy(rs); mut(x)
        assert schema_errors(x), "should fail: " + name
    print("negative schema tests OK (%d)" % len(bad))
    print("ALL OK")
