import { describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import AppLayout from '../AppLayout.jsx'
import { NAV_SECTIONS } from '../navItems.js'
import { AuthContext } from '../../../features/auth/authContext.js'
import { ROUTER_FUTURE } from '../../../lib/routerFuture.js'

/**
 * The shell, tested through the keyboard.
 *
 * Everything asserted here is invisible on a mouse and unnoticeable in a screenshot, which
 * is exactly why it needs a test: a skip link nobody tabs to, focus that stays on a nav item
 * after the page behind it has been replaced, a drawer that strands focus on `<body>` when it
 * closes. All three are the kind of regression a later phase introduces by accident and no
 * amount of clicking around finds.
 *
 * The pages are stubs on purpose. This is a test of the frame, so the routes render one
 * heading each — a real page would drag its API client in and the assertions would then be
 * about mocking rather than about focus.
 */

function renderShell({ path = '/dashboard' } = {}) {
  const session = {
    status: 'authenticated',
    user: { email: 'casey@example.test', fullName: 'Casey Rivera' },
    isAuthenticated: true,
    isLoading: false,
    signIn: vi.fn(),
    signUp: vi.fn(),
    signOut: vi.fn(),
    applyUser: vi.fn(),
  }

  render(
    <MemoryRouter initialEntries={[path]} future={ROUTER_FUTURE}>
      <AuthContext.Provider value={session}>
        <Routes>
          <Route element={<AppLayout />}>
            <Route path="/dashboard" element={<h1>Dashboard</h1>} />
            <Route path="/resumes" element={<h1>My resumes</h1>} />
          </Route>
        </Routes>
      </AuthContext.Provider>
    </MemoryRouter>,
  )
}

const main = () => document.querySelector('main')
const drawer = () => screen.queryByRole('dialog', { name: 'Navigation' })

describe('AppLayout', () => {
  it('puts a skip link to the main region first in the tab order', async () => {
    const user = userEvent.setup()
    renderShell()

    const skip = screen.getByRole('link', { name: 'Skip to content' })

    expect(skip).toHaveAttribute('href', '#main')
    expect(main()).toHaveAttribute('id', 'main')

    // First in the document, therefore first in the tab order — the property that makes it
    // useful. A skip link placed after the navigation is decoration.
    await user.tab()
    expect(skip).toHaveFocus()
  })

  it('moves focus into the drawer, and hands it back to the button that opened it', async () => {
    const user = userEvent.setup()
    renderShell()

    const trigger = screen.getByRole('button', { name: 'Open navigation' })

    await user.click(trigger)

    const panel = drawer()

    expect(panel).toBeInTheDocument()
    expect(panel).toContainElement(document.activeElement)
    expect(document.body.style.overflow).toBe('hidden')

    await user.keyboard('{Escape}')

    expect(drawer()).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
    expect(document.body.style.overflow).toBe('')
  })

  it('keeps Tab inside the open drawer', async () => {
    const user = userEvent.setup()
    renderShell()

    await user.click(screen.getByRole('button', { name: 'Open navigation' }))

    const panel = within(drawer())
    const close = panel.getByRole('button', { name: 'Close navigation' })
    const links = panel.getAllByRole('link')

    links[links.length - 1].focus()
    await user.tab()

    // Without the wrap, this Tab would land on the browser's own chrome and the next one
    // would be somewhere behind the overlay.
    expect(close).toHaveFocus()

    await user.tab({ shift: true })
    expect(links[links.length - 1]).toHaveFocus()
  })

  it('does not leave focus on the navigation after a route change', async () => {
    const user = userEvent.setup()
    renderShell()

    expect(screen.getByRole('heading', { level: 1, name: 'Dashboard' })).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'My resumes' }))

    expect(screen.getByRole('heading', { level: 1, name: 'My resumes' })).toBeInTheDocument()
    expect(main()).toHaveFocus()
  })

  it('links every navigation row, with nothing left marked "Soon"', () => {
    renderShell()

    const nav = within(screen.getByRole('navigation', { name: 'Main' }))

    NAV_SECTIONS.flatMap((section) => section.items).forEach((item) => {
      expect(item.ready).toBe(true)
      expect(nav.getByRole('link', { name: item.label })).toHaveAttribute('href', item.to)
    })

    expect(screen.queryByText('Soon')).not.toBeInTheDocument()
  })

  it('marks the open page as current, and only that one', async () => {
    const user = userEvent.setup()
    renderShell({ path: '/resumes' })

    const nav = within(screen.getByRole('navigation', { name: 'Main' }))
    const current = () =>
      nav
        .getAllByRole('link')
        .filter((link) => link.getAttribute('aria-current') === 'page')
        .map((link) => link.textContent.trim())

    expect(current()).toEqual(['My resumes'])

    await user.click(nav.getByRole('link', { name: 'Dashboard' }))

    expect(current()).toEqual(['Dashboard'])
  })
})
