/** Coleta preço, duração e demais dados usados ao criar ou editar um serviço. */
import { useState, type FormEvent } from 'react'
import { Clock3, Percent, Wallet } from 'lucide-react'
import { Modal } from '@/components/ui/Modal'
import type { SalonService, SalonServiceInput } from '@/features/catalog/api/servicesApi'

interface ServiceFormModalProps {
  service?: SalonService | null
  isSubmitting: boolean
  errorMessage?: string | null
  onSubmit: (values: SalonServiceInput) => void
  onClose: () => void
}

/** Coleta duração, preço e comissão antes de criar ou atualizar um serviço. */
export function ServiceFormModal({ service, isSubmitting, errorMessage, onSubmit, onClose }: ServiceFormModalProps) {
  const [error, setError] = useState<string | null>(null)

  /** Converte os campos do formulário para os valores esperados pela API. */
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const price = Number(form.get('price'))
    const durationMinutes = Number(form.get('durationMinutes'))
    const commissionText = String(form.get('commissionPercentage') || '')
    const commissionPercentage = commissionText === '' ? 0 : Number(commissionText)

    if (price <= 0 || price > 99999999.99 || durationMinutes <= 0 || durationMinutes > 1440 || commissionPercentage < 0 || commissionPercentage > 100) {
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
      <form onSubmit={handleSubmit} className="space-y-5">
        <p className="text-sm leading-6 text-text-600">Defina como o serviço aparece no catálogo e quais valores serão usados nos atendimentos.</p>
        <ServiceField name="name" label="Nome do serviço" defaultValue={service?.name} maxLength={100} placeholder="Ex.: Corte feminino" />
        <ServiceField name="description" label="Descrição (opcional)" defaultValue={service?.description ?? ''} required={false} maxLength={255} placeholder="Uma descrição curta para identificar o serviço" />
        <div className="grid gap-3 min-[380px]:grid-cols-2">
          <ServiceField name="price" label="Valor" type="text" inputMode="decimal" pattern="[0-9]+([.][0-9]{1,2})?" maxLength={11} onInput={onlyDecimal} defaultValue={service?.price} icon={<Wallet size={15} />} prefix="R$" />
          <ServiceField name="durationMinutes" label="Duração" type="text" inputMode="numeric" pattern="[0-9]+" maxLength={4} onInput={onlyDigits} defaultValue={service?.durationMinutes ?? 30} icon={<Clock3 size={15} />} suffix="min" />
        </div>
        <ServiceField name="commissionPercentage" label="Comissão (opcional)" type="text" inputMode="decimal" pattern="[0-9]+([.][0-9]{1,2})?" maxLength={6} onInput={onlyDecimal} defaultValue={service?.commissionPercentage ?? 0} required={false} icon={<Percent size={15} />} suffix="%" />
        {(error || errorMessage) && <p role="alert" className="rounded-xl border border-danger-500/20 bg-danger-50 px-4 py-3 text-sm text-danger-600">{error ?? errorMessage}</p>}
        <div className="flex flex-col-reverse gap-3 border-t border-background-300 pt-5 sm:flex-row sm:justify-end">
          <button type="button" onClick={onClose} className="secondary-button">Cancelar</button>
          <button disabled={isSubmitting} className="primary-button">{isSubmitting ? 'Salvando...' : 'Salvar serviço'}</button>
        </div>
      </form>
    </Modal>
  )
}

function onlyDigits(event: React.FormEvent<HTMLInputElement>) {
  event.currentTarget.value = event.currentTarget.value.replace(/\D/g, '')
}

function onlyDecimal(event: React.FormEvent<HTMLInputElement>) {
  const [integer, ...fraction] = event.currentTarget.value.replace(',', '.').replace(/[^\d.]/g, '').split('.')
  event.currentTarget.value = fraction.length ? `${integer}.${fraction.join('').slice(0, 2)}` : integer
}

function ServiceField({ label, required = true, icon, prefix, suffix, ...props }: React.InputHTMLAttributes<HTMLInputElement> & { label: string; name: string; icon?: React.ReactNode; prefix?: string; suffix?: string }) {
  return (
    <label className="block text-sm font-semibold text-text-800">
      {label}
      <span className="relative block">
        {(icon || prefix) && <span className="pointer-events-none absolute left-3.5 top-1/2 mt-1 flex -translate-y-1/2 items-center gap-1 text-xs font-medium text-text-400">{icon}{prefix}</span>}
        <input required={required} {...props} className={`field-control ${icon && prefix ? 'pl-16' : icon || prefix ? 'pl-10' : ''} ${suffix ? 'pr-11' : ''}`} />
        {suffix && <span className="pointer-events-none absolute right-3.5 top-1/2 mt-1 -translate-y-1/2 text-xs font-medium text-text-400">{suffix}</span>}
      </span>
    </label>
  )
}
