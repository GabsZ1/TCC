import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Building2, Power, Search } from 'lucide-react'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { getApiErrorMessage } from '@/lib/apiError'
import {
  deactivateSystemEstablishment,
  fetchSystemEstablishments,
  type SystemEstablishment,
} from '@/features/system-admin/systemEstablishmentsApi'

const QUERY_KEY = ['system-establishments']

export function SystemEstablishmentsPage() {
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [selected, setSelected] = useState<SystemEstablishment | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const establishments = useQuery({ queryKey: QUERY_KEY, queryFn: fetchSystemEstablishments })
  const deactivate = useMutation({
    mutationFn: deactivateSystemEstablishment,
    onSuccess: (_, establishmentId) => {
      queryClient.setQueryData<SystemEstablishment[]>(QUERY_KEY, (current) =>
        current?.map((item) => item.id === establishmentId ? { ...item, active: false } : item),
      )
      setMessage('Estabelecimento desativado. Os atendimentos existentes foram preservados.')
      setSelected(null)
    },
    onError: () => setSelected(null),
  })
  const filtered = useMemo(() => {
    const term = search.trim().toLocaleLowerCase('pt-BR')
    if (!term) return establishments.data ?? []
    return (establishments.data ?? []).filter((item) =>
      item.name.toLocaleLowerCase('pt-BR').includes(term) || item.cnpj.includes(term.replace(/\D/g, '')),
    )
  }, [establishments.data, search])

  return (
    <div className="page-shell">
      <header>
        <p className="eyebrow">Administração do sistema</p>
        <h1 className="page-title mt-2">Estabelecimentos</h1>
        <p className="page-subtitle">Consulte os estabelecimentos cadastrados e interrompa novas reservas quando necessário.</p>
      </header>

      {message && <p role="status" className="mt-6 rounded-xl bg-success-50 px-4 py-3 text-sm text-success-700">{message}</p>}
      {deactivate.isError && <p role="alert" className="mt-6 rounded-xl bg-danger-50 px-4 py-3 text-sm text-danger-600">{getApiErrorMessage(deactivate.error, 'Não foi possível desativar o estabelecimento.')}</p>}

      <section className="surface-card mt-7 overflow-hidden">
        <div className="flex flex-col gap-4 border-b border-background-300 p-5 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="font-semibold text-text-900">Todos os estabelecimentos</h2>
            <p className="mt-1 text-sm text-text-500">{establishments.data?.length ?? 0} cadastro(s) encontrado(s)</p>
          </div>
          <label className="relative block sm:w-80">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-text-400" size={17} />
            <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Buscar por nome ou CNPJ" className="w-full rounded-xl border border-background-300 py-2.5 pl-10 pr-3 text-sm outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100" />
          </label>
        </div>

        {establishments.isLoading && <State>Carregando estabelecimentos...</State>}
        {establishments.isError && <State error>{getApiErrorMessage(establishments.error, 'Não foi possível carregar os estabelecimentos.')}</State>}
        {!establishments.isLoading && !establishments.isError && filtered.length === 0 && <State>Nenhum estabelecimento encontrado.</State>}
        {filtered.length > 0 && (
          <div className="divide-y divide-background-300">
            {filtered.map((item) => (
              <article key={item.id} className="flex flex-col gap-4 p-5 sm:flex-row sm:items-center">
                <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-primary-100 text-primary-700"><Building2 size={20} /></span>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="font-semibold text-text-900">{item.name}</h3>
                    <span className={`status-pill ${item.active ? 'bg-success-50 text-success-700' : 'bg-danger-50 text-danger-600'}`}>{item.active ? 'Ativo' : 'Inativo'}</span>
                  </div>
                  <p className="mt-1 text-sm text-text-500">CNPJ {formatCnpj(item.cnpj)}{item.email ? ` · ${item.email}` : ''}</p>
                </div>
                <button type="button" disabled={!item.active || deactivate.isPending} onClick={() => { setMessage(null); setSelected(item) }} className="inline-flex items-center justify-center gap-2 rounded-xl border border-danger-500/30 px-4 py-2.5 text-sm font-semibold text-danger-600 hover:bg-danger-50 disabled:cursor-not-allowed disabled:opacity-45">
                  <Power size={16} /> {item.active ? 'Desativar' : 'Desativado'}
                </button>
              </article>
            ))}
          </div>
        )}
      </section>

      {selected && <ConfirmDialog title="Desativar estabelecimento" message={`Desativar “${selected.name}”? Novos agendamentos e remarcações serão bloqueados, mas os atendimentos já existentes continuarão disponíveis normalmente.`} confirmLabel="Desativar estabelecimento" isLoading={deactivate.isPending} onConfirm={() => deactivate.mutate(selected.id)} onCancel={() => setSelected(null)} />}
    </div>
  )
}

function State({ children, error = false }: { children: React.ReactNode; error?: boolean }) {
  return <div className={`p-10 text-center text-sm ${error ? 'text-danger-600' : 'text-text-500'}`}>{children}</div>
}

function formatCnpj(value: string) {
  return value.replace(/^(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})$/, '$1.$2.$3/$4-$5')
}
