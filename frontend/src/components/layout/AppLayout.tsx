import { NavLink, Outlet } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard' },
  { to: '/funcionarios', label: 'Funcionários' },
  { to: '/servicos', label: 'Serviços' },
  { to: '/atendimentos', label: 'Atendimentos' },
]

export function AppLayout() {
  const { user, logout } = useAuth()

  return (
    <div className="flex min-h-screen bg-sand-50">
      <aside className="flex w-60 shrink-0 flex-col border-r border-brand-100 bg-white">
        <div className="border-b border-brand-100 px-5 py-5">
          <span className="font-[var(--font-display)] text-lg text-brand-900">SalonHub</span>
        </div>

        <nav className="flex-1 space-y-1 px-3 py-4">
          {NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                `block rounded-lg px-3 py-2 text-sm font-medium transition ${
                  isActive
                    ? 'bg-brand-100 text-brand-800'
                    : 'text-brand-600 hover:bg-sand-100 hover:text-brand-800'
                }`
              }
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="border-t border-brand-100 px-4 py-4">
          <p className="truncate text-sm font-medium text-brand-900">{user?.name}</p>
          <button
            onClick={logout}
            className="mt-2 text-sm text-brand-500 hover:text-brand-700"
          >
            Sair
          </button>
        </div>
      </aside>

      <main className="flex-1">
        <Outlet />
      </main>
    </div>
  )
}
