import { Navigate, Route, Routes } from 'react-router-dom'
import AppLayout from './components/layout/AppLayout.jsx'
import RequireAuth from './features/auth/RequireAuth.jsx'
import AnalysisDetail from './pages/AnalysisDetail.jsx'
import Dashboard from './pages/Dashboard.jsx'
import History from './pages/History.jsx'
import Login from './pages/Login.jsx'
import NewAnalysis from './pages/NewAnalysis.jsx'
import Profile from './pages/Profile.jsx'
import Resumes from './pages/Resumes.jsx'
import SignUp from './pages/SignUp.jsx'
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
 * The four sidebar destinations that are not built yet deliberately have no routes. The
 * sidebar renders them disabled from the same list, so the app has no dead links rather
 * than a set of empty pages pretending to be features.
 *
 * "/" is the setup check until Phase 10 puts the landing page there.
 */
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<SystemCheck />} />
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
          <Route path="/profile" element={<Profile />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
