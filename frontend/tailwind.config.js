/**
 * Tailwind reads colours as `rgb(var(--token) / <alpha-value>)`, so every colour lives in
 * one place as an RGB triplet in index.css. Consequences worth knowing:
 *   - dark mode is a single `dark` class on <html>, no duplicated palette
 *   - opacity modifiers still work: bg-brand-600/10, border-band-strong/30
 *   - a new component can never invent a colour; it has to add a token
 */
/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        bg: 'rgb(var(--bg) / <alpha-value>)',
        surface: {
          DEFAULT: 'rgb(var(--surface) / <alpha-value>)',
          raised: 'rgb(var(--surface-raised) / <alpha-value>)',
          sunken: 'rgb(var(--surface-sunken) / <alpha-value>)',
        },
        line: {
          DEFAULT: 'rgb(var(--line) / <alpha-value>)',
          strong: 'rgb(var(--line-strong) / <alpha-value>)',
        },
        ink: {
          DEFAULT: 'rgb(var(--ink) / <alpha-value>)',
          muted: 'rgb(var(--ink-muted) / <alpha-value>)',
          subtle: 'rgb(var(--ink-subtle) / <alpha-value>)',
          inverse: 'rgb(var(--ink-inverse) / <alpha-value>)',
        },
        brand: {
          50: 'rgb(var(--brand-50) / <alpha-value>)',
          100: 'rgb(var(--brand-100) / <alpha-value>)',
          200: 'rgb(var(--brand-200) / <alpha-value>)',
          300: 'rgb(var(--brand-300) / <alpha-value>)',
          400: 'rgb(var(--brand-400) / <alpha-value>)',
          500: 'rgb(var(--brand-500) / <alpha-value>)',
          600: 'rgb(var(--brand-600) / <alpha-value>)',
          700: 'rgb(var(--brand-700) / <alpha-value>)',
          800: 'rgb(var(--brand-800) / <alpha-value>)',
          900: 'rgb(var(--brand-900) / <alpha-value>)',
        },
        accent: {
          400: 'rgb(var(--accent-400) / <alpha-value>)',
          500: 'rgb(var(--accent-500) / <alpha-value>)',
          600: 'rgb(var(--accent-600) / <alpha-value>)',
        },
        success: {
          500: 'rgb(var(--success-500) / <alpha-value>)',
          600: 'rgb(var(--success-600) / <alpha-value>)',
        },
        warning: {
          500: 'rgb(var(--warning-500) / <alpha-value>)',
          600: 'rgb(var(--warning-600) / <alpha-value>)',
        },
        danger: {
          500: 'rgb(var(--danger-500) / <alpha-value>)',
          600: 'rgb(var(--danger-600) / <alpha-value>)',
        },
        // Score bands map 1:1 to the thresholds in the spec, so no component ever
        // decides for itself what a "good" score looks like.
        band: {
          critical: 'rgb(var(--band-critical) / <alpha-value>)',
          low: 'rgb(var(--band-low) / <alpha-value>)',
          moderate: 'rgb(var(--band-moderate) / <alpha-value>)',
          strong: 'rgb(var(--band-strong) / <alpha-value>)',
          excellent: 'rgb(var(--band-excellent) / <alpha-value>)',
        },
      },
      fontFamily: {
        // Bricolage Grotesque is a grotesque, so the fallbacks are grotesques too.
        // A serif fallback would change the page's character on a slow font load.
        display: ['"Bricolage Grotesque"', 'Inter', 'system-ui', 'sans-serif'],
        sans: ['"Instrument Sans"', 'system-ui', '-apple-system', 'Segoe UI', 'sans-serif'],
        mono: ['"JetBrains Mono"', 'ui-monospace', 'SFMono-Regular', 'Menlo', 'monospace'],
      },
      fontSize: {
        'display-xl': ['clamp(2.75rem, 6vw, 4.5rem)', { lineHeight: '0.98', letterSpacing: '-0.03em' }],
        'display-lg': ['clamp(2.25rem, 4.5vw, 3.25rem)', { lineHeight: '1.02', letterSpacing: '-0.025em' }],
        'display-md': ['clamp(1.75rem, 3vw, 2.25rem)', { lineHeight: '1.1', letterSpacing: '-0.02em' }],
        eyebrow: ['0.6875rem', { lineHeight: '1', letterSpacing: '0.14em' }],
        metric: ['clamp(2.5rem, 5vw, 3.5rem)', { lineHeight: '1', letterSpacing: '-0.03em' }],
      },
      borderRadius: {
        card: '14px',
        panel: '20px',
      },
      boxShadow: {
        card: '0 1px 2px rgb(var(--shadow-color) / 0.04), 0 8px 24px -12px rgb(var(--shadow-color) / 0.12)',
        raised: '0 2px 4px rgb(var(--shadow-color) / 0.05), 0 16px 40px -16px rgb(var(--shadow-color) / 0.18)',
        inset: 'inset 0 1px 0 0 rgb(255 255 255 / 0.04)',
      },
      screens: {
        xs: '420px',
      },
      keyframes: {
        'fade-up': {
          from: { opacity: '0', transform: 'translateY(8px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        'rail-draw': {
          from: { strokeDashoffset: '100' },
          to: { strokeDashoffset: '0' },
        },
        // Without an explicit start the sweep begins mid-element, so the first
        // cycle of a loading skeleton looks like a glitch rather than a sweep.
        shimmer: {
          from: { transform: 'translateX(-100%)' },
          to: { transform: 'translateX(100%)' },
        },
      },
      animation: {
        'fade-up': 'fade-up 0.4s cubic-bezier(0.22, 1, 0.36, 1) both',
        'rail-draw': 'rail-draw 0.9s cubic-bezier(0.22, 1, 0.36, 1) both',
        shimmer: 'shimmer 1.6s infinite',
      },
      transitionTimingFunction: {
        'out-expo': 'cubic-bezier(0.22, 1, 0.36, 1)',
      },
    },
  },
  plugins: [],
}
