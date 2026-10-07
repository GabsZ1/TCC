import { type ComponentType } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import {
  ArrowRight,
  CalendarCheck,
  CalendarDays,
  Clock3,
  Building2,
  Scissors,
  Wallet,
} from 'lucide-react'
import { useAuth } from '@/hooks/useAuth'
import { fetchDashboardSummary } from '@/features/dashboard/dashboardApi'
import { getApiErrorMessage } from '@/lib/apiError'
import { fetchNextClientAppointment, fetchProfessionalToday, type Appointment } from '@/features/appointments/appointmentsApi'
import { businessToday, formatBusinessDateWithWeekday } from '@/lib/dateTime'

export function DashboardPage() {
  const { user } = useAuth()
  const dashboard = useQuery({ queryKey: ['dashboard-summary'], queryFn: fetchDashboardSummary, enabled: user?.role === 'ADMIN' })
  const clientNext = useQuery({ queryKey: ['client-next-appointment'], queryFn: fetchNextClientAppointment, enabled: user?.role === 'CLIENT' })
  const professionalToday = useQuery({ queryKey: ['professional-today'], queryFn: fetchProfessionalToday, enabled: user?.role === 'PROFESSIONAL' })

  if (user?.role === 'SYSTEM_ADMIN') return <SystemAdministratorHome name={user.name} />
  if (user?.role === 'CLIENT') return <ClientHome name={user.name} next={clientNext.data ?? null} isLoading={clientNext.isLoading} error={clientNext.isError ? clientNext.error : null} />
  if (user?.role === 'PROFESSIONAL') return <ProfessionalHome name={user.name} establishmentName={user.establishmentName} today={professionalToday.data} isLoading={professionalToday.isLoading} error={professionalToday.isError ? professionalToday.error : null} />

  const firstName = user?.name?.split(' ')[0] ?? 'Olá'

  return (
    <div className="page-shell">
      <header className="flex flex-col gap-5 border-b border-background-300 pb-7 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <p className="eyebrow">Painel administrativo</p>
          <h1 className="page-title mt-3">{user?.establishmentName ?? 'Seu estabelecimento'}</h1>
          <p className="page-subtitle">Visão do dia para você decidir rápido e tocar a operação, {firstName}.</p>
        </div>
        <div className="text-left lg:text-right">
          <p className="text-[0.62rem] font-extrabold uppercase tracking-[0.16em] text-text-500">Hoje</p>
          <p className="mt-1 text-sm font-bold capitalize text-text-800">{formatToday()}</p>
        </div>
      </header>

      {dashboard.isError && (
        <p className="mt-6 rounded-[0.8rem] border border-danger-500/25 bg-danger-50 px-4 py-3 text-sm font-semibold text-danger-600">
          {getApiErrorMessage(dashboard.error, 'Não foi possível carregar os indicadores do dashboard.')}
        </p>
      )}

      <section className="surface-card mt-7 grid overflow-hidden sm:grid-cols-2 lg:grid-cols-4">
        <MetricLine icon={CalendarDays} label="Agendamentos hoje" helper="horários marcados para hoje" value={dashboard.isLoading ? '—' : String(dashboard.data?.todaysAppointments ?? 0)} />
        <MetricLine icon={CalendarCheck} label="Concluídos hoje" helper="finalizados hoje" value={dashboard.isLoading ? '—' : String(dashboard.data?.todaysCompletedServices ?? 0)} bordered />
        <MetricLine icon={Clock3} label="Pendentes de fechamento" helper="confirmados já encerrados" value={dashboard.isLoading ? '—' : String(dashboard.data?.pendingClosure ?? 0)} bordered />
        <MetricLine icon={Wallet} label="Realizado hoje" helper="atendimentos concluídos" value={dashboard.isLoading ? '—' : formatMoney(dashboard.data?.todaysRevenue ?? 0)} bordered />
      </section>

      <section className="surface-card mt-5 p-5 sm:p-6">
        <div className="flex items-center justify-between"><h2 className="font-semibold">Próximos atendimentos</h2><Link to="/agenda" className="text-sm font-semibold text-primary-700">Abrir agenda</Link></div>
        {dashboard.data?.upcomingAppointments.length === 0 && <p className="mt-4 text-sm text-text-600">Nenhum atendimento confirmado pela frente.</p>}
        <ul className="mt-3 divide-y divide-background-300">{dashboard.data?.upcomingAppointments.map(item => <li key={item.id} className="flex flex-wrap justify-between gap-2 py-3 text-sm"><span><strong>{item.customerName}</strong> · {item.serviceName} · {item.professionalName}</span><span>{formatAppointmentDate(item.startsAt)} às {item.startsAt.slice(11, 16)}</span></li>)}</ul>
      </section>

      <section className="surface-card mt-5 divide-y divide-background-300 overflow-hidden">
        <ActionRow to="/relatorio-atendimentos" icon={CalendarCheck} eyebrow="Operação" title="Acompanhar atendimentos" description="Histórico da equipe, períodos e serviços concluídos." />
        <ActionRow to="/relatorio-comissoes" icon={Wallet} eyebrow="Financeiro" title="Consultar comissões" description="Valores gerados por período e por profissional." />
      </section>
    </div>
  )
}

function ClientHome({ name, next, isLoading, error }: { name: string; next: Appointment | null; isLoading: boolean; error: unknown }) {

  return (
    <RoleHomeHeader eyebrow="Área do cliente" name={name} subtitle="Seus próximos horários, sem complicação.">
      <div className="mt-8 grid gap-5 lg:grid-cols-[minmax(0,1.45fr)_minmax(18rem,.55fr)]">
        <section className="relative overflow-hidden rounded-[1rem] bg-secondary-700 p-6 text-white shadow-[var(--shadow-lifted)] sm:p-8">
          <div className="absolute left-0 top-0 h-full w-[5px] bg-primary-500" />
          <p className="text-[0.62rem] font-extrabold uppercase tracking-[0.18em] text-primary-300">Próximo compromisso</p>
          {isLoading ? <p className="mt-6 text-[#c3cde0]">Carregando sua agenda...</p> : error ? <p role="alert" className="mt-6 text-danger-200">{getApiErrorMessage(error, 'Não foi possível carregar sua agenda.')}</p> : next ? (
            <>
              <h2 className="mt-5 text-3xl font-extrabold tracking-[-0.04em] text-white">{next.serviceName}</h2>
              <p className="mt-2 text-sm text-[#c3cde0]">{next.establishmentName} · com {next.professionalName}</p>
              <div className="mt-7 flex flex-wrap gap-x-7 gap-y-3 border-t border-white/10 pt-5 text-sm font-bold">
                <span>{formatAppointmentDate(next.startsAt)}</span><span className="text-primary-300">{next.startsAt.slice(11, 16)}</span>
              </div>
            </>
          ) : <><h2 className="mt-5 text-3xl font-extrabold tracking-[-0.04em] text-white">Sua agenda está livre.</h2><p className="mt-2 max-w-xl text-sm leading-6 text-[#c3cde0]">Escolha um estabelecimento e encontre um horário que encaixe no seu dia.</p></>}
        </section>
        <aside className="surface-card flex flex-col p-6">
          <span className="grid size-10 place-items-center rounded-[0.65rem] bg-primary-50 text-primary-700"><CalendarDays size={19} /></span>
          <h2 className="mt-5 text-lg font-extrabold tracking-[-0.025em] text-text-950">Meus agendamentos</h2>
          <p className="mt-2 flex-1 text-sm leading-6 text-text-600">Agende, remarque ou consulte seu histórico.</p>
          <Link to="/agendamentos" className="primary-button mt-6 w-full">Abrir agendamentos <ArrowRight size={16} /></Link>
        </aside>
      </div>
    </RoleHomeHeader>
  )
}

function ProfessionalHome({ name, establishmentName, today, isLoading, error }: { name: string; establishmentName: string | null; today?: { confirmedCount: number; next: Appointment | null }; isLoading: boolean; error: unknown }) {
  const next = today?.next

  return (
    <RoleHomeHeader eyebrow="Área do profissional" name={name} subtitle={`Seu dia${establishmentName ? ` no ${establishmentName}` : ''}, direto ao ponto.`}>
      {Boolean(error) && <p role="alert" className="mt-6 rounded-[0.8rem] border border-danger-500/25 bg-danger-50 px-4 py-3 text-sm font-semibold text-danger-600">{getApiErrorMessage(error, 'Não foi possível carregar sua agenda.')}</p>}
      <section className="surface-card mt-8 grid overflow-hidden md:grid-cols-3">
        <MetricLine icon={CalendarCheck} label="Atendimentos hoje" helper="confirmados na agenda" value={isLoading ? '—' : String(today?.confirmedCount ?? 0)} />
        <MetricLine icon={Clock3} label="Próximo horário" helper={next?.clientName ?? 'nenhum cliente aguardando'} value={next ? next.startsAt.slice(11, 16) : 'Livre'} bordered />
        <MetricLine icon={Scissors} label="Serviço seguinte" helper={next?.clientName ?? 'sua agenda está livre'} value={next?.serviceName ?? '—'} bordered />
      </section>
      <section className="surface-card mt-5 divide-y divide-background-300 overflow-hidden">
        <ActionRow to="/minha-agenda" icon={CalendarDays} eyebrow="Operação" title="Abrir minha agenda" description="Horários do dia, clientes e detalhes dos atendimentos." />
        <ActionRow to="/meus-servicos" icon={Scissors} eyebrow="Perfil profissional" title="Consultar meus serviços" description="Serviços vinculados ao seu perfil no estabelecimento." />
      </section>
    </RoleHomeHeader>
  )
}

function SystemAdministratorHome({ name }: { name: string }) {
  return (
    <RoleHomeHeader eyebrow="Administração do sistema" name={name} subtitle="Controle os estabelecimentos que utilizam a plataforma.">
      <section className="surface-card mt-8 max-w-3xl overflow-hidden">
        <ActionRow to="/admin/estabelecimentos" icon={Building2} eyebrow="Gestão da plataforma" title="Gerenciar estabelecimentos" description="Cadastros, estado operacional e acesso a novas reservas." />
      </section>
    </RoleHomeHeader>
  )
}

function RoleHomeHeader({ eyebrow, name, subtitle, children }: { eyebrow: string; name: string; subtitle: string; children: React.ReactNode }) {
  const firstName = name.split(' ')[0]
  return <div className="page-shell"><header className="border-b border-background-300 pb-7"><p className="eyebrow">{eyebrow}</p><h1 className="page-title mt-3">Olá, {firstName}</h1><p className="page-subtitle">{subtitle}</p></header>{children}</div>
}

function MetricLine({ icon: Icon, label, helper, value, bordered = false }: { icon: ComponentType<{ size?: number; strokeWidth?: number }>; label: string; helper: string; value: string; bordered?: boolean }) {
  return (
    <article className={`p-5 sm:p-6 ${bordered ? 'border-t border-background-300 sm:border-l sm:border-t-0' : ''}`}>
      <div className="flex items-center gap-2 text-text-500"><Icon size={16} strokeWidth={2} /><p className="text-[0.65rem] font-extrabold uppercase tracking-[0.13em]">{label}</p></div>
      <p className="mt-5 break-words text-[2rem] font-extrabold leading-none tracking-[-0.055em] text-text-950">{value}</p>
      <p className="mt-2 truncate text-xs font-medium text-text-500">{helper}</p>
    </article>
  )
}

function ActionRow({ to, icon: Icon, eyebrow, title, description }: { to: string; icon: ComponentType<{ size?: number }>; eyebrow: string; title: string; description: string }) {
  return (
    <Link to={to} className="group flex items-center gap-4 px-5 py-5 hover:bg-primary-50/60 sm:px-6">
      <span className="grid size-10 shrink-0 place-items-center rounded-[0.65rem] border border-background-300 bg-white text-text-700 group-hover:border-primary-200 group-hover:text-primary-700"><Icon size={18} /></span>
      <span className="min-w-0 flex-1"><span className="text-[0.6rem] font-extrabold uppercase tracking-[0.15em] text-primary-700">{eyebrow}</span><span className="mt-1 block text-sm font-extrabold text-text-950">{title}</span><span className="mt-1 block text-sm text-text-600">{description}</span></span>
      <ArrowRight size={17} className="shrink-0 text-text-400 transition group-hover:translate-x-1 group-hover:text-primary-700" />
    </Link>
  )
}

function formatMoney(value: number) { return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value) }
function formatToday() { return formatBusinessDateWithWeekday(businessToday()) }
function formatAppointmentDate(value: string) { return formatBusinessDateWithWeekday(value.slice(0, 10)) }
