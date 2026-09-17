import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Modal } from '@/components/ui/Modal'
import { getApiErrorMessage } from '@/lib/apiError'
import { invalidateAppointment, type Appointment } from '@/features/appointments/appointmentsApi'

interface InvalidateAppointmentModalProps {
  appointment: Appointment
  onInvalidated: () => void
  onClose: () => void
}

/**
 * Modal dedicado para invalidar um atendimento já concluído.
 *
 * Diferente do ConfirmDialog (que só suporta digitar um texto para
 * confirmar), aqui o admin precisa registrar o motivo da invalidação —
 * exigido pelo backend e mantido no histórico para auditoria.
 */
export function InvalidateAppointmentModal({ appointment, onInvalidated, onClose }: InvalidateAppointmentModalProps) {
  const [reason, setReason] = useState('')
  const invalidateMutation = useMutation({
    mutationFn: () => invalidateAppointment(appointment.id, reason.trim()),
    onSuccess: onInvalidated,
  })
  const isReasonValid = reason.trim().length > 0

  return (
    <Modal title="Invalidar atendimento" onClose={onClose}>
      <p className="text-sm text-brand-700">
        Esta ação anula o atendimento de <strong>{appointment.serviceName}</strong> com {appointment.clientName}, já
        concluído, e remove a comissão correspondente do cálculo do mês. Descreva o motivo para manter o histórico.
      </p>

      <div className="mt-4">
        <label className="text-sm font-medium text-brand-800" htmlFor="invalidation-reason">
          Motivo da invalidação
        </label>
        <textarea
          id="invalidation-reason"
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          autoFocus
          rows={3}
          maxLength={500}
          placeholder="Ex.: serviço não foi realmente prestado, lançamento em duplicidade..."
          className="mt-2 w-full rounded-lg border border-brand-200 px-3 py-2 text-sm text-brand-900 focus:border-brand-500 focus:outline-none"
        />
      </div>

      {invalidateMutation.isError && (
        <p role="alert" className="mt-3 text-sm text-danger-600">
          {getApiErrorMessage(invalidateMutation.error, 'Não foi possível invalidar o atendimento.')}
        </p>
      )}

      <div className="mt-6 flex justify-end gap-3">
        <button
          type="button"
          onClick={onClose}
          className="rounded-lg border border-brand-200 px-4 py-2 text-sm font-medium text-brand-700 hover:bg-sand-100"
        >
          Cancelar
        </button>
        <button
          type="button"
          onClick={() => invalidateMutation.mutate()}
          disabled={!isReasonValid || invalidateMutation.isPending}
          className="rounded-lg bg-danger-600 px-4 py-2 text-sm font-medium text-white hover:bg-danger-500 disabled:opacity-60"
        >
          {invalidateMutation.isPending ? 'Aguarde...' : 'Invalidar atendimento'}
        </button>
      </div>
    </Modal>
  )
}
