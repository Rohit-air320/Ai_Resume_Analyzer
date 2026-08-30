import useDocumentTitle from '../../lib/useDocumentTitle.js'

/**
 * The heading block every page opens with: an eyebrow, a title, a line of context, and
 * room for the page's primary action.
 *
 * One component rather than a repeated pattern, because the alternative is six pages
 * that each drift slightly in type size and spacing — and drift is exactly what makes
 * an interface feel assembled rather than designed. The `h1` lives here too, so no page
 * can accidentally ship without one or ship with two.
 *
 * It also names the browser tab, for the same reason: this component already knows what
 * the page is called, so there is nothing to keep in sync. Every signed-in page had been
 * inheriting the marketing title from `index.html`, which makes a browser history list, a
 * bookmark and a switcher tab read identically for eight different screens. A `documentTitle`
 * override exists for the one case where the heading is a person's data — "Backend Developer
 * at Acme" is a good `h1` and a poor tab label — and it is also the way a page whose title
 * is still loading passes nothing at all instead of flashing "Loading" into the tab.
 */
export default function PageHeader({ eyebrow, title, lead, documentTitle, children }) {
  useDocumentTitle(documentTitle ?? (typeof title === 'string' ? title : null))

  return (
    <header className="flex flex-wrap items-end justify-between gap-4 pb-7">
      <div className="min-w-0">
        {eyebrow ? <p className="eyebrow">{eyebrow}</p> : null}
        <h1 className="mt-2.5 text-display-md">{title}</h1>
        {lead ? <p className="mt-2.5 max-w-2xl text-sm text-ink-muted">{lead}</p> : null}
      </div>

      {children ? <div className="flex shrink-0 items-center gap-2">{children}</div> : null}
    </header>
  )
}
