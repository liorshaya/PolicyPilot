import type { FieldSchema } from '../../api/types'

/**
 * The field hints of `author/v2` (Document 4, Prompt 1: Author, Field hints): the inputs an application supplies, one
 * line each, `- <name> (<type>[, <unit>][: <value>, <value>])`, under the line `The application supplies these
 * inputs:`. Inputs only, never a derived field. Step 1 of the demo sends the seeded rule set's, because the 200 cases
 * exist before the rules and a rule set that names other fields cannot decide them.
 */
export function fieldHints(fields: FieldSchema[]): string {
  const lines = fields
    .filter((field) => field.derived !== true)
    .map((field) => {
      const unit = field.unit === undefined ? '' : `, ${field.unit}`
      const values = field.values === undefined ? '' : `: ${field.values.join(', ')}`
      return `- ${field.name} (${field.type}${unit}${values})`
    })
  return ['The application supplies these inputs:', ...lines].join('\n')
}
