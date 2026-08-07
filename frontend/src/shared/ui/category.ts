/**
 * 소비영역 4종.
 *
 * 칩·점·티켓·차트가 모두 같은 목록을 써야 해서 컴포넌트가 아니라 여기에 둔다.
 * 영역을 늘리거나 줄이는 결정은 디자인 토큰(`app/styles/tokens.css`)과 함께 움직인다.
 */
export const CATEGORIES = ['beauty', 'shopping', 'show', 'food'] as const

export type Category = (typeof CATEGORIES)[number]
