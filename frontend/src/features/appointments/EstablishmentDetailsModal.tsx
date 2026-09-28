import { useQuery } from '@tanstack/react-query'
import { AtSign, Clock3, MapPin, MessageCircle, Phone, Scissors } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { Modal } from '@/components/ui/Modal'
import { fetchEstablishmentProfile } from '@/features/appointments/appointmentsApi'
import { EmployeeAvatar } from '@/features/employees/components/EmployeeAvatar'
import { getApiErrorMessage } from '@/lib/apiError'

interface Props {
  establishmentId: number
  establishmentName: string
  onClose: () => void
}

export function EstablishmentDetailsModal({ establishmentId, establishmentName, onClose }: Props) {
  const navigate = useNavigate()
  const profileQuery = useQuery({
    queryKey: ['establishment-profile', establishmentId],
    queryFn: () => fetchEstablishmentProfile(establishmentId),
  })
  const profile = profileQuery.data
  const address = profile ? formatAddress(profile) : null

  return (
    <Modal title={establishmentName} onClose={onClose} size="lg">
      {profileQuery.isLoading && <div className="py-10 text-center text-sm text-text-500">Carregando detalhes...</div>}
      {profileQuery.isError && <div className="rounded-xl border border-danger-500/20 bg-danger-50 p-4 text-sm text-danger-600">{getApiErrorMessage(profileQuery.error, 'Não foi possível carregar os detalhes do estabelecimento.')}</div>}
      {profile && (
        <div className="space-y-7">
          <section className="rounded-2xl bg-primary-900 p-5 text-white sm:p-6">
            <p className="text-[0.65rem] font-bold uppercase tracking-[0.14em] text-primary-200">Quem somos</p>
            <p className="mt-3 whitespace-pre-line text-sm leading-7 text-primary-50">{profile.aboutUs || 'Este estabelecimento ainda não adicionou uma apresentação.'}</p>
          </section>

          <div className="grid gap-5 md:grid-cols-2">
            <section className="rounded-2xl border border-background-300 p-5">
              <h3 className="flex items-center gap-2 font-semibold text-text-950"><MapPin size={18} className="text-primary-600" /> Endereço</h3>
              <p className="mt-3 text-sm leading-6 text-text-600">{address || 'Endereço ainda não informado.'}</p>
              {profile.postalCode && <p className="mt-1 text-xs text-text-500">CEP {formatPostalCode(profile.postalCode)}</p>}
              <div className="mt-4 rounded-xl bg-background-100 px-4 py-3 text-xs leading-5 text-text-500">O mapa poderá ser exibido aqui quando a integração de localização for adicionada.</div>
            </section>

            <section className="rounded-2xl border border-background-300 p-5">
              <h3 className="font-semibold text-text-950">Fale com o estabelecimento</h3>
              <div className="mt-3 space-y-2">
                {profile.whatsapp && <ContactLink href={`https://wa.me/${whatsappNumber(profile.whatsapp)}`} icon={<MessageCircle size={16} />} label="WhatsApp" value={profile.whatsapp} />}
                {profile.phone && <ContactLink href={`tel:${profile.phone}`} icon={<Phone size={16} />} label="Telefone" value={profile.phone} />}
                {profile.instagram && <ContactLink href={`https://instagram.com/${profile.instagram}`} icon={<AtSign size={16} />} label="Instagram" value={`@${profile.instagram}`} />}
                {!profile.whatsapp && !profile.phone && !profile.instagram && <p className="text-sm text-text-500">Contatos ainda não informados.</p>}
              </div>
            </section>
          </div>

          {profile.businessHours.length > 0 && (
            <section>
              <h3 className="flex items-center gap-2 text-lg font-semibold text-text-950"><Clock3 size={18} className="text-primary-600" /> Horários de funcionamento</h3>
              <div className="mt-3 grid gap-2 sm:grid-cols-2">
                {profile.businessHours.map((hour) => <div key={hour.dayOfWeek} className="flex items-center justify-between rounded-xl bg-background-100 px-3.5 py-2.5 text-sm"><span className="font-medium text-text-700">{weekdayLabel(hour.dayOfWeek)}</span><span className={hour.closed ? 'text-text-500' : 'font-semibold text-text-800'}>{hour.closed ? 'Fechado' : `${shortTime(hour.openingTime)} às ${shortTime(hour.closingTime)}`}</span></div>)}
              </div>
            </section>
          )}

          <section>
            <div className="flex items-end justify-between gap-3"><div><p className="eyebrow">Catálogo</p><h3 className="mt-1 text-lg font-semibold text-text-950">Serviços</h3></div><span className="text-xs text-text-500">{profile.services.length} disponível(is)</span></div>
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              {profile.services.map((service) => <div key={service.id} className="rounded-xl border border-background-300 p-4"><div className="flex items-start justify-between gap-3"><p className="flex items-center gap-2 font-semibold text-text-900"><Scissors size={15} className="text-primary-600" />{service.name}</p><span className="whitespace-nowrap text-sm font-bold text-primary-700">{formatMoney(service.price)}</span></div>{service.description && <p className="mt-2 text-sm leading-5 text-text-500">{service.description}</p>}<p className="mt-2 flex items-center gap-1 text-xs text-text-500"><Clock3 size={13} /> {service.durationMinutes} min</p></div>)}
              {profile.services.length === 0 && <p className="text-sm text-text-500">Nenhum serviço disponível no momento.</p>}
            </div>
          </section>

          <section>
            <div className="flex items-end justify-between gap-3"><h3 className="text-lg font-semibold text-text-950">Equipe</h3><span className="text-xs text-text-500">{profile.professionals.length} profissional(is)</span></div>
            <div className="mt-3 flex flex-wrap gap-2">{profile.professionals.map((professional) => <span key={professional.id} className="inline-flex items-center gap-2 rounded-xl bg-background-100 py-1.5 pl-1.5 pr-3.5 text-sm font-medium text-text-700"><EmployeeAvatar id={professional.id} name={professional.name} hasPhoto={professional.hasPhoto} catalogEstablishmentId={establishmentId} size="sm" />{professional.name}</span>)}{profile.professionals.length === 0 && <p className="text-sm text-text-500">Nenhum profissional disponível no momento.</p>}</div>
          </section>

          <div className="flex justify-end border-t border-background-300 pt-5">
            <button type="button" onClick={() => navigate(`/estabelecimentos/${establishmentId}/agendar`)} className="primary-button">Agendar neste estabelecimento</button>
          </div>
        </div>
      )}
    </Modal>
  )
}

function ContactLink({ href, icon, label, value }: { href: string; icon: React.ReactNode; label: string; value: string }) {
  return <a href={href} target="_blank" rel="noreferrer" className="flex items-center gap-3 rounded-xl bg-background-100 px-3.5 py-3 hover:bg-primary-50"><span className="text-primary-600">{icon}</span><span><span className="block text-xs text-text-500">{label}</span><span className="block text-sm font-semibold text-text-800">{value}</span></span></a>
}

function formatAddress(profile: Awaited<ReturnType<typeof fetchEstablishmentProfile>>) {
  const street = [profile.street, profile.streetNumber].filter(Boolean).join(', ')
  return [street, profile.addressComplement, profile.neighborhood, [profile.city, profile.state].filter(Boolean).join(' - ')].filter(Boolean).join(' · ')
}
function formatPostalCode(value: string) { return value.replace(/^(\d{5})(\d{3})$/, '$1-$2') }
function shortTime(value: string | null) { return value?.slice(0, 5) ?? '--:--' }
function weekdayLabel(value: string) { return ({ MONDAY: 'Segunda-feira', TUESDAY: 'Terça-feira', WEDNESDAY: 'Quarta-feira', THURSDAY: 'Quinta-feira', FRIDAY: 'Sexta-feira', SATURDAY: 'Sábado', SUNDAY: 'Domingo' } as Record<string, string>)[value] ?? value }
function whatsappNumber(value: string) { const digits = value.replace(/\D/g, ''); return digits.startsWith('55') ? digits : `55${digits}` }
function formatMoney(value: number) { return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value) }
