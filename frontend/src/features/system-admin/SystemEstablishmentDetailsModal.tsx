import { useQuery } from '@tanstack/react-query'
import { AtSign, CalendarCheck, CalendarClock, Clock3, Mail, MapPin, MessageCircle, Phone, Scissors, ShieldCheck, UsersRound } from 'lucide-react'
import { Modal } from '@/components/ui/Modal'
import { getApiErrorMessage } from '@/lib/apiError'
import { formatDate, formatMoney } from '@/lib/format'
import { formatPhone } from '@/lib/phone'
import { fetchSystemEstablishmentDetails, type SystemEstablishmentDetails } from '@/features/system-admin/systemEstablishmentsApi'

interface Props {
  establishmentId: number
  establishmentName: string
  onClose: () => void
}

/** Detalhes de um estabelecimento para o administrador da plataforma (somente leitura). */
export function SystemEstablishmentDetailsModal({ establishmentId, establishmentName, onClose }: Props) {
  const details = useQuery({
    queryKey: ['system-establishment-details', establishmentId],
    queryFn: () => fetchSystemEstablishmentDetails(establishmentId),
  })
  const data = details.data

  return (
    <Modal title={establishmentName} onClose={onClose} size="lg">
      {details.isLoading && <p className="py-10 text-center text-sm text-text-500">Carregando detalhes...</p>}
      {details.isError && <p role="alert" className="rounded-xl border border-danger-500/20 bg-danger-50 p-4 text-sm text-danger-600">{getApiErrorMessage(details.error, 'Não foi possível carregar os detalhes do estabelecimento.')}</p>}
      {data && <DetailsContent data={data} />}
    </Modal>
  )
}

function DetailsContent({ data }: { data: SystemEstablishmentDetails }) {
  const { establishment, stats, profile } = data
  const address = profile ? formatAddress(profile) : ''

  return (
    <div className="space-y-7">
      <section>
        <div className="flex flex-wrap gap-2">
          <Pill tone={establishment.active ? 'success' : 'warning'}>{establishment.active ? 'Site ativo' : 'Site desativado'}</Pill>
          {establishment.paused && <Pill tone="warning">{establishment.pausedBySystem ? 'Operação pausada pela plataforma' : 'Operação pausada pelo estabelecimento'}</Pill>}
          {establishment.bookingsBlocked && <Pill tone="warning">{establishment.bookingsBlockedBySystem ? 'Agendamentos bloqueados pela plataforma' : 'Agendamentos bloqueados pelo estabelecimento'}</Pill>}
        </div>
        <dl className="mt-4 grid gap-4 text-sm sm:grid-cols-3">
          <Info label="CNPJ" value={formatCnpj(establishment.cnpj)} />
          <Info label="E-mail" value={establishment.email ?? 'Não informado'} />
          <Info label="Cadastrado em" value={formatDate(establishment.createdAt.slice(0, 10))} />
        </dl>
      </section>

      <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Stat icon={<UsersRound size={17} />} label="Profissionais ativos" value={`${stats.activeProfessionals} de ${stats.totalProfessionals}`} />
        <Stat icon={<Scissors size={17} />} label="Serviços ativos" value={`${stats.activeServices} de ${stats.totalServices}`} />
        <Stat icon={<CalendarClock size={17} />} label="Agendamentos futuros" value={String(stats.upcomingAppointments)} />
        <Stat icon={<CalendarCheck size={17} />} label="Concluídos no mês" value={String(stats.completedThisMonth)} helper={`${stats.totalAppointments} agendamento(s) no total`} />
      </section>

      <section>
        <SectionTitle icon={<ShieldCheck size={18} />} title="Responsáveis" />
        <div className="mt-3 space-y-2">
          {data.administrators.map((admin) => (
            <div key={admin.id} className="flex flex-wrap items-center justify-between gap-2 rounded-xl bg-background-100 px-4 py-3 text-sm">
              <span className="font-semibold text-text-900">{admin.name}</span>
              <span className="flex items-center gap-2 text-text-600"><Mail size={14} />{admin.email}{!admin.active && <Pill tone="warning">Inativo</Pill>}</span>
            </div>
          ))}
          {data.administrators.length === 0 && <Empty>Nenhum administrador cadastrado.</Empty>}
        </div>
      </section>

      <div className="grid gap-5 md:grid-cols-2">
        <section className="rounded-2xl border border-background-300 p-5">
          <SectionTitle icon={<MapPin size={18} />} title="Endereço" />
          <p className="mt-3 text-sm leading-6 text-text-600">{address || 'Endereço ainda não informado.'}</p>
          {profile?.postalCode && <p className="mt-1 text-xs text-text-500">CEP {profile.postalCode.replace(/^(\d{5})(\d{3})$/, '$1-$2')}</p>}
        </section>
        <section className="rounded-2xl border border-background-300 p-5">
          <h3 className="font-semibold text-text-950">Contatos públicos</h3>
          <div className="mt-3 space-y-2 text-sm text-text-700">
            {profile?.whatsapp && <p className="flex items-center gap-2"><MessageCircle size={15} className="text-primary-600" />WhatsApp {formatPhone(profile.whatsapp)}</p>}
            {profile?.phone && <p className="flex items-center gap-2"><Phone size={15} className="text-primary-600" />Telefone {formatPhone(profile.phone)}</p>}
            {profile?.instagram && <p className="flex items-center gap-2"><AtSign size={15} className="text-primary-600" />{profile.instagram.replace(/^@?/, '@')}</p>}
            {!profile?.whatsapp && !profile?.phone && !profile?.instagram && <Empty>Contatos ainda não informados.</Empty>}
          </div>
        </section>
      </div>

      {profile?.aboutUs && (
        <section className="rounded-2xl bg-background-100 p-5">
          <p className="text-[0.65rem] font-bold uppercase tracking-[0.14em] text-text-500">Quem somos</p>
          <p className="mt-2 whitespace-pre-line text-sm leading-6 text-text-700">{profile.aboutUs}</p>
        </section>
      )}

      <section>
        <SectionTitle icon={<Clock3 size={18} />} title="Funcionamento e agendamentos" />
        <div className="mt-3 grid gap-2 sm:grid-cols-2">
          {data.businessHours.map((hour) => (
            <div key={hour.dayOfWeek} className="flex items-center justify-between rounded-xl bg-background-100 px-3.5 py-2.5 text-sm">
              <span className="font-medium text-text-700">{weekdayLabel(hour.dayOfWeek)}</span>
              <span className={hour.closed ? 'text-text-500' : 'font-semibold text-text-800'}>{hour.closed ? 'Fechado' : `${shortTime(hour.openingTime)} às ${shortTime(hour.closingTime)}`}</span>
            </div>
          ))}
        </div>
        <p className="mt-3 text-sm text-text-600">Antecedência mínima de {formatAdvance(data.minimumAdvanceMinutes)} e máxima de {data.maximumAdvanceDays} dia(s).</p>
      </section>

      <section>
        <SectionTitle icon={<UsersRound size={18} />} title="Equipe" aside={`${data.professionals.length} profissional(is)`} />
        <div className="mt-3 divide-y divide-background-300 rounded-2xl border border-background-300">
          {data.professionals.map((professional) => (
            <div key={professional.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3 text-sm">
              <div><p className="font-semibold text-text-900">{professional.name}</p><p className="text-xs text-text-500">{professional.occupation ?? 'Ocupação não informada'} · {professional.email}</p></div>
              <Pill tone={professional.active ? 'success' : 'warning'}>{professional.active ? 'Ativo' : 'Inativo'}</Pill>
            </div>
          ))}
          {data.professionals.length === 0 && <div className="px-4 py-3"><Empty>Nenhum profissional cadastrado.</Empty></div>}
        </div>
      </section>

      <section>
        <SectionTitle icon={<Scissors size={18} />} title="Serviços" aside={`${data.services.length} cadastrado(s)`} />
        <div className="mt-3 divide-y divide-background-300 rounded-2xl border border-background-300">
          {data.services.map((service) => (
            <div key={service.id} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3 text-sm">
              <div><p className="font-semibold text-text-900">{service.name}</p><p className="text-xs text-text-500">{formatMoney(service.price)} · {service.durationMinutes} min</p></div>
              <Pill tone={service.active ? 'success' : 'warning'}>{service.active ? 'Ativo' : 'Inativo'}</Pill>
            </div>
          ))}
          {data.services.length === 0 && <div className="px-4 py-3"><Empty>Nenhum serviço cadastrado.</Empty></div>}
        </div>
      </section>
    </div>
  )
}

function SectionTitle({ icon, title, aside }: { icon: React.ReactNode; title: string; aside?: string }) {
  return <div className="flex items-end justify-between gap-3"><h3 className="flex items-center gap-2 text-lg font-semibold text-text-950"><span className="text-primary-600">{icon}</span>{title}</h3>{aside && <span className="text-xs text-text-500">{aside}</span>}</div>
}
function Stat({ icon, label, value, helper }: { icon: React.ReactNode; label: string; value: string; helper?: string }) {
  return <div className="rounded-2xl border border-background-300 p-4"><p className="flex items-center gap-2 text-xs font-semibold text-text-500"><span className="text-primary-600">{icon}</span>{label}</p><p className="mt-2 text-2xl font-extrabold tracking-[-0.03em] text-text-950">{value}</p>{helper && <p className="mt-1 text-xs text-text-500">{helper}</p>}</div>
}
function Info({ label, value }: { label: string; value: string }) { return <div><dt className="text-text-500">{label}</dt><dd className="mt-1 break-words font-semibold text-text-900">{value}</dd></div> }
function Pill({ tone, children }: { tone: 'success' | 'warning'; children: React.ReactNode }) { return <span className={`status-pill ${tone === 'success' ? 'bg-success-50 text-success-700' : 'bg-warning-100 text-warning-700'}`}>{children}</span> }
function Empty({ children }: { children: React.ReactNode }) { return <p className="text-sm text-text-500">{children}</p> }

function formatAddress(profile: NonNullable<SystemEstablishmentDetails['profile']>) {
  const street = [profile.street, profile.streetNumber].filter(Boolean).join(', ')
  return [street, profile.addressComplement, profile.neighborhood, [profile.city, profile.state].filter(Boolean).join(' - ')].filter(Boolean).join(' · ')
}
function formatCnpj(value: string) { return value.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, '$1.$2.$3/$4-$5') }
function shortTime(value: string | null) { return value?.slice(0, 5) ?? '--:--' }
function weekdayLabel(value: string) { return ({ MONDAY: 'Segunda-feira', TUESDAY: 'Terça-feira', WEDNESDAY: 'Quarta-feira', THURSDAY: 'Quinta-feira', FRIDAY: 'Sexta-feira', SATURDAY: 'Sábado', SUNDAY: 'Domingo' } as Record<string, string>)[value] ?? value }
function formatAdvance(minutes: number) {
  if (minutes % 60 === 0) return `${minutes / 60} hora(s)`
  return `${minutes} minuto(s)`
}
