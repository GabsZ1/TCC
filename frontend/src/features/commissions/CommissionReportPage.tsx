import { useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { BadgeDollarSign, CalendarRange, Scissors, UsersRound } from 'lucide-react'
import { fetchEmployees } from '@/features/employees/services/employeesApi'
import { fetchCommissionReport, type CommissionReportFilters } from '@/features/commissions/commissionReportApi'
import { getApiErrorMessage } from '@/lib/apiError'

const REPORT_KEY = 'commission-report'

export function CommissionReportPage() {
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')
  const [professionalId, setProfessionalId] = useState('')
  const [filters, setFilters] = useState<CommissionReportFilters>({})
  const [filterError, setFilterError] = useState<string | null>(null)

  const employees = useQuery({ queryKey: ['employees'], queryFn: fetchEmployees })
  const report = useQuery({
    queryKey: [REPORT_KEY, filters.startDate, filters.endDate, filters.professionalId],
    queryFn: () => fetchCommissionReport(filters),
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
    })
  }

  function clearFilters() {
    setStartDate('')
    setEndDate('')
    setProfessionalId('')
    setFilterError(null)
    setFilters({})
  }

  return (
    <div className="p-5 sm:p-7 lg:p-9">
      <div>
        <h1 className="page-title">Relatório de comissões</h1>
        <p className="page-subtitle">Acompanhe as comissões dos atendimentos concluídos por profissional e período.</p>
      </div>

      <form onSubmit={applyFilters} className="mt-6 grid gap-4 rounded-2xl border border-background-300 bg-background-50 p-5 shadow-sm sm:grid-cols-2 lg:grid-cols-3">
        <Filter label="Data inicial">
          <input type="date" value={startDate} max={endDate || undefined} onChange={(event) => setStartDate(event.target.value)} className="filter-input" />
        </Filter>
        <Filter label="Data final">
          <input type="date" value={endDate} min={startDate || undefined} onChange={(event) => setEndDate(event.target.value)} className="filter-input" />
        </Filter>
        <Filter label="Profissional">
          <select value={professionalId} onChange={(event) => setProfessionalId(event.target.value)} className="filter-input">
            <option value="">Todos</option>
            {employees.data?.map((employee) => <option key={employee.id} value={employee.id}>{employee.name}{employee.active ? '' : ' (inativo)'}</option>)}
          </select>
        </Filter>
        {employees.isError && <p role="alert" className="text-sm text-danger-600 sm:col-span-2 lg:col-span-3">{getApiErrorMessage(employees.error, 'Não foi possível carregar os profissionais.')}</p>}
        {filterError && <p role="alert" className="text-sm text-danger-600 sm:col-span-2 lg:col-span-3">{filterError}</p>}
        <div className="flex flex-wrap gap-3 sm:col-span-2 lg:col-span-3">
          <button type="submit" className="primary-button">Aplicar filtros</button>
          <button type="button" onClick={clearFilters} className="rounded-xl border border-background-300 px-4 py-2.5 text-sm font-semibold text-text-700 hover:bg-primary-50">Limpar filtros</button>
        </div>
      </form>

      {report.isLoading && <StateCard>Carregando relatório...</StateCard>}
      {report.isError && <StateCard error>{getApiErrorMessage(report.error, 'Não foi possível carregar o relatório de comissões.')}</StateCard>}

      {report.data && !report.isError && (
        <>
          <section className="mt-6 grid gap-4 sm:grid-cols-2">
            <SummaryCard icon={CalendarRange} label="Período consultado" value={`${formatDate(report.data.periodStart)} a ${formatDate(report.data.periodEnd)}`} />
            <SummaryCard icon={BadgeDollarSign} label="Comissão total" value={formatMoney(report.data.totalCommission)} />
          </section>

          {report.data.entries.length === 0
            ? <StateCard>Nenhum atendimento concluído encontrado para os filtros selecionados.</StateCard>
            : (
              <section className="mt-6 space-y-4" aria-label="Comissões por profissional">
                {report.data.entries.map((entry) => (
                  <article key={entry.professionalId} className="rounded-2xl border border-background-300 bg-background-50 p-5 shadow-sm">
                    <div className="flex flex-col gap-5 lg:flex-row lg:items-center lg:justify-between">
                      <div className="min-w-0">
                        <div className="flex items-center gap-2"><UsersRound size={18} className="text-text-600" /><h2 className="truncate text-lg font-semibold text-text-900">{entry.professionalName}</h2></div>
                        <p className="mt-2 flex items-center gap-2 text-sm text-text-600"><Scissors size={16} /> {formatCount(entry.completedServicesCount)}</p>
                      </div>
                      <dl className="grid gap-4 sm:grid-cols-3 lg:min-w-[32rem]">
                        <Metric label="Valor dos serviços" value={formatMoney(entry.totalServiceValue)} />
                        <Metric label="Percentual médio" value={formatPercentage(entry.averageCommissionPercentage)} />
                        <Metric label="Comissão" value={formatMoney(entry.totalCommission)} highlight />
                      </dl>
                    </div>
                  </article>
                ))}
              </section>
            )}
        </>
      )}
    </div>
  )
}

function Filter({ label, children }: { label: string; children: React.ReactNode }) { return <label className="text-sm font-medium text-text-800">{label}{children}</label> }
function StateCard({ children, error = false }: { children: React.ReactNode; error?: boolean }) { return <div className={`mt-6 rounded-2xl border bg-background-50 p-8 text-center ${error ? 'border-danger-500/30 text-danger-600' : 'border-background-300 text-text-600'}`}>{children}</div> }
function SummaryCard({ icon: Icon, label, value }: { icon: typeof CalendarRange; label: string; value: string }) { return <div className="rounded-2xl border border-background-300 bg-background-50 p-5 shadow-sm"><Icon size={21} className="text-text-600" /><p className="mt-3 text-sm text-text-600">{label}</p><p className="mt-1 text-xl font-bold text-text-900">{value}</p></div> }
function Metric({ label, value, highlight = false }: { label: string; value: string; highlight?: boolean }) { return <div><dt className="text-xs font-medium uppercase tracking-wide text-text-600">{label}</dt><dd className={`mt-1 font-semibold ${highlight ? 'text-lg text-text-900' : 'text-text-700'}`}>{value}</dd></div> }
function formatMoney(value: number) { return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value) }
function formatPercentage(value: number) { return new Intl.NumberFormat('pt-BR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value) + '%' }
function formatDate(value: string) { return new Intl.DateTimeFormat('pt-BR', { timeZone: 'UTC' }).format(new Date(`${value}T00:00:00Z`)) }
function formatCount(value: number) { return `${value} ${value === 1 ? 'serviço concluído' : 'serviços concluídos'}` }
