/** Registra o primeiro estabelecimento e seu administrador. */
import { useState, type FormEvent, type InputHTMLAttributes } from 'react'
import { ArrowRight, Building2, UserRound } from 'lucide-react'
import { Link, useNavigate } from 'react-router-dom'
import { IMaskInput } from 'react-imask'
import { AuthShell } from '@/features/auth/components/AuthShell'
import { registerInitial } from '@/features/auth/services/authApi'
import { getApiErrorMessage } from '@/lib/apiError'
import { PHONE_MASK } from '@/lib/phone'

/** Agrupa os dados do responsável e da unidade no cadastro inicial. */
export function InitialRegistrationPage() {
  const navigate = useNavigate()
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [passwordMismatch, setPasswordMismatch] = useState(false)

  /** Envia juntos os dados do responsável e do estabelecimento inicial. */
  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    const form = new FormData(event.currentTarget)
    const password = String(form.get('password'))
    const mismatch = password !== form.get('confirmPassword')
    setPasswordMismatch(mismatch)
    if (mismatch) {
      event.currentTarget.querySelector<HTMLInputElement>('input[name="confirmPassword"]')?.focus()
      return
    }

    setIsSubmitting(true)
    try {
      await registerInitial({
        ownerName: String(form.get('ownerName')),
        ownerEmail: String(form.get('ownerEmail')),
        password,
        establishmentName: String(form.get('establishmentName')),
        cnpj: String(form.get('cnpj')),
        establishmentEmail: String(form.get('establishmentEmail') || ''),
        establishmentPhone: String(form.get('establishmentPhone') || ''),
      })
      navigate('/login', { replace: true, state: { message: 'Cadastro concluído. Entre com sua conta.' } })
    } catch (requestError) {
      setError(getApiErrorMessage(requestError, 'Não foi possível concluir o cadastro.'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <AuthShell
      eyebrow="Comece por aqui"
      title="Cadastre seu estabelecimento"
      subtitle="Crie o acesso do responsável e os dados básicos do negócio. Depois você poderá configurar equipe, serviços e agenda."
      maxWidth="lg"
      scrollFormOnDesktop
    >
      <form onSubmit={handleSubmit}>
        <FormSection icon={<UserRound size={18} />} title="Sua conta" description="Dados usados para acessar e administrar o Meu Espaço.">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field name="ownerName" label="Seu nome" maxLength={150} autoComplete="name" placeholder="Nome completo" />
            <Field name="ownerEmail" label="Seu e-mail" type="email" maxLength={150} autoComplete="email" placeholder="voce@exemplo.com" />
            <Field name="password" label="Senha" type="password" minLength={8} maxLength={72} autoComplete="new-password" placeholder="Mínimo de 8 caracteres" onChange={() => setPasswordMismatch(false)} />
            <Field name="confirmPassword" label="Confirmar senha" type="password" minLength={8} maxLength={72} autoComplete="new-password" aria-invalid={passwordMismatch} aria-describedby={passwordMismatch ? 'confirm-password-error' : undefined} onChange={() => setPasswordMismatch(false)} />
            {passwordMismatch && <p id="confirm-password-error" role="alert" className="text-sm text-danger-600 sm:col-span-2">As senhas não coincidem.</p>}
          </div>
        </FormSection>

        <div className="my-7 border-t border-background-300" />

        <FormSection icon={<Building2 size={18} />} title="Seu negócio" description="Informações básicas do estabelecimento que será criado.">
          <div className="grid gap-4 sm:grid-cols-2">
            <Field name="establishmentName" label="Nome do estabelecimento" maxLength={150} placeholder="Ex.: Studio Aurora" className="sm:col-span-2" />
            <CnpjField />
            <label className="block text-sm font-semibold text-text-800">Telefone (opcional)<IMaskInput name="establishmentPhone" mask={PHONE_MASK} inputMode="tel" maxLength={20} placeholder="(44) 99999-9999" className="auth-input" /></label>
            <Field name="establishmentEmail" label="E-mail do estabelecimento (opcional)" type="email" required={false} maxLength={150} placeholder="contato@negocio.com" className="sm:col-span-2" />
          </div>
        </FormSection>

        {error && (
          <p role="alert" className="mt-6 rounded-xl border border-danger-500/20 bg-danger-50 px-4 py-3 text-sm text-danger-600">
            {error}
          </p>
        )}

        <div className="mt-7 flex flex-col-reverse gap-4 border-t border-background-300 pt-6 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-text-600">
            Já possui conta? <Link className="font-semibold text-primary-700 hover:underline" to="/login">Entrar</Link>
          </p>
          <button disabled={isSubmitting} className="primary-button px-5 py-3">
            {isSubmitting ? 'Cadastrando...' : 'Criar estabelecimento'}
            {!isSubmitting && <ArrowRight size={17} />}
          </button>
        </div>
      </form>
    </AuthShell>
  )
}

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  name: string
  label: string
  className?: string
}

function Field({ name, label, required = true, className = '', ...props }: FieldProps) {
  return (
    <label className={`block text-sm font-semibold text-text-800 ${className}`}>
      {label}
      <input name={name} required={required} {...props} className="auth-input" />
    </label>
  )
}

/** Aceita CNPJ numérico ou alfanumérico, com ou sem pontuação, no cadastro inicial. */
function CnpjField() {
  return (
    <label className="block text-sm font-semibold text-text-800">
      CNPJ
      <input
        name="cnpj"
        required
        maxLength={18}
        autoCapitalize="characters"
        pattern="(?:[A-Za-z0-9]{12}[0-9]{2}|[A-Za-z0-9]{2}[.][A-Za-z0-9]{3}[.][A-Za-z0-9]{3}/[A-Za-z0-9]{4}-[0-9]{2})"
        title="Informe 12 letras ou números e 2 dígitos verificadores, com ou sem pontuação"
        placeholder="00.000.000/E08G-12"
        className="auth-input"
      />
      <span className="mt-1.5 block text-xs font-normal text-text-600">Aceita CNPJ numérico ou alfanumérico. Os 2 últimos caracteres são números.</span>
    </label>
  )
}

/** Separa os dados do responsável dos dados do estabelecimento. */
function FormSection({ icon, title, description, children }: { icon: React.ReactNode; title: string; description: string; children: React.ReactNode }) {
  return (
    <section>
      <div className="mb-5 flex items-start gap-3">
        <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-primary-100 text-primary-700">{icon}</span>
        <div>
          <h2 className="font-semibold text-text-900">{title}</h2>
          <p className="mt-0.5 text-sm text-text-500">{description}</p>
        </div>
      </div>
      {children}
    </section>
  )
}
