import { useEffect } from 'react'

/**
 * Names the browser tab after the page that is open.
 *
 * A single-page app keeps whatever title it was given until something changes it, which
 * makes this the kind of bug that only appears in a history list: leave `/dashboard` for
 * the landing page and the tab still says "Dashboard", so the back-button menu, the
 * bookmark and the window switcher all describe a screen the reader is no longer on.
 *
 * Passing `null` is meaningful — it means "this render does not know the page's name yet",
 * as when a title is being loaded — and leaves the previous title alone rather than
 * flashing a placeholder into the tab. The suffix is applied here so no caller can spell
 * the product name differently.
 */
export const TITLE_SUFFIX = 'ResumeIQ'

export default function useDocumentTitle(title) {
  useEffect(() => {
    if (!title) return
    document.title = `${title} · ${TITLE_SUFFIX}`
  }, [title])
}
