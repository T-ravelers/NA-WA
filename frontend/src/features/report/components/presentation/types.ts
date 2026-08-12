/**
 * Individual final Report 프레젠테이션 계약.
 *
 * **이 파일이 #152가 소유하는 계약의 정본이다.** 백엔드 wire DTO(#151)가 아니라
 * 화면이 그대로 그릴 수 있는 형태다. wire 값(`string | number`, nullable)을 여기 타입으로
 * 정규화하는 어댑터는 #153이 `features/report/api`에서 구현한다. 이 디렉터리의 컴포넌트는
 * fetch를 하지 않고 아래 타입만 props로 받는다.
 *
 * 사용자에게 보이는 문자열(제목·빈 상태 문구·대체 설명)은 이 타입에 넣지 않는다.
 * 컴포넌트가 개별 prop으로 받으므로 `useI18n`이나 #153의 i18n key에 의존하지 않는다.
 */

/** 정규화가 끝난 금액. 소수점 없는 최소 화폐 단위 정수다. */
export type MoneyValue = number

export interface ReportKpiData {
  totalSpent: MoneyValue
  dailyAverage: MoneyValue
  currency: 'KRW'
}

export interface ReportCategoryBreakdownItem {
  /**
   * 지출 카테고리 식별자. **임의 문자열이다.**
   *
   * Wallet의 `spendingCategory`가 현재 자유 문자열이고 null도 가능해서, 여기서
   * `shared/ui`의 Explore 전용 `Category` 유니온으로 좁히지 않는다. 값이 없는 경우
   * 어떤 문자열로 접을지는 #153 어댑터가 정한다.
   */
  category: string
  /** 화면에 그대로 찍히는 표시명. 번역은 #153이 끝낸 뒤 넘긴다. */
  label: string
  amount: MoneyValue
  /** 0–100 스케일. 0.42가 아니라 42다. 합이 100이 아니어도 각 항목을 그대로 그린다. */
  percentage: number
}

export interface ReportDailyTrendPoint {
  /** `YYYY-MM-DD`. 정렬과 key에만 쓰고 표시에는 쓰지 않는다. */
  date: string
  /** 축에 찍히는 표시명. 포맷은 #153이 끝낸 뒤 넘긴다. */
  label: string
  amount: MoneyValue
}

/* ── 컴포넌트 props ──
 * 각 SFC 안이 아니라 여기에 둔다. 화면을 조립하는 #153이 prop 타입을 그대로 import해
 * 어댑터 반환형을 맞출 수 있고, 컴포넌트 인스턴스에서 뽑아 쓸 때 딸려 오는
 * `Record<string, any>`를 피할 수 있다. */

/** 금액 표기에 쓸 로케일. 기본은 앱 폴백과 같은 `en`이다. */
interface LocaleAware {
  locale?: string
}

/** 있으면 컴포넌트가 섹션 제목을 함께 그린다. */
interface Headed {
  heading?: string
}

export interface ReportKpiCardProps extends Headed, LocaleAware {
  data: ReportKpiData
  /** 총 지출 라벨. 예: `Total spent` */
  totalLabel: string
  /** 일 평균 라벨. 예: `Daily avg` */
  dailyAverageLabel: string
}

export interface ReportCategoryBreakdownProps extends Headed, LocaleAware {
  items: ReportCategoryBreakdownItem[]
  currency: ReportKpiData['currency']
  /** 도넛 가운데 큰 값. 예: `12` */
  centerValue?: string
  /** 도넛 가운데 작은 라벨. `centerValue`와 함께 있을 때만 그린다. 예: `events` */
  centerLabel?: string
  /** 차트를 한 문장으로 설명한다. 화면에는 보이지 않고 스크린 리더만 읽는다. */
  description?: string
  emptyTitle: string
  emptyDescription: string
}

export interface ReportDailyTrendProps extends Headed, LocaleAware {
  points: ReportDailyTrendPoint[]
  currency: ReportKpiData['currency']
  /** 차트를 한 문장으로 설명한다. 화면에는 보이지 않고 스크린 리더만 읽는다. */
  description?: string
  emptyTitle: string
  emptyDescription: string
}
