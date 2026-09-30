import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, expect, it } from 'vitest'
import { EstablishmentImage } from './EstablishmentImage'

describe('identidade visual do estabelecimento', () => {
  it('mostra fallback quando não há logo nem capa', () => {
    const client = new QueryClient()
    const logo = renderToStaticMarkup(createElement(QueryClientProvider, { client },
      createElement(EstablishmentImage, { establishmentId: 1, kind: 'logo', hasImage: false })))
    const cover = renderToStaticMarkup(createElement(QueryClientProvider, { client },
      createElement(EstablishmentImage, { establishmentId: 1, kind: 'capa', hasImage: false })))
    expect(logo).toContain('Sem logo')
    expect(logo).toContain('ME')
    expect(cover).toContain('Sem imagem de capa')
    expect(cover).toContain('Bem-vindo')
  })
})
