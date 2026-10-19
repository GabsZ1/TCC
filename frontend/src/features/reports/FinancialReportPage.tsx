import { useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { BadgeDollarSign, CalendarCheck, CalendarRange, Receipt, TrendingUp, Wallet } from 'lucide-react'
import { ExportReportButtons } from '@/components/ui/ExportReportButtons'
import { fetchEmployees } from '@/features/employees/services/employeesApi'
import { fetchAllAdministrativeServices } from '@/features/services/servicesApi'
import { fetchFinancialReport, type FinancialReportFilters, type FinancialReportLine } from '@/features/reports/financialReportApi'
import { getApiErrorMessage } from '@/lib/apiError'
import { formatMoney } from '@/lib/format'

/**
 * Relatório financeiro/produção: quanto os atendimentos concluídos geraram no período, quanto foi de
 * comissão e o que sobra depois dela, com detalhamento por serviço e por profissional.
 */
export function FinancialReportPage() {
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [professionalId, setProfessionalId] = useState('')
  const [serviceId, setServiceId] = useState('')
  const [filters, setFilters] = useState<FinancialReportFilters>({})
  const [filterError, setFilterError] = useState<string | null>(null)

  const employees = useQuery({ queryKey: ['employees'], queryFn: fetchEmployees })
  const services = useQuery({ queryKey: ['administrative-services'], queryFn: fetchAllAdministrativeServices })
  const report = useQuery({
    queryKey: ['financial-report', filters.startDate, filters.endDate, filters.professionalId, filters.serviceId],
    queryFn: () => fetchFinancialReport(filters),
  })

  function applyFilters(event: FormEvent) {
    event.preventDefault()
    if (Boolean(startDate) !== Boolean(endDate)) {
      setFilterError('Informe a data inicial e a data final para filtrar por período.')
      return
    }
    if (startDate && endDate && startDate > endDate) {
      setFilterError('A data inicial não pode ser posterior à data final.')
      return
    }
    setFilterError(null)
    setFilters({
      startDate: startDate || undefined,
      endDate: endDate || undefined,
      professionalId: professionalId ? Number(professionalId) : undefined,
      serviceId: serviceId ? Number(serviceId) : undefined,
    })
  }

  function clearFilters() {
    setStartDate('')
    setEndDate('')
    setProfessionalId('')
    setServiceId('')
    setFilterError(null)
    setFilters({})
  }

  const data = report.data
  return (
    <div className="page-shell">
      <header className="border-b border-background-300 pb-7">
        <p className="eyebrow">Financeiro</p>
        <h1 className="page-title mt-3">Relatório financeiro</h1>
        <p className="page-subtitle">Faturamento dos atendimentos concluídos, comissões e valor após comissões, por serviço e por profissional.</p>
      </header>

      <form onSubmit={applyFilters} className="surface-card mt-6 grid gap-4 p-5 sm:grid-cols-2 lg:grid-cols-4">
        <Filter label="Data inicial"><input type="date" value={startDate} max={endDate || undefined} onChange={(event) => setStartDate(event.target.value)} className="filter-input" /></Filter>
        <Filter label="Data final"><input type="date" value={endDate} min={startDate || undefined} onChange={(event) => setEndDate(event.target.value)} className="filter-input" /></Filter>
        <Filter label="Profissional">
          <select value={professionalId} onChange={(event) => setProfessionalId(event.target.value)} className="filter-input">
            <option value="">Todos</option>
            {employees.data?.map((employee) => <option key={employee.id} value={employee.id}>{employee.name}{employee.active ? '' : ' (desativado)'}</option>)}
          </select>
        </Filter>
        <Filter label="Serviço">
          <select value={serviceId} onChange={(event) => setServiceId(event.target.value)} className="filter-input">
            <option value="">Todos</option>
            {services.data?.map((service) => <option key={service.id} value={service.id}>{service.name}{service.active ? '' : ' (desativado)'}</option>)}
          </select>
        </Filter>
        {filterError && <p role="alert" className="text-sm text-danger-600 sm:col-span-2 lg:col-span-4">{filterError}</p>}
        <div className="flex flex-wrap items-center gap-3 sm:col-span-2 lg:col-span-4">
          <button type="submit" className="primary-button">Aplicar filtros</button>
          <button type="button" onClick={clearFilters} className="secondary-button">Limpar filtros</button>
          <ExportReportButtons path="/estabelecimentos/me/relatorio-financeiro" params={filters} filename="relatorio-financeiro" />
        </div>
      </form>

      {report.isLoading && <StateCard>Carregando relatório...</StateCard>}
      {report.isError && <StateCard error>{getApiErrorMessage(report.error, 'Não foi possível carregar o relatório financeiro.')}</StateCard>}

      {data && !report.isError && (
        <>
          <p className="mt-6 flex items-center gap-2 text-sm text-text-600"><CalendarRange size={16} /> Período: {formatDate(data.periodStart)} a {formatDate(data.periodEnd)} (pela data de conclusão)</p>
          <section className="surface-card mt-3 grid overflow-hidden sm:grid-cols-2 lg:grid-cols-5">
            <SummaryCard icon={TrendingUp} label="Faturamento bruto" value={formatMoney(data.grossRevenue)} />
            <SummaryCard icon={BadgeDollarSign} label="Comissões" value={formatMoney(data.totalCommission)} bordered />
            <SummaryCard icon={Wallet} label="Valor após comissões" value={formatMoney(data.revenueAfterCommission)} bordered highlight />
            <SummaryCard icon={CalendarCheck} label="Atendimentos concluídos" value={String(data.completedCount)} bordered />
            <SummaryCard icon={Receipt} label="Ticket médio" value={formatMoney(data.averageTicket)} bordered />
          </section>
          <p className="mt-2 text-xs text-text-500">"Valor após comissões" não é lucro: despesas como aluguel, produtos e impostos não entram no sistema.</p>

          {data.completedCount === 0
            ? <StateCard>Nenhum atendimento concluído encontrado para os filtros selecionados.</StateCard>
            : (
              <div className="mt-6 grid gap-6 xl:grid-cols-2">
                <BreakdownTable title="Por serviço" nameLabel="Serviço" lines={data.byService} total={data.grossRevenue} />
                <BreakdownTable title="Por profissional" nameLabel="Profissional" lines={data.byProfessional} total={data.grossRevenue} />
              </div>
            )}
        </>
      )}
    </div>
  )
}

function BreakdownTable({ title, nameLabel, lines, total }: { title: string; nameLabel: string; lines: FinancialReportLine[]; total: number }) {
  return (
    <section className="surface-card overflow-hidden">
      <h2 className="border-b border-background-300 px-5 py-4 font-semibold text-text-900">{title}</h2>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[32rem] text-sm">
          <thead className="bg-background-100 text-left text-xs uppercase tracking-wide text-text-600">
            <tr><th className="px-5 py-3">{nameLabel}</th><th className="px-3 py-3 text-right">Qtde</th><th className="px-3 py-3 text-right">Faturamento</th><th className="px-3 py-3 text-right">Comissões</th><th className="px-5 py-3 text-right">Ticket médio</th></tr>
          </thead>
          <tbody className="divide-y divide-background-300">
            {lines.map((line) => (
              <tr key={line.id}>
                <td className="px-5 py-3">
                  <span className="font-medium text-text-900">{line.name}</span>
                  <span className="mt-1 block h-1.5 rounded-full bg-background-200" aria-hidden="true"><span className="block h-1.5 rounded-full bg-primary-500" style={{ width: `${total > 0 ? Math.max(2, (line.revenue / total) * 100) : 0}%` }} /></span>
                </td>
                <td className="px-3 py-3 text-right text-text-700">{line.completedCount}</td>
                <td className="px-3 py-3 text-right font-semibold text-text-900">{formatMoney(line.revenue)}</td>
                <td className="px-3 py-3 text-right text-text-700">{formatMoney(line.commission)}</td>
                <td className="px-5 py-3 text-right text-text-700">{formatMoney(line.averageTicket)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  )
}

function Filter({ label, children }: { label: string; children: React.ReactNode }) { return <label className="text-sm font-medium text-text-800">{label}{children}</label> }
function StateCard({ children, error = false }: { children: React.ReactNode; error?: boolean }) { return <div className={`mt-6 rounded-2xl border bg-background-50 p-8 text-center ${error ? 'border-danger-500/30 text-danger-600' : 'border-background-300 text-text-600'}`}>{children}</div> }
function SummaryCard({ icon: Icon, label, value, bordered = false, highlight = false }: { icon: typeof Wallet; label: string; value: string; bordered?: boolean; highlight?: boolean }) {
  return <div className={`p-5 ${bordered ? 'border-t border-background-300 sm:border-l sm:border-t-0' : ''} ${highlight ? 'bg-primary-50' : ''}`}><Icon size={18} className="text-primary-700" /><p className="mt-4 text-[0.65rem] font-extrabold uppercase tracking-[0.12em] text-text-500">{label}</p><p className="mt-1 text-xl font-extrabold tracking-[-0.03em] text-text-950">{value}</p></div>
}
function formatDate(value: string) { return new Intl.DateTimeFormat('pt-BR', { timeZone: 'UTC' }).format(new Date(`${value}T00:00:00Z`)) }
