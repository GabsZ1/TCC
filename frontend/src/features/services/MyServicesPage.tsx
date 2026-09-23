import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Clock3 } from 'lucide-react'
import { fetchMyProfessionalServices } from '@/features/employees/services/employeesApi'
import { getApiErrorMessage } from '@/lib/apiError'

export function MyServicesPage() {
  const servicesQuery = useQuery({ queryKey: ['my-professional-services'], queryFn: fetchMyProfessionalServices })
  const services = useMemo(
    () => [...(servicesQuery.data?.services ?? [])].sort((a, b) => a.name.localeCompare(b.name, 'pt-BR')),
    [servicesQuery.data],
  )

  return (
    <div className="page-shell">
      <header className="border-b border-background-300 pb-7">
        <p className="eyebrow">Perfil profissional</p>
        <h1 className="page-title mt-3">Meus serviços</h1>
        <p className="page-subtitle">Serviços que você está habilitado a realizar.</p>
      </header>

      {servicesQuery.data?.allServices && (
        <p className="mt-4 rounded-xl border border-background-300 bg-background-200 px-4 py-3 text-sm text-text-700">
          Você está habilitado a realizar todos os serviços do estabelecimento, incluindo novos que forem cadastrados.
        </p>
      )}

      <div className="mt-7 grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {servicesQuery.isLoading && <StateCard>Carregando serviços...</StateCard>}
        {servicesQuery.isError && (
          <StateCard error>{getApiErrorMessage(servicesQuery.error, 'Não foi possível carregar seus serviços.')}</StateCard>
        )}
        {!servicesQuery.isLoading && !servicesQuery.isError && services.length === 0 && (
          <StateCard>Nenhum serviço liberado para você ainda. Fale com o administrador do estabelecimento.</StateCard>
        )}
        {services.map((service) => (
          <article key={service.id} className="surface-card p-5">
            <h2 className="text-lg font-semibold text-text-900">{service.name}</h2>
            <p className="mt-1 text-sm text-text-600">{service.description || 'Sem descrição'}</p>
            <div className="mt-5 flex items-center justify-between border-t border-background-300 pt-4">
              <p className="font-semibold text-text-900">{formatMoney(service.price)}</p>
              <p className="flex items-center gap-1 text-sm text-text-600">
                <Clock3 size={15} /> {service.durationMinutes} min · {service.commissionPercentage}% comissão
              </p>
            </div>
          </article>
        ))}
      </div>
    </div>
  )
}

function StateCard({ children, error = false }: { children: React.ReactNode; error?: boolean }) {
  return (
    <div
      className={`sm:col-span-2 xl:col-span-3 rounded-2xl border bg-background-50 p-8 text-center ${
        error ? 'border-danger-500/30 text-danger-600' : 'border-background-300 text-text-600'
      }`}
    >
      {children}
    </div>
  )
}

function formatMoney(value: number) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value)
}
