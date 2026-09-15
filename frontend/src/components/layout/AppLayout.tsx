import { NavLink, Outlet } from 'react-router-dom'
import { Building2, CalendarDays, House, Scissors, UsersRound } from 'lucide-react'
import { useAuth } from '@/hooks/useAuth'

export function AppLayout() {
  const { user, logout } = useAuth()
  const navItems = user?.role === 'ADMIN'
    ? [
        { to: '/', label: 'Início', icon: House },
        { to: '/agenda', label: 'Agenda', icon: CalendarDays },
        { to: '/funcionarios', label: 'Funcionários', icon: UsersRound },
        { to: '/servicos', label: 'Serviços', icon: Scissors },
        { to: '/estabelecimento', label: 'Estabelecimento', icon: Building2 },
      ]
    : user?.role === 'CLIENT'
      ? [{ to: '/agendamentos', label: 'Agendamentos', icon: CalendarDays }]
      : user?.role === 'PROFESSIONAL'
        ? [{ to: '/minha-agenda', label: 'Minha agenda', icon: CalendarDays }]
        : [{ to: '/', label: 'Início', icon: House }]

  return (
    <div className="min-h-screen bg-sand-50 md:flex">
      <aside className="hidden w-64 shrink-0 flex-col border-r border-brand-100 bg-white md:flex">
        <div className="border-b border-brand-100 px-5 py-5">
          <span className="font-[var(--font-display)] text-lg text-brand-900">SalonHub</span>
        </div>

        <nav className="flex-1 space-y-1 px-3 py-4">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                `flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition ${
                  isActive
                    ? 'bg-brand-100 text-brand-800'
                    : 'text-brand-600 hover:bg-sand-100 hover:text-brand-800'
                }`
              }
            >
              <item.icon size={18} /> {item.label}
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

      <div className="min-w-0 flex-1">
        <header className="border-b border-brand-100 bg-white px-4 py-3 md:hidden">
          <div className="flex items-center justify-between"><span className="font-[var(--font-display)] text-lg text-brand-900">SalonHub</span><button onClick={logout} className="text-sm font-medium text-brand-600">Sair</button></div>
          <nav className="mt-3 flex gap-2 overflow-x-auto">
            {navItems.map((item) => <NavLink key={item.to} to={item.to} end={item.to === '/'} className={({ isActive }) => `whitespace-nowrap rounded-lg px-3 py-2 text-sm font-medium ${isActive ? 'bg-brand-100 text-brand-800' : 'text-brand-600'}`}>{item.label}</NavLink>)}
          </nav>
        </header>
        <main><Outlet /></main>
      </div>
    </div>
  )
}
