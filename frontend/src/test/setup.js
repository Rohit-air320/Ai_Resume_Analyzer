import '@testing-library/jest-dom/vitest'

/**
 * jsdom does not implement matchMedia, and the theme bootstrap depends on it.
 * Defaulting to light keeps snapshots and class assertions deterministic.
 */
if (!window.matchMedia) {
  window.matchMedia = (query) => ({
    matches: false,
    media: query,
    onchange: null,
    addEventListener: () => {},
    removeEventListener: () => {},
    addListener: () => {},
    removeListener: () => {},
    dispatchEvent: () => false,
  })
}

/**
 * Recharts' ResponsiveContainer constructs a `ResizeObserver` in an effect, and jsdom does
 * not implement one — without this, mounting any page that contains a chart throws a
 * ReferenceError before a single assertion runs.
 *
 * A no-op stand-in is the right stand-in rather than a shortcut. The observer never fires,
 * so the container keeps its initial zero size and Recharts renders no SVG at all, which
 * mirrors what happens in a browser whose layout has not settled. That is precisely why
 * every chart ships its data as a table through ChartFrame, and why the chart tests assert
 * on that table: the numbers are testable without a layout engine, and so is the page.
 */
if (!globalThis.ResizeObserver) {
  globalThis.ResizeObserver = class ResizeObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
}
