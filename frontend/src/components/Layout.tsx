import { useEffect, useState } from 'react'
import { NavLink, Outlet } from 'react-router-dom'
import {
  LayoutDashboard,
  ArrowLeftRight,
  Send,
  ShieldCheck,
  ScrollText,
  Rocket,
  Building2,
  Workflow,
  Cloud,
  Lock,
  DownloadCloud,
  Moon,
  Sun,
  LogOut,
} from 'lucide-react'
import { useAuth } from '../lib/auth'
import Copilot from './Copilot'

const NAV = [
  { to: '/', label: 'Dashboard', icon: LayoutDashboard, end: true },
  { to: '/onboarding', label: 'Get started', icon: Rocket },
  { to: '/partners', label: 'Partners', icon: Building2 },
  { to: '/send', label: 'Ad-hoc Send', icon: Send },
  { to: '/transfers', label: 'Transfers', icon: ArrowLeftRight },
  { to: '/workflows', label: 'Workflows', icon: Workflow },
  { to: '/connectors', label: 'Connectors', icon: Cloud },
  { to: '/import', label: 'Migrate / Import', icon: DownloadCloud },
  { to: '/audit', label: 'Audit & Compliance', icon: ScrollText },
  { to: '/security', label: 'Security', icon: Lock },
  { to: '/trust', label: 'Trust Center', icon: ShieldCheck },
]

function useTheme() {
  const [dark, setDark] = useState(() => localStorage.getItem('mft_theme') === 'dark')
  useEffect(() => {
    document.documentElement.classList.toggle('dark', dark)
    localStorage.setItem('mft_theme', dark ? 'dark' : 'light')
  }, [dark])
  return { dark, toggle: () => setDark((d) => !d) }
}

export default function Layout() {
  const { user, logout } = useAuth()
  const { dark, toggle } = useTheme()

  return (
    <div className="flex min-h-screen">
      {/* Sidebar */}
      <aside className="hidden w-64 shrink-0 flex-col bg-brand-header text-white md:flex">
        <div className="flex items-center gap-2 px-6 py-5">
          <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-white/15 text-lg font-bold">
            C
          </div>
          <div className="leading-tight">
            <div className="text-sm font-semibold">CloudFuze</div>
            <div className="text-xs text-white/60">Managed File Transfer</div>
          </div>
        </div>
        <nav className="mt-2 flex-1 space-y-1 px-3">
          {NAV.map(({ to, label, icon: Icon, end }) => (
            <NavLink
              key={to}
              to={to}
              end={end}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                  isActive ? 'bg-white/15 text-white' : 'text-white/70 hover:bg-white/10 hover:text-white'
                }`
              }
            >
              <Icon size={18} />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="px-6 py-4 text-xs text-white/50">v0.3 · demo build</div>
      </aside>

      {/* Main */}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-3 dark:border-slate-800 dark:bg-[#141B2E]">
          <div className="text-sm text-muted">
            Tenant: <span className="font-medium text-ink dark:text-slate-200">{user?.tenantId?.slice(0, 8)}…</span>
          </div>
          <div className="flex items-center gap-3">
            <button className="btn-ghost !p-2" onClick={toggle} title="Toggle theme">
              {dark ? <Sun size={18} /> : <Moon size={18} />}
            </button>
            <div className="text-right">
              <div className="text-sm font-medium">{user?.fullName || user?.email}</div>
              <div className="text-xs text-muted">{user?.role}</div>
            </div>
            <button className="btn-ghost !p-2" onClick={logout} title="Sign out">
              <LogOut size={18} />
            </button>
          </div>
        </header>
        <main className="flex-1 overflow-y-auto p-6">
          <Outlet />
        </main>
      </div>
      <Copilot />
    </div>
  )
}
