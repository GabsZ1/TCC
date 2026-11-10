/** Exibe a identidade visual da aplicação nos tamanhos usados pelas telas. */
interface BrandProps {
  compact?: boolean
  inverse?: boolean
  className?: string
}

/** Mostra a marca nas variações de tamanho usadas pela navegação e pelos formulários. */
export function Brand({ compact = false, inverse = false, className = '' }: BrandProps) {
  return (
    <div className={`inline-flex items-center gap-3 ${className}`} aria-label="Meu Espaço">
      <span
        className={`relative grid size-9 shrink-0 place-items-center overflow-hidden rounded-[0.65rem] border ${
          inverse
            ? 'border-white/15 bg-white/[0.06]'
            : 'border-primary-200 bg-primary-50'
        }`}
        aria-hidden="true"
      >
        <span className={`absolute h-[2px] w-5 -rotate-[32deg] rounded-full ${inverse ? 'bg-white' : 'bg-primary-700'}`} />
        <span className={`absolute h-[2px] w-5 translate-y-[6px] rotate-[32deg] rounded-full ${inverse ? 'bg-primary-300' : 'bg-primary-400'}`} />
      </span>
      {!compact && (
        <span className="leading-none">
          <span className={`block text-[1.08rem] font-extrabold tracking-[-0.045em] ${inverse ? 'text-white' : 'text-text-950'}`}>
            Meu <span className={inverse ? 'text-primary-300' : 'text-primary-600'}>Espaço</span>
          </span>
          <span className={`mt-1 block text-[0.56rem] font-extrabold uppercase tracking-[0.18em] ${inverse ? 'text-[var(--sidebar-muted)]' : 'text-text-500'}`}>
            beleza em movimento
          </span>
        </span>
      )}
    </div>
  )
}
