import {
  FileText,
  History,
  LayoutDashboard,
  Lightbulb,
  Settings,
  Sparkles,
  Target,
  User,
} from 'lucide-react'

/**
 * The sidebar, as data.
 *
 * Two reasons it is a list rather than markup. The sidebar is rendered twice — once
 * docked, once inside the mobile drawer — and a second copy of the markup is a second
 * place to forget a link. And an item carries a `ready` flag, which is how the shell
 * showed the product's real shape before every destination existed: an unbuilt page
 * rendered as a disabled row with a quiet "Soon" rather than as a link that goes nowhere.
 * A dead link costs a user their trust in the whole nav; a disabled row costs nothing.
 *
 * Every row is `ready` as of Phase 11. The flag stays because it is how the next
 * destination gets added — and because `verify_nav_routes` in `tools/verify_sources.py`
 * reads this file against the route table, so a row marked ready with no route, or a
 * signed-in route with no row, fails the build rather than the user's next click.
 *
 * Grouped because eight flat items is a list you scan; four groups of two is a shape
 * you learn. The group names say what the items are for, not what they are.
 */
export const NAV_SECTIONS = [
  {
    title: null,
    items: [
      { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, ready: true },
      { to: '/analyses/new', label: 'New analysis', icon: Sparkles, ready: true },
    ],
  },
  {
    title: 'Library',
    items: [
      { to: '/resumes', label: 'My resumes', icon: FileText, ready: true },
      { to: '/analyses', label: 'Analysis history', icon: History, ready: true },
    ],
  },
  {
    title: 'Insight',
    items: [
      { to: '/skill-gap', label: 'Skill gap', icon: Target, ready: true },
      { to: '/recommendations', label: 'Recommendations', icon: Lightbulb, ready: true },
    ],
  },
  {
    title: 'Account',
    items: [
      { to: '/profile', label: 'Profile', icon: User, ready: true },
      { to: '/settings', label: 'Settings', icon: Settings, ready: true },
    ],
  },
]
