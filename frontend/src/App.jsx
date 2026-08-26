import { Route, Routes } from 'react-router-dom'
import AppLayout from './components/layout/AppLayout.jsx'
import RequireAuth from './features/auth/RequireAuth.jsx'
import AnalysisDetail from './pages/AnalysisDetail.jsx'
import Dashboard from './pages/Dashboard.jsx'
import Demo from './pages/Demo.jsx'
import History from './pages/History.jsx'
import Landing from './pages/Landing.jsx'
import Login from './pages/Login.jsx'
import NewAnalysis from './pages/NewAnalysis.jsx'
import NotFound from './pages/NotFound.jsx'
import Profile from './pages/Profile.jsx'
import Recommendations from './pages/Recommendations.jsx'
import Resumes from './pages/Resumes.jsx'
import SignUp from './pages/SignUp.jsx'
import SkillGap from './pages/SkillGap.jsx'
import SystemCheck from './pages/SystemCheck.jsx'

/**
 * Route table.
 *
 * Two nested layout routes carry everything the signed-in pages have in common.
 * `RequireAuth` decides whether to render at all; `AppLayout` supplies the sidebar, top
 * bar and mobile drawer. Neither concern is repeated in a page, and a new page joins both
 * by being written in the right block — it cannot forget the guard or arrive without the
 * shell. The API still authorises every request independently; this only decides what to
 * render, never what is allowed.
 *
 * `/analyses` is the history list, `/analyses/new` is the wizard and `/analyses/:id` is one
 * result. The static segment ranks above the dynamic one in React Router's matcher, so
 * "new" is a page rather than an id — the order below is for the reader, not the router.
 *
 * `/skill-gap` and `/recommendations` read across analyses rather than into one, which is
 * why they are top-level rather than nested under `/analyses/:id`: neither is a view of a
 * single result, and both are reachable with no analysis selected.
 *
 * Settings is the one sidebar destination without a route. The sidebar renders it disabled
 * from the same list, so the app has no dead link rather than an empty page pretending to
 * be a feature.
 *
 * The four public routes stay public for a signed-in visitor too. `/` and `/demo` explain and
 * demonstrate the product, and redirecting somebody away from them because they have a session
 * makes the explanation unreadable to the only people who can check it, and breaks a shared
 * link. `SiteHeader` swaps its call to action instead.
 *
 * `*` renders `NotFound` rather than redirecting to `/`. A silent redirect rewrites the address
 * bar and leaves the reader unsure whether they mistyped or the link is dead.
 */
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Landing />} />
      <Route path="/demo" element={<Demo />} />
      <Route path="/system-check" element={<SystemCheck />} />
      <Route path="/login" element={<Login />} />
      <Route path="/signup" element={<SignUp />} />

      <Route element={<RequireAuth />}>
        <Route element={<AppLayout />}>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/resumes" element={<Resumes />} />
          <Route path="/analyses" element={<History />} />
          <Route path="/analyses/new" element={<NewAnalysis />} />
          <Route path="/analyses/:id" element={<AnalysisDetail />} />
          <Route path="/skill-gap" element={<SkillGap />} />
          <Route path="/recommendations" element={<Recommendations />} />
          <Route path="/profile" element={<Profile />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFound />} />
    </Routes>
  )
}
