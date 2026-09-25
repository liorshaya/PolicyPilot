/**
 * Demo step 1 as the tests serve it (Document 2, POST /policies/{id}/rulesets): the four stages of the generation
 * stream, the draft it ends with and the review of that draft, or a refusal in their place. The step 1 spec and the
 * presenter's run of all four steps both answer the generation with these.
 */

export const STAGES =
  'event:parsing\ndata:{"paragraphs":9}\n\n' +
  'event:authoring\ndata:{"paragraphs":9}\n\n' +
  'event:validating\ndata:{"paragraphs":9}\n\n' +
  'event:reviewing\ndata:{"paragraphs":9}\n\n'

export const DRAFT = {
  rulesetId: '0f4c1c9e-0000-4000-8000-0000000000b9',
  name: 'מדיניות אשראי צרכני',
  domain: 'consumer-lending',
  protected: false,
  versionId: '0f4c1c9e-0000-4000-8000-0000000000c9',
  versionNo: 1,
  status: 'DRAFT',
  findings: [],
}

/**
 * The review of step 1: SF-1 and SF-2 of fixtures/eval/policies/consumer-lending/seeded.findings.json, the undefined
 * "stable income" and the age conflict of paragraphs 1 and 8 (Document 1, demo step 1: "two rows carry warnings").
 */
export const REVIEW = {
  status: 'DONE',
  promptVersion: 'v1',
  coverage: {},
  findings: [
    {
      id: 'F-1',
      kind: 'ambiguity',
      severity: 'warning',
      ruleIds: ['R-420'],
      paragraphIndexes: [4],
      message: 'הכנסה יציבה אינה מוגדרת',
      suggestion: 'להוסיף סימון לבדיקה ידנית',
      confidence: 0.8,
      blocking: false,
    },
    {
      id: 'F-2',
      kind: 'conflict',
      severity: 'error',
      ruleIds: ['R-110', 'R-115'],
      paragraphIndexes: [1, 8],
      message: 'סעיף 1 מגביל את הגיל ל-70 וסעיף 8 מתיר גמלאים עד 75',
      suggestion: 'להחריג גמלאים מ-R-110',
      confidence: 0.9,
      blocking: true,
    },
  ],
}

export const REFUSAL =
  'event:error\ndata:{"code":"RULESET_INVALID","findings":[{"code":"PROVENANCE_QUOTE_MISMATCH",' +
  '"severity":"error","path":"/rules/0/provenance/quote","message":"R-100: the quote does not occur in ' +
  'paragraph 1","ruleIds":["R-100"],"fieldNames":[]}]}\n\n'
