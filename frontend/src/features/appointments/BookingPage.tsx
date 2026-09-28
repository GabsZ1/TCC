import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, Check, CheckCircle2, Clock3, MapPin, Scissors, UserRound } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { createAppointment, fetchActiveEstablishments, fetchAvailability, fetchCatalogProfessionals, fetchCatalogServices } from '@/features/appointments/appointmentsApi'
import { EmployeeAvatar } from '@/features/employees/components/EmployeeAvatar'
import { getApiErrorMessage } from '@/lib/apiError'

export function BookingPage() {
  const { establishmentId: rawId } = useParams()
  const establishmentId = Number(rawId)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [serviceId, setServiceId] = useState<number | null>(null)
  const [professionalId, setProfessionalId] = useState<number | null>(null)
  const [date, setDate] = useState('')
  const [time, setTime] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  const services = useQuery({ queryKey: ['catalog-services', establishmentId], queryFn: () => fetchCatalogServices(establishmentId), enabled: establishmentId > 0 })
  const establishments = useQuery({ queryKey: ['active-establishments'], queryFn: fetchActiveEstablishments })
  const professionals = useQuery({ queryKey: ['catalog-professionals', establishmentId, serviceId], queryFn: () => fetchCatalogProfessionals(establishmentId, serviceId), enabled: establishmentId > 0 && !!serviceId })
  const availability = useQuery({
    queryKey: ['availability', establishmentId, serviceId, professionalId, date],
    queryFn: () => fetchAvailability(establishmentId, serviceId!, professionalId!, date),
    enabled: !!serviceId && !!professionalId && !!date,
    retry: false,
  })
  const selectedService = useMemo(() => services.data?.find((item) => item.id === serviceId), [services.data, serviceId])
  const selectedProfessional = useMemo(() => professionals.data?.find((item) => item.id === professionalId), [professionals.data, professionalId])
  const establishment = establishments.data?.find((item) => item.id === establishmentId)

  const createMutation = useMutation({
    mutationFn: () => createAppointment(establishmentId, serviceId!, professionalId!, `${date}T${normalizeTime(time!)}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-appointments'] })
      setSuccess(true)
    },
  })

  if (!Number.isInteger(establishmentId) || establishmentId <= 0) {
    return <div className="page-shell"><div className="surface-card max-w-lg p-7"><p className="text-danger-600">Código de estabelecimento inválido.</p><Link to="/agendamentos" className="secondary-button mt-5"><ArrowLeft size={16} /> Voltar</Link></div></div>
  }

  if (success) {
    return (
      <div className="page-shell flex min-h-[75vh] items-center justify-center">
        <section className="surface-card w-full max-w-xl p-8 text-center sm:p-10">
          <span className="mx-auto grid size-16 place-items-center rounded-2xl bg-success-50 text-success-600"><CheckCircle2 size={32} /></span>
          <p className="eyebrow mt-6 justify-center">Reserva concluída</p>
          <h1 className="mt-2 font-[var(--font-display)] text-3xl font-semibold tracking-[-0.04em] text-text-950">Agendamento confirmado</h1>
          <p className="mx-auto mt-3 max-w-md text-sm leading-6 text-text-600">{selectedService?.name} com {selectedProfessional?.name}, em {formatDate(date)} às {shortTime(time!)}.</p>
          <button onClick={() => navigate('/agendamentos')} className="primary-button mx-auto mt-7">Ver meus agendamentos</button>
        </section>
      </div>
    )
  }

  return (
    <div className="page-shell">
      <Link to="/agendamentos" className="ghost-button -ml-3 w-fit"><ArrowLeft size={16} /> Voltar</Link>
      <div className="mt-5 flex flex-col gap-2">
        <p className="eyebrow">Novo horário</p>
        <h1 className="page-title">Agendar atendimento</h1>
        <p className="page-subtitle flex items-center gap-2"><MapPin size={15} /> {establishment?.name ?? `Estabelecimento #${establishmentId}`}</p>
      </div>

      <div className="mt-8 grid gap-6 lg:grid-cols-[minmax(0,1fr)_21rem]">
        <section className="surface-card divide-y divide-background-300 overflow-hidden">
          {(services.isError || professionals.isError) && (
            <div role="alert" className="rounded-xl border border-danger-500/20 bg-danger-50 p-4 text-sm text-danger-600">
              {getApiErrorMessage(services.error ?? professionals.error, 'Não foi possível carregar o catálogo deste estabelecimento.')}
            </div>
          )}

          <BookingStep number="01" title="Escolha o serviço" subtitle="O serviço define duração e valor do atendimento.">
            <div className="grid gap-3 sm:grid-cols-2">
              {services.data?.map((service) => <ChoiceButton key={service.id} selected={service.id === serviceId} onClick={() => { setServiceId(service.id); setProfessionalId(null); setTime(null) }} icon={<Scissors size={17} />} title={service.name} detail={`${formatMoney(service.price)} · ${service.durationMinutes} min`} />)}
              {services.isLoading && <Hint>Carregando serviços...</Hint>}
              {services.data?.length === 0 && <Hint>Nenhum serviço disponível.</Hint>}
            </div>
          </BookingStep>

          <BookingStep number="02" title="Escolha o profissional" subtitle="Mostramos apenas profissionais habilitados para o serviço selecionado.">
            <div className="grid gap-3 sm:grid-cols-2">
              {professionals.data?.map((professional) => <ChoiceButton key={professional.id} selected={professional.id === professionalId} onClick={() => { setProfessionalId(professional.id); setTime(null) }} avatar={<EmployeeAvatar id={professional.id} name={professional.name} hasPhoto={professional.hasPhoto} catalogEstablishmentId={establishmentId} />} title={professional.name} detail="Profissional disponível" />)}
              {professionals.isLoading && <Hint>Carregando profissionais...</Hint>}
              {professionals.data?.length === 0 && serviceId && <Hint>Nenhum profissional disponível.</Hint>}
              {!serviceId && <Hint>Selecione um serviço primeiro.</Hint>}
            </div>
          </BookingStep>

          <BookingStep number="03" title="Data e horário" subtitle="Escolha uma data e depois um dos horários livres.">
            <input type="date" min={today()} max={establishment ? addDays(establishment.maximumAdvanceDays) : undefined} value={date} onChange={(event) => { setDate(event.target.value); setTime(null) }} className="field-control mt-0 max-w-xs" />
            {establishment && <p className="mt-2 text-xs leading-5 text-text-500">Reservas permitidas entre {establishment.minimumAdvanceHours}h e {establishment.maximumAdvanceDays} dias de antecedência.</p>}
            <div className="mt-5 flex flex-wrap gap-2">
              {availability.isFetching && <Hint>Buscando horários...</Hint>}
              {availability.data?.availableTimes.map((availableTime) => (
                <button key={availableTime} type="button" onClick={() => setTime(availableTime)} className={`rounded-[0.68rem] border px-3.5 py-2.5 text-sm font-semibold ${time === availableTime ? 'border-primary-600 bg-primary-600 text-on-primary shadow-sm' : 'border-background-300 bg-background-50 text-text-700 hover:border-primary-300 hover:bg-primary-50'}`}>{shortTime(availableTime)}</button>
              ))}
              {availability.data && availability.data.availableTimes.length === 0 && <Hint>Não há horários disponíveis nesta data.</Hint>}
              {availability.isError && <p className="text-sm text-danger-600">{getApiErrorMessage(availability.error, 'Não foi possível consultar os horários.')}</p>}
              {(!serviceId || !professionalId) && <Hint>Selecione o serviço e o profissional para consultar horários.</Hint>}
            </div>
          </BookingStep>
        </section>

        <aside className="surface-card h-fit overflow-hidden lg:sticky lg:top-6">
          <div className="border-b border-background-300 bg-background-100/60 p-5">
            <p className="text-[0.65rem] font-bold uppercase tracking-[0.13em] text-text-400">Resumo</p>
            <h2 className="mt-1 font-[var(--font-display)] text-xl font-semibold tracking-[-0.03em] text-text-950">Seu agendamento</h2>
          </div>
          <div className="p-5">
            <Summary icon={<Scissors size={15} />} label="Serviço" value={selectedService?.name} />
            <Summary icon={<UserRound size={15} />} label="Profissional" value={selectedProfessional?.name} />
            <Summary icon={<Clock3 size={15} />} label="Data e horário" value={date ? `${formatDate(date)}${time ? ` · ${shortTime(time)}` : ''}` : undefined} />
            {selectedService && <div className="mt-5 rounded-[0.75rem] bg-primary-50 p-4"><p className="text-xs font-semibold text-primary-700">Valor do serviço</p><p className="mt-1 text-xl font-bold tracking-[-0.03em] text-text-950">{formatMoney(selectedService.price)}</p><p className="mt-1 text-xs text-text-500">Duração estimada: {selectedService.durationMinutes} min</p></div>}
            {createMutation.isError && <p role="alert" className="mt-4 text-sm text-danger-600">{getApiErrorMessage(createMutation.error, 'Não foi possível confirmar o agendamento.')}</p>}
            <button disabled={!serviceId || !professionalId || !date || !time || createMutation.isPending} onClick={() => createMutation.mutate()} className="primary-button mt-5 w-full">{createMutation.isPending ? 'Confirmando...' : 'Confirmar agendamento'}</button>
          </div>
        </aside>
      </div>
    </div>
  )
}

function BookingStep({ number, title, subtitle, children }: { number: string; title: string; subtitle: string; children: React.ReactNode }) {
  return <section className="p-5 sm:p-6"><div className="mb-5 flex items-start gap-4"><span className="pt-0.5 font-mono text-[0.68rem] font-extrabold tracking-[0.12em] text-primary-700">{number}</span><div><h2 className="text-lg font-extrabold tracking-[-0.03em] text-text-950">{title}</h2><p className="mt-1 text-sm text-text-500">{subtitle}</p></div></div>{children}</section>
}
/** Opção selecionável. Com `avatar` (foto do profissional), a foto fica sempre visível e a seleção aparece num selo. */
function ChoiceButton({ selected, onClick, icon, avatar, title, detail }: { selected: boolean; onClick: () => void; icon?: React.ReactNode; avatar?: React.ReactNode; title: string; detail: string }) {
  const marker = avatar
    ? <span className="relative shrink-0">{avatar}{selected && <span className="absolute -bottom-1 -right-1 grid size-4 place-items-center rounded-full bg-primary-600 text-on-primary ring-2 ring-primary-50"><Check size={10} strokeWidth={3} /></span>}</span>
    : <span className={`grid size-9 shrink-0 place-items-center rounded-[0.65rem] ${selected ? 'bg-primary-600 text-on-primary' : 'bg-background-200 text-text-600 group-hover:bg-primary-100 group-hover:text-primary-700'}`}>{selected ? <Check size={16} /> : icon}</span>
  return <button type="button" onClick={onClick} className={`group flex items-start gap-3 rounded-[0.75rem] border p-4 text-left ${selected ? 'border-primary-400 bg-primary-50 ring-2 ring-primary-100' : 'border-background-300 bg-background-50 hover:border-primary-300 hover:bg-primary-50/60'}`}>{marker}<span><span className="block font-semibold text-text-900">{title}</span><span className="mt-1 block text-sm text-text-500">{detail}</span></span></button>
}
function Summary({ icon, label, value }: { icon: React.ReactNode; label: string; value?: string }) { return <div className="mb-4 flex gap-3"><span className="mt-0.5 text-text-400">{icon}</span><div><p className="text-[0.63rem] font-bold uppercase tracking-[0.11em] text-text-400">{label}</p><p className="mt-1 text-sm font-medium text-text-800">{value ?? 'Não selecionado'}</p></div></div> }
function Hint({ children }: { children: React.ReactNode }) { return <p className="text-sm text-text-500">{children}</p> }
function today() { const date = new Date(); const offset = date.getTimezoneOffset(); return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 10) }
function addDays(days: number) { const date = new Date(); date.setDate(date.getDate() + days); const offset = date.getTimezoneOffset(); return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 10) }
function normalizeTime(value: string) { return value.length === 5 ? `${value}:00` : value }
function shortTime(value: string) { return value.slice(0, 5) }
function formatDate(value: string) { return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long', timeZone: 'UTC' }).format(new Date(`${value}T12:00:00Z`)) }
function formatMoney(value: number) { return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value) }
