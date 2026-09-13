import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { CalendarDays, Clock3, Scissors, User } from 'lucide-react'
import { fetchMyAppointmentsAsProfessional, type Appointment } from '@/features/appointments/appointmentsApi'
import { getApiErrorMessage } from '@/lib/apiError'

type AppointmentSection = 'upcoming' | 'history'

export function ProfessionalAgendaPage() {
  const [section, setSection] = useState<AppointmentSection>('upcoming')
  const appointments = useQuery({ queryKey: ['my-appointments-professional'], queryFn: fetchMyAppointmentsAsProfessional })
  const upcomingAppointments = (appointments.data ?? []).filter(isUpcoming)
  const historyAppointments = (appointments.data ?? []).filter((appointment) => !isUpcoming(appointment))
  const displayedAppointments = section === 'upcoming' ? upcomingAppointments : historyAppointments

  return (
    <div className="p-5 sm:p-7 lg:p-9">
      <div className="max-w-3xl">
        <h1 className="page-title">Minha agenda</h1>
        <p className="page-subtitle">{section === 'upcoming' ? 'Seus próximos atendimentos confirmados aparecem aqui.' : 'Consulte atendimentos anteriores e cancelados.'}</p>
        <Tabs section={section} onChange={setSection} />
        <div className="mt-7 space-y-4">
          {appointments.isLoading && <EmptyState>Carregando sua agenda...</EmptyState>}
          {appointments.isError && <EmptyState error>{getApiErrorMessage(appointments.error, 'Não foi possível carregar sua agenda.')}</EmptyState>}
          {!appointments.isLoading && !appointments.isError && displayedAppointments.length === 0 && <EmptyState>{section === 'upcoming' ? 'Você não possui próximos atendimentos.' : 'Você ainda não possui atendimentos no histórico.'}</EmptyState>}
          {displayedAppointments.map((appointment) => (
            <article key={appointment.id} className="rounded-2xl border border-brand-100 bg-white p-5 shadow-sm">
              <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <div className="flex items-center gap-2"><Scissors size={18} className="text-brand-500" /><h2 className="text-lg font-semibold text-brand-900">{appointment.serviceName}</h2></div>
                  <p className="mt-2 flex items-center gap-2 text-sm text-brand-600"><User size={16} /> {appointment.clientName}</p>
                  {appointment.status === 'CANCELLED' && <span className="mt-2 inline-flex items-center rounded-full bg-danger-500/10 px-2.5 py-1 text-xs font-semibold text-danger-600">Cancelado</span>}
                </div>
                <div className="rounded-xl bg-brand-50 px-4 py-3 text-sm text-brand-800"><p className="flex items-center gap-2 font-semibold"><CalendarDays size={16} /> {formatDate(appointment.startsAt)}</p><p className="mt-1 flex items-center gap-2"><Clock3 size={16} /> {formatTime(appointment.startsAt)} às {formatTime(appointment.endsAt)}</p></div>
              </div>
            </article>
          ))}
        </div>
      </div>
    </div>
  )
}

function Tabs({ section, onChange }: { section: AppointmentSection; onChange: (section: AppointmentSection) => void }) { return <div className="mt-5 flex gap-2 border-b border-brand-100"><TabButton active={section === 'upcoming'} onClick={() => onChange('upcoming')}>Próximos</TabButton><TabButton active={section === 'history'} onClick={() => onChange('history')}>Histórico</TabButton></div> }
function TabButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) { return <button type="button" onClick={onClick} className={`border-b-2 px-3 py-2 text-sm font-semibold ${active ? 'border-brand-600 text-brand-800' : 'border-transparent text-brand-600 hover:text-brand-800'}`}>{children}</button> }
function isUpcoming(appointment: Appointment) { return appointment.status === 'CONFIRMED' && new Date(appointment.startsAt).getTime() > Date.now() }
function EmptyState({ children, error = false }: { children: React.ReactNode; error?: boolean }) { return <div className={`rounded-2xl border bg-white p-8 text-center ${error ? 'border-danger-500/30 text-danger-600' : 'border-brand-100 text-brand-600'}`}>{children}</div> }
function formatDate(value: string) { return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long' }).format(new Date(value)) }
function formatTime(value: string) { return value.slice(11, 16) }