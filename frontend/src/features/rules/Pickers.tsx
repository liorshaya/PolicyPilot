import type { ReactNode } from 'react'
import type { RulesetSummary } from '../../api/types'
import { isolate } from '../../shared/i18n/direction'
import { VERSION_LABELS, type VersionStatus } from '../../shared/ui/decisionLabels'
import './Pickers.css'

/**
 * What a screen shows: a rule set of the sandbox, and one of its versions (Work Plan day 14: the version picker). The
 * rules screen, the audit log and the comparison of two versions choose with these, so a version reads the same
 * everywhere. Bare, a picker is the select alone, named for a screen reader, as a section's title row holds it (the
 * spec, section 10); otherwise its label stands beside it.
 */

/**
 * A rule set as its option reads: the name, isolated because it is often Hebrew inside an English label, the domain,
 * and whether it is the seeded one or the sandbox's own copy of it, which have the same name and domain (Document 3,
 * Version lineage: an approval on the seeded rule set publishes into the sandbox's copy).
 */
function rulesetLabel(ruleset: RulesetSummary): string {
  const whose = ruleset.protected
    ? ' · seeded'
    : ruleset.forkedFromId !== undefined
      ? ' · your copy'
      : ''
  return `${isolate(ruleset.name)} · ${ruleset.domain}${whose}`
}

interface PickerProps {
  label: string
  bare: boolean
  /** The select's id, for a label of its own elsewhere, which then names it instead of `label`. */
  id?: string
  className?: string
  value: string | number
  onChange: (value: string) => void
  children: ReactNode
}

function Picker({ label, bare, id, className, value, onChange, children }: PickerProps) {
  const select = (
    <select
      id={id}
      className={['select', 'select--sm', className].filter(Boolean).join(' ')}
      aria-label={bare && id === undefined ? label : undefined}
      value={value}
      onChange={(event) => onChange(event.target.value)}
    >
      {children}
    </select>
  )
  return bare ? (
    select
  ) : (
    <label className="picker">
      <span className="picker__label">{label}</span>
      {select}
    </label>
  )
}

interface RulesetSwitcherProps {
  rulesets: RulesetSummary[]
  value: string
  onChange: (rulesetId: string) => void
  /** The select alone, named for a screen reader. */
  bare?: boolean
  className?: string
}

export function RulesetSwitcher({
  rulesets,
  value,
  onChange,
  bare = false,
  className,
}: RulesetSwitcherProps) {
  return (
    <Picker label="Rule set" bare={bare} className={className} value={value} onChange={onChange}>
      {rulesets.map((ruleset) => (
        <option key={ruleset.id} value={ruleset.id}>
          {rulesetLabel(ruleset)}
        </option>
      ))}
    </Picker>
  )
}

interface VersionPickerProps {
  ruleset: RulesetSummary
  value: number
  onChange: (versionNo: number) => void
  /** What the choice is for, when a screen chooses two: "From" and "To". */
  label?: string
  /** The select alone, named for a screen reader. */
  bare?: boolean
  /** The select's id, for a field's label of its own, as the audit log's compare control has it. */
  id?: string
  className?: string
}

/** A version as its option reads, as its tag writes it: "Published v1" (the spec, section 10). */
export function VersionPicker({
  ruleset,
  value,
  onChange,
  label = 'Version',
  bare = false,
  id,
  className,
}: VersionPickerProps) {
  return (
    <Picker
      label={label}
      bare={bare}
      id={id}
      className={className}
      value={value}
      onChange={(next) => onChange(Number(next))}
    >
      {ruleset.versions.map((version) => (
        <option key={version.versionNo} value={version.versionNo}>
          {`${VERSION_LABELS[version.status as VersionStatus]} v${String(version.versionNo)}`}
        </option>
      ))}
    </Picker>
  )
}
