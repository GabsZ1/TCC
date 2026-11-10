/** Reúne a visão administrativa dos agendamentos do estabelecimento. */
import { useSearchParams } from 'react-router-dom'
import { BarChart3, ClipboardCheck } from 'lucide-react'
import { AppointmentsReportPage } from '@/features/reports/AppointmentsReportPage'
import { CompletedAppointmentsPanel } from '@/features/reports/CompletedAppointmentsPanel'

type View = 'realizados' | 'status'

/**
 * Tela "Atendimentos" do administrador.
 * - Aba "Atendimentos realizados" (padrão): US 2.13, lista de atendimentos finalizados.
 * - Aba "Comparecimento": agenda por profissional com taxas de conclusão, cancelamento, falta e comparecimento.
 */
export function EstablishmentAppointmentsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const view: View = searchParams.get('visao') === 'status' ? 'status' : 'realizados'

  function selectView(next: View) {
    setSearchParams(next === 'realizados' ? {} : { visao: next }, { replace: true })
  }

  return (
    <div className="page-shell">
      <header className="border-b border-background-300 pb-7">
        <p className="eyebrow">Operação</p>
        <h1 className="page-title mt-3">Atendimentos</h1>
        <p className="page-subtitle">
          {view === 'realizados'
            ? 'Consulte os atendimentos finalizados pela equipe, filtrando por funcionário e período de conclusão.'
            : 'Veja se a agenda de cada profissional está virando atendimento: concluídos, cancelamentos, faltas e taxa de comparecimento.'}
        </p>
      </header>

      <div role="tablist" aria-label="Visões de atendimentos" className="mt-6 inline-flex rounded-xl border border-background-300 bg-background-100 p-1">
        <TabButton active={view === 'realizados'} onClick={() => selectView('realizados')} icon={<ClipboardCheck size={16} />}>Atendimentos realizados</TabButton>
        <TabButton active={view === 'status'} onClick={() => selectView('status')} icon={<BarChart3 size={16} />}>Comparecimento</TabButton>
      </div>

      <div role="tabpanel">
        {view === 'realizados' ? <CompletedAppointmentsPanel /> : <AppointmentsReportPage embedded />}
      </div>
    </div>
  )
}

/** Indica a seção ativa e permite trocar de visão administrativa. */
function TabButton({ active, onClick, icon, children }: { active: boolean; onClick: () => void; icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      onClick={onClick}
      className={`flex items-center gap-2 rounded-lg px-3.5 py-2 text-sm font-semibold transition ${
        active ? 'bg-white text-text-950 shadow-sm' : 'text-text-600 hover:text-text-900'
      }`}
    >
      {icon}
      {children}
    </button>
  )
}
