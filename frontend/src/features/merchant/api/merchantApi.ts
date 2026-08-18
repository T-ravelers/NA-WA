import { httpClient } from '@/shared/api/httpClient'

/**
 * 가맹점 화면이 쓰는 요청.
 *
 * QR 생성·매출 조회·계정 조회는 wallet·member feature와 같은 엔드포인트를 부르지만 함수는
 * 여기에 둔다. feature가 다른 feature의 내부를 import하지 않는다는 계층 규칙 때문이다.
 * 같은 URL을 부르는 것 자체는 의존이 아니다.
 */

/**
 * 이 화면이 필요로 하는 계정 정보.
 *
 * 회원 프로필 전체가 아니라 가맹점 판정과 상호명 표시에 쓰는 두 필드만 받는다. 서버가
 * 새 `accountType` 값을 추가해도 화면이 깨지지 않도록 문자열로 열어 두고, 가맹점 판정은
 * `MERCHANT` 일치로만 한다.
 */
export interface MerchantAccount {
  accountType: string
  displayName: string
}

/** 가맹점이 스스로 만든 결제 QR. 백엔드 `QrPaymentCreateResponse`와 1:1이다. */
export interface MerchantQr {
  qrPaymentId: number
  qrToken: string
  amount: number | null
  memo: string | null
  paymentStatus: string
  currencyCode: string
  /** `@JsonFormat`이 붙어 있어 `2026-08-18T12:00:00` 형태의 문자열로 내려온다. */
  expiresAt: string
}

/** 매출 1건. 백엔드 `TransactionSummaryResponse`와 1:1이다. */
export interface MerchantIncomeEntry {
  transferId: number
  transferType: string
  entryType: string
  amount: number
  balanceAfter: number
  /** 지갑 DTO에는 `@JsonFormat`이 없어 숫자 배열로 내려온다. */
  createdAt: string | number[] | null
}

export interface MerchantIncomeResponse {
  transactions: MerchantIncomeEntry[]
  nextCursor: string | null
}

/**
 * 가맹점으로 등록한다.
 *
 * 소셜 로그인은 계정을 항상 `TRAVELER`로 만든다. 상호명을 확정하는 이 호출이 가맹점
 * 회원가입의 마지막 단계이며, 한 번 성공하면 되돌릴 수 없다(재호출은 `MEMBER-009`).
 */
export async function registerAsMerchant(businessName: string): Promise<MerchantAccount> {
  const { data } = await httpClient.post<MerchantAccount>('/api/v1/members/me/merchant', {
    businessName,
  })

  return data
}

export async function fetchMerchantAccount(): Promise<MerchantAccount> {
  const { data } = await httpClient.get<MerchantAccount>('/api/v1/members/me')

  return data
}

export async function createMerchantQr(amount: number, memo: string | null): Promise<MerchantQr> {
  const { data } = await httpClient.post<MerchantQr>('/api/v1/wallet/qr/create', {
    amount,
    memo,
  })

  return data
}

/**
 * 기간별 매출을 조회한다.
 *
 * 가맹점은 결제·충전·정산을 하지 않으므로 그 지갑 원장에는 QR 수입만 쌓인다. 그래서
 * 매출 전용 API 없이 거래 내역 조회에 `type`·`status` 필터만 걸면 된다.
 */
export async function fetchMerchantIncome(
  from: string,
  to: string,
): Promise<MerchantIncomeResponse> {
  const { data } = await httpClient.get<MerchantIncomeResponse>('/api/v1/me/transactions', {
    params: {
      type: 'QR_PAYMENT',
      status: 'COMPLETED',
      from,
      to,
      size: 50,
    },
  })

  return data
}
