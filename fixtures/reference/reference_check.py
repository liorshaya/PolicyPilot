"""Reference implementation of the PolicyPilot Rules DSL 1.0 semantics (Document 3).

Not the product engine (that is Java); this script exists so that every number and every
claim in the specification is produced by code, and so the conformance cases can be
checked before the Java engine exists.

Run: python3 fixtures/reference/reference_check.py [fixtures-root]

Paths are resolved from the fixtures root (Document 6, "Fixtures layout"); the default root is the
parent of this file's directory, so the script runs from any working directory:
  <root>/schemas/ruleset-1.0.schema.json                 the DSL 1.0 schema
  <root>/policies/consumer-lending/ruleset.v1.json       the published example rule set
  <root>/policies/consumer-lending/policy.he.md          the Hebrew policy text (paragraphs separated by blank lines)
  <root>/policies/consumer-lending/sample-decision.json  golden decision for case 17, written by the self-test
  <root>/policies/consumer-lending/cases-200.json        the 200 fixture cases (written by tools/generate_cases.py)
  <root>/conformance/C-01.json ... C-31.json             the conformance suite (Document 3), run by run_conformance()
  <root>/conformance/invalid-<CODE>.json                 one rule set per validator code and warning, run by run_invalid()
  <root>/eval/policies/<slug>/, questions.json, changes.json   the labeled evaluation set (Document 4), admitted by run_eval()

The validator runs in three layers (schema, semantic, structural) and stops after the first layer
that reports an error; warnings and info never stop it. The engine is a pure function of the rule
set and the case: no clock, no randomness, exact decimals, total ordering.
"""
import json, re, sys, unicodedata, copy
from pathlib import Path
from decimal import Decimal, ROUND_HALF_EVEN, getcontext
from datetime import date
from jsonschema import Draft202012Validator

getcontext().prec = 40
D = Decimal
SCALE = D("0.000000000001")  # 12 places

# ----------------------------------------------------------------------------- paths
ROOT = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 and not sys.argv[1].startswith("-") else Path(__file__).resolve().parent.parent
SCHEMA_PATH = ROOT / "schemas" / "ruleset-1.0.schema.json"
POLICY_DIR = ROOT / "policies" / "consumer-lending"
RULESET_PATH = POLICY_DIR / "ruleset.v1.json"
POLICY_TEXT_PATH = POLICY_DIR / "policy.he.md"
SAMPLE_DECISION_PATH = POLICY_DIR / "sample-decision.json"
CASES_PATH = POLICY_DIR / "cases-200.json"
CONFORMANCE_DIR = ROOT / "conformance"

# ----------------------------------------------------------------------------- schema
schema = json.load(open(SCHEMA_PATH, encoding="utf-8"))
Draft202012Validator.check_schema(schema)
V = Draft202012Validator(schema)

def schema_errors(doc):
    return sorted(V.iter_errors(doc), key=lambda e: [str(p) for p in e.path])

# ----------------------------------------------------------------------------- validator
NUMERIC = ("number", "integer")
RESERVED = {"today", "now", "null", "true", "false"}
ARITY = {"add": (2, 8), "mul": (2, 8), "min": (2, 8), "max": (2, 8), "sub": (2, 2), "div": (2, 2),
         "pow": (2, 2), "round": (2, 2), "months_between": (2, 2), "abs": (1, 1)}
BANDS = [(1, 99, "set"), (100, 299, "reject"), (300, 399, "refer"), (400, 499, "flag"), (900, 999, "approve")]
RE2_UNSUPPORTED = re.compile(r"\\[1-9]|\(\?<?[=!]")
LAYER = {}
SEVERITY = {}
for _code in ("DSL_SCHEMA", "DSL_VERSION_UNSUPPORTED", "DERIVED_REQUIRED"):
    LAYER[_code], SEVERITY[_code] = "schema", "error"
for _code in ("FIELD_DUPLICATE", "FIELD_UNKNOWN", "FIELD_TYPE_MISMATCH", "FIELD_DOMAIN_INVALID", "ENUM_VALUE_UNKNOWN",
              "EXPR_TYPE_MISMATCH", "EXPR_ARITY", "BETWEEN_RANGE_INVALID", "REGEX_INVALID", "RESERVED_IDENTIFIER",
              "RULE_ID_DUPLICATE", "DERIVED_WRITE_ONLY", "PROVENANCE_PARAGRAPH_MISSING", "PROVENANCE_QUOTE_MISMATCH",
              "PROVENANCE_ANALYST_FROM_MODEL", "PROVENANCE_PENDING_FROM_MODEL", "PROVENANCE_PENDING_AT_PUBLISH"):
    LAYER[_code], SEVERITY[_code] = "semantic", "error"
for _code, _sev in (("DERIVED_CYCLE", "error"), ("DERIVED_ORDER", "error"), ("DERIVED_NEVER_SET", "warning"),
                    ("FIELD_UNUSED", "warning"), ("RULE_UNREACHABLE", "warning"), ("RULE_OVERLAP_CONFLICT", "warning"),
                    ("REFER_PRECEDES_REJECT", "warning"), ("CANDIDATE_NEVER_WINS", "warning"),
                    ("DIVISION_BY_UNGUARDED_FIELD", "warning"), ("MISSING_FIELD_UNDER_NOT", "warning"),
                    ("PRIORITY_BAND_UNUSUAL", "info"), ("NO_TERMINAL_APPROVE", "info")):
    LAYER[_code], SEVERITY[_code] = "structural", _sev

def F(code, path, message):
    return {"code": code, "severity": SEVERITY[code], "path": path, "message": message}

def norm(s):
    s = unicodedata.normalize("NFKD", s)
    s = "".join(ch for ch in s if not unicodedata.category(ch).startswith("M"))
    s = s.lower()
    s = re.sub(r"[\"'“”‘’«»,.;:!?()\[\]{}\-–—/%]", "", s)
    return re.sub(r"\s+", " ", s).strip()

def is_cmp(c):
    return isinstance(c, dict) and "field" in c and "op" in c

def is_date(v):
    if not (isinstance(v, str) and re.fullmatch(r"\d{4}-\d{2}-\d{2}", v)): return False
    try: date.fromisoformat(v); return True
    except ValueError: return False

def type_ok(ftype, v):
    """A literal fits a field type."""
    if isinstance(v, bool): return ftype == "boolean"
    if isinstance(v, (int, float)): return ftype in NUMERIC and (ftype == "number" or float(v).is_integer())
    if isinstance(v, str):
        if ftype in ("string", "enum"): return True
        if ftype == "date": return is_date(v)
    return False

def refs_in_expr(e, acc):
    if isinstance(e, dict):
        if "field" in e and "fn" not in e: acc.add(e["field"])
        for a in e.get("args", []): refs_in_expr(a, acc)

def refs_in_cond(c, acc):
    if is_cmp(c):
        acc.add(c["field"]); refs_in_expr(c.get("value"), acc)
    for k in ("all", "any"):
        for x in c.get(k, []): refs_in_cond(x, acc)
    if "not" in c: refs_in_cond(c["not"], acc)

def cond_excludes_zero(c, field):
    """True when the condition (top level or inside a top-level all) requires field > 0."""
    leaves = [c] if is_cmp(c) else c.get("all", [])
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

# --- layer 1: schema
def schema_layer(rs):
    out = []
    if not isinstance(rs, dict):
        return [F("DSL_SCHEMA", "/", "document is not an object")]
    if rs.get("dslVersion") != "1.0":
        out.append(F("DSL_VERSION_UNSUPPORTED", "/dslVersion", str(rs.get("dslVersion"))))
    for e in schema_errors(rs):
        sp = [str(p) for p in e.absolute_schema_path]
        if "dslVersion" in sp: continue
        code = "DSL_SCHEMA"
        # the field schema's allOf[1] is the derived-field constraint (required false, no default): reported by name
        if sp[:3] == ["properties", "fields", "items"] and sp[3:5] == ["allOf", "1"]:
            code = "DERIVED_REQUIRED"
        out.append(F(code, "/" + "/".join(str(p) for p in e.path), e.message[:160]))
    return out

# --- layer 2: semantic
def check_expr(e, fields, rid, out, path):
    """Returns the expression's type: 'number', 'date' or None (error already reported)."""
    if isinstance(e, bool): out.append(F("EXPR_TYPE_MISMATCH", path, f"{rid} boolean literal in expression")); return None
    if isinstance(e, (int, float)): return "number"
    if "fn" not in e:
        f = fields.get(e["field"])
        if not f: out.append(F("FIELD_UNKNOWN", path, f"{rid} {e['field']}")); return None
        if f["type"] in NUMERIC: return "number"
        if f["type"] == "date": return "date"
        out.append(F("EXPR_TYPE_MISMATCH", path, f"{rid} {e['field']} is {f['type']}")); return None
    fn, args = e["fn"], e["args"]
    lo, hi = ARITY[fn]
    if not (lo <= len(args) <= hi):
        out.append(F("EXPR_ARITY", path, f"{rid} {fn} takes {lo}..{hi} arguments, got {len(args)}")); return None
    types = [check_expr(a, fields, rid, out, path) for a in args]
    if fn == "months_between":
        for t, a in zip(types, args):
            if t is not None and t != "date":
                out.append(F("EXPR_TYPE_MISMATCH", path, f"{rid} months_between needs date fields")); return None
        return "number"
    for t in types:
        if t == "date":
            out.append(F("EXPR_TYPE_MISMATCH", path, f"{rid} date field in arithmetic")); return None
    return "number"

def check_cmp(c, fields, rid, out, path):
    f = fields.get(c["field"])
    if not f:
        out.append(F("FIELD_UNKNOWN", path, f"{rid} {c['field']}")); return
    op, v, ftype = c["op"], c.get("value"), f["type"]
    if op in ("present", "absent"): return
    if op == "matches":
        if ftype != "string":
            out.append(F("FIELD_TYPE_MISMATCH", path, f"{rid} matches on {ftype} field {c['field']}")); return
        if len(v) > 200 or RE2_UNSUPPORTED.search(v):
            out.append(F("REGEX_INVALID", path, f"{rid} pattern too long or uses unsupported syntax")); return
        try: re.compile(v)
        except re.error: out.append(F("REGEX_INVALID", path, f"{rid} pattern does not compile"))
        return
    if op in ("in", "not_in"):
        if not all(type_ok(ftype, x) for x in v):
            out.append(F("FIELD_TYPE_MISMATCH", path, f"{rid} {op} list does not fit {ftype} field {c['field']}")); return
        if ftype == "enum":
            unknown = [x for x in v if x not in f["values"]]
            if unknown: out.append(F("ENUM_VALUE_UNKNOWN", path, f"{rid} {c['field']} {unknown}"))
        return
    if op == "between":
        if ftype not in NUMERIC + ("date",):
            out.append(F("FIELD_TYPE_MISMATCH", path, f"{rid} between on {ftype} field {c['field']}")); return
        lo, hi = v
        if not (type_ok(ftype, lo) and type_ok(ftype, hi)):
            out.append(F("FIELD_TYPE_MISMATCH", path, f"{rid} between bounds do not fit {ftype}")); return
        if lo > hi: out.append(F("BETWEEN_RANGE_INVALID", path, f"{rid} {c['field']} [{lo}, {hi}]"))
        return
    # eq ne lt lte gt gte
    ordered = op in ("lt", "lte", "gt", "gte")
    if ordered and ftype not in NUMERIC + ("date",):
        out.append(F("FIELD_TYPE_MISMATCH", path, f"{rid} {op} on {ftype} field {c['field']}")); return
    if isinstance(v, dict):
        if "fn" in v:
            if ftype not in NUMERIC:
                out.append(F("FIELD_TYPE_MISMATCH", path, f"{rid} expression against {ftype} field {c['field']}")); return
            check_expr(v, fields, rid, out, path); return
        other = fields.get(v["field"])
        if not other: out.append(F("FIELD_UNKNOWN", path, f"{rid} {v['field']}")); return
        cat = lambda t: "number" if t in NUMERIC else ("string" if t in ("string", "enum") else t)
        if cat(ftype) != cat(other["type"]):
            out.append(F("FIELD_TYPE_MISMATCH", path, f"{rid} {c['field']} compared with {v['field']}"))
        return
    if not type_ok(ftype, v):
        out.append(F("FIELD_TYPE_MISMATCH", path, f"{rid} literal {v!r} against {ftype} field {c['field']}")); return
    if ftype == "enum" and v not in f["values"]:
        out.append(F("ENUM_VALUE_UNKNOWN", path, f"{rid} {c['field']} {v!r}"))

def check_cond(c, fields, rid, out, path):
    if is_cmp(c): check_cmp(c, fields, rid, out, path); return
    for k in ("all", "any"):
        for x in c.get(k, []): check_cond(x, fields, rid, out, path)
    if "not" in c: check_cond(c["not"], fields, rid, out, path)

def semantic_layer(rs, paras, context, model_rule_ids):
    out = []
    fields = {}
    for i, f in enumerate(rs["fields"]):
        if f["name"] in fields: out.append(F("FIELD_DUPLICATE", f"/fields/{i}", f["name"]))
        fields[f["name"]] = f
        if f["name"] in RESERVED: out.append(F("RESERVED_IDENTIFIER", f"/fields/{i}", f["name"]))
        dom = [k for k in ("minimum", "maximum", "exclusiveMinimum", "exclusiveMaximum") if k in f]
        if dom and f["type"] not in NUMERIC:
            out.append(F("FIELD_DOMAIN_INVALID", f"/fields/{i}", f"{f['name']} is {f['type']} but declares {dom}"))
        elif dom:
            lo = f.get("minimum", f.get("exclusiveMinimum")); hi = f.get("maximum", f.get("exclusiveMaximum"))
            if lo is not None and hi is not None and lo > hi:
                out.append(F("FIELD_DOMAIN_INVALID", f"/fields/{i}", f"{f['name']} minimum {lo} exceeds maximum {hi}"))
        s = f.get("source")
        if s:
            if not (1 <= s["paragraph"] <= len(paras)):
                out.append(F("PROVENANCE_PARAGRAPH_MISSING", f"/fields/{i}/source", f["name"]))
            elif norm(s["quote"]) not in norm(paras[s["paragraph"] - 1]):
                out.append(F("PROVENANCE_QUOTE_MISMATCH", f"/fields/{i}/source", f["name"]))
    seen = set()
    for i, r in enumerate(rs["rules"]):
        rid, path = r["id"], f"/rules/{i}"
        if rid in seen: out.append(F("RULE_ID_DUPLICATE", path, rid))
        seen.add(rid)
        p = r["provenance"]
        if p["kind"] == "quoted":
            if not (1 <= p["paragraph"] <= len(paras)):
                out.append(F("PROVENANCE_PARAGRAPH_MISSING", path + "/provenance", rid))
            elif norm(p["quote"]) not in norm(paras[p["paragraph"] - 1]):
                out.append(F("PROVENANCE_QUOTE_MISMATCH", path + "/provenance", rid))
        elif p["kind"] == "analyst" and (context == "AUTHORING" or (context == "CHANGE_PROPOSAL" and rid in (model_rule_ids or set()))):
            out.append(F("PROVENANCE_ANALYST_FROM_MODEL", path + "/provenance", rid))
        elif p["kind"] == "pending" and context != "CHANGE_PROPOSAL":
            out.append(F("PROVENANCE_PENDING_AT_PUBLISH" if context == "PUBLISH" else "PROVENANCE_PENDING_FROM_MODEL", path + "/provenance", rid))
        check_cond(r["condition"], fields, rid, out, path + "/condition")
        for j, a in enumerate(r["actions"]):
            if a["type"] != "set": continue
            apath = f"{path}/actions/{j}"
            fld = fields.get(a["field"])
            if not fld: out.append(F("FIELD_UNKNOWN", apath, f"{rid} {a['field']}")); continue
            if not fld.get("derived"): out.append(F("DERIVED_WRITE_ONLY", apath, f"{rid} sets {a['field']}"))
            if isinstance(a["value"], dict):
                if fld["type"] not in NUMERIC: out.append(F("FIELD_TYPE_MISMATCH", apath, f"{rid} expression into {fld['type']} field {a['field']}"))
                else: check_expr(a["value"], fields, rid, out, apath)
            elif not type_ok(fld["type"], a["value"]):
                out.append(F("FIELD_TYPE_MISMATCH", apath, f"{rid} literal {a['value']!r} into {fld['type']} field {a['field']}"))
    return out

# --- layer 3: structural
class Interval:
    def __init__(self, lo, lo_inc, hi, hi_inc): self.lo, self.lo_inc, self.hi, self.hi_inc = lo, lo_inc, hi, hi_inc
    @staticmethod
    def of(op, v):
        num = lambda x: isinstance(x, (int, float)) and not isinstance(x, bool)
        if op == "between" and isinstance(v, list) and len(v) == 2 and num(v[0]) and num(v[1]): return Interval(v[0], True, v[1], True)
        if not num(v): return None
        return {"eq": Interval(v, True, v, True), "lt": Interval(None, False, v, False), "lte": Interval(None, False, v, True),
                "gt": Interval(v, False, None, False), "gte": Interval(v, True, None, False)}.get(op)
    def intersect(self, o):
        lo, lo_inc = self.lo, self.lo_inc
        if o.lo is not None and (lo is None or o.lo > lo or (o.lo == lo and not o.lo_inc)): lo, lo_inc = o.lo, o.lo_inc
        hi, hi_inc = self.hi, self.hi_inc
        if o.hi is not None and (hi is None or o.hi < hi or (o.hi == hi and not o.hi_inc)): hi, hi_inc = o.hi, o.hi_inc
        return Interval(lo, lo_inc, hi, hi_inc)
    def empty(self):
        return self.lo is not None and self.hi is not None and (self.lo > self.hi or (self.lo == self.hi and not (self.lo_inc and self.hi_inc)))
    def subset_of(self, o):
        if o.lo is not None and (self.lo is None or self.lo < o.lo or (self.lo == o.lo and self.lo_inc and not o.lo_inc)): return False
        if o.hi is not None and (self.hi is None or self.hi > o.hi or (self.hi == o.hi and self.hi_inc and not o.hi_inc)): return False
        return True
    def overlaps(self, o): return not self.intersect(o).empty()

class EnumSet:
    def __init__(self, allowed): self.allowed = frozenset(allowed)
    @staticmethod
    def of(op, v, values):
        vs = set(values)
        if op == "eq" and isinstance(v, str): return EnumSet({v} & vs)
        if op == "ne" and isinstance(v, str): return EnumSet(vs - {v})
        if op == "in" and isinstance(v, list): return EnumSet(set(v) & vs)
        if op == "not_in" and isinstance(v, list): return EnumSet(vs - set(v))
        return None
    def intersect(self, o): return EnumSet(self.allowed & o.allowed)
    def empty(self): return not self.allowed
    def subset_of(self, o): return self.allowed <= o.allowed
    def overlaps(self, o): return bool(self.allowed & o.allowed)

def simple_constraints(cond, fields):
    """{field: Interval|EnumSet} for a comparison or an all-of comparisons with literal operands; None otherwise."""
    leaves = [cond] if is_cmp(cond) else (cond["all"] if "all" in cond else None)
    if leaves is None: return None
    out = {}
    for l in leaves:
        if not is_cmp(l) or l["field"] not in fields: return None
        f = fields[l["field"]]
        c = Interval.of(l["op"], l.get("value")) if f["type"] in NUMERIC else (EnumSet.of(l["op"], l.get("value"), f["values"]) if f["type"] == "enum" else None)
        if c is None: return None
        out[l["field"]] = out[l["field"]].intersect(c) if l["field"] in out else c
    return out

def decides(r, outcome=None, terminal=None):
    for a in r["actions"]:
        if a["type"] != "decide": continue
        if outcome is not None and a["outcome"] != outcome: continue
        if terminal is not None and a.get("terminal", True) != terminal: continue
        return True
    return False

def under_not(c, fields, acc, inside=False, present=None):
    if present is None:
        present = set()
        def collect(x):
            if is_cmp(x) and x["op"] == "present": present.add(x["field"])
            for k in ("all", "any"):
                for y in x.get(k, []): collect(y)
            if "not" in x: collect(x["not"])
        collect(c)
    if is_cmp(c):
        f = fields.get(c["field"])
        if inside and f and c["op"] not in ("present", "absent") and not f.get("derived") and not f.get("required") and "default" not in f and c["field"] not in present:
            acc.add(c["field"])
        return
    for k in ("all", "any"):
        for x in c.get(k, []): under_not(x, fields, acc, inside, present)
    if "not" in c: under_not(c["not"], fields, acc, True, present)

def structural_layer(rs):
    out = []
    fields = {f["name"]: f for f in rs["fields"]}
    rules = sorted([r for r in rs["rules"] if r.get("enabled", True)], key=lambda r: (r["priority"], r["id"]))
    # derived fields: setters, dependency graph
    setters, deps, used = {}, {}, set()
    for r in rules:
        for a in r["actions"]:
            if a["type"] == "set" and a["field"] in fields:
                setters.setdefault(a["field"], r["priority"]); used.add(a["field"])
                acc = set(); refs_in_expr(a["value"], acc)
                # a rule that reads the field it sets (x = max(x, 5)) is a read-modify-write, legal after an earlier
                # set (DERIVED_ORDER checks that), so self-edges are not cycles
                deps.setdefault(a["field"], set()).update(x for x in acc if fields.get(x, {}).get("derived") and x != a["field"])
    def has_cycle(start):
        seen, stack = set(), [start]
        while stack:
            x = stack.pop()
            for y in deps.get(x, ()):
                if y == start: return True
                if y not in seen: seen.add(y); stack.append(y)
        return False
    for name in sorted(deps):
        if has_cycle(name): out.append(F("DERIVED_CYCLE", "/rules", f"{name} depends on itself"))
    for r in rules:
        acc = set(); refs_in_cond(r["condition"], acc)
        for a in r["actions"]:
            if a["type"] == "set": refs_in_expr(a["value"], acc)
        used |= acc
        for name in sorted(acc):
            # a read needs a set strictly earlier; a rule reading the field it sets needs an earlier setter too
            if fields.get(name, {}).get("derived") and name in setters and setters[name] >= r["priority"]:
                out.append(F("DERIVED_ORDER", "/rules", f"{r['id']} reads {name}, first set at priority {setters[name]}"))
        divs = set()
        for a in r["actions"]:
            if a["type"] == "set": divisors(a["value"], divs)
        for dname in sorted(divs):
            fd = fields.get(dname, {})
            safe = ("exclusiveMinimum" in fd and fd["exclusiveMinimum"] >= 0) or ("minimum" in fd and fd["minimum"] > 0)
            if not safe and not cond_excludes_zero(r["condition"], dname):
                out.append(F("DIVISION_BY_UNGUARDED_FIELD", "/rules", f"{r['id']} divides by {dname}, which may be 0 and is not excluded by the rule's condition"))
        missing = set(); under_not(r["condition"], fields, missing)
        for name in sorted(missing):
            out.append(F("MISSING_FIELD_UNDER_NOT", "/rules", f"{r['id']} {name} under not without a present guard"))
        band = next(((lo, hi, kind) for lo, hi, kind in BANDS if lo <= r["priority"] <= hi), None)
        if band:
            kind = band[2]
            ok = (kind == "set" and any(a["type"] == "set" for a in r["actions"])) or \
                 (kind == "flag" and any(a["type"] == "flag" for a in r["actions"])) or \
                 (kind in ("reject", "refer", "approve") and decides(r, kind))
            if not ok: out.append(F("PRIORITY_BAND_UNUSUAL", "/rules", f"{r['id']} at {r['priority']} is in the {kind} band"))
    for f in rs["fields"]:
        if f.get("derived") and f["name"] not in setters: out.append(F("DERIVED_NEVER_SET", "/fields", f["name"]))
        elif not f.get("derived") and f["name"] not in used: out.append(F("FIELD_UNUSED", "/fields", f["name"]))
    # reachability of terminal rules, overlap of decide rules
    terminals = [r for r in rules if decides(r, terminal=True)]
    cons = {r["id"]: simple_constraints(r["condition"], fields) for r in rules}
    for j, b in enumerate(terminals):
        for a in terminals[:j]:
            if "always" in a["condition"] or (cons[a["id"]] and cons[b["id"]] and all(
                    f in cons[b["id"]] and cons[b["id"]][f].subset_of(cons[a["id"]][f]) for f in cons[a["id"]])):
                out.append(F("RULE_UNREACHABLE", "/rules", f"{b['id']} is subsumed by {a['id']}")); break
    deciders = [r for r in rules if decides(r)]
    for j, b in enumerate(deciders):
        for a in deciders[:j]:
            if decides(a, terminal=True): continue
            ca, cb = cons[a["id"]], cons[b["id"]]
            if not ca or not cb or len(ca) != 1 or len(cb) != 1: continue
            (fa, ia), (fb, ib) = next(iter(ca.items())), next(iter(cb.items()))
            oa = next(x["outcome"] for x in a["actions"] if x["type"] == "decide")
            ob = next(x["outcome"] for x in b["actions"] if x["type"] == "decide")
            if fa == fb and isinstance(ia, Interval) and oa != ob and ia.overlaps(ib):
                out.append(F("RULE_OVERLAP_CONFLICT", "/rules", f"{a['id']} ({oa}) and {b['id']} ({ob}) overlap on {fa}"))
    last_reject = max([r["priority"] for r in rules if decides(r, "reject", True)], default=None)
    for r in rules:
        if decides(r, "refer", True) and last_reject is not None and r["priority"] < last_reject and "precedence_intended" not in r.get("tags", []):
            out.append(F("REFER_PRECEDES_REJECT", "/rules", f"terminal refer {r['id']} at {r['priority']} precedes a terminal reject at {last_reject}"))
    always_terminal = [r["priority"] for r in rules if "always" in r["condition"] and decides(r, terminal=True)]
    for r in rules:
        if decides(r, terminal=False) and any(p > r["priority"] for p in always_terminal):
            out.append(F("CANDIDATE_NEVER_WINS", "/rules", r["id"]))
    if not any(decides(r, "approve") for r in rules):
        out.append(F("NO_TERMINAL_APPROVE", "/rules", "no rule can produce approve"))
    return out

def validate(rs, paras, context="AUTHORING", model_rule_ids=None):
    """context: AUTHORING (model draft: every rule is the model's), CHANGE_PROPOSAL (only the patched
    rules, model_rule_ids, are the model's; untouched rules keep their provenance), PUBLISH, ANALYST_EDIT.
    Returns the list of findings {code, severity, path, message}; stops after the first layer with an error."""
    out = schema_layer(rs)
    if out: return out
    out = semantic_layer(rs, paras, context, model_rule_ids)
    if any(f["severity"] == "error" for f in out): return out
    return out + structural_layer(rs)

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
            elif "default" in f: case[n] = D(str(f["default"])) if t in NUMERIC else f["default"]
            continue
        v = raw[n]
        ok = True
        if t in NUMERIC:
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
        elif t == "date": ok = is_date(v)
        if not ok: problems.append({"code": "CASE_TYPE_MISMATCH", "field": n, "value": raw[n]}); continue
        case[n] = v
    if problems: raise CaseInvalid(problems)
    return case

def months_between(a, b):
    """Whole calendar months from date a to date b; negative when b precedes a."""
    da, db = date.fromisoformat(a), date.fromisoformat(b)
    if db < da: return -months_between(b, a)
    months = (db.year - da.year) * 12 + (db.month - da.month)
    if db.day < da.day: months -= 1
    return months

def ev_expr(e, case, fields):
    if isinstance(e, bool): raise EvalError("EXPR_TYPE_MISMATCH")
    if isinstance(e, (int, float)): return D(str(e))
    if "field" in e and "fn" not in e:
        f, v = fields[e["field"]], case.get(e["field"])
        if v is None:
            if f.get("derived"): raise EvalError("EVAL_DERIVED_ABSENT", e["field"])
            raise EvalError("EVAL_ABSENT_DATE" if f["type"] == "date" else "EVAL_ABSENT_FIELD", e["field"])
        return v
    fn = e["fn"]
    args = [ev_expr(a, case, fields) for a in e["args"]]
    if fn == "months_between":
        if not all(isinstance(a, str) for a in args): raise EvalError("EXPR_TYPE_MISMATCH")
        return D(months_between(args[0], args[1]))
    if not all(isinstance(a, D) for a in args): raise EvalError("EXPR_TYPE_MISMATCH")
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
        try:
            r = args[0] ** args[1]
            if not r.is_finite(): raise EvalError("EVAL_NON_FINITE")
            return r
        except EvalError: raise
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
    elif op == "matches": res = re.fullmatch(v, actual[:2000]) is not None   # whole value, Java matches() semantics
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
                        new = ev_expr(a["value"], case, fields) if isinstance(a["value"], dict) else (D(str(a["value"])) if isinstance(a["value"], (int, float)) and not isinstance(a["value"], bool) else a["value"])
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

def decide_or_error(rs, raw):
    """A decision, an ERROR decision, or a CASE_INVALID object: what the API returns for one case."""
    try:
        return evaluate(rs, raw)
    except CaseInvalid as e:
        return {"status": "CASE_INVALID", "problems": e.problems}

def canonical(d):
    return json.dumps(d, sort_keys=True, ensure_ascii=False, separators=(",", ":"))

# ----------------------------------------------------------------------------- fixture runners
def load_ref(x):
    """A fixture value that is either inline JSON or a path relative to the fixtures root."""
    return json.load(open(ROOT / x, encoding="utf-8")) if isinstance(x, str) else x

def load_paragraphs(x):
    if x is None: return []
    if isinstance(x, list): return x
    return [p.strip() for p in open(ROOT / x, encoding="utf-8").read().split("\n\n") if p.strip()]

def subset(expected, actual, path="$"):
    """Every key of expected exists in actual with an equal (recursively subset) value; lists match element-wise."""
    if isinstance(expected, dict):
        if not isinstance(actual, dict): return f"{path}: expected object"
        for k, v in expected.items():
            if k not in actual: return f"{path}.{k}: missing"
            r = subset(v, actual[k], f"{path}.{k}")
            if r: return r
        return None
    if isinstance(expected, list):
        if not isinstance(actual, list) or len(actual) != len(expected): return f"{path}: expected {len(expected)} items, got {len(actual) if isinstance(actual, list) else type(actual).__name__}"
        for i, (e, a) in enumerate(zip(expected, actual)):
            r = subset(e, a, f"{path}[{i}]")
            if r: return r
        return None
    if isinstance(expected, float) or isinstance(actual, float):
        try: return None if abs(float(expected) - float(actual)) < 1e-9 else f"{path}: expected {expected!r}, got {actual!r}"
        except (TypeError, ValueError): return f"{path}: expected {expected!r}, got {actual!r}"
    return None if expected == actual else f"{path}: expected {expected!r}, got {actual!r}"

def run_conformance(verbose=True):
    files = sorted(CONFORMANCE_DIR.glob("C-*.json"))
    assert files, f"no conformance fixtures in {CONFORMANCE_DIR}"
    for fp in files:
        fx = json.load(open(fp, encoding="utf-8"))
        rs = load_ref(fx["ruleset"])
        errors = [f for f in validate(rs, load_paragraphs(fx.get("policyText")), fx.get("context", "ANALYST_EDIT")) if f["severity"] == "error"]
        assert not errors, (fx["id"], errors)
        checks = fx.get("checks") or [fx]
        for n, chk in enumerate(checks):
            where = f"{fx['id']} check {n}"
            if "cases" in chk:
                cases = load_ref(chk["cases"])["cases"]
                first = [canonical(decide_or_error(rs, c["input"])) for c in cases]
                second = [canonical(decide_or_error(rs, c["input"])) for c in cases]
                assert first == second, where + ": not byte-identical"
                assert len(cases) == chk["expected"].get("count", len(cases)), where
                continue
            if "overrides" in chk:
                before = canonical(evaluate(rs, chk["case"]))
                actual = simulate(rs, chk["case"], chk["overrides"])
                assert canonical(evaluate(rs, chk["case"])) == before, where + ": simulation changed the original"
            else:
                actual = decide_or_error(rs, chk["case"])
            problem = subset(chk["expected"], actual)
            assert problem is None, f"{where}: {problem}\nactual: {json.dumps(actual, ensure_ascii=False)[:1500]}"
            for rid, status in chk.get("traceStatus", {}).items():   # status of named steps, when the whole trace is too long to list
                step = next((s for s in actual.get("trace", []) if s["ruleId"] == rid), None)
                assert step and step["status"] == status, f"{where}: {rid} expected {status}, got {step and step['status']}"
        if verbose: print(f"  {fx['id']:5s} {fx['name']:42s} OK ({len(checks)} check{'s' if len(checks) != 1 else ''})")
    return len(files)

def run_invalid(verbose=True):
    files = sorted(CONFORMANCE_DIR.glob("invalid-*.json"))
    assert files, f"no invalid fixtures in {CONFORMANCE_DIR}"
    codes_seen = set()
    for fp in files:
        fx = json.load(open(fp, encoding="utf-8"))
        rs = load_ref(fx["ruleset"])
        found = validate(rs, load_paragraphs(fx.get("policyText")), fx.get("context", "PUBLISH"), set(fx.get("modelRuleIds", [])))
        code = fx["code"]
        hits = [f for f in found if f["code"] == code]
        assert hits, f"{fp.name}: expected {code}, got {[f['code'] for f in found]}"
        assert all(h["severity"] == SEVERITY[code] for h in hits), fp.name
        layers = {LAYER[f["code"]] for f in found}
        if SEVERITY[code] == "error":
            assert layers == {LAYER[code]}, f"{fp.name}: an error must stop the validator in its own layer, got layers {layers}"
        for extra in fx.get("alsoExpected", []):
            assert any(f["code"] == extra for f in found), f"{fp.name}: expected {extra} too"
        codes_seen.add(code)
        if verbose: print(f"  {code:30s} {SEVERITY[code]:8s} OK")
    missing = sorted(set(LAYER) - codes_seen)
    assert not missing, f"validator codes without a fixture: {missing}"
    return len(files)

# ----------------------------------------------------------------------------- evaluation set (Document 4)
EVAL_DIR = ROOT / "eval"
FINDING_KINDS = {"ambiguity", "conflict", "unsupported", "gap", "duplicate", "injection"}
TOOLS = {"getDecision", "getDecisionStats", "listRules", "getRule", "simulate"}
MARKER = re.compile(r"^\[\[(p:[1-9][0-9]*|r:R-[0-9]{2,4}|d:[1-9][0-9]*|sim:\*)\]\]$")

def apply_mutation(rs, m):
    """A seeded defect planted in the rule set: the draft the reviewer is given during evaluation."""
    out = copy.deepcopy(rs)
    by_id = {r["id"]: r for r in out["rules"]}
    if m["op"] == "set":
        node = by_id[m["ruleId"]]
        for p in m["path"][:-1]: node = node[p]
        assert node[m["path"][-1]] != m["value"], f"mutation of {m['ruleId']} changes nothing"
        node[m["path"][-1]] = m["value"]
    elif m["op"] == "remove":
        assert m["ruleId"] in by_id, m
        out["rules"] = [r for r in out["rules"] if r["id"] != m["ruleId"]]
    elif m["op"] == "duplicate":
        assert m["ruleId"] in by_id and m["asId"] not in by_id, m
        dup = copy.deepcopy(by_id[m["ruleId"]]); dup["id"] = m["asId"]
        out["rules"].append(dup)
    else:
        raise ValueError(m["op"])
    return out

def apply_patches(rs, patches):
    """Document 4 patches: replace keeps the id, add needs a fresh id, remove drops the rule."""
    out = copy.deepcopy(rs)
    by_id = {r["id"]: r for r in out["rules"]}
    for p in patches:
        if p["op"] == "replace":
            assert p["ruleId"] in by_id and p["rule"]["id"] == p["ruleId"], p["ruleId"]
            by_id[p["ruleId"]].clear(); by_id[p["ruleId"]].update(p["rule"])
        elif p["op"] == "add":
            assert p["ruleId"] not in by_id and p["rule"]["id"] == p["ruleId"], p["ruleId"]
            out["rules"].append(p["rule"])
        elif p["op"] == "remove":
            assert p["ruleId"] in by_id, p["ruleId"]
            out["rules"] = [r for r in out["rules"] if r["id"] != p["ruleId"]]
        else:
            raise ValueError(p["op"])
    return out

def run_eval(verbose=True):
    """Admission checks for the labeled evaluation set: expected rule sets validate against their policy text,
    the labeled cases are reproduced by the engine, seeded defects are well formed, questions and change
    requests point at things that exist, and patch sets apply and validate."""
    dirs = sorted(p for p in (EVAL_DIR / "policies").iterdir() if p.is_dir())
    assert len(dirs) == 18, f"expected 18 labeled policies, found {len(dirs)}"
    langs, slugs = {"he": 0, "en": 0}, {}
    for pdir in dirs:
        md = list(pdir.glob("policy.*.md")); assert len(md) == 1, pdir
        lang = md[0].name.split(".")[1]; langs[lang] += 1
        paras = load_paragraphs(str(md[0].relative_to(ROOT)))
        rs = json.load(open(pdir / "expected.ruleset.json", encoding="utf-8"))
        assert rs["id"] == pdir.name and rs["language"] == lang, pdir.name
        findings = validate(rs, paras, "PUBLISH")
        errors = [f for f in findings if f["severity"] == "error"]
        assert not errors, (pdir.name, errors)
        cases = json.load(open(pdir / "cases.json", encoding="utf-8"))["cases"]
        assert 10 <= len(cases) <= 30, (pdir.name, len(cases))
        for c in cases:
            d = evaluate(rs, c["input"])
            got = {"outcome": d["outcome"], "decidingRuleId": d["decidingRuleId"], "derived": d["derived"], "flags": [f["code"] for f in d["flags"]]}
            assert d["status"] == "OK" and got == c["expected"], (pdir.name, c["id"], got, c["expected"])
        seeded = json.load(open(pdir / "seeded.findings.json", encoding="utf-8"))["findings"]
        assert seeded, pdir.name
        rule_ids = {r["id"] for r in rs["rules"]}
        for s in seeded:
            assert s["kind"] in FINDING_KINDS and s["planted"] in ("text", "ruleset"), (pdir.name, s["id"])
            assert all(1 <= i <= len(paras) for i in s["paragraphIndexes"]), (pdir.name, s["id"])
            extra = {s["mutation"]["asId"]} if s.get("mutation", {}).get("op") == "duplicate" else set()
            assert set(s["ruleIds"]) <= rule_ids | extra, (pdir.name, s["id"], s["ruleIds"])
            if s["planted"] == "ruleset":
                draft = apply_mutation(rs, s["mutation"])
                assert not [f for f in validate(draft, paras, "PUBLISH") if f["code"] == "DSL_SCHEMA"], (pdir.name, s["id"])
            else:
                assert "mutation" not in s, (pdir.name, s["id"])
        slugs[pdir.name] = (rs, len(paras))
        non_errors = [f["code"] for f in findings]
        if verbose: print(f"  {pdir.name:32s} {lang} {len(rs['rules']):2d} rules {len(paras)} paragraphs {len(cases):2d} cases {len(seeded)} seeded" + (f"  ({', '.join(non_errors)})" if non_errors else ""))
    assert langs == {"he": 12, "en": 6}, langs

    def resolve(entry):
        rs = load_ref(entry["ruleset"]); paras = load_paragraphs(entry["policyText"])
        return rs, paras

    questions = json.load(open(EVAL_DIR / "questions.json", encoding="utf-8"))["questions"]
    assert len(questions) == 30, len(questions)
    refusals = 0
    for qn in questions:
        rs, paras = resolve(qn)
        rule_ids = {r["id"] for r in rs["rules"]}
        assert qn["language"] in ("he", "en") and qn["question"].strip(), qn["id"]
        assert qn.get("expectedTool") in TOOLS | {None}, qn["id"]
        for ch in qn["expectedChunks"]:
            kind, _, ident = ch.partition(":")
            assert (kind == "p" and 1 <= int(ident) <= len(paras)) or (kind == "r" and ident in rule_ids), (qn["id"], ch)
        for mk in qn["expectedMarkers"]:
            assert MARKER.match(mk), (qn["id"], mk)
            inner = mk[2:-2]
            if inner.startswith(("p:", "r:")): assert inner in qn["expectedChunks"], (qn["id"], mk)
            if inner.startswith("d:"): assert qn["expectedTool"] == "getDecision", qn["id"]
            if inner.startswith("sim:"): assert qn["expectedTool"] == "simulate", qn["id"]
        if qn["refusal"]:
            refusals += 1
            assert not qn["expectedChunks"] and not qn["expectedMarkers"] and qn.get("expectedTool") is None, qn["id"]
        else:
            assert qn["expectedChunks"] or qn.get("expectedTool"), qn["id"]
    assert refusals >= 5, refusals

    changes = json.load(open(EVAL_DIR / "changes.json", encoding="utf-8"))["changes"]
    assert len(changes) == 6, len(changes)
    for cr in changes:
        rs, paras = resolve(cr)
        rule_ids = {r["id"] for r in rs["rules"]}
        exp = cr["expected"]
        assert set(exp["candidates"]) <= rule_ids and set(exp["untouched"]) <= rule_ids, cr["id"]
        if not exp["patches"]:
            assert exp.get("notes"), cr["id"]
            continue
        patched = apply_patches(rs, exp["patches"])
        model_ids = {p["ruleId"] for p in exp["patches"] if p["op"] != "remove"}
        for p in exp["patches"]:
            if p["op"] != "remove": assert p["rule"]["provenance"]["kind"] == "pending", (cr["id"], p["ruleId"])
        errors = [f for f in validate(patched, paras, "CHANGE_PROPOSAL", model_ids) if f["severity"] == "error"]
        assert not errors, (cr["id"], errors)
        reg = cr.get("regression")
        if reg:
            inputs = [c["input"] for c in load_ref(reg["cases"])["cases"]]
            flips = sum(1 for i in inputs if decide_or_error(rs, i).get("outcome") != decide_or_error(patched, i).get("outcome"))
            assert flips == reg["flips"], (cr["id"], flips, reg["flips"])
    if verbose: print(f"  {len(questions)} questions ({refusals} refusals), {len(changes)} change requests")
    return len(dirs)

# ----------------------------------------------------------------------------- self-test
if __name__ == "__main__":
    rs = json.load(open(RULESET_PATH, encoding="utf-8"))
    paras = [p.strip() for p in open(POLICY_TEXT_PATH, encoding="utf-8").read().split("\n\n") if p.strip()]
    print("fixtures root:", ROOT)
    print("schema OK;", len(paras), "paragraphs;", len(rs["rules"]), "rules")
    fnd = validate(rs, paras, "PUBLISH")
    for x in fnd: print("  finding:", x)
    assert not [x for x in fnd if x["severity"] == "error"], "published example must have no errors"
    assert not fnd, "published example is expected to be clean of warnings and info too"
    print("example validates: no errors, no warnings, no info")

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
    print("static checks OK")

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
    next(f for f in syn2["fields"] if f["name"] == "term_months").pop("minimum")
    r = evaluate(syn2, dict(base, term_months=0))
    assert r["status"] == "ERROR" and r["errorCode"] == "EVAL_DIV_ZERO" and r["errorRuleId"] == "R-010" and len(r["trace"]) == 1
    print("evaluation errors OK")

    # C-31: simulation for the chat counterfactual
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

    # the committed fixture files: the same suite the Java engine and validator run
    print("conformance suite:")
    n = run_conformance()
    print(f"conformance OK ({n} cases)")
    print("invalid fixtures:")
    m = run_invalid()
    print(f"invalid fixtures OK ({m} files, every validator code covered)")
    print("evaluation set:")
    k = run_eval()
    print(f"evaluation set OK ({k} labeled policies admitted)")
    print("ALL OK")
