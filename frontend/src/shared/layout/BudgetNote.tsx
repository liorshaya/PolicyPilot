import { useBudget } from '../../api/queries'
import { timeOf } from '../i18n/time'
import { Note } from '../ui/Note'
import './BudgetNote.css'

/**
 * The one note that is app-wide (the spec, section 08): when the day's model budget is spent (Document 5's degradation
 * order; Document 2, GET /system/budget), it stands under the header of every screen that calls a model and says what
 * keeps working and until when. Nothing is said while the budget lasts.
 */
export function BudgetNote() {
  const budget = useBudget()
  if (!budget.data?.spent) {
    return null
  }
  return (
    <div className="budget-note">
      <Note tone="warning" label="Budget">
        {`Today's model budget is spent; answers, explanations and drafts come from the cache until ${timeOf(budget.data.resumesAt)}. Decisions, the table and the audit log are unaffected.`}
      </Note>
    </div>
  )
}
