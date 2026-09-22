import { useEffect, useState, type ComponentType } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import {
  BadgeDollarSign,
  Building2,
  CalendarDays,
  ClipboardList,
  House,
  LogOut,
  Menu,
  Scissors,
  UsersRound,
  X,
} from 'lucide-react'
import { Brand } from '@/components/brand/Brand'
import { useAuth } from '@/hooks/useAuth'

type NavItem = {
  to: string
  label: string
  icon: ComponentType<{ size?: number; strokeWidth?: number; className?: string }>
  group?: 'Visão geral' | 'Operação' | 'Gestão'
}

export function AppLayout() {
  const { user, logout } = useAuth()
  const location = useLocation()
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)

  useEffect(() => {
    setMobileMenuOpen(false)
  }, [location.pathname])

  const navItems: NavItem[] = user?.role === 'ADMIN'
    ? [
        { to: '/', label: 'Início', icon: House, group: 'Visão geral' },
        { to: '/agenda', label: 'Agenda', icon: CalendarDays, group: 'Operação' },
        { to: '/relatorio-atendimentos', label: 'Atendimentos', icon: ClipboardList, group: 'Operação' },
        { to: '/relatorio-comissoes', label: 'Comissões', icon: BadgeDollarSign, group: 'Operação' },
        { to: '/funcionarios', label: 'Equipe', icon: UsersRound, group: 'Gestão' },
        { to: '/servicos', label: 'Serviços', icon: Scissors, group: 'Gestão' },
        { to: '/estabelecimento', label: 'Estabelecimento', icon: Building2, group: 'Gestão' },
      ]
    : user?.role === 'CLIENT'
      ? [{ to: '/agendamentos', label: 'Meus agendamentos', icon: CalendarDays, group: 'Visão geral' }]
      : user?.role === 'PROFESSIONAL'
        ? [
            { to: '/minha-agenda', label: 'Minha agenda', icon: CalendarDays, group: 'Visão geral' },
            { to: '/meus-servicos', label: 'Meus serviços', icon: Scissors, group: 'Gestão' },
          ]
        : [{ to: '/', label: 'Início', icon: House, group: 'Visão geral' }]

  return (
    <div className="min-h-screen bg-background-100 md:flex md:h-screen md:overflow-hidden">
      <DesktopSidebar items={navItems} user={user} onLogout={logout} />

      <div className="min-w-0 flex-1 md:h-screen md:overflow-y-auto">
        <header className="sticky top-0 z-30 border-b border-background-300/80 bg-background-100/90 px-4 py-3 backdrop-blur-xl md:hidden">
          <div className="flex items-center justify-between gap-3">
            <Brand compact />
            <div className="min-w-0 flex-1">
              <p className="truncate text-xs font-semibold uppercase tracking-[0.12em] text-text-500">
                {roleLabel(user?.role)}
              </p>
              <p className="truncate text-sm font-semibold text-text-900">
                {user?.establishmentName ?? user?.name ?? 'SalonHub'}
              </p>
            </div>
            <button
              type="button"
              className="grid size-10 place-items-center rounded-xl border border-background-300 bg-background-50 text-text-700 shadow-sm"
              onClick={() => setMobileMenuOpen(true)}
              aria-label="Abrir menu"
            >
              <Menu size={20} />
            </button>
          </div>
        </header>

        <main className="min-h-full">
          <Outlet />
        </main>
      </div>

      {mobileMenuOpen && (
        <MobileDrawer
          items={navItems}
          user={user}
          onClose={() => setMobileMenuOpen(false)}
          onLogout={logout}
        />
      )}
    </div>
  )
}

function DesktopSidebar({ items, user, onLogout }: { items: NavItem[]; user: ReturnType<typeof useAuth>['user']; onLogout: () => void }) {
  const groups = Array.from(new Set(items.map((item) => item.group)))

  return (
    <aside className="hidden w-[17.5rem] shrink-0 flex-col bg-[var(--sidebar-bg)] md:flex md:h-screen">
      <div className="px-5 pb-4 pt-5">
        <Brand inverse />
      </div>

      {user?.establishmentName && (
        <div className="mx-4 mt-2 rounded-2xl border border-[var(--sidebar-border)] bg-white/[0.045] p-3.5">
          <p className="text-[0.62rem] font-bold uppercase tracking-[0.16em] text-[var(--sidebar-muted)]">Workspace</p>
          <div className="mt-2 flex items-center gap-2.5">
            <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-white/10 text-[var(--sidebar-text)]">
              <Building2 size={15} />
            </span>
            <p className="truncate text-sm font-semibold text-[var(--sidebar-text)]">{user.establishmentName}</p>
          </div>
        </div>
      )}

      <nav className="mt-4 flex-1 overflow-y-auto px-3 pb-4">
        {groups.map((group) => (
          <div key={group ?? 'nav'} className="mb-5">
            <p className="mb-1.5 px-3 text-[0.61rem] font-bold uppercase tracking-[0.17em] text-[var(--sidebar-muted)]">
              {group}
            </p>
            <div className="space-y-1">
              {items.filter((item) => item.group === group).map((item) => (
                <SidebarLink key={item.to} item={item} />
              ))}
            </div>
          </div>
        ))}
      </nav>

      <div className="border-t border-[var(--sidebar-border)] p-3.5">
        <div className="flex items-center gap-3 rounded-2xl bg-white/[0.045] p-2.5">
          <Avatar name={user?.name} />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-semibold text-[var(--sidebar-text)]">{user?.name}</p>
            <p className="mt-0.5 truncate text-xs text-[var(--sidebar-muted)]">{roleLabel(user?.role)}</p>
          </div>
          <button
            type="button"
            onClick={onLogout}
            className="grid size-9 shrink-0 place-items-center rounded-xl text-[var(--sidebar-muted)] hover:bg-white/10 hover:text-[var(--sidebar-text)]"
            title="Sair"
            aria-label="Sair"
          >
            <LogOut size={17} />
          </button>
        </div>
      </div>
    </aside>
  )
}

function SidebarLink({ item }: { item: NavItem }) {
  const Icon = item.icon
  return (
    <NavLink
      to={item.to}
      end={item.to === '/'}
      className={({ isActive }) =>
        `group flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition ${
          isActive
            ? 'bg-[var(--sidebar-active)] text-[var(--sidebar-active-text)] shadow-sm'
            : 'text-[var(--sidebar-muted)] hover:bg-[var(--sidebar-bg-hover)] hover:text-[var(--sidebar-text)]'
        }`
      }
    >
      {({ isActive }) => (
        <>
          <span className={`grid size-7 place-items-center rounded-lg ${isActive ? 'bg-black/[0.055]' : 'bg-white/[0.045] group-hover:bg-white/[0.07]'}`}>
            <Icon size={16} strokeWidth={1.9} />
          </span>
          <span>{item.label}</span>
        </>
      )}
    </NavLink>
  )
}

function MobileDrawer({ items, user, onClose, onLogout }: { items: NavItem[]; user: ReturnType<typeof useAuth>['user']; onClose: () => void; onLogout: () => void }) {
  return (
    <div className="fixed inset-0 z-[60] md:hidden" role="dialog" aria-modal="true" aria-label="Menu de navegação">
      <button className="absolute inset-0 bg-overlay backdrop-blur-[2px]" onClick={onClose} aria-label="Fechar menu" />
      <aside className="absolute inset-y-0 left-0 flex w-[min(88vw,21rem)] flex-col bg-[var(--sidebar-bg)] p-4 shadow-2xl">
        <div className="flex items-center justify-between">
          <Brand inverse />
          <button onClick={onClose} className="grid size-10 place-items-center rounded-xl text-[var(--sidebar-muted)] hover:bg-white/10 hover:text-[var(--sidebar-text)]" aria-label="Fechar menu">
            <X size={20} />
          </button>
        </div>

        <div className="mt-7 flex-1 space-y-1 overflow-y-auto">
          {items.map((item) => <SidebarLink key={item.to} item={item} />)}
        </div>

        <div className="border-t border-[var(--sidebar-border)] pt-4">
          <div className="flex items-center gap-3">
            <Avatar name={user?.name} />
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-semibold text-[var(--sidebar-text)]">{user?.name}</p>
              <p className="truncate text-xs text-[var(--sidebar-muted)]">{user?.establishmentName ?? roleLabel(user?.role)}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={onLogout}
            className="mt-4 flex w-full items-center justify-center gap-2 rounded-xl border border-[var(--sidebar-border)] py-2.5 text-sm font-semibold text-[var(--sidebar-text)] hover:bg-white/10"
          >
            <LogOut size={16} /> Sair
          </button>
        </div>
      </aside>
    </div>
  )
}

function Avatar({ name }: { name?: string | null }) {
  const initials = (name ?? 'SH')
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('')

  return (
    <span className="grid size-10 shrink-0 place-items-center rounded-xl bg-primary-100 text-xs font-bold text-primary-800">
      {initials || 'SH'}
    </span>
  )
}

function roleLabel(role?: string) {
  if (role === 'ADMIN') return 'Administrador'
  if (role === 'PROFESSIONAL') return 'Profissional'
  if (role === 'CLIENT') return 'Cliente'
  return 'Conta'
}
