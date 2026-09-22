import { useState, type ComponentType } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import {
  ArrowUpRight,
  CalendarCheck,
  CalendarDays,
  Check,
  Clipboard,
  Clock3,
  Building2,
  Scissors,
  UsersRound,
  Wallet,
} from 'lucide-react'
import { useAuth } from '@/hooks/useAuth'
import { fetchDashboardSummary } from '@/features/dashboard/dashboardApi'
import { getApiErrorMessage } from '@/lib/apiError'
import { fetchMyAppointments, fetchMyAppointmentsAsProfessional, type Appointment } from '@/features/appointments/appointmentsApi'

export function DashboardPage() {
  const { user } = useAuth()
  const [copied, setCopied] = useState(false)
  const dashboard = useQuery({
    queryKey: ['dashboard-summary'],
    queryFn: fetchDashboardSummary,
    enabled: user?.role === 'ADMIN',
  })
  const clientAppointments = useQuery({
    queryKey: ['my-appointments'],
    queryFn: fetchMyAppointments,
    enabled: user?.role === 'CLIENT',
  })
  const professionalAppointments = useQuery({
    queryKey: ['professional-appointments'],
    queryFn: fetchMyAppointmentsAsProfessional,
    enabled: user?.role === 'PROFESSIONAL',
  })

  if (user?.role === 'SYSTEM_ADMIN') return <SystemAdministratorHome name={user.name} />
  if (user?.role === 'CLIENT') return <ClientHome name={user.name} appointments={clientAppointments.data ?? []} isLoading={clientAppointments.isLoading} />
  if (user?.role === 'PROFESSIONAL') return <ProfessionalHome name={user.name} establishmentName={user.establishmentName} appointments={professionalAppointments.data ?? []} isLoading={professionalAppointments.isLoading} />

  const firstName = user?.name?.split(' ')[0] ?? 'Olá'
  const establishmentCode = user?.establishmentId ? `#${user.establishmentId}` : '—'

  async function copyCode() {
    if (!user?.establishmentId) return
    try {
      await navigator.clipboard.writeText(String(user.establishmentId))
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1800)
    } catch {
      // Clipboard pode estar indisponível em alguns contextos; o código continua visível.
    }
  }

  return (
    <div className="page-shell">
      <header className="flex flex-col gap-5 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <p className="eyebrow">Painel administrativo</p>
          <h1 className="page-title mt-2">{user?.establishmentName ?? 'Seu estabelecimento'}</h1>
          <p className="page-subtitle">Boa gestão começa com uma visão simples do que está acontecendo agora, {firstName}.</p>
        </div>
        <p className="w-fit rounded-full border border-background-300 bg-background-50 px-3 py-1.5 text-xs font-semibold text-text-600 shadow-sm">
          {formatToday()}
        </p>
      </header>

      {dashboard.isError && (
        <p className="mt-6 rounded-2xl border border-danger-500/25 bg-danger-50 px-4 py-3 text-sm text-danger-600">
          {getApiErrorMessage(dashboard.error, 'Não foi possível carregar os indicadores do dashboard.')}
        </p>
      )}

      <section className="mt-8 grid gap-4 md:grid-cols-3">
        <MetricCard
          icon={CalendarCheck}
          label="Concluídos hoje"
          helper="serviços finalizados"
          value={dashboard.isLoading ? '—' : String(dashboard.data?.todaysCompletedServices ?? 0)}
          tone="primary"
        />
        <MetricCard
          icon={Wallet}
          label="Comissões no mês"
          helper="valor acumulado"
          value={dashboard.isLoading ? '—' : formatMoney(dashboard.data?.monthlyCommissionTotal ?? 0)}
          tone="accent"
        />
        <MetricCard
          icon={UsersRound}
          label="Equipe ativa"
          helper="profissionais disponíveis"
          value={dashboard.isLoading ? '—' : String(dashboard.data?.activeEmployees ?? 0)}
          tone="secondary"
        />
      </section>

      <section className="mt-5 grid gap-5 xl:grid-cols-[minmax(0,1.45fr)_minmax(18rem,.55fr)]">
        <div className="overflow-hidden rounded-[1.4rem] bg-primary-900 text-white shadow-[var(--shadow-lifted)]">
          <div className="relative p-6 sm:p-7">
            <div className="pointer-events-none absolute -right-16 -top-20 size-64 rounded-full border border-white/10" />
            <div className="pointer-events-none absolute -right-5 -top-5 size-40 rounded-full border border-white/10" />
            <div className="relative max-w-2xl">
              <p className="text-[0.68rem] font-bold uppercase tracking-[0.16em] text-primary-200">Atalhos do dia</p>
              <h2 className="mt-3 font-[var(--font-display)] text-2xl font-semibold tracking-[-0.03em] text-white sm:text-3xl">
                Tudo que você mais usa, sem caçar menu.
              </h2>
              <p className="mt-2 max-w-xl text-sm leading-6 text-primary-100">
                Consulte a agenda, ajuste a equipe ou atualize o catálogo de serviços em poucos cliques.
              </p>
              <div className="mt-6 grid gap-3 sm:grid-cols-3">
                <QuickAction to="/agenda" icon={CalendarDays} label="Abrir agenda" />
                <QuickAction to="/funcionarios" icon={UsersRound} label="Gerenciar equipe" />
                <QuickAction to="/servicos" icon={Scissors} label="Editar serviços" />
              </div>
            </div>
          </div>
        </div>

        <aside className="surface-card p-6">
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-[0.68rem] font-bold uppercase tracking-[0.14em] text-text-500">Código público</p>
              <p className="mt-2 font-[var(--font-display)] text-4xl font-semibold tracking-[-0.04em] text-text-900">{establishmentCode}</p>
            </div>
            <span className="grid size-11 place-items-center rounded-xl bg-accent-100 text-accent-700">
              <Clipboard size={19} />
            </span>
          </div>
          <p className="mt-4 text-sm leading-6 text-text-600">
            Clientes usam esse código para localizar o estabelecimento e iniciar um agendamento.
          </p>
          <button type="button" onClick={copyCode} className="secondary-button mt-5 w-full">
            {copied ? <Check size={16} /> : <Clipboard size={16} />}
            {copied ? 'Código copiado' : 'Copiar código'}
          </button>
        </aside>
      </section>

      <section className="mt-5 grid gap-4 md:grid-cols-2">
        <ActionCard
          to="/relatorio-atendimentos"
          icon={CalendarCheck}
          eyebrow="Operação"
          title="Acompanhar atendimentos"
          description="Veja o histórico da equipe, filtre períodos e confira o que foi concluído."
        />
        <ActionCard
          to="/relatorio-comissoes"
          icon={Wallet}
          eyebrow="Financeiro"
          title="Consultar comissões"
          description="Revise os valores gerados no período sem perder o contexto de cada profissional."
        />
      </section>
    </div>
  )
}

function ClientHome({ name, appointments, isLoading }: { name: string; appointments: Appointment[]; isLoading: boolean }) {
  const [now] = useState(() => Date.now())
  const next = appointments
    .filter((appointment) => appointment.status === 'CONFIRMED' && new Date(appointment.startsAt).getTime() > now)
    .sort((first, second) => first.startsAt.localeCompare(second.startsAt))[0]

  return (
    <RoleHomeHeader eyebrow="Área do cliente" name={name} subtitle="Organize seus próximos cuidados e acompanhe tudo em um só lugar.">
      <div className="mt-8 grid gap-5 lg:grid-cols-[minmax(0,1.35fr)_minmax(18rem,.65fr)]">
        <section className="overflow-hidden rounded-[1.4rem] bg-primary-900 p-6 text-white shadow-[var(--shadow-lifted)] sm:p-8">
          <p className="text-xs font-bold uppercase tracking-[0.16em] text-primary-200">Próximo compromisso</p>
          {isLoading ? <p className="mt-6 text-primary-100">Carregando sua agenda...</p> : next ? (
            <>
              <h2 className="mt-4 font-[var(--font-display)] text-3xl font-semibold">{next.serviceName}</h2>
              <p className="mt-2 text-primary-100">{next.establishmentName} · com {next.professionalName}</p>
              <div className="mt-6 flex flex-wrap gap-3 text-sm font-semibold"><span className="rounded-xl bg-white/10 px-3 py-2">{formatAppointmentDate(next.startsAt)}</span><span className="rounded-xl bg-white/10 px-3 py-2">{next.startsAt.slice(11, 16)}</span></div>
            </>
          ) : <><h2 className="mt-4 font-[var(--font-display)] text-3xl font-semibold">Sua agenda está livre</h2><p className="mt-2 text-primary-100">Escolha um estabelecimento e encontre o melhor horário para você.</p></>}
        </section>
        <aside className="surface-card p-6">
          <span className="grid size-11 place-items-center rounded-xl bg-accent-100 text-accent-700"><CalendarDays size={20} /></span>
          <h2 className="mt-5 text-xl font-semibold text-text-900">Meus agendamentos</h2>
          <p className="mt-2 text-sm leading-6 text-text-600">Agende um novo horário, remarque ou consulte seu histórico.</p>
          <Link to="/agendamentos" className="primary-button mt-6 w-full">Abrir agendamentos <ArrowUpRight size={16} /></Link>
        </aside>
      </div>
    </RoleHomeHeader>
  )
}

function ProfessionalHome({ name, establishmentName, appointments, isLoading }: { name: string; establishmentName: string | null; appointments: Appointment[]; isLoading: boolean }) {
  const [now] = useState(() => Date.now())
  const today = localDateKey(new Date(now))
  const todaysAppointments = appointments
    .filter((appointment) => appointment.status === 'CONFIRMED' && appointment.startsAt.slice(0, 10) === today)
    .sort((first, second) => first.startsAt.localeCompare(second.startsAt))
  const next = todaysAppointments.find((appointment) => new Date(appointment.endsAt).getTime() > now)

  return (
    <RoleHomeHeader eyebrow="Área do profissional" name={name} subtitle={`Acompanhe seu dia${establishmentName ? ` no ${establishmentName}` : ''} e chegue preparado para cada atendimento.`}>
      <section className="mt-8 grid gap-4 md:grid-cols-3">
        <MetricCard icon={CalendarCheck} label="Atendimentos hoje" helper="confirmados na agenda" value={isLoading ? '—' : String(todaysAppointments.length)} tone="primary" />
        <MetricCard icon={Clock3} label="Próximo horário" helper={next?.clientName ?? 'nenhum cliente aguardando'} value={next ? next.startsAt.slice(11, 16) : 'Livre'} tone="accent" />
        <MetricCard icon={Scissors} label="Serviço seguinte" helper={next?.clientName ?? 'sua agenda está livre'} value={next?.serviceName ?? '—'} tone="secondary" />
      </section>
      <section className="mt-5 grid gap-4 md:grid-cols-2">
        <ActionCard to="/minha-agenda" icon={CalendarDays} eyebrow="Operação" title="Abrir minha agenda" description="Veja os horários do dia, os clientes e os detalhes de cada atendimento." />
        <ActionCard to="/meus-servicos" icon={Scissors} eyebrow="Perfil profissional" title="Consultar meus serviços" description="Confira quais serviços estão vinculados ao seu perfil no estabelecimento." />
      </section>
    </RoleHomeHeader>
  )
}

function SystemAdministratorHome({ name }: { name: string }) {
  return (
    <RoleHomeHeader eyebrow="Administração do sistema" name={name} subtitle="Acompanhe os estabelecimentos que utilizam a plataforma e gerencie o acesso a novas reservas.">
      <section className="mt-8 max-w-2xl">
        <ActionCard to="/admin/estabelecimentos" icon={Building2} eyebrow="Gestão da plataforma" title="Gerenciar estabelecimentos" description="Liste todos os cadastros e desative estabelecimentos sem apagar o histórico de atendimentos." />
      </section>
    </RoleHomeHeader>
  )
}

function RoleHomeHeader({ eyebrow, name, subtitle, children }: { eyebrow: string; name: string; subtitle: string; children: React.ReactNode }) {
  const firstName = name.split(' ')[0]
  return <div className="page-shell"><header><p className="eyebrow">{eyebrow}</p><h1 className="page-title mt-2">Olá, {firstName}</h1><p className="page-subtitle">{subtitle}</p></header>{children}</div>
}

const metricTones = {
  primary: 'bg-primary-100 text-primary-700',
  accent: 'bg-accent-100 text-accent-700',
  secondary: 'bg-secondary-100 text-secondary-700',
}

function MetricCard({
  icon: Icon,
  label,
  helper,
  value,
  tone,
}: {
  icon: ComponentType<{ size?: number; strokeWidth?: number }>
  label: string
  helper: string
  value: string
  tone: keyof typeof metricTones
}) {
  return (
    <article className="surface-card group p-5 transition hover:-translate-y-0.5 hover:shadow-[var(--shadow-lifted)] sm:p-6">
      <div className="flex items-start justify-between gap-4">
        <span className={`grid size-11 place-items-center rounded-xl ${metricTones[tone]}`}>
          <Icon size={20} strokeWidth={1.9} />
        </span>
        <span className="text-[0.65rem] font-bold uppercase tracking-[0.13em] text-text-400">Hoje</span>
      </div>
      <p className="mt-6 text-[2rem] font-bold leading-none tracking-[-0.04em] text-text-950">{value}</p>
      <p className="mt-3 text-sm font-semibold text-text-800">{label}</p>
      <p className="mt-1 text-xs text-text-500">{helper}</p>
    </article>
  )
}

function QuickAction({ to, icon: Icon, label }: { to: string; icon: ComponentType<{ size?: number }>; label: string }) {
  return (
    <Link
      to={to}
      className="group flex items-center justify-between gap-3 rounded-xl border border-white/10 bg-white/[0.07] px-3.5 py-3 text-sm font-semibold text-white backdrop-blur-sm hover:-translate-y-0.5 hover:bg-white/[0.12]"
    >
      <span className="flex items-center gap-2.5"><Icon size={17} /> {label}</span>
      <ArrowUpRight size={15} className="opacity-55 transition group-hover:translate-x-0.5 group-hover:-translate-y-0.5 group-hover:opacity-100" />
    </Link>
  )
}

function ActionCard({
  to,
  icon: Icon,
  eyebrow,
  title,
  description,
}: {
  to: string
  icon: ComponentType<{ size?: number }>
  eyebrow: string
  title: string
  description: string
}) {
  return (
    <Link to={to} className="surface-card group flex items-start gap-4 p-5 hover:-translate-y-0.5 hover:border-primary-300 hover:shadow-[var(--shadow-lifted)] sm:p-6">
      <span className="grid size-11 shrink-0 place-items-center rounded-xl border border-background-300 bg-background-100 text-text-700 group-hover:border-primary-200 group-hover:bg-primary-100 group-hover:text-primary-700">
        <Icon size={20} />
      </span>
      <span className="min-w-0 flex-1">
        <span className="text-[0.64rem] font-bold uppercase tracking-[0.14em] text-text-500">{eyebrow}</span>
        <span className="mt-1 block text-base font-semibold text-text-900">{title}</span>
        <span className="mt-1.5 block text-sm leading-6 text-text-600">{description}</span>
      </span>
      <ArrowUpRight size={17} className="mt-1 shrink-0 text-text-400 transition group-hover:translate-x-0.5 group-hover:-translate-y-0.5 group-hover:text-primary-600" />
    </Link>
  )
}

function formatMoney(value: number) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value)
}

function formatToday() {
  return new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: '2-digit', month: 'long' }).format(new Date())
}

function formatAppointmentDate(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: '2-digit', month: 'long' }).format(new Date(value))
}

function localDateKey(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}
