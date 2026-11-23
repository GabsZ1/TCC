/** Permite administrar dados, horários e regras operacionais do estabelecimento. */
import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Building2, CalendarClock, Save, CalendarOff, Pause, Play, RotateCcw } from 'lucide-react'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { EstablishmentSectionHeader } from '../components/EstablishmentSectionHeader'
import { getApiErrorMessage } from '@/lib/apiError'
import { formatCnpj } from '@/lib/format'
import { allowMyEstablishmentBookings, blockMyEstablishmentBookings, fetchMyEstablishment, pauseMyEstablishment, resumeMyEstablishment, updateMyEstablishment, type BusinessHour, type Establishment, type EstablishmentUpdateInput, type WeekDay } from '@/features/establishment/api/establishmentApi'

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

/** Mantém separados o formulário cadastral e as operações de estado do estabelecimento. */
function EstablishmentContent({ establishment }: { establishment: Awaited<ReturnType<typeof fetchMyEstablishment>> }) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<EstablishmentUpdateInput>(() => toForm(establishment))
  const [message, setMessage] = useState<string | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [feedbackPlacement, setFeedbackPlacement] = useState<'form' | 'state'>('state')
  const [confirmation, setConfirmation] = useState<'block' | 'pause' | null>(null)

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
  // Uma mudança operacional afeta outras consultas; invalida os dados dependentes após salvar.
  const stateMutation = useMutation({
    mutationFn: ({ operation }: { operation: () => Promise<Establishment>; successMessage: string }) => operation(),
    onSuccess: (updated, variables) => {
      queryClient.setQueryData(QUERY_KEY, updated)
      queryClient.invalidateQueries()
      setConfirmation(null)
      setMessage(variables.successMessage)
      setErrorMessage(null)
    },
    onError: (error) => {
      setConfirmation(null)
      setMessage(null)
      setErrorMessage(getApiErrorMessage(error, 'Não foi possível alterar o estado do estabelecimento.'))
    },
  })

  function updateField(field: 'name' | 'email', value: string) { setForm((current) => ({ ...current, [field]: value })) }
  function updateBusinessHour(dayOfWeek: WeekDay, changes: Partial<BusinessHour>) {
    setForm((current) => ({ ...current, businessHours: current.businessHours.map((hour) => hour.dayOfWeek === dayOfWeek ? { ...hour, ...changes } : hour) }))
  }
  function submit(event: FormEvent) {
    event.preventDefault()
    if (!establishment?.active) return
    setFeedbackPlacement('form')
    setMessage(null)
    setErrorMessage(null)
    if (form.minimumAdvanceHours < 0 || form.minimumAdvanceHours > 168 || form.maximumAdvanceDays < 1 || form.maximumAdvanceDays > 365) {
      setErrorMessage('Confira os limites de antecedência informados.')
      return
    }
    updateMutation.mutate(form)
  }

  function changeState(operation: () => Promise<Establishment>, successMessage: string) {
    setFeedbackPlacement('state')
    setMessage(null)
    setErrorMessage(null)
    stateMutation.mutate({ operation, successMessage })
  }

  return (
    <PageContainer>
      <header className="flex flex-col gap-4 border-b border-background-300 pb-7 sm:flex-row sm:items-start sm:justify-between">
        <div><p className="eyebrow">Administração</p><h1 className="page-title mt-2">Configurações do estabelecimento</h1><p className="page-subtitle">Gerencie os dados cadastrais, a agenda e o estado operacional do estabelecimento.</p></div>
        <div className="flex flex-wrap gap-2">
          <span className="status-pill bg-success-50 text-success-700">Ativo</span>
          {establishment.paused && <span className="status-pill bg-warning-100 text-warning-700">Pausado{establishment.pausedBySystem ? ' pelo sistema' : ''}</span>}
          {establishment.bookingsBlocked && <span className="status-pill bg-danger-50 text-danger-600">Agendamentos bloqueados{establishment.bookingsBlockedBySystem ? ' pelo sistema' : ''}</span>}
        </div>
      </header>
      {(establishment.pausedBySystem || establishment.bookingsBlockedBySystem) && <p className="mt-5 rounded-xl border border-warning-500/30 bg-warning-100 px-4 py-3 text-sm text-warning-700">Uma ou mais restrições foram aplicadas pelo administrador do sistema e somente ele poderá removê-las.</p>}

      <div className="mt-7 grid gap-6 xl:grid-cols-[minmax(0,1fr)_18rem]">
        <form onSubmit={submit} className="space-y-5">
          <fieldset disabled={updateMutation.isPending || stateMutation.isPending} className="space-y-5">
            <section className="surface-card p-5 sm:p-6">
              <EstablishmentSectionHeader icon={<Building2 size={20} />} title="Dados do estabelecimento" description="Informações usadas para identificar e entrar em contato com o estabelecimento." />
              <div className="mt-6 grid gap-5 sm:grid-cols-2">
                <Field label="Nome do estabelecimento" value={form.name} onChange={(value) => updateField('name', value)} maxLength={150} required className="sm:col-span-2" />
                <label className="block"><span className="text-sm font-medium text-text-800">CNPJ</span><input value={formatCnpj(establishment.cnpj)} readOnly className="mt-2 w-full rounded-xl border border-background-300 bg-background-100 px-3.5 py-3 text-text-700" /></label>
                <Field label="E-mail" value={form.email} type="email" onChange={(value) => updateField('email', value)} maxLength={150} />
              </div>
              <p className="mt-4 text-xs text-text-600">O CNPJ é definido no cadastro inicial e não pode ser alterado.</p>
            </section>

            <section className="surface-card p-5 sm:p-6">
              <EstablishmentSectionHeader icon={<CalendarClock size={20} />} title="Política de agendamento" description="Defina com quanta antecedência os clientes podem reservar um horário." />
              <div className="mt-6 grid gap-5 sm:grid-cols-2">
                <label className="block"><span className="text-sm font-medium text-text-800">Antecedência mínima</span><div className="relative mt-2"><input type="text" inputMode="numeric" pattern="[0-9]+" maxLength={3} required value={form.minimumAdvanceHours} onChange={(event) => setForm((current) => ({ ...current, minimumAdvanceHours: Number(event.target.value.replace(/\D/g, '')) }))} className="w-full rounded-xl border border-background-300 py-3 pl-3.5 pr-16 text-text-900 outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100" /><span className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-sm text-text-600">horas</span></div><span className="mt-2 block text-xs text-text-600">Evita reservas muito próximas do horário atual.</span></label>
                <label className="block"><span className="text-sm font-medium text-text-800">Antecedência máxima</span><div className="relative mt-2"><input type="text" inputMode="numeric" pattern="[0-9]+" maxLength={3} required value={form.maximumAdvanceDays} onChange={(event) => setForm((current) => ({ ...current, maximumAdvanceDays: Number(event.target.value.replace(/\D/g, '')) }))} className="w-full rounded-xl border border-background-300 py-3 pl-3.5 pr-14 text-text-900 outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100" /><span className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-sm text-text-600">dias</span></div><span className="mt-2 block text-xs text-text-600">Limita até quando a agenda fica aberta para reservas.</span></label>
              </div>
              <div className="mt-5 rounded-xl bg-secondary-50 px-4 py-3 text-sm text-text-700">Com a configuração atual, clientes podem agendar entre <strong>{form.minimumAdvanceHours} horas</strong> e <strong>{form.maximumAdvanceDays} dias</strong> de antecedência.</div>
            </section>

            <section className="surface-card p-5 sm:p-6">
              <EstablishmentSectionHeader icon={<CalendarClock size={20} />} title="Horários de funcionamento" description="Defina os horários de cada dia ou marque o estabelecimento como fechado." />
              <div className="mt-5 space-y-3">{form.businessHours.map((hour) => <BusinessHourRow key={hour.dayOfWeek} hour={hour} onChange={(changes) => updateBusinessHour(hour.dayOfWeek, changes)} />)}</div>
            </section>
          </fieldset>

          {feedbackPlacement === 'form' && message && <p role="status" className="rounded-xl bg-success-50 px-4 py-3 text-sm text-success-600">{message}</p>}
          {feedbackPlacement === 'form' && errorMessage && <p role="alert" className="rounded-xl border border-danger-500/30 bg-danger-500/5 px-4 py-3 text-sm text-danger-600">{errorMessage}</p>}
          <div className="surface-card flex justify-end p-4">
            <button type="submit" disabled={updateMutation.isPending || stateMutation.isPending} className="primary-button w-full justify-center disabled:cursor-not-allowed disabled:opacity-60 sm:w-auto"><Save size={18} /> {updateMutation.isPending ? 'Salvando...' : 'Salvar alterações'}</button>
          </div>
        </form>

        <aside className="surface-card h-fit p-5">
          <h2 className="text-lg font-semibold text-text-900">Informações</h2>
          {feedbackPlacement === 'state' && message && <p role="status" className="mt-4 rounded-xl bg-success-50 px-4 py-3 text-sm text-success-600">{message}</p>}
          {feedbackPlacement === 'state' && errorMessage && <p role="alert" className="mt-4 rounded-xl border border-danger-500/30 bg-danger-500/5 px-4 py-3 text-sm text-danger-600">{errorMessage}</p>}
          <dl className="mt-5 space-y-4 text-sm"><Info label="Funcionamento" value="Configuração semanal" icon={<CalendarClock size={16} />} /></dl>
          <div className="mt-6 space-y-6 border-t border-background-300 pt-5">
            <OperationalAction
              title={establishment.bookingsBlocked ? 'Liberar agendamentos' : 'Bloquear novos agendamentos'}
              description={establishment.bookingsBlocked ? 'Volta a permitir novas reservas e remarcações.' : 'Impede novas reservas e remarcações sem alterar os atendimentos existentes.'}
              icon={establishment.bookingsBlocked ? <RotateCcw size={17} /> : <CalendarOff size={17} />}
              disabled={stateMutation.isPending || establishment.bookingsBlockedBySystem}
              onClick={() => establishment.bookingsBlocked
                ? changeState(allowMyEstablishmentBookings, 'Novos agendamentos liberados.')
                : setConfirmation('block')}
            />
            <OperationalAction
              title={establishment.paused ? 'Retomar estabelecimento' : 'Pausar estabelecimento'}
              description={establishment.paused ? 'Libera novamente o acesso dos funcionários.' : 'Exige que todos os atendimentos estejam cancelados ou concluídos e bloqueia o acesso dos funcionários.'}
              icon={establishment.paused ? <Play size={17} /> : <Pause size={17} />}
              disabled={stateMutation.isPending || establishment.pausedBySystem}
              onClick={() => establishment.paused
                ? changeState(resumeMyEstablishment, 'Estabelecimento retomado.')
                : setConfirmation('pause')}
            />
          </div>
        </aside>
      </div>
      {confirmation === 'block' && <ConfirmDialog title="Bloquear novos agendamentos" message={`Bloquear novas reservas e remarcações em “${establishment.name}”? Os atendimentos existentes não serão alterados.`} confirmLabel="Bloquear agendamentos" isLoading={stateMutation.isPending} onConfirm={() => changeState(blockMyEstablishmentBookings, 'Novos agendamentos bloqueados.')} onCancel={() => setConfirmation(null)} />}
      {confirmation === 'pause' && <ConfirmDialog title="Pausar estabelecimento" message={`Pausar “${establishment.name}”? A operação só será concluída se não houver nenhum atendimento confirmado, e os funcionários não conseguirão acessar o sistema.`} confirmLabel="Pausar estabelecimento" isLoading={stateMutation.isPending} onConfirm={() => changeState(pauseMyEstablishment, 'Estabelecimento pausado.')} onCancel={() => setConfirmation(null)} />}
    </PageContainer>
  )
}

function Field({ label, value, onChange, type = 'text', maxLength, required = false, className = '' }: { label: string; value: string; onChange: (value: string) => void; type?: string; maxLength?: number; required?: boolean; className?: string }) { return <label className={`block ${className}`}><span className="text-sm font-medium text-text-800">{label}</span><input type={type} value={value} maxLength={maxLength} required={required} onChange={(event) => onChange(event.target.value)} className="mt-2 w-full rounded-xl border border-background-300 px-3.5 py-3 text-text-900 outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100 disabled:bg-background-100" /></label> }
function OperationalAction({ title, description, icon, disabled, onClick }: { title: string; description: string; icon: React.ReactNode; disabled: boolean; onClick: () => void }) { return <div><h3 className="font-semibold text-text-900">{title}</h3><p className="mt-1.5 text-sm leading-6 text-text-600">{description}</p><button type="button" disabled={disabled} onClick={onClick} className="mt-3 inline-flex items-center gap-2 rounded-xl border border-primary-300 px-4 py-2 text-sm font-semibold text-primary-700 hover:bg-primary-50 disabled:cursor-not-allowed disabled:opacity-45">{icon}{title}</button></div> }
function BusinessHourRow({ hour, onChange }: { hour: BusinessHour; onChange: (changes: Partial<BusinessHour>) => void }) { const label = WEEK_DAYS.find((day) => day.value === hour.dayOfWeek)?.label ?? hour.dayOfWeek; return <div className="grid items-center gap-3 rounded-xl bg-background-50 p-3 sm:grid-cols-[9rem_auto_1fr_1fr]"><span className="text-sm font-medium text-text-800">{label}</span><label className="flex items-center gap-2 text-sm text-text-600"><input type="checkbox" checked={hour.closed} onChange={(event) => onChange({ closed: event.target.checked, openingTime: event.target.checked ? null : '09:00', closingTime: event.target.checked ? null : '18:00' })} /> Fechado</label><input aria-label={`Abertura de ${label}`} type="time" required={!hour.closed} disabled={hour.closed} value={hour.openingTime?.slice(0, 5) ?? ''} onChange={(event) => onChange({ openingTime: event.target.value })} className="rounded-lg border border-background-300 px-3 py-2 disabled:bg-background-200" /><input aria-label={`Fechamento de ${label}`} type="time" required={!hour.closed} disabled={hour.closed} value={hour.closingTime?.slice(0, 5) ?? ''} onChange={(event) => onChange({ closingTime: event.target.value })} className="rounded-lg border border-background-300 px-3 py-2 disabled:bg-background-200" /></div> }
function Info({ label, value, icon }: { label: string; value: string; icon?: React.ReactNode }) { return <div><dt className="text-text-600">{label}</dt><dd className="mt-1 flex items-center gap-2 font-semibold text-text-900">{icon}{value}</dd></div> }
function PageContainer({ children }: { children: React.ReactNode }) { return <div className="page-shell">{children}</div> }
function StateCard({ children, error = false }: { children: React.ReactNode; error?: boolean }) { return <div className={`rounded-2xl border bg-background-50 p-8 text-center ${error ? 'border-danger-500/30 text-danger-600' : 'border-background-300 text-text-600'}`}>{children}</div> }
/** Garante uma linha para cada dia, mesmo quando o backend ainda não possui horário cadastrado. */
function orderBusinessHours(hours: BusinessHour[]) { return WEEK_DAYS.map((day) => hours.find((hour) => hour.dayOfWeek === day.value) ?? { dayOfWeek: day.value, closed: false, openingTime: '09:00', closingTime: '18:00' }) }
function toForm(establishment: Awaited<ReturnType<typeof fetchMyEstablishment>>): EstablishmentUpdateInput { return { name: establishment.name, email: establishment.email ?? '', businessHours: orderBusinessHours(establishment.businessHours), minimumAdvanceHours: establishment.minimumAdvanceHours, maximumAdvanceDays: establishment.maximumAdvanceDays } }
