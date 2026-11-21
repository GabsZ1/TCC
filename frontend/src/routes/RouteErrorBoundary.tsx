import { Component, type ErrorInfo, type ReactNode } from 'react'

interface Props {
  children: ReactNode
}

interface State {
  hasError: boolean
}

/** Mantém uma saída visível quando uma rota ou seu módulo não consegue renderizar. */
export class RouteErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false }

  static getDerivedStateFromError(): State {
    return { hasError: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Falha ao carregar a página', error, info)
  }

  render() {
    if (this.state.hasError) {
      return (
        <main className="grid min-h-screen place-items-center bg-background-100 px-5">
          <div className="surface-card w-full max-w-md p-8 text-center">
            <h1 className="text-2xl font-extrabold text-text-950">Não foi possível abrir esta página</h1>
            <p className="mt-3 text-sm leading-6 text-text-600">Tente atualizar a página. Se o problema continuar, verifique a conexão e tente novamente.</p>
            <button type="button" onClick={() => window.location.reload()} className="primary-button mt-6 w-full justify-center">
              Atualizar página
            </button>
          </div>
        </main>
      )
    }

    return this.props.children
  }
}
