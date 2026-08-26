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
 * shows the product's real shape before every destination exists: Skill Gap and
 * Recommendations arrive in Phase 9, Settings in Phase 11, and until then they render
 * as disabled rows with a quiet "Soon" rather than as links that go nowhere. A dead
 * link costs a user their trust in the whole nav; a disabled row costs nothing.
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
      { to: '/skill-gap', label: 'Skill gap', icon: Target, ready: false },
      { to: '/recommendations', label: 'Recommendations', icon: Lightbulb, ready: false },
    ],
  },
  {
    title: 'Account',
    items: [
      { to: '/profile', label: 'Profile', icon: User, ready: true },
      { to: '/settings', label: 'Settings', icon: Settings, ready: false },
    ],
  },
]
