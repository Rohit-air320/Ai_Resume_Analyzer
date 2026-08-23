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
