"""Rule matching for the sample lending policy (Brief, Definition of Done line 3), as a manual checklist.

Applies Document 4, "Evaluation Set and Metrics", "Rule matching", to the ten live authoring runs of gate G1,
after the hand-written field and enum mapping in mappings.json. Expected values come only from
fixtures/eval/policies/consumer-lending/expected.ruleset.json and cases.json.

Three readings are computed; the first is the result, the other two are sensitivity checks only:
  strict    Document 4 literally. Conditions are equal after mapping and the listed normalizations
            (combinators flattened and sorted, `between` and `not between` unified with two comparisons,
            `gt x` / `gte x+1` and `lte x` / `lt x+1` unified on integer fields, enum operands as a sorted
            set of the closed enum). Set expressions must give the same exact decimal on every labeled case.
  semantic  strict, plus conditions that are not equal after normalization count as equivalent when they
            agree on a grid of inputs (exhaustive over integer and enum domains, boundary values for numbers),
            with derived fields that have no counterpart computed by the run's own set rules.
  unrounded semantic, plus set expressions compared with every `round` removed (relative tolerance 1e-9).

Run: python3 docs/eval/rule-match-lending/rulematch.py [--json out.json]
"""
import copy, itertools, json, sys
from decimal import Decimal as D
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
REC = REPO / "fixtures/eval/recordings/openai/author/v1/45e240b93e8f90a34f4c59cdbbd6217584198acf143358e43e66df2ce7cf2d3a"
EXPECTED = REPO / "fixtures/eval/policies/consumer-lending/expected.ruleset.json"
CASES = REPO / "fixtures/eval/policies/consumer-lending/cases.json"

_argv = sys.argv; sys.argv = [_argv[0]]                    # the reference reads argv[1] as a fixtures root
sys.path.insert(0, str(REPO / "fixtures/reference"))
import reference_check as ref                                # noqa: E402
sys.argv = _argv

EXP = json.load(open(EXPECTED, encoding="utf-8"))
EFIELDS = {f["name"]: f for f in EXP["fields"]}
CASELIST = json.load(open(CASES, encoding="utf-8"))["cases"]
MAPS = json.load(open(HERE / "mappings.json", encoding="utf-8"))["runs"]


def load_run(i):
    r = json.load(open(f"{REC}.run{i}.json", encoding="utf-8"))["response"]
    return json.loads(r) if isinstance(r, str) else r


# ----------------------------------------------------------------------------- mapping
class Unmapped(Exception):
    pass


def map_expr(e, m, keep_unmapped=False):
    if isinstance(e, (int, float)) and not isinstance(e, bool):
        return e
    if isinstance(e, dict) and "field" in e and "fn" not in e:
        to = m["fields"][e["field"]]["to"]
        if to is None:
            if keep_unmapped:
                return {"field": "~" + e["field"]}
            raise Unmapped(e["field"])
        return {"field": to}
    return {"fn": e["fn"], "args": [map_expr(a, m, keep_unmapped) for a in e["args"]]}


def map_value(field, v, m):
    enums = m["enums"].get(field)
    if enums is None:
        return v
    if isinstance(v, list):
        return [enums[x]["to"] for x in v]
    return enums[v]["to"]


def map_cond(c, m, keep_unmapped=False):
    """Rename fields and enum values; an unmapped field raises Unmapped (or is kept as ~name)."""
    if "always" in c:
        return {"always": True}
    for k in ("all", "any"):
        if k in c:
            return {k: [map_cond(x, m, keep_unmapped) for x in c[k]]}
    if "not" in c:
        return {"not": map_cond(c["not"], m, keep_unmapped)}
    to = m["fields"][c["field"]]["to"]
    if to is None:
        if not keep_unmapped:
            raise Unmapped(c["field"])
        to = "~" + c["field"]
    out = {"field": to, "op": c["op"]}
    if "value" in c:
        v = c["value"]
        out["value"] = map_expr(v, m, keep_unmapped) if isinstance(v, dict) else map_value(c["field"], v, m)
    return out


# ----------------------------------------------------------------------------- normalization (strict)
def num(x):
    return D(str(x))


def expr_key(e):
    if isinstance(e, (int, float)) and not isinstance(e, bool):
        return ("n", str(num(e).normalize()))
    if "field" in e and "fn" not in e:
        return ("f", e["field"])
    return ("fn", e["fn"], tuple(expr_key(a) for a in e["args"]))


def atom(field, op, value):
    f = EFIELDS[field]
    t = f["type"]
    if isinstance(value, dict):
        return ("cmp", field, op, expr_key(value))
    if t == "enum":
        vals = set(f["values"])
        if op == "eq": s = {value}
        elif op == "ne": s = vals - {value}
        elif op == "in": s = set(value)
        elif op == "not_in": s = vals - set(value)
        else: return ("cmp", field, op, repr(value))
        return ("enum_in", field, tuple(sorted(s)))
    if t in ("integer", "number") and isinstance(value, (int, float)) and not isinstance(value, bool):
        v = num(value)
        if t == "integer" and v == v.to_integral_value():
            if op == "gt": op, v = "gte", v + 1
            elif op == "lte": op, v = "lt", v + 1
        return ("cmp", field, op, str(v.normalize()))
    return ("cmp", field, op, repr(value))


def norm(c):
    if "always" in c:
        return ("always",)
    for k in ("all", "any"):
        if k in c:
            kids = []
            for x in c[k]:
                n = norm(x)
                kids.extend(n[1] if n[0] == k else [n])
            kids = sorted(set(kids), key=repr)
            return kids[0] if len(kids) == 1 else (k, tuple(kids))
    if "not" in c:
        inner = c["not"]
        if "op" in inner and inner["op"] == "between":           # not between == lt low or gt high
            lo, hi = inner["value"]
            return norm({"any": [{"field": inner["field"], "op": "lt", "value": lo},
                                 {"field": inner["field"], "op": "gt", "value": hi}]})
        return ("not", norm(inner))
    if c["field"].startswith("~"):
        return ("unmapped", c["field"], c["op"], repr(c.get("value")))
    if c["op"] == "between":                                      # between == gte low and lte high
        lo, hi = c["value"]
        return norm({"all": [{"field": c["field"], "op": "gte", "value": lo},
                             {"field": c["field"], "op": "lte", "value": hi}]})
    return atom(c["field"], c["op"], c.get("value"))


# ----------------------------------------------------------------------------- evaluation helpers
def eval_cond(c, env):
    """Condition truth with Document 3 semantics (missing comparison is false); env holds Decimals."""
    return ref.ev_cond(c, env, EFIELDS_EXT, [])


EFIELDS_EXT = dict(EFIELDS)   # grows with ~unmapped derived fields during the semantic check


def cases_env():
    """Each labeled case as an engine environment with the expected derived values computed in order."""
    envs = []
    for case in CASELIST:
        env = ref.validate_case(EXP, case["input"])
        for r in sorted(EXP["rules"], key=lambda r: (r["priority"], r["id"])):
            for a in r["actions"]:
                if a["type"] == "set" and ref.ev_cond(r["condition"], env, EFIELDS, []):
                    env[a["field"]] = ref.ev_expr(a["value"], env, EFIELDS)
        envs.append((case["id"], env))
    return envs


ENVS = cases_env()


def strip_round(e):
    if isinstance(e, dict) and e.get("fn") == "round":
        return strip_round(e["args"][0])
    if isinstance(e, dict) and "fn" in e:
        return {"fn": e["fn"], "args": [strip_round(a) for a in e["args"]]}
    return e


def compare_set(exp_rule, exp_action, gen_expr, unrounded):
    """(equal, detail) for two set expressions on every labeled case where the expected rule applies."""
    ee, ge = exp_action["value"], gen_expr
    if unrounded:
        ee, ge = strip_round(ee), strip_round(ge)
    diffs, n = [], 0
    for cid, env in ENVS:
        env = {k: v for k, v in env.items() if k != exp_action["field"]}   # inputs + the other expected derived values
        if not ref.ev_cond(exp_rule["condition"], env, EFIELDS, []):
            continue
        n += 1
        try:
            a = ref.ev_expr(ee, env, EFIELDS)
            b = ref.ev_expr(ge, env, EFIELDS)
        except ref.EvalError as e:
            return False, f"evaluation error {e.code} on case {cid}"
        if (abs(a - b) > D("1e-9") * max(abs(a), D(1))) if unrounded else (a != b):
            diffs.append((cid, a, b, abs(a - b)))
    if not diffs:
        return True, f"equal on {n} of {len(ENVS)} cases"
    mx = max(diffs, key=lambda d: d[3])
    return False, (f"differs on {len(diffs)} of {n} cases; largest difference {float(mx[3]):.6g} "
                   f"on case {mx[0]} (expected {float(mx[1])}, generated {float(mx[2]):.10g})")


# ----------------------------------------------------------------------------- semantic grid check
GRID_INT = {"age": range(0, 121), "term_months": range(1, 241), "employment_months": range(0, 61),
            "credit_events_24m": range(0, 6)}


def cond_fields(c, acc):
    if "always" in c: return acc
    for k in ("all", "any"):
        if k in c:
            for x in c[k]: cond_fields(x, acc)
            return acc
    if "not" in c: return cond_fields(c["not"], acc)
    acc.add(c["field"])
    if isinstance(c.get("value"), dict): expr_fields(c["value"], acc)
    return acc


def expr_fields(e, acc):
    if isinstance(e, dict):
        if "field" in e and "fn" not in e: acc.add(e["field"])
        else:
            for a in e["args"]: expr_fields(a, acc)
    return acc


def literals(c, acc):
    if isinstance(c, dict):
        for k, v in c.items():
            if k == "value" and isinstance(v, (int, float)) and not isinstance(v, bool): acc.add(num(v))
            elif k == "value" and isinstance(v, list):
                acc.update(num(x) for x in v if isinstance(x, (int, float)) and not isinstance(x, bool))
            else: literals(v, acc)
    elif isinstance(c, list):
        for x in c: literals(x, acc)
    return acc


def semantic_equal(exp_cond, gen_cond, gen_derivers):
    """Agree on a grid; gen_derivers are the run's mapped set rules for derived fields with no counterpart."""
    need = cond_fields(exp_cond, set()) | cond_fields(gen_cond, set())
    targets = {r["target"] for r in gen_derivers}
    inputs_without_counterpart = sorted(f[1:] for f in need if f.startswith("~") and f not in targets)
    if inputs_without_counterpart:
        return False, "condition reads the input " + ", ".join(inputs_without_counterpart) + " (no counterpart)"
    used, grew = [], True
    while grew:                                              # pull in the derivations used, transitively
        grew = False
        for r in gen_derivers:
            if r["target"] in need and r not in used:
                used.append(r); grew = True
                need |= cond_fields(r["condition"], set()) | expr_fields(r["value"], set())
    gen_derivers = [r for r in gen_derivers if r in used]    # keep priority order
    base = sorted(f for f in need if not f.startswith("~"))
    lits = literals(exp_cond, set()) | literals(gen_cond, set()) | set().union(*[literals(r["condition"], set()) for r in gen_derivers] or [set()])
    axes = []
    for f in base:
        t = EFIELDS[f]["type"]
        if t == "integer": axes.append(list(GRID_INT.get(f, range(0, 121))))
        elif t == "enum": axes.append(EFIELDS[f]["values"])
        elif t == "boolean": axes.append([True, False])
        else:
            pts = {D(EFIELDS[f].get("minimum", 0))}
            for x in lits:
                pts |= {x, x - D("1e-12"), x + D("1e-12"), x - D("0.01"), x + D("0.01")}
            pts |= {D(k) / 100 for k in range(0, 101)}
            axes.append(sorted(p for p in pts if p >= D(EFIELDS[f].get("minimum", 0))))
    points = 0
    for combo in itertools.product(*axes):
        env = {f: (D(v) if EFIELDS[f]["type"] == "integer" else v) for f, v in zip(base, combo)}
        try:
            for r in gen_derivers:
                if eval_cond(r["condition"], env):
                    env[r["target"]] = ref.ev_expr(r["value"], env, EFIELDS_EXT) if isinstance(r["value"], dict) \
                        else r["value"]
            a = eval_cond(exp_cond, env)
            b = eval_cond(gen_cond, env)
        except ref.EvalError:
            return False, "evaluation error on the grid"
        points += 1
        if a != b:
            return False, f"disagree at {({k: (str(v) if isinstance(v, D) else v) for k, v in env.items() if not k.startswith('~')})}"
    return True, f"agree on all {points} grid points"


# ----------------------------------------------------------------------------- matching
def action_sig(a):
    if a["type"] == "decide": return ("decide", a["outcome"], a.get("terminal", True))
    if a["type"] == "flag": return ("flag", a["code"])
    return ("set",)


def rules_match(er, gr, m, mode, derivers):
    """(matched, reason) for one expected rule and one generated rule under a reading."""
    if len(er["actions"]) != len(gr["actions"]):
        return False, "different number of actions"
    ea, ga = er["actions"][0], gr["actions"][0]
    if action_sig(ea) != action_sig(ga):
        return False, f"action {action_sig(ga)} instead of {action_sig(ea)}"
    if ea["type"] == "set":
        tgt = m["fields"][ga["field"]]["to"]
        if tgt != ea["field"]:
            return False, f"sets {ga['field']} (no counterpart)" if tgt is None else f"sets {tgt}, not {ea['field']}"
        try:
            gexpr = map_expr(ga["value"], m)
        except Unmapped as u:
            return False, f"set expression reads {u} (no counterpart)"
        ok, detail = compare_set(er, ea, gexpr, unrounded=(mode == "unrounded"))
        if not ok:
            return False, "set value " + detail
    gc = map_cond(gr["condition"], m, keep_unmapped=True)
    if norm(gc) == norm(er["condition"]):
        return True, "equal after normalization"
    if mode == "strict":
        unm = sorted(f[1:] for f in cond_fields(gc, set()) if f.startswith("~"))
        return False, ("condition reads " + ", ".join(unm) + " (no counterpart)") if unm else "condition not equivalent after normalization"
    return semantic_equal(er["condition"], gc, derivers)


def derivers_for(gen, m):
    out = []
    for r in sorted(gen["rules"], key=lambda r: (r["priority"], r["id"])):
        for a in r["actions"]:
            if a["type"] == "set" and m["fields"][a["field"]]["to"] is None:
                tgt = "~" + a["field"]
                EFIELDS_EXT[tgt] = {"name": tgt, "type": "number", "derived": True}
                v = a["value"]
                out.append({"target": tgt, "condition": map_cond(r["condition"], m, keep_unmapped=True),
                            "value": map_expr(v, m, keep_unmapped=True) if isinstance(v, dict) else v})
    return out


def max_matching(cands, order):
    """Maximum bipartite matching expected -> generated; cands[e] is an ordered list of generated ids."""
    owner = {}

    def augment(e, seen):
        for g in cands[e]:
            if g in seen: continue
            seen.add(g)
            if g not in owner or augment(owner[g], seen):
                owner[g] = e
                return True
        return False

    for e in order:
        augment(e, set())
    return {e: g for g, e in owner.items()}


def run_one(i, mode):
    gen, m = load_run(i), MAPS[str(i)]
    for f in gen["fields"]:
        assert f["name"] in m["fields"], f"run {i}: field {f['name']} has no mapping entry"
    derivers = derivers_for(gen, m) if mode != "strict" else []
    grules = {r["id"]: r for r in gen["rules"]}
    cands, reasons = {}, {}
    for er in EXP["rules"]:
        ok_list = []
        for gr in gen["rules"]:
            ok, why = rules_match(er, gr, m, mode, derivers)
            reasons[(er["id"], gr["id"])] = why
            if ok: ok_list.append(gr["id"])
        # prefer the candidate that cites the expected paragraph
        ok_list.sort(key=lambda g: grules[g]["provenance"].get("paragraph") != er["provenance"]["paragraph"])
        cands[er["id"]] = ok_list
    match = max_matching(cands, [r["id"] for r in EXP["rules"]])
    prov = sum(1 for e, g in match.items()
               if grules[g]["provenance"].get("paragraph") == next(r for r in EXP["rules"] if r["id"] == e)["provenance"]["paragraph"])
    return {"run": i, "mode": mode, "generated": len(gen["rules"]), "expected": len(EXP["rules"]),
            "matched": len(match), "recall": len(match) / len(EXP["rules"]), "precision": len(match) / len(gen["rules"]),
            "provenance_correct": prov, "match": match, "candidates": cands,
            "unmatched_generated": [g for g in grules if g not in match.values()],
            "reasons": {f"{e}|{g}": w for (e, g), w in reasons.items()}}


def median(xs):
    s = sorted(xs); n = len(s)
    return s[n // 2] if n % 2 else (s[n // 2 - 1] + s[n // 2]) / 2


def main():
    out = {}
    for mode in ("strict", "semantic", "unrounded"):
        res = [run_one(i, mode) for i in range(1, 11)]
        out[mode] = res
        print(f"\n== {mode}")
        print("run | generated | matched/18 | recall | precision | provenance ok")
        for r in res:
            print(f"{r['run']:>3} | {r['generated']:>9} | {r['matched']:>2} of 18 | {r['recall']:.1%} | {r['precision']:.1%} | "
                  f"{r['provenance_correct']} of {r['matched']}")
        print(f"median recall {median([r['recall'] for r in res]):.1%}, lowest {min(r['recall'] for r in res):.1%}; "
              f"median precision {median([r['precision'] for r in res]):.1%}, lowest {min(r['precision'] for r in res):.1%}")
        for r in res:
            miss = [e["id"] for e in EXP["rules"] if e["id"] not in r["match"]]
            print(f"  run {r['run']}: unmatched expected {miss}; unmatched generated {r['unmatched_generated']}")
    if "--json" in sys.argv:
        json.dump(out, open(sys.argv[sys.argv.index("--json") + 1], "w"), ensure_ascii=False, indent=1, default=str)


if __name__ == "__main__":
    main()
