/** Explica a falta de permissão quando a rota não corresponde ao papel do usuário. */
import { ArrowLeft, ShieldX } from 'lucide-react'
import { Link } from 'react-router-dom'
import { Brand } from '@/components/brand/Brand'

/** Oferece uma saída de navegação quando o papel do usuário não permite a rota. */
export function ForbiddenPage() {
  return (
    <main className="flex min-h-screen items-center justify-center bg-background-100 px-4 py-12">
      <div className="w-full max-w-md text-center">
        <Brand className="justify-center" />
        <section className="surface-card mt-8 p-8 sm:p-10">
          <span className="mx-auto grid size-14 place-items-center rounded-2xl bg-danger-50 text-danger-600">
            <ShieldX size={27} />
          </span>
          <p className="eyebrow mt-6 justify-center">Acesso restrito</p>
          <h1 className="mt-2 font-[var(--font-display)] text-3xl font-semibold tracking-[-0.04em] text-text-950">Essa área não está disponível para o seu perfil.</h1>
          <p className="mt-3 text-sm leading-6 text-text-600">Volte para uma área liberada ou entre com outra conta.</p>
          <Link to="/" className="secondary-button mt-6"><ArrowLeft size={16} /> Voltar ao início</Link>
        </section>
      </div>
    </main>
  )
}
