export type MoneyValue = string | number | null | undefined

function isDecimalString(value: string): boolean {
  return /^[+-]?(?:\d+(?:\.\d*)?|\.\d+)$/.test(value)
}

function getNumberSymbols(locale: string): {
  group: string
  decimal: string
  primary: number
  secondary: number
} {
  const formatter = new Intl.NumberFormat(locale, { useGrouping: true })
  const parts = formatter.formatToParts(123_456_789_012_345.67)
  const integerParts = parts.filter((part) => part.type === 'integer').map((part) => part.value)
  const group = parts.find((part) => part.type === 'group')?.value ?? ','
  const decimal = parts.find((part) => part.type === 'decimal')?.value ?? '.'

  return {
    group,
    decimal,
    primary: integerParts[integerParts.length - 1]?.length ?? 3,
    secondary:
      integerParts[integerParts.length - 2]?.length ??
      integerParts[integerParts.length - 1]?.length ??
      3,
  }
}

function groupInteger(value: string, locale: string, useGrouping: boolean): string {
  if (!useGrouping) return value

  const { group, primary, secondary } = getNumberSymbols(locale)
  if (value.length <= primary) return value

  const chunks: string[] = []
  let end = value.length
  let groupSize = primary
  while (end > 0) {
    const start = Math.max(0, end - groupSize)
    chunks.unshift(value.slice(start, end))
    end = start
    groupSize = secondary
  }

  return chunks.join(group)
}

function roundDecimal(
  integer: string,
  fraction: string,
  maximumFractionDigits: number | undefined,
): { integer: string; fraction: string } {
  if (maximumFractionDigits === undefined || fraction.length <= maximumFractionDigits) {
    return { integer, fraction }
  }

  const keptFraction = fraction.slice(0, maximumFractionDigits)
  const nextDigit = fraction[maximumFractionDigits]
  if (nextDigit === undefined || nextDigit < '5') {
    return { integer, fraction: keptFraction }
  }

  const combined = BigInt(`${integer}${keptFraction || ''}`) + 1n
  const combinedText = combined.toString().padStart(integer.length + keptFraction.length, '0')
  if (maximumFractionDigits === 0) {
    return { integer: combinedText, fraction: '' }
  }

  return {
    integer: combinedText.slice(0, -maximumFractionDigits),
    fraction: combinedText.slice(-maximumFractionDigits),
  }
}

function resolveDecimalSign(
  negative: boolean,
  zero: boolean,
  signDisplay: Intl.NumberFormatOptions['signDisplay'] = 'auto',
): string {
  if (signDisplay === 'never') return ''
  if (signDisplay === 'always') return negative ? '-' : '+'
  if (signDisplay === 'exceptZero') return zero ? '' : negative ? '-' : '+'
  return negative ? '-' : ''
}

function formatExactDecimal(
  value: string,
  locale: string,
  options: Intl.NumberFormatOptions,
): string {
  if (!isDecimalString(value)) return ''

  const negative = value.startsWith('-')
  const unsigned = value.replace(/^[+-]/, '')
  const [rawInteger = '0', rawFraction = ''] = unsigned.split('.')
  const maximumFractionDigits = options.maximumFractionDigits
  const minimumFractionDigits = options.minimumFractionDigits ?? 0
  const rounded = roundDecimal(
    rawInteger.replace(/^0+(?=\d)/, '') || '0',
    rawFraction,
    maximumFractionDigits,
  )
  const fraction = rounded.fraction.padEnd(minimumFractionDigits, '0')
  const { decimal } = getNumberSymbols(locale)
  const groupedInteger = groupInteger(rounded.integer, locale, options.useGrouping !== false)
  const zero = rounded.integer === '0' && !/[1-9]/.test(fraction)
  const sign = resolveDecimalSign(negative, zero, options.signDisplay)

  return `${sign}${groupedInteger}${fraction ? decimal + fraction : ''}`
}

function formatFiniteNumber(
  value: number,
  locale: string,
  options: Intl.NumberFormatOptions,
): string {
  return Number.isFinite(value) ? new Intl.NumberFormat(locale, options).format(value) : ''
}

function formatExactCurrency(
  value: string,
  locale: string,
  options: Intl.NumberFormatOptions,
): string {
  const formatter = new Intl.NumberFormat(locale, options)
  const resolved = formatter.resolvedOptions()
  const unsigned = value.replace(/^[+-]/, '')
  const negative = value.startsWith('-')
  const numberText = formatExactDecimal(unsigned, locale, {
    maximumFractionDigits: resolved.maximumFractionDigits,
    minimumFractionDigits: resolved.minimumFractionDigits,
    useGrouping: resolved.useGrouping,
  })
  const zero = !/[1-9]/.test(numberText)
  const templateValue = zero ? (negative ? -0 : 0) : negative ? -1 : 1
  const parts = formatter.formatToParts(templateValue)
  const numericPartTypes = new Set(['integer', 'group', 'decimal', 'fraction'])
  const firstNumericPart = parts.findIndex((part) => numericPartTypes.has(part.type))
  let lastNumericPart = -1
  for (let index = parts.length - 1; index >= 0; index -= 1) {
    if (numericPartTypes.has(parts[index]?.type ?? '')) {
      lastNumericPart = index
      break
    }
  }
  if (firstNumericPart < 0 || lastNumericPart < firstNumericPart) return ''

  const prefix = parts
    .slice(0, firstNumericPart)
    .map((part) => part.value)
    .join('')
  const suffix = parts
    .slice(lastNumericPart + 1)
    .map((part) => part.value)
    .join('')
  return `${prefix}${numberText}${suffix}`
}

/**
 * 로케일 숫자 포맷터. 문자열은 Number로 바꾸지 않고 십진수 자릿수를 그대로 묶는다.
 * 통화·퍼센트처럼 Intl 의미가 필요한 숫자는 서버가 내려준 값의 계약에 맞춰 호출부에서
 * 숫자를 전달하고, 지갑 원장 문자열은 formatGroupedDecimal을 사용한다.
 */
export function formatMoney(
  value: MoneyValue,
  locale: string,
  options: Intl.NumberFormatOptions = {},
): string {
  if (value === null || value === undefined) return ''
  if (typeof value === 'number') return formatFiniteNumber(value, locale, options)
  if (!isDecimalString(value)) return ''

  if (options.style === 'currency') {
    return formatExactCurrency(value, locale, options)
  }
  if (options.style !== undefined && options.style !== 'decimal') return ''
  return formatExactDecimal(value, locale, options)
}

/** 문자열 원장 금액을 정밀도 손실 없이 로케일 구분자로 표시한다. */
export function formatGroupedDecimal(
  value: MoneyValue,
  locale = 'en-US',
  options: Pick<
    Intl.NumberFormatOptions,
    'maximumFractionDigits' | 'minimumFractionDigits' | 'useGrouping' | 'signDisplay'
  > = {},
): string {
  return formatMoney(value, locale, options)
}

export function formatNumber(
  value: MoneyValue,
  locale: string,
  options: Intl.NumberFormatOptions = {},
): string {
  return formatMoney(value, locale, options)
}

export function formatCurrency(
  value: MoneyValue,
  locale: string,
  currency: string,
  options: Omit<Intl.NumberFormatOptions, 'style' | 'currency'> = {},
): string {
  if (value === null || value === undefined) return ''

  return formatMoney(value, locale, {
    ...options,
    style: 'currency',
    currency,
  })
}

/** 기존 지갑 화면의 영문 원화 표기를 보존한다. */
export function formatKrw(value: MoneyValue): string {
  const amount = formatMoney(value, 'en-US', { maximumFractionDigits: 0 })
  return amount === '' ? '' : `₩${amount}`
}
