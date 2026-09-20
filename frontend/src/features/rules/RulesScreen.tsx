import { useState } from 'react'
import { ApiError } from '../../api/client'
import { usePolicy, usePublish, useReplaceRules, useRulesets, useVersion } from '../../api/queries'
import type { RuleSetDocument, VersionResponse } from '../../api/types'
import type { ContentLanguage } from '../../shared/i18n/direction'
import { SplitView } from '../../shared/layout/SplitView'
import { WorkspaceHeader } from '../../shared/layout/WorkspaceHeader'
import { Button } from '../../shared/ui/Button'
import { Panel } from '../../shared/ui/Panel'
import { EmptyState, ErrorState, LoadingRows } from '../../shared/ui/States'
import type { VersionStatus } from '../../shared/ui/decisionLabels'
import { VersionTag } from '../../shared/ui/StatusTag'
import { PolicyText } from '../policy/PolicyText'
import { DecisionTable } from './DecisionTable'
import { RuleDrawer } from './RuleDrawer'
import { withLeaf } from './tableModel'
import type { Leaf } from './cellGrammar'
import './RulesScreen.css'

type SidePanel = 'source' | 'rule' | 'json'

/**
 * The rule set screen (Work Plan day 6): the decision table with the cell grammar, the raw JSON view, the 422
 * pointers shown on the cells they name, and publish. A rule, its source paragraph and its findings are shown
 * together, because that pairing is what makes a published version auditable.
 */
export function RulesScreen({ onOpenCases }: { onOpenCases: () => void }) {
  const rulesets = useRulesets()
  const first = rulesets.data?.[0]
  const ruleset = first
    ? { id: first.id, versionNo: first.versions[first.versions.length - 1]?.versionNo ?? 1 }
    : null
  const version = useVersion(ruleset)
  const replaceRules = useReplaceRules(ruleset ?? { id: '', versionNo: 1 })
  const publish = usePublish(
    replaceRules.data
      ? { id: replaceRules.data.rulesetId, versionNo: replaceRules.data.versionNo }
      : (ruleset ?? { id: '', versionNo: 1 }),
  )
  const [selectedRuleId, setSelectedRuleId] = useState<string | null>(null)
  const [panel, setPanel] = useState<SidePanel>('source')

  // the version on the screen is the last answer the API gave: an edit or a publish replaces it
  const shown: VersionResponse | undefined = publish.data ?? replaceRules.data ?? version.data
  const document = shown?.ruleSet as RuleSetDocument | undefined
  const language: ContentLanguage = document?.language ?? 'en'
  const policy = usePolicy(first?.policyId ?? null)
  const selectedRule = document?.rules.find((rule) => rule.id === selectedRuleId)
  const provenance = selectedRule?.provenance
  const citedIndex = provenance?.kind === 'quoted' ? provenance.paragraph : null
  const paragraphs = policy.data?.versions?.[0]?.paragraphs ?? []
  const citedParagraph =
    citedIndex === null ? undefined : paragraphs.find((one) => one.index === citedIndex)

  const refusal = replaceRules.error instanceof ApiError ? replaceRules.error : null
  const publishRefusal = publish.error instanceof ApiError ? publish.error : null
  const findings = shown?.findings ?? []
  const draft = shown?.status === 'DRAFT'

  function editCell(ruleId: string, previous: Leaf, next: Leaf) {
    if (!document) {
      return
    }
    replaceRules.mutate(withLeaf(document, ruleId, previous, next))
  }

  return (
    <>
      <WorkspaceHeader
        title="Rules"
        context={
          shown ? (
            <>
              <bdi dir="auto">{shown.name}</bdi> · <span className="mono">{shown.domain}</span>
            </>
          ) : (
            'The decision table of the rule set, and where every rule comes from'
          )
        }
        version={
          shown ? (
            <>
              <span className="tabular">Version {shown.versionNo}</span>
              <VersionTag status={shown.status as VersionStatus} />
              {shown.protected ? <span className="rules__seeded">Seeded, read-only</span> : null}
            </>
          ) : null
        }
        actions={
          shown ? (
            <>
              <Button onClick={onOpenCases}>Run cases</Button>
              <Button
                variant="primary"
                loading={publish.isPending}
                disabled={!draft || findings.some((finding) => finding.severity === 'error')}
                title={draft ? undefined : 'Only a draft is published'}
                onClick={() => publish.mutate()}
              >
                Publish version
              </Button>
            </>
          ) : null
        }
      />
      <SplitView
        sideOpen
        main={
          <>
            {refusal ? <RefusedEdit error={refusal} /> : null}
            {publishRefusal ? <RefusedEdit error={publishRefusal} /> : null}
            <Panel
              fill
              title="Decision table"
              subtitle={
                document
                  ? `${document.rules.length} rules in evaluation order · ${draft ? 'edit a cell to change a rule' : 'published versions are read-only'}`
                  : 'The rules of this version'
              }
              actions={
                <div
                  className="rules__tabs"
                  role="group"
                  aria-label="What to show beside the table"
                >
                  {(['source', 'rule', 'json'] as const).map((id) => (
                    <Button
                      key={id}
                      variant={panel === id ? 'primary' : 'secondary'}
                      aria-pressed={panel === id}
                      onClick={() => setPanel(id)}
                    >
                      {id === 'source' ? 'Policy' : id === 'rule' ? 'Rule' : 'JSON'}
                    </Button>
                  ))}
                </div>
              }
              flush
            >
              {ruleset !== null && version.isPending ? (
                <LoadingRows rows={8} label="Loading the rule set" />
              ) : null}
              {version.error ? (
                <ErrorState
                  code={version.error instanceof ApiError ? version.error.code : undefined}
                  description="The rule set could not be read."
                  onRetry={() => void version.refetch()}
                />
              ) : null}
              {document ? (
                <DecisionTable
                  document={document}
                  selectedRuleId={selectedRuleId}
                  onSelect={(ruleId) => {
                    setSelectedRuleId(ruleId)
                    if (panel === 'json') {
                      setPanel('rule')
                    }
                  }}
                  onEditCell={draft ? editCell : undefined}
                  problems={refusal?.details}
                />
              ) : null}
              {!document && !(ruleset !== null && version.isPending) && !version.error ? (
                <EmptyState
                  title="No rule set yet"
                  description="The seeded lending rule set is loaded with the demo data; a rule set of your own is generated from a policy on day 7."
                />
              ) : null}
            </Panel>
          </>
        }
        side={
          panel === 'json' ? (
            <Panel
              title="Rule set JSON"
              subtitle="The document the engine runs, exactly as it is stored"
              flush
            >
              <pre className="rules__json mono">{JSON.stringify(document ?? {}, null, 2)}</pre>
            </Panel>
          ) : panel === 'rule' && selectedRule ? (
            <RuleDrawer
              rule={selectedRule}
              language={language}
              paragraph={citedParagraph}
              findings={findings.filter((finding) => finding.ruleIds.includes(selectedRule.id))}
              onClose={() => setPanel('source')}
            />
          ) : (
            <Panel
              fill
              title="Policy"
              subtitle={
                selectedRule?.provenance.kind === 'quoted'
                  ? `Paragraph ${selectedRule.provenance.paragraph} is the source of ${selectedRule.id}`
                  : 'Choose a rule to see the paragraph it cites'
              }
              flush
            >
              {policy.data ? (
                <PolicyText
                  language={policy.data.language as ContentLanguage}
                  paragraphs={paragraphs}
                  highlighted={citedParagraph?.index ?? null}
                />
              ) : (
                <LoadingRows rows={5} label="Loading the policy" />
              )}
            </Panel>
          )
        }
      />
    </>
  )
}

/** What the API refused, with the pointers it named (Document 2, 422 with the error list). */
function RefusedEdit({ error }: { error: ApiError }) {
  return (
    <div className="rules__refusal" role="alert">
      <p className="rules__refusal-title">
        The change was refused (<span className="mono">{error.code}</span>)
      </p>
      {error.details.length > 0 ? (
        <ul className="rules__refusal-list">
          {error.details.map((detail) => (
            <li key={detail.path}>
              <span className="mono">{detail.path}</span>
              <span>{detail.problem}</span>
            </li>
          ))}
        </ul>
      ) : (
        <p>The version on the screen is unchanged.</p>
      )}
    </div>
  )
}
