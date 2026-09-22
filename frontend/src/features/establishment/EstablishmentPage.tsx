import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Building2, CalendarClock, Save, Power, RotateCcw } from 'lucide-react'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { getApiErrorMessage } from '@/lib/apiError'
import { deactivateMyEstablishment, fetchMyEstablishment, reactivateMyEstablishment, updateMyEstablishment, type BusinessHour, type EstablishmentUpdateInput, type WeekDay } from '@/features/establishment/establishmentApi'

const QUERY_KEY = ['my-establishment']
const WEEK_DAYS: { value: WeekDay; label: string }[] = [
  { value: 'MONDAY', label: 'Segunda-feira' }, { value: 'TUESDAY', label: 'Terça-feira' },
  { value: 'WEDNESDAY', label: 'Quarta-feira' }, { value: 'THURSDAY', label: 'Quinta-feira' },
  { value: 'FRIDAY', label: 'Sexta-feira' }, { value: 'SATURDAY', label: 'Sábado' },
  { value: 'SUNDAY', label: 'Domingo' },
]
export function EstablishmentPage() {
  const establishmentQuery = useQuery({ queryKey: QUERY_KEY, queryFn: fetchMyEstablishment })
  const establishment = establishmentQuery.data

  if (establishmentQuery.isLoading) return <PageContainer><StateCard>Carregando dados do estabelecimento...</StateCard></PageContainer>
  if (establishmentQuery.isError || !establishment) return <PageContainer><StateCard error>{getApiErrorMessage(establishmentQuery.error, 'Não foi possível carregar o estabelecimento.')}</StateCard></PageContainer>

  return <EstablishmentContent establishment={establishment} />
}

function EstablishmentContent({ establishment }: { establishment: Awaited<ReturnType<typeof fetchMyEstablishment>> }) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<EstablishmentUpdateInput>(() => toForm(establishment))
  const [message, setMessage] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isConfirmingDeactivation, setIsConfirmingDeactivation] = useState(false)

  const updateMutation = useMutation({
    mutationFn: updateMyEstablishment,
    onSuccess: (updated) => {
      queryClient.setQueryData(QUERY_KEY, updated)
      setForm(toForm(updated))
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
      queryClient.invalidateQueries()
      setIsConfirmingDeactivation(false)
      setMessage('Estabelecimento desativado com sucesso.')
      setErrorMessage(null)
    },
    onError: (error) => {
      setIsConfirmingDeactivation(false)
      setMessage(null)
      setErrorMessage(getApiErrorMessage(error, 'Não foi possível desativar. Cancele ou conclua os atendimentos confirmados pendentes e tente novamente.'))
    },
  })

  const reactivateMutation = useMutation({
    mutationFn: reactivateMyEstablishment,
    onSuccess: (updated) => {
      queryClient.setQueryData(QUERY_KEY, updated)
      queryClient.invalidateQueries()
      setMessage('Estabelecimento reativado com sucesso.')
      setErrorMessage(null)
    },
    onError: (error) => {
      setMessage(null)
      setErrorMessage(getApiErrorMessage(error, 'Não foi possível reativar o estabelecimento.'))
    },
  })

  function updateField(field: 'name' | 'email' | 'phone', value: string) { setForm((current) => ({ ...current, [field]: value })) }
  function updateBusinessHour(dayOfWeek: WeekDay, changes: Partial<BusinessHour>) {
    setForm((current) => ({ ...current, businessHours: current.businessHours.map((hour) => hour.dayOfWeek === dayOfWeek ? { ...hour, ...changes } : hour) }))
  }
  function submit(event: FormEvent) {
    event.preventDefault()
    if (!establishment?.active) return
    setMessage(null)
    setErrorMessage(null)
    updateMutation.mutate(form)
  }

  const isInactive = !establishment.active
  return (
    <PageContainer>
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div><h1 className="page-title">Meu estabelecimento</h1><p className="page-subtitle">Consulte e mantenha os dados cadastrais do seu estabelecimento.</p></div>
        <span className={`inline-flex w-fit items-center rounded-full px-3 py-1 text-sm font-semibold ${isInactive ? 'bg-danger-500/10 text-danger-600' : 'bg-success-50 text-success-600'}`}>{isInactive ? 'Inativo' : 'Ativo'}</span>
      </div>
      {message && <p role="status" className="mt-5 rounded-xl bg-success-50 px-4 py-3 text-sm text-success-600">{message}</p>}
      {errorMessage && <p role="alert" className="mt-5 rounded-xl border border-danger-500/30 bg-danger-500/5 px-4 py-3 text-sm text-danger-600">{errorMessage}</p>}
      {isInactive && <p className="mt-5 rounded-xl border border-danger-500/30 bg-danger-500/5 px-4 py-3 text-sm text-danger-600">Este estabelecimento está inativo. Reative-o para liberar as operações administrativas e novos agendamentos.</p>}

      <div className="mt-7 grid gap-6 xl:grid-cols-[minmax(0,1fr)_18rem]">
        <form onSubmit={submit} className="space-y-5">
          <fieldset disabled={isInactive || updateMutation.isPending || deactivateMutation.isPending} className="space-y-5">
            <section className="surface-card p-5 sm:p-6">
              <SectionHeader icon={<Building2 size={20} />} title="Dados do estabelecimento" description="Informações usadas para identificar e entrar em contato com o estabelecimento." />
              <div className="mt-6 grid gap-5 sm:grid-cols-2">
                <Field label="Nome do estabelecimento" value={form.name} onChange={(value) => updateField('name', value)} required className="sm:col-span-2" />
                <label className="block"><span className="text-sm font-medium text-text-800">CNPJ</span><input value={formatCnpj(establishment.cnpj)} readOnly className="mt-2 w-full rounded-xl border border-background-300 bg-background-100 px-3.5 py-3 text-text-700" /></label>
                <Field label="Telefone" value={form.phone} onChange={(value) => updateField('phone', value)} />
                <Field label="E-mail" value={form.email} type="email" onChange={(value) => updateField('email', value)} className="sm:col-span-2" />
              </div>
              <p className="mt-4 text-xs text-text-600">O CNPJ é definido no cadastro inicial e não pode ser alterado.</p>
            </section>

            <section className="surface-card p-5 sm:p-6">
              <SectionHeader icon={<CalendarClock size={20} />} title="Política de agendamento" description="Defina com quanta antecedência os clientes podem reservar um horário." />
              <div className="mt-6 grid gap-5 sm:grid-cols-2">
                <label className="block"><span className="text-sm font-medium text-text-800">Antecedência mínima</span><div className="relative mt-2"><input type="number" min={0} max={168} required value={form.minimumAdvanceHours} onChange={(event) => setForm((current) => ({ ...current, minimumAdvanceHours: Number(event.target.value) }))} className="w-full rounded-xl border border-background-300 py-3 pl-3.5 pr-16 text-text-900 outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100" /><span className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-sm text-text-600">horas</span></div><span className="mt-2 block text-xs text-text-600">Evita reservas muito próximas do horário atual.</span></label>
                <label className="block"><span className="text-sm font-medium text-text-800">Antecedência máxima</span><div className="relative mt-2"><input type="number" min={1} max={365} required value={form.maximumAdvanceDays} onChange={(event) => setForm((current) => ({ ...current, maximumAdvanceDays: Number(event.target.value) }))} className="w-full rounded-xl border border-background-300 py-3 pl-3.5 pr-14 text-text-900 outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100" /><span className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-sm text-text-600">dias</span></div><span className="mt-2 block text-xs text-text-600">Limita até quando a agenda fica aberta para reservas.</span></label>
              </div>
              <div className="mt-5 rounded-xl bg-secondary-50 px-4 py-3 text-sm text-text-700">Com a configuração atual, clientes podem agendar entre <strong>{form.minimumAdvanceHours} horas</strong> e <strong>{form.maximumAdvanceDays} dias</strong> de antecedência.</div>
            </section>

            <section className="surface-card p-5 sm:p-6">
              <SectionHeader icon={<CalendarClock size={20} />} title="Horários de funcionamento" description="Defina os horários de cada dia ou marque o estabelecimento como fechado." />
              <div className="mt-5 space-y-3">{form.businessHours.map((hour) => <BusinessHourRow key={hour.dayOfWeek} hour={hour} onChange={(changes) => updateBusinessHour(hour.dayOfWeek, changes)} />)}</div>
            </section>
          </fieldset>

          <div className="surface-card flex justify-end p-4">
            <button type="submit" disabled={isInactive || updateMutation.isPending || deactivateMutation.isPending} className="primary-button disabled:cursor-not-allowed disabled:opacity-60"><Save size={18} /> {updateMutation.isPending ? 'Salvando...' : 'Salvar alterações'}</button>
          </div>
        </form>

        <aside className="surface-card h-fit p-5">
          <h2 className="text-lg font-semibold text-text-900">Informações</h2>
          <dl className="mt-5 space-y-4 text-sm"><Info label="Código" value={`#${establishment.id}`} /><Info label="Funcionamento" value="Configuração semanal" icon={<CalendarClock size={16} />} /></dl>
          <div className="mt-6 border-t border-background-300 pt-5">
            <h3 className={`font-semibold ${isInactive ? 'text-success-600' : 'text-danger-600'}`}>{isInactive ? 'Reativar estabelecimento' : 'Desativar estabelecimento'}</h3>
            <p className="mt-2 text-sm leading-6 text-text-600">{isInactive ? 'Reative o estabelecimento para retomar as operações com os dados e vínculos preservados.' : 'Antes de desativar, resolva na agenda todos os atendimentos confirmados futuros ou em andamento. Depois disso, o estabelecimento deixará de aceitar novas operações; o histórico será preservado.'}</p>
            <button type="button" disabled={updateMutation.isPending || deactivateMutation.isPending || reactivateMutation.isPending} onClick={() => {
              setMessage(null)
              setErrorMessage(null)
              if (isInactive) reactivateMutation.mutate()
              else setIsConfirmingDeactivation(true)
            }} className={`mt-4 inline-flex items-center gap-2 rounded-xl border px-4 py-2 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-60 ${isInactive ? 'border-success-600/30 text-success-600 hover:bg-success-50' : 'border-danger-500/30 text-danger-600 hover:bg-danger-500/5'}`}>
              {isInactive ? <RotateCcw size={17} /> : <Power size={17} />}
              {reactivateMutation.isPending ? 'Reativando...' : isInactive ? 'Reativar' : 'Desativar'}
            </button>
          </div>
        </aside>
      </div>
      {isConfirmingDeactivation && <ConfirmDialog title="Desativar estabelecimento" message={`Tem certeza que deseja desativar “${establishment.name}”? Confirme somente depois de cancelar ou concluir os atendimentos confirmados futuros e em andamento. Os dados e o histórico serão preservados.`} confirmLabel="Desativar estabelecimento" isLoading={deactivateMutation.isPending} onConfirm={() => deactivateMutation.mutate()} onCancel={() => setIsConfirmingDeactivation(false)} />}
    </PageContainer>
  )
}

function Field({ label, value, onChange, type = 'text', required = false, className = '' }: { label: string; value: string; onChange: (value: string) => void; type?: string; required?: boolean; className?: string }) { return <label className={`block ${className}`}><span className="text-sm font-medium text-text-800">{label}</span><input type={type} value={value} required={required} onChange={(event) => onChange(event.target.value)} className="mt-2 w-full rounded-xl border border-background-300 px-3.5 py-3 text-text-900 outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 disabled:bg-background-100" /></label> }
function SectionHeader({ icon, title, description }: { icon: React.ReactNode; title: string; description: string }) { return <div><div className="flex items-center gap-2 text-text-600">{icon}<h2 className="text-lg font-semibold text-text-900">{title}</h2></div><p className="mt-1 text-sm text-text-600">{description}</p></div> }
function BusinessHourRow({ hour, onChange }: { hour: BusinessHour; onChange: (changes: Partial<BusinessHour>) => void }) { const label = WEEK_DAYS.find((day) => day.value === hour.dayOfWeek)?.label ?? hour.dayOfWeek; return <div className="grid items-center gap-3 rounded-xl bg-background-50 p-3 sm:grid-cols-[9rem_auto_1fr_1fr]"><span className="text-sm font-medium text-text-800">{label}</span><label className="flex items-center gap-2 text-sm text-text-600"><input type="checkbox" checked={hour.closed} onChange={(event) => onChange({ closed: event.target.checked, openingTime: event.target.checked ? null : '09:00', closingTime: event.target.checked ? null : '18:00' })} /> Fechado</label><input aria-label={`Abertura de ${label}`} type="time" required={!hour.closed} disabled={hour.closed} value={hour.openingTime?.slice(0, 5) ?? ''} onChange={(event) => onChange({ openingTime: event.target.value })} className="rounded-lg border border-background-300 px-3 py-2 disabled:bg-background-200" /><input aria-label={`Fechamento de ${label}`} type="time" required={!hour.closed} disabled={hour.closed} value={hour.closingTime?.slice(0, 5) ?? ''} onChange={(event) => onChange({ closingTime: event.target.value })} className="rounded-lg border border-background-300 px-3 py-2 disabled:bg-background-200" /></div> }
function Info({ label, value, icon }: { label: string; value: string; icon?: React.ReactNode }) { return <div><dt className="text-text-600">{label}</dt><dd className="mt-1 flex items-center gap-2 font-semibold text-text-900">{icon}{value}</dd></div> }
function PageContainer({ children }: { children: React.ReactNode }) { return <div className="page-shell">{children}</div> }
function StateCard({ children, error = false }: { children: React.ReactNode; error?: boolean }) { return <div className={`rounded-2xl border bg-background-50 p-8 text-center ${error ? 'border-danger-500/30 text-danger-600' : 'border-background-300 text-text-600'}`}>{children}</div> }
function formatCnpj(value: string) { return value.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, '$1.$2.$3/$4-$5') }
function orderBusinessHours(hours: BusinessHour[]) { return WEEK_DAYS.map((day) => hours.find((hour) => hour.dayOfWeek === day.value) ?? { dayOfWeek: day.value, closed: false, openingTime: '09:00', closingTime: '18:00' }) }
function toForm(establishment: Awaited<ReturnType<typeof fetchMyEstablishment>>): EstablishmentUpdateInput { return { name: establishment.name, email: establishment.email ?? '', phone: establishment.phone ?? '', businessHours: orderBusinessHours(establishment.businessHours), minimumAdvanceHours: establishment.minimumAdvanceHours, maximumAdvanceDays: establishment.maximumAdvanceDays } }
