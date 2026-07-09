import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './lib/auth'
import Layout from './components/Layout'
import Login from './pages/Login'
import Dashboard from './pages/Dashboard'
import Transfers from './pages/Transfers'
import Send from './pages/Send'
import Audit from './pages/Audit'
import Trust from './pages/Trust'
import Partners from './pages/Partners'
import Onboarding from './pages/Onboarding'
import Workflows from './pages/Workflows'
import Connectors from './pages/Connectors'
import LegacyImport from './pages/LegacyImport'
import Security from './pages/Security'
import { Spinner } from './components/ui'

function Protected() {
  const { user, loading } = useAuth()
  if (loading) {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <Spinner className="text-brand" />
      </div>
    )
  }
  return user ? <Layout /> : <Navigate to="/login" replace />
}

function LoginRoute() {
  const { user, loading } = useAuth()
  if (loading) return null
  return user ? <Navigate to="/" replace /> : <Login />
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginRoute />} />
          <Route element={<Protected />}>
            <Route path="/" element={<Dashboard />} />
            <Route path="/onboarding" element={<Onboarding />} />
            <Route path="/workflows" element={<Workflows />} />
            <Route path="/transfers" element={<Transfers />} />
            <Route path="/partners" element={<Partners />} />
            <Route path="/connectors" element={<Connectors />} />
            <Route path="/import" element={<LegacyImport />} />
            <Route path="/send" element={<Send />} />
            <Route path="/audit" element={<Audit />} />
            <Route path="/security" element={<Security />} />
            <Route path="/trust" element={<Trust />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
