/** Solicita o motivo e confirma o cancelamento feito pela administração. */
import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Modal } from '@/components/ui/Modal'
import { getApiErrorMessage } from '@/lib/apiError'
import { cancelAppointmentAsAdministrator, type Appointment } from '@/features/appointments/api/appointmentsApi'

interface AdminCancelAppointmentModalProps {
  appointment: Appointment
  onCancelled: () => void
  onClose: () => void
}

/** Solicita um motivo antes de confirmar o cancelamento administrativo. */
export function AdminCancelAppointmentModal({ appointment, onCancelled, onClose }: AdminCancelAppointmentModalProps) {
  const [reason, setReason] = useState('')
  const mutation = useMutation({
    mutationFn: () => cancelAppointmentAsAdministrator(appointment.id, reason.trim()),
    onSuccess: onCancelled,
  })
  const valid = reason.trim().length > 0

  return (
    <Modal title="Cancelar agendamento" onClose={onClose}>
      <p className="text-sm text-text-700">
        Cancele o atendimento de <strong>{appointment.serviceName}</strong> com {appointment.clientName}.
        O motivo e seus dados de administrador ficarão registrados no histórico.
      </p>
      <label className="mt-4 block text-sm font-medium text-text-800" htmlFor="admin-cancellation-reason">Motivo do cancelamento</label>
      <textarea id="admin-cancellation-reason" value={reason} onChange={(event) => setReason(event.target.value)} autoFocus rows={3} maxLength={500} placeholder="Ex.: estabelecimento indisponível nesta data..." className="mt-2 w-full rounded-lg border border-background-300 px-3 py-2 text-sm text-text-900 focus:border-primary-500 focus:outline-none" />
      {mutation.isError && <p role="alert" className="mt-3 text-sm text-danger-600">{getApiErrorMessage(mutation.error, 'Não foi possível cancelar o agendamento.')}</p>}
      <div className="mt-6 flex justify-end gap-3">
        <button type="button" onClick={onClose} className="rounded-lg border border-background-300 px-4 py-2 text-sm font-medium text-text-700 hover:bg-background-100">Voltar</button>
        <button type="button" onClick={() => mutation.mutate()} disabled={!valid || mutation.isPending} className="rounded-lg bg-danger-600 px-4 py-2 text-sm font-medium text-on-danger hover:bg-danger-500 disabled:opacity-60">{mutation.isPending ? 'Cancelando...' : 'Cancelar agendamento'}</button>
      </div>
    </Modal>
  )
}
