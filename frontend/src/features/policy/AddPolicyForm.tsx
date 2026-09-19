import { useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import { Button } from '../../shared/ui/Button'
import { Field } from '../../shared/ui/Field'
import { Panel } from '../../shared/ui/Panel'
import { directionOfText } from '../../shared/i18n/direction'
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
}

/** What the API refuses, said in the words of the person who pasted it (Document 2, error codes). */
function refusal(error: unknown): string | null {
  if (!(error instanceof ApiError)) {
    return error ? 'The policy could not be saved. Try again.' : null
  }
  switch (error.code) {
    case 'POLICY_INVALID':
      return 'The text is over its limits (40 KB, 200 paragraphs, 4,000 characters a paragraph) or holds a control character.'
    case 'UPLOAD_REJECTED':
      return 'The file must be a .txt, .md or .pdf under 2 MB, with readable text and no scripts.'
    case 'PAYLOAD_TOO_LARGE':
      return 'The file is larger than the 2 MB the API accepts.'
    case 'RATE_LIMITED':
      return 'Too many requests from this address; wait a moment and try again.'
    default:
      return `The policy was refused (${error.code}).`
  }
}

/**
 * Paste a policy, or upload one (Brief FR-1; Document 5, Input Validation). The labels stay above the controls and
 * the refusals name the limit that was broken, never the text that broke it.
 */
export function AddPolicyForm({ pending, error, onSubmit, onCancel }: AddPolicyFormProps) {
  const [mode, setMode] = useState<'paste' | 'upload'>('paste')
  const [title, setTitle] = useState('')
  const [language, setLanguage] = useState<'he' | 'en'>('he')
  const [text, setText] = useState('')
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
    <Panel
      title="Add a policy"
      subtitle="The text a rule set is written from; every rule will cite one of its paragraphs."
      actions={
        <div className="add-policy__modes" role="group" aria-label="How to add the policy">
          <Button
            variant={mode === 'paste' ? 'primary' : 'secondary'}
            onClick={() => setMode('paste')}
            aria-pressed={mode === 'paste'}
          >
            Paste text
          </Button>
          <Button
            variant={mode === 'upload' ? 'primary' : 'secondary'}
            onClick={() => setMode('upload')}
            aria-pressed={mode === 'upload'}
          >
            Upload a file
          </Button>
        </div>
      }
    >
      <form className="add-policy" onSubmit={handleSubmit}>
        <div className="add-policy__row">
          <Field
            label="Title"
            htmlFor="policy-title"
            hint="How this document is listed in the workspace."
          >
            <input
              id="policy-title"
              className="input"
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
        </div>

        {mode === 'paste' ? (
          <Field
            label="Policy text"
            htmlFor="policy-text"
            hint="Paragraphs are separated by a blank line. Up to 40 KB and 200 paragraphs."
            error={message}
          >
            <textarea
              id="policy-text"
              className="textarea"
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
          <Button type="submit" variant="primary" loading={pending} disabled={!ready}>
            Add policy
          </Button>
          <Button variant="ghost" onClick={onCancel}>
            Cancel
          </Button>
        </div>
      </form>
    </Panel>
  )
}
