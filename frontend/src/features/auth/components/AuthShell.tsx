import type { ReactNode } from 'react'
import { CalendarDays, Clock3, Scissors } from 'lucide-react'
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
  sm: 'max-w-[30rem]',
  md: 'max-w-[37rem]',
  lg: 'max-w-[48rem]',
}

export function AuthShell({ eyebrow, title, subtitle, children, maxWidth = 'md' }: AuthShellProps) {
  return (
    <main className="min-h-screen bg-background-50 lg:grid lg:grid-cols-[minmax(27rem,.92fr)_minmax(34rem,1.08fr)]">
      <aside className="relative hidden min-h-screen overflow-hidden bg-[var(--sidebar-bg)] px-10 py-9 text-white lg:flex lg:flex-col xl:px-14 xl:py-11">
        <div className="absolute inset-y-0 left-0 w-[4px] bg-primary-500" aria-hidden="true" />
        <div className="pointer-events-none absolute -right-24 top-24 text-[16rem] font-extrabold leading-none tracking-[-.12em] text-white/[0.025]" aria-hidden="true">SH</div>

        <Link to="/login" className="relative w-fit"><Brand inverse /></Link>

        <div className="relative my-auto max-w-[35rem] py-12">
          <p className="text-[0.66rem] font-extrabold uppercase tracking-[0.2em] text-primary-300">Salões · barbearias · spas</p>
          <h2 className="mt-5 max-w-[31rem] font-[var(--font-display)] text-[clamp(3.4rem,5vw,5.9rem)] font-normal leading-[.88] tracking-[-0.045em] text-white">
            Sua rotina,<br /><span className="italic text-primary-300">em ritmo.</span>
          </h2>
          <p className="mt-7 max-w-md text-[0.96rem] leading-7 text-[var(--sidebar-muted)]">
            Uma interface pensada para quem atende pessoas: rápida no balcão, clara na gestão e confortável no celular.
          </p>

          <div className="mt-10 border-y border-white/10 py-5">
            <div className="flex items-center justify-between gap-4">
              <div>
                <p className="text-[0.6rem] font-extrabold uppercase tracking-[0.16em] text-[var(--sidebar-muted)]">Agenda de hoje</p>
                <p className="mt-1.5 text-sm font-bold text-white">Próximos atendimentos</p>
              </div>
              <CalendarDays size={19} className="text-primary-300" />
            </div>
            <div className="mt-4 space-y-2.5">
              <ScheduleRow time="09:30" service="Corte + finalização" />
              <ScheduleRow time="11:00" service="Massagem relaxante" />
              <ScheduleRow time="14:15" service="Barba + acabamento" />
            </div>
          </div>
        </div>

        <div className="relative flex items-center justify-between gap-4 border-t border-white/10 pt-5 text-[0.7rem] text-[var(--sidebar-muted)]">
          <span>SalonHub · gestão de beleza e bem-estar</span>
          <span className="flex items-center gap-1.5"><Clock3 size={13} /> simples de usar</span>
        </div>
      </aside>

      <section className="relative flex min-h-screen items-center justify-center px-5 py-16 sm:px-8 lg:px-12 xl:px-16">
        <div className={`w-full ${widths[maxWidth]}`}>
          <Link to="/login" className="mb-10 inline-flex lg:hidden"><Brand /></Link>

          <header className="mb-8">
            <p className="eyebrow">{eyebrow}</p>
            <h1 className="mt-4 text-[2.45rem] font-extrabold leading-[.98] tracking-[-0.055em] text-text-950 sm:text-[3.15rem]">{title}</h1>
            <p className="mt-4 max-w-xl text-[0.96rem] leading-7 text-text-600">{subtitle}</p>
          </header>

          <div className="border-t border-background-300 pt-7">{children}</div>

          <div className="mt-8 flex items-center gap-2 text-xs text-text-500 lg:hidden">
            <Scissors size={14} className="text-primary-600" />
            Feito para negócios de beleza, cuidado e bem-estar.
          </div>
        </div>
      </section>
    </main>
  )
}

function ScheduleRow({ time, service }: { time: string; service: string }) {
  return (
    <div className="grid grid-cols-[3.2rem_1fr] items-center gap-3 rounded-[0.7rem] border border-white/[0.08] bg-white/[0.035] px-3.5 py-3">
      <span className="font-mono text-[0.72rem] font-bold text-primary-300">{time}</span>
      <span className="truncate text-xs font-semibold text-white/90">{service}</span>
    </div>
  )
}
