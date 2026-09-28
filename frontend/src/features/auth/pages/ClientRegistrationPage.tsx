import { useState, type FormEvent } from 'react'
import { ArrowRight, Mail, Phone, UserRound } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { IMaskInput } from 'react-imask'
import { AuthShell } from '@/features/auth/components/AuthShell'
import { PasswordField } from '@/features/auth/components/PasswordFields'
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from '@/features/auth/passwordPolicy'
import { registerClient } from '@/features/auth/services/authApi'
import { getApiErrorMessage } from '@/lib/apiError'
import { isValidPhone, PHONE_MASK } from '@/lib/phone'

/** US 2.23 — Cadastrar cliente (nome, e-mail, telefone, senha e confirmação). */
export function ClientRegistrationPage() {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const confirmationMismatch = confirmation.length > 0 && password !== confirmation

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    if (!name.trim() || !email.trim() || !phone.trim() || !password || !confirmation) {
      return setError('Preencha todos os campos obrigatórios!')
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) return setError('Informe um e-mail válido!')
    if (!isValidPhone(phone)) return setError('Informe um telefone válido!')
    if (password.length < PASSWORD_MIN_LENGTH) return setError(`A senha deve ter no mínimo ${PASSWORD_MIN_LENGTH} caracteres.`)
    if (password !== confirmation) return setError('As senhas não coincidem!')

    setIsSubmitting(true)
    try {
      await registerClient({ name: name.trim(), email: email.trim(), phone, password, passwordConfirmation: confirmation })
      navigate('/login', { replace: true, state: { message: 'Cadastro realizado com sucesso! Entre para agendar.' } })
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Não foi possível criar sua conta.'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AuthShell
      eyebrow="Conta de cliente"
      title="Crie sua conta"
      subtitle="Use um único acesso para marcar horários e acompanhar seus agendamentos."
      maxWidth="sm"
    >
      <form onSubmit={handleSubmit} className="space-y-5" noValidate>
        <p className="text-xs text-text-500">Campos marcados com <span className="font-bold text-danger-600">*</span> são obrigatórios.</p>
        <ClientField label="Nome completo" value={name} onChange={setName} autoComplete="name" placeholder="Seu nome" icon={<UserRound size={17} />} />
        <ClientField label="E-mail" type="email" value={email} onChange={setEmail} autoComplete="email" placeholder="voce@exemplo.com" icon={<Mail size={17} />} />

        <label className="block text-sm font-semibold text-text-800">
          Telefone <Required />
          <span className="relative block">
            <span className="pointer-events-none absolute left-3.5 top-1/2 mt-1 -translate-y-1/2 text-text-400"><Phone size={17} /></span>
            <IMaskInput
              mask={PHONE_MASK}
              value={phone}
              onAccept={(value: string) => setPhone(value)}
              inputMode="tel"
              autoComplete="tel"
              placeholder="(00) 00000-0000"
              className="auth-input pl-10"
            />
          </span>
        </label>

        <PasswordField id="client-password" label="Senha *" autoComplete="new-password" value={password} maxLength={PASSWORD_MAX_LENGTH} onChange={setPassword} />
        <PasswordField id="client-password-confirmation" label="Confirmar senha *" autoComplete="new-password" value={confirmation} maxLength={PASSWORD_MAX_LENGTH} invalid={confirmationMismatch} onChange={setConfirmation} />
        {confirmationMismatch && <p className="-mt-3 text-sm font-medium text-danger-600">As senhas não coincidem!</p>}

        {error && <p role="alert" className="rounded-xl border border-danger-500/20 bg-danger-50 px-4 py-3 text-sm text-danger-600">{error}</p>}

        <button disabled={isSubmitting} className="primary-button w-full py-3">
          {isSubmitting ? 'Criando conta...' : 'Cadastrar'}
          {!isSubmitting && <ArrowRight size={17} />}
        </button>
      </form>

      <p className="mt-6 border-t border-background-300 pt-5 text-center text-sm text-text-600">
        Já possui conta? <Link className="font-semibold text-primary-700 hover:underline" to="/login">Entrar</Link>
      </p>
    </AuthShell>
  )
}

function Required() {
  return <span className="font-bold text-danger-600" aria-hidden="true">*</span>
}

function ClientField({ label, icon, value, onChange, ...props }: Omit<React.InputHTMLAttributes<HTMLInputElement>, 'onChange' | 'value'> & {
  label: string
  icon?: React.ReactNode
  value: string
  onChange: (value: string) => void
}) {
  return (
    <label className="block text-sm font-semibold text-text-800">
      {label} <Required />
      <span className="relative block">
        {icon && <span className="pointer-events-none absolute left-3.5 top-1/2 mt-1 -translate-y-1/2 text-text-400">{icon}</span>}
        <input {...props} value={value} onChange={(event) => onChange(event.target.value)} className={`auth-input ${icon ? 'pl-10' : ''}`} />
      </span>
    </label>
  )
}
