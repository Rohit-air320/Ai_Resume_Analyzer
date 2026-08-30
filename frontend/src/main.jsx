import React from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.jsx'
import { AuthProvider } from './features/auth/AuthProvider.jsx'
import { ThemeProvider } from './features/theme/ThemeProvider.jsx'
import { ROUTER_FUTURE } from './lib/routerFuture.js'
import './index.css'

/**
 * AuthProvider sits inside BrowserRouter, because the route guard it feeds needs a
 * router to redirect with, and outside App, because every route reads the session.
 *
 * ThemeProvider is outermost: it writes a class on `<html>` rather than rendering
 * anything, so it has no reason to re-run when a route changes, and the toggle in the
 * top bar must keep working on the pages that sit outside the auth guard.
 *
 * The router's future flags come from one module so the app and its tests cannot opt
 * into different behaviour — see `lib/routerFuture.js` for what each one changes.
 */
ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ThemeProvider>
      <BrowserRouter future={ROUTER_FUTURE}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </BrowserRouter>
    </ThemeProvider>
  </React.StrictMode>,
)
