import { useEffect, useMemo, useRef, useState } from 'react'
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
import { createCalendarEvent, deleteCalendarEvent, fetchProfessionalAgenda, fetchProfessionalSchedule, updateCalendarEvent, updateProfessionalSchedule, type CalendarEventInput, type ProfessionalAgendaItem, type ProfessionalHour, type ProfessionalHourInput } from '@/features/appointments/professionalAgendaApi'

const AGENDA_KEY = ['professional-agenda']
interface VisibleRange { startsAt: string; endsAt: string }
interface EventEditorState { event: ProfessionalAgendaItem | null; startsAt: string; endsAt: string; allDay: boolean }

export function ProfessionalAgendaPage() {
  const calendarRef = useRef<FullCalendar>(null)
  const queryClient = useQueryClient()
  const [view, setView] = useState<'month' | 'week'>('week')
  const [range, setRange] = useState<VisibleRange | null>(null)
  const [editor, setEditor] = useState<EventEditorState | null>(null)
  const [selectedAppointment, setSelectedAppointment] = useState<ProfessionalAgendaItem | null>(null)
  const [deletingEvent, setDeletingEvent] = useState<ProfessionalAgendaItem | null>(null)
  const [scheduleOpen, setScheduleOpen] = useState(false)
  const agenda = useQuery({ queryKey: [...AGENDA_KEY, range?.startsAt, range?.endsAt], queryFn: () => fetchProfessionalAgenda(range!.startsAt, range!.endsAt), enabled: Boolean(range), refetchInterval: 30_000 })
  const schedule = useQuery({ queryKey: ['professional-schedule'], queryFn: fetchProfessionalSchedule, staleTime: 60_000, refetchInterval: 5 * 60_000 })
  const events = useMemo(() => (agenda.data ?? []).map((item) => toCalendarEvent(item, view)), [agenda.data, view])
  const visibleHours = useMemo(() => calendarVisibleHours(agenda.data ?? [], schedule.data ?? []), [agenda.data, schedule.data])
  const businessHours = useMemo(() => (schedule.data ?? []).filter((hour) => !hour.closed && hour.openingTime && hour.closingTime).map((hour) => ({ daysOfWeek: [FULL_CALENDAR_DAYS[hour.dayOfWeek]], startTime: hour.openingTime!, endTime: hour.closingTime! })), [schedule.data])

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
      <FullCalendar ref={calendarRef} plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]} initialView="timeGridWeek" locale={ptBrLocale} firstDay={0} headerToolbar={{ left: 'prev,next today', center: 'title', right: '' }} buttonText={{ today: 'Hoje' }} height="auto" dayMaxEvents={3} nowIndicator selectable selectMirror select={handleSelect} dateClick={handleDateClick} eventClick={handleEventClick} datesSet={(info: DatesSetArg) => setRange({ startsAt: toLocalDateTime(info.start), endsAt: toLocalDateTime(info.end) })} events={events} businessHours={businessHours} eventTimeFormat={{ hour: '2-digit', minute: '2-digit', hour12: false }} slotLabelFormat={{ hour: '2-digit', minute: '2-digit', hour12: false }} slotMinTime={visibleHours.min} slotMaxTime={visibleHours.max} scrollTime={visibleHours.scroll} allDaySlot={false} noEventsText="Nenhum item" />
    </section>
    <p className="mt-2 text-xs text-brand-500">As áreas sombreadas ficam fora da sua jornada. A agenda é atualizada automaticamente.</p>
    <div className="mt-4 flex flex-wrap gap-4 text-xs text-brand-600"><Legend color="bg-brand-600" label="Atendimento confirmado" /><Legend color="bg-sage-500" label="Compromisso pessoal" /><Legend color="bg-brand-300" label="Atendimento concluído" /><Legend color="bg-danger-600" label="Atendimento cancelado" /><Legend color="bg-amber-700" label="Atendimento invalidado" /></div>
    {editor && <EventEditor initial={editor} onClose={() => setEditor(null)} onSaved={() => { setEditor(null); queryClient.invalidateQueries({ queryKey: AGENDA_KEY }) }} onDelete={editor.event ? () => { setEditor(null); setDeletingEvent(editor.event) } : undefined} />}
    {selectedAppointment && <AppointmentDetails item={selectedAppointment} onClose={() => setSelectedAppointment(null)} onCompleted={() => { setSelectedAppointment(null); queryClient.invalidateQueries({ queryKey: AGENDA_KEY }) }} />}
    {scheduleOpen && <ScheduleEditor onClose={() => setScheduleOpen(false)} />}
    {deletingEvent && <DeleteEventDialog event={deletingEvent} onClose={() => setDeletingEvent(null)} onDeleted={() => { setDeletingEvent(null); queryClient.invalidateQueries({ queryKey: AGENDA_KEY }) }} />}
  </div>
}

function EventEditor({ initial, onClose, onSaved, onDelete }: { initial: EventEditorState; onClose: () => void; onSaved: () => void; onDelete?: () => void }) {
  const [title, setTitle] = useState(initial.event?.title ?? '')
  const [description, setDescription] = useState(initial.event?.description ?? '')
  const [startsAt, setStartsAt] = useState(() => initial.allDay ? initial.startsAt : alignLocalInputToFiveMinutes(initial.startsAt))
  const [endsAt, setEndsAt] = useState(() => initial.allDay ? initial.endsAt : alignLocalInputToFiveMinutes(initial.endsAt))
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
  return <Modal title={initial.event ? 'Editar compromisso' : 'Novo compromisso'} onClose={onClose} size="lg"><form onSubmit={submit} className="space-y-4"><Field label="Título" value={title} onChange={setTitle} required placeholder="Ex.: Consulta médica" /><label className="flex items-center gap-2 text-sm font-medium text-brand-800"><input type="checkbox" checked={allDay} onChange={(event) => changeAllDay(event.target.checked)} /> Ocupar o dia inteiro</label><div className="grid gap-5 lg:grid-cols-2"><DateTimeField label="Início" value={startsAt} onChange={changeStart} allDay={allDay} /><EndDateTimeField value={endsAt} onChange={setEndsAt} startsAt={startsAt} allDay={allDay} min={minimumEnd} /></div><p className="-mt-2 text-xs text-brand-500">{allDay ? 'O término indica o primeiro dia livre após o compromisso.' : 'No mesmo dia, aparecem somente horários posteriores ao início.'}</p><label className="block"><span className="text-sm font-medium text-brand-800">Descrição</span><textarea value={description} maxLength={500} onChange={(event) => setDescription(event.target.value)} rows={3} className="filter-input resize-none" /></label>{mutation.isError && <p role="alert" className="text-sm text-danger-600">{getApiErrorMessage(mutation.error, 'Não foi possível salvar o compromisso.')}</p>}<div className="flex flex-wrap items-center justify-between gap-3 pt-2">{onDelete ? <button type="button" onClick={onDelete} className="inline-flex items-center gap-2 text-sm font-semibold text-danger-600"><Trash2 size={16} /> Excluir</button> : <span />}<div className="ml-auto flex gap-2"><button type="button" onClick={onClose} className="rounded-lg border border-brand-200 px-4 py-2 text-sm font-medium text-brand-700">Cancelar</button><button type="submit" disabled={mutation.isPending} className="primary-button disabled:opacity-60">{mutation.isPending ? 'Salvando...' : 'Salvar'}</button></div></div></form></Modal>
}

function ScheduleEditor({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient()
  const schedule = useQuery({ queryKey: ['professional-schedule'], queryFn: fetchProfessionalSchedule })
  if (schedule.isLoading) return <Modal title="Minha jornada" onClose={onClose}><p className="text-sm text-brand-600">Carregando horários...</p></Modal>
  if (schedule.isError || !schedule.data) return <Modal title="Minha jornada" onClose={onClose}><p className="text-sm text-danger-600">{getApiErrorMessage(schedule.error, 'Não foi possível carregar sua jornada.')}</p></Modal>
  return <ScheduleForm initial={schedule.data} onClose={onClose} onSaved={() => { queryClient.invalidateQueries({ queryKey: ['professional-schedule'] }); queryClient.invalidateQueries({ queryKey: AGENDA_KEY }); onClose() }} />
}

type ScheduleMode = 'inherit' | 'custom' | 'closed'

function modeOf(hour: ProfessionalHour): ScheduleMode {
  if (hour.inheritedFromEstablishment) return 'inherit'
  return hour.closed ? 'closed' : 'custom'
}

function ScheduleForm({ initial, onClose, onSaved }: { initial: ProfessionalHour[]; onClose: () => void; onSaved: () => void }) {
  const [hours, setHours] = useState(initial)
  const inputs = useMemo<ProfessionalHourInput[]>(() => hours.map((hour) => {
    const mode = modeOf(hour)
    return {
      dayOfWeek: hour.dayOfWeek,
      useEstablishmentHours: mode === 'inherit',
      closed: mode === 'closed',
      openingTime: mode === 'custom' ? hour.openingTime : null,
      closingTime: mode === 'custom' ? hour.closingTime : null,
    }
  }), [hours])
  const invalidDay = hours.find((hour) => modeOf(hour) === 'custom' && customHourError(hour))
  const mutation = useMutation({ mutationFn: () => updateProfessionalSchedule(inputs), onSuccess: onSaved })

  function selectMode(index: number, mode: ScheduleMode) {
    setHours((current) => current.map((hour, itemIndex) => {
      if (itemIndex !== index) return hour
      if (mode === 'inherit') return { ...hour, inheritedFromEstablishment: true, closed: false }
      if (mode === 'closed') return { ...hour, inheritedFromEstablishment: false, closed: true, openingTime: null, closingTime: null }
      return {
        ...hour,
        inheritedFromEstablishment: false,
        closed: false,
        openingTime: hour.openingTime ?? hour.establishmentOpeningTime ?? '09:00',
        closingTime: hour.closingTime ?? hour.establishmentClosingTime ?? '18:00',
      }
    }))
  }
  function changeTime(index: number, changes: { openingTime?: string; closingTime?: string }) {
    setHours((current) => current.map((hour, itemIndex) => itemIndex === index ? { ...hour, ...changes } : hour))
  }

  return <Modal title="Minha jornada semanal" onClose={onClose}>
    <p className="mb-4 text-sm text-brand-600">Por padrão, você acompanha o horário do salão — inclusive quando o administrador mudar o expediente depois. Personalize apenas os dias em que sua jornada for diferente.</p>
    <div className="max-h-[60vh] space-y-3 overflow-y-auto pr-1">
      {hours.map((hour, index) => {
        const mode = modeOf(hour)
        const establishmentLabel = hour.establishmentClosed
          ? 'Salão fechado neste dia'
          : `${hour.establishmentOpeningTime?.slice(0, 5)} — ${hour.establishmentClosingTime?.slice(0, 5)}`
        return <div key={hour.dayOfWeek} className="rounded-xl bg-sand-50 p-3">
          <span className="text-sm font-semibold text-brand-800">{DAY_LABELS[hour.dayOfWeek]}</span>
          <div className="mt-2 space-y-1.5 text-sm text-brand-700">
            <label className="flex items-center gap-2">
              <input type="radio" name={`mode-${hour.dayOfWeek}`} checked={mode === 'inherit'} onChange={() => selectMode(index, 'inherit')} />
              Usar horário do salão <span className="text-xs text-brand-500">({establishmentLabel})</span>
            </label>
            <label className="flex items-center gap-2">
              <input type="radio" name={`mode-${hour.dayOfWeek}`} checked={mode === 'custom'} disabled={hour.establishmentClosed} onChange={() => selectMode(index, 'custom')} />
              Personalizar {hour.establishmentClosed && <span className="text-xs text-brand-500">(indisponível com o salão fechado)</span>}
            </label>
            {mode === 'custom' && <div className="ml-6 grid grid-cols-2 gap-2">
              <input aria-label={`Início de ${DAY_LABELS[hour.dayOfWeek]}`} type="time" step={300} min={hour.establishmentOpeningTime?.slice(0, 5)} max={hour.establishmentClosingTime?.slice(0, 5)} value={hour.openingTime?.slice(0, 5) ?? ''} onChange={(event) => changeTime(index, { openingTime: event.target.value })} className="rounded-lg border border-brand-200 px-2 py-1.5 text-sm" />
              <input aria-label={`Término de ${DAY_LABELS[hour.dayOfWeek]}`} type="time" step={300} min={hour.establishmentOpeningTime?.slice(0, 5)} max={hour.establishmentClosingTime?.slice(0, 5)} value={hour.closingTime?.slice(0, 5) ?? ''} onChange={(event) => changeTime(index, { closingTime: event.target.value })} className="rounded-lg border border-brand-200 px-2 py-1.5 text-sm" />
            </div>}
            <label className="flex items-center gap-2">
              <input type="radio" name={`mode-${hour.dayOfWeek}`} checked={mode === 'closed'} onChange={() => selectMode(index, 'closed')} />
              Não trabalho neste dia
            </label>
          </div>
        </div>
      })}
    </div>
    {invalidDay && <p role="alert" className="mt-3 text-sm text-danger-600">Em {DAY_LABELS[invalidDay.dayOfWeek]}, {customHourError(invalidDay)}</p>}
    {mutation.isError && <p className="mt-3 text-sm text-danger-600">{getApiErrorMessage(mutation.error, 'Não foi possível salvar sua jornada.')}</p>}
    <div className="mt-5 flex justify-end gap-2">
      <button type="button" onClick={onClose} className="rounded-lg border border-brand-200 px-4 py-2 text-sm text-brand-700">Cancelar</button>
      <button type="button" onClick={() => mutation.mutate()} disabled={mutation.isPending || Boolean(invalidDay)} className="primary-button disabled:opacity-60">Salvar jornada</button>
    </div>
  </Modal>
}

function AppointmentDetails({ item, onClose, onCompleted }: { item: ProfessionalAgendaItem; onClose: () => void; onCompleted: () => void }) {
  const now = useCurrentTimeUntil(item.startsAt, item.status === 'CONFIRMED')
  const [confirming, setConfirming] = useState(false)
  const canComplete = item.status === 'CONFIRMED' && new Date(item.startsAt).getTime() <= now
  const mutation = useMutation({ mutationFn: () => completeAppointment(item.id), onSuccess: onCompleted })
  if (confirming) {
    return <ConfirmDialog
      title="Concluir atendimento"
      message={`Confirmar a conclusão do atendimento de ${item.clientName ?? 'cliente'}? Essa ação não pode ser desfeita.`}
      confirmLabel={mutation.isPending ? 'Concluindo...' : 'Concluir atendimento'}
      isLoading={mutation.isPending}
      errorMessage={mutation.isError ? getApiErrorMessage(mutation.error, 'Não foi possível concluir o atendimento.') : null}
      onConfirm={() => mutation.mutate()}
      onCancel={() => setConfirming(false)}
    />
  }
  return <Modal title={item.serviceName ?? item.title} onClose={onClose}><div className="space-y-3 text-sm text-brand-700"><p className="flex items-center gap-2"><User size={16} /> {item.clientName}</p><p className="flex items-center gap-2"><CalendarDays size={16} /> {formatDate(item.startsAt)}</p><p className="flex items-center gap-2"><Clock3 size={16} /> {formatTime(item.startsAt)} às {formatTime(item.endsAt)}</p><p>Status: <strong>{statusLabel(item.status)}</strong></p>{mutation.isError && <p className="text-danger-600">{getApiErrorMessage(mutation.error, 'Não foi possível concluir o atendimento.')}</p>}{canComplete && <button type="button" onClick={() => setConfirming(true)} className="primary-button">Concluir atendimento</button>}</div></Modal>
}

function DeleteEventDialog({ event, onClose, onDeleted }: { event: ProfessionalAgendaItem; onClose: () => void; onDeleted: () => void }) { const mutation = useMutation({ mutationFn: () => deleteCalendarEvent(event.id), onSuccess: onDeleted }); return <ConfirmDialog title="Excluir compromisso" message={`Deseja excluir “${event.title}”?`} confirmLabel="Excluir compromisso" isLoading={mutation.isPending} errorMessage={mutation.isError ? getApiErrorMessage(mutation.error, 'Não foi possível excluir o compromisso.') : null} onConfirm={() => mutation.mutate()} onCancel={onClose} /> }
function toCalendarEvent(item: ProfessionalAgendaItem, view: 'month' | 'week'): EventInput {
  const appointment = item.type === 'APPOINTMENT'
  const fillsDayColumn = view === 'week' && item.allDay
  return {
    id: `${item.type}-${item.id}`,
    title: appointment ? `${item.serviceName} · ${item.clientName}` : item.title,
    start: item.startsAt,
    end: item.endsAt,
    allDay: fillsDayColumn ? false : item.allDay,
    backgroundColor: eventColor(item),
    borderColor: 'transparent',
    textColor: '#fff',
    extendedProps: { item },
  }
}
// Cada situação do atendimento tem uma cor própria no calendário — antes cancelado,
// concluído e invalidado eram todos exibidos com a mesma cor "encerrado", dificultando
// distinguir um caso do outro só olhando a agenda.
function eventColor(item: ProfessionalAgendaItem) {
  if (item.type !== 'APPOINTMENT') return '#6f9a5f'
  switch (item.status) {
    case 'COMPLETED': return '#b79cd6'
    case 'CANCELLED': return '#a63a30'
    case 'INVALIDATED': return '#b45309'
    default: return '#64408c'
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
function DateTimeField({ label, value, onChange, allDay, min }: { label: string; value: string; onChange: (value: string) => void; allDay: boolean; min?: string }) {
  if (allDay) return <label className="block min-w-0"><span className="text-sm font-medium text-brand-800">{label}</span><input type="date" value={value.slice(0, 10)} min={min?.slice(0, 10)} onChange={(event) => onChange(`${event.target.value}T00:00`)} required className="filter-input min-w-0" /></label>
  const date = value.slice(0, 10)
  const time = alignTimeToFiveMinutes(value.slice(11, 16))
  return <div className="min-w-0"><span className="text-sm font-medium text-brand-800">{label}</span><div className="mt-2 grid min-w-0 grid-cols-1 gap-2 sm:grid-cols-[minmax(0,1fr)_auto]"><input aria-label={`Data de ${label.toLowerCase()}`} type="date" value={date} min={min?.slice(0, 10)} onChange={(event) => onChange(`${event.target.value}T${time}`)} required className="min-w-0 w-full rounded-xl border border-brand-200 bg-white px-3 py-2.5 text-sm text-brand-900 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100" /><TimeSelect label={label} value={time} onChange={(nextTime) => onChange(`${date}T${nextTime}`)} /></div></div>
}
function EndDateTimeField({ value, onChange, startsAt, allDay, min }: { value: string; onChange: (value: string) => void; startsAt: string; allDay: boolean; min: string }) {
  if (allDay) return <DateTimeField label="Término" value={value} onChange={onChange} allDay min={min} />
  const startDate = startsAt.slice(0, 10)
  const startTime = startsAt.slice(11, 16)
  const endDate = value.slice(0, 10)
  const currentTime = alignTimeToFiveMinutes(value.slice(11, 16))
  const sameDay = endDate === startDate
  const minTime = sameDay ? addMinutesToTime(startTime, 5) : undefined
  function changeDate(date: string) {
    const nextSameDay = date === startDate
    const time = nextSameDay && currentTime < addMinutesToTime(startTime, 5) ? addMinutesToTime(startTime, 5) : currentTime
    onChange(`${date}T${time}`)
  }
  function changeTime(time: string) {
    onChange(`${endDate}T${sameDay && time < minTime! ? minTime : time}`)
  }
  return <div className="min-w-0"><span className="text-sm font-medium text-brand-800">Término</span><div className="mt-2 grid min-w-0 grid-cols-1 gap-2 sm:grid-cols-[minmax(0,1fr)_auto]"><input aria-label="Data de término" type="date" value={endDate} min={startDate} onChange={(event) => changeDate(event.target.value)} required className="min-w-0 w-full rounded-xl border border-brand-200 bg-white px-3 py-2.5 text-sm text-brand-900 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100" /><TimeSelect label="Término" value={currentTime} min={minTime} onChange={changeTime} /></div></div>
}
function TimeSelect({ label, value, min, onChange }: { label: string; value: string; min?: string; onChange: (value: string) => void }) {
  const alignedValue = alignTimeToFiveMinutes(value)
  const [hour, minute] = alignedValue.split(':')
  function update(nextHour: string, nextMinute: string) {
    const next = `${nextHour}:${nextMinute}`
    onChange(min && next < min ? min : next)
  }
  return <div className="flex w-full items-center gap-1 sm:w-auto" aria-label={`Horário de ${label.toLowerCase()}`}>
    <select aria-label={`Hora de ${label.toLowerCase()}`} value={hour} onChange={(event) => update(event.target.value, minute)} className="min-w-0 flex-1 rounded-xl border border-brand-200 bg-white px-3 py-2.5 text-sm text-brand-900 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 sm:flex-none">{HOUR_OPTIONS.map((option) => <option key={option} value={option}>{option}</option>)}</select>
    <span aria-hidden className="font-semibold text-brand-500">:</span>
    <select aria-label={`Minutos de ${label.toLowerCase()}`} value={minute} onChange={(event) => update(hour, event.target.value)} className="min-w-0 flex-1 rounded-xl border border-brand-200 bg-white px-3 py-2.5 text-sm text-brand-900 outline-none focus:border-brand-500 focus:ring-2 focus:ring-brand-100 sm:flex-none">{MINUTE_OPTIONS.map((option) => <option key={option} value={option}>{option}</option>)}</select>
  </div>
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
  return toLocalInput(addMinutesFromLocalInput(startsAt, 5))
}
function addMinutesFromLocalInput(value: string, minutes: number) { const date = new Date(value); date.setMinutes(date.getMinutes() + minutes); return date }
function addMinutesToTime(time: string, amount: number) {
  const [hours, minutes] = time.split(':').map(Number)
  const total = Math.min(hours * 60 + minutes + amount, 1435)
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`
}
function alignTimeToFiveMinutes(time: string) {
  const [hours = 0, minutes = 0] = time.split(':').map(Number)
  const total = Math.min(hours * 60 + Math.ceil(minutes / 5) * 5, 1435)
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`
}
function alignLocalInputToFiveMinutes(value: string) { return `${value.slice(0, 10)}T${alignTimeToFiveMinutes(value.slice(11, 16))}` }
function customHourError(hour: ProfessionalHour) {
  if (hour.establishmentClosed) return 'não é possível personalizar porque o salão está fechado.'
  if (!hour.openingTime || !hour.closingTime || hour.openingTime >= hour.closingTime) return 'o horário inicial deve ser anterior ao horário final.'
  if (hour.establishmentOpeningTime && hour.openingTime.slice(0, 5) < hour.establishmentOpeningTime.slice(0, 5)) return `o início não pode ser anterior a ${hour.establishmentOpeningTime.slice(0, 5)}.`
  if (hour.establishmentClosingTime && hour.closingTime.slice(0, 5) > hour.establishmentClosingTime.slice(0, 5)) return `o término não pode ser posterior a ${hour.establishmentClosingTime.slice(0, 5)}.`
  return null
}
function useCurrentTimeUntil(startsAt: string, enabled: boolean) {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!enabled) return
    const start = new Date(startsAt).getTime()
    if (start <= now) return
    const timeout = window.setTimeout(() => setNow(Date.now()), Math.min(start - now + 50, 30_000))
    return () => window.clearTimeout(timeout)
  }, [enabled, now, startsAt])
  return now
}
function calendarVisibleHours(items: ProfessionalAgendaItem[], hours: ProfessionalHour[]) {
  const ranges: Array<[number, number]> = []
  hours.filter((hour) => !hour.closed && hour.openingTime && hour.closingTime).forEach((hour) => {
    ranges.push([timeToMinutes(hour.openingTime!), timeToMinutes(hour.closingTime!)])
  })
  items.forEach((item) => {
    if (!item.allDay) ranges.push([timeToMinutes(item.startsAt.slice(11, 16)), item.startsAt.slice(0, 10) === item.endsAt.slice(0, 10) ? timeToMinutes(item.endsAt.slice(11, 16)) : 1440])
  })
  if (ranges.length === 0) return { min: '06:00:00', max: '23:00:00', scroll: '08:00:00' }
  const minimum = Math.max(0, Math.floor((Math.min(...ranges.map(([start]) => start)) - 60) / 60) * 60)
  const maximum = Math.min(1440, Math.ceil((Math.max(...ranges.map(([, end]) => end)) + 60) / 60) * 60)
  return { min: minutesToCalendarTime(minimum), max: minutesToCalendarTime(Math.max(maximum, minimum + 60)), scroll: minutesToCalendarTime(Math.min(Math.max(minimum, 8 * 60), maximum - 60)) }
}
function timeToMinutes(value: string) { const [hour, minute] = value.split(':').map(Number); return hour * 60 + minute }
function minutesToCalendarTime(value: number) { if (value >= 1440) return '24:00:00'; return `${String(Math.floor(value / 60)).padStart(2, '0')}:${String(value % 60).padStart(2, '0')}:00` }
function formatDate(value: string) { return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long' }).format(new Date(value)) }
function formatTime(value: string) { return value.slice(11, 16) }
function statusLabel(status: ProfessionalAgendaItem['status']) { return ({ CONFIRMED: 'Confirmado', CANCELLED: 'Cancelado', COMPLETED: 'Concluído', INVALIDATED: 'Invalidado' } as const)[status ?? 'CONFIRMED'] }
const DAY_LABELS = { MONDAY: 'Segunda-feira', TUESDAY: 'Terça-feira', WEDNESDAY: 'Quarta-feira', THURSDAY: 'Quinta-feira', FRIDAY: 'Sexta-feira', SATURDAY: 'Sábado', SUNDAY: 'Domingo' } as const
const FULL_CALENDAR_DAYS: Record<ProfessionalHour['dayOfWeek'], number> = { SUNDAY: 0, MONDAY: 1, TUESDAY: 2, WEDNESDAY: 3, THURSDAY: 4, FRIDAY: 5, SATURDAY: 6 }
const HOUR_OPTIONS = Array.from({ length: 24 }, (_, hour) => String(hour).padStart(2, '0'))
const MINUTE_OPTIONS = Array.from({ length: 12 }, (_, index) => String(index * 5).padStart(2, '0'))
