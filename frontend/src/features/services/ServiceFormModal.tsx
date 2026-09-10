import { useState, type FormEvent } from 'react'
import { Modal } from '@/components/ui/Modal'
import type { SalonService, SalonServiceInput } from '@/features/services/servicesApi'

interface ServiceFormModalProps {
  service?: SalonService | null
  isSubmitting: boolean
  errorMessage?: string | null
  onSubmit: (values: SalonServiceInput) => void
  onClose: () => void
}

export function ServiceFormModal({ service, isSubmitting, errorMessage, onSubmit, onClose }: ServiceFormModalProps) {
  const [error, setError] = useState<string | null>(null)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const price = Number(form.get('price'))
    const durationMinutes = Number(form.get('durationMinutes'))
    const commissionText = String(form.get('commissionPercentage') || '')
    const commissionPercentage = commissionText === '' ? 0 : Number(commissionText)

    if (price <= 0 || durationMinutes <= 0 || commissionPercentage < 0 || commissionPercentage > 100) {
      setError('Confira o valor, a duração e a comissão informados.')
      return
    }

    setError(null)
    onSubmit({
      name: String(form.get('name')).trim(),
      description: String(form.get('description') || '').trim(),
      price,
      durationMinutes,
      commissionPercentage,
    })
  }

  return (
    <Modal title={service ? 'Editar serviço' : 'Novo serviço'} onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-4">
        <ServiceField name="name" label="Nome" defaultValue={service?.name} maxLength={100} />
        <ServiceField name="description" label="Descrição (opcional)" defaultValue={service?.description ?? ''} required={false} maxLength={255} />
        <div className="grid grid-cols-2 gap-3">
          <ServiceField name="price" label="Valor (R$)" type="number" min="0.01" step="0.01" defaultValue={service?.price} />
          <ServiceField name="durationMinutes" label="Duração (min)" type="number" min="1" max="1440" defaultValue={service?.durationMinutes ?? 30} />
        </div>
        <ServiceField name="commissionPercentage" label="Comissão % (opcional)" type="number" min="0" max="100" step="0.01" defaultValue={service?.commissionPercentage ?? 0} required={false} />
        {(error || errorMessage) && <p role="alert" className="text-sm text-danger-600">{error ?? errorMessage}</p>}
        <div className="flex justify-end gap-3 pt-2">
          <button type="button" onClick={onClose} className="rounded-xl border border-brand-200 px-4 py-2.5 text-sm font-semibold text-brand-700 hover:bg-sand-100">Cancelar</button>
          <button disabled={isSubmitting} className="rounded-xl bg-brand-600 px-4 py-2.5 text-sm font-semibold text-white hover:bg-brand-700 disabled:opacity-60">{isSubmitting ? 'Salvando...' : 'Salvar serviço'}</button>
        </div>
      </form>
    </Modal>
  )
}

function ServiceField({ label, required = true, ...props }: React.InputHTMLAttributes<HTMLInputElement> & { label: string; name: string }) {
  return (
    <label className="block text-sm font-medium text-brand-800">
      {label}
      <input required={required} {...props} className="mt-1.5 w-full rounded-xl border border-brand-200 px-3.5 py-2.5 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100" />
    </label>
  )
}
