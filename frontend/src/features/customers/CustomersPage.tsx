import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useAuth } from '@/hooks/useAuth'
import { getApiErrorMessage } from '@/lib/apiError'
import { createCustomer, createCustomerAppointment, fetchBookingOptions, fetchCustomerAppointments, fetchCustomers, updateCustomer, type Customer } from './customersApi'

export function CustomersPage() {
  const queryClient = useQueryClient()
  const { user } = useAuth()
  // O profissional só agenda na própria agenda (o backend também garante isso).
  const isProfessional = user?.role === 'PROFESSIONAL'
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<Customer | null>(null)
  const [editing, setEditing] = useState<Customer | 'new' | null>(null)
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [serviceId, setServiceId] = useState('')
  const [professionalId, setProfessionalId] = useState('')
  const [startsAt, setStartsAt] = useState('')
  const customers = useQuery({ queryKey: ['customers', search], queryFn: () => fetchCustomers(search) })
  const history = useQuery({ queryKey: ['customer-appointments', selected?.id], queryFn: () => fetchCustomerAppointments(selected!.id), enabled: !!selected })
  const options = useQuery({ queryKey: ['customer-booking-options'], queryFn: fetchBookingOptions })
  const save = useMutation({
    mutationFn: () => editing === 'new' ? createCustomer({ name, phone, email }) : updateCustomer((editing as Customer).id, { name, phone, email, active: (editing as Customer).active }),
    onSuccess: (customer) => { queryClient.invalidateQueries({ queryKey: ['customers'] }); setSelected(customer); setEditing(null) },
  })
  const booking = useMutation({
    mutationFn: () => createCustomerAppointment(selected!.id, Number(serviceId), isProfessional ? Number(user!.id) : Number(professionalId), startsAt),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['customer-appointments', selected?.id] }); queryClient.invalidateQueries({ queryKey: ['establishment-agenda'] }); setStartsAt('') },
  })
  const status = useMutation({
    mutationFn: () => updateCustomer(selected!.id, { name: selected!.name, phone: selected!.phone, email: selected!.email ?? '', active: !selected!.active }),
    onSuccess: (customer) => { setSelected(customer); queryClient.invalidateQueries({ queryKey: ['customers'] }) },
  })

  function openForm(customer: Customer | 'new') {
    setEditing(customer)
    setName(customer === 'new' ? '' : customer.name)
    setPhone(customer === 'new' ? '' : customer.phone)
    setEmail(customer === 'new' ? '' : customer.email ?? '')
  }
  function submitCustomer(event: FormEvent) { event.preventDefault(); save.mutate() }
  function submitBooking(event: FormEvent) { event.preventDefault(); booking.mutate() }

  return <div className="page-shell">
    <header className="border-b border-background-300 pb-7">
      <p className="eyebrow">Gestão</p><h1 className="page-title mt-3">Clientes do estabelecimento</h1>
      <p className="page-subtitle">Cadastre clientes sem conta, consulte o histórico e marque atendimentos recebidos no balcão, por telefone ou WhatsApp.</p>
    </header>
    <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(260px,1fr)_minmax(360px,2fr)]">
      <section className="surface-card p-5">
        <div className="flex items-center justify-between gap-3"><h2 className="font-semibold">Clientes</h2><button className="btn-primary" onClick={() => openForm('new')}>Novo cliente</button></div>
        <input className="filter-input mt-4 w-full" aria-label="Buscar cliente" placeholder="Buscar por nome ou telefone" value={search} onChange={(event) => setSearch(event.target.value)} />
        {customers.isError && <p role="alert" className="mt-3 text-danger-600">{getApiErrorMessage(customers.error, 'Não foi possível carregar os clientes.')}</p>}
        <div className="mt-4 space-y-2">{customers.data?.map((customer) => <button key={customer.id} type="button" onClick={() => setSelected(customer)} className="w-full rounded-xl border border-background-300 p-3 text-left hover:bg-background-50">
          <strong className="block">{customer.name}</strong><span className="text-sm text-text-600">{customer.phone}{customer.active ? '' : ' · Inativo'}</span>
        </button>)}</div>
      </section>
      <section className="space-y-6">
        {editing && <form onSubmit={submitCustomer} className="surface-card space-y-3 p-5">
          <h2 className="font-semibold">{editing === 'new' ? 'Novo cliente' : 'Editar cliente'}</h2>
          <label className="block text-sm">Nome<input className="filter-input mt-1 w-full" required maxLength={150} value={name} onChange={(event) => setName(event.target.value)} /></label>
          <label className="block text-sm">Telefone<input className="filter-input mt-1 w-full" required minLength={10} maxLength={20} value={phone} onChange={(event) => setPhone(event.target.value)} /></label>
          <label className="block text-sm">Email (opcional)<input className="filter-input mt-1 w-full" type="email" value={email} onChange={(event) => setEmail(event.target.value)} /></label>
          {save.isError && <p role="alert" className="text-danger-600">{getApiErrorMessage(save.error, 'Não foi possível salvar o cliente.')}</p>}
          <div className="flex gap-3"><button className="btn-primary" disabled={save.isPending}>Salvar</button><button type="button" onClick={() => setEditing(null)}>Cancelar</button></div>
        </form>}
        {selected ? <>
          <section className="surface-card p-5"><div className="flex flex-wrap items-center justify-between gap-3"><div><h2 className="text-lg font-semibold">{selected.name}</h2><p className="text-sm text-text-600">{selected.phone}{selected.email ? ` · ${selected.email}` : ''}</p></div><div className="flex gap-3"><button onClick={() => openForm(selected)}>Editar</button><button onClick={() => status.mutate()} disabled={status.isPending}>{selected.active ? 'Desativar' : 'Reativar'}</button></div></div></section>
          {selected.active && <form onSubmit={submitBooking} className="surface-card space-y-3 p-5"><h2 className="font-semibold">Agendar para {selected.name}</h2>
            <label className="block text-sm">Serviço<select required className="filter-input mt-1 w-full" value={serviceId} onChange={(event) => setServiceId(event.target.value)}><option value="">Selecione</option>{options.data?.services.map((service) => <option key={service.id} value={service.id}>{service.name}</option>)}</select></label>
            {isProfessional
              ? <p className="text-sm">Profissional<span className="filter-input mt-1 block w-full bg-background-100 text-text-700">{user?.name} (sua agenda)</span></p>
              : <label className="block text-sm">Profissional<select required className="filter-input mt-1 w-full" value={professionalId} onChange={(event) => setProfessionalId(event.target.value)}><option value="">Selecione</option>{options.data?.professionals.map((employee) => <option key={employee.id} value={employee.id}>{employee.name}</option>)}</select></label>}
            <label className="block text-sm">Data e horário<input required type="datetime-local" className="filter-input mt-1 w-full" value={startsAt} onChange={(event) => setStartsAt(event.target.value)} /></label>
            {booking.isError && <p role="alert" className="text-danger-600">{getApiErrorMessage(booking.error, 'Não foi possível agendar.')}</p>}
            {booking.isSuccess && <p role="status" className="text-success-700">Agendamento criado.</p>}
            <button className="btn-primary" disabled={booking.isPending}>Agendar</button>
          </form>}
          <section className="surface-card p-5"><h2 className="font-semibold">Histórico</h2>{history.data?.length === 0 && <p className="mt-3 text-sm text-text-600">Nenhum atendimento ainda.</p>}{history.data?.map((appointment) => <div key={appointment.id} className="mt-3 border-t border-background-300 pt-3 text-sm"><strong>{appointment.serviceName}</strong><p>{new Date(appointment.startsAt).toLocaleString('pt-BR')} · {appointment.professionalName} · {appointment.status}</p></div>)}</section>
        </> : <div className="surface-card p-5 text-text-600">Selecione um cliente para ver o histórico e agendar.</div>}
      </section>
    </div>
  </div>
}
