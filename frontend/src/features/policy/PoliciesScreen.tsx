import { useId, useState, type ReactNode } from 'react'
import { useDemoStep } from '../demo/useDemoStep'
import { ApiError } from '../../api/client'
import { usePolicies, usePolicy, useCreatePolicy, useRulesets, useVersion } from '../../api/queries'
import type { PolicySummary, RuleSetDocument, VersionResponse } from '../../api/types'
import { contentAttributes, type ContentLanguage } from '../../shared/i18n/direction'
import { timeOf } from '../../shared/i18n/time'
import { fieldHints } from '../demo/fieldHints'
import { BudgetNote } from '../../shared/layout/BudgetNote'
import { WorkspaceHeader } from '../../shared/layout/WorkspaceHeader'
import { SplitView } from '../../shared/layout/SplitView'
import { useDrawer } from '../../shared/layout/useDrawer'
import { Button } from '../../shared/ui/Button'
import { Chip } from '../../shared/ui/Chip'
import { Section } from '../../shared/ui/Section'
import { Severity } from '../../shared/ui/Severity'
import { EmptyState, ErrorState, LoadingRows } from '../../shared/ui/States'
import { VersionTag } from '../../shared/ui/StatusTag'
import { PolicyText } from './PolicyText'
import { AddPolicyForm } from './AddPolicyForm'
import { GenerationProgress, GenerationResult, ReviewSummary } from './GenerationProgress'
import { useGeneration } from './useGeneration'
import './PoliciesScreen.css'

/**
 * The policy screen (Work Plan day 6; the spec, section 10, "Policies · author"). The policy is the sheet, each
 * paragraph numbered, because the number is what a rule cites (Document 3, Provenance), and under each paragraph the
 * rules that cite it and the findings that name it, so provenance runs both ways; a Hebrew policy reads right to left
 * inside the left-to-right workspace. The margin holds the form that adds a policy, the documents, the generation's
 * stages and the review's summary; below 1200px it is a drawer that opens on Documents, on Add policy and on a run's
 * stages while they run (the spec, section 08, v3.9).
 */
export function PoliciesScreen({
  onOpenRules,
  focusParagraph = null,
  demoAsked = false,
  onDemoHandled,
}: {
  /** Opens a rule set in the Rules screen, on one of its rules when a chip under a paragraph named it. */
  onOpenRules: (rulesetId: string, ruleId?: string) => void
  /** A paragraph the palette opened (the spec, section 08): its policy opens, the paragraph marked and in view. */
  focusParagraph?: { policyId: string; index: number } | null
  /** Step 1 of the guided demo: open the form on the sample policy, ready to generate (Brief FR-23). */
  demoAsked?: boolean
  onDemoHandled?: () => void
}) {
  const policies = usePolicies()
  const rulesets = useRulesets()
  const [chosenId, setChosenId] = useState<string | null>(focusParagraph?.policyId ?? null)
  const [adding, setAdding] = useState(false)
  // the form opens empty for a person, and holding the sample policy when the guided panel opened it
  const [fromDemo, setFromDemo] = useState(false)
  // the policy step 1 pasted: generating it sends the seeded rule set's inputs as the author's field hints
  const [demoPolicyId, setDemoPolicyId] = useState<string | null>(null)
  const create = useCreatePolicy()
  // below 1200px the margin is a drawer, closed until the reader asks for it: what it was opened for, which a run keeps
  // open only while its stages run, since the run's result stands on the sheet (the spec, section 08, v3.9)
  const drawer = useDrawer()
  const [drawerFor, setDrawerFor] = useState<'documents' | 'form' | 'run' | null>(null)

  const generation = useGeneration()
  // seeded first, then the sandbox's own (the spec, section 10: "Documents · seeded first, then this sandbox's")
  const list = seededFirst(policies.data ?? [])
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
  // Document 4, Field hints: the inputs of the seeded rule set, which the 200 cases supply
  const seededRuleset = (rulesets.data ?? []).find(
    (one) => one.protected === true && one.policyId === sample?.id,
  )
  const seededVersion = useVersion(seededRuleset ? { id: seededRuleset.id, versionNo: 1 } : null)
  const seededFields = (seededVersion.data?.ruleSet as RuleSetDocument | undefined)?.fields
  useDemoStep(
    demoAsked && sampleText !== '',
    () => {
      setFromDemo(true)
      setAdding(true)
      setDrawerFor('form')
    },
    onDemoHandled,
  )
  // the first policy is open until the reader chooses another, so nothing has to be selected in an effect
  const selectedId = chosenId ?? list[0]?.id ?? null
  // Generating waits while a new policy is being added, since the button still belongs to the policy that was open
  // (day 15: a quick click generated the seeded policy instead of the pasted copy), and, for the policy step 1 pasted,
  // until the seeded inputs are read, so its generation never goes out without its hints
  const preparing =
    create.isPending ||
    (selectedId !== null && selectedId === demoPolicyId && seededFields === undefined)
  const selected = usePolicy(selectedId)
  const versions = selected.data?.versions ?? []
  const latest = versions[versions.length - 1]
  const paragraphs = latest?.paragraphs ?? []
  const language: ContentLanguage = selected.data?.language === 'en' ? 'en' : 'he'
  // the run on the screen belongs to the policy it was started for
  const run = generation.policyId !== null && generation.policyId === selectedId ? generation : null
  // the rule set written from this policy, which is what "its rules" means: the one the run just wrote, else the
  // sandbox's own, else the seeded one; a policy may not have one yet
  const written = (rulesets.data ?? []).filter((one) => one.policyId === selectedId)
  const citing =
    written.find((one) => one.id === run?.draft?.rulesetId) ??
    written.find((one) => !one.protected) ??
    written[0]
  const citingNo = citing?.versions[citing.versions.length - 1]?.versionNo
  const citingVersion = useVersion(
    citing && citingNo !== undefined ? { id: citing.id, versionNo: citingNo } : null,
  )
  // the version whose rules and findings stand under the paragraphs: the run's draft, else the rule set's latest
  const shown: VersionResponse | undefined = run?.draft ?? citingVersion.data
  const rulesetId = run?.draft?.rulesetId ?? citing?.id
  const sideOpen =
    !drawer ||
    drawerFor === 'documents' ||
    (drawerFor === 'form' && adding) ||
    (drawerFor === 'run' && generation.running)

  // the drawer's opener, at the end of the sheet's title row, which has the focus again when the drawer closes
  const documents = drawer ? (
    <Button
      variant="quiet"
      size="sm"
      aria-expanded={sideOpen}
      onClick={() => setDrawerFor('documents')}
    >
      Documents
    </Button>
  ) : null

  function openForm() {
    setFromDemo(false)
    setAdding(true)
    setDrawerFor('form')
  }

  /** Under a paragraph: the rules that cite it, in the document's order, then the review's findings that name it. */
  function citesOf(index: number): ReactNode {
    const document = shown?.ruleSet as RuleSetDocument | undefined
    const rules = (document?.rules ?? []).filter(
      (rule) => rule.provenance.kind === 'quoted' && rule.provenance.paragraph === index,
    )
    const findings = (shown?.review?.findings ?? []).filter((finding) =>
      finding.paragraphIndexes.includes(index),
    )
    if (rules.length + findings.length === 0) {
      return null
    }
    return (
      <>
        {rules.map((rule) => (
          <Chip
            key={rule.id}
            onClick={() => {
              if (shown) {
                onOpenRules(shown.rulesetId, rule.id)
              }
            }}
          >
            {rule.id}
          </Chip>
        ))}
        {findings.map((finding) => (
          <Severity key={finding.id} kind={finding.kind} code={finding.id} brief />
        ))}
      </>
    )
  }

  return (
    <>
      <WorkspaceHeader
        title="Policies"
        provenance={[
          <span key="documents">
            <b>{list.length}</b> {list.length === 1 ? 'document' : 'documents'}
          </span>,
          ...(selected.data
            ? [
                ...(selected.data.protected
                  ? [<VersionTag key="seeded" status="PUBLISHED" seeded />]
                  : []),
                <span key="paragraphs">
                  <b>{paragraphs.length}</b> {paragraphs.length === 1 ? 'paragraph' : 'paragraphs'}
                </span>,
                language === 'he' ? 'Hebrew' : 'English',
                'every rule cites one of them',
              ]
            : []),
        ]}
        secondary={
          adding ? null : (
            <Button icon="plus" onClick={openForm}>
              Add policy
            </Button>
          )
        }
        reason={
          selected.data && preparing
            ? create.isPending
              ? 'The new policy is still being added'
              : "Reading the seeded rule set's inputs for the hints"
            : undefined
        }
        primary={
          selected.data ? (
            <Button
              variant="primary"
              busy={generation.running}
              disabled={preparing}
              onClick={() => {
                const hints =
                  selected.data.id === demoPolicyId && seededFields !== undefined
                    ? fieldHints(seededFields)
                    : undefined
                generation.start(selected.data.id, hints)
                setDrawerFor('run')
              }}
            >
              Generate rules
            </Button>
          ) : null
        }
      />
      <BudgetNote />
      <SplitView
        sideOpen={sideOpen}
        onCloseSide={() => setDrawerFor(null)}
        closeButton
        fill
        main={
          <Section
            title={
              selected.data ? (
                // a document's own title is content, in its own language and direction
                <bdi className="policies__title" {...contentAttributes(language)}>
                  {selected.data.title}
                </bdi>
              ) : (
                'Policy'
              )
            }
            actions={
              selected.data || documents ? (
                <>
                  {selected.data ? (
                    <>
                      <span className="muted policies__size">
                        {`Version ${String(latest?.versionNo ?? 1)} · ${String(paragraphs.length)} paragraphs`}
                      </span>
                      {rulesetId === undefined ? (
                        <span className="reason">Generate rules for this policy first</span>
                      ) : null}
                      <Button
                        size="sm"
                        disabled={rulesetId === undefined}
                        onClick={() => {
                          if (rulesetId !== undefined) {
                            onOpenRules(rulesetId)
                          }
                        }}
                      >
                        Open its rules
                      </Button>
                    </>
                  ) : null}
                  {documents}
                </>
              ) : null
            }
            flush
          >
            {run ? (
              <GenerationResult
                generation={run}
                onReview={() => {
                  if (run.draft) {
                    onOpenRules(run.draft.rulesetId)
                  }
                }}
              />
            ) : null}
            {selectedId === null ? (
              policies.isPending ? (
                <LoadingRows label="Loading the policy" />
              ) : policies.error ? null : (
                <EmptyState
                  title="No policy open"
                  action={
                    <Button size="sm" onClick={openForm}>
                      Add policy
                    </Button>
                  }
                />
              )
            ) : selected.isPending ? (
              <LoadingRows label="Loading the policy" />
            ) : selected.error ? (
              <ErrorState
                code={selected.error instanceof ApiError ? selected.error.code : undefined}
                description="The policy could not be read. It may belong to another sandbox."
                onRetry={() => void selected.refetch()}
              />
            ) : (
              <div className="sheet__scroll">
                <PolicyText
                  language={language}
                  paragraphs={paragraphs}
                  highlighted={
                    focusParagraph !== null && focusParagraph.policyId === selectedId
                      ? focusParagraph.index
                      : null
                  }
                  sheet
                  cites={citesOf}
                />
              </div>
            )}
          </Section>
        }
        side={
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
                      setDemoPolicyId(fromDemo ? policy.id : null)
                    },
                  })
                }
              />
            ) : null}
            <Documents
              query={policies}
              list={list}
              selectedId={selectedId}
              onSelect={(policyId) => {
                setChosenId(policyId)
                // a document chosen in the drawer is read on the sheet, which the drawer would cover
                setDrawerFor(null)
              }}
            />
            {run ? <GenerationProgress generation={run} /> : null}
            <ReviewSummary
              review={shown?.review}
              language={language}
              onOpen={() => {
                if (rulesetId !== undefined) {
                  onOpenRules(rulesetId)
                }
              }}
            />
          </>
        }
      />
    </>
  )
}

/**
 * The documents the sandbox can see (the spec, section 10, the Policies margin): each with its name in its own
 * language, its size and language, and "Seeded" or when it was added; the open one is the current row.
 */
function Documents({
  query,
  list,
  selectedId,
  onSelect,
}: {
  query: ReturnType<typeof usePolicies>
  list: PolicySummary[]
  selectedId: string | null
  onSelect: (policyId: string) => void
}) {
  const titleId = useId()
  return (
    <section className="margin__section" aria-labelledby={titleId}>
      <div className="margin__title">
        <span id={titleId}>Documents</span>{' '}
        <span className="quiet">seeded first, then this sandbox&apos;s</span>
      </div>
      {query.isPending ? <LoadingRows label="Loading the policies" /> : null}
      {query.error ? (
        <ErrorState
          code={query.error instanceof ApiError ? query.error.code : undefined}
          description="The policy list could not be read."
          onRetry={() => void query.refetch()}
        />
      ) : null}
      {query.data ? (
        <ul className="docs">
          {list.map((policy) => (
            <li key={policy.id}>
              <button
                type="button"
                className="doc-row"
                aria-current={policy.id === selectedId ? 'true' : undefined}
                onClick={() => onSelect(policy.id)}
              >
                <span className="doc-row__name" {...contentAttributes(languageOf(policy))}>
                  {policy.title}
                </span>
                <span className="doc-row__meta">
                  <span>{`${String(policy.paragraphs)} ${policy.paragraphs === 1 ? 'paragraph' : 'paragraphs'}`}</span>
                  <span>{languageOf(policy) === 'he' ? 'Hebrew' : 'English'}</span>
                  {policy.protected ? (
                    <span className="vstatus vstatus--seeded">Seeded</span>
                  ) : (
                    <span>{`added ${timeOf(policy.createdAt)}`}</span>
                  )}
                </span>
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  )
}

/** The seeded documents first, then the sandbox's own, each group in the order the API lists it. */
function seededFirst(policies: PolicySummary[]): PolicySummary[] {
  return [...policies.filter((one) => one.protected), ...policies.filter((one) => !one.protected)]
}

/** A policy's language as the form takes it; the API's is a string, and only these two are policy languages. */
function languageOf(policy: PolicySummary): 'he' | 'en' {
  return policy.language === 'en' ? 'en' : 'he'
}
