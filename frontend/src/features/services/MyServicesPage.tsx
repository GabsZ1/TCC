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
    <div className="p-5 sm:p-7 lg:p-9">
      <div>
        <h1 className="page-title">Meus serviços</h1>
        <p className="page-subtitle">Serviços que você está habilitado a realizar.</p>
      </div>

      {servicesQuery.data?.allServices && (
        <p className="mt-4 rounded-xl border border-brand-100 bg-brand-50/60 px-4 py-3 text-sm text-brand-700">
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
          <article key={service.id} className="rounded-2xl border border-brand-100 bg-white p-5 shadow-sm">
            <h2 className="text-lg font-semibold text-brand-900">{service.name}</h2>
            <p className="mt-1 text-sm text-brand-600">{service.description || 'Sem descrição'}</p>
            <div className="mt-5 flex items-center justify-between border-t border-brand-100 pt-4">
              <p className="font-semibold text-brand-900">{formatMoney(service.price)}</p>
              <p className="flex items-center gap-1 text-sm text-brand-600">
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
      className={`sm:col-span-2 xl:col-span-3 rounded-2xl border bg-white p-8 text-center ${
        error ? 'border-danger-500/30 text-danger-600' : 'border-brand-100 text-brand-600'
      }`}
    >
      {children}
    </div>
  )
}

function formatMoney(value: number) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value)
}
