import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Modal } from '@/components/ui/Modal'
import { getApiErrorMessage } from '@/lib/apiError'
import { fetchBookingOptions } from '@/features/customers/customersApi'
import { rescheduleAppointment, type Appointment } from '@/features/appointments/appointmentsApi'

interface Props {
  appointment: Appointment
  onRescheduled: () => void
  onClose: () => void
}

/**
 * Remarcação pelo administrador: novo horário, outro profissional ou outro serviço para o mesmo cliente.
 * O backend repete as validações de jornada, bloqueios e conflitos. Manter o serviço mantém o preço combinado.
 */
export function RescheduleAppointmentModal({ appointment, onRescheduled, onClose }: Props) {
  const options = useQuery({ queryKey: ['customer-booking-options'], queryFn: () => fetchBookingOptions() })
  const [serviceId, setServiceId] = useState(String(appointment.serviceId))
  const [professionalId, setProfessionalId] = useState(String(appointment.professionalId))
  const [startsAt, setStartsAt] = useState(appointment.startsAt.slice(0, 16))
  const mutation = useMutation({
    mutationFn: () => rescheduleAppointment(appointment.id, { serviceId: Number(serviceId), professionalId: Number(professionalId), startsAt }),
    onSuccess: onRescheduled,
  })
  const changesService = serviceId !== String(appointment.serviceId)
  const unchanged = !changesService && professionalId === String(appointment.professionalId) && startsAt === appointment.startsAt.slice(0, 16)
  const services = options.data?.services ?? []
  const currentServiceListed = services.some((service) => String(service.id) === String(appointment.serviceId))

  return (
    <Modal title="Remarcar agendamento" onClose={onClose}>
      <form onSubmit={(event) => { event.preventDefault(); mutation.mutate() }} className="space-y-4">
        <p className="text-sm text-text-700">Cliente: <strong>{appointment.clientName}</strong>. Os horários seguem a grade de 15 minutos.</p>
        <label className="block text-sm font-medium text-text-800">Serviço
          <select className="filter-input mt-1 w-full" value={serviceId} onChange={(event) => setServiceId(event.target.value)}>
            {!currentServiceListed && <option value={appointment.serviceId}>{appointment.serviceName} (atual)</option>}
            {services.map((service) => <option key={service.id} value={service.id}>{service.name}{service.id === appointment.serviceId ? ' (atual)' : ''}</option>)}
          </select>
        </label>
        {changesService && <p className="rounded-lg bg-warning-50 px-3 py-2 text-xs text-warning-700">Ao trocar o serviço, o preço, a comissão e a duração passam a ser os do novo serviço.</p>}
        <label className="block text-sm font-medium text-text-800">Profissional
          <select className="filter-input mt-1 w-full" value={professionalId} onChange={(event) => setProfessionalId(event.target.value)}>
            {!options.data?.professionals.some((employee) => employee.id === appointment.professionalId) && <option value={appointment.professionalId}>{appointment.professionalName} (atual)</option>}
            {options.data?.professionals.map((employee) => <option key={employee.id} value={employee.id}>{employee.name}{employee.id === appointment.professionalId ? ' (atual)' : ''}</option>)}
          </select>
        </label>
        <label className="block text-sm font-medium text-text-800">Data e horário
          <input required type="datetime-local" step={900} className="filter-input mt-1 w-full" value={startsAt} onChange={(event) => setStartsAt(event.target.value)} />
        </label>
        {mutation.isError && <p role="alert" className="text-sm text-danger-600">{getApiErrorMessage(mutation.error, 'Não foi possível remarcar o agendamento.')}</p>}
        <div className="flex justify-end gap-3">
          <button type="button" onClick={onClose} className="rounded-lg border border-background-300 px-4 py-2 text-sm font-medium text-text-700 hover:bg-background-100">Voltar</button>
          <button type="submit" disabled={unchanged || mutation.isPending} className="primary-button disabled:opacity-60">{mutation.isPending ? 'Salvando...' : 'Remarcar'}</button>
        </div>
      </form>
    </Modal>
  )
}
