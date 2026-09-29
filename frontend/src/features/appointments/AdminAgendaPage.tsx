import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarDays, Clock3, LockKeyhole, Scissors, User } from 'lucide-react'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { InvalidateAppointmentModal } from '@/features/appointments/InvalidateAppointmentModal'
import { AdminCancelAppointmentModal } from '@/features/appointments/AdminCancelAppointmentModal'
import { PENDING_CLOSURE_KEY, completeAppointment, markAppointmentNoShow, fetchEstablishmentAgenda, fetchTeamAvailability, type Appointment } from '@/features/appointments/appointmentsApi'
import { fetchEmployees } from '@/features/employees/services/employeesApi'
import { getApiErrorMessage } from '@/lib/apiError'
import { canMarkNoShow } from '@/features/appointments/noShowPolicy'
import { PendingClosurePanel } from '@/features/appointments/PendingClosurePanel'

type StatusFilter = '' | Appointment['status']

const AGENDA_QUERY_KEY = 'establishment-agenda'

export function AdminAgendaPage() {
  const queryClient = useQueryClient()
  const [date, setDate] = useState(today())
  const [professionalId, setProfessionalId] = useState('')
  const [status, setStatus] = useState<StatusFilter>('')
  const [invalidating, setInvalidating] = useState<Appointment | null>(null)
  const [cancelling, setCancelling] = useState<Appointment | null>(null)
  const [completing, setCompleting] = useState<Appointment | null>(null)
  const [noShowing, setNoShowing] = useState<Appointment | null>(null)
  const employees = useQuery({ queryKey: ['employees'], queryFn: fetchEmployees })
  const agenda = useQuery({
    queryKey: [AGENDA_QUERY_KEY, date, professionalId, status],
    queryFn: () => fetchEstablishmentAgenda({
      date: date || undefined,
      professionalId: professionalId ? Number(professionalId) : undefined,
      status: status || undefined,
    }),
    refetchInterval: 30_000,
  })
  const teamAvailability = useQuery({
    queryKey: ['team-availability', date, professionalId],
    queryFn: () => fetchTeamAvailability(date, professionalId ? Number(professionalId) : undefined),
    enabled: Boolean(date),
    refetchInterval: 30_000,
  })

  function refreshAgenda() {
    queryClient.invalidateQueries({ queryKey: [AGENDA_QUERY_KEY] })
    queryClient.invalidateQueries({ queryKey: PENDING_CLOSURE_KEY })
  }

  function handleInvalidated() {
    refreshAgenda()
    setInvalidating(null)
  }

  return (
    <div className="page-shell">
      <header className="border-b border-background-300 pb-7">
        <p className="eyebrow">Operação</p>
        <h1 className="page-title mt-3">Agenda do estabelecimento</h1>
        <p className="page-subtitle">Consulte os atendimentos de toda a equipe e filtre por data, profissional ou situação.</p>
      </header>

      <div className="mt-6"><PendingClosurePanel showProfessional onChanged={refreshAgenda} /></div>

      <section className="surface-card grid gap-4 p-5 sm:grid-cols-3">
        <Filter label="Data"><input type="date" value={date} onChange={(event) => setDate(event.target.value)} className="filter-input" /></Filter>
        <Filter label="Profissional"><select value={professionalId} onChange={(event) => setProfessionalId(event.target.value)} className="filter-input"><option value="">Todos</option>{employees.data?.map((employee) => <option key={employee.id} value={employee.id}>{employee.name}{employee.active ? '' : ' (inativo)'}</option>)}</select></Filter>
        <Filter label="Situação"><select value={status} onChange={(event) => setStatus(event.target.value as StatusFilter)} className="filter-input"><option value="">Todas</option><option value="CONFIRMED">Confirmado</option><option value="COMPLETED">Concluído</option><option value="CANCELLED">Cancelado</option><option value="NO_SHOW">Não compareceu</option><option value="INVALIDATED">Invalidado</option></select></Filter>
        <button type="button" onClick={() => { setDate(''); setProfessionalId(''); setStatus('') }} className="text-left text-sm font-semibold text-text-600 hover:text-text-800 sm:col-span-3">Limpar filtros</button>
      </section>

      {date && <section className="surface-card mt-6 p-5">
        <div className="flex items-center gap-2"><Clock3 size={18} className="text-text-600" /><h2 className="font-semibold text-text-900">Jornada e indisponibilidades</h2></div>
        <p className="mt-1 text-sm text-text-600">Compromissos pessoais aparecem apenas como indisponíveis; os detalhes permanecem privados.</p>
        {teamAvailability.isLoading && <p className="mt-4 text-sm text-text-600">Carregando disponibilidade da equipe...</p>}
        {teamAvailability.isError && <p role="alert" className="mt-4 text-sm text-danger-600">{getApiErrorMessage(teamAvailability.error, 'Não foi possível carregar a disponibilidade da equipe.')}</p>}
        <div className="mt-4 grid gap-3 lg:grid-cols-2">
          {teamAvailability.data?.map((professional) => <div key={professional.professionalId} className="rounded-xl bg-background-50 p-4">
            <div className="flex flex-wrap items-center justify-between gap-2"><strong className="text-sm text-text-900">{professional.professionalName}</strong><span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${professional.working ? 'bg-success-50 text-success-700' : 'bg-background-200 text-text-600'}`}>{professional.working ? `${formatTimeOnly(professional.openingTime)} às ${formatTimeOnly(professional.closingTime)}` : 'Não trabalha neste dia'}</span></div>
            {professional.blocks.length === 0 ? <p className="mt-3 text-xs text-text-600">Sem bloqueios pessoais.</p> : <div className="mt-3 space-y-2">{professional.blocks.map((block) => <p key={block.id} className="flex items-center gap-2 rounded-lg bg-background-50 px-3 py-2 text-xs text-text-700"><LockKeyhole size={14} /> Indisponível · {block.allDay ? 'Dia inteiro' : `${formatTime(block.startsAt)} às ${formatTime(block.endsAt)}`}</p>)}</div>}
          </div>)}
        </div>
      </section>}

      <div className="mt-6 space-y-4">
        {agenda.isLoading && <StateCard>Carregando agenda...</StateCard>}
        {agenda.isError && <StateCard error>{getApiErrorMessage(agenda.error, 'Não foi possível carregar a agenda.')}</StateCard>}
        {!agenda.isLoading && !agenda.isError && agenda.data?.length === 0 && <StateCard>Nenhum atendimento encontrado para os filtros selecionados.</StateCard>}
        {agenda.data?.map((appointment) => (
          <AppointmentCard
            key={appointment.id}
            appointment={appointment}
            onComplete={() => setCompleting(appointment)}
            onNoShow={() => setNoShowing(appointment)}
            onInvalidate={() => setInvalidating(appointment)}
            onCancel={() => setCancelling(appointment)}
          />
        ))}
      </div>

      {completing && (
        <CompleteAppointmentDialog
          appointment={completing}
          onClose={() => setCompleting(null)}
          onCompleted={() => { refreshAgenda(); setCompleting(null) }}
        />
      )}
      {noShowing && <NoShowDialog appointment={noShowing} onClose={() => setNoShowing(null)}
        onMarked={() => { refreshAgenda(); setNoShowing(null) }} />}
      {cancelling && (
        <AdminCancelAppointmentModal
          appointment={cancelling}
          onCancelled={() => { refreshAgenda(); setCancelling(null) }}
          onClose={() => setCancelling(null)}
        />
      )}
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

function CompleteAppointmentDialog({
  appointment,
  onClose,
  onCompleted,
}: {
  appointment: Appointment
  onClose: () => void
  onCompleted: () => void
}) {
  const mutation = useMutation({ mutationFn: () => completeAppointment(appointment.id), onSuccess: onCompleted })
  return <ConfirmDialog
    title="Concluir atendimento"
    message={`Confirmar a conclusão do atendimento de ${appointment.clientName} com ${appointment.professionalName}? Essa ação não pode ser desfeita.`}
    confirmLabel={mutation.isPending ? 'Concluindo...' : 'Concluir atendimento'}
    isLoading={mutation.isPending}
    errorMessage={mutation.isError ? getApiErrorMessage(mutation.error, 'Não foi possível concluir o atendimento.') : null}
    onConfirm={() => mutation.mutate()}
    onCancel={onClose}
  />
}

function NoShowDialog({ appointment, onClose, onMarked }: { appointment: Appointment; onClose: () => void; onMarked: () => void }) {
  const mutation = useMutation({ mutationFn: () => markAppointmentNoShow(appointment.id), onSuccess: onMarked })
  return <ConfirmDialog title="Registrar falta"
    message={`Confirmar que ${appointment.clientName} não compareceu ao atendimento?`}
    confirmLabel={mutation.isPending ? 'Registrando...' : 'Registrar falta'} isLoading={mutation.isPending}
    errorMessage={mutation.isError ? getApiErrorMessage(mutation.error, 'Não foi possível registrar a falta.') : null}
    onConfirm={() => mutation.mutate()} onCancel={onClose} />
}

function AppointmentCard({
  appointment,
  onComplete,
  onNoShow,
  onInvalidate,
  onCancel,
}: {
  appointment: Appointment
  onComplete: () => void
  onNoShow: () => void
  onInvalidate: () => void
  onCancel: () => void
}) {
  const canComplete = appointment.status === 'CONFIRMED' && hasStarted(appointment)
  const canNoShow = appointment.status === 'CONFIRMED' && canMarkNoShow(appointment.startsAt)
  const canInvalidate = appointment.status === 'COMPLETED'
  const canCancel = appointment.status === 'CONFIRMED'

  return (
    <article className="surface-card p-5">
      <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <div className="flex items-center gap-2"><Scissors size={18} className="text-text-600" /><h2 className="text-lg font-semibold text-text-900">{appointment.serviceName}</h2></div>
          <p className="mt-2 flex items-center gap-2 text-sm text-text-600"><User size={16} /> Cliente: {appointment.clientName}</p>
          <p className="mt-1 text-sm text-text-600">Profissional: {appointment.professionalName}</p>
          {appointment.status === 'CANCELLED' && appointment.cancellationReason && (
            <p className="mt-2 max-w-sm text-sm text-danger-600">
              Motivo: {appointment.cancellationReason}
              {appointment.cancelledByUserName && ` · Cancelado por ${appointment.cancelledByUserName}`}
            </p>
          )}
          {appointment.status === 'INVALIDATED' && appointment.invalidationReason && (
            <p className="mt-2 max-w-sm text-sm text-warning-700">Motivo: {appointment.invalidationReason}</p>
          )}
        </div>
        <div className="rounded-xl bg-background-200 px-4 py-3 text-sm text-text-800">
          <p className="flex items-center gap-2 font-semibold"><CalendarDays size={16} /> {formatDate(appointment.startsAt)}</p>
          <p className="mt-1 flex items-center gap-2"><Clock3 size={16} /> {formatTime(appointment.startsAt)} às {formatTime(appointment.endsAt)}</p>
        </div>
        <div className="text-sm lg:text-right">
          <StatusBadge status={appointment.status} />
          <p className="mt-2 font-semibold text-text-900">{formatMoney(appointment.agreedPrice)}</p>
          <p className="text-text-600">Comissão: {formatMoney(appointment.commissionAmount)}</p>
          {(canComplete || canNoShow || canInvalidate || canCancel) && (
            <div className="mt-3 flex flex-wrap justify-end gap-2">
              {canCancel && (
                <button type="button" onClick={onCancel} className="rounded-lg border border-danger-500/30 px-3 py-1.5 text-xs font-semibold text-danger-600 hover:bg-danger-500/5">
                  Cancelar
                </button>
              )}
              {canComplete && (
                <button
                  type="button"
                  onClick={onComplete}
                  className="rounded-lg border border-success-300 px-3 py-1.5 text-xs font-semibold text-success-700 hover:bg-success-50"
                >
                  Concluir
                </button>
              )}
              {canNoShow && <button type="button" onClick={onNoShow} className="rounded-lg border border-warning-300 px-3 py-1.5 text-xs font-semibold text-warning-700 hover:bg-warning-50">Não compareceu</button>}
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

function Filter({ label, children }: { label: string; children: React.ReactNode }) { return <label className="text-sm font-medium text-text-800">{label}{children}</label> }
function StatusBadge({ status }: { status: Appointment['status'] }) {
  const labels: Record<Appointment['status'], string> = { CONFIRMED: 'Confirmado', CANCELLED: 'Cancelado', COMPLETED: 'Concluído', INVALIDATED: 'Invalidado', NO_SHOW: 'Não compareceu' }
  const styles: Record<Appointment['status'], string> = {
    CONFIRMED: 'bg-primary-100 text-text-800',
    CANCELLED: 'bg-danger-500/10 text-danger-600',
    COMPLETED: 'bg-success-50 text-success-700',
    INVALIDATED: 'bg-warning-100 text-warning-700',
    NO_SHOW: 'bg-warning-100 text-warning-700',
  }
  return <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-semibold ${styles[status]}`}>{labels[status]}</span>
}
function StateCard({ children, error = false }: { children: React.ReactNode; error?: boolean }) { return <div className={`rounded-2xl border bg-background-50 p-8 text-center ${error ? 'border-danger-500/30 text-danger-600' : 'border-background-300 text-text-600'}`}>{children}</div> }
function today() { const date = new Date(); const offset = date.getTimezoneOffset(); return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 10) }
function hasStarted(appointment: Appointment) { return new Date(appointment.startsAt).getTime() <= Date.now() }
function formatDate(value: string) { return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long' }).format(new Date(value)) }
function formatTime(value: string) { return value.slice(11, 16) }
function formatTimeOnly(value: string | null) { return value?.slice(0, 5) ?? '--:--' }
function formatMoney(value: number) { return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value) }
