import { httpClient } from '@/shared/api/httpClient'

/**
 * 거래 1건. 백엔드 `TransactionSummaryResponse`와 1:1이다.
 *
 * 금액은 `BigDecimal`이 JSON number로 내려온다. 문자열이 섞일 여지가 없으므로
 * `string | number`로 열어 두지 않는다. 열어 두면 화면이 `String()`으로 강제 변환하게 되고,
 * 자릿수 구분과 부호 처리가 전부 문자열 조작으로 흘러간다.
 *
 * `transferType`·`entryType`은 서버 enum이지만 여기서는 좁히지 않는다. 모르는 값이
 * 내려왔을 때 화면이 깨지지 않도록 좁히는 일은 model이 담당한다.
 *
 * `createdAt`은 `LocalDateTime`이며 지금은 **숫자 배열**로 내려온다. 해석 규칙은
 * `model/walletHome.ts`에 있다.
 */
export interface WalletTransaction {
  transferId: number
  transferType: string
  entryType: string
  amount: number
  balanceAfter: number
  createdAt: ServerDateTime
}

/**
 * 서버가 내려주는 시각의 표현.
 *
 * 지갑 DTO에는 `@JsonFormat`이 없어 Jackson이 `LocalDateTime`을 `[년, 월, 일, 시, 분, 초,
 * 나노초]` 배열로 직렬화한다. 뒤쪽 0은 생략되므로 길이가 5~7로 흔들린다.
 *
 * ```json
 * "createdAt": [2026, 7, 25, 12, 0]
 * ```
 *
 * 문자열도 함께 받는다. journey·explore 도메인은 이미 `@JsonFormat`으로 ISO 문자열을
 * 내보내고 있어 지갑도 그렇게 바뀔 수 있는데(#108 후속), 그때 이 타입을 손대지 않아도
 * 화면이 계속 동작해야 한다.
 */
export type ServerDateTime = string | number[] | null

/** `GET /api/v1/wallet` 응답. 백엔드 `WalletHomeResponse`와 1:1이다. */
export interface WalletHome {
  balance: number
  availabilityStatus: string
  recentTransactions: WalletTransaction[]
}

export async function fetchWalletHome(): Promise<WalletHome> {
  const { data } = await httpClient.get<WalletHome>('/api/v1/wallet')

  return data
}
