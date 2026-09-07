export function DashboardPage() {
  return (
    <div className="p-6">
      <h1 className="font-[var(--font-display)] text-2xl text-brand-900">Dashboard</h1>
      <p className="mt-1 text-sm text-brand-600">
        Indicadores administrativos do estabelecimento.
      </p>

      {/* RNF.01.01: indicadores em cards no topo, com ícones intuitivos */}
      <div className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {/* Substituir por dados reais vindos da API (ex: useQuery('dashboard-summary')) */}
        <DashboardCard label="Comissões do mês" value="R$ 0,00" />
        <DashboardCard label="Atendimentos do mês" value="0" />
        <DashboardCard label="Funcionários ativos" value="0" />
        <DashboardCard label="Ticket médio" value="R$ 0,00" />
      </div>
    </div>
  )
}

function DashboardCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-2xl border border-brand-100 bg-white p-5 shadow-sm">
      <p className="text-sm text-brand-600">{label}</p>
      <p className="mt-2 text-2xl font-semibold text-brand-900">{value}</p>
    </div>
  )
}
