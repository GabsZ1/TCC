import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { IMaskInput } from 'react-imask'
import { registerInitial } from '@/features/auth/services/authApi'
import { getApiErrorMessage } from '@/lib/apiError'

export function InitialRegistrationPage() {
  const navigate = useNavigate()
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError(null)
    setIsSubmitting(true)
    const form = new FormData(event.currentTarget)

    try {
      await registerInitial({
        ownerName: String(form.get('ownerName')),
        ownerEmail: String(form.get('ownerEmail')),
        password: String(form.get('password')),
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
    <AuthPage title="Cadastre seu estabelecimento" subtitle="Crie o acesso do administrador e os dados básicos do salão.">
      <form onSubmit={handleSubmit} className="grid gap-4 sm:grid-cols-2">
        <Field name="ownerName" label="Seu nome" autoComplete="name" />
        <Field name="ownerEmail" label="Seu e-mail" type="email" autoComplete="email" />
        <Field name="password" label="Senha" type="password" minLength={8} autoComplete="new-password" />
        <Field name="establishmentName" label="Nome do estabelecimento" />
        <CnpjField />
        <Field name="establishmentEmail" label="E-mail do estabelecimento (opcional)" type="email" required={false} />
        <Field name="establishmentPhone" label="Telefone (opcional)" required={false} />

        {error && <p role="alert" className="sm:col-span-2 text-sm text-danger-600">{error}</p>}

        <div className="sm:col-span-2 flex flex-col-reverse gap-3 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-brand-600">Já possui conta? <Link className="font-semibold text-brand-700 hover:underline" to="/login">Entrar</Link></p>
          <button disabled={isSubmitting} className="rounded-xl bg-brand-600 px-5 py-3 font-semibold text-white hover:bg-brand-700 disabled:opacity-60">
            {isSubmitting ? 'Cadastrando...' : 'Criar estabelecimento'}
          </button>
        </div>
      </form>
    </AuthPage>
  )
}

interface FieldProps extends React.InputHTMLAttributes<HTMLInputElement> {
  name: string
  label: string
}

function Field({ name, label, required = true, ...props }: FieldProps) {
  return (
    <label className="block text-sm font-medium text-brand-800">
      {label}
      <input name={name} required={required} {...props} className="mt-1.5 w-full rounded-xl border border-brand-200 bg-white px-3.5 py-2.5 text-brand-900 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100" />
    </label>
  )
}

function CnpjField() {
  return (
    <label className="block text-sm font-medium text-brand-800">
      CNPJ
      <IMaskInput
        name="cnpj"
        mask="00.000.000/0000-00"
        required
        inputMode="numeric"
        pattern="[0-9]{2}\\.[0-9]{3}\\.[0-9]{3}/[0-9]{4}-[0-9]{2}"
        title="Informe os 14 dígitos do CNPJ"
        placeholder="00.000.000/0000-00"
        className="mt-1.5 w-full rounded-xl border border-brand-200 bg-white px-3.5 py-2.5 text-brand-900 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100"
      />
    </label>
  )
}

function AuthPage({ title, subtitle, children }: { title: string; subtitle: string; children: React.ReactNode }) {
  return (
    <main className="min-h-screen bg-sand-50 px-4 py-10">
      <div className="mx-auto max-w-3xl">
        <Link to="/login" className="font-[var(--font-display)] text-xl text-brand-900">SalonHub</Link>
        <section className="mt-8 rounded-3xl border border-brand-100 bg-white p-6 shadow-sm sm:p-9">
          <h1 className="font-[var(--font-display)] text-3xl text-brand-900">{title}</h1>
          <p className="mt-2 mb-7 text-base text-brand-600">{subtitle}</p>
          {children}
        </section>
      </div>
    </main>
  )
}
