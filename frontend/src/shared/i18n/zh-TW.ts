/**
 * 공통 문구 (zh-TW).
 *
 * `en.ts`와 같은 구조를 유지한다. 새 키는 `en.ts`에 먼저 추가한 뒤 여기에 번역을 더한다.
 * 여기 없는 key는 `en`으로 폴백한다.
 */
export default {
  app: {
    name: 'NA-WA',
    tagline: '一起規劃、旅行、結算',
  },
  action: {
    retry: '重試',
    back: '返回',
    goHome: '回到首頁',
    close: '關閉',
  },
  state: {
    loading: '載入中',
    empty: {
      title: '目前沒有內容',
      description: '這個畫面目前沒有可顯示的內容。',
    },
    error: {
      title: '發生錯誤',
      description: '無法載入這個畫面，請再試一次。',
    },
  },
  error: {
    network: '您似乎處於離線狀態。請檢查網路連線後再試一次。',
    timeout: '要求逾時，請再試一次。',
    unknown: '發生錯誤，請再試一次。',
  },
  nav: {
    label: '主導覽',
    home: '首頁',
    report: '報告',
    profile: '個人檔案',
    wallet: '錢包',
    journey: '旅程',
    comingSoon: '即將推出',
  },
  calendar: {
    previousMonth: '上個月',
    nextMonth: '下個月',
    selectDate: '選擇 {date}',
    weekdays: {
      sun: '日',
      mon: '一',
      tue: '二',
      wed: '三',
      thu: '四',
      fri: '五',
      sat: '六',
    },
  },
  spendingCategory: {
    FOOD: '美食',
    SHOPPING: '購物',
    BEAUTY: '美容',
    SHOW: '表演',
    TRANSPORT: '交通',
    STAY: '住宿',
    OTHER: '其他',
  },
  notFound: {
    title: '找不到頁面',
    description: '您要找的頁面不存在或已移動。',
  },
}
