/** Traduz erros de validação de formulários para mensagens em português. */
const localizedInputs = new WeakSet<HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement>()

/** Converte mensagens técnicas de validação em instruções compreensíveis no formulário. */
export function localizeFormValidation() {
  document.addEventListener('invalid', (event) => {
    const field = event.target
    if (!(field instanceof HTMLInputElement || field instanceof HTMLSelectElement || field instanceof HTMLTextAreaElement)) return
    if (field.validity.customError) return

    const { validity } = field
    let message: string | null = null
    if (validity.valueMissing) message = 'Preencha este campo.'
    else if (validity.typeMismatch) message = field instanceof HTMLInputElement && field.type === 'email' ? 'Informe um e-mail válido.' : 'Informe um valor válido.'
    else if (validity.badInput) message = 'Informe um número válido.'
    else if (validity.rangeUnderflow) message = field instanceof HTMLInputElement && field.name === 'price' ? 'O valor mínimo é R$ 0,01.' : `O valor mínimo permitido é ${field instanceof HTMLInputElement ? field.min : ''}.`
    else if (validity.rangeOverflow) message = `O valor máximo permitido é ${field instanceof HTMLInputElement ? field.max : ''}.`
    else if (validity.stepMismatch) message = 'Informe um valor dentro dos intervalos permitidos.'
    else if (validity.tooShort) message = `Use pelo menos ${field instanceof HTMLInputElement || field instanceof HTMLTextAreaElement ? field.minLength : 0} caracteres.`
    else if (validity.tooLong) message = 'O texto ultrapassa o tamanho permitido.'
    else if (validity.patternMismatch) message = 'Informe um valor no formato solicitado.'

    if (message) {
      field.setCustomValidity(message)
      localizedInputs.add(field)
    }
  }, true)

  const clearMessage = (event: Event) => {
    const field = event.target
    if (field instanceof HTMLInputElement || field instanceof HTMLSelectElement || field instanceof HTMLTextAreaElement) {
      if (localizedInputs.has(field)) {
        field.setCustomValidity('')
        localizedInputs.delete(field)
      }
    }
  }
  document.addEventListener('input', clearMessage, true)
  document.addEventListener('change', clearMessage, true)
}
