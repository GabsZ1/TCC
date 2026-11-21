/** Mostra os agendamentos do cliente e oferece as ações permitidas para cada estado. */
import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowRight, CalendarPlus, ChevronLeft, ChevronRight, Clock3, Eye, MapPin, Scissors, Search, UserRound } from 'lucide-react'
import { IMaskInput } from 'react-imask'
import { useNavigate } from 'react-router-dom'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { AppointmentEditModal } from '@/features/appointments/components/AppointmentEditModal'
import { EstablishmentDetailsModal } from '@/features/appointments/EstablishmentDetailsModal'
import { cancelAppointment, fetchActiveEstablishments, fetchNearbyEstablishmentsByPostalCode, fetchMyAppointmentPage, type ActiveEstablishment, type Appointment } from '@/features/appointments/appointmentsApi'
import { getApiErrorMessage } from '@/lib/apiError'
import { EstablishmentImage } from '@/features/establishment/EstablishmentImage'
import { formatBusinessDate, formatBusinessDayNumber, formatBusinessMonthShort, formatBusinessWeekday, isBusinessLocalDateTimeFuture } from '@/lib/dateTime'

const QUERY_KEY = ['my-appointments']
const ESTABLISHMENTS_PER_PAGE = 6
type AppointmentSection = 'upcoming' | 'history'

/** Separa próximas reservas e histórico e apresenta somente as ações permitidas em cada estado. */
export function AppointmentsPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [section, setSection] = useState<AppointmentSection>('upcoming')
  const [appointmentPage, setAppointmentPage] = useState(0)
  const [details, setDetails] = useState<ActiveEstablishment | null>(null)
  const [cancelling, setCancelling] = useState<Appointment | null>(null)
  const [editing, setEditing] = useState<Appointment | null>(null)
  const [cancellationError, setCancellationError] = useState<string | null>(null)
  const [establishmentSearch, setEstablishmentSearch] = useState('')
  const [establishmentPage, setEstablishmentPage] = useState(0)
  const [proximityActive, setProximityActive] = useState(false)
  const [locationMessage, setLocationMessage] = useState<string | null>(null)
  const [postalCode, setPostalCode] = useState('')
  const appointments = useQuery({ queryKey: [...QUERY_KEY, section, appointmentPage], queryFn: () => fetchMyAppointmentPage(section === 'history', appointmentPage) })
  const upcoming = useQuery({ queryKey: [...QUERY_KEY, 'upcoming', 0], queryFn: () => fetchMyAppointmentPage(false, 0) })
  const establishments = useQuery({ queryKey: ['active-establishments'], queryFn: fetchActiveEstablishments })
  const nearby = useMutation({
    mutationFn: fetchNearbyEstablishmentsByPostalCode,
    onSuccess: () => { setProximityActive(true); setLocationMessage(null); setEstablishmentPage(0) },
    onError: (error) => {
      setProximityActive(false)
      const reason = getApiErrorMessage(error, 'Não foi possível buscar por esse CEP. Tente novamente.')
      setLocationMessage(`${reason} A lista de estabelecimentos continua disponível.`)
    },
  })
  const establishmentData = proximityActive && nearby.data ? nearby.data : establishments.data
  const displayedAppointments = appointments.data?.content ?? []

  // Lista de estabelecimentos: busca por nome (US 2.23, CT04) e paginação de 6 em 6.
  const searchTerm = normalizeText(establishmentSearch)
  const filteredEstablishments = (establishmentData ?? []).filter((establishment) => normalizeText(establishment.name).includes(searchTerm))
  const establishmentPages = Math.max(1, Math.ceil(filteredEstablishments.length / ESTABLISHMENTS_PER_PAGE))
  const currentEstablishmentPage = Math.min(establishmentPage, establishmentPages - 1)
  const visibleEstablishments = filteredEstablishments.slice(
    currentEstablishmentPage * ESTABLISHMENTS_PER_PAGE,
    (currentEstablishmentPage + 1) * ESTABLISHMENTS_PER_PAGE,
  )

  function searchByPostalCode(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (postalCode.length !== 8) { setProximityActive(false); setLocationMessage('Informe um CEP com 8 números. A lista de estabelecimentos continua disponível.'); return }
    setProximityActive(false)
    setLocationMessage(null)
    nearby.mutate(postalCode)
  }
  const cancelMutation = useMutation({
    mutationFn: cancelAppointment,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEY })
      setCancelling(null)
      setSection('history')
      setCancellationError(null)
    },
    onError: (error) => setCancellationError(getApiErrorMessage(error, 'Não foi possível cancelar o agendamento.')),
  })

  return (
    <div className="page-shell">
      <div className="grid gap-7 xl:grid-cols-[minmax(0,1fr)_22rem]">
        <section>
          <p className="eyebrow">Sua agenda</p>
          <h1 className="page-title mt-2">Meus agendamentos</h1>
          <p className="page-subtitle">Acompanhe seus próximos horários e consulte o histórico quando precisar.</p>

          {cancellationError && <p className="mt-5 rounded-xl border border-danger-500/20 bg-danger-50 px-4 py-3 text-sm text-danger-600">{cancellationError}</p>}
          <Tabs section={section} onChange={(value) => { setSection(value); setAppointmentPage(0) }} upcomingCount={upcoming.data?.totalElements ?? 0} />

          <div className="mt-6 space-y-3">
            {appointments.isLoading && <EmptyState>Carregando agendamentos...</EmptyState>}
            {appointments.isError && <EmptyState error>{getApiErrorMessage(appointments.error, 'Não foi possível carregar seus agendamentos.')}</EmptyState>}
            {!appointments.isLoading && !appointments.isError && displayedAppointments.length === 0 && <EmptyState>{section === 'upcoming' ? 'Você não possui próximos agendamentos.' : 'Você ainda não possui agendamentos no histórico.'}</EmptyState>}
            {displayedAppointments.map((appointment) => (
              <article key={appointment.id} className="surface-card overflow-hidden">
                <div className="grid gap-0 md:grid-cols-[7.5rem_minmax(0,1fr)_auto]">
                  <div className="flex flex-row items-center gap-3 border-b border-background-300 bg-background-100/70 px-5 py-4 md:flex-col md:items-start md:justify-center md:border-b-0 md:border-r md:px-5">
                    <span className="text-[0.65rem] font-bold uppercase tracking-[0.12em] text-text-400">{weekday(appointment.startsAt)}</span>
                    <div className="flex items-baseline gap-1 md:block">
                      <p className="font-[var(--font-display)] text-3xl font-semibold leading-none tracking-[-0.04em] text-text-950">{dayNumber(appointment.startsAt)}</p>
                      <p className="mt-1 text-sm font-semibold text-text-600">{monthShort(appointment.startsAt)}</p>
                    </div>
                    <p className="ml-auto flex items-center gap-1 text-sm font-semibold text-primary-700 md:ml-0 md:mt-2"><Clock3 size={14} /> {formatTime(appointment.startsAt)}</p>
                  </div>

                  <div className="p-5">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <div>
                        <div className="flex items-center gap-2"><Scissors size={17} className="text-primary-600" /><h2 className="text-lg font-semibold tracking-[-0.02em] text-text-950">{appointment.serviceName}</h2></div>
                        <p className="mt-2 flex items-center gap-2 text-sm text-text-600"><MapPin size={15} /> {appointment.establishmentName}</p>
                        <p className="mt-1 flex items-center gap-2 text-sm text-text-600"><UserRound size={15} /> {appointment.professionalName}</p>
                      </div>
                      <StatusBadge status={appointment.status} />
                    </div>
                    <div className="mt-4 flex flex-wrap gap-x-5 gap-y-2 text-sm">
                      <span className="font-semibold text-text-900">{formatMoney(appointment.agreedPrice)}</span>
                      <span className="text-text-500">{appointment.durationMinutes} min</span>
                      <span className="text-text-500">até {formatTime(appointment.endsAt)}</span>
                    </div>
                    {appointment.status === 'INVALIDATED' && appointment.invalidationReason && <p className="mt-3 rounded-lg bg-warning-50 px-3 py-2 text-xs leading-5 text-warning-700">Motivo: {appointment.invalidationReason}</p>}
                  </div>

                  {appointment.status === 'CONFIRMED' && isFuture(appointment.startsAt) && (
                    <div className="flex items-center gap-2 border-t border-background-300 px-5 py-4 md:flex-col md:justify-center md:border-l md:border-t-0">
                      <button type="button" onClick={() => setEditing(appointment)} className="secondary-button w-full whitespace-nowrap">Editar</button>
                      <button type="button" onClick={() => { setCancellationError(null); setCancelling(appointment) }} className="inline-flex w-full items-center justify-center rounded-xl px-4 py-2.5 text-sm font-semibold text-danger-600 hover:bg-danger-50">Cancelar</button>
                    </div>
                  )}
                </div>
              </article>
            ))}
            {appointments.data && appointments.data.totalPages > 1 && <nav className="flex items-center justify-between text-sm" aria-label="Páginas de agendamentos"><button type="button" disabled={appointmentPage === 0} onClick={() => setAppointmentPage(appointmentPage - 1)}>Anterior</button><span>Página {appointmentPage + 1} de {appointments.data.totalPages}</span><button type="button" disabled={appointmentPage + 1 >= appointments.data.totalPages} onClick={() => setAppointmentPage(appointmentPage + 1)}>Próxima</button></nav>}
          </div>
        </section>

        <aside className="h-fit overflow-hidden rounded-[1rem] bg-secondary-700 text-white shadow-[var(--shadow-lifted)] xl:sticky xl:top-7">
          <div className="relative p-6">
            <div className="absolute inset-y-0 right-0 w-[4px] bg-primary-500" aria-hidden="true" />
            <span className="grid size-10 place-items-center rounded-[0.65rem] bg-white/[0.08] text-primary-300"><CalendarPlus size={19} /></span>
            <h2 className="mt-5 text-2xl font-extrabold tracking-[-0.04em]">Marcar um horário</h2>
            <p className="mt-2 text-sm leading-6 text-[#c3cde0]">Conheça cada estabelecimento ou vá direto para o agendamento.</p>
            <div className="mt-6 space-y-3">
              <p className="text-xs leading-5 text-[#c3cde0]">Informe seu CEP para ver os estabelecimentos mais próximos da sua região. A distância é aproximada.</p>
              <form onSubmit={searchByPostalCode} className="flex gap-2">
                <label className="min-w-0 flex-1">
                  <span className="sr-only">Seu CEP</span>
                  <IMaskInput
                    mask="00000-000"
                    unmask={true}
                    inputMode="numeric"
                    autoComplete="postal-code"
                    value={postalCode}
                    onAccept={(digits: string) => setPostalCode(digits)}
                    placeholder="00000-000"
                    className="w-full rounded-[0.65rem] border border-white/15 bg-white/[0.07] px-3 py-2.5 text-sm text-white outline-none placeholder:text-[#a9b4c9] focus:border-primary-300"
                  />
                </label>
                <button type="submit" disabled={nearby.isPending} className="rounded-[0.65rem] border border-white/20 px-3 py-2 text-xs font-semibold text-white hover:bg-white/10 disabled:opacity-40">{nearby.isPending ? 'Buscando...' : 'Buscar pelo CEP'}</button>
              </form>
              {proximityActive && <button type="button" onClick={() => { setProximityActive(false); nearby.reset(); setLocationMessage(null) }} className="text-xs font-semibold text-primary-200 underline">Limpar busca por CEP</button>}
              {locationMessage && <p role="alert" className="text-xs text-warning-100">{locationMessage}</p>}
              {establishments.isLoading && <p className="rounded-xl bg-white/10 px-4 py-3 text-sm text-[#c3cde0]">Carregando estabelecimentos...</p>}
              {establishments.isError && <p className="mt-3 text-sm text-danger-700">{getApiErrorMessage(establishments.error, 'Não foi possível carregar os estabelecimentos.')}</p>}
              {!establishments.isLoading && !establishments.isError && establishmentData?.length === 0 && <p className="mt-3 text-sm text-[#c3cde0]">Nenhum estabelecimento está disponível no momento.</p>}
              {(establishmentData?.length ?? 0) > 0 && (
                <label className="relative block">
                  <span className="sr-only">Buscar estabelecimento</span>
                  <Search size={15} className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-[#c3cde0]" />
                  <input
                    type="search"
                    maxLength={150}
                    value={establishmentSearch}
                    onChange={(event) => { setEstablishmentSearch(event.target.value); setEstablishmentPage(0) }}
                    placeholder="Buscar pelo nome"
                    className="w-full rounded-[0.65rem] border border-white/15 bg-white/[0.07] py-2.5 pl-9 pr-3 text-sm text-white outline-none placeholder:text-[#a9b4c9] focus:border-primary-300 focus:ring-2 focus:ring-primary-300/30"
                  />
                </label>
              )}
              {!establishments.isLoading && !establishments.isError && (establishmentData?.length ?? 0) > 0 && filteredEstablishments.length === 0 && (
                <p className="text-sm text-[#c3cde0]">Nenhum estabelecimento encontrado para "{establishmentSearch.trim()}".</p>
              )}
              {visibleEstablishments.map((establishment) => (
                <article key={establishment.id} className="rounded-[0.75rem] border border-white/12 bg-white/[0.055] p-3.5">
                  <div className="flex items-center gap-3"><EstablishmentImage establishmentId={establishment.id} kind="logo" hasImage={establishment.hasLogo} className="size-10 shrink-0 rounded-lg object-cover" /><p className="font-semibold text-white">{establishment.name}</p></div>
                  {proximityActive && <p className="mt-1 text-xs text-primary-200">{formatPostalCodeDistance(establishment.distanceKm)}</p>}
                  <div className="mt-3 grid grid-cols-2 gap-2">
                    <button type="button" onClick={() => setDetails(establishment)} className="inline-flex items-center justify-center gap-1.5 rounded-[0.6rem] border border-white/20 px-3 py-2 text-xs font-semibold text-white hover:bg-white/10"><Eye size={14} /> Detalhes</button>
                    <button type="button" onClick={() => navigate(`/estabelecimentos/${establishment.id}/agendar`)} className="inline-flex items-center justify-center gap-1.5 rounded-[0.6rem] bg-white px-3 py-2 text-xs font-semibold text-primary-900 hover:bg-primary-50">Agendar <ArrowRight size={14} /></button>
                  </div>
                </article>
              ))}
              {establishmentPages > 1 && (
                <nav className="flex items-center justify-between gap-2 pt-1" aria-label="Páginas de estabelecimentos">
                  <button
                    type="button"
                    onClick={() => setEstablishmentPage(Math.max(0, currentEstablishmentPage - 1))}
                    disabled={currentEstablishmentPage === 0}
                    className="inline-flex items-center gap-1 rounded-[0.6rem] border border-white/20 px-2.5 py-1.5 text-xs font-semibold text-white hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-40"
                    aria-label="Página anterior"
                  >
                    <ChevronLeft size={14} /> Anterior
                  </button>
                  <span className="text-xs font-medium text-[#c3cde0]">Página {currentEstablishmentPage + 1} de {establishmentPages}</span>
                  <button
                    type="button"
                    onClick={() => setEstablishmentPage(Math.min(establishmentPages - 1, currentEstablishmentPage + 1))}
                    disabled={currentEstablishmentPage >= establishmentPages - 1}
                    className="inline-flex items-center gap-1 rounded-[0.6rem] border border-white/20 px-2.5 py-1.5 text-xs font-semibold text-white hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-40"
                    aria-label="Próxima página"
                  >
                    Próxima <ChevronRight size={14} />
                  </button>
                </nav>
              )}
            </div>
          </div>
        </aside>
      </div>

      {cancelling && <ConfirmDialog title="Cancelar agendamento" message={`Deseja cancelar o agendamento de ${cancelling.serviceName} em ${formatDate(cancelling.startsAt)}?`} confirmLabel="Cancelar agendamento" isLoading={cancelMutation.isPending} onConfirm={() => cancelMutation.mutate(cancelling.id)} onCancel={() => setCancelling(null)} />}
      {editing && <AppointmentEditModal appointment={editing} onUpdated={() => { queryClient.invalidateQueries({ queryKey: QUERY_KEY }); setEditing(null) }} onClose={() => setEditing(null)} />}
      {details && <EstablishmentDetailsModal establishmentId={details.id} establishmentName={details.name} onClose={() => setDetails(null)} />}
    </div>
  )
}

function Tabs({ section, onChange, upcomingCount }: { section: AppointmentSection; onChange: (section: AppointmentSection) => void; upcomingCount: number }) {
  return <div className="mt-6 inline-flex rounded-xl border border-background-300 bg-background-50 p-1 shadow-sm"><TabButton active={section === 'upcoming'} onClick={() => onChange('upcoming')}>Próximos <span className="ml-1 opacity-60">{upcomingCount}</span></TabButton><TabButton active={section === 'history'} onClick={() => onChange('history')}>Histórico</TabButton></div>
}
function TabButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) { return <button type="button" onClick={onClick} className={`rounded-lg px-3.5 py-2 text-sm font-semibold ${active ? 'bg-primary-100 text-primary-800' : 'text-text-500 hover:text-text-800'}`}>{children}</button> }
function StatusBadge({ status }: { status: Appointment['status'] }) {
  const labels: Record<Appointment['status'], string> = { CONFIRMED: 'Confirmado', CANCELLED: 'Cancelado', COMPLETED: 'Concluído', INVALIDATED: 'Invalidado', NO_SHOW: 'Não compareceu' }
  const styles: Record<Appointment['status'], string> = { CONFIRMED: 'bg-primary-100 text-primary-800', CANCELLED: 'bg-danger-50 text-danger-600', COMPLETED: 'bg-success-50 text-success-700', INVALIDATED: 'bg-warning-100 text-warning-700', NO_SHOW: 'bg-warning-100 text-warning-700' }
  return <span className={`status-pill ${styles[status]}`}>{labels[status]}</span>
}
function isFuture(value: string) { return isBusinessLocalDateTimeFuture(value) }
function EmptyState({ children, error = false }: { children: React.ReactNode; error?: boolean }) { return <div className={`surface-card p-10 text-center text-sm ${error ? 'text-danger-600' : 'text-text-500'}`}>{children}</div> }
function formatDate(value: string) { return formatBusinessDate(value, 'long') }
function formatTime(value: string) { return value.slice(11, 16) }
function formatMoney(value: number) { return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value) }
function weekday(value: string) { return formatBusinessWeekday(value) }
function dayNumber(value: string) { return formatBusinessDayNumber(value) }
function monthShort(value: string) { return formatBusinessMonthShort(value) }

function formatPostalCodeDistance(distanceKm: number | null | undefined) {
  if (distanceKm == null) return 'Distância indisponível'
  const distance = distanceKm < 1
    ? `${Math.round(distanceKm * 1000).toLocaleString('pt-BR')} m`
    : `${distanceKm.toLocaleString('pt-BR', { maximumFractionDigits: 1 })} km`
  return `Aproximadamente ${distance} do CEP informado`
}

/** Compara nomes ignorando maiúsculas, acentos e espaços extras. */
function normalizeText(value: string) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().toLocaleLowerCase('pt-BR')
}
