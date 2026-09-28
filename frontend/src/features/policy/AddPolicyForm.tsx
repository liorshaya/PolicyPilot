import { useId, useState, type FormEvent, type ReactNode } from 'react'
import { ApiError } from '../../api/client'
import { Button } from '../../shared/ui/Button'
import { Field } from '../../shared/ui/Field'
import { directionOfText } from '../../shared/i18n/direction'
import { policyFailureText } from './failures'
import './AddPolicyForm.css'

interface AddPolicyInput {
  title: string
  language: 'he' | 'en'
  text?: string
  file?: File
}

interface AddPolicyFormProps {
  pending: boolean
  error: unknown
  onSubmit: (input: AddPolicyInput) => void
  onCancel: () => void
  /** What the form opens with; the guided demo panel fills it with the sample policy (Brief FR-23). */
  initial?: { title: string; language: 'he' | 'en'; text: string }
}

/** What the API refused: the sentence, then the code the API sent, in mono; a failure that never reached it has none. */
function refusal(error: unknown): ReactNode {
  if (!(error instanceof ApiError)) {
    return error ? 'The policy could not be saved. Try again.' : null
  }
  return (
    <>
      {policyFailureText(error.code)} <span className="mono">{error.code}</span>
    </>
  )
}

/**
 * Paste a policy, or upload one (Brief FR-1; Document 5, Input Validation; the spec, section 05, "Fields · the
 * product's own"), in the margin of the Policies screen (section 10). The labels stay above the controls, the policy
 * text is in the document serif so the analyst sees what the sheet will show, and the refusals name the limit that was
 * broken, never the text that broke it.
 */
export function AddPolicyForm({ pending, error, onSubmit, onCancel, initial }: AddPolicyFormProps) {
  const titleId = useId()
  const [mode, setMode] = useState<'paste' | 'upload'>('paste')
  const [title, setTitle] = useState(initial?.title ?? '')
  const [language, setLanguage] = useState<'he' | 'en'>(initial?.language ?? 'he')
  const [text, setText] = useState(initial?.text ?? '')
  const [file, setFile] = useState<File | null>(null)
  const message = refusal(error)
  const ready = title.trim() !== '' && (mode === 'paste' ? text.trim() !== '' : file !== null)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (mode === 'paste' && text.trim() !== '' && title.trim() !== '') {
      onSubmit({ title: title.trim(), language, text })
    } else if (mode === 'upload' && file !== null && title.trim() !== '') {
      onSubmit({ title: title.trim(), language, file })
    }
  }

  return (
    <section className="margin__section" aria-labelledby={titleId}>
      <div className="margin__title">
        <span id={titleId}>Add a policy</span>
      </div>
      <div className="segment add-policy__modes" role="group" aria-label="How to add the policy">
        <Button size="sm" aria-pressed={mode === 'paste'} onClick={() => setMode('paste')}>
          Paste text
        </Button>
        <Button size="sm" aria-pressed={mode === 'upload'} onClick={() => setMode('upload')}>
          Upload a file
        </Button>
      </div>
      <form className="add-policy" onSubmit={handleSubmit}>
        <Field
          label="Title"
          htmlFor="policy-title"
          hint="How this document is listed in the workspace."
        >
          <input
            id="policy-title"
            className="input"
            dir={directionOfText(title)}
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            maxLength={200}
          />
        </Field>
        <Field
          label="Language"
          htmlFor="policy-language"
          hint="Sets the direction the text is read in."
        >
          <select
            id="policy-language"
            className="select"
            value={language}
            onChange={(event) => setLanguage(event.target.value as 'he' | 'en')}
          >
            <option value="he">Hebrew</option>
            <option value="en">English</option>
          </select>
        </Field>

        {mode === 'paste' ? (
          <Field
            label="Policy text"
            htmlFor="policy-text"
            hint="Paragraphs are separated by a blank line. Up to 40 KB and 200 paragraphs."
            error={message}
          >
            <textarea
              id="policy-text"
              className="textarea textarea--doc"
              dir={directionOfText(text)}
              value={text}
              onChange={(event) => setText(event.target.value)}
              aria-invalid={message ? true : undefined}
            />
          </Field>
        ) : (
          <Field
            label="Policy file"
            htmlFor="policy-file"
            hint="A .txt, .md or .pdf file up to 2 MB. The file is read in memory and never stored."
            error={message}
          >
            <input
              id="policy-file"
              className="input"
              type="file"
              accept=".txt,.md,.pdf,text/plain,text/markdown,application/pdf"
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
              aria-invalid={message ? true : undefined}
            />
          </Field>
        )}

        <div className="add-policy__actions">
          <Button type="submit" busy={pending} disabled={!ready}>
            Add policy
          </Button>
          <Button variant="quiet" onClick={onCancel}>
            Cancel
          </Button>
        </div>
      </form>
    </section>
  )
}
