/** Protege por testes o comportamento de themeContrast, incluindo os cenários de regressão deste módulo. */
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'

const css = readFileSync(fileURLToPath(new URL('./index.css', import.meta.url)), 'utf8')
const MINIMUM_NORMAL_TEXT_CONTRAST = 4.5

type ThemeTokens = Record<string, string>

function rootTokens() {
  const match = css.match(/:root\s*\{([\s\S]*?)\n\}/)
  if (!match) throw new Error('Bloco :root não encontrado')

  const rawTokens = Object.fromEntries(
    [...match[1].matchAll(/--([a-z0-9-]+):\s*([^;]+);/gi)].map((token) => [token[1], token[2].trim()]),
  )

  const resolve = (name: string, trail: string[] = []): string => {
    if (trail.includes(name)) throw new Error(`Referência circular: ${[...trail, name].join(' -> ')}`)
    const value = rawTokens[name]
    if (!value) throw new Error(`Token não encontrado: ${name}`)
    if (value.startsWith('#')) return normalizeHex(value)
    const reference = value.match(/^var\(--([a-z0-9-]+)\)$/i)?.[1]
    if (!reference) throw new Error(`Valor de cor não suportado em ${name}: ${value}`)
    return resolve(reference, [...trail, name])
  }

  return Object.fromEntries(
    Object.keys(rawTokens)
      .filter((name) => rawTokens[name].startsWith('#') || rawTokens[name].startsWith('var('))
      .map((name) => [name, resolve(name)]),
  ) as ThemeTokens
}

function normalizeHex(hex: string) {
  if (hex.length === 4) return `#${[...hex.slice(1)].map((digit) => digit.repeat(2)).join('')}`
  return hex.toLowerCase()
}

function relativeLuminance(hex: string) {
  const channels = hex.slice(1).match(/.{2}/g)?.map((channel) => Number.parseInt(channel, 16) / 255)
  if (!channels || channels.length !== 3) throw new Error(`Cor inválida: ${hex}`)
  const [red, green, blue] = channels.map((channel) => channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4)
  return 0.2126 * red + 0.7152 * green + 0.0722 * blue
}

function contrast(foreground: string, background: string) {
  const fg = relativeLuminance(foreground)
  const bg = relativeLuminance(background)
  return (Math.max(fg, bg) + 0.05) / (Math.min(fg, bg) + 0.05)
}

const combinations = [
  ['text-400', 'background-50'],
  ['text-500', 'background-50'],
  ['text-600', 'background-200'],
  ['text-900', 'background-50'],
  ['primary-700', 'primary-50'],
  ['accent-700', 'accent-100'],
  ['success-700', 'success-50'],
  ['warning-700', 'warning-50'],
  ['danger-600', 'danger-50'],
  ['on-primary', 'primary-600'],
  ['on-danger', 'danger-500'],
  ['on-success', 'success-600'],
  ['on-warning', 'warning-700'],
  ['sidebar-text', 'sidebar-bg'],
] as const

describe('contraste do tema claro', () => {
  const tokens = rootTokens()
  it.each(combinations)('%s sobre %s atende WCAG AA', (foregroundToken, backgroundToken) => {
    const foreground = tokens[foregroundToken]
    const background = tokens[backgroundToken]
    expect(foreground, `${foregroundToken} não foi definido`).toBeTruthy()
    expect(background, `${backgroundToken} não foi definido`).toBeTruthy()
    expect(contrast(foreground, background), `${foregroundToken} (${foreground}) sobre ${backgroundToken} (${background})`).toBeGreaterThanOrEqual(MINIMUM_NORMAL_TEXT_CONTRAST)
  })
})
