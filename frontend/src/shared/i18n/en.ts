/**
 * 도메인에 속하지 않는 공통 문구.
 *
 * 이 파일은 `en`이 원본이다. 다른 로케일 파일은 같은 구조를 따르고, 비어 있는 key는
 * `en`으로 폴백한다. 도메인 문구는 여기가 아니라 `features/<domain>/i18n/`에 둔다.
 */
export default {
  app: {
    name: 'NA-WA',
    tagline: 'Plan, travel and settle up together',
  },
  action: {
    retry: 'Try again',
    back: 'Go back',
    goHome: 'Go to home',
    close: 'Close',
  },
  state: {
    loading: 'Loading',
    empty: {
      title: 'Nothing here yet',
      description: 'There is nothing to show on this screen right now.',
    },
    error: {
      title: 'Something went wrong',
      description: 'We could not load this screen. Please try again.',
    },
  },
  error: {
    network: 'You appear to be offline. Check your connection and try again.',
    timeout: 'The request took too long. Please try again.',
    unknown: 'Something went wrong. Please try again.',
  },
  nav: {
    label: 'Main navigation',
    journey: 'Journey',
    explore: 'Explore',
    wallet: 'Wallet',
    report: 'Report',
    my: 'My',
    comingSoon: 'Coming soon',
  },
  /**
   * 공용 달력. `shared/ui/CalendarGrid.vue`가 읽는다.
   *
   * 요일은 한 글자로 줄인다. 7칸이 390px 안에 들어가야 한다.
   */
  calendar: {
    previousMonth: 'Previous month',
    nextMonth: 'Next month',
    /** `{date}`는 로케일 표기로 풀어 쓴 날짜다. 스크린 리더만 읽는다. */
    selectDate: 'Select {date}',
    weekdays: {
      sun: 'S',
      mon: 'M',
      tue: 'T',
      wed: 'W',
      thu: 'T',
      fri: 'F',
      sat: 'S',
    },
  },

  /**
   * 소비 카테고리 표시명.
   *
   * 결제 화면·거래 상세·리포트가 함께 쓰는 어휘라 도메인이 아니라 여기에 둔다.
   * key는 서버가 보내는 값과 1:1이다.
   */
  spendingCategory: {
    FOOD: 'Food',
    SHOPPING: 'Shopping',
    BEAUTY: 'Beauty',
    SHOW: 'Shows',
    TRANSPORT: 'Transport',
    STAY: 'Stay',
    OTHER: 'Other',
  },
  notFound: {
    title: 'Page not found',
    description: 'The page you are looking for does not exist or has moved.',
  },
}
