import { Moon, Sun } from 'lucide-react'
import { useTheme } from '../../features/theme/themeContext.js'

/**
 * The light/dark switch.
 *
 * `aria-pressed` rather than two buttons or a checkbox: this is one control with an
 * on state, and a toggle button is exactly what the pattern is for. The accessible
 * name says what pressing it will do — "Switch to dark mode" — because a name that
 * describes the current state instead leaves a screen reader user guessing which way
 * the switch goes.
 *
 * The icon shows the destination, not the present, for the same reason.
 */
export default function ThemeToggle() {
  const { theme, toggleTheme } = useTheme()
  const goingDark = theme === 'light'

  return (
    <button
      type="button"
      onClick={toggleTheme}
      aria-pressed={theme === 'dark'}
      aria-label={goingDark ? 'Switch to dark mode' : 'Switch to light mode'}
      className="btn btn-ghost px-2.5 py-2"
    >
      {goingDark
        ? <Moon size={17} aria-hidden="true" />
        : <Sun size={17} aria-hidden="true" />}
    </button>
  )
}
