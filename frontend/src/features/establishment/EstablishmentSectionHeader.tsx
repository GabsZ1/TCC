import type { ReactNode } from 'react'

export function EstablishmentSectionHeader({ icon, title, description }: { icon: ReactNode; title: string; description: string }) {
  return (
    <div>
      <div className="flex items-center gap-2 text-text-600">
        {icon}
        <h2 className="text-lg font-semibold text-text-900">{title}</h2>
      </div>
      <p className="mt-1 text-sm text-text-600">{description}</p>
    </div>
  )
}
