import { Link, Navigate } from 'react-router-dom'
import { CalendarPlus, Scissors, UsersRound } from 'lucide-react'
import { useAuth } from '@/hooks/useAuth'

export function DashboardPage() {
  const { user } = useAuth()

  if (user?.role === 'CLIENT') return <Navigate to="/agendamentos" replace />
  if (user?.role === 'PROFESSIONAL') return <Navigate to="/minha-agenda" replace />

  return (
    <div className="p-5 sm:p-7 lg:p-9">
      <div className="max-w-4xl">
        <p className="text-sm font-semibold text-brand-500">Olá, {user?.name}</p>
        <h1 className="mt-2 font-[var(--font-display)] text-3xl text-brand-900 sm:text-4xl">{user?.role === 'ADMIN' ? user.establishmentName : 'Seu acesso profissional'}</h1>
        <p className="mt-3 max-w-2xl text-base leading-7 text-brand-600">{user?.role === 'ADMIN' ? 'Prepare o catálogo e a equipe para começar a receber agendamentos.' : 'Seu acesso está ativo. A gestão do estabelecimento é feita pelo administrador.'}</p>

        {user?.role === 'ADMIN' && (
          <>
            <div className="mt-7 rounded-2xl border border-brand-200 bg-brand-50 p-5">
              <p className="text-sm font-semibold text-brand-800">Código do estabelecimento</p>
              <p className="mt-1 text-3xl font-bold text-brand-900">#{user.establishmentId}</p>
              <p className="mt-2 text-sm text-brand-600">Compartilhe este código com clientes para que encontrem sua agenda.</p>
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

function ActionCard({ to, icon: Icon, title, description }: { to: string; icon: typeof CalendarPlus; title: string; description: string }) {
  return (
    <Link to={to} className="group rounded-2xl border border-brand-100 bg-white p-5 shadow-sm transition hover:-translate-y-0.5 hover:border-brand-300 hover:shadow-md">
      <Icon className="text-brand-500" size={24} />
      <h2 className="mt-4 text-lg font-semibold text-brand-900 group-hover:text-brand-700">{title}</h2>
      <p className="mt-2 text-sm leading-6 text-brand-600">{description}</p>
    </Link>
  )
}
