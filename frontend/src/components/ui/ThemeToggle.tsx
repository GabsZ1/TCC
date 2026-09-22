import { Moon, Sun } from 'lucide-react'
import { useTheme } from '@/hooks/useTheme'

export function ThemeToggle() {
  const { theme, toggleTheme } = useTheme()
  const isDark = theme === 'dark'
  return <button type="button" onClick={toggleTheme} className="fixed right-4 top-4 z-50 grid size-10 place-items-center rounded-xl border border-background-300 bg-background-50 text-text-700 shadow-sm hover:border-primary-400 hover:bg-primary-50 hover:text-text-900" aria-label={isDark ? 'Ativar tema claro' : 'Ativar tema escuro'} title={isDark ? 'Ativar tema claro' : 'Ativar tema escuro'}>{isDark ? <Sun size={19} aria-hidden="true" /> : <Moon size={19} aria-hidden="true" />}</button>
}

