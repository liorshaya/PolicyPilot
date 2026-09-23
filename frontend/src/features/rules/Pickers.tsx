import type { RulesetSummary } from '../../api/types'
import { isolate } from '../../shared/i18n/direction'
import { VERSION_LABELS, type VersionStatus } from '../../shared/ui/decisionLabels'
import './Pickers.css'

/**
 * What a screen shows: a rule set of the sandbox, and one of its versions (Work Plan day 14: the version picker). The
 * rules screen, the audit log and the comparison of two versions choose with these, so a version reads the same
 * everywhere.
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

interface RulesetSwitcherProps {
  rulesets: RulesetSummary[]
  value: string
  onChange: (rulesetId: string) => void
}

export function RulesetSwitcher({ rulesets, value, onChange }: RulesetSwitcherProps) {
  return (
    <label className="picker">
      <span className="picker__label">Rule set</span>
      <select
        className="picker__select"
        value={value}
        onChange={(event) => onChange(event.target.value)}
      >
        {rulesets.map((ruleset) => (
          <option key={ruleset.id} value={ruleset.id}>
            {rulesetLabel(ruleset)}
          </option>
        ))}
      </select>
    </label>
  )
}

interface VersionPickerProps {
  ruleset: RulesetSummary
  value: number
  onChange: (versionNo: number) => void
  /** What the choice is for, when a screen chooses two: "From" and "To". */
  label?: string
}

export function VersionPicker({ ruleset, value, onChange, label = 'Version' }: VersionPickerProps) {
  return (
    <label className="picker">
      <span className="picker__label">{label}</span>
      <select
        className="picker__select"
        value={value}
        onChange={(event) => onChange(Number(event.target.value))}
      >
        {ruleset.versions.map((version) => (
          <option key={version.versionNo} value={version.versionNo}>
            {`Version ${version.versionNo} · ${VERSION_LABELS[version.status as VersionStatus]}`}
          </option>
        ))}
      </select>
    </label>
  )
}
