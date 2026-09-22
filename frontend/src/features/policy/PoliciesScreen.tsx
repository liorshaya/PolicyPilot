import { useState } from 'react'
import { useDemoStep } from '../demo/useDemoStep'
import { ApiError } from '../../api/client'
import { usePolicies, usePolicy, useCreatePolicy, useRulesets } from '../../api/queries'
import type { PolicySummary } from '../../api/types'
import { WorkspaceHeader } from '../../shared/layout/WorkspaceHeader'
import { SplitView } from '../../shared/layout/SplitView'
import { Button } from '../../shared/ui/Button'
import { Panel } from '../../shared/ui/Panel'
import { EmptyState, ErrorState, LoadingRows } from '../../shared/ui/States'
import { PolicyText } from './PolicyText'
import { AddPolicyForm } from './AddPolicyForm'
import { GenerationProgress } from './GenerationProgress'
import { useGeneration } from './useGeneration'
import './PoliciesScreen.css'

/**
 * The policy screen (Work Plan day 6: paste or upload, paragraph list). A policy's paragraphs are numbered, because
 * the number is what a rule cites (Document 3, Provenance), and a Hebrew policy reads right to left inside the
 * left-to-right workspace.
 */
export function PoliciesScreen({
  onOpenRules,
  demoAsked = false,
  onDemoHandled,
}: {
  onOpenRules: (rulesetId: string) => void
  /** Step 1 of the guided demo: open the form on the sample policy, ready to generate (Brief FR-23). */
  demoAsked?: boolean
  onDemoHandled?: () => void
}) {
  const policies = usePolicies()
  const rulesets = useRulesets()
  const [chosenId, setChosenId] = useState<string | null>(null)
  const [adding, setAdding] = useState(false)
  // the form opens empty for a person, and holding the sample policy when the guided panel opened it
  const [fromDemo, setFromDemo] = useState(false)
  const create = useCreatePolicy()

  const generation = useGeneration()
  const list = policies.data ?? []
  // Step 1 of the demo pastes the sample policy. It is the seeded one, so its text is read back through the same
  // API the screen already uses rather than copied into the web app, where it would be a second fixture to keep
  const sample = list.find((one) => one.protected === true)
  // the key stays the seeded policy's whether or not a step is running: switching it to "none" the moment the
  // step is handled would drop the cached text and open the form empty
  const sampleDocument = usePolicy(sample?.id ?? null)
  const sampleVersions = sampleDocument.data?.versions ?? []
  const sampleText = (sampleVersions[sampleVersions.length - 1]?.paragraphs ?? [])
    .map((paragraph) => paragraph.text)
    .join('\n\n')
  useDemoStep(
    demoAsked && sampleText !== '',
    () => {
      setFromDemo(true)
      setAdding(true)
    },
    onDemoHandled,
  )
  // the first policy is open until the reader chooses another, so nothing has to be selected in an effect
  const selectedId = chosenId ?? list[0]?.id ?? null
  const selected = usePolicy(selectedId)
  // the rule set written from this policy, which is what "its rules" means; a policy may not have one yet
  const ownRuleset = (rulesets.data ?? []).find((one) => one.policyId === selectedId)

  return (
    <>
      <WorkspaceHeader
        title="Policies"
        context={
          list.length > 0
            ? `${list.length} ${list.length === 1 ? 'document' : 'documents'} in this sandbox`
            : 'The policy text every rule is cited from'
        }
        actions={
          <Button
            variant={adding ? 'secondary' : 'primary'}
            onClick={() => {
              setFromDemo(false)
              setAdding((open) => !open)
            }}
          >
            {adding ? 'Close' : 'Add policy'}
          </Button>
        }
      />
      <SplitView
        sideOpen
        main={
          <>
            {adding ? (
              <AddPolicyForm
                initial={
                  fromDemo && sample
                    ? { title: sample.title, language: languageOf(sample), text: sampleText }
                    : undefined
                }
                pending={create.isPending}
                error={create.error}
                onCancel={() => setAdding(false)}
                onSubmit={(input) =>
                  create.mutate(input, {
                    onSuccess: (policy) => {
                      setAdding(false)
                      setChosenId(policy.id)
                    },
                  })
                }
              />
            ) : null}
            <Panel
              fill
              title={
                selected.data ? (
                  // a document's own title is content, so it takes the direction of its own first letters
                  <bdi dir="auto">{selected.data.title}</bdi>
                ) : (
                  'Policy text'
                )
              }
              subtitle={
                selected.data
                  ? `Version ${selected.data.versions?.[0]?.versionNo ?? 1} · ${selected.data.versions?.[0]?.paragraphs?.length ?? 0} paragraphs · each one is a source a rule can cite`
                  : 'Choose a policy to read its paragraphs'
              }
              actions={
                selected.data ? (
                  <>
                    <Button
                      disabled={!ownRuleset}
                      title={ownRuleset ? undefined : 'Generate rules for this policy first'}
                      onClick={() => {
                        if (ownRuleset) {
                          onOpenRules(ownRuleset.id)
                        }
                      }}
                    >
                      Open its rules
                    </Button>
                    <Button
                      variant="primary"
                      loading={generation.running}
                      disabled={generation.running}
                      onClick={() => {
                        generation.start(selected.data.id)
                      }}
                    >
                      Generate rules
                    </Button>
                  </>
                ) : null
              }
              flush
            >
              <GenerationProgress
                generation={generation}
                onOpenRules={() => {
                  if (generation.draft) {
                    onOpenRules(generation.draft.rulesetId)
                  }
                }}
              />
              {selected.isPending && selectedId !== null ? (
                <LoadingRows rows={6} label="Loading the policy" />
              ) : null}
              {selected.error ? (
                <ErrorState
                  code={selected.error instanceof ApiError ? selected.error.code : undefined}
                  description="The policy could not be read. It may belong to another sandbox."
                  onRetry={() => void selected.refetch()}
                />
              ) : null}
              {selected.data ? (
                <PolicyText
                  language={(selected.data.language as 'he' | 'en') ?? 'en'}
                  paragraphs={selected.data.versions?.[0]?.paragraphs ?? []}
                />
              ) : null}
              {!selected.data && !selected.isPending && selectedId === null ? (
                <EmptyState
                  title="No policy open"
                  description="Paste or upload a policy document, or open the seeded lending policy from the list."
                />
              ) : null}
            </Panel>
          </>
        }
        side={
          <Panel
            fill
            title="Documents"
            subtitle="Seeded first, then the ones added in this sandbox"
            flush
          >
            {policies.isPending ? <LoadingRows rows={3} label="Loading the policies" /> : null}
            {policies.error ? (
              <ErrorState
                code={policies.error instanceof ApiError ? policies.error.code : undefined}
                description="The policy list could not be read."
                onRetry={() => void policies.refetch()}
              />
            ) : null}
            {policies.data ? (
              <ul className="policy-list">
                {list.map((policy) => (
                  <PolicyListItem
                    key={policy.id}
                    policy={policy}
                    selected={policy.id === selectedId}
                    onSelect={() => setChosenId(policy.id)}
                  />
                ))}
              </ul>
            ) : null}
          </Panel>
        }
      />
    </>
  )
}

function PolicyListItem({
  policy,
  selected,
  onSelect,
}: {
  policy: PolicySummary
  selected: boolean
  onSelect: () => void
}) {
  return (
    <li>
      <button
        type="button"
        className={`policy-list__item${selected ? ' policy-list__item--selected' : ''}`}
        aria-current={selected ? 'true' : undefined}
        onClick={onSelect}
      >
        <bdi className="policy-list__title" dir="auto">
          {policy.title}
        </bdi>
        <span className="policy-list__meta">
          <span className="tabular">{policy.paragraphs} paragraphs</span>
          <span aria-hidden="true">·</span>
          <span>{policy.language === 'he' ? 'Hebrew' : 'English'}</span>
          {policy.protected ? <span className="policy-list__seeded">Seeded</span> : null}
        </span>
      </button>
    </li>
  )
}

/** A policy's language as the form takes it; the API's is a string, and only these two are policy languages. */
function languageOf(policy: PolicySummary): 'he' | 'en' {
  return policy.language === 'en' ? 'en' : 'he'
}
