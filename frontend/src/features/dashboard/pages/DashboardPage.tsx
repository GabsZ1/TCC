import { useState, type ComponentType } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import {
  ArrowRight,
  ArrowUpRight,
  CalendarCheck,
  CalendarDays,
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
  const dashboard = useQuery({ queryKey: ['dashboard-summary'], queryFn: fetchDashboardSummary, enabled: user?.role === 'ADMIN' })
  const clientAppointments = useQuery({ queryKey: ['my-appointments'], queryFn: fetchMyAppointments, enabled: user?.role === 'CLIENT' })
  const professionalAppointments = useQuery({ queryKey: ['professional-appointments'], queryFn: fetchMyAppointmentsAsProfessional, enabled: user?.role === 'PROFESSIONAL' })

  if (user?.role === 'SYSTEM_ADMIN') return <SystemAdministratorHome name={user.name} />
  if (user?.role === 'CLIENT') return <ClientHome name={user.name} appointments={clientAppointments.data ?? []} isLoading={clientAppointments.isLoading} />
  if (user?.role === 'PROFESSIONAL') return <ProfessionalHome name={user.name} establishmentName={user.establishmentName} appointments={professionalAppointments.data ?? []} isLoading={professionalAppointments.isLoading} />

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

      <section className="surface-card mt-7 grid overflow-hidden sm:grid-cols-3">
        <MetricLine icon={CalendarCheck} label="Concluídos hoje" helper="serviços finalizados" value={dashboard.isLoading ? '—' : String(dashboard.data?.todaysCompletedServices ?? 0)} />
        <MetricLine icon={Wallet} label="Comissões no mês" helper="valor acumulado" value={dashboard.isLoading ? '—' : formatMoney(dashboard.data?.monthlyCommissionTotal ?? 0)} bordered />
        <MetricLine icon={UsersRound} label="Equipe ativa" helper="profissionais disponíveis" value={dashboard.isLoading ? '—' : String(dashboard.data?.activeEmployees ?? 0)} bordered />
      </section>

      <section className="mt-5">
        <div className="relative overflow-hidden rounded-[1rem] bg-secondary-700 p-6 text-white shadow-[var(--shadow-lifted)] sm:p-8">
          <div className="absolute right-0 top-0 h-full w-[5px] bg-primary-500" aria-hidden="true" />
          <div className="max-w-2xl">
            <p className="text-[0.64rem] font-extrabold uppercase tracking-[0.18em] text-primary-300">Operação agora</p>
            <h2 className="mt-4 max-w-xl text-[1.8rem] font-extrabold leading-[1.08] tracking-[-0.04em] text-white sm:text-[2.25rem]">
              Menos painel. Mais trabalho resolvido.
            </h2>
            <p className="mt-3 max-w-xl text-sm leading-6 text-[#c3cde0]">As três áreas que mais mudam durante o dia ficam aqui, sem transformar a home num mural de cartões.</p>
          </div>
          <div className="mt-7 grid border-y border-white/10 sm:grid-cols-3">
            <QuickAction to="/agenda" icon={CalendarDays} label="Abrir agenda" />
            <QuickAction to="/funcionarios" icon={UsersRound} label="Gerenciar equipe" bordered />
            <QuickAction to="/servicos" icon={Scissors} label="Editar serviços" bordered />
          </div>
        </div>

      </section>

      <section className="surface-card mt-5 divide-y divide-background-300 overflow-hidden">
        <ActionRow to="/relatorio-atendimentos" icon={CalendarCheck} eyebrow="Operação" title="Acompanhar atendimentos" description="Histórico da equipe, períodos e serviços concluídos." />
        <ActionRow to="/relatorio-comissoes" icon={Wallet} eyebrow="Financeiro" title="Consultar comissões" description="Valores gerados por período e por profissional." />
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
    <RoleHomeHeader eyebrow="Área do cliente" name={name} subtitle="Seus próximos horários, sem complicação.">
      <div className="mt-8 grid gap-5 lg:grid-cols-[minmax(0,1.45fr)_minmax(18rem,.55fr)]">
        <section className="relative overflow-hidden rounded-[1rem] bg-secondary-700 p-6 text-white shadow-[var(--shadow-lifted)] sm:p-8">
          <div className="absolute left-0 top-0 h-full w-[5px] bg-primary-500" />
          <p className="text-[0.62rem] font-extrabold uppercase tracking-[0.18em] text-primary-300">Próximo compromisso</p>
          {isLoading ? <p className="mt-6 text-[#c3cde0]">Carregando sua agenda...</p> : next ? (
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

function ProfessionalHome({ name, establishmentName, appointments, isLoading }: { name: string; establishmentName: string | null; appointments: Appointment[]; isLoading: boolean }) {
  const [now] = useState(() => Date.now())
  const today = localDateKey(new Date(now))
  const todaysAppointments = appointments.filter((appointment) => appointment.status === 'CONFIRMED' && appointment.startsAt.slice(0, 10) === today).sort((first, second) => first.startsAt.localeCompare(second.startsAt))
  const next = todaysAppointments.find((appointment) => new Date(appointment.endsAt).getTime() > now)

  return (
    <RoleHomeHeader eyebrow="Área do profissional" name={name} subtitle={`Seu dia${establishmentName ? ` no ${establishmentName}` : ''}, direto ao ponto.`}>
      <section className="surface-card mt-8 grid overflow-hidden md:grid-cols-3">
        <MetricLine icon={CalendarCheck} label="Atendimentos hoje" helper="confirmados na agenda" value={isLoading ? '—' : String(todaysAppointments.length)} />
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

function QuickAction({ to, icon: Icon, label, bordered = false }: { to: string; icon: ComponentType<{ size?: number; className?: string }>; label: string; bordered?: boolean }) {
  return (
    <Link to={to} className={`group flex items-center justify-between gap-3 px-1 py-4 text-sm font-bold text-white sm:px-4 ${bordered ? 'border-t border-white/10 sm:border-l sm:border-t-0' : ''}`}>
      <span className="flex items-center gap-2.5"><Icon size={17} className="text-primary-300" /> {label}</span>
      <ArrowUpRight size={15} className="text-white/50 transition group-hover:-translate-y-0.5 group-hover:translate-x-0.5 group-hover:text-white" />
    </Link>
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
function formatToday() { return new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: '2-digit', month: 'long' }).format(new Date()) }
function formatAppointmentDate(value: string) { return new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: '2-digit', month: 'long' }).format(new Date(value)) }
function localDateKey(date: Date) { const year = date.getFullYear(); const month = String(date.getMonth() + 1).padStart(2, '0'); const day = String(date.getDate()).padStart(2, '0'); return `${year}-${month}-${day}` }
