import { useEffect, useId, useRef, useState } from 'react'
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { ChevronDown, LayoutDashboard, LogOut, Menu, Settings, TrendingDown, X } from 'lucide-react'
import { useAuth } from '../../context/AuthContext'

const NAV_ITEMS = [
  { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
  { to: '/settings', label: 'Settings', icon: Settings },
]

const focusRing = 'focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-500'

function navLinkCls({ isActive }) {
  return `flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium transition-colors ${focusRing} ${
    isActive ? 'bg-primary-50 text-primary-700' : 'text-gray-600 hover:text-gray-900 hover:bg-gray-100'
  }`
}

function UserMenu({ user, onLogout }) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef(null)
  const buttonRef = useRef(null)
  const menuId = useId()

  useEffect(() => {
    if (!open) return undefined
    const onPointerDown = (e) => {
      if (!rootRef.current?.contains(e.target)) setOpen(false)
    }
    const onKeyDown = (e) => {
      if (e.key === 'Escape') {
        setOpen(false)
        buttonRef.current?.focus()
      }
    }
    document.addEventListener('mousedown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  const initial = (user?.name || user?.email || '?').trim().charAt(0).toUpperCase()

  return (
    <div ref={rootRef} className="relative">
      <button
        ref={buttonRef}
        type="button"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
        aria-controls={menuId}
        aria-haspopup="true"
        className={`flex items-center gap-2 pl-1 pr-2 py-1 rounded-lg hover:bg-gray-100 ${focusRing}`}
      >
        <span className="w-7 h-7 rounded-full bg-primary-600 text-white text-sm font-semibold flex items-center justify-center" aria-hidden="true">
          {initial}
        </span>
        <span className="text-sm text-gray-700 max-w-[10rem] truncate">{user?.name}</span>
        <ChevronDown className="w-4 h-4 text-gray-400" aria-hidden="true" />
      </button>
      {open && (
        <div id={menuId} className="absolute right-0 mt-2 w-60 bg-white rounded-xl shadow-lg border border-gray-100 py-1 z-30">
          <div className="px-4 py-3 border-b border-gray-100">
            <p className="text-sm font-medium text-gray-900 truncate">{user?.name}</p>
            <p className="text-xs text-gray-500 truncate">{user?.email}</p>
          </div>
          <Link
            to="/settings"
            onClick={() => setOpen(false)}
            className={`flex items-center gap-2 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 ${focusRing} focus-visible:ring-inset`}
          >
            <Settings className="w-4 h-4" aria-hidden="true" /> Settings
          </Link>
          <button
            type="button"
            onClick={onLogout}
            className={`w-full flex items-center gap-2 px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 hover:text-red-600 ${focusRing} focus-visible:ring-inset`}
          >
            <LogOut className="w-4 h-4" aria-hidden="true" /> Log out
          </button>
        </div>
      )}
    </div>
  )
}

export default function AppShell() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [mobileState, setMobileState] = useState({ open: false, path: location.pathname })
  const mobileMenuId = useId()

  // The menu closes by itself whenever the route changes.
  const mobileOpen = mobileState.open && mobileState.path === location.pathname
  const toggleMobile = () => setMobileState({ open: !mobileOpen, path: location.pathname })

  const handleLogout = async () => {
    try {
      await logout()
    } finally {
      navigate('/login', { replace: true })
    }
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50 focus:bg-white focus:px-3 focus:py-2 focus:rounded-lg focus:shadow"
      >
        Skip to content
      </a>
      <header className="bg-white border-b border-gray-200 sticky top-0 z-20">
        <div className="max-w-5xl mx-auto px-4 h-14 flex items-center justify-between gap-4">
          <div className="flex items-center gap-6">
            <Link to="/dashboard" className={`flex items-center gap-2 rounded-lg ${focusRing}`}>
              <div className="w-7 h-7 bg-primary-600 rounded-lg flex items-center justify-center">
                <TrendingDown className="w-4 h-4 text-white" aria-hidden="true" />
              </div>
              <span className="font-semibold text-gray-900">ExpenseFlow</span>
            </Link>
            <nav aria-label="Main" className="hidden md:flex items-center gap-1">
              {NAV_ITEMS.map(({ to, label, icon: Icon }) => (
                <NavLink key={to} to={to} className={navLinkCls}>
                  <Icon className="w-4 h-4" aria-hidden="true" /> {label}
                </NavLink>
              ))}
            </nav>
          </div>

          <div className="hidden md:block">
            <UserMenu user={user} onLogout={handleLogout} />
          </div>

          <button
            type="button"
            onClick={toggleMobile}
            aria-label={mobileOpen ? 'Close menu' : 'Open menu'}
            aria-expanded={mobileOpen}
            aria-controls={mobileMenuId}
            className={`md:hidden p-2 rounded-lg text-gray-600 hover:bg-gray-100 ${focusRing}`}
          >
            {mobileOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
          </button>
        </div>

        {mobileOpen && (
          <div id={mobileMenuId} className="md:hidden border-t border-gray-100 px-4 py-3 space-y-1">
            <div className="px-3 py-2 mb-1">
              <p className="text-sm font-medium text-gray-900 truncate">{user?.name}</p>
              <p className="text-xs text-gray-500 truncate">{user?.email}</p>
            </div>
            <nav aria-label="Main mobile" className="space-y-1">
              {NAV_ITEMS.map(({ to, label, icon: Icon }) => (
                <NavLink key={to} to={to} className={navLinkCls}>
                  <Icon className="w-4 h-4" aria-hidden="true" /> {label}
                </NavLink>
              ))}
            </nav>
            <button
              type="button"
              onClick={handleLogout}
              className={`w-full flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-sm font-medium text-gray-600 hover:text-red-600 hover:bg-gray-100 ${focusRing}`}
            >
              <LogOut className="w-4 h-4" aria-hidden="true" /> Log out
            </button>
          </div>
        )}
      </header>

      <main id="main-content" tabIndex={-1} className="max-w-5xl mx-auto px-4 py-6 focus:outline-none">
        <Outlet />
      </main>
    </div>
  )
}
