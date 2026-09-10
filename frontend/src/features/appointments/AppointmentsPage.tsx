import { useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { CalendarDays, Clock3, MapPin, Scissors } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { fetchMyAppointments } from '@/features/appointments/appointmentsApi'
import { getApiErrorMessage } from '@/lib/apiError'

export function AppointmentsPage() {
  const navigate = useNavigate()
  const [establishmentCode, setEstablishmentCode] = useState('')
  const appointments = useQuery({ queryKey: ['my-appointments'], queryFn: fetchMyAppointments })

  function startBooking(event: FormEvent) {
    event.preventDefault()
    const id = Number(establishmentCode)
    if (Number.isInteger(id) && id > 0) navigate(`/estabelecimentos/${id}/agendar`)
  }

  return (
    <div className="p-5 sm:p-7 lg:p-9">
      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]">
        <section>
          <h1 className="page-title">Meus agendamentos</h1>
          <p className="page-subtitle">Seus próximos horários aparecem aqui.</p>

          <div className="mt-7 space-y-4">
            {appointments.isLoading && <EmptyState>Carregando agendamentos...</EmptyState>}
            {appointments.isError && <EmptyState error>{getApiErrorMessage(appointments.error, 'Não foi possível carregar seus agendamentos.')}</EmptyState>}
            {!appointments.isLoading && !appointments.isError && appointments.data?.length === 0 && <EmptyState>Você ainda não possui agendamentos.</EmptyState>}
            {appointments.data?.map((appointment) => (
              <article key={appointment.id} className="rounded-2xl border border-brand-100 bg-white p-5 shadow-sm">
                <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                  <div>
                    <div className="flex items-center gap-2"><Scissors size={18} className="text-brand-500" /><h2 className="text-lg font-semibold text-brand-900">{appointment.serviceName}</h2></div>
                    <p className="mt-2 flex items-center gap-2 text-sm text-brand-600"><MapPin size={16} /> {appointment.establishmentName}</p>
                    <p className="mt-1 text-sm text-brand-600">com {appointment.professionalName}</p>
                  </div>
                  <div className="rounded-xl bg-brand-50 px-4 py-3 text-sm text-brand-800">
                    <p className="flex items-center gap-2 font-semibold"><CalendarDays size={16} /> {formatDate(appointment.startsAt)}</p>
                    <p className="mt-1 flex items-center gap-2"><Clock3 size={16} /> {formatTime(appointment.startsAt)} às {formatTime(appointment.endsAt)}</p>
                  </div>
                </div>
              </article>
            ))}
          </div>
        </section>

        <aside className="h-fit rounded-2xl border border-brand-200 bg-brand-900 p-6 text-white shadow-sm">
          <h2 className="font-[var(--font-display)] text-2xl">Marcar um horário</h2>
          <p className="mt-2 text-sm leading-6 text-brand-100">Informe o código compartilhado pelo estabelecimento.</p>
          <form onSubmit={startBooking} className="mt-5">
            <label className="text-sm font-medium" htmlFor="establishment-code">Código do estabelecimento</label>
            <input id="establishment-code" type="number" min="1" required value={establishmentCode} onChange={(event) => setEstablishmentCode(event.target.value)} className="mt-2 w-full rounded-xl border border-white/20 bg-white px-3.5 py-3 text-brand-900 outline-none focus:ring-2 focus:ring-brand-300" placeholder="Ex.: 1" />
            <button className="mt-3 w-full rounded-xl bg-white px-4 py-3 font-semibold text-brand-800 hover:bg-brand-50">Continuar</button>
          </form>
        </aside>
      </div>
    </div>
  )
}

function EmptyState({ children, error = false }: { children: React.ReactNode; error?: boolean }) { return <div className={`rounded-2xl border bg-white p-8 text-center ${error ? 'border-danger-500/30 text-danger-600' : 'border-brand-100 text-brand-600'}`}>{children}</div> }
function formatDate(value: string) { return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long' }).format(new Date(value)) }
function formatTime(value: string) { return value.slice(11, 16) }
