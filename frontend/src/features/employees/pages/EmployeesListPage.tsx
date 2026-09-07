export function EmployeesListPage() {
  return (
    <div className="p-6">
      <div className="flex items-center justify-between">
        <h1 className="font-[var(--font-display)] text-2xl text-brand-900">Funcionários</h1>
        <button className="rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700">
          Novo funcionário
        </button>
      </div>

      {/* TODO: substituir por tabela real (useQuery + endpoint /employees),
          ordenada alfabeticamente (RN) e com badge visual para inativos. */}
      <div className="mt-6 overflow-hidden rounded-2xl border border-brand-100 bg-white shadow-sm">
        <table className="w-full text-left text-sm">
          <thead className="bg-sand-100 text-brand-700">
            <tr>
              <th className="px-4 py-3 font-medium">Nome</th>
              <th className="px-4 py-3 font-medium">Ocupação</th>
              <th className="px-4 py-3 font-medium">Status</th>
              <th className="px-4 py-3 font-medium">Ações</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td className="px-4 py-6 text-brand-500" colSpan={4}>
                Nenhum funcionário carregado ainda.
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  )
}
