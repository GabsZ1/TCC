import { useState } from 'react'
import { CheckCircle2, Circle, Eye, EyeOff } from 'lucide-react'

export function PasswordField({ id, label, value, onChange, autoComplete, maxLength, invalid = false }: {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  autoComplete: string
  maxLength?: number
  invalid?: boolean
}) {
  const [visible, setVisible] = useState(false)
  return (
    <div>
      <label htmlFor={id} className="block text-sm font-semibold text-text-800">{label}</label>
      <div className="relative">
        <input
          id={id}
          type={visible ? 'text' : 'password'}
          autoComplete={autoComplete}
          value={value}
          maxLength={maxLength}
          aria-invalid={invalid}
          onChange={(event) => onChange(event.target.value)}
          className={`field-control pr-11 ${invalid ? 'border-danger-500 focus:border-danger-500 focus:ring-danger-500/10' : ''}`}
        />
        <button
          type="button"
          onClick={() => setVisible((current) => !current)}
          className="absolute right-2 top-1/2 mt-1 grid size-8 -translate-y-1/2 place-items-center rounded-lg text-text-500 hover:bg-background-200 hover:text-text-800"
          aria-label={visible ? 'Ocultar senha' : 'Mostrar senha'}
          title={visible ? 'Ocultar senha' : 'Mostrar senha'}
        >
          {visible ? <EyeOff size={17} /> : <Eye size={17} />}
        </button>
      </div>
    </div>
  )
}

export function CriterionItem({ label, met, optional = false }: { label: string; met: boolean; optional?: boolean }) {
  return (
    <li className={`flex items-center gap-2 ${met ? 'text-success-600' : optional ? 'text-text-500' : 'text-text-600'}`}>
      {met ? <CheckCircle2 size={15} /> : <Circle size={15} />}
      {label}
    </li>
  )
}
