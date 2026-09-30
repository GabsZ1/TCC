import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle, ChevronDown, ChevronUp } from 'lucide-react'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { getApiErrorMessage } from '@/lib/apiError'
import { formatDateTime } from '@/lib/format'
import { PENDING_CLOSURE_KEY, completeAppointment, fetchPendingClosure, markAppointmentNoShow, type Appointment } from '@/features/appointments/appointmentsApi'

type Action = { appointment: Appointment; kind: 'complete' | 'no-show' }

/**
 * Destaque para atendimentos que já terminaram e continuam "Confirmado": ninguém concluiu nem
 * registrou a falta. Não muda nada sozinho; só lembra quem opera a agenda de fechar o atendimento.
 */
export function PendingClosurePanel({ showProfessional, onChanged }: { showProfessional: boolean; onChanged?: () => void }) {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [page, setPage] = useState(0)
  const [action, setAction] = useState<Action | null>(null)
  const pending = useQuery({ queryKey: [...PENDING_CLOSURE_KEY, page], queryFn: () => fetchPendingClosure(page), refetchInterval: 60_000 })
  const mutation = useMutation({
    mutationFn: ({ appointment, kind }: Action) => kind === 'complete' ? completeAppointment(appointment.id) : markAppointmentNoShow(appointment.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: PENDING_CLOSURE_KEY })
      setPage(0)
      onChanged?.()
      setAction(null)
    },
  })

  const items = pending.data?.content ?? []
  if (pending.data?.totalElements === 0) return null

  return (
    <section className="mb-6 rounded-2xl border border-warning-300 bg-warning-50 p-4 sm:p-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-start gap-3">
          <AlertTriangle size={20} className="mt-0.5 shrink-0 text-warning-700" />
          <div>
            <p className="font-semibold text-text-900">{pending.data?.totalElements ?? '—'} atendimento(s) aguardando fechamento</p>
            <p className="mt-0.5 text-sm text-text-700">Já terminaram e continuam como confirmados. Conclua ou registre a falta do cliente.</p>
          </div>
        </div>
        <button type="button" onClick={() => setOpen((value) => !value)} aria-expanded={open} className="inline-flex items-center gap-1.5 rounded-lg border border-warning-300 bg-background-50 px-3 py-1.5 text-sm font-semibold text-warning-700 hover:bg-warning-100">
          {open ? <ChevronUp size={16} /> : <ChevronDown size={16} />}{open ? 'Ocultar' : 'Ver atendimentos'}
        </button>
      </div>
      {open && (
        <ul className="mt-4 divide-y divide-warning-300/60 overflow-hidden rounded-xl border border-warning-300/60 bg-background-50">
          {items.map((appointment) => (
            <li key={appointment.id} className="flex flex-wrap items-center justify-between gap-3 px-4 py-3 text-sm">
              <div>
                <p className="font-semibold text-text-900">{appointment.serviceName} · {appointment.clientName}</p>
                <p className="text-text-600">{formatDateTime(appointment.startsAt)}{showProfessional ? ` · ${appointment.professionalName}` : ''}</p>
              </div>
              <div className="flex gap-2">
                <button type="button" onClick={() => setAction({ appointment, kind: 'complete' })} className="rounded-lg border border-success-300 px-3 py-1.5 text-xs font-semibold text-success-700 hover:bg-success-50">Concluir</button>
                <button type="button" onClick={() => setAction({ appointment, kind: 'no-show' })} className="rounded-lg border border-warning-300 px-3 py-1.5 text-xs font-semibold text-warning-700 hover:bg-warning-50">Não compareceu</button>
              </div>
            </li>
          ))}
        </ul>
      )}
      {open && pending.data && pending.data.totalPages > 1 && <div className="mt-3 flex justify-between text-sm"><button type="button" disabled={page === 0} onClick={() => setPage(page - 1)}>Anterior</button><span>Página {page + 1} de {pending.data.totalPages}</span><button type="button" disabled={page + 1 >= pending.data.totalPages} onClick={() => setPage(page + 1)}>Próxima</button></div>}
      {action && (
        <ConfirmDialog
          title={action.kind === 'complete' ? 'Concluir atendimento' : 'Registrar falta'}
          message={action.kind === 'complete'
            ? `Confirmar a conclusão de ${action.appointment.serviceName} de ${action.appointment.clientName}? Essa ação não pode ser desfeita.`
            : `Confirmar que ${action.appointment.clientName} não compareceu? Essa ação não pode ser desfeita.`}
          confirmLabel={action.kind === 'complete' ? 'Concluir atendimento' : 'Registrar falta'}
          isLoading={mutation.isPending}
          errorMessage={mutation.isError ? getApiErrorMessage(mutation.error, 'Não foi possível atualizar o atendimento.') : null}
          onConfirm={() => mutation.mutate(action)}
          onCancel={() => { mutation.reset(); setAction(null) }}
        />
      )}
    </section>
  )
}
