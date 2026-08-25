/** 일정 카드 멤버 칩의 머리글자. 이름이 비면 물음표로 대신한다. */
export function initialsOf(name: string): string {
  const trimmed = name.trim()
  if (trimmed === '') return '?'

  return trimmed.slice(0, 1).toLocaleUpperCase()
}
