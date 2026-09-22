import { Moon, Sun } from 'lucide-react'
import { useTheme } from '@/hooks/useTheme'

export function ThemeToggle() {
  const { theme, toggleTheme } = useTheme()
  const isDark = theme === 'dark'

  return (
    <button
      type="button"
      onClick={toggleTheme}
      className="fixed bottom-5 right-5 z-50 grid size-11 place-items-center rounded-full border border-background-300/90 bg-background-50/90 text-text-700 shadow-[var(--shadow-soft)] backdrop-blur-xl hover:-translate-y-0.5 hover:border-primary-300 hover:bg-primary-50 hover:text-primary-700"
      aria-label={isDark ? 'Ativar tema claro' : 'Ativar tema escuro'}
      title={isDark ? 'Ativar tema claro' : 'Ativar tema escuro'}
    >
      {isDark ? <Sun size={18} strokeWidth={1.9} aria-hidden="true" /> : <Moon size={18} strokeWidth={1.9} aria-hidden="true" />}
    </button>
  )
}
