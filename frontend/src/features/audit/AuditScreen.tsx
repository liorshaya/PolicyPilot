import { useId, useState } from 'react'
import { api, ApiError } from '../../api/client'
import { useAudit, useDiff, usePolicies, useRulesets, useVersion } from '../../api/queries'
import type { AuditEntry, FieldSchema, RuleSetDocument, RulesetSummary } from '../../api/types'
import type { ContentLanguage } from '../../shared/i18n/direction'
import { dateOf } from '../../shared/i18n/time'
import { SplitView } from '../../shared/layout/SplitView'
import { WorkspaceHeader } from '../../shared/layout/WorkspaceHeader'
import { Button } from '../../shared/ui/Button'
import { Section } from '../../shared/ui/Section'
import { EmptyState, ErrorState, LoadingRows } from '../../shared/ui/States'
import { VERSION_LABELS, type VersionStatus } from '../../shared/ui/decisionLabels'
import { saveFile } from '../../shared/ui/saveFile'
import { DiffView } from '../change/DiffView'
import { RulesetSwitcher, VersionPicker } from '../rules/Pickers'
import { AuditEntryView } from './AuditEntryView'
import './AuditScreen.css'

interface AuditScreenProps {
  /** The rule set the workspace is on; its versions narrow the log and are compared. */
  rulesetId?: string | null
  /** Told when the reader switches rule sets, so the choice outlives this screen. */
  onChooseRuleset?: (rulesetId: string) => void
}

/** The select's value for the whole log, beside each version's number. */
const ALL = 'all'

/**
 * The audit log (Brief, demo step 4: "the audit log shows who changed what, when and why"; the spec, section 09, "Audit
 * log"): append-only, newest first, grouped by day, one row per entry the API records. It opens on every entry the
 * sandbox can see (Document 2, GET /audit without a version); a version of the workspace's rule set narrows it, the
 * export follows the same choice, and the compare control puts two versions side by side. Nothing on this screen edits
 * or deletes: the log is append-only in the database, and the screen offers no such affordance.
 */
export function AuditScreen({ rulesetId = null, onChooseRuleset }: AuditScreenProps) {
  const rulesets = useRulesets()
  const policies = usePolicies()
  const list = rulesets.data ?? []
  const chosen = list.find((one) => one.id === rulesetId) ?? list[0]
  // a version the reader narrowed to belongs to its rule set; switching rule sets goes back to the whole log
  const [narrowed, setNarrowed] = useState<{ rulesetId: string; versionNo: number } | null>(null)
  const versionNo =
    narrowed !== null && narrowed.rulesetId === chosen?.id ? narrowed.versionNo : null
  const latest = chosen?.versions[chosen.versions.length - 1]?.versionNo ?? 1
  const version = useVersion(chosen ? { id: chosen.id, versionNo: versionNo ?? latest } : null)
  const scope =
    versionNo === null
      ? { versionId: null }
      : version.data
        ? { versionId: version.data.versionId }
        : null
  const audit = useAudit(scope)
  const [exportRefused, setExportRefused] = useState<string | null>(null)
  const document = version.data?.ruleSet as RuleSetDocument | undefined
  const entries = audit.data
  const failure = audit.error ?? (versionNo === null ? null : version.error)
  // content turns with the language of its rule set's policy, an entry's with its own: the whole log spans them all
  const languages = new Map<string, ContentLanguage>()
  for (const ruleset of list) {
    const found = policies.data?.find((policy) => policy.id === ruleset.policyId)?.language
    if (found === 'he' || found === 'en') {
      languages.set(ruleset.id, found)
    }
  }
  const language = (chosen && languages.get(chosen.id)) ?? 'en'
  const languageOf = (entry: AuditEntry): ContentLanguage => {
    const rulesetOf = (entry.details as { rulesetId?: string } | null)?.rulesetId
    return (rulesetOf === undefined ? undefined : languages.get(rulesetOf)) ?? language
  }

  async function exportLog() {
    setExportRefused(null)
    try {
      const { blob, name } = await api.exportAudit(scope?.versionId ?? null, 'text/csv')
      saveFile(blob, name ?? 'audit-log.csv')
    } catch (error) {
      setExportRefused(error instanceof ApiError ? error.code : 'NETWORK_ERROR')
    }
  }

  return (
    <>
      <WorkspaceHeader
        title="Audit log"
        provenance={
          chosen
            ? [
                <bdi key="name" dir="auto" className="sans">
                  {chosen.name}
                </bdi>,
                <span key="domain" className="mono">
                  {chosen.domain}
                </span>,
              ]
            : ['Every publication, proposal and decision the sandbox can see']
        }
        controls={
          chosen && list.length > 1 && onChooseRuleset ? (
            <RulesetSwitcher rulesets={list} value={chosen.id} onChange={onChooseRuleset} />
          ) : null
        }
      />
      <SplitView
        sideOpen={false}
        main={
          <Section
            title="Audit log"
            subtitle={
              entries
                ? `${String(entries.length)} ${entries.length === 1 ? 'entry' : 'entries'}`
                : undefined
            }
            actions={
              <>
                <span className="append-only">append-only</span>
                <Button variant="quiet" size="sm" onClick={() => void exportLog()}>
                  Export the log
                </Button>
                {chosen ? (
                  <select
                    className="select select--sm"
                    aria-label="Version"
                    value={versionNo ?? ALL}
                    onChange={(event) =>
                      setNarrowed(
                        event.target.value === ALL
                          ? null
                          : { rulesetId: chosen.id, versionNo: Number(event.target.value) },
                      )
                    }
                  >
                    <option value={ALL}>All versions</option>
                    {[...chosen.versions].reverse().map((one) => (
                      <option key={one.versionNo} value={one.versionNo}>
                        {`${VERSION_LABELS[one.status as VersionStatus]} v${String(one.versionNo)}`}
                      </option>
                    ))}
                  </select>
                ) : null}
              </>
            }
            flush
          >
            {exportRefused ? (
              <p className="audit__refused" role="alert">
                The export was refused (<span className="mono">{exportRefused}</span>).
              </p>
            ) : null}
            {chosen ? (
              <Compare
                key={chosen.id}
                ruleset={chosen}
                language={language}
                fields={document?.fields}
              />
            ) : null}
            {failure ? (
              <div className="audit__state">
                <ErrorState
                  code={failure instanceof ApiError ? failure.code : undefined}
                  description="The audit log could not be read."
                  onRetry={() => void (audit.error ? audit.refetch() : version.refetch())}
                />
              </div>
            ) : entries === undefined || policies.data === undefined ? (
              <LoadingRows label="Loading the audit log" />
            ) : entries.length === 0 ? (
              <EmptyState
                title="Nothing recorded yet"
                description="Every publication, change request and acknowledgement is recorded here, and nothing is ever removed."
              />
            ) : (
              <Timeline entries={entries} languageOf={languageOf} fields={document?.fields} />
            )}
          </Section>
        }
      />
    </>
  )
}

/** The entries newest first, as the API gives them, under the day each was recorded on in the reader's zone. */
function Timeline({
  entries,
  languageOf,
  fields,
}: {
  entries: AuditEntry[]
  languageOf: (entry: AuditEntry) => ContentLanguage
  fields?: FieldSchema[]
}) {
  const days: { day: string; entries: AuditEntry[] }[] = []
  for (const entry of entries) {
    const day = dateOf(entry.at)
    if (days.at(-1)?.day === day) {
      days.at(-1)!.entries.push(entry)
    } else {
      days.push({ day, entries: [entry] })
    }
  }
  return (
    <div className="timeline">
      {days.map(({ day, entries: recorded }) => (
        <Day key={day} day={day} entries={recorded} languageOf={languageOf} fields={fields} />
      ))}
    </div>
  )
}

function Day({
  day,
  entries,
  languageOf,
  fields,
}: {
  day: string
  entries: AuditEntry[]
  languageOf: (entry: AuditEntry) => ContentLanguage
  fields?: FieldSchema[]
}) {
  const id = useId()
  return (
    <section aria-labelledby={id}>
      <h3 className="timeline__day" id={id}>
        {day}
      </h3>
      <ol className="timeline__entries" aria-labelledby={id}>
        {entries.map((entry) => (
          <AuditEntryView
            key={entry.id}
            entry={entry}
            language={languageOf(entry)}
            fields={fields}
          />
        ))}
      </ol>
    </section>
  )
}

/**
 * Two versions of the rule set side by side (Brief FR-20; Document 2, GET .../diff/{b}), the last two to begin with:
 * after an approval, the version it published beside the one before. The diff is read when asked for.
 */
function Compare({
  ruleset,
  language,
  fields,
}: {
  ruleset: RulesetSummary
  language: ContentLanguage
  fields?: FieldSchema[]
}) {
  const fromId = useId()
  const toId = useId()
  const numbers = ruleset.versions.map((version) => version.versionNo)
  const [pair, setPair] = useState({
    from: numbers[numbers.length - 2] ?? 1,
    to: numbers[numbers.length - 1] ?? 1,
  })
  const [asked, setAsked] = useState<{ from: number; to: number } | null>(null)
  const diff = useDiff(
    asked !== null && asked.from !== asked.to
      ? { rulesetId: ruleset.id, from: asked.from, to: asked.to }
      : null,
  )
  if (numbers.length < 2) {
    return (
      <div className="compare">
        <p className="audit__note">
          This rule set has one version: there is nothing to compare yet.
        </p>
      </div>
    )
  }
  return (
    <>
      <div className="compare">
        <div className="field">
          <label className="field__label" htmlFor={fromId}>
            Compare · from
          </label>
          <VersionPicker
            id={fromId}
            bare
            ruleset={ruleset}
            value={pair.from}
            onChange={(from) => setPair({ ...pair, from })}
          />
        </div>
        <div className="field">
          <label className="field__label" htmlFor={toId}>
            to
          </label>
          <VersionPicker
            id={toId}
            bare
            ruleset={ruleset}
            value={pair.to}
            onChange={(to) => setPair({ ...pair, to })}
          />
        </div>
        <Button onClick={() => setAsked(pair)}>Compare two versions</Button>
      </div>
      {asked === null ? null : (
        <div className="audit__compared">
          {asked.from === asked.to ? (
            <p className="audit__note">Choose two different versions.</p>
          ) : diff.error ? (
            <ErrorState
              code={diff.error instanceof ApiError ? diff.error.code : undefined}
              description="The two versions could not be compared."
              onRetry={() => void diff.refetch()}
            />
          ) : diff.data === undefined ? (
            <LoadingRows label="Comparing the two versions" />
          ) : (
            <DiffView
              diff={diff.data}
              language={language}
              fields={fields}
              beforeLabel={`v${String(asked.from)}`}
              afterLabel={`v${String(asked.to)}`}
            />
          )}
        </div>
      )}
    </>
  )
}
