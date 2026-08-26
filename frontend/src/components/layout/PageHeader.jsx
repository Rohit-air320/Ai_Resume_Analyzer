/**
 * The heading block every page opens with: an eyebrow, a title, a line of context, and
 * room for the page's primary action.
 *
 * One component rather than a repeated pattern, because the alternative is six pages
 * that each drift slightly in type size and spacing — and drift is exactly what makes
 * an interface feel assembled rather than designed. The `h1` lives here too, so no page
 * can accidentally ship without one or ship with two.
 */
export default function PageHeader({ eyebrow, title, lead, children }) {
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
