import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarDays, Clock3, Scissors, User } from 'lucide-react'
import { completeAppointment, fetchMyAppointmentsAsProfessional, type Appointment } from '@/features/appointments/appointmentsApi'
import { getApiErrorMessage } from '@/lib/apiError'
import { fetchMyProfessionalServices } from '@/features/employees/services/employeesApi'

type AppointmentSection = 'upcoming' | 'history'

const QUERY_KEY = ['my-appointments-professional']

export function ProfessionalAgendaPage() {
  const queryClient = useQueryClient()
  const [section, setSection] = useState<AppointmentSection>('upcoming')
  const appointments = useQuery({ queryKey: QUERY_KEY, queryFn: fetchMyAppointmentsAsProfessional })
  const services = useQuery({ queryKey: ['my-professional-services'], queryFn: fetchMyProfessionalServices })
  const completeMutation = useMutation({
    mutationFn: completeAppointment,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: QUERY_KEY }),
  })
  const upcomingAppointments = (appointments.data ?? []).filter(isUpcoming)
  const historyAppointments = (appointments.data ?? []).filter((appointment) => !isUpcoming(appointment))
  const displayedAppointments = section === 'upcoming' ? upcomingAppointments : historyAppointments

  return (
    <div className="p-5 sm:p-7 lg:p-9">
      <div className="max-w-3xl">
        <h1 className="page-title">Minha agenda</h1>
        <p className="page-subtitle">{section === 'upcoming' ? 'Seus próximos atendimentos confirmados aparecem aqui.' : 'Consulte atendimentos anteriores e cancelados.'}</p>
        <section className="mt-6 rounded-2xl border border-brand-100 bg-white p-5 shadow-sm">
          <div className="flex items-center gap-2"><Scissors size={18} className="text-brand-500" /><h2 className="font-semibold text-brand-900">Serviços que você oferece</h2></div>
          {services.isLoading && <p className="mt-3 text-sm text-brand-500">Carregando serviços...</p>}
          {services.isError && <p className="mt-3 text-sm text-danger-600">{getApiErrorMessage(services.error, 'Não foi possível carregar seus serviços.')}</p>}
          {services.data && <div className="mt-3 flex flex-wrap gap-2">{services.data.services.map((service) => <span key={service.id} className="rounded-full bg-brand-50 px-3 py-1.5 text-sm text-brand-700">{service.name}</span>)}{services.data.services.length === 0 && <p className="text-sm text-brand-500">Nenhum serviço foi atribuído ao seu perfil.</p>}</div>}
        </section>
        <Tabs section={section} onChange={setSection} />
        {completeMutation.isError && (
          <p className="mt-4 rounded-xl border border-danger-500/30 bg-danger-500/5 px-4 py-3 text-sm text-danger-600">
            {getApiErrorMessage(completeMutation.error, 'Não foi possível concluir o atendimento.')}
          </p>
        )}
        <div className="mt-7 space-y-4">
          {appointments.isLoading && <EmptyState>Carregando sua agenda...</EmptyState>}
          {appointments.isError && <EmptyState error>{getApiErrorMessage(appointments.error, 'Não foi possível carregar sua agenda.')}</EmptyState>}
          {!appointments.isLoading && !appointments.isError && displayedAppointments.length === 0 && <EmptyState>{section === 'upcoming' ? 'Você não possui próximos atendimentos.' : 'Você ainda não possui atendimentos no histórico.'}</EmptyState>}
          {displayedAppointments.map((appointment) => {
            const canComplete = appointment.status === 'CONFIRMED' && hasStarted(appointment)
            const isCompletingThis = completeMutation.isPending && completeMutation.variables === appointment.id
            return (
              <article key={appointment.id} className="rounded-2xl border border-brand-100 bg-white p-5 shadow-sm">
                <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <div className="flex items-center gap-2"><Scissors size={18} className="text-brand-500" /><h2 className="text-lg font-semibold text-brand-900">{appointment.serviceName}</h2></div>
                    <p className="mt-2 flex items-center gap-2 text-sm text-brand-600"><User size={16} /> {appointment.clientName}</p>
                    <p className="mt-1 text-sm text-brand-700">Serviço: {formatMoney(appointment.agreedPrice)} · Comissão: {formatMoney(appointment.commissionAmount)} ({appointment.commissionPercentage}%)</p>
                    {appointment.status !== 'CONFIRMED' && <StatusBadge status={appointment.status} />}
                    {appointment.status === 'INVALIDATED' && appointment.invalidationReason && (
                      <p className="mt-2 max-w-sm text-sm text-amber-700">Motivo: {appointment.invalidationReason}</p>
                    )}
                  </div>
                  <div className="flex flex-col items-end gap-3">
                    <div className="rounded-xl bg-brand-50 px-4 py-3 text-sm text-brand-800">
                      <p className="flex items-center gap-2 font-semibold"><CalendarDays size={16} /> {formatDate(appointment.startsAt)}</p>
                      <p className="mt-1 flex items-center gap-2"><Clock3 size={16} /> {formatTime(appointment.startsAt)} às {formatTime(appointment.endsAt)}</p>
                    </div>
                    {canComplete && (
                      <button
                        type="button"
                        onClick={() => completeMutation.mutate(appointment.id)}
                        disabled={isCompletingThis}
                        className="rounded-lg border border-sage-300 px-3 py-1.5 text-xs font-semibold text-sage-700 hover:bg-sage-50 disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        {isCompletingThis ? 'Concluindo...' : 'Concluir atendimento'}
                      </button>
                    )}
                  </div>
                </div>
              </article>
            )
          })}
        </div>
      </div>
    </div>
  )
}

function Tabs({ section, onChange }: { section: AppointmentSection; onChange: (section: AppointmentSection) => void }) { return <div className="mt-5 flex gap-2 border-b border-brand-100"><TabButton active={section === 'upcoming'} onClick={() => onChange('upcoming')}>Próximos</TabButton><TabButton active={section === 'history'} onClick={() => onChange('history')}>Histórico</TabButton></div> }
function TabButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) { return <button type="button" onClick={onClick} className={`border-b-2 px-3 py-2 text-sm font-semibold ${active ? 'border-brand-600 text-brand-800' : 'border-transparent text-brand-600 hover:text-brand-800'}`}>{children}</button> }
function StatusBadge({ status }: { status: Appointment['status'] }) {
  const labels: Record<Appointment['status'], string> = { CONFIRMED: 'Confirmado', CANCELLED: 'Cancelado', COMPLETED: 'Concluído', INVALIDATED: 'Invalidado' }
  const styles: Record<Appointment['status'], string> = {
    CONFIRMED: 'bg-sage-50 text-sage-600',
    CANCELLED: 'bg-danger-500/10 text-danger-600',
    COMPLETED: 'bg-brand-50 text-brand-700',
    INVALIDATED: 'bg-amber-100 text-amber-700',
  }
  return <span className={`mt-2 inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold ${styles[status]}`}>{labels[status]}</span>
}
function isUpcoming(appointment: Appointment) { return appointment.status === 'CONFIRMED' && new Date(appointment.startsAt).getTime() > Date.now() }
function hasStarted(appointment: Appointment) { return new Date(appointment.startsAt).getTime() <= Date.now() }
function EmptyState({ children, error = false }: { children: React.ReactNode; error?: boolean }) { return <div className={`rounded-2xl border bg-white p-8 text-center ${error ? 'border-danger-500/30 text-danger-600' : 'border-brand-100 text-brand-600'}`}>{children}</div> }
function formatDate(value: string) { return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long' }).format(new Date(value)) }
function formatTime(value: string) { return value.slice(11, 16) }
function formatMoney(value: number) { return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value) }
