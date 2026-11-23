/** Política de senha do SalonHub — a mesma validada no backend (8 a 72 caracteres). */
export const PASSWORD_MIN_LENGTH = 8
export const PASSWORD_MAX_LENGTH = 72

/** Critérios recomendados (ainda não obrigatórios — decisão do grupo, fica para o final). */
export function recommendedCriteria(password: string) {
  return [
    { label: 'Letras e números (recomendado)', met: /[A-Za-z]/.test(password) && /\d/.test(password) },
    { label: 'Um símbolo, como ! @ # (recomendado)', met: /[^A-Za-z0-9]/.test(password) },
  ]
}
