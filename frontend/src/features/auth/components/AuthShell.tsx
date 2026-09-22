import type { ReactNode } from 'react'
import { CalendarDays, Scissors, UsersRound } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Brand } from '@/components/brand/Brand'

interface AuthShellProps {
  eyebrow: string
  title: string
  subtitle: string
  children: ReactNode
  maxWidth?: 'sm' | 'md' | 'lg'
}

const widths = {
  sm: 'max-w-md',
  md: 'max-w-xl',
  lg: 'max-w-3xl',
}

export function AuthShell({ eyebrow, title, subtitle, children, maxWidth = 'md' }: AuthShellProps) {
  return (
    <main className="min-h-screen bg-background-100 lg:grid lg:grid-cols-[minmax(25rem,.82fr)_minmax(34rem,1.18fr)]">
      <aside className="relative hidden min-h-screen overflow-hidden bg-[var(--sidebar-bg)] p-10 text-[var(--sidebar-text)] lg:flex lg:flex-col xl:p-14">
        <div className="pointer-events-none absolute -left-24 top-[28%] size-80 rounded-full border border-white/[0.07]" />
        <div className="pointer-events-none absolute -left-2 top-[34%] size-52 rounded-full border border-white/[0.08]" />
        <div className="pointer-events-none absolute -bottom-24 -right-16 size-[25rem] rounded-full bg-primary-500/10 blur-2xl" />

        <Link to="/login" className="relative w-fit"><Brand inverse /></Link>

        <div className="relative my-auto max-w-lg py-14">
          <p className="text-[0.7rem] font-bold uppercase tracking-[0.18em] text-primary-300">Uma rotina mais organizada</p>
          <h2 className="mt-5 font-[var(--font-display)] text-[clamp(2.4rem,4vw,4.4rem)] font-semibold leading-[.98] tracking-[-0.055em] text-white">
            Gestão bonita por fora. Útil por dentro.
          </h2>
          <p className="mt-6 max-w-md text-[0.95rem] leading-7 text-[var(--sidebar-muted)]">
            Agenda, equipe, serviços e operação no mesmo lugar — com uma interface feita para funcionar tanto no balcão quanto no celular.
          </p>

          <div className="mt-9 grid gap-3 sm:grid-cols-3 lg:grid-cols-1 xl:grid-cols-3">
            <Feature icon={<CalendarDays size={17} />} label="Agenda" />
            <Feature icon={<UsersRound size={17} />} label="Equipe" />
            <Feature icon={<Scissors size={17} />} label="Serviços" />
          </div>
        </div>

        <p className="relative text-xs leading-5 text-[var(--sidebar-muted)]">
          SalonHub · plataforma de gestão para negócios de beleza, cuidado e bem-estar.
        </p>
      </aside>

      <section className="relative flex min-h-screen items-center justify-center overflow-hidden px-4 py-20 sm:px-8 lg:px-12">
        <div className="pointer-events-none absolute right-[-8rem] top-[-10rem] size-[30rem] rounded-full border border-primary-200/50" />
        <div className="pointer-events-none absolute right-[-2rem] top-[-4rem] size-[18rem] rounded-full border border-accent-200/45" />

        <div className={`relative w-full ${widths[maxWidth]}`}>
          <Link to="/login" className="mb-8 inline-flex lg:hidden"><Brand /></Link>
          <div className="mb-8">
            <p className="eyebrow">{eyebrow}</p>
            <h1 className="mt-3 font-[var(--font-display)] text-[2.25rem] font-semibold leading-[1.05] tracking-[-0.045em] text-text-950 sm:text-[2.8rem]">
              {title}
            </h1>
            <p className="mt-3 max-w-xl text-[0.95rem] leading-6 text-text-600">{subtitle}</p>
          </div>

          <div className="surface-card p-5 sm:p-7 lg:p-8">
            {children}
          </div>
        </div>
      </section>
    </main>
  )
}

function Feature({ icon, label }: { icon: ReactNode; label: string }) {
  return (
    <div className="flex items-center gap-2.5 rounded-xl border border-white/[0.09] bg-white/[0.045] px-3.5 py-3 text-sm font-semibold text-[var(--sidebar-text)] backdrop-blur-sm">
      <span className="text-primary-300">{icon}</span>
      {label}
    </div>
  )
}
