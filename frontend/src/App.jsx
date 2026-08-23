import { Navigate, Route, Routes } from 'react-router-dom'
import SystemCheck from './pages/SystemCheck.jsx'

/**
 * Route table for Phase 1.
 *
 * Only the setup check exists so far, and it is the honest state of the project: the
 * frontend builds, the API answers, the tokens render. The landing page takes over "/"
 * in Phase 10 and this page moves to /system-check as a developer tool.
 */
export default function App() {
  return (
    <Routes>
      <Route path="/" element={<SystemCheck />} />
      <Route path="/system-check" element={<SystemCheck />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
