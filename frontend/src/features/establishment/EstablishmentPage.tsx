import { useEffect, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Building2, Clock3, Save, Trash2 } from 'lucide-react'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { getApiErrorMessage } from '@/lib/apiError'
import { deactivateMyEstablishment, fetchMyEstablishment, updateMyEstablishment, type EstablishmentUpdateInput } from '@/features/establishment/establishmentApi'

const QUERY_KEY = ['my-establishment']
const EMPTY_FORM: EstablishmentUpdateInput = { name: '', email: '', phone: '' }

export function EstablishmentPage() {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<EstablishmentUpdateInput>(EMPTY_FORM)
  const [message, setMessage] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isConfirmingDeactivation, setIsConfirmingDeactivation] = useState(false)
  const establishmentQuery = useQuery({ queryKey: QUERY_KEY, queryFn: fetchMyEstablishment })
  const establishment = establishmentQuery.data

  useEffect(() => {
    if (establishment) setForm({ name: establishment.name, email: establishment.email ?? '', phone: establishment.phone ?? '' })
  }, [establishment])

  const updateMutation = useMutation({
    mutationFn: updateMyEstablishment,
    onSuccess: (updated) => {
      queryClient.setQueryData(QUERY_KEY, updated)
      setMessage('Dados do estabelecimento atualizados com sucesso.')
      setErrorMessage(null)
    },
    onError: (error) => {
      setMessage(null)
      setErrorMessage(getApiErrorMessage(error, 'Não foi possível atualizar o estabelecimento.'))
    },
  })
  const deactivateMutation = useMutation({
    mutationFn: deactivateMyEstablishment,
    onSuccess: () => {
      queryClient.setQueryData(QUERY_KEY, (current: typeof establishment) => current ? { ...current, active: false } : current)
      setIsConfirmingDeactivation(false)
      setMessage('Estabelecimento desativado com sucesso.')
      setErrorMessage(null)
    },
    onError: (error) => {
      setIsConfirmingDeactivation(false)
      setMessage(null)
      setErrorMessage(getApiErrorMessage(error, 'Não foi possível desativar o estabelecimento.'))
    },
  })

  function updateField(field: keyof EstablishmentUpdateInput, value: string) { setForm((current) => ({ ...current, [field]: value })) }
  function submit(event: FormEvent) {
    event.preventDefault()
    if (!establishment?.active) return
    setMessage(null)
    setErrorMessage(null)
    updateMutation.mutate(form)
  }

  if (establishmentQuery.isLoading) return <PageContainer><StateCard>Carregando dados do estabelecimento...</StateCard></PageContainer>
  if (establishmentQuery.isError || !establishment) return <PageContainer><StateCard error>{getApiErrorMessage(establishmentQuery.error, 'Não foi possível carregar o estabelecimento.')}</StateCard></PageContainer>

  const isInactive = !establishment.active
  return (
    <PageContainer>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div><h1 className="page-title">Meu estabelecimento</h1><p className="page-subtitle">Consulte e mantenha os dados cadastrais do seu salão.</p></div>
        <span className={`inline-flex w-fit items-center rounded-full px-3 py-1 text-sm font-semibold ${isInactive ? 'bg-danger-500/10 text-danger-600' : 'bg-sage-50 text-sage-600'}`}>{isInactive ? 'Inativo' : 'Ativo'}</span>
      </div>
      {message && <p role="status" className="mt-5 rounded-xl bg-sage-50 px-4 py-3 text-sm text-sage-600">{message}</p>}
      {errorMessage && <p role="alert" className="mt-5 rounded-xl border border-danger-500/30 bg-danger-500/5 px-4 py-3 text-sm text-danger-600">{errorMessage}</p>}
      {isInactive && <p className="mt-5 rounded-xl border border-danger-500/30 bg-danger-500/5 px-4 py-3 text-sm text-danger-600">Este estabelecimento está inativo. A edição e a desativação não estão disponíveis.</p>}

      <div className="mt-7 grid gap-6 xl:grid-cols-[minmax(0,1fr)_18rem]">
        <form onSubmit={submit} className="rounded-2xl border border-brand-100 bg-white p-5 shadow-sm sm:p-6">
          <div className="flex items-center gap-2"><Building2 size={20} className="text-brand-500" /><h2 className="text-lg font-semibold text-brand-900">Dados cadastrais</h2></div>
          <p className="mt-1 text-sm text-brand-600">Nome e telefone podem ser atualizados. O CNPJ é definido no cadastro inicial.</p>
          <fieldset disabled={isInactive || updateMutation.isPending} className="mt-6 grid gap-5 sm:grid-cols-2">
            <Field label="Nome do estabelecimento" value={form.name} onChange={(value) => updateField('name', value)} required className="sm:col-span-2" />
            <label className="block"><span className="text-sm font-medium text-brand-800">CNPJ</span><input value={establishment.cnpj} readOnly className="mt-2 w-full rounded-xl border border-brand-200 bg-sand-100 px-3.5 py-3 text-brand-700" /></label>
            <Field label="Telefone" value={form.phone} onChange={(value) => updateField('phone', value)} />
          </fieldset>
          <button type="submit" disabled={isInactive || updateMutation.isPending} className="primary-button mt-7 disabled:cursor-not-allowed disabled:opacity-60"><Save size={18} /> {updateMutation.isPending ? 'Salvando...' : 'Salvar alterações'}</button>
        </form>

        <aside className="h-fit rounded-2xl border border-brand-100 bg-white p-5 shadow-sm">
          <h2 className="text-lg font-semibold text-brand-900">Informações</h2>
          <dl className="mt-5 space-y-4 text-sm"><Info label="Código" value={`#${establishment.id}`} /><Info label="Funcionamento" value={`${formatTime(establishment.openingTime)} às ${formatTime(establishment.closingTime)}`} icon={<Clock3 size={16} />} /></dl>
          <div className="mt-6 border-t border-brand-100 pt-5"><h3 className="font-semibold text-danger-600">Desativar estabelecimento</h3><p className="mt-2 text-sm leading-6 text-brand-600">O salão deixará de aceitar novas operações administrativas e novos agendamentos. O histórico será preservado.</p><button type="button" disabled={isInactive || deactivateMutation.isPending} onClick={() => { setMessage(null); setErrorMessage(null); setIsConfirmingDeactivation(true) }} className="mt-4 inline-flex items-center gap-2 rounded-xl border border-danger-500/30 px-4 py-2 text-sm font-semibold text-danger-600 hover:bg-danger-500/5 disabled:cursor-not-allowed disabled:opacity-60"><Trash2 size={17} /> Desativar</button></div>
        </aside>
      </div>
      {isConfirmingDeactivation && <ConfirmDialog title="Desativar estabelecimento" message={`Tem certeza que deseja desativar “${establishment.name}”? Esta ação bloqueará novas operações administrativas e novos agendamentos, mas preservará o histórico.`} confirmLabel="Desativar estabelecimento" isLoading={deactivateMutation.isPending} onConfirm={() => deactivateMutation.mutate()} onCancel={() => setIsConfirmingDeactivation(false)} />}
    </PageContainer>
  )
}

function Field({ label, value, onChange, type = 'text', required = false, className = '' }: { label: string; value: string; onChange: (value: string) => void; type?: string; required?: boolean; className?: string }) { return <label className={`block ${className}`}><span className="text-sm font-medium text-brand-800">{label}</span><input type={type} value={value} required={required} onChange={(event) => onChange(event.target.value)} className="mt-2 w-full rounded-xl border border-brand-200 px-3.5 py-3 text-brand-900 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 disabled:bg-sand-100" /></label> }
function Info({ label, value, icon }: { label: string; value: string; icon?: React.ReactNode }) { return <div><dt className="text-brand-600">{label}</dt><dd className="mt-1 flex items-center gap-2 font-semibold text-brand-900">{icon}{value}</dd></div> }
function PageContainer({ children }: { children: React.ReactNode }) { return <div className="p-5 sm:p-7 lg:p-9">{children}</div> }
function StateCard({ children, error = false }: { children: React.ReactNode; error?: boolean }) { return <div className={`rounded-2xl border bg-white p-8 text-center ${error ? 'border-danger-500/30 text-danger-600' : 'border-brand-100 text-brand-600'}`}>{children}</div> }
function formatTime(value: string) { return value.slice(0, 5) }