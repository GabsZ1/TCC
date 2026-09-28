import { useEffect, useState, type ComponentType } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import {
  BadgeDollarSign,
  Building2,
  CalendarDays,
  ClipboardList,
  House,
  KeyRound,
  LogOut,
  Menu,
  Scissors,
  Settings,
  Store,
  UsersRound,
  X,
} from 'lucide-react'
import { Brand } from '@/components/brand/Brand'
import { useAuth } from '@/hooks/useAuth'

type NavItem = {
  to: string
  label: string
  icon: ComponentType<{ size?: number; strokeWidth?: number; className?: string }>
  group?: 'Visão geral' | 'Operação' | 'Gestão' | 'Conta'
}

export function AppLayout() {
  const { user, logout } = useAuth()
  const location = useLocation()
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)

  useEffect(() => setMobileMenuOpen(false), [location.pathname])

  const roleNavItems: NavItem[] = user?.role === 'SYSTEM_ADMIN'
    ? [
        { to: '/', label: 'Início', icon: House, group: 'Visão geral' },
        { to: '/admin/estabelecimentos', label: 'Estabelecimentos', icon: Building2, group: 'Gestão' },
      ]
    : user?.role === 'ADMIN'
      ? [
          { to: '/', label: 'Início', icon: House, group: 'Visão geral' },
          { to: '/agenda', label: 'Agenda', icon: CalendarDays, group: 'Operação' },
          { to: '/relatorio-atendimentos', label: 'Atendimentos', icon: ClipboardList, group: 'Operação' },
          { to: '/relatorio-comissoes', label: 'Comissões', icon: BadgeDollarSign, group: 'Operação' },
          { to: '/funcionarios', label: 'Equipe', icon: UsersRound, group: 'Gestão' },
          { to: '/servicos', label: 'Serviços', icon: Scissors, group: 'Gestão' },
          { to: '/perfil-estabelecimento', label: 'Perfil público', icon: Store, group: 'Gestão' },
          { to: '/estabelecimento', label: 'Configurações', icon: Settings, group: 'Gestão' },
        ]
      : user?.role === 'CLIENT'
        ? [
            { to: '/', label: 'Início', icon: House, group: 'Visão geral' },
            { to: '/agendamentos', label: 'Meus agendamentos', icon: CalendarDays, group: 'Operação' },
          ]
        : user?.role === 'PROFESSIONAL'
          ? [
              { to: '/', label: 'Início', icon: House, group: 'Visão geral' },
              { to: '/minha-agenda', label: 'Minha agenda', icon: CalendarDays, group: 'Operação' },
              { to: '/meus-atendimentos', label: 'Atendimentos realizados', icon: BadgeDollarSign, group: 'Operação' },
              { to: '/meus-servicos', label: 'Meus serviços', icon: Scissors, group: 'Gestão' },
            ]
          : [{ to: '/', label: 'Início', icon: House, group: 'Visão geral' }]

  const navItems: NavItem[] = [
    ...roleNavItems,
    { to: '/alterar-senha', label: 'Alterar senha', icon: KeyRound, group: 'Conta' },
  ]

  return (
    <div className="min-h-screen bg-background-100 md:flex md:h-screen md:overflow-hidden">
      <DesktopSidebar items={navItems} user={user} onLogout={logout} />

      <div className="min-w-0 flex-1 md:h-screen md:overflow-y-auto">
        <header className="sticky top-0 z-30 border-b border-background-300 bg-white/95 px-4 py-3 backdrop-blur-lg md:hidden">
          <div className="flex items-center gap-3">
            <Brand compact />
            <div className="min-w-0 flex-1 border-l border-background-300 pl-3">
              <p className="truncate text-[0.62rem] font-extrabold uppercase tracking-[0.15em] text-primary-700">{roleLabel(user?.role)}</p>
              <p className="mt-0.5 truncate text-sm font-bold text-text-950">{user?.establishmentName ?? user?.name ?? 'SalonHub'}</p>
            </div>
            <button
              type="button"
              className="grid size-10 place-items-center rounded-[0.7rem] border border-background-300 bg-white text-text-700 shadow-sm"
              onClick={() => setMobileMenuOpen(true)}
              aria-label="Abrir menu"
            >
              <Menu size={20} />
            </button>
          </div>
        </header>

        <main className="min-h-full"><Outlet /></main>
      </div>

      {mobileMenuOpen && (
        <MobileDrawer items={navItems} user={user} onClose={() => setMobileMenuOpen(false)} onLogout={logout} />
      )}
    </div>
  )
}

function DesktopSidebar({ items, user, onLogout }: { items: NavItem[]; user: ReturnType<typeof useAuth>['user']; onLogout: () => void }) {
  const groups = Array.from(new Set(items.map((item) => item.group)))

  return (
    <aside className="relative hidden w-[16.5rem] shrink-0 flex-col overflow-hidden bg-[var(--sidebar-bg)] md:flex md:h-screen">
      <div className="absolute inset-y-0 left-0 w-[3px] bg-primary-500" aria-hidden="true" />

      <div className="px-6 pb-6 pt-6">
        <Brand inverse />
        <div className="mt-7 border-t border-[var(--sidebar-border)] pt-5">
          <p className="text-[0.58rem] font-extrabold uppercase tracking-[0.18em] text-[var(--sidebar-muted)]">Workspace</p>
          <p className="mt-2 truncate text-sm font-bold text-[var(--sidebar-text)]">{user?.establishmentName ?? 'SalonHub'}</p>
          <p className="mt-1 text-xs text-[var(--sidebar-muted)]">{roleLabel(user?.role)}</p>
        </div>
      </div>

      <nav className="flex-1 overflow-y-auto px-3 pb-5">
        {groups.map((group) => (
          <div key={group ?? 'nav'} className="mb-5">
            <p className="mb-1.5 px-3 text-[0.57rem] font-extrabold uppercase tracking-[0.17em] text-[var(--sidebar-muted)]">{group}</p>
            <div className="space-y-1">
              {items.filter((item) => item.group === group).map((item) => <SidebarLink key={item.to} item={item} />)}
            </div>
          </div>
        ))}
      </nav>

      <div className="border-t border-[var(--sidebar-border)] px-4 py-4">
        <div className="flex items-center gap-3">
          <Avatar name={user?.name} />
          <div className="min-w-0 flex-1">
            <p className="truncate text-sm font-bold text-[var(--sidebar-text)]">{user?.name}</p>
            <p className="mt-0.5 truncate text-xs text-[var(--sidebar-muted)]">Conta ativa</p>
          </div>
          <button
            type="button"
            onClick={onLogout}
            className="grid size-9 shrink-0 place-items-center rounded-[0.65rem] text-[var(--sidebar-muted)] hover:bg-white/10 hover:text-white"
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
        `group relative flex items-center gap-3 rounded-[0.72rem] px-3 py-2.5 text-sm font-semibold transition ${
          isActive
            ? 'bg-[var(--sidebar-active)] text-[var(--sidebar-active-text)] shadow-sm'
            : 'text-[var(--sidebar-muted)] hover:bg-[var(--sidebar-bg-hover)] hover:text-white'
        }`
      }
    >
      {({ isActive }) => (
        <>
          {isActive && <span className="absolute -left-1 h-5 w-[3px] rounded-full bg-primary-600" aria-hidden="true" />}
          <Icon size={17} strokeWidth={1.9} className={isActive ? 'text-primary-700' : ''} />
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
      <aside className="absolute inset-y-0 left-0 flex w-[min(88vw,20rem)] flex-col bg-[var(--sidebar-bg)] p-4 shadow-2xl">
        <div className="flex items-center justify-between px-1 pt-1">
          <Brand inverse />
          <button onClick={onClose} className="grid size-10 place-items-center rounded-[0.7rem] text-[var(--sidebar-muted)] hover:bg-white/10 hover:text-white" aria-label="Fechar menu"><X size={20} /></button>
        </div>
        <div className="mt-8 flex-1 space-y-1 overflow-y-auto">{items.map((item) => <SidebarLink key={item.to} item={item} />)}</div>
        <div className="border-t border-[var(--sidebar-border)] pt-4">
          <div className="flex items-center gap-3"><Avatar name={user?.name} /><div className="min-w-0 flex-1"><p className="truncate text-sm font-bold text-white">{user?.name}</p><p className="truncate text-xs text-[var(--sidebar-muted)]">{user?.establishmentName ?? roleLabel(user?.role)}</p></div></div>
          <button type="button" onClick={onLogout} className="mt-4 flex w-full items-center justify-center gap-2 rounded-[0.72rem] border border-[var(--sidebar-border)] py-2.5 text-sm font-bold text-white hover:bg-white/10"><LogOut size={16} /> Sair</button>
        </div>
      </aside>
    </div>
  )
}

function Avatar({ name }: { name?: string | null }) {
  const initials = (name ?? 'SH').split(' ').filter(Boolean).slice(0, 2).map((part) => part[0]?.toUpperCase()).join('')
  return <span className="grid size-10 shrink-0 place-items-center rounded-[0.7rem] bg-primary-500 text-xs font-extrabold text-white">{initials || 'SH'}</span>
}

function roleLabel(role?: string) {
  if (role === 'SYSTEM_ADMIN') return 'Administrador do sistema'
  if (role === 'ADMIN') return 'Administrador'
  if (role === 'PROFESSIONAL') return 'Profissional'
  if (role === 'CLIENT') return 'Cliente'
  return 'Conta'
}
