/**
 * Loading placeholders shaped like the thing that is loading.
 *
 * A spinner in the middle of a page says "wait"; a block the size of the card that is
 * coming says "wait, and here is where it will be", which stops the layout jumping when
 * the data lands. The `.skeleton` class carries the shimmer and respects
 * `prefers-reduced-motion` through the global rule in index.css.
 *
 * One `role="status"` wrapper with a text label per group, rather than per block: a
 * screen reader should hear "loading" once, not once per grey rectangle.
 */

export function SkeletonBlock({ className = 'h-4 w-full' }) {
  return <span className={`skeleton block ${className}`} />
}

/** A few stacked card outlines, for lists. */
export function SkeletonList({ rows = 3, label = 'Loading' }) {
  return (
    <div role="status" aria-label={label} className="space-y-3">
      {Array.from({ length: rows }, (unused, index) => (
        <div key={index} className="card space-y-3 p-5">
          <SkeletonBlock className="h-3 w-24" />
          <SkeletonBlock className="h-5 w-2/3" />
          <SkeletonBlock className="h-3 w-1/3" />
        </div>
      ))}
    </div>
  )
}

/** The dashboard's shape: a row of metrics over a wide panel. */
export function SkeletonDashboard() {
  return (
    <div role="status" aria-label="Loading your dashboard" className="space-y-6">
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 4 }, (unused, index) => (
          <div key={index} className="card space-y-4 p-5">
            <SkeletonBlock className="h-3 w-20" />
            <SkeletonBlock className="h-9 w-16" />
          </div>
        ))}
      </div>
      <div className="panel p-6">
        <SkeletonBlock className="h-3 w-32" />
        <SkeletonBlock className="mt-6 h-40 w-full" />
      </div>
    </div>
  )
}
