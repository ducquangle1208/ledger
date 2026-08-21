import { Navigate, Outlet, Route, Routes } from 'react-router-dom'
import { AppShell } from './app/AppShell'
import { useAuth } from './app/auth-context'
import { AuthPage } from './pages/AuthPage'
import { DashboardPage } from './pages/DashboardPage'
import { FaucetPage } from './pages/FaucetPage'
import { HistoryPage } from './pages/HistoryPage'
import { LandingPage } from './pages/LandingPage'
import { TransactionPage } from './pages/TransactionPage'
import { TransferPage } from './pages/TransferPage'

function ProtectedRoute() {
  const { user, loading } = useAuth()
  if (loading) return <div className="app-loading">Đang mở MiniLedger…</div>
  return user ? <Outlet /> : <Navigate to="/login" replace />
}

export default function App() {
  return <Routes>
    <Route path="/" element={<LandingPage />} />
    <Route path="/login" element={<AuthPage mode="login" />} />
    <Route path="/register" element={<AuthPage mode="register" />} />
    <Route element={<ProtectedRoute />}>
      <Route path="/app" element={<AppShell />}>
        <Route index element={<DashboardPage />} />
        <Route path="transfer" element={<TransferPage />} />
        <Route path="faucet" element={<FaucetPage />} />
        <Route path="accounts/:accountId/history" element={<HistoryPage />} />
        <Route path="transactions/:transactionId" element={<TransactionPage />} />
      </Route>
    </Route>
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>
}
