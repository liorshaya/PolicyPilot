/**
 * The Register's faces, fetched when the product starts (the spec, section 03, v3.10). A browser fetches a face when
 * the first text set in it is drawn; until it arrives that text stands in a fallback face and is then drawn again in
 * its own, narrower or wider: the assistant's questions, the first Hebrew at weight 400, were redrawn up to 20px
 * narrower as the screen opened. So every face the stylesheets declare is asked for at the start, and is there before
 * a screen needs it.
 */

/** A Latin letter and a Hebrew one, alef: a face is fetched in the subsets that hold the text it is asked to draw. */
const BOTH_SCRIPTS = `A${String.fromCodePoint(0x05d0)}`

/**
 * Asks the browser for each family at each weight its stylesheets declare, in the subsets of the two scripts the
 * product writes. A browser without the font loading API, and a face that cannot be fetched, are left to the face's
 * first use, as before.
 */
export function loadFaces(
  fonts: FontFaceSet | undefined = (document as Partial<Pick<Document, 'fonts'>>).fonts,
): void {
  if (fonts === undefined) {
    return
  }
  const wanted = new Set<string>()
  for (const face of fonts) {
    // a browser gives a family back with or without the quotes it was declared in
    const family = face.family.replace(/^["']|["']$/g, '')
    wanted.add(`${face.style} ${face.weight} 1em "${family}"`)
  }
  for (const font of wanted) {
    fonts.load(font, BOTH_SCRIPTS).catch(() => undefined)
  }
}
