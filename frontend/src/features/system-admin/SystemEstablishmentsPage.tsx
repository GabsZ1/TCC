/** Permite ao administrador do sistema consultar e controlar estabelecimentos. */
import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Building2, CalendarOff, Eye, Pause, Power, RotateCcw, Search } from 'lucide-react'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { getApiErrorMessage } from '@/lib/apiError'
import { formatCnpj } from '@/lib/format'
import { SystemEstablishmentDetailsModal } from '@/features/system-admin/SystemEstablishmentDetailsModal'
import {
  allowSystemEstablishmentBookings,
  blockSystemEstablishmentBookings,
  deactivateSystemEstablishment,
  fetchSystemEstablishments,
  pauseSystemEstablishment,
  reactivateSystemEstablishment,
  resumeSystemEstablishment,
  type SystemEstablishment,
} from '@/features/system-admin/systemEstablishmentsApi'

const QUERY_KEY = ['system-establishments']
const ESTABLISHMENTS_PER_PAGE = 10
type Action = 'block' | 'allow' | 'pause' | 'resume' | 'deactivate' | 'reactivate'

/** Filtra os estabelecimentos e exige confirmação antes de alterar seu estado operacional. */
export function SystemEstablishmentsPage() {
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [selection, setSelection] = useState<{ establishment: SystemEstablishment; action: Action } | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [detailsOf, setDetailsOf] = useState<SystemEstablishment | null>(null)
  const establishments = useQuery({ queryKey: QUERY_KEY, queryFn: fetchSystemEstablishments })
  const stateMutation = useMutation({
    mutationFn: ({ establishment, action }: NonNullable<typeof selection>) => runAction(establishment.id, action),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({ queryKey: QUERY_KEY })
      setMessage(successMessage(variables.action))
      setSelection(null)
    },
    onError: () => setSelection(null),
  })
  // A busca ignora acentos no nome e pontuação no CNPJ; a paginação usa a lista filtrada.
  const filtered = useMemo(() => {
    const term = normalizeName(search)
    if (!term) return establishments.data ?? []
    const cnpjTerm = term.replace(/[^a-z0-9]/g, '').toUpperCase()
    return (establishments.data ?? []).filter((item) =>
      normalizeName(item.name).includes(term) || (cnpjTerm.length > 0 && item.cnpj.includes(cnpjTerm)),
    )
  }, [establishments.data, search])
  const totalPages = Math.max(1, Math.ceil(filtered.length / ESTABLISHMENTS_PER_PAGE))
  const currentPage = Math.min(page, totalPages - 1)
  const visible = filtered.slice(currentPage * ESTABLISHMENTS_PER_PAGE, (currentPage + 1) * ESTABLISHMENTS_PER_PAGE)

  function select(establishment: SystemEstablishment, action: Action) {
    setMessage(null)
    setSelection({ establishment, action })
  }

  return (
    <div className="page-shell">
      <header className="border-b border-background-300 pb-7"><p className="eyebrow">Administração do sistema</p><h1 className="page-title mt-2">Estabelecimentos</h1><p className="page-subtitle">Controle reservas, operação e acesso à plataforma sem apagar o histórico.</p></header>
      {message && <p role="status" className="mt-6 rounded-xl bg-success-50 px-4 py-3 text-sm text-success-700">{message}</p>}
      {stateMutation.isError && <p role="alert" className="mt-6 rounded-xl bg-danger-50 px-4 py-3 text-sm text-danger-600">{getApiErrorMessage(stateMutation.error, 'Não foi possível alterar o estado do estabelecimento.')}</p>}
      <section className="surface-card mt-7 overflow-hidden">
        <div className="flex flex-col gap-4 border-b border-background-300 p-5 sm:flex-row sm:items-center sm:justify-between">
          <div><h2 className="font-semibold text-text-900">Todos os estabelecimentos</h2><p className="mt-1 text-sm text-text-500">{filtered.length} de {establishments.data?.length ?? 0} cadastro(s)</p></div>
          <label className="relative block sm:w-80"><span className="sr-only">Buscar estabelecimento por nome ou CNPJ</span><Search className="absolute left-3 top-1/2 -translate-y-1/2 text-text-400" size={17} /><input type="search" maxLength={150} value={search} onChange={(event) => { setSearch(event.target.value); setPage(0) }} placeholder="Buscar por nome ou CNPJ" className="w-full rounded-xl border border-background-300 py-2.5 pl-10 pr-3 text-sm outline-none focus:border-primary-500 focus:ring-2 focus:ring-primary-100" /></label>
        </div>
        {establishments.isLoading && <State>Carregando estabelecimentos...</State>}
        {establishments.isError && <State error>{getApiErrorMessage(establishments.error, 'Não foi possível carregar os estabelecimentos.')}</State>}
        {!establishments.isLoading && !establishments.isError && filtered.length === 0 && <State>Nenhum estabelecimento encontrado.</State>}
        {visible.length > 0 && <div className="divide-y divide-background-300">{visible.map((item) => (
          <article key={item.id} className="p-5">
            <div className="flex items-start gap-3"><span className="grid size-11 shrink-0 place-items-center rounded-xl bg-primary-100 text-primary-700"><Building2 size={20} /></span><div className="min-w-0 flex-1"><h3 className="break-words font-semibold text-text-900">{item.name}</h3><p className="mt-1 break-all text-sm text-text-500">CNPJ {formatCnpj(item.cnpj)}{item.email ? ` · ${item.email}` : ''}</p><div className="mt-3 flex flex-wrap gap-2"><Status active={item.active} label={item.active ? 'Estabelecimento ativo' : 'Estabelecimento desativado'} />{item.paused && <Status label="Operação pausada" />}{item.bookingsBlocked && <Status label="Agendamentos bloqueados" />}</div></div></div>
            <div className="mt-4 grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
              <ActionButton icon={<Eye size={16} />} disabled={false} onClick={() => setDetailsOf(item)}>Ver detalhes</ActionButton>
              <ActionButton icon={<CalendarOff size={16} />} disabled={!item.active || stateMutation.isPending} onClick={() => select(item, item.bookingsBlocked ? 'allow' : 'block')}>{item.bookingsBlocked ? 'Liberar agendamentos' : 'Bloquear agendamentos'}</ActionButton>
              <ActionButton icon={item.paused ? <RotateCcw size={16} /> : <Pause size={16} />} disabled={!item.active || stateMutation.isPending} onClick={() => select(item, item.paused ? 'resume' : 'pause')}>{item.paused ? 'Retomar operação' : 'Pausar operação'}</ActionButton>
              <ActionButton danger={item.active} icon={<Power size={16} />} disabled={stateMutation.isPending} onClick={() => select(item, item.active ? 'deactivate' : 'reactivate')}>{item.active ? 'Desativar estabelecimento' : 'Reativar estabelecimento'}</ActionButton>
            </div>
          </article>
        ))}</div>}
        {totalPages > 1 && <nav aria-label="Páginas de estabelecimentos" className="flex flex-wrap items-center justify-between gap-3 border-t border-background-300 p-5 text-sm text-text-600"><button type="button" className="secondary-button disabled:opacity-50" disabled={currentPage === 0} onClick={() => setPage(currentPage - 1)}>Anterior</button><span>Página {currentPage + 1} de {totalPages}</span><button type="button" className="secondary-button disabled:opacity-50" disabled={currentPage >= totalPages - 1} onClick={() => setPage(currentPage + 1)}>Próxima</button></nav>}
      </section>
      {detailsOf && <SystemEstablishmentDetailsModal establishmentId={detailsOf.id} establishmentName={detailsOf.name} onClose={() => setDetailsOf(null)} />}
      {selection && <ConfirmDialog {...confirmationProps(selection.establishment, selection.action)} isLoading={stateMutation.isPending} onConfirm={() => stateMutation.mutate(selection)} onCancel={() => setSelection(null)} />}
    </div>
  )
}

/** Traduz a ação escolhida para a chamada administrativa correspondente. */
function runAction(id: number, action: Action) {
  if (action === 'block') return blockSystemEstablishmentBookings(id)
  if (action === 'allow') return allowSystemEstablishmentBookings(id)
  if (action === 'pause') return pauseSystemEstablishment(id)
  if (action === 'resume') return resumeSystemEstablishment(id)
  if (action === 'reactivate') return reactivateSystemEstablishment(id)
  return deactivateSystemEstablishment(id)
}

function confirmationProps(item: SystemEstablishment, action: Action) {
  const copy: Record<Action, { title: string; message: string; confirmLabel: string }> = {
    block: { title: 'Bloquear agendamentos', message: `Bloquear novas reservas e remarcações em “${item.name}”? Os atendimentos existentes serão preservados.`, confirmLabel: 'Bloquear' },
    allow: { title: 'Liberar agendamentos', message: `Permitir novamente novas reservas em “${item.name}”?`, confirmLabel: 'Liberar' },
    pause: { title: 'Pausar operação', message: `Pausar “${item.name}”? A pausa só será possível se não houver atendimentos confirmados e bloqueará o acesso dos funcionários.`, confirmLabel: 'Pausar' },
    resume: { title: 'Retomar operação', message: `Liberar novamente o acesso dos funcionários de “${item.name}”?`, confirmLabel: 'Retomar' },
    deactivate: { title: 'Desativar estabelecimento', message: `Desativar “${item.name}”? Todos os usuários vinculados perderão o acesso e os atendimentos confirmados serão cancelados definitivamente com o motivo “Estabelecimento desativado”.`, confirmLabel: 'Desativar estabelecimento' },
    reactivate: { title: 'Reativar estabelecimento', message: `Reativar o acesso de “${item.name}”? Os atendimentos cancelados na desativação continuarão cancelados.`, confirmLabel: 'Reativar estabelecimento' },
  }
  return copy[action]
}

function successMessage(action: Action) {
  if (action === 'deactivate') return 'Estabelecimento desativado e atendimentos pendentes cancelados.'
  if (action === 'reactivate') return 'Estabelecimento reativado. Os cancelamentos anteriores foram mantidos.'
  if (action === 'pause') return 'Operação pausada pelo sistema.'
  if (action === 'resume') return 'Operação retomada.'
  return action === 'block' ? 'Novos agendamentos bloqueados pelo sistema.' : 'Novos agendamentos liberados.'
}

function ActionButton({ children, icon, danger = false, disabled, onClick }: { children: React.ReactNode; icon: React.ReactNode; danger?: boolean; disabled: boolean; onClick: () => void }) { return <button type="button" disabled={disabled} onClick={onClick} className={`inline-flex items-center justify-center gap-2 rounded-xl border px-3 py-2.5 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-40 ${danger ? 'border-danger-500/30 text-danger-600 hover:bg-danger-50' : 'border-background-300 text-text-700 hover:bg-background-100'}`}>{icon}{children}</button> }
function Status({ active = false, label }: { active?: boolean; label: string }) { return <span className={`status-pill ${active ? 'bg-success-50 text-success-700' : 'bg-warning-100 text-warning-700'}`}>{label}</span> }
function State({ children, error = false }: { children: React.ReactNode; error?: boolean }) { return <div className={`p-10 text-center text-sm ${error ? 'text-danger-600' : 'text-text-500'}`}>{children}</div> }
function normalizeName(value: string) { return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().toLocaleLowerCase('pt-BR') }
