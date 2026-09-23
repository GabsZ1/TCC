import { useMemo, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Clock3 } from 'lucide-react'
import { Modal } from '@/components/ui/Modal'
import { getApiErrorMessage } from '@/lib/apiError'
import { fetchAvailability, fetchCatalogProfessionals, fetchCatalogServices, updateAppointment, type Appointment } from '@/features/appointments/appointmentsApi'

interface AppointmentEditModalProps {
  appointment: Appointment
  onUpdated: () => void
  onClose: () => void
}

export function AppointmentEditModal({ appointment, onUpdated, onClose }: AppointmentEditModalProps) {
  const [serviceId, setServiceId] = useState(appointment.serviceId)
  const [professionalId, setProfessionalId] = useState(appointment.professionalId)
  const [date, setDate] = useState(appointment.startsAt.slice(0, 10))
  const [time, setTime] = useState(appointment.startsAt.slice(11, 16))
  const services = useQuery({ queryKey: ['catalog-services', appointment.establishmentId], queryFn: () => fetchCatalogServices(appointment.establishmentId) })
  const professionals = useQuery({ queryKey: ['catalog-professionals', appointment.establishmentId], queryFn: () => fetchCatalogProfessionals(appointment.establishmentId) })
  const availability = useQuery({
    queryKey: ['availability', appointment.establishmentId, serviceId, professionalId, date, appointment.id],
    queryFn: () => fetchAvailability(appointment.establishmentId, serviceId, professionalId, date, appointment.id),
    enabled: !!serviceId && !!professionalId && !!date,
    retry: false,
  })
  const selectedService = useMemo(() => services.data?.find((service) => service.id === serviceId), [services.data, serviceId])
  const selectedProfessional = useMemo(() => professionals.data?.find((professional) => professional.id === professionalId), [professionals.data, professionalId])
  const updateMutation = useMutation({
    mutationFn: () => updateAppointment(appointment.id, serviceId, professionalId, `${date}T${normalizeTime(time)}`),
    onSuccess: onUpdated,
  })

  function chooseService(id: number) { setServiceId(id); setTime('') }
  function chooseProfessional(id: number) { setProfessionalId(id); setTime('') }

  return (
    <Modal title="Editar agendamento" onClose={onClose}>
      <div className="max-h-[72vh] space-y-5 overflow-y-auto pr-1">
        <Step number="1" title="Serviço">
          <div className="grid gap-2">{services.data?.map((service) => <ChoiceButton key={service.id} selected={service.id === serviceId} onClick={() => chooseService(service.id)} title={service.name} detail={`${formatMoney(service.price)} · ${service.durationMinutes} min`} />)}</div>
        </Step>
        <Step number="2" title="Profissional">
          <div className="grid gap-2">{professionals.data?.map((professional) => <ChoiceButton key={professional.id} selected={professional.id === professionalId} onClick={() => chooseProfessional(professional.id)} title={professional.name} detail="Profissional disponível" />)}</div>
        </Step>
        <Step number="3" title="Data e horário">
          <input type="date" min={today()} value={date} onChange={(event) => { setDate(event.target.value); setTime('') }} className="w-full rounded-xl border border-background-300 px-3 py-2.5 text-text-900 outline-none focus:border-primary-500" />
          {availability.isError && <p className="mt-3 text-sm text-danger-600">{getApiErrorMessage(availability.error, 'Não foi possível consultar os horários.')}</p>}
          {availability.data && <div className="mt-3 grid grid-cols-3 gap-2">{availability.data.availableTimes.map((availableTime) => <button key={availableTime} type="button" onClick={() => setTime(availableTime)} className={`rounded-lg border px-2 py-2 text-sm font-medium ${time === availableTime.slice(0, 5) ? 'border-primary-600 bg-primary-50 text-text-800' : 'border-background-300 text-text-700 hover:border-primary-300'}`}>{shortTime(availableTime)}</button>)}</div>}
        </Step>
        <div className="rounded-xl bg-background-200 p-4 text-sm text-text-700"><p><strong>Serviço:</strong> {selectedService?.name}</p><p className="mt-1"><strong>Profissional:</strong> {selectedProfessional?.name}</p><p className="mt-1"><strong>Horário:</strong> {date} às {time || 'não selecionado'}</p>{selectedService && <p className="mt-2 flex items-center gap-2"><Clock3 size={15} /> Duração de {selectedService.durationMinutes} minutos</p>}</div>
        {updateMutation.isError && <p role="alert" className="text-sm text-danger-600">{getApiErrorMessage(updateMutation.error, 'Não foi possível atualizar o agendamento.')}</p>}
      </div>
      <div className="mt-6 flex justify-end gap-3"><button type="button" onClick={onClose} className="rounded-lg border border-background-300 px-4 py-2 text-sm font-medium text-text-700 hover:bg-background-100">Cancelar</button><button type="button" disabled={!serviceId || !professionalId || !date || !time || updateMutation.isPending} onClick={() => updateMutation.mutate()} className="primary-button disabled:cursor-not-allowed disabled:opacity-60">{updateMutation.isPending ? 'Salvando...' : 'Salvar alterações'}</button></div>
    </Modal>
  )
}

function Step({ number, title, children }: { number: string; title: string; children: React.ReactNode }) { return <section><div className="mb-3 flex items-center gap-2"><span className="flex h-7 w-7 items-center justify-center rounded-full bg-primary-100 text-xs font-bold text-text-700">{number}</span><h3 className="font-semibold text-text-900">{title}</h3></div>{children}</section> }
function ChoiceButton({ selected, onClick, title, detail }: { selected: boolean; onClick: () => void; title: string; detail: string }) { return <button type="button" onClick={onClick} className={`rounded-xl border p-3 text-left ${selected ? 'border-primary-600 bg-primary-50 ring-1 ring-primary-600' : 'border-background-300 hover:border-primary-300'}`}><span className="block font-semibold text-text-900">{title}</span><span className="mt-1 block text-sm text-text-600">{detail}</span></button> }
function today() { const now = new Date(); const offset = now.getTimezoneOffset(); return new Date(now.getTime() - offset * 60000).toISOString().slice(0, 10) }
function normalizeTime(value: string) { return value.length === 5 ? `${value}:00` : value }
function shortTime(value: string) { return value.slice(0, 5) }
function formatMoney(value: number) { return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value) }
