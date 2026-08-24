import { Navigate, Route, Routes } from 'react-router-dom'
import RequireAuth from './features/auth/RequireAuth.jsx'
import Dashboard from './pages/Dashboard.jsx'
import Login from './pages/Login.jsx'
import SignUp from './pages/SignUp.jsx'
import SystemCheck from './pages/SystemCheck.jsx'

/**
 * Route table.
 *
 * The guarded routes are nested inside one `RequireAuth` layout route rather than
 * each page checking for itself. That is not only less typing: a page cannot forget,
 * and the next phase's routes inherit the guard by being written in the right block.
 * The API still authorises every request independently — this decides what to render,
 * never what is allowed.
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
        <Route path="/dashboard" element={<Dashboard />} />
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
