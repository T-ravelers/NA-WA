export function formatSettlementAmount(amount: string): string {
  const [integer = '0', fraction] = amount.split('.')
  const sign = integer.startsWith('-') ? '-' : ''
  const digits = sign === '' ? integer : integer.slice(1)
  const grouped = digits.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return fraction === undefined ? `${sign}${grouped}` : `${sign}${grouped}.${fraction}`
}
