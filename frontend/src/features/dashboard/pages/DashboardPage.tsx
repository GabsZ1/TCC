import { useQuery } from '@tanstack/react-query'
import { Link, Navigate } from 'react-router-dom'
import { CalendarCheck, CalendarPlus, Scissors, UsersRound, Wallet } from 'lucide-react'
import { useAuth } from '@/hooks/useAuth'
import { fetchDashboardSummary } from '@/features/dashboard/dashboardApi'
import { getApiErrorMessage } from '@/lib/apiError'

export function DashboardPage() {
  const { user } = useAuth()
  const dashboard = useQuery({
    queryKey: ['dashboard-summary'],
    queryFn: fetchDashboardSummary,
    enabled: user?.role === 'ADMIN',
  })

  if (user?.role === 'CLIENT') return <Navigate to="/agendamentos" replace />
  if (user?.role === 'PROFESSIONAL') return <Navigate to="/minha-agenda" replace />

  return (
    <div className="p-5 sm:p-7 lg:p-9">
      <div className="max-w-4xl">
        <p className="text-sm font-semibold text-text-600">Olá, {user?.name}</p>
        <h1 className="mt-2 font-[var(--font-display)] text-3xl text-text-900 sm:text-4xl">{user?.role === 'ADMIN' ? user.establishmentName : 'Seu acesso profissional'}</h1>
        <p className="mt-3 max-w-2xl text-base leading-7 text-text-600">{user?.role === 'ADMIN' ? 'Acompanhe o dia a dia do estabelecimento e prepare o catálogo e a equipe para receber agendamentos.' : 'Seu acesso está ativo. A gestão do estabelecimento é feita pelo administrador.'}</p>

        {user?.role === 'ADMIN' && (
          <>
            {dashboard.isError && (
              <p className="mt-6 rounded-xl border border-danger-500/30 bg-danger-500/5 px-4 py-3 text-sm text-danger-600">
                {getApiErrorMessage(dashboard.error, 'Não foi possível carregar os indicadores do dashboard.')}
              </p>
            )}

            <div className="mt-7 grid gap-4 sm:grid-cols-3">
              <StatCard
                icon={CalendarCheck}
                tone="success"
                label="Serviços concluídos hoje"
                value={dashboard.isLoading ? '—' : String(dashboard.data?.todaysCompletedServices ?? 0)}
              />
              <StatCard
                icon={Wallet}
                tone="coral"
                label="Comissões do mês"
                value={dashboard.isLoading ? '—' : formatMoney(dashboard.data?.monthlyCommissionTotal ?? 0)}
              />
              <StatCard
                icon={UsersRound}
                tone="secondary"
                label="Funcionários ativos"
                value={dashboard.isLoading ? '—' : String(dashboard.data?.activeEmployees ?? 0)}
              />
            </div>

            <div className="mt-6 rounded-2xl border border-coral-200 bg-coral-50 p-5">
              <p className="text-sm font-semibold text-text-800">Código do estabelecimento</p>
              <p className="mt-1 text-3xl font-bold text-text-900">#{user.establishmentId}</p>
              <p className="mt-2 text-sm text-text-600">Compartilhe este código com clientes para que encontrem sua agenda.</p>
            </div>
            <div className="mt-6 grid gap-4 sm:grid-cols-2">
              <ActionCard to="/funcionarios" icon={UsersRound} title="Cadastrar equipe" description="Adicione os profissionais que poderão receber agendamentos." />
              <ActionCard to="/servicos" icon={Scissors} title="Cadastrar serviços" description="Defina valores e duração dos atendimentos oferecidos." />
            </div>
          </>
        )}
      </div>
    </div>
  )
}

const statCardTones = {
  success: 'bg-success-50 text-success-700',
  coral: 'bg-coral-50 text-coral-700',
  secondary: 'bg-secondary-50 text-secondary-700',
}

function StatCard({ icon: Icon, label, value, tone }: { icon: typeof CalendarCheck; label: string; value: string; tone: keyof typeof statCardTones }) {
  return (
    <div className="rounded-2xl border border-background-300 bg-background-50 p-5 shadow-sm">
      <span className={`inline-grid size-10 place-items-center rounded-xl ${statCardTones[tone]}`}><Icon size={21} /></span>
      <p className="mt-3 text-2xl font-bold text-text-900">{value}</p>
      <p className="mt-1 text-sm text-text-600">{label}</p>
    </div>
  )
}

function ActionCard({ to, icon: Icon, title, description }: { to: string; icon: typeof CalendarPlus; title: string; description: string }) {
  return (
    <Link to={to} className="group rounded-2xl border border-background-300 bg-background-50 p-5 shadow-sm transition hover:-translate-y-0.5 hover:border-primary-300 hover:shadow-md">
      <Icon className="text-text-600" size={24} />
      <h2 className="mt-4 text-lg font-semibold text-text-900 group-hover:text-text-700">{title}</h2>
      <p className="mt-2 text-sm leading-6 text-text-600">{description}</p>
    </Link>
  )
}

function formatMoney(value: number) {
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value)
}
