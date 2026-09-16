import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowLeft, CheckCircle2, Clock3 } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { createAppointment, fetchActiveEstablishments, fetchAvailability, fetchCatalogProfessionals, fetchCatalogServices } from '@/features/appointments/appointmentsApi'
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
    return <div className="p-8"><p className="text-danger-600">Código de estabelecimento inválido.</p><Link to="/agendamentos" className="mt-4 inline-block text-brand-700 underline">Voltar</Link></div>
  }

  if (success) {
    return (
      <div className="mx-auto max-w-xl p-5 sm:p-10">
        <section className="rounded-3xl border border-sage-500/30 bg-white p-8 text-center shadow-sm">
          <CheckCircle2 className="mx-auto text-sage-600" size={48} />
          <h1 className="mt-5 font-[var(--font-display)] text-3xl text-brand-900">Agendamento confirmado</h1>
          <p className="mt-3 text-brand-600">{selectedService?.name} com {selectedProfessional?.name}, em {formatDate(date)} às {shortTime(time!)}.</p>
          <button onClick={() => navigate('/agendamentos')} className="primary-button mx-auto mt-7">Ver meus agendamentos</button>
        </section>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-5xl p-5 sm:p-7 lg:p-9">
      <Link to="/agendamentos" className="inline-flex items-center gap-2 text-sm font-semibold text-brand-600 hover:text-brand-800"><ArrowLeft size={17} /> Voltar</Link>
      <h1 className="page-title mt-5">Novo agendamento</h1>
      <p className="page-subtitle">Estabelecimento #{establishmentId}</p>

      <div className="mt-7 grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <section className="space-y-5">
          {(services.isError || professionals.isError) && (
            <div role="alert" className="rounded-2xl border border-danger-500/30 bg-white p-4 text-sm text-danger-600">
              {getApiErrorMessage(services.error ?? professionals.error, 'Não foi possível carregar o catálogo deste estabelecimento.')}
            </div>
          )}
          <BookingStep number="1" title="Escolha o serviço">
            <div className="grid gap-3 sm:grid-cols-2">
              {services.data?.map((service) => <ChoiceButton key={service.id} selected={service.id === serviceId} onClick={() => { setServiceId(service.id); setProfessionalId(null); setTime(null) }} title={service.name} detail={`${formatMoney(service.price)} · ${service.durationMinutes} min`} />)}
              {services.isLoading && <p className="text-brand-500">Carregando serviços...</p>}
              {services.data?.length === 0 && <p className="text-brand-500">Nenhum serviço disponível.</p>}
            </div>
          </BookingStep>

          <BookingStep number="2" title="Escolha o profissional">
            <div className="grid gap-3 sm:grid-cols-2">
              {professionals.data?.map((professional) => <ChoiceButton key={professional.id} selected={professional.id === professionalId} onClick={() => { setProfessionalId(professional.id); setTime(null) }} title={professional.name} detail="Profissional disponível" />)}
              {professionals.isLoading && <p className="text-brand-500">Carregando profissionais...</p>}
              {professionals.data?.length === 0 && <p className="text-brand-500">Nenhum profissional disponível.</p>}
              {!serviceId && <p className="text-brand-500">Selecione um serviço primeiro.</p>}
            </div>
          </BookingStep>

          <BookingStep number="3" title="Escolha a data e o horário">
            <input type="date" min={today()} max={establishment ? addDays(establishment.maximumAdvanceDays) : undefined} value={date} onChange={(event) => { setDate(event.target.value); setTime(null) }} className="rounded-xl border border-brand-200 px-3.5 py-2.5 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100" />
            {establishment && <p className="mt-2 text-sm text-brand-500">Agendamentos entre {establishment.minimumAdvanceHours}h e {establishment.maximumAdvanceDays} dias de antecedência.</p>}
            <div className="mt-4 flex flex-wrap gap-2">
              {availability.isFetching && <p className="text-sm text-brand-500">Buscando horários...</p>}
              {availability.data?.availableTimes.map((availableTime) => (
                <button key={availableTime} type="button" onClick={() => setTime(availableTime)} className={`rounded-xl border px-3.5 py-2 text-sm font-semibold ${time === availableTime ? 'border-brand-600 bg-brand-600 text-white' : 'border-brand-200 bg-white text-brand-700 hover:border-brand-400'}`}>{shortTime(availableTime)}</button>
              ))}
              {availability.data && availability.data.availableTimes.length === 0 && <p className="text-sm text-brand-500">Não há horários disponíveis nesta data.</p>}
              {availability.isError && <p className="text-sm text-danger-600">{getApiErrorMessage(availability.error, 'Não foi possível consultar os horários.')}</p>}
              {!serviceId || !professionalId ? <p className="text-sm text-brand-500">Selecione o serviço e o profissional para consultar os horários.</p> : null}
            </div>
          </BookingStep>
        </section>

        <aside className="h-fit rounded-2xl border border-brand-100 bg-white p-5 shadow-sm lg:sticky lg:top-6">
          <h2 className="font-[var(--font-display)] text-xl text-brand-900">Resumo</h2>
          <Summary label="Serviço" value={selectedService?.name} />
          <Summary label="Profissional" value={selectedProfessional?.name} />
          <Summary label="Data" value={date ? formatDate(date) : undefined} />
          <Summary label="Horário" value={time ? shortTime(time) : undefined} />
          {selectedService && <p className="mt-4 flex items-center gap-2 border-t border-brand-100 pt-4 text-sm text-brand-600"><Clock3 size={16} /> Duração de {selectedService.durationMinutes} minutos</p>}
          {createMutation.isError && <p role="alert" className="mt-4 text-sm text-danger-600">{getApiErrorMessage(createMutation.error, 'Não foi possível confirmar o agendamento.')}</p>}
          <button disabled={!serviceId || !professionalId || !date || !time || createMutation.isPending} onClick={() => createMutation.mutate()} className="primary-button mt-5 w-full justify-center disabled:cursor-not-allowed disabled:opacity-50">{createMutation.isPending ? 'Confirmando...' : 'Confirmar agendamento'}</button>
        </aside>
      </div>
    </div>
  )
}

function BookingStep({ number, title, children }: { number: string; title: string; children: React.ReactNode }) { return <section className="rounded-2xl border border-brand-100 bg-white p-5 shadow-sm"><div className="mb-4 flex items-center gap-3"><span className="flex h-8 w-8 items-center justify-center rounded-full bg-brand-100 text-sm font-bold text-brand-700">{number}</span><h2 className="text-lg font-semibold text-brand-900">{title}</h2></div>{children}</section> }
function ChoiceButton({ selected, onClick, title, detail }: { selected: boolean; onClick: () => void; title: string; detail: string }) { return <button type="button" onClick={onClick} className={`rounded-xl border p-4 text-left ${selected ? 'border-brand-600 bg-brand-50 ring-1 ring-brand-600' : 'border-brand-100 hover:border-brand-300'}`}><span className="block font-semibold text-brand-900">{title}</span><span className="mt-1 block text-sm text-brand-600">{detail}</span></button> }
function Summary({ label, value }: { label: string; value?: string }) { return <div className="mt-4"><p className="text-xs font-semibold uppercase tracking-wide text-brand-400">{label}</p><p className="mt-1 text-sm text-brand-800">{value ?? 'Não selecionado'}</p></div> }
function today() { const date = new Date(); const offset = date.getTimezoneOffset(); return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 10) }
function addDays(days: number) { const date = new Date(); date.setDate(date.getDate() + days); const offset = date.getTimezoneOffset(); return new Date(date.getTime() - offset * 60000).toISOString().slice(0, 10) }
function normalizeTime(value: string) { return value.length === 5 ? `${value}:00` : value }
function shortTime(value: string) { return value.slice(0, 5) }
function formatDate(value: string) { return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long', timeZone: 'UTC' }).format(new Date(`${value}T12:00:00Z`)) }
function formatMoney(value: number) { return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value) }
