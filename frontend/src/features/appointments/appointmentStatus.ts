import type { Appointment } from '@/features/appointments/appointmentsApi'

/** Nome amigável de cada status para exibir ao usuário. */
export const APPOINTMENT_STATUS_LABELS: Record<Appointment['status'], string> = {
  CONFIRMED: 'Confirmado',
  COMPLETED: 'Concluído',
  CANCELLED: 'Cancelado',
  NO_SHOW: 'Não compareceu',
  INVALIDATED: 'Invalidado',
}

export const APPOINTMENT_STATUS_STYLES: Record<Appointment['status'], string> = {
  CONFIRMED: 'bg-primary-50 text-primary-700',
  COMPLETED: 'bg-success-50 text-success-700',
  CANCELLED: 'bg-danger-50 text-danger-600',
  NO_SHOW: 'bg-warning-100 text-warning-700',
  INVALIDATED: 'bg-background-200 text-text-600',
}
