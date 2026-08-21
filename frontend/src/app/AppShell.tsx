import { Landmark, LogOut, Send, Sparkles, WalletCards } from 'lucide-react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from './auth-context'

export function AppShell() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  return (
    <div className="app-frame">
      <aside className="sidebar">
        <NavLink className="brand" to="/app">
          <span className="brand-mark"><Landmark size={22} /></span>
          <span>MiniLedger</span>
        </NavLink>
        <nav aria-label="Điều hướng chính">
          <NavLink end to="/app"><WalletCards size={19} /> Tổng quan</NavLink>
          <NavLink to="/app/transfer"><Send size={19} /> Chuyển tiền</NavLink>
          <NavLink to="/app/faucet"><Sparkles size={19} /> Nhận tiền demo</NavLink>
        </nav>
        <div className="sidebar-foot">
          <div className="user-chip">
            <span>{user?.username.slice(0, 1).toUpperCase()}</span>
            <div><strong>{user?.username}</strong><small>{user?.email}</small></div>
          </div>
          <button className="icon-button" aria-label="Đăng xuất" onClick={async () => {
            await logout()
            navigate('/login')
          }}><LogOut size={19} /></button>
        </div>
      </aside>
      <main className="main-content">
        <div className="demo-banner">MÔ PHỎNG · Tiền trong ứng dụng không có giá trị thực</div>
        <Outlet />
      </main>
    </div>
  )
}
