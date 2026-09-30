import { useEffect, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { IMaskInput } from 'react-imask'
import { useAuth } from '@/hooks/useAuth'
import { formatDateTime } from '@/lib/format'
import { APPOINTMENT_STATUS_LABELS, APPOINTMENT_STATUS_STYLES } from '@/features/appointments/appointmentStatus'
import { getApiErrorMessage } from '@/lib/apiError'
import { formatPhone, isValidPhone, PHONE_MASK } from '@/lib/phone'
import { createCustomer, createCustomerAppointment, fetchBookingOptions, fetchCustomerAppointments, fetchCustomerDuplicates, fetchCustomerSuggestions, fetchCustomers, fetchInternalAvailability, keepCustomersSeparate, mergeCustomers, updateCustomer, type Customer } from './customersApi'

export function CustomersPage() {
  const queryClient = useQueryClient()
  const { user } = useAuth()
  // O profissional só agenda na própria agenda (o backend também garante isso).
  const isProfessional = user?.role === 'PROFESSIONAL'
  const [search, setSearch] = useState('')
  const [customerPage, setCustomerPage] = useState(0)
  const [activeFilter, setActiveFilter] = useState('all')
  const [selected, setSelected] = useState<Customer | null>(null)
  const [editing, setEditing] = useState<Customer | 'new' | null>(null)
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [serviceId, setServiceId] = useState('')
  const [professionalId, setProfessionalId] = useState('')
  const [date, setDate] = useState('')
  const [time, setTime] = useState('')
  const debouncedSearch = useDebouncedValue(search)
  const debouncedPhone = useDebouncedValue(phone)
  const debouncedEmail = useDebouncedValue(email)
  const [minBookingDate] = useState(() => {
    const now = new Date()
    return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 10)
  })
  const customers = useQuery({ queryKey: ['customers', debouncedSearch, customerPage, activeFilter], queryFn: () => fetchCustomers(debouncedSearch, customerPage, activeFilter === 'all' ? undefined : activeFilter === 'active') })
  const suggestions = useQuery({
    queryKey: ['customer-suggestions', debouncedPhone, debouncedEmail],
    queryFn: () => fetchCustomerSuggestions(debouncedPhone, debouncedEmail),
    enabled: editing === 'new' && (debouncedPhone.replace(/\D/g, '').length >= 10 || debouncedEmail.includes('@')),
  })
  const duplicates = useQuery({ queryKey: ['customer-duplicates', selected?.id], queryFn: () => fetchCustomerDuplicates(selected!.id), enabled: !!selected && user?.role === 'ADMIN' })
  const options = useQuery({ queryKey: ['customer-booking-options', serviceId], queryFn: () => fetchBookingOptions(serviceId ? Number(serviceId) : undefined) })
  const bookingProfessionalId = isProfessional ? Number(user?.id) : Number(professionalId)
  const availability = useQuery({
    queryKey: ['internal-availability', selected?.id, serviceId, bookingProfessionalId, date],
    queryFn: () => fetchInternalAvailability(selected!.id, Number(serviceId), bookingProfessionalId, date),
    enabled: !!selected?.active && !!serviceId && !!bookingProfessionalId && !!date,
    retry: false,
  })
  const save = useMutation({
    mutationFn: () => editing === 'new' ? createCustomer({ name, phone, email }) : updateCustomer((editing as Customer).id, { name, phone, email, active: (editing as Customer).active }),
    onSuccess: (customer) => { queryClient.invalidateQueries({ queryKey: ['customers'] }); setSelected(customer); setTime(''); setEditing(null) },
  })
  const booking = useMutation({
    mutationFn: () => createCustomerAppointment(selected!.id, Number(serviceId), bookingProfessionalId, `${date}T${time.slice(0, 5)}:00`),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['customer-appointments', selected?.id] }); queryClient.invalidateQueries({ queryKey: ['internal-availability'] }); queryClient.invalidateQueries({ queryKey: ['establishment-agenda'] }); setTime('') },
  })
  const status = useMutation({
    mutationFn: () => updateCustomer(selected!.id, { name: selected!.name, phone: selected!.phone, email: selected!.email ?? '', active: !selected!.active }),
    onSuccess: (customer) => { setSelected(customer); queryClient.invalidateQueries({ queryKey: ['customers'] }) },
  })
  const merge = useMutation({
    mutationFn: (otherId: number) => mergeCustomers(selected!.id, otherId),
    onSuccess: (customer) => { setSelected(customer); setTime(''); queryClient.invalidateQueries({ queryKey: ['customers'] }); queryClient.invalidateQueries({ queryKey: ['customer-duplicates'] }); queryClient.invalidateQueries({ queryKey: ['customer-appointments'] }) },
  })
  const separate = useMutation({
    mutationFn: (otherId: number) => keepCustomersSeparate(selected!.id, otherId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['customer-duplicates'] }),
  })

  function openForm(customer: Customer | 'new') {
    setEditing(customer)
    setName(customer === 'new' ? '' : customer.name)
    setPhone(customer === 'new' ? '' : customer.phone)
    setEmail(customer === 'new' ? '' : customer.email ?? '')
  }
  function submitCustomer(event: FormEvent) { event.preventDefault(); if (isValidPhone(phone)) save.mutate() }
  function submitBooking(event: FormEvent) { event.preventDefault(); booking.mutate() }

  return <div className="page-shell">
    <header className="border-b border-background-300 pb-7">
      <p className="eyebrow">Gestão</p><h1 className="page-title mt-3">Clientes do estabelecimento</h1>
      <p className="page-subtitle">Cadastre clientes sem conta, consulte o histórico e marque atendimentos recebidos no balcão, por telefone ou WhatsApp.</p>
    </header>
    <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(260px,1fr)_minmax(360px,2fr)]">
      <section className="surface-card p-5">
        <div className="flex items-center justify-between gap-3"><h2 className="font-semibold">Clientes</h2><button className="btn-primary" onClick={() => openForm('new')}>Novo cliente</button></div>
        <input className="filter-input mt-4 w-full" aria-label="Buscar cliente" placeholder="Buscar por nome, telefone ou e-mail" value={search} onChange={(event) => { setSearch(event.target.value); setCustomerPage(0) }} />
        <select className="filter-input mt-2 w-full" aria-label="Filtrar clientes por status" value={activeFilter} onChange={(event) => { setActiveFilter(event.target.value); setCustomerPage(0) }}><option value="all">Todos</option><option value="active">Ativos</option><option value="inactive">Inativos</option></select>
        {customers.isError && <p role="alert" className="mt-3 text-danger-600">{getApiErrorMessage(customers.error, 'Não foi possível carregar os clientes.')}</p>}
        <div className="mt-4 space-y-2">{customers.data?.content.map((customer) => <button key={customer.id} type="button" onClick={() => { setSelected(customer); setTime(''); merge.reset(); separate.reset() }} className="w-full rounded-xl border border-background-300 p-3 text-left hover:bg-background-50">
          <strong className="block">{customer.name}</strong><span className="text-sm text-text-600">{formatPhone(customer.phone)}{customer.active ? '' : ' · Inativo'}</span>
        </button>)}</div>
        {customers.data && customers.data.totalPages > 1 && <div className="mt-4 flex items-center justify-between text-sm"><button type="button" disabled={customerPage === 0} onClick={() => setCustomerPage(customerPage - 1)}>Anterior</button><span>Página {customerPage + 1} de {customers.data.totalPages}</span><button type="button" disabled={customerPage + 1 >= customers.data.totalPages} onClick={() => setCustomerPage(customerPage + 1)}>Próxima</button></div>}
      </section>
      <section className="space-y-6">
        {editing && <form onSubmit={submitCustomer} className="surface-card space-y-3 p-5">
          <h2 className="font-semibold">{editing === 'new' ? 'Novo cliente' : 'Editar cliente'}</h2>
          <label className="block text-sm">Nome<input className="filter-input mt-1 w-full" required maxLength={150} value={name} onChange={(event) => setName(event.target.value)} /></label>
          <label className="block text-sm">Telefone<IMaskInput mask={PHONE_MASK} className="filter-input mt-1 w-full" required inputMode="tel" placeholder="(00) 00000-0000" value={formatPhone(phone)} onAccept={(value: string) => setPhone(value)} /></label>
          {phone && !isValidPhone(phone) && <p className="text-sm text-danger-600">Informe um telefone com DDD e 10 ou 11 dígitos.</p>}
          <label className="block text-sm">Email (opcional)<input className="filter-input mt-1 w-full" type="email" maxLength={150} value={email} onChange={(event) => setEmail(event.target.value)} /></label>
          {editing === 'new' && suggestions.data && suggestions.data.length > 0 && <div className="rounded-xl border border-background-300 p-3 text-sm">
            <p className="font-semibold">Possíveis clientes já cadastrados</p>
            <p className="mt-1 text-text-600">Selecione um cadastro existente para agendar, ou salve este formulário para criar outro cliente.</p>
            {suggestions.data.map((customer) => <div key={customer.id} className="mt-2 flex flex-wrap items-center justify-between gap-2 border-t border-background-300 pt-2">
              <span>{customer.name} · {formatPhone(customer.phone)}{customer.email ? ` · ${customer.email}` : ''}</span>
              <button type="button" className="font-semibold" onClick={() => { setSelected(customer); setTime(''); setEditing(null) }}>Usar cadastro existente</button>
            </div>)}
          </div>}
          {editing === 'new' && suggestions.isError && <p role="alert" className="text-danger-600">{getApiErrorMessage(suggestions.error, 'Não foi possível consultar cadastros semelhantes.')}</p>}
          {save.isError && <p role="alert" className="text-danger-600">{getApiErrorMessage(save.error, 'Não foi possível salvar o cliente.')}</p>}
          <div className="flex gap-3"><button className="btn-primary" disabled={save.isPending || !isValidPhone(phone)}>Salvar</button><button type="button" onClick={() => setEditing(null)}>Cancelar</button></div>
        </form>}
        {selected ? <>
          <section className="surface-card p-5"><div className="flex flex-wrap items-center justify-between gap-3"><div><h2 className="text-lg font-semibold">{selected.name}</h2><p className="text-sm text-text-600">{formatPhone(selected.phone)}{selected.email ? ` · ${selected.email}` : ''}</p><p className="mt-1 text-xs text-text-600">{selected.userId != null ? 'Conta vinculada' : 'Conta não vinculada'}</p></div><div className="flex gap-3"><button onClick={() => openForm(selected)}>Editar</button>{!isProfessional && <button onClick={() => status.mutate()} disabled={status.isPending}>{selected.active ? 'Desativar' : 'Reativar'}</button>}</div></div>
          </section>
          {user?.role === 'ADMIN' && (duplicates.data?.length || duplicates.isError) && <section className="surface-card p-5">
            <h2 className="font-semibold">Possíveis cadastros duplicados</h2>
            <p className="mt-1 text-sm text-text-600">Compare os dados e históricos antes de decidir.</p>
            {duplicates.isError && <p role="alert" className="mt-3 text-sm text-danger-600">{getApiErrorMessage(duplicates.error, 'Não foi possível consultar possíveis duplicidades.')}</p>}
            {duplicates.data?.map((candidate) => <div key={candidate.id} className="mt-3 border-t border-background-300 pt-3 text-sm">
              <strong>{candidate.name}</strong><p className="text-text-600">{candidate.phone ? formatPhone(candidate.phone) : 'Sem telefone'}{candidate.email ? ` · ${candidate.email}` : ''}</p>
              <p className="text-text-600">{candidate.linked ? 'Conta vinculada' : 'Conta não vinculada'} · {candidate.appointmentCount} atendimento(s)</p>
              <div className="mt-2 flex gap-3"><button type="button" className="btn-primary" disabled={merge.isPending || separate.isPending} onClick={() => { if (window.confirm('Mesclar os históricos destes cadastros? Esta ação não pode ser desfeita.')) merge.mutate(candidate.id) }}>Mesclar cadastros</button>
                <button type="button" disabled={merge.isPending || separate.isPending} onClick={() => separate.mutate(candidate.id)}>Manter separados</button></div>
            </div>)}
            {merge.isError && <p role="alert" className="mt-3 text-sm text-danger-600">{getApiErrorMessage(merge.error, 'Não foi possível mesclar os cadastros.')}</p>}
            {separate.isError && <p role="alert" className="mt-3 text-sm text-danger-600">{getApiErrorMessage(separate.error, 'Não foi possível manter os cadastros separados.')}</p>}
          </section>}
          {selected.active && <form onSubmit={submitBooking} className="surface-card space-y-3 p-5"><h2 className="font-semibold">Agendar para {selected.name}</h2>
            <label className="block text-sm">Serviço<select required className="filter-input mt-1 w-full" value={serviceId} onChange={(event) => { setServiceId(event.target.value); setProfessionalId(''); setTime('') }}><option value="">Selecione</option>{options.data?.services.map((service) => <option key={service.id} value={service.id}>{service.name}</option>)}</select></label>
            {isProfessional
              ? <p className="text-sm">Profissional<span className="filter-input mt-1 block w-full bg-background-100 text-text-700">{user?.name} (sua agenda)</span></p>
              : <label className="block text-sm">Profissional<select required disabled={!serviceId} className="filter-input mt-1 w-full" value={professionalId} onChange={(event) => { setProfessionalId(event.target.value); setTime('') }}><option value="">Selecione</option>{options.data?.professionals.map((employee) => <option key={employee.id} value={employee.id}>{employee.name}</option>)}</select></label>}
            <label className="block text-sm">Data<input required type="date" min={minBookingDate} className="filter-input mt-1 w-full" value={date} onChange={(event) => { setDate(event.target.value); setTime('') }} /></label>
            <div><p className="text-sm">Horários disponíveis</p><div className="mt-2 flex flex-wrap gap-2">
              {availability.isFetching && <span className="text-sm text-text-600">Buscando horários...</span>}
              {availability.data?.availableTimes.map((slot) => <button key={slot} type="button" aria-pressed={time === slot} onClick={() => setTime(slot)} className={time === slot ? 'btn-primary' : 'rounded-lg border border-background-300 px-3 py-2 text-sm'}>{slot.slice(0, 5)}</button>)}
              {availability.data?.availableTimes.length === 0 && <span className="text-sm text-text-600">Nenhum horário disponível nesta data.</span>}
              {availability.isError && <p role="alert" className="text-sm text-danger-600">{getApiErrorMessage(availability.error, 'Não foi possível consultar os horários.')}</p>}
            </div></div>
            {booking.isError && <p role="alert" className="text-danger-600">{getApiErrorMessage(booking.error, 'Não foi possível agendar.')}</p>}
            {booking.isSuccess && <p role="status" className="text-success-700">Agendamento criado.</p>}
            <button className="btn-primary" disabled={booking.isPending || !time || !availability.data?.availableTimes.includes(time)}>Agendar</button>
          </form>}
          <CustomerHistory key={selected.id} customerId={selected.id} />
        </> : <div className="surface-card p-5 text-text-600">Selecione um cliente para ver o histórico e agendar.</div>}
      </section>
    </div>
  </div>
}

function useDebouncedValue<T>(value: T, delay = 300) {
  const [debouncedValue, setDebouncedValue] = useState(value)

  useEffect(() => {
    const timeoutId = window.setTimeout(() => setDebouncedValue(value), delay)
    return () => window.clearTimeout(timeoutId)
  }, [value, delay])

  return debouncedValue
}

/** O `key` do cliente reinicia a página ao trocar de cliente. */
function CustomerHistory({ customerId }: { customerId: number }) {
  const [page, setPage] = useState(0)
  const history = useQuery({ queryKey: ['customer-appointments', customerId, page], queryFn: () => fetchCustomerAppointments(customerId, page) })
  const visible = history.data?.content ?? []
  const totalPages = history.data?.totalPages ?? 1
  const current = page

  return <section className="surface-card p-5">
    <div className="flex items-center justify-between gap-3">
      <h2 className="font-semibold">Histórico</h2>
      {!!history.data?.totalElements && <span className="text-xs text-text-500">{history.data.totalElements} atendimento(s)</span>}
    </div>
    {history.isLoading && <p className="mt-3 text-sm text-text-600">Carregando histórico...</p>}
    {history.isError && <p role="alert" className="mt-3 text-sm text-danger-600">Não foi possível carregar o histórico.</p>}
    {!history.isLoading && !history.isError && history.data?.totalElements === 0 && <p className="mt-3 text-sm text-text-600">Nenhum atendimento ainda.</p>}
    <ul className="mt-2">
      {visible.map((appointment) => (
        <li key={appointment.id} className="flex flex-wrap items-start justify-between gap-2 border-t border-background-300 py-3 text-sm first:border-t-0">
          <div>
            <strong className="text-text-900">{appointment.serviceName}</strong>
            <p className="text-text-600">{formatDateTime(appointment.startsAt).replace(' ', ' às ')} · {appointment.professionalName}</p>
          </div>
          <span className={`status-pill ${APPOINTMENT_STATUS_STYLES[appointment.status]}`}>{APPOINTMENT_STATUS_LABELS[appointment.status]}</span>
        </li>
      ))}
    </ul>
    {totalPages > 1 && (
      <div className="mt-3 flex items-center justify-between gap-3 border-t border-background-300 pt-3 text-sm">
        <button type="button" onClick={() => setPage(current - 1)} disabled={current === 0} className="inline-flex items-center gap-1 font-semibold text-text-700 disabled:opacity-40"><ChevronLeft size={16} /> Anterior</button>
        <span className="text-text-600">Página {current + 1} de {totalPages}</span>
        <button type="button" onClick={() => setPage(current + 1)} disabled={current >= totalPages - 1} className="inline-flex items-center gap-1 font-semibold text-text-700 disabled:opacity-40">Próxima <ChevronRight size={16} /></button>
      </div>
    )}
  </section>
}
