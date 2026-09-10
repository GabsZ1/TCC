import { useQuery } from '@tanstack/react-query'
import { CalendarDays, Clock3, Scissors, User } from 'lucide-react'
import { fetchMyAppointmentsAsProfessional } from '@/features/appointments/appointmentsApi'
import { getApiErrorMessage } from '@/lib/apiError'

export function ProfessionalAgendaPage() {
  const appointments = useQuery({
    queryKey: ['my-appointments-professional'],
    queryFn: fetchMyAppointmentsAsProfessional,
  })

  return (
    <div className="p-5 sm:p-7 lg:p-9">
      <div className="max-w-3xl">
        <h1 className="page-title">Minha agenda</h1>
        <p className="page-subtitle">Seus próximos atendimentos com clientes aparecem aqui.</p>

        <div className="mt-7 space-y-4">
          {appointments.isLoading && <EmptyState>Carregando sua agenda...</EmptyState>}
          {appointments.isError && (
            <EmptyState error>
              {getApiErrorMessage(appointments.error, 'Não foi possível carregar sua agenda.')}
            </EmptyState>
          )}
          {!appointments.isLoading && !appointments.isError && appointments.data?.length === 0 && (
            <EmptyState>Você ainda não possui atendimentos marcados.</EmptyState>
          )}
          {appointments.data?.map((appointment) => (
            <article
              key={appointment.id}
              className="rounded-2xl border border-brand-100 bg-white p-5 shadow-sm"
            >
              <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <div className="flex items-center gap-2">
                    <Scissors size={18} className="text-brand-500" />
                    <h2 className="text-lg font-semibold text-brand-900">{appointment.serviceName}</h2>
                  </div>
                  <p className="mt-2 flex items-center gap-2 text-sm text-brand-600">
                    <User size={16} /> {appointment.clientName}
                  </p>
                  {appointment.status === 'CANCELLED' && (
                    <span className="mt-2 inline-flex items-center rounded-full bg-danger-500/10 px-2.5 py-1 text-xs font-semibold text-danger-600">
                      Cancelado
                    </span>
                  )}
                </div>
                <div className="rounded-xl bg-brand-50 px-4 py-3 text-sm text-brand-800">
                  <p className="flex items-center gap-2 font-semibold">
                    <CalendarDays size={16} /> {formatDate(appointment.startsAt)}
                  </p>
                  <p className="mt-1 flex items-center gap-2">
                    <Clock3 size={16} /> {formatTime(appointment.startsAt)} às {formatTime(appointment.endsAt)}
                  </p>
                </div>
              </div>
            </article>
          ))}
        </div>
      </div>
    </div>
  )
}

function EmptyState({ children, error = false }: { children: React.ReactNode; error?: boolean }) {
  return (
    <div
      className={`rounded-2xl border bg-white p-8 text-center ${error ? 'border-danger-500/30 text-danger-600' : 'border-brand-100 text-brand-600'}`}
    >
      {children}
    </div>
  )
}
function formatDate(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long' }).format(new Date(value))
}
function formatTime(value: string) {
  return value.slice(11, 16)
}
