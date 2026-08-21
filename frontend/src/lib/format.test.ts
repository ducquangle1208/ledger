import { describe, expect, it } from 'vitest'
import { formatMoney } from './format'

describe('formatMoney', () => {
  it('formats VND values without fractional display', () => {
    expect(formatMoney('125000.00', 'VND')).toContain('125.000')
    expect(formatMoney('125000.00', 'VND')).toContain('₫')
  })
})
