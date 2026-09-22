import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowRight, CalendarPlus, Clock3, Eye, MapPin, Scissors, UserRound } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { AppointmentEditModal } from '@/features/appointments/AppointmentEditModal'
import { EstablishmentDetailsModal } from '@/features/appointments/EstablishmentDetailsModal'
import { cancelAppointment, fetchActiveEstablishments, fetchMyAppointments, type ActiveEstablishment, type Appointment } from '@/features/appointments/appointmentsApi'
import { getApiErrorMessage } from '@/lib/apiError'

const QUERY_KEY = ['my-appointments']
type AppointmentSection = 'upcoming' | 'history'

export function AppointmentsPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [section, setSection] = useState<AppointmentSection>('upcoming')
  const [details, setDetails] = useState<ActiveEstablishment | null>(null)
  const [cancelling, setCancelling] = useState<Appointment | null>(null)
  const [editing, setEditing] = useState<Appointment | null>(null)
  const [cancellationError, setCancellationError] = useState<string | null>(null)
  const appointments = useQuery({ queryKey: QUERY_KEY, queryFn: fetchMyAppointments })
  const establishments = useQuery({ queryKey: ['active-establishments'], queryFn: fetchActiveEstablishments })
  const upcomingAppointments = (appointments.data ?? []).filter(isUpcoming)
  const historyAppointments = (appointments.data ?? []).filter((appointment) => !isUpcoming(appointment))
  const displayedAppointments = section === 'upcoming' ? upcomingAppointments : historyAppointments

  const cancelMutation = useMutation({
    mutationFn: cancelAppointment,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEY })
      setCancelling(null)
      setSection('history')
      setCancellationError(null)
    },
    onError: (error) => setCancellationError(getApiErrorMessage(error, 'Não foi possível cancelar o agendamento.')),
  })

  return (
    <div className="page-shell">
      <div className="grid gap-7 xl:grid-cols-[minmax(0,1fr)_22rem]">
        <section>
          <p className="eyebrow">Sua agenda</p>
          <h1 className="page-title mt-2">Meus agendamentos</h1>
          <p className="page-subtitle">Acompanhe seus próximos horários e consulte o histórico quando precisar.</p>

          {cancellationError && <p className="mt-5 rounded-xl border border-danger-500/20 bg-danger-50 px-4 py-3 text-sm text-danger-600">{cancellationError}</p>}
          <Tabs section={section} onChange={setSection} upcomingCount={upcomingAppointments.length} />

          <div className="mt-6 space-y-3">
            {appointments.isLoading && <EmptyState>Carregando agendamentos...</EmptyState>}
            {appointments.isError && <EmptyState error>{getApiErrorMessage(appointments.error, 'Não foi possível carregar seus agendamentos.')}</EmptyState>}
            {!appointments.isLoading && !appointments.isError && displayedAppointments.length === 0 && <EmptyState>{section === 'upcoming' ? 'Você não possui próximos agendamentos.' : 'Você ainda não possui agendamentos no histórico.'}</EmptyState>}
            {displayedAppointments.map((appointment) => (
              <article key={appointment.id} className="surface-card overflow-hidden">
                <div className="grid gap-0 md:grid-cols-[7.5rem_minmax(0,1fr)_auto]">
                  <div className="flex flex-row items-center gap-3 border-b border-background-300 bg-background-100/70 px-5 py-4 md:flex-col md:items-start md:justify-center md:border-b-0 md:border-r md:px-5">
                    <span className="text-[0.65rem] font-bold uppercase tracking-[0.12em] text-text-400">{weekday(appointment.startsAt)}</span>
                    <div className="flex items-baseline gap-1 md:block">
                      <p className="font-[var(--font-display)] text-3xl font-semibold leading-none tracking-[-0.04em] text-text-950">{dayNumber(appointment.startsAt)}</p>
                      <p className="mt-1 text-sm font-semibold text-text-600">{monthShort(appointment.startsAt)}</p>
                    </div>
                    <p className="ml-auto flex items-center gap-1 text-sm font-semibold text-primary-700 md:ml-0 md:mt-2"><Clock3 size={14} /> {formatTime(appointment.startsAt)}</p>
                  </div>

                  <div className="p-5">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <div className="flex items-center gap-2"><Scissors size={17} className="text-primary-600" /><h2 className="text-lg font-semibold tracking-[-0.02em] text-text-950">{appointment.serviceName}</h2></div>
                        <p className="mt-2 flex items-center gap-2 text-sm text-text-600"><MapPin size={15} /> {appointment.establishmentName}</p>
                        <p className="mt-1 flex items-center gap-2 text-sm text-text-600"><UserRound size={15} /> {appointment.professionalName}</p>
                      </div>
                      <StatusBadge status={appointment.status} />
                    </div>
                    <div className="mt-4 flex flex-wrap gap-x-5 gap-y-2 text-sm">
                      <span className="font-semibold text-text-900">{formatMoney(appointment.agreedPrice)}</span>
                      <span className="text-text-500">{appointment.durationMinutes} min</span>
                      <span className="text-text-500">até {formatTime(appointment.endsAt)}</span>
                    </div>
                    {appointment.status === 'INVALIDATED' && appointment.invalidationReason && <p className="mt-3 rounded-lg bg-warning-50 px-3 py-2 text-xs leading-5 text-warning-700">Motivo: {appointment.invalidationReason}</p>}
                  </div>

                  {appointment.status === 'CONFIRMED' && isFuture(appointment.startsAt) && (
                    <div className="flex items-center gap-2 border-t border-background-300 px-5 py-4 md:flex-col md:justify-center md:border-l md:border-t-0">
                      <button type="button" onClick={() => setEditing(appointment)} className="secondary-button w-full whitespace-nowrap">Editar</button>
                      <button type="button" onClick={() => { setCancellationError(null); setCancelling(appointment) }} className="inline-flex w-full items-center justify-center rounded-xl px-4 py-2.5 text-sm font-semibold text-danger-600 hover:bg-danger-50">Cancelar</button>
                    </div>
                  )}
                </div>
              </article>
            ))}
          </div>
        </section>

        <aside className="h-fit overflow-hidden rounded-[1.35rem] bg-primary-900 text-white shadow-[var(--shadow-lifted)] xl:sticky xl:top-7">
          <div className="relative p-6">
            <div className="pointer-events-none absolute -right-12 -top-12 size-40 rounded-full border border-white/10" />
            <span className="grid size-11 place-items-center rounded-xl bg-white/10 text-primary-200"><CalendarPlus size={20} /></span>
            <h2 className="mt-5 font-[var(--font-display)] text-2xl font-semibold tracking-[-0.03em]">Marcar um horário</h2>
            <p className="mt-2 text-sm leading-6 text-primary-100">Conheça cada estabelecimento ou vá direto para o agendamento.</p>
            <div className="mt-6 space-y-3">
              {establishments.isLoading && <p className="rounded-xl bg-white/10 px-4 py-3 text-sm text-primary-100">Carregando estabelecimentos...</p>}
              {establishments.isError && <p className="mt-3 text-sm text-danger-700">{getApiErrorMessage(establishments.error, 'Não foi possível carregar os estabelecimentos.')}</p>}
              {!establishments.isLoading && !establishments.isError && establishments.data?.length === 0 && <p className="mt-3 text-sm text-primary-100">Nenhum estabelecimento está disponível no momento.</p>}
              {establishments.data?.map((establishment) => (
                <article key={establishment.id} className="rounded-xl border border-white/15 bg-white/10 p-3.5">
                  <p className="font-semibold text-white">{establishment.name}</p>
                  <div className="mt-3 grid grid-cols-2 gap-2">
                    <button type="button" onClick={() => setDetails(establishment)} className="inline-flex items-center justify-center gap-1.5 rounded-lg border border-white/20 px-3 py-2 text-xs font-semibold text-white hover:bg-white/10"><Eye size={14} /> Detalhes</button>
                    <button type="button" onClick={() => navigate(`/estabelecimentos/${establishment.id}/agendar`)} className="inline-flex items-center justify-center gap-1.5 rounded-lg bg-white px-3 py-2 text-xs font-semibold text-primary-900 hover:bg-primary-50">Agendar <ArrowRight size={14} /></button>
                  </div>
                </article>
              ))}
            </div>
          </div>
        </aside>
      </div>

      {cancelling && <ConfirmDialog title="Cancelar agendamento" message={`Deseja cancelar o agendamento de ${cancelling.serviceName} em ${formatDate(cancelling.startsAt)}?`} confirmLabel="Cancelar agendamento" isLoading={cancelMutation.isPending} onConfirm={() => cancelMutation.mutate(cancelling.id)} onCancel={() => setCancelling(null)} />}
      {editing && <AppointmentEditModal appointment={editing} onUpdated={() => { queryClient.invalidateQueries({ queryKey: QUERY_KEY }); setEditing(null) }} onClose={() => setEditing(null)} />}
      {details && <EstablishmentDetailsModal establishmentId={details.id} establishmentName={details.name} onClose={() => setDetails(null)} />}
    </div>
  )
}

function Tabs({ section, onChange, upcomingCount }: { section: AppointmentSection; onChange: (section: AppointmentSection) => void; upcomingCount: number }) {
  return <div className="mt-6 inline-flex rounded-xl border border-background-300 bg-background-50 p-1 shadow-sm"><TabButton active={section === 'upcoming'} onClick={() => onChange('upcoming')}>Próximos <span className="ml-1 opacity-60">{upcomingCount}</span></TabButton><TabButton active={section === 'history'} onClick={() => onChange('history')}>Histórico</TabButton></div>
}
function TabButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) { return <button type="button" onClick={onClick} className={`rounded-lg px-3.5 py-2 text-sm font-semibold ${active ? 'bg-primary-100 text-primary-800' : 'text-text-500 hover:text-text-800'}`}>{children}</button> }
function StatusBadge({ status }: { status: Appointment['status'] }) {
  const labels: Record<Appointment['status'], string> = { CONFIRMED: 'Confirmado', CANCELLED: 'Cancelado', COMPLETED: 'Concluído', INVALIDATED: 'Invalidado' }
  const styles: Record<Appointment['status'], string> = { CONFIRMED: 'bg-primary-100 text-primary-800', CANCELLED: 'bg-danger-50 text-danger-600', COMPLETED: 'bg-success-50 text-success-700', INVALIDATED: 'bg-warning-100 text-warning-700' }
  return <span className={`status-pill ${styles[status]}`}>{labels[status]}</span>
}
function isFuture(value: string) { return new Date(value).getTime() > Date.now() }
function isUpcoming(appointment: Appointment) { return appointment.status === 'CONFIRMED' && isFuture(appointment.startsAt) }
function EmptyState({ children, error = false }: { children: React.ReactNode; error?: boolean }) { return <div className={`surface-card p-10 text-center text-sm ${error ? 'text-danger-600' : 'text-text-500'}`}>{children}</div> }
function formatDate(value: string) { return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long' }).format(new Date(value)) }
function formatTime(value: string) { return value.slice(11, 16) }
function formatMoney(value: number) { return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value) }
function weekday(value: string) { return new Intl.DateTimeFormat('pt-BR', { weekday: 'short' }).format(new Date(value)).replace('.', '') }
function dayNumber(value: string) { return new Intl.DateTimeFormat('pt-BR', { day: '2-digit' }).format(new Date(value)) }
function monthShort(value: string) { return new Intl.DateTimeFormat('pt-BR', { month: 'short' }).format(new Date(value)).replace('.', '') }
