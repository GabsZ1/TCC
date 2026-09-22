import { useState } from 'react'
import { Modal } from '@/components/ui/Modal'

interface ConfirmDialogProps {
  title: string
  message: string
  confirmLabel?: string
  isLoading?: boolean
  onConfirm: () => void
  onCancel: () => void
  /**
   * Quando definido, o botão de confirmação só é liberado depois que o texto
   * digitado no campo bater exatamente com esse valor (ex.: o nome do
   * funcionário). Usado para ações destrutivas e potencialmente
   * irreversíveis, como exige a RNF.01.01 (dupla confirmação).
   */
  confirmationText?: string
  confirmationLabel?: string
  errorMessage?: string | null
}

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
  const isConfirmDisabled =
    isLoading || (requiresTypedConfirmation && typedValue.trim() !== confirmationText)

  return (
    <Modal title={title} onClose={onCancel}>
      <p className="text-sm text-text-700">{message}</p>

      {requiresTypedConfirmation && (
        <div className="mt-4">
          <label className="text-sm font-medium text-text-800">
            {confirmationLabel ?? `Digite "${confirmationText}" para confirmar`}
          </label>
          <input
            type="text"
            value={typedValue}
            onChange={(event) => setTypedValue(event.target.value)}
            autoFocus
            className="mt-2 w-full rounded-lg border border-background-300 px-3 py-2 text-sm text-text-900 focus:border-primary-500 focus:outline-none"
            placeholder={confirmationText}
          />
        </div>
      )}

      {errorMessage && (
        <p role="alert" className="mt-4 rounded-lg border border-danger-500/30 bg-danger-500/5 px-3 py-2 text-sm text-danger-600">
          {errorMessage}
        </p>
      )}

      <div className="mt-6 flex justify-end gap-3">
        <button
          type="button"
          onClick={onCancel}
          className="rounded-lg border border-background-300 px-4 py-2 text-sm font-medium text-text-700 hover:bg-background-100"
        >
          Cancelar
        </button>
        <button
          type="button"
          onClick={onConfirm}
          disabled={isConfirmDisabled}
          className="rounded-lg bg-danger-600 px-4 py-2 text-sm font-medium text-on-danger hover:bg-danger-500 disabled:opacity-60"
        >
          {isLoading ? 'Aguarde...' : confirmLabel}
        </button>
      </div>
    </Modal>
  )
}
