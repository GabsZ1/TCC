import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Modal } from '@/components/ui/Modal'
import { getApiErrorMessage } from '@/lib/apiError'
import { fetchBookingOptions, fetchInternalAvailability } from '@/features/customers/customersApi'
import { rescheduleAppointment, type Appointment } from '@/features/appointments/appointmentsApi'

interface Props { appointment: Appointment; onRescheduled: () => void; onClose: () => void }

export function RescheduleAppointmentModal({ appointment, onRescheduled, onClose }: Props) {
  const [serviceId, setServiceId] = useState(String(appointment.serviceId))
  const [professionalId, setProfessionalId] = useState('')
  const [date, setDate] = useState('')
  const [time, setTime] = useState('')
  const [minDate] = useState(() => {
    const now = new Date()
    return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
  })
  const options = useQuery({ queryKey: ['customer-booking-options', serviceId],
    queryFn: () => fetchBookingOptions(Number(serviceId)), enabled: !!serviceId })
  const availability = useQuery({ queryKey: ['reschedule-availability', appointment.id, serviceId, professionalId, date],
    queryFn: () => fetchInternalAvailability(appointment.customerId, Number(serviceId), Number(professionalId), date, appointment.id),
    enabled: !!serviceId && !!professionalId && !!date, retry: false })
  const mutation = useMutation({
    mutationFn: () => rescheduleAppointment(appointment.id, {
      serviceId: Number(serviceId), professionalId: Number(professionalId), startsAt: `${date}T${time.slice(0, 5)}:00`,
    }), onSuccess: onRescheduled,
  })
  const services = options.data?.services ?? []
  const changesService = serviceId !== String(appointment.serviceId)

  return <Modal title="Remarcar agendamento" onClose={onClose}>
    <form onSubmit={(event) => { event.preventDefault(); mutation.mutate() }} className="space-y-4">
      <p className="text-sm text-text-700">Cliente: <strong>{appointment.clientName}</strong>. Os horários seguem a grade de 15 minutos.</p>
      <label className="block text-sm font-medium text-text-800">Serviço
        <select className="filter-input mt-1 w-full" value={serviceId} onChange={(event) => { setServiceId(event.target.value); setProfessionalId(''); setDate(''); setTime('') }}>
          {!services.some((service) => service.id === appointment.serviceId) && <option value={appointment.serviceId}>{appointment.serviceName} (atual)</option>}
          {services.map((service) => <option key={service.id} value={service.id}>{service.name}</option>)}
        </select>
      </label>
      {changesService && <p className="rounded-lg bg-warning-50 px-3 py-2 text-xs text-warning-700">Ao trocar o serviço, o preço, a comissão e a duração passam a ser os do novo serviço.</p>}
      <label className="block text-sm font-medium text-text-800">Profissional
        <select required className="filter-input mt-1 w-full" value={professionalId} onChange={(event) => { setProfessionalId(event.target.value); setDate(''); setTime('') }}>
          <option value="">Selecione</option>
          {options.data?.professionals.map((employee) => <option key={employee.id} value={employee.id}>{employee.name}</option>)}
        </select>
      </label>
      <label className="block text-sm font-medium text-text-800">Data
        <input required type="date" min={minDate} className="filter-input mt-1 w-full" value={date} onChange={(event) => { setDate(event.target.value); setTime('') }} />
      </label>
      <div><p className="text-sm font-medium text-text-800">Horários disponíveis</p><div className="mt-2 flex flex-wrap gap-2">
        {availability.isFetching && <span className="text-sm text-text-600">Buscando horários...</span>}
        {availability.data?.availableTimes.map((slot) => <button key={slot} type="button" aria-pressed={time === slot} onClick={() => setTime(slot)} className={time === slot ? 'btn-primary' : 'rounded-lg border border-background-300 px-3 py-2 text-sm'}>{slot.slice(0, 5)}</button>)}
        {availability.data?.availableTimes.length === 0 && <span className="text-sm text-text-600">Nenhum horário disponível nesta data.</span>}
        {availability.isError && <p role="alert" className="text-sm text-danger-600">{getApiErrorMessage(availability.error, 'Não foi possível consultar os horários.')}</p>}
      </div></div>
      {mutation.isError && <p role="alert" className="text-sm text-danger-600">{getApiErrorMessage(mutation.error, 'Não foi possível remarcar o agendamento.')}</p>}
      <div className="flex justify-end gap-3"><button type="button" onClick={onClose}>Voltar</button>
        <button type="submit" disabled={!time || !availability.data?.availableTimes.includes(time) || mutation.isPending} className="primary-button disabled:opacity-60">{mutation.isPending ? 'Salvando...' : 'Remarcar'}</button></div>
    </form>
  </Modal>
}
