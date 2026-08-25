/**
 * Individual Report 프레젠테이션 계약.
 *
 * **이 파일이 #152가 소유하는 계약의 정본이다.** 백엔드 wire DTO(#151)가 아니라
 * 화면이 그대로 그릴 수 있는 형태다. wire 값(`string | number`, nullable)을 여기 타입으로
 * 정규화하는 어댑터는 #153이 `features/report/api`에서 구현한다. 이 디렉터리의 컴포넌트는
 * fetch를 하지 않고 아래 타입만 props로 받는다.
 *
 * 사용자에게 보이는 문자열(제목·빈 상태 문구·대체 설명)은 이 타입에 넣지 않는다.
 * 컴포넌트가 개별 prop으로 받으므로 `useI18n`이나 #153의 i18n key에 의존하지 않는다.
 */

import type { Category } from '@/shared/ui/category'

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

export interface ReportPersonaTicketProps extends Headed {
  /** 티켓 상단 라벨. 예: `Travel spending type` */
  label: string
  /** 해시태그 칭호. 예: `#FLAVORSEEKER` */
  title: string
  /** 비중이 채워진 설명 문장. */
  description: string
  /** 스탬프 큰 값. 예: `42%` */
  stampValue: string
  /** 스탬프 작은 라벨. 1위 카테고리 표시명. 예: `Food` */
  stampLabel: string
  /**
   * stub 왼쪽 공유 버튼의 라벨. 예: `Share ticket`. 없으면 버튼을 그리지 않는다.
   * 누르면 `share`를 emit한다 — 무엇을 어떻게 보낼지는 화면이 정한다.
   */
  shareLabel?: string
  /**
   * 티켓 색. 시안 R4에서 티켓은 도넛 1위 조각과 같은 색이다.
   * 어느 색인지는 화면(#153)이 정한다 — 여기서 소비 카테고리 문자열을 알지 않는다.
   */
  tone?: Category | 'paper'
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

/* ── 비교(#404) ── */

export interface ReportComparisonBarRow {
  id: number
  /** 표시명. 나는 화면이 번역한 `You`다. */
  label: string
  totalSpent: MoneyValue
  dailyAverage: MoneyValue
  /** GROUP 동료 칩의 사진. 나와 SIMILAR 평균처럼 칩이 없으면 생략한다. */
  profileImageUrl?: string | null
}

export interface ReportComparisonBarsProps extends LocaleAware {
  /** 블록 라벨. 예: `Total spend` */
  totalLabel: string
  /** 일 평균 블록 라벨. 예: `Daily avg` */
  dailyAverageLabel: string
  /** 동료 칩 라디오 그룹의 접근 가능한 이름. 예: `Group members` */
  chipsLabel: string
  me: ReportComparisonBarRow
  peers: ReportComparisonBarRow[]
  /** 동료 칩을 그린다. 비교 상대가 평균 하나뿐인 SIMILAR에서는 끈다. 기본 `true`. */
  chips?: boolean
}

export interface ReportRadarAxis {
  key: string
  label: string
  /** 0–100 비중. */
  mine: number
  /** 0–100 비중. */
  cohort: number
}

export interface ReportRadarChartProps {
  /** 받은 순서대로 12시부터 시계 방향. 3개 미만이면 그리지 않는다. */
  axes: ReportRadarAxis[]
  mineLabel: string
  cohortLabel: string
  /** 차트를 한 문장으로 설명한다. 화면에는 보이지 않고 스크린 리더만 읽는다. */
  description?: string
}

export interface ReportRankTile {
  key: string
  label: string
  /** 로케일에 맞춰 만든 순위 글자, 또는 코호트 대비 비중 차이. 예: `1st`, `+12%`, `AVG` */
  rankText: string
  tone: Category | 'surface'
}

export interface ReportRankTilesProps {
  tiles: ReportRankTile[]
  /**
   * 목록의 접근 가능한 이름. 값이 순위인지 코호트 대비 비중인지는 scope가 정하므로
   * 화면이 넘긴다 — `# Food +12%`만으로는 무엇 대비인지 읽히지 않는다.
   */
  label?: string
}
