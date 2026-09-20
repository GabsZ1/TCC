import { useMemo, useRef, useState } from 'react'
import FullCalendar from '@fullcalendar/react'
import dayGridPlugin from '@fullcalendar/daygrid'
import timeGridPlugin from '@fullcalendar/timegrid'
import interactionPlugin, { type DateClickArg } from '@fullcalendar/interaction'
import ptBrLocale from '@fullcalendar/core/locales/pt-br'
import type { DatesSetArg, EventClickArg, EventInput, DateSelectArg } from '@fullcalendar/core'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CalendarDays, ChevronLeft, Clock3, Plus, Settings2, Trash2, User } from 'lucide-react'
import { Modal } from '@/components/ui/Modal'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { getApiErrorMessage } from '@/lib/apiError'
import { completeAppointment } from '@/features/appointments/appointmentsApi'
import { createCalendarEvent, deleteCalendarEvent, fetchProfessionalAgenda, fetchProfessionalSchedule, updateCalendarEvent, updateProfessionalSchedule, type CalendarEventInput, type ProfessionalAgendaItem, type ProfessionalHour } from '@/features/appointments/professionalAgendaApi'

const AGENDA_KEY = ['professional-agenda']
interface VisibleRange { startsAt: string; endsAt: string }
interface EventEditorState { event: ProfessionalAgendaItem | null; startsAt: string; endsAt: string; allDay: boolean }

export function ProfessionalAgendaPage() {
  const calendarRef = useRef<FullCalendar>(null)
  const queryClient = useQueryClient()
  const [view, setView] = useState<'month' | 'week'>('month')
  const [range, setRange] = useState<VisibleRange | null>(null)
  const [editor, setEditor] = useState<EventEditorState | null>(null)
  const [selectedAppointment, setSelectedAppointment] = useState<ProfessionalAgendaItem | null>(null)
  const [deletingEvent, setDeletingEvent] = useState<ProfessionalAgendaItem | null>(null)
  const [scheduleOpen, setScheduleOpen] = useState(false)
  const agenda = useQuery({ queryKey: [...AGENDA_KEY, range?.startsAt, range?.endsAt], queryFn: () => fetchProfessionalAgenda(range!.startsAt, range!.endsAt), enabled: Boolean(range) })
  const events = useMemo(() => (agenda.data ?? []).map((item) => toCalendarEvent(item, view)), [agenda.data, view])

  function openWeek(date: Date) { setView('week'); calendarRef.current?.getApi().changeView('timeGridWeek', date) }
  function openMonth() { setView('month'); calendarRef.current?.getApi().changeView('dayGridMonth') }
  function openNewEvent(start: Date, end: Date, allDay: boolean) { setEditor({ event: null, startsAt: toLocalInput(start), endsAt: toLocalInput(end), allDay }) }
  function handleDateClick(info: DateClickArg) { if (view === 'month') openWeek(info.date); else openNewEvent(info.date, addHours(info.date, 1), info.allDay) }
  function handleSelect(info: DateSelectArg) { if (view === 'week') openNewEvent(info.start, info.end, info.allDay); else openWeek(info.start); info.view.calendar.unselect() }
  function handleEventClick(info: EventClickArg) {
    const item = info.event.extendedProps.item as ProfessionalAgendaItem
    if (item.type === 'APPOINTMENT') setSelectedAppointment(item)
    else setEditor({ event: item, startsAt: item.startsAt.slice(0, 16), endsAt: item.endsAt.slice(0, 16), allDay: item.allDay })
  }

  return <div className="p-4 sm:p-6 lg:p-8">
    <div className="mb-5 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
      <div className="flex items-center gap-3">{view === 'week' && <button type="button" onClick={openMonth} className="icon-button" aria-label="Voltar ao mês"><ChevronLeft size={22} /></button>}<div><h1 className="page-title">Minha agenda</h1><p className="page-subtitle">{view === 'month' ? 'Visão mensal dos seus atendimentos e compromissos.' : 'Organize sua semana e bloqueie horários pessoais.'}</p></div></div>
      <div className="flex flex-wrap gap-2"><button type="button" onClick={() => setScheduleOpen(true)} className="inline-flex items-center gap-2 rounded-xl border border-brand-200 bg-white px-4 py-2.5 text-sm font-semibold text-brand-700 hover:bg-brand-50"><Settings2 size={17} /> Jornada</button><button type="button" onClick={() => { const start = roundToNextHour(new Date()); openNewEvent(start, addHours(start, 1), false) }} className="primary-button"><Plus size={18} /> Novo compromisso</button></div>
    </div>
    {agenda.isError && <p role="alert" className="mb-4 rounded-xl border border-danger-500/30 bg-danger-500/5 px-4 py-3 text-sm text-danger-600">{getApiErrorMessage(agenda.error, 'Não foi possível carregar sua agenda.')}</p>}
    <section className={`professional-calendar rounded-2xl border border-brand-100 bg-white p-3 shadow-sm sm:p-5 ${agenda.isFetching ? 'opacity-70' : ''}`}>
      <FullCalendar ref={calendarRef} plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]} initialView="dayGridMonth" locale={ptBrLocale} firstDay={0} headerToolbar={{ left: 'prev,next today', center: 'title', right: '' }} buttonText={{ today: 'Hoje' }} height="auto" dayMaxEvents={3} nowIndicator selectable selectMirror select={handleSelect} dateClick={handleDateClick} eventClick={handleEventClick} datesSet={(info: DatesSetArg) => setRange({ startsAt: toLocalDateTime(info.start), endsAt: toLocalDateTime(info.end) })} events={events} eventTimeFormat={{ hour: '2-digit', minute: '2-digit', hour12: false }} slotLabelFormat={{ hour: '2-digit', minute: '2-digit', hour12: false }} slotMinTime="06:00:00" slotMaxTime="23:00:00" allDaySlot={false} noEventsText="Nenhum item" />
    </section>
    <div className="mt-4 flex flex-wrap gap-4 text-xs text-brand-600"><Legend color="bg-brand-600" label="Atendimento" /><Legend color="bg-sage-500" label="Compromisso pessoal" /><Legend color="bg-brand-300" label="Atendimento encerrado" /></div>
    {editor && <EventEditor initial={editor} onClose={() => setEditor(null)} onSaved={() => { setEditor(null); queryClient.invalidateQueries({ queryKey: AGENDA_KEY }) }} onDelete={editor.event ? () => { setEditor(null); setDeletingEvent(editor.event) } : undefined} />}
    {selectedAppointment && <AppointmentDetails item={selectedAppointment} onClose={() => setSelectedAppointment(null)} onCompleted={() => { setSelectedAppointment(null); queryClient.invalidateQueries({ queryKey: AGENDA_KEY }) }} />}
    {scheduleOpen && <ScheduleEditor onClose={() => setScheduleOpen(false)} />}
    {deletingEvent && <DeleteEventDialog event={deletingEvent} onClose={() => setDeletingEvent(null)} onDeleted={() => { setDeletingEvent(null); queryClient.invalidateQueries({ queryKey: AGENDA_KEY }) }} />}
  </div>
}

function EventEditor({ initial, onClose, onSaved, onDelete }: { initial: EventEditorState; onClose: () => void; onSaved: () => void; onDelete?: () => void }) {
  const [title, setTitle] = useState(initial.event?.title ?? '')
  const [description, setDescription] = useState(initial.event?.description ?? '')
  const [startsAt, setStartsAt] = useState(initial.startsAt)
  const [endsAt, setEndsAt] = useState(initial.endsAt)
  const [allDay, setAllDay] = useState(initial.allDay)
  const mutation = useMutation({ mutationFn: (input: CalendarEventInput) => initial.event ? updateCalendarEvent(initial.event.id, input) : createCalendarEvent(input), onSuccess: onSaved })
  const minimumEnd = minimumEndValue(startsAt, allDay)
  function changeStart(value: string) {
    setStartsAt(value)
    const nextMinimum = minimumEndValue(value, allDay)
    if (!endsAt || endsAt < nextMinimum) setEndsAt(nextMinimum)
  }
  function changeAllDay(checked: boolean) {
    setAllDay(checked)
    const normalizedStart = checked ? `${startsAt.slice(0, 10)}T00:00` : startsAt
    const nextMinimum = minimumEndValue(normalizedStart, checked)
    setStartsAt(normalizedStart)
    if (!endsAt || endsAt < nextMinimum || checked) setEndsAt(nextMinimum)
  }
  function submit(event: React.FormEvent) { event.preventDefault(); mutation.mutate(normalizeEventInput({ title, description: description || null, startsAt, endsAt, allDay })) }
  return <Modal title={initial.event ? 'Editar compromisso' : 'Novo compromisso'} onClose={onClose}><form onSubmit={submit} className="space-y-4"><Field label="Título" value={title} onChange={setTitle} required placeholder="Ex.: Consulta médica" /><label className="flex items-center gap-2 text-sm font-medium text-brand-800"><input type="checkbox" checked={allDay} onChange={(event) => changeAllDay(event.target.checked)} /> Ocupar o dia inteiro</label><div className="grid gap-4 sm:grid-cols-2"><DateTimeField label="Início" value={startsAt} onChange={changeStart} allDay={allDay} /><EndDateTimeField value={endsAt} onChange={setEndsAt} startsAt={startsAt} allDay={allDay} min={minimumEnd} /></div><p className="-mt-2 text-xs text-brand-500">{allDay ? 'O término indica o primeiro dia livre após o compromisso.' : 'No mesmo dia, aparecem somente horários posteriores ao início.'}</p><label className="block"><span className="text-sm font-medium text-brand-800">Descrição</span><textarea value={description} maxLength={500} onChange={(event) => setDescription(event.target.value)} rows={3} className="filter-input resize-none" /></label>{mutation.isError && <p role="alert" className="text-sm text-danger-600">{getApiErrorMessage(mutation.error, 'Não foi possível salvar o compromisso.')}</p>}<div className="flex items-center justify-between gap-3 pt-2">{onDelete ? <button type="button" onClick={onDelete} className="inline-flex items-center gap-2 text-sm font-semibold text-danger-600"><Trash2 size={16} /> Excluir</button> : <span />}<div className="flex gap-2"><button type="button" onClick={onClose} className="rounded-lg border border-brand-200 px-4 py-2 text-sm font-medium text-brand-700">Cancelar</button><button type="submit" disabled={mutation.isPending} className="primary-button disabled:opacity-60">{mutation.isPending ? 'Salvando...' : 'Salvar'}</button></div></div></form></Modal>
}

function ScheduleEditor({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient()
  const schedule = useQuery({ queryKey: ['professional-schedule'], queryFn: fetchProfessionalSchedule })
  if (schedule.isLoading) return <Modal title="Minha jornada" onClose={onClose}><p className="text-sm text-brand-600">Carregando horários...</p></Modal>
  if (schedule.isError || !schedule.data) return <Modal title="Minha jornada" onClose={onClose}><p className="text-sm text-danger-600">{getApiErrorMessage(schedule.error, 'Não foi possível carregar sua jornada.')}</p></Modal>
  return <ScheduleForm initial={schedule.data} onClose={onClose} onSaved={() => { queryClient.invalidateQueries({ queryKey: ['professional-schedule'] }); queryClient.invalidateQueries({ queryKey: AGENDA_KEY }); onClose() }} />
}

function ScheduleForm({ initial, onClose, onSaved }: { initial: ProfessionalHour[]; onClose: () => void; onSaved: () => void }) {
  const [hours, setHours] = useState(initial)
  const mutation = useMutation({ mutationFn: () => updateProfessionalSchedule(hours.map((hour) => ({ dayOfWeek: hour.dayOfWeek, closed: hour.closed, openingTime: hour.openingTime, closingTime: hour.closingTime }))), onSuccess: onSaved })
  function change(index: number, changes: Partial<ProfessionalHour>) { setHours((current) => current.map((hour, itemIndex) => itemIndex === index ? { ...hour, ...changes } : hour)) }
  return <Modal title="Minha jornada semanal" onClose={onClose}><p className="mb-4 text-sm text-brand-600">A jornada começa com o expediente do salão. Você pode reduzi-la ou fechar dias específicos.</p><div className="max-h-[60vh] space-y-2 overflow-y-auto pr-1">{hours.map((hour, index) => <div key={hour.dayOfWeek} className="rounded-xl bg-sand-50 p-3"><div className="flex items-center justify-between"><span className="text-sm font-semibold text-brand-800">{DAY_LABELS[hour.dayOfWeek]}</span><label className="flex items-center gap-2 text-xs text-brand-600"><input type="checkbox" checked={hour.closed} onChange={(event) => change(index, { closed: event.target.checked, openingTime: event.target.checked ? null : (hour.openingTime ?? '09:00'), closingTime: event.target.checked ? null : (hour.closingTime ?? '18:00') })} /> Fechado</label></div>{!hour.closed && <div className="mt-2 grid grid-cols-2 gap-2"><input aria-label={`Início de ${DAY_LABELS[hour.dayOfWeek]}`} type="time" value={hour.openingTime?.slice(0, 5) ?? ''} onChange={(event) => change(index, { openingTime: event.target.value })} className="rounded-lg border border-brand-200 px-2 py-1.5 text-sm" /><input aria-label={`Término de ${DAY_LABELS[hour.dayOfWeek]}`} type="time" value={hour.closingTime?.slice(0, 5) ?? ''} onChange={(event) => change(index, { closingTime: event.target.value })} className="rounded-lg border border-brand-200 px-2 py-1.5 text-sm" /></div>}</div>)}</div>{mutation.isError && <p className="mt-3 text-sm text-danger-600">{getApiErrorMessage(mutation.error, 'Não foi possível salvar sua jornada.')}</p>}<div className="mt-5 flex justify-end gap-2"><button type="button" onClick={onClose} className="rounded-lg border border-brand-200 px-4 py-2 text-sm text-brand-700">Cancelar</button><button type="button" onClick={() => mutation.mutate()} disabled={mutation.isPending} className="primary-button disabled:opacity-60">Salvar jornada</button></div></Modal>
}

function AppointmentDetails({ item, onClose, onCompleted }: { item: ProfessionalAgendaItem; onClose: () => void; onCompleted: () => void }) {
  const [openedAt] = useState(() => Date.now())
  const canComplete = item.status === 'CONFIRMED' && new Date(item.startsAt).getTime() <= openedAt
  const mutation = useMutation({ mutationFn: () => completeAppointment(item.id), onSuccess: onCompleted })
  return <Modal title={item.serviceName ?? item.title} onClose={onClose}><div className="space-y-3 text-sm text-brand-700"><p className="flex items-center gap-2"><User size={16} /> {item.clientName}</p><p className="flex items-center gap-2"><CalendarDays size={16} /> {formatDate(item.startsAt)}</p><p className="flex items-center gap-2"><Clock3 size={16} /> {formatTime(item.startsAt)} às {formatTime(item.endsAt)}</p><p>Status: <strong>{statusLabel(item.status)}</strong></p>{mutation.isError && <p className="text-danger-600">{getApiErrorMessage(mutation.error, 'Não foi possível concluir o atendimento.')}</p>}{canComplete && <button type="button" onClick={() => mutation.mutate()} disabled={mutation.isPending} className="primary-button disabled:opacity-60">{mutation.isPending ? 'Concluindo...' : 'Concluir atendimento'}</button>}</div></Modal>
}

function DeleteEventDialog({ event, onClose, onDeleted }: { event: ProfessionalAgendaItem; onClose: () => void; onDeleted: () => void }) { const mutation = useMutation({ mutationFn: () => deleteCalendarEvent(event.id), onSuccess: onDeleted }); return <ConfirmDialog title="Excluir compromisso" message={`Deseja excluir “${event.title}”?`} confirmLabel="Excluir compromisso" isLoading={mutation.isPending} onConfirm={() => mutation.mutate()} onCancel={onClose} /> }
function toCalendarEvent(item: ProfessionalAgendaItem, view: 'month' | 'week'): EventInput {
  const appointment = item.type === 'APPOINTMENT'
  const ended = item.status !== 'CONFIRMED'
  const fillsDayColumn = view === 'week' && item.allDay
  return {
    id: `${item.type}-${item.id}`,
    title: appointment ? `${item.serviceName} · ${item.clientName}` : item.title,
    start: item.startsAt,
    end: item.endsAt,
    allDay: fillsDayColumn ? false : item.allDay,
    backgroundColor: appointment ? (ended ? '#b79cd6' : '#64408c') : '#6f9a5f',
    borderColor: 'transparent',
    textColor: '#fff',
    extendedProps: { item },
  }
}
function normalizeEventInput(input: CalendarEventInput): CalendarEventInput {
  if (!input.allDay) return { ...input, startsAt: ensureSeconds(input.startsAt), endsAt: ensureSeconds(input.endsAt) }
  const startDate = input.startsAt.slice(0, 10)
  const selectedEndDate = input.endsAt.slice(0, 10)
  const endDate = selectedEndDate > startDate ? selectedEndDate : addDaysToDate(startDate, 1)
  return { ...input, startsAt: `${startDate}T00:00:00`, endsAt: `${endDate}T00:00:00` }
}
function Field({ label, value, onChange, required, placeholder }: { label: string; value: string; onChange: (value: string) => void; required?: boolean; placeholder?: string }) { return <label className="block"><span className="text-sm font-medium text-brand-800">{label}</span><input value={value} onChange={(event) => onChange(event.target.value)} required={required} maxLength={120} placeholder={placeholder} className="filter-input" /></label> }
function DateTimeField({ label, value, onChange, allDay, min }: { label: string; value: string; onChange: (value: string) => void; allDay: boolean; min?: string }) { return <label className="block min-w-0"><span className="text-sm font-medium text-brand-800">{label}</span><input type={allDay ? 'date' : 'datetime-local'} value={allDay ? value.slice(0, 10) : value} min={min ? (allDay ? min.slice(0, 10) : min) : undefined} onChange={(event) => onChange(allDay ? `${event.target.value}T00:00` : event.target.value)} required className="filter-input min-w-0" /></label> }
function EndDateTimeField({ value, onChange, startsAt, allDay, min }: { value: string; onChange: (value: string) => void; startsAt: string; allDay: boolean; min: string }) {
  if (allDay) return <DateTimeField label="Término" value={value} onChange={onChange} allDay min={min} />
  const endDate = value.slice(0, 10)
  const currentTime = value.slice(11, 16)
  const availableTimes = endTimeOptions(startsAt, endDate, currentTime)
  function changeDate(date: string) {
    const times = endTimeOptions(startsAt, date, currentTime)
    const time = times.includes(currentTime) ? currentTime : (times[0] ?? '00:00')
    onChange(`${date}T${time}`)
  }
  return <div className="min-w-0"><span className="text-sm font-medium text-brand-800">Término</span><div className="mt-2 grid min-w-0 grid-cols-2 gap-2"><input aria-label="Data de término" type="date" value={endDate} min={startsAt.slice(0, 10)} onChange={(event) => changeDate(event.target.value)} required className="min-w-0 w-full rounded-xl border border-brand-200 bg-white px-2 py-2.5 text-sm text-brand-900 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100" /><select aria-label="Horário de término" value={currentTime} onChange={(event) => onChange(`${endDate}T${event.target.value}`)} required className="min-w-0 w-full rounded-xl border border-brand-200 bg-white px-2 py-2.5 text-sm text-brand-900 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100">{availableTimes.map((time) => <option key={time} value={time}>{time}</option>)}</select></div></div>
}
function Legend({ color, label }: { color: string; label: string }) { return <span className="inline-flex items-center gap-2"><span className={`h-3 w-3 rounded ${color}`} /> {label}</span> }
function toLocalInput(date: Date) { return toLocalDateTime(date).slice(0, 16) }
function toLocalDateTime(date: Date) { const offset = date.getTimezoneOffset() * 60000; return new Date(date.getTime() - offset).toISOString().slice(0, 19) }
function ensureSeconds(value: string) { return value.length === 16 ? `${value}:00` : value }
function roundToNextHour(date: Date) { const result = new Date(date); result.setMinutes(0, 0, 0); result.setHours(result.getHours() + 1); return result }
function addHours(date: Date, hours: number) { return new Date(date.getTime() + hours * 60 * 60 * 1000) }
function addDaysToDate(value: string, days: number) { const date = new Date(`${value}T12:00:00`); date.setDate(date.getDate() + days); return toLocalDateTime(date).slice(0, 10) }
function minimumEndValue(startsAt: string, allDay: boolean) {
  if (!startsAt) return ''
  if (allDay) return `${addDaysToDate(startsAt.slice(0, 10), 1)}T00:00`
  return toLocalInput(addMinutesFromLocalInput(startsAt, 1))
}
function addMinutesFromLocalInput(value: string, minutes: number) { const date = new Date(value); date.setMinutes(date.getMinutes() + minutes); return date }
function endTimeOptions(startsAt: string, endDate: string, currentTime: string) {
  const startDate = startsAt.slice(0, 10)
  const startTime = startsAt.slice(11, 16)
  const times = Array.from({ length: 1440 }, (_, index) => `${String(Math.floor(index / 60)).padStart(2, '0')}:${String(index % 60).padStart(2, '0')}`)
  const validTimes = endDate === startDate ? times.filter((time) => time > startTime) : times
  if (currentTime && !validTimes.includes(currentTime) && (`${endDate}T${currentTime}` > startsAt)) validTimes.push(currentTime)
  return validTimes.sort()
}
function formatDate(value: string) { return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long' }).format(new Date(value)) }
function formatTime(value: string) { return value.slice(11, 16) }
function statusLabel(status: ProfessionalAgendaItem['status']) { return ({ CONFIRMED: 'Confirmado', CANCELLED: 'Cancelado', COMPLETED: 'Concluído', INVALIDATED: 'Invalidado' } as const)[status ?? 'CONFIRMED'] }
const DAY_LABELS = { MONDAY: 'Segunda-feira', TUESDAY: 'Terça-feira', WEDNESDAY: 'Quarta-feira', THURSDAY: 'Quinta-feira', FRIDAY: 'Sexta-feira', SATURDAY: 'Sábado', SUNDAY: 'Domingo' } as const
