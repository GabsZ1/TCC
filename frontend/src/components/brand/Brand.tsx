import { Layers3 } from 'lucide-react'

interface BrandProps {
  compact?: boolean
  inverse?: boolean
  className?: string
}

export function Brand({ compact = false, inverse = false, className = '' }: BrandProps) {
  return (
    <div className={`inline-flex items-center gap-3 ${className}`} aria-label="SalonHub">
      <span
        className={`grid size-10 shrink-0 place-items-center rounded-[0.9rem] border shadow-sm ${
          inverse
            ? 'border-white/10 bg-white/10 text-[var(--sidebar-text)]'
            : 'border-primary-200 bg-primary-100 text-primary-700'
        }`}
        aria-hidden="true"
      >
        <Layers3 size={20} strokeWidth={1.8} />
      </span>
      {!compact && (
        <span className="leading-none">
          <span
            className={`block font-[var(--font-display)] text-[1.28rem] font-semibold tracking-[-0.035em] ${
              inverse ? 'text-[var(--sidebar-text)]' : 'text-text-950'
            }`}
          >
            SalonHub
          </span>
          <span
            className={`mt-1 block text-[0.61rem] font-bold uppercase tracking-[0.16em] ${
              inverse ? 'text-[var(--sidebar-muted)]' : 'text-text-500'
            }`}
          >
            gestão & agenda
          </span>
        </span>
      )}
    </div>
  )
}
