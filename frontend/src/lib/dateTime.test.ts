import { describe, expect, it } from 'vitest'
import { addBusinessDays, addMinutesToBusinessLocalDateTime, businessToday, formatBusinessDateTime, hasBusinessLocalDateTimeStarted, calendarDateToBusinessLocalDateTime, formatBusinessDateWithWeekday, isBusinessLocalDateTimeFuture } from './dateTime'

describe('dateTime', () => {
  it('uses the Sao Paulo date across midnight', () => {
    expect(businessToday(new Date('2026-10-08T02:30:00Z'))).toBe('2026-10-07')
    expect(businessToday(new Date('2026-10-08T03:30:00Z'))).toBe('2026-10-08')
  })

  it('compares API LocalDateTime using business time', () => {
    const now = new Date('2026-10-08T03:00:00Z')
    expect(hasBusinessLocalDateTimeStarted('2026-10-07T23:59:59', now)).toBe(true)
    expect(isBusinessLocalDateTimeFuture('2026-10-08T00:00:01', now)).toBe(true)
  })

  it('keeps calendar arithmetic and formatting timezone independent', () => {
    expect(addMinutesToBusinessLocalDateTime('2026-10-07T23:55:00', 15)).toBe('2026-10-08T00:10:00')
    expect(addBusinessDays('2026-10-31', 1)).toBe('2026-11-01')
    expect(formatBusinessDateTime('2026-10-08T00:05:00')).toBe('08/10/2026 00:05')
  })
  it('preserves FullCalendar UTC-coerced selections as business local date-times', () => {
    expect(calendarDateToBusinessLocalDateTime(new Date('2026-10-08T00:05:00Z'))).toBe('2026-10-08T00:05:00')
    expect(formatBusinessDateWithWeekday('2026-10-08')).toContain('08')
  })
})
