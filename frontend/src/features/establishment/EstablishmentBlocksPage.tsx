import { useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api'
import { getApiErrorMessage } from '@/lib/apiError'
import { formatDateTime } from '@/lib/format'

interface Block { id: number; startsAt: string; endsAt: string; reason: string }
interface Input { startsAt: string; endsAt: string; reason: string }
const path = '/estabelecimentos/me/bloqueios'
const currentMonth = () => {
  const now = new Date()
  const year = now.getFullYear()
  const month = now.getMonth()
  const local = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}T00:00`
  return { start: local(new Date(year, month, 1)), end: local(new Date(year, month + 2, 1)) }
}

export function EstablishmentBlocksPage() {
  const queryClient = useQueryClient()
  const [period] = useState(currentMonth)
  const [start, setStart] = useState(period.start.slice(0, 10))
  const [end, setEnd] = useState(period.end.slice(0, 10))
  const [editing, setEditing] = useState<number | null>(null)
  const [input, setInput] = useState<Input>({ startsAt: '', endsAt: '', reason: '' })
  const blocks = useQuery({ queryKey: ['establishment-blocks', start, end], queryFn: () => api.get<Block[]>(path, { params: { startsAt: `${start}T00:00`, endsAt: `${end}T00:00` } }).then(response => response.data), enabled: Boolean(start && end && start < end) })
  const save = useMutation({ mutationFn: () => editing == null ? api.post(path, input) : api.put(`${path}/${editing}`, input), onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['establishment-blocks'] }); setEditing(null); setInput({ startsAt: '', endsAt: '', reason: '' }) } })
  const remove = useMutation({ mutationFn: (id: number) => api.delete(`${path}/${id}`), onSuccess: () => queryClient.invalidateQueries({ queryKey: ['establishment-blocks'] }) })
  const submit = (event: FormEvent) => { event.preventDefault(); if (input.startsAt < input.endsAt && input.reason.trim()) save.mutate() }

  return <div className="page-shell space-y-6">
    <header><p className="eyebrow">Operação</p><h1 className="page-title mt-3">Exceções do estabelecimento</h1><p className="page-subtitle">Bloqueie períodos de fechamento, manutenção ou eventos para toda a equipe.</p></header>
    <section className="surface-card p-5"><h2 className="text-lg font-semibold text-text-900">{editing == null ? 'Novo bloqueio' : 'Editar bloqueio'}</h2>
      <form className="mt-4 grid gap-3 sm:grid-cols-2" onSubmit={submit}>
        <label className="text-sm">Início<input className="filter-input mt-1 w-full" type="datetime-local" required value={input.startsAt} onChange={event => setInput({ ...input, startsAt: event.target.value })} /></label>
        <label className="text-sm">Fim<input className="filter-input mt-1 w-full" type="datetime-local" required value={input.endsAt} onChange={event => setInput({ ...input, endsAt: event.target.value })} /></label>
        <label className="text-sm sm:col-span-2">Motivo<input className="filter-input mt-1 w-full" required maxLength={200} value={input.reason} onChange={event => setInput({ ...input, reason: event.target.value })} /></label>
        {input.startsAt && input.endsAt && input.startsAt >= input.endsAt && <p role="alert" className="text-danger-600 sm:col-span-2">O fim deve ser posterior ao início.</p>}
        {save.isError && <p role="alert" className="text-danger-600 sm:col-span-2">{getApiErrorMessage(save.error, 'Não foi possível salvar o bloqueio.')}</p>}
        <div className="flex flex-wrap gap-3 sm:col-span-2"><button className="primary-button" disabled={save.isPending || !input.startsAt || input.startsAt >= input.endsAt}>Salvar</button>{editing != null && <button type="button" className="secondary-button" onClick={() => { setEditing(null); setInput({ startsAt: '', endsAt: '', reason: '' }) }}>Cancelar</button>}</div>
      </form>
    </section>
    <section className="surface-card p-5"><h2 className="text-lg font-semibold text-text-900">Bloqueios</h2><div className="mt-4 grid gap-3 sm:grid-cols-2"><label className="text-sm font-medium text-text-800">De<input className="filter-input mt-1" type="date" value={start} onChange={event => setStart(event.target.value)} /></label><label className="text-sm font-medium text-text-800">Até<input className="filter-input mt-1" type="date" value={end} onChange={event => setEnd(event.target.value)} /></label></div>
      {blocks.isLoading && <p className="mt-4 text-sm">Carregando...</p>}{blocks.isError && <p role="alert" className="mt-4 text-danger-600">{getApiErrorMessage(blocks.error, 'Não foi possível carregar os bloqueios.')}</p>}
      {remove.isError && <p role="alert" className="mt-4 text-danger-600">{getApiErrorMessage(remove.error, 'Não foi possível remover o bloqueio.')}</p>}
      {blocks.data?.length === 0 && <p className="mt-4 text-sm text-text-600">Nenhum bloqueio neste período.</p>}
      <ul className="mt-4 divide-y divide-background-300">{blocks.data?.map(block => <li key={block.id} className="flex flex-wrap items-center justify-between gap-3 py-4 text-sm"><div className="min-w-0"><strong className="text-text-900">{block.reason}</strong><p className="mt-1 text-text-600">{formatDateTime(block.startsAt)} até {formatDateTime(block.endsAt)}</p></div><div className="flex flex-wrap gap-2"><button type="button" className="secondary-button" onClick={() => { setEditing(block.id); setInput({ startsAt: block.startsAt.slice(0, 16), endsAt: block.endsAt.slice(0, 16), reason: block.reason }) }}>Editar</button><button type="button" className="secondary-button text-danger-600 hover:border-danger-500/30 hover:bg-danger-50 hover:text-danger-700" disabled={remove.isPending} onClick={() => { if (window.confirm('Remover este bloqueio?')) remove.mutate(block.id) }}>Remover</button></div></li>)}</ul>
    </section>
  </div>
}
