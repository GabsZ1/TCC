import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarDays, Clock3, MapPin, Scissors } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { AppointmentEditModal } from '@/features/appointments/AppointmentEditModal'
import { cancelAppointment, fetchActiveEstablishments, fetchMyAppointments, type Appointment } from '@/features/appointments/appointmentsApi'
import { getApiErrorMessage } from '@/lib/apiError'

const QUERY_KEY = ['my-appointments']

type AppointmentSection = 'upcoming' | 'history'

export function AppointmentsPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [establishmentId, setEstablishmentId] = useState('')
  const [section, setSection] = useState<AppointmentSection>('upcoming')
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

  function startBooking(event: FormEvent) {
    event.preventDefault()
    const id = Number(establishmentId)
    if (Number.isInteger(id) && id > 0) navigate(`/estabelecimentos/${id}/agendar`)
  }

  function requestCancellation(appointment: Appointment) {
    setCancellationError(null)
    setCancelling(appointment)
  }

  return (
    <div className="p-5 sm:p-7 lg:p-9">
      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]">
        <section>
          <h1 className="page-title">Meus agendamentos</h1>
          <p className="page-subtitle">{section === 'upcoming' ? 'Seus próximos horários confirmados aparecem aqui.' : 'Consulte seus agendamentos anteriores e cancelados.'}</p>
          {cancellationError && <p className="mt-4 rounded-xl border border-danger-500/30 bg-danger-500/5 px-4 py-3 text-sm text-danger-600">{cancellationError}</p>}
          <Tabs section={section} onChange={setSection} />

          <div className="mt-7 space-y-4">
            {appointments.isLoading && <EmptyState>Carregando agendamentos...</EmptyState>}
            {appointments.isError && <EmptyState error>{getApiErrorMessage(appointments.error, 'Não foi possível carregar seus agendamentos.')}</EmptyState>}
            {!appointments.isLoading && !appointments.isError && displayedAppointments.length === 0 && <EmptyState>{section === 'upcoming' ? 'Você não possui próximos agendamentos.' : 'Você ainda não possui agendamentos no histórico.'}</EmptyState>}
            {displayedAppointments.map((appointment) => (
              <article key={appointment.id} className="rounded-2xl border border-brand-100 bg-white p-5 shadow-sm">
                <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <div className="flex items-center gap-2"><Scissors size={18} className="text-brand-500" /><h2 className="text-lg font-semibold text-brand-900">{appointment.serviceName}</h2></div>
                    <p className="mt-2 flex items-center gap-2 text-sm text-brand-600"><MapPin size={16} /> {appointment.establishmentName}</p>
                    <p className="mt-1 text-sm text-brand-600">com {appointment.professionalName}</p>
                    <p className="mt-1 text-sm font-semibold text-brand-800">{formatMoney(appointment.agreedPrice)} · {appointment.durationMinutes} min</p>
                  </div>
                  <div className="rounded-xl bg-brand-50 px-4 py-3 text-sm text-brand-800">
                    <p className="flex items-center gap-2 font-semibold"><CalendarDays size={16} /> {formatDate(appointment.startsAt)}</p>
                    <p className="mt-1 flex items-center gap-2"><Clock3 size={16} /> {formatTime(appointment.startsAt)} às {formatTime(appointment.endsAt)}</p>
                  </div>
                  <div>
                    {appointment.status === 'CANCELLED' && <StatusBadge>Cancelado</StatusBadge>}
                    {appointment.status === 'CONFIRMED' && isFuture(appointment.startsAt) && <div className="flex flex-wrap gap-2"><button type="button" onClick={() => setEditing(appointment)} className="rounded-xl border border-brand-200 px-4 py-2 text-sm font-semibold text-brand-700 hover:bg-brand-50">Editar agendamento</button><button type="button" onClick={() => requestCancellation(appointment)} className="rounded-xl border border-danger-500/30 px-4 py-2 text-sm font-semibold text-danger-600 hover:bg-danger-500/5">Cancelar agendamento</button></div>}
                  </div>
                </div>
              </article>
            ))}
          </div>
        </section>

        <aside className="h-fit rounded-2xl border border-brand-200 bg-brand-900 p-6 text-white shadow-sm">
          <h2 className="font-[var(--font-display)] text-2xl">Marcar um horário</h2>
          <p className="mt-2 text-sm leading-6 text-brand-100">Escolha um dos estabelecimentos disponíveis.</p>
          <form onSubmit={startBooking} className="mt-5">
            <label className="text-sm font-medium" htmlFor="establishment">Estabelecimento</label>
            <select id="establishment" required value={establishmentId} onChange={(event) => setEstablishmentId(event.target.value)} disabled={establishments.isLoading || establishments.isError || establishments.data?.length === 0} className="mt-2 w-full rounded-xl border border-white/20 bg-white px-3.5 py-3 text-brand-900 outline-none focus:ring-2 focus:ring-brand-300 disabled:cursor-not-allowed disabled:opacity-70">
              <option value="">{establishments.isLoading ? 'Carregando estabelecimentos...' : 'Selecione um estabelecimento'}</option>
              {establishments.data?.map((establishment) => <option key={establishment.id} value={establishment.id}>{establishment.name}</option>)}
            </select>
            {establishments.isError && <p className="mt-2 text-sm text-danger-500">{getApiErrorMessage(establishments.error, 'Não foi possível carregar os estabelecimentos.')}</p>}
            {!establishments.isLoading && !establishments.isError && establishments.data?.length === 0 && <p className="mt-2 text-sm text-brand-100">Nenhum estabelecimento está disponível no momento.</p>}
            <button disabled={!establishmentId} className="mt-3 w-full rounded-xl bg-white px-4 py-3 font-semibold text-brand-800 hover:bg-brand-50 disabled:cursor-not-allowed disabled:opacity-60">Continuar</button>
          </form>
        </aside>
      </div>
      {cancelling && <ConfirmDialog title="Cancelar agendamento" message={`Deseja cancelar o agendamento de ${cancelling.serviceName} em ${formatDate(cancelling.startsAt)}?`} confirmLabel="Cancelar agendamento" isLoading={cancelMutation.isPending} onConfirm={() => cancelMutation.mutate(cancelling.id)} onCancel={() => setCancelling(null)} />}
      {editing && <AppointmentEditModal appointment={editing} onUpdated={() => { queryClient.invalidateQueries({ queryKey: QUERY_KEY }); setEditing(null) }} onClose={() => setEditing(null)} />}
    </div>
  )
}

function Tabs({ section, onChange }: { section: AppointmentSection; onChange: (section: AppointmentSection) => void }) { return <div className="mt-5 flex gap-2 border-b border-brand-100"><TabButton active={section === 'upcoming'} onClick={() => onChange('upcoming')}>Próximos</TabButton><TabButton active={section === 'history'} onClick={() => onChange('history')}>Histórico</TabButton></div> }
function TabButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) { return <button type="button" onClick={onClick} className={`border-b-2 px-3 py-2 text-sm font-semibold ${active ? 'border-brand-600 text-brand-800' : 'border-transparent text-brand-600 hover:text-brand-800'}`}>{children}</button> }
function StatusBadge({ children }: { children: React.ReactNode }) { return <span className="rounded-full bg-danger-500/10 px-2.5 py-1 text-xs font-semibold text-danger-600">{children}</span> }
function isFuture(value: string) { return new Date(value).getTime() > Date.now() }
function isUpcoming(appointment: Appointment) { return appointment.status === 'CONFIRMED' && isFuture(appointment.startsAt) }
function EmptyState({ children, error = false }: { children: React.ReactNode; error?: boolean }) { return <div className={`rounded-2xl border bg-white p-8 text-center ${error ? 'border-danger-500/30 text-danger-600' : 'border-brand-100 text-brand-600'}`}>{children}</div> }
function formatDate(value: string) { return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long' }).format(new Date(value)) }
function formatTime(value: string) { return value.slice(11, 16) }
function formatMoney(value: number) { return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value) }
