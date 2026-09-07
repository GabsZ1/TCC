export function ForbiddenPage() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-2 bg-sand-50 px-4 text-center">
      <h1 className="font-[var(--font-display)] text-2xl text-brand-900">Acesso restrito</h1>
      <p className="text-brand-600">Você não possui permissão para acessar esta página.</p>
    </div>
  )
}
