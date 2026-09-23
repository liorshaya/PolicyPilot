import { useState } from 'react'
import { ApiError } from '../../api/client'
import { useAudit, useDiff, useRulesets, useVersion } from '../../api/queries'
import type { FieldSchema, RuleSetDocument, RulesetSummary } from '../../api/types'
import type { ContentLanguage } from '../../shared/i18n/direction'
import { SplitView } from '../../shared/layout/SplitView'
import { WorkspaceHeader } from '../../shared/layout/WorkspaceHeader'
import { Panel } from '../../shared/ui/Panel'
import { EmptyState, ErrorState, LoadingRows } from '../../shared/ui/States'
import { DiffView } from '../change/DiffView'
import { RulesetSwitcher, VersionPicker } from '../rules/Pickers'
import { AuditEntryView } from './AuditEntryView'
import './AuditScreen.css'

interface AuditScreenProps {
  /** The rule set the workspace is on; without one the sandbox's first is shown. */
  rulesetId?: string | null
  /** Told when the reader switches rule sets, so the choice outlives this screen. */
  onChooseRuleset?: (rulesetId: string) => void
}

/**
 * The audit log (Document 2, Frontend Architecture: "audit log, version timeline"; GET /audit: a version's entries,
 * newest first). It opens on the latest version of the workspace's rule set, which after an approval is the version
 * the approval published, and any two versions of the rule set can be compared side by side (Brief FR-20).
 */
export function AuditScreen({ rulesetId = null, onChooseRuleset }: AuditScreenProps) {
  const rulesets = useRulesets()
  const list = rulesets.data ?? []
  const chosen = list.find((one) => one.id === rulesetId) ?? list[0]
  const latest = chosen?.versions[chosen.versions.length - 1]?.versionNo ?? 1
  // a version the reader picked belongs to its rule set; switching rule sets goes back to the latest
  const [picked, setPicked] = useState<{ rulesetId: string; versionNo: number } | null>(null)
  const versionNo = picked !== null && picked.rulesetId === chosen?.id ? picked.versionNo : latest
  const version = useVersion(chosen ? { id: chosen.id, versionNo } : null)
  const audit = useAudit(version.data?.versionId ?? null)
  const document = version.data?.ruleSet as RuleSetDocument | undefined
  const language: ContentLanguage = document?.language ?? 'en'

  return (
    <>
      <WorkspaceHeader
        title="Audit log"
        context={
          chosen ? (
            <>
              <bdi dir="auto">{chosen.name}</bdi> · <span className="mono">{chosen.domain}</span>
            </>
          ) : (
            'Every publication, proposal and decision of a version'
          )
        }
        actions={
          chosen ? (
            <>
              {list.length > 1 && onChooseRuleset ? (
                <RulesetSwitcher rulesets={list} value={chosen.id} onChange={onChooseRuleset} />
              ) : null}
              {chosen.versions.length > 1 ? (
                <VersionPicker
                  ruleset={chosen}
                  value={versionNo}
                  onChange={(next) => setPicked({ rulesetId: chosen.id, versionNo: next })}
                />
              ) : null}
            </>
          ) : null
        }
      />
      <SplitView
        sideOpen={false}
        main={
          <>
            <Panel
              title="Entries"
              subtitle={`Version ${versionNo}, newest first: who changed what, when and why`}
            >
              {audit.error || version.error ? (
                <ErrorState
                  code={codeOf(audit.error ?? version.error)}
                  description="The audit log could not be read."
                  onRetry={() => void (version.error ? version.refetch() : audit.refetch())}
                />
              ) : audit.data === undefined ? (
                <LoadingRows rows={4} label="Loading the audit log" />
              ) : audit.data.length === 0 ? (
                <EmptyState
                  title="Nothing recorded"
                  description={`Nothing has been recorded for version ${versionNo} yet.`}
                />
              ) : (
                <ol className="audit__entries" aria-label="Entries">
                  {audit.data.map((entry) => (
                    <li key={entry.id}>
                      <AuditEntryView entry={entry} language={language} fields={document?.fields} />
                    </li>
                  ))}
                </ol>
              )}
            </Panel>
            {chosen ? (
              <Compare
                key={chosen.id}
                ruleset={chosen}
                language={language}
                fields={document?.fields}
              />
            ) : null}
          </>
        }
      />
    </>
  )
}

/**
 * Any two versions of the rule set side by side (Brief FR-20; Document 2, GET .../diff/{b}), the last two to begin
 * with: after an approval, the version it published beside the one it was proposed on.
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
  const numbers = ruleset.versions.map((version) => version.versionNo)
  const [pair, setPair] = useState({
    from: numbers[numbers.length - 2] ?? 1,
    to: numbers[numbers.length - 1] ?? 1,
  })
  const diff = useDiff(
    numbers.length > 1 && pair.from !== pair.to
      ? { rulesetId: ruleset.id, from: pair.from, to: pair.to }
      : null,
  )
  if (numbers.length < 2) {
    return (
      <Panel title="Compare two versions">
        <p className="audit__note">
          This rule set has one version: there is nothing to compare yet.
        </p>
      </Panel>
    )
  }
  return (
    <Panel
      title="Compare two versions"
      subtitle="Rule by rule, as the structural diff of the API gives it"
      actions={
        <>
          <VersionPicker
            ruleset={ruleset}
            label="From"
            value={pair.from}
            onChange={(from) => setPair({ ...pair, from })}
          />
          <VersionPicker
            ruleset={ruleset}
            label="To"
            value={pair.to}
            onChange={(to) => setPair({ ...pair, to })}
          />
        </>
      }
    >
      {pair.from === pair.to ? (
        <p className="audit__note">Choose two different versions.</p>
      ) : diff.error ? (
        <ErrorState
          code={codeOf(diff.error)}
          description="The two versions could not be compared."
          onRetry={() => void diff.refetch()}
        />
      ) : diff.data === undefined ? (
        <LoadingRows rows={3} label="Comparing the two versions" />
      ) : (
        <DiffView
          diff={diff.data}
          language={language}
          fields={fields}
          beforeLabel={`Version ${pair.from}`}
          afterLabel={`Version ${pair.to}`}
        />
      )}
    </Panel>
  )
}

function codeOf(error: unknown): string | undefined {
  return error instanceof ApiError ? error.code : undefined
}
