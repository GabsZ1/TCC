import { useState } from 'react'
import { Download } from 'lucide-react'
import { downloadFromApi } from '@/lib/download'

/** Exporta em CSV o relatório com os filtros aplicados na tela (o backend usa o mesmo cálculo da tela). */
export function ExportCsvButton({ path, params, filename }: { path: string; params: object; filename: string }) {
  const [state, setState] = useState<'idle' | 'loading' | 'error'>('idle')

  async function exportCsv() {
    setState('loading')
    try {
      await downloadFromApi(path, params, filename)
      setState('idle')
    } catch {
      setState('error')
    }
  }

  return (
    <span className="inline-flex flex-wrap items-center gap-2">
      <button type="button" onClick={exportCsv} disabled={state === 'loading'} className="secondary-button disabled:opacity-60">
        <Download size={16} /> {state === 'loading' ? 'Exportando...' : 'Exportar CSV'}
      </button>
      {state === 'error' && <span role="alert" className="text-sm text-danger-600">Não foi possível exportar o relatório.</span>}
    </span>
  )
}
