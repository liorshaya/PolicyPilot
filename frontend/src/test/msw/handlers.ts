import { http, HttpResponse, type RequestHandler } from 'msw'
import type {
  Aggregates,
  BatchResult,
  Decision,
  PoliciesResponse,
  PolicyResponse,
  RulesetsResponse,
  VersionResponse,
} from '../../api/types'
import { lendingParagraphs, lendingRuleSet } from '../fixtures/lending'

/**
 * The API as the component tests see it (Document 6, Frontend Test Design: components render from realistic
 * responses). Every body is typed by the generated client, so a mock cannot drift from the contract, and the
 * content is the committed demo fixture: the Hebrew lending policy and its rule set.
 */

const BASE = 'http://localhost:8080/api/v1'

export const SEEDED_POLICY_ID = '0f4c1c9e-0000-4000-8000-0000000000a1'
export const SEEDED_RULESET_ID = '0f4c1c9e-0000-4000-8000-0000000000b1'
export const SEEDED_VERSION_ID = '0f4c1c9e-0000-4000-8000-0000000000c1'

export const seededPolicy: PolicyResponse = {
  id: SEEDED_POLICY_ID,
  title: 'מדיניות אשראי צרכני',
  language: 'he',
  protected: true,
  createdAt: '2026-09-20T09:00:00Z',
  versions: [
    {
      versionNo: 1,
      createdAt: '2026-09-20T09:00:00Z',
      paragraphs: lendingParagraphs,
    },
  ],
}

export const policies: PoliciesResponse = {
  policies: [
    {
      id: SEEDED_POLICY_ID,
      title: seededPolicy.title,
      language: 'he',
      protected: true,
      versionNo: 1,
      paragraphs: lendingParagraphs.length,
      createdAt: '2026-09-20T09:00:00Z',
    },
  ],
}

export const rulesets: RulesetsResponse = {
  rulesets: [
    {
      id: SEEDED_RULESET_ID,
      name: 'מדיניות אשראי צרכני',
      domain: 'consumer-lending',
      protected: true,
      policyId: SEEDED_POLICY_ID,
      versions: [{ versionNo: 1, status: 'PUBLISHED' }],
    },
  ],
}

export const publishedVersion: VersionResponse = {
  rulesetId: SEEDED_RULESET_ID,
  name: 'מדיניות אשראי צרכני',
  domain: 'consumer-lending',
  protected: true,
  versionId: SEEDED_VERSION_ID,
  versionNo: 1,
  status: 'PUBLISHED',
  policyVersionId: '0f4c1c9e-0000-4000-8000-0000000000d1',
  publishedAt: '2026-09-20T09:00:00Z',
  publishedBy: 'demo-analyst',
  ruleSet: lendingRuleSet,
  findings: [],
}

export const decision: Decision = {
  status: 'OK',
  outcome: 'refer',
  reason: 'נדרש ערב בשל אירוע אשראי אחד ב-24 החודשים האחרונים',
  decidingRuleId: 'R-330',
  terminal: true,
  derived: { monthly_installment: 1493.1, debt_to_income: 0.2835 },
  flags: [],
  candidates: [],
  trace: [
    {
      ruleId: 'R-170',
      label: 'דחייה: הכנסה חודשית נטו נמוכה מ-8,000',
      priority: 170,
      status: 'not_fired',
      comparisons: [
        { field: 'monthly_income', op: 'lt', expected: 8000, actual: 9500, result: false },
      ],
      provenance: {
        kind: 'quoted',
        paragraph: 4,
        quote: 'הכנסה חודשית נטו של 8,000 ש"ח לפחות',
        confidence: 0.95,
      },
    },
    {
      ruleId: 'R-330',
      label: 'בדיקת חתם: אירוע אשראי אחד ללא ערב',
      priority: 330,
      status: 'fired',
      comparisons: [
        { field: 'credit_events_24m', op: 'eq', expected: 1, actual: 1, result: true },
        { field: 'has_guarantor', op: 'eq', expected: false, actual: false, result: true },
      ],
      actions: [{ type: 'decide', outcome: 'refer', terminal: true }],
      provenance: {
        kind: 'quoted',
        paragraph: 7,
        quote: 'מבקש עם אירוע אחד יידרש להעמיד ערב',
        confidence: 0.88,
      },
    },
    { ruleId: 'R-900', label: 'אישור כשכל התנאים מתקיימים', priority: 900, status: 'skipped' },
  ],
  id: '0f4c1c9e-0000-4000-8000-0000000000e1',
  caseNo: 17,
  rulesetVersion: { id: 'consumer-lending', versionNo: 1, versionId: SEEDED_VERSION_ID },
  decidedAt: '2026-09-20T09:05:00Z',
  durationMicros: 412,
}

export const aggregates: Aggregates = {
  outcomes: { approve: 113, reject: 60, refer: 27 },
  errors: 0,
  topDecidingRules: [
    { ruleId: 'R-900', count: 113 },
    { ruleId: 'R-320', count: 11 },
    { ruleId: 'R-330', count: 11 },
    { ruleId: 'R-200', count: 8 },
    { ruleId: 'R-220', count: 8 },
  ],
  decisions: 200,
}

export const batch: BatchResult = {
  aggregates,
  results: [
    {
      id: decision.id,
      caseNo: 17,
      status: 'OK',
      outcome: 'refer',
      decidingRuleId: 'R-330',
      flags: [],
    },
    {
      id: '0f4c1c9e-0000-4000-8000-0000000000e2',
      caseNo: 18,
      status: 'OK',
      outcome: 'approve',
      decidingRuleId: 'R-900',
      flags: ['STABLE_INCOME_MANUAL_CHECK'],
    },
  ],
}

export const handlers: RequestHandler[] = [
  http.get(`${BASE}/policies`, () => HttpResponse.json(policies)),
  http.get(`${BASE}/policies/:id`, () => HttpResponse.json(seededPolicy)),
  http.post(`${BASE}/policies`, () => HttpResponse.json(seededPolicy, { status: 201 })),
  http.get(`${BASE}/rulesets`, () => HttpResponse.json(rulesets)),
  http.get(`${BASE}/rulesets/:id/versions/:no`, () => HttpResponse.json(publishedVersion)),
  http.post(`${BASE}/rulesets/:id/versions/:no/decide`, () => HttpResponse.json(batch)),
  http.get(`${BASE}/rulesets/:id/versions/:no/stats`, () => HttpResponse.json(aggregates)),
  http.get(`${BASE}/decisions/:id`, () => HttpResponse.json(decision)),
]
