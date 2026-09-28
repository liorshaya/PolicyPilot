/**
 * A change request's name (the spec, sections 09 and 10: "CR-0001" on its chip, in the seal and in the audit log): its
 * number in the sandbox (Document 2, change_request.number, from 1), with four digits at least.
 */
export function changeRequestName(number: number): string {
  return `CR-${String(number).padStart(4, '0')}`
}
