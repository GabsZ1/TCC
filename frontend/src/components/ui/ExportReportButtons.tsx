/** Oferece os formatos de exportação disponíveis para os relatórios. */
import { useState } from 'react'
import { Download } from 'lucide-react'
import { downloadFromApi } from '@/lib/download'

type Format = 'csv' | 'pdf'

/** Exporta o relatório com os filtros já aplicados na tela. */
export function ExportReportButtons({ path, params, filename, formats = ['csv', 'pdf'] }: { path: string; params: object; filename: string; formats?: Format[] }) {
  const [loading, setLoading] = useState<Format | null>(null)
  const [error, setError] = useState(false)

  async function exportReport(format: Format) {
    setLoading(format)
    setError(false)
    try {
      await downloadFromApi(`${path}/${format}`, params, `${filename}.${format}`)
    } catch {
      setError(true)
    } finally {
      setLoading(null)
    }
  }

  return (
    <span className="inline-flex flex-wrap items-center gap-2">
      {formats.map((format) => (
        <button key={format} type="button" onClick={() => exportReport(format)} disabled={loading !== null} className="secondary-button disabled:opacity-60">
          <Download size={16} /> {loading === format ? 'Exportando...' : `Exportar ${format.toUpperCase()}`}
        </button>
      ))}
      {error && <span role="alert" className="text-sm text-danger-600">Não foi possível exportar o relatório.</span>}
    </span>
  )
}
