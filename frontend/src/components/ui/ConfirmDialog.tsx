/** Pede confirmação explícita antes de uma ação que altera dados. */
import { useState } from 'react'
import { AlertTriangle } from 'lucide-react'
import { Modal } from '@/components/ui/Modal'

interface ConfirmDialogProps {
  title: string
  message: string
  confirmLabel?: string
  isLoading?: boolean
  onConfirm: () => void
  onCancel: () => void
  confirmationText?: string
  confirmationLabel?: string
  errorMessage?: string | null
}

/** Padroniza a confirmação de ações com consequências sobre dados persistidos. */
export function ConfirmDialog({
  title,
  message,
  confirmLabel = 'Confirmar',
  isLoading = false,
  onConfirm,
  onCancel,
  confirmationText,
  confirmationLabel,
  errorMessage,
}: ConfirmDialogProps) {
  const [typedValue, setTypedValue] = useState('')
  const requiresTypedConfirmation = Boolean(confirmationText)
  const isConfirmDisabled = isLoading || (requiresTypedConfirmation && typedValue.trim() !== confirmationText)

  return (
    <Modal title={title} onClose={onCancel}>
      <div className="flex items-start gap-3 rounded-[0.8rem] border border-warning-500/20 bg-warning-50 p-4">
        <span className="grid size-9 shrink-0 place-items-center rounded-[0.65rem] bg-warning-100 text-warning-700">
          <AlertTriangle size={17} />
        </span>
        <p className="pt-1 text-sm leading-6 text-text-700">{message}</p>
      </div>

      {requiresTypedConfirmation && (
        <div className="mt-5">
          <label className="text-sm font-semibold text-text-800">
            {confirmationLabel ?? `Digite "${confirmationText}" para confirmar`}
          </label>
          <input
            type="text"
            maxLength={confirmationText?.length}
            value={typedValue}
            onChange={(event) => setTypedValue(event.target.value)}
            autoFocus
            className="field-control"
            placeholder={confirmationText}
          />
        </div>
      )}

      {errorMessage && (
        <p role="alert" className="mt-4 rounded-[0.8rem] border border-danger-500/20 bg-danger-50 px-3.5 py-3 text-sm text-danger-600">
          {errorMessage}
        </p>
      )}

      <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
        <button type="button" onClick={onCancel} className="secondary-button">Cancelar</button>
        <button
          type="button"
          onClick={onConfirm}
          disabled={isConfirmDisabled}
          className="inline-flex items-center justify-center rounded-[0.72rem] bg-danger-600 px-4 py-2.5 text-sm font-bold text-on-danger shadow-sm hover:bg-danger-500 disabled:cursor-not-allowed disabled:opacity-55"
        >
          {isLoading ? 'Aguarde...' : confirmLabel}
        </button>
      </div>
    </Modal>
  )
}
