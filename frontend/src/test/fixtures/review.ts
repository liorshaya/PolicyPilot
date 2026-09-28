import seeded from '../../../../fixtures/eval/policies/consumer-lending/seeded.findings.json'
import type { FindingKind, Review, ReviewFinding } from '../../api/types'

/**
 * The review of the lending draft with the five planted findings of fixtures/eval/policies/consumer-lending/
 * seeded.findings.json, in their order: SF-1 the undefined "stable income" (paragraph 4), SF-2 the age conflict
 * (paragraphs 1 and 8), SF-3 the income threshold the draft moved (paragraph 4), SF-4 the self-employed seniority
 * clause without a rule (paragraph 3), SF-5 the minimum age stated twice (paragraph 1). The kinds, paragraphs and rules
 * are the committed file's; the rest is what the API adds to each: the claim and what to do in the policy's Hebrew, and
 * whether the finding blocks publishing, which Document 2 (Flow 1) gives to the errors (conflict, unsupported), every
 * gap and every injection, and never to an ambiguity or a duplicate.
 */
const ADDED: Record<
  string,
  Pick<ReviewFinding, 'severity' | 'blocking' | 'message' | 'suggestion' | 'confidence'>
> = {
  'SF-1': {
    severity: 'warning',
    blocking: false,
    message: 'המונח "הכנסה יציבה" אינו מוגדר במדיניות.',
    suggestion: 'להוסיף סימון לבדיקה ידנית',
    confidence: 0.8,
  },
  'SF-2': {
    severity: 'error',
    blocking: true,
    message: 'סעיף 1 מגביל את גיל כל המבקשים ל-70, בעוד סעיף 8 מתיר לגמלאים עד גיל 75.',
    suggestion: 'להחריג גמלאים מ-R-110',
    confidence: 0.9,
  },
  'SF-3': {
    severity: 'error',
    blocking: true,
    message: 'סף ההכנסה בטיוטה הוא 8,500 ש"ח, בעוד המדיניות קובעת 8,000.',
    suggestion: 'להחזיר את הסף ל-8,000',
    confidence: 0.95,
  },
  'SF-4': {
    severity: 'warning',
    blocking: true,
    message: 'עבור עצמאי, הטיוטה אינה בודקת ותק של 24 חודשים.',
    suggestion: 'להוסיף כלל',
    confidence: 0.7,
  },
  'SF-5': {
    severity: 'warning',
    blocking: false,
    message: 'הטיוטה דוחה פעמיים מבקש שגילו מתחת ל-21.',
    suggestion: 'למחוק את אחד הכללים',
    confidence: 0.85,
  },
}

export const seededReview: Review = {
  status: 'DONE',
  promptVersion: 'v1',
  coverage: {},
  findings: seeded.findings.map((planted, position) => ({
    id: `F-${String(position + 1)}`,
    kind: planted.kind as FindingKind,
    ruleIds: planted.ruleIds,
    paragraphIndexes: planted.paragraphIndexes,
    ...ADDED[planted.id]!,
  })),
}
