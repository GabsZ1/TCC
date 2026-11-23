/** Monta a navegação da área autenticada conforme o papel e a sessão do usuário. */
import { useCallback, useEffect, useRef, useState, type ComponentType } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import {
  BadgeDollarSign,
  Building2,
  CalendarDays,
  CalendarOff,
  ChevronDown,
  ClipboardList,
  House,
  KeyRound,
  LogOut,
  Menu,
  UserRound,
  Scissors,
  Settings,
  Store,
  TrendingUp,
  UsersRound,
  X,
} from 'lucide-react'
import { Brand } from '@/components/brand/Brand'
import { useAuth } from '@/features/auth/session/useAuth'

type NavGroup = 'Agenda' | 'Cadastros' | 'Relatórios' | 'Estabelecimento' | 'Conta'

type NavItem = {
  to: string
  label: string
  icon: ComponentType<{ size?: number; strokeWidth?: number; className?: string }>
  group?: NavGroup
}

/** Escolhe navegação e atalhos a partir da sessão e do papel autenticado. */
export function AppLayout() {
  const { user, logout } = useAuth()
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const closeMobileMenu = useCallback(() => setMobileMenuOpen(false), [])

  const roleNavItems: NavItem[] = user?.role === 'SYSTEM_ADMIN'
    ? [
        { to: '/', label: 'Início', icon: House },
        { to: '/admin/estabelecimentos', label: 'Estabelecimentos', icon: Building2, group: 'Estabelecimento' },
      ]
    : user?.role === 'ADMIN'
      ? [
          { to: '/', label: 'Início', icon: House },
          { to: '/agenda', label: 'Agenda', icon: CalendarDays, group: 'Agenda' },
          { to: '/estabelecimento/bloqueios', label: 'Bloqueios', icon: CalendarOff, group: 'Agenda' },
          { to: '/funcionarios', label: 'Equipe', icon: UsersRound, group: 'Cadastros' },
          { to: '/clientes', label: 'Clientes e agendamentos', icon: UserRound, group: 'Cadastros' },
          { to: '/servicos', label: 'Serviços', icon: Scissors, group: 'Cadastros' },
          { to: '/relatorio-atendimentos', label: 'Atendimentos', icon: ClipboardList, group: 'Relatórios' },
          { to: '/relatorio-comissoes', label: 'Comissões', icon: BadgeDollarSign, group: 'Relatórios' },
          { to: '/relatorio-financeiro', label: 'Financeiro', icon: TrendingUp, group: 'Relatórios' },
          { to: '/perfil-estabelecimento', label: 'Perfil público', icon: Store, group: 'Estabelecimento' },
          { to: '/estabelecimento', label: 'Configurações', icon: Settings, group: 'Estabelecimento' },
        ]
      : user?.role === 'CLIENT'
        ? [
            { to: '/', label: 'Início', icon: House },
            { to: '/agendamentos', label: 'Meus agendamentos', icon: CalendarDays, group: 'Agenda' },
          ]
        : user?.role === 'PROFESSIONAL'
          ? [
              { to: '/', label: 'Início', icon: House },
              { to: '/minha-agenda', label: 'Minha agenda', icon: CalendarDays, group: 'Agenda' },
              { to: '/clientes', label: 'Clientes e agendamentos', icon: UserRound, group: 'Cadastros' },
              { to: '/meus-servicos', label: 'Meus serviços', icon: Scissors, group: 'Cadastros' },
              { to: '/meus-atendimentos', label: 'Atendimentos realizados', icon: BadgeDollarSign, group: 'Relatórios' },
            ]
          : [{ to: '/', label: 'Início', icon: House }]

  const navItems: NavItem[] = [
    ...roleNavItems,
    // Cliente e profissional trocam a senha pelo card em "Meu perfil"; os demais perfis, pelo item do menu.
    ...(user?.role === 'CLIENT' || user?.role === 'PROFESSIONAL'
      ? [{ to: '/meu-perfil', label: 'Meu perfil', icon: UserRound, group: 'Conta' } as NavItem]
      : [{ to: '/alterar-senha', label: 'Alterar senha', icon: KeyRound, group: 'Conta' } as NavItem]),
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
              <p className="mt-0.5 truncate text-sm font-bold text-text-950">{user?.establishmentName ?? user?.name ?? 'Meu Espaço'}</p>
            </div>
            <button
              type="button"
              className="grid size-10 place-items-center rounded-[0.7rem] border border-background-300 bg-white text-text-700 shadow-sm"
              onClick={() => setMobileMenuOpen(true)}
              aria-label="Abrir menu"
              title="Abrir menu de navegação"
            >
              <Menu size={20} />
            </button>
          </div>
        </header>

        <main className="min-h-full"><Outlet /></main>
      </div>

      {mobileMenuOpen && (
        <MobileDrawer items={navItems} user={user} onClose={closeMobileMenu} onLogout={logout} />
      )}
    </div>
  )
}

/** Apresenta as seções disponíveis para o papel atual na navegação lateral. */
function DesktopSidebar({ items, user, onLogout }: { items: NavItem[]; user: ReturnType<typeof useAuth>['user']; onLogout: () => void }) {
  return (
    <aside className="relative hidden w-[16.5rem] shrink-0 flex-col overflow-hidden bg-[var(--sidebar-bg)] md:flex md:h-screen">
      <div className="absolute inset-y-0 left-0 w-[3px] bg-primary-500" aria-hidden="true" />

      <div className="px-6 pb-6 pt-6">
        <Brand inverse />
        <div className="mt-7 border-t border-[var(--sidebar-border)] pt-5">
          <p className="text-[0.58rem] font-extrabold uppercase tracking-[0.18em] text-[var(--sidebar-muted)]">Workspace</p>
          <p className="mt-2 truncate text-sm font-bold text-[var(--sidebar-text)]">{user?.establishmentName ?? 'Meu Espaço'}</p>
          <p className="mt-1 text-xs text-[var(--sidebar-muted)]">{roleLabel(user?.role)}</p>
        </div>
      </div>

      <nav className="flex-1 overflow-y-auto px-3 pb-5">
        <NavSections items={items} idPrefix="desktop-nav" />
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

/** Mantém o grupo da página atual aberto e permite recolher os demais. */
function NavSections({ items, idPrefix, onNavigate }: { items: NavItem[]; idPrefix: string; onNavigate?: () => void }) {
  const { pathname } = useLocation()
  const activeGroup = items.find((item) => item.group && (pathname === item.to || pathname.startsWith(`${item.to}/`)))?.group ?? null
  const [selection, setSelection] = useState({ pathname, activeGroup, group: activeGroup })
  const openGroup = selection.pathname === pathname && selection.activeGroup === activeGroup ? selection.group : activeGroup

  const groups = Array.from(new Set(items.map((item) => item.group).filter((group): group is NavGroup => !!group)))

  return <>
    <div className="mb-4 space-y-1">
      {items.filter((item) => !item.group).map((item) => <SidebarLink key={item.to} item={item} onNavigate={onNavigate} />)}
    </div>
    {groups.map((group) => {
      const groupItems = items.filter((item) => item.group === group)
      if (groupItems.length === 1) {
        return <div key={group} className="mb-3"><SidebarLink item={groupItems[0]} onNavigate={onNavigate} /></div>
      }
      const isOpen = openGroup === group
      const panelId = `${idPrefix}-${group.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '')}`
      return <div key={group} className="mb-2">
        <button
          type="button"
          className={`flex min-h-10 w-full items-center justify-between rounded-[0.72rem] px-3 text-left text-xs font-bold uppercase tracking-[0.13em] hover:bg-white/10 hover:text-white ${activeGroup === group ? 'text-white' : 'text-[var(--sidebar-muted)]'}`}
          aria-expanded={isOpen}
          aria-controls={panelId}
          onClick={() => setSelection({ pathname, activeGroup, group: isOpen ? null : group })}
        >
          {group}
          <ChevronDown size={16} className={`shrink-0 transition-transform ${isOpen ? 'rotate-180' : ''}`} aria-hidden="true" />
        </button>
        <div id={panelId} hidden={!isOpen} className="ml-3 space-y-1 border-l border-[var(--sidebar-border)] pl-2 pt-1">
          {groupItems.map((item) => <SidebarLink key={item.to} item={item} onNavigate={onNavigate} />)}
        </div>
      </div>
    })}
  </>
}

/** Marca a rota ativa e padroniza os links da navegação lateral. */
function SidebarLink({ item, onNavigate }: { item: NavItem; onNavigate?: () => void }) {
  const Icon = item.icon
  return (
    <NavLink
      to={item.to}
      onClick={onNavigate}
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

/** Reproduz a navegação permitida em telas pequenas e fecha o painel ao navegar. */
function MobileDrawer({ items, user, onClose, onLogout }: { items: NavItem[]; user: ReturnType<typeof useAuth>['user']; onClose: () => void; onLogout: () => void }) {
  const drawerRef = useRef<HTMLElement>(null)
  const closeButtonRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    closeButtonRef.current?.focus()

    /** Fecha o painel com Escape e mantém a navegação por Tab dentro dele. */
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        event.preventDefault()
        onClose()
      }
      if (event.key !== 'Tab' || !drawerRef.current) return
      const focusable = Array.from(drawerRef.current.querySelectorAll<HTMLElement>('a[href], button:not([disabled])'))
        .filter((element) => element.getClientRects().length > 0)
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last?.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first?.focus()
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      document.body.style.overflow = previousOverflow
      previouslyFocused?.focus()
    }
  }, [onClose])

  return (
    <div className="fixed inset-0 z-[60] md:hidden" role="dialog" aria-modal="true" aria-label="Menu de navegação">
      <button className="absolute inset-0 bg-overlay backdrop-blur-[2px]" onClick={onClose} aria-label="Fechar menu" />
      <aside ref={drawerRef} className="absolute inset-y-0 left-0 flex w-[min(88vw,20rem)] flex-col bg-[var(--sidebar-bg)] p-4 shadow-2xl">
        <div className="flex items-center justify-between px-1 pt-1">
          <Brand inverse />
          <button ref={closeButtonRef} onClick={onClose} className="grid size-10 place-items-center rounded-[0.7rem] text-[var(--sidebar-muted)] hover:bg-white/10 hover:text-white" aria-label="Fechar menu" title="Fechar menu de navegação"><X size={20} /></button>
        </div>
        <nav className="mt-8 flex-1 overflow-y-auto" aria-label="Menu principal">
          <NavSections items={items} idPrefix="mobile-nav" onNavigate={onClose} />
        </nav>
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
