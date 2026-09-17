import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarDays, Clock3, Scissors, User } from 'lucide-react'
import { InvalidateAppointmentModal } from '@/features/appointments/InvalidateAppointmentModal'
import { completeAppointment, fetchEstablishmentAgenda, type Appointment } from '@/features/appointments/appointmentsApi'
import { fetchEmployees } from '@/features/employees/services/employeesApi'
import { getApiErrorMessage } from '@/lib/apiError'

type StatusFilter = '' | Appointment['status']

const AGENDA_QUERY_KEY = 'establishment-agenda'

export function AdminAgendaPage() {
  const queryClient = useQueryClient()
  const [date, setDate] = useState(today())
  const [professionalId, setProfessionalId] = useState('')
  const [status, setStatus] = useState<StatusFilter>('')
  const [invalidating, setInvalidating] = useState<Appointment | null>(null)
  const employees = useQuery({ queryKey: ['employees'], queryFn: fetchEmployees })
  const agenda = useQuery({
    queryKey: [AGENDA_QUERY_KEY, date, professionalId, status],
    queryFn: () => fetchEstablishmentAgenda({
      date: date || undefined,
      professionalId: professionalId ? Number(professionalId) : undefined,
      status: status || undefined,
    }),
  })

  const completeMutation = useMutation({
    mutationFn: completeAppointment,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: [AGENDA_QUERY_KEY] }),
  })

  function handleInvalidated() {
    queryClient.invalidateQueries({ queryKey: [AGENDA_QUERY_KEY] })
    setInvalidating(null)
  }

  return (
    <div className="p-5 sm:p-7 lg:p-9">
      <h1 className="page-title">Agenda do estabelecimento</h1>
      <p className="page-subtitle">Consulte os atendimentos de toda a equipe e filtre por data, profissional ou situação.</p>

      <section className="mt-6 grid gap-4 rounded-2xl border border-brand-100 bg-white p-5 shadow-sm sm:grid-cols-3">
        <Filter label="Data"><input type="date" value={date} onChange={(event) => setDate(event.target.value)} className="filter-input" /></Filter>
        <Filter label="Profissional"><select value={professionalId} onChange={(event) => setProfessionalId(event.target.value)} className="filter-input"><option value="">Todos</option>{employees.data?.map((employee) => <option key={employee.id} value={employee.id}>{employee.name}{employee.active ? '' : ' (inativo)'}</option>)}</select></Filter>
        <Filter label="Situação"><select value={status} onChange={(event) => setStatus(event.target.value as StatusFilter)} className="filter-input"><option value="">Todas</option><option value="CONFIRMED">Confirmado</option><option value="COMPLETED">Concluído</option><option value="CANCELLED">Cancelado</option><option value="INVALIDATED">Invalidado</option></select></Filter>
        <button type="button" onClick={() => { setDate(''); setProfessionalId(''); setStatus('') }} className="text-left text-sm font-semibold text-brand-600 hover:text-brand-800 sm:col-span-3">Limpar filtros</button>
      </section>

      {completeMutation.isError && (
        <p className="mt-4 rounded-xl border border-danger-500/30 bg-danger-500/5 px-4 py-3 text-sm text-danger-600">
          {getApiErrorMessage(completeMutation.error, 'Não foi possível concluir o atendimento.')}
        </p>
      )}

      <div className="mt-6 space-y-4">
        {agenda.isLoading && <StateCard>Carregando agenda...</StateCard>}
        {agenda.isError && <StateCard error>{getApiErrorMessage(agenda.error, 'Não foi possível carregar a agenda.')}</StateCard>}
        {!agenda.isLoading && !agenda.isError && agenda.data?.length === 0 && <StateCard>Nenhum atendimento encontrado para os filtros selecionados.</StateCard>}
        {agenda.data?.map((appointment) => (
          <AppointmentCard
            key={appointment.id}
            appointment={appointment}
            onComplete={() => completeMutation.mutate(appointment.id)}
            isCompleting={completeMutation.isPending && completeMutation.variables === appointment.id}
            onInvalidate={() => setInvalidating(appointment)}
          />
        ))}
      </div>

      {invalidating && (
        <InvalidateAppointmentModal
          appointment={invalidating}
          onInvalidated={handleInvalidated}
          onClose={() => setInvalidating(null)}
        />
      )}
    </div>
  )
}

function AppointmentCard({
  appointment,
  onComplete,
  isCompleting,
  onInvalidate,
}: {
  appointment: Appointment
  onComplete: () => void
  isCompleting: boolean
  onInvalidate: () => void
}) {
  const canComplete = appointment.status === 'CONFIRMED' && hasStarted(appointment)
  const canInvalidate = appointment.status === 'COMPLETED'

  return (
    <article className="rounded-2xl border border-brand-100 bg-white p-5 shadow-sm">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <div className="flex items-center gap-2"><Scissors size={18} className="text-brand-500" /><h2 className="text-lg font-semibold text-brand-900">{appointment.serviceName}</h2></div>
          <p className="mt-2 flex items-center gap-2 text-sm text-brand-600"><User size={16} /> Cliente: {appointment.clientName}</p>
          <p className="mt-1 text-sm text-brand-600">Profissional: {appointment.professionalName}</p>
          {appointment.status === 'INVALIDATED' && appointment.invalidationReason && (
            <p className="mt-2 max-w-sm text-sm text-amber-700">Motivo: {appointment.invalidationReason}</p>
          )}
        </div>
        <div className="rounded-xl bg-brand-50 px-4 py-3 text-sm text-brand-800">
          <p className="flex items-center gap-2 font-semibold"><CalendarDays size={16} /> {formatDate(appointment.startsAt)}</p>
          <p className="mt-1 flex items-center gap-2"><Clock3 size={16} /> {formatTime(appointment.startsAt)} às {formatTime(appointment.endsAt)}</p>
        </div>
        <div className="text-sm lg:text-right">
          <StatusBadge status={appointment.status} />
          <p className="mt-2 font-semibold text-brand-900">{formatMoney(appointment.agreedPrice)}</p>
          <p className="text-brand-600">Comissão: {formatMoney(appointment.commissionAmount)}</p>
          {(canComplete || canInvalidate) && (
            <div className="mt-3 flex flex-wrap justify-end gap-2">
              {canComplete && (
                <button
                  type="button"
                  onClick={onComplete}
                  disabled={isCompleting}
                  className="rounded-lg border border-sage-300 px-3 py-1.5 text-xs font-semibold text-sage-700 hover:bg-sage-50 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {isCompleting ? 'Concluindo...' : 'Concluir'}
                </button>
              )}
              {canInvalidate && (
                <button
                  type="button"
                  onClick={onInvalidate}
                  className="rounded-lg border border-danger-500/30 px-3 py-1.5 text-xs font-semibold text-danger-600 hover:bg-danger-500/5"
                >
                  Invalidar
                </button>
              )}
            </div>
          )}
        </div>
      </div>
    </article>
  )
}

function Filter({ label, children }: { label: string; children: React.ReactNode }) { return <label className="text-sm font-medium text-brand-800">{label}{children}</label> }
function StatusBadge({ status }: { status: Appointment['status'] }) {
  const labels: Record<Appointment['status'], string> = { CONFIRMED: 'Confirmado', CANCELLED: 'Cancelado', COMPLETED: 'Concluído', INVALIDATED: 'Invalidado' }
  const styles: Record<Appointment['status'], string> = {
    CONFIRMED: 'bg-sage-50 text-sage-600',
    CANCELLED: 'bg-danger-500/10 text-danger-600',
    COMPLETED: 'bg-brand-50 text-brand-700',
    INVALIDATED: 'bg-amber-100 text-amber-700',
  }
  return <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${styles[status]}`}>{labels[status]}</span>
}
function StateCard({ children, error = false }: { children: React.ReactNode; error?: boolean }) { return <div className={`rounded-2xl border bg-white p-8 text-center ${error ? 'border-danger-500/30 text-danger-600' : 'border-brand-100 text-brand-600'}`}>{children}</div> }
function today() { const date = new Date(); const offset = date.getTimezoneOffset(); return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 10) }
function hasStarted(appointment: Appointment) { return new Date(appointment.startsAt).getTime() <= Date.now() }
function formatDate(value: string) { return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long' }).format(new Date(value)) }
function formatTime(value: string) { return value.slice(11, 16) }
function formatMoney(value: number) { return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value) }
