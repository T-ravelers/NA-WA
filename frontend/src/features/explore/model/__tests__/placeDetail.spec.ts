import { describe, expect, it } from 'vitest'

import { normalizePlaceDetail, toClosedDays, toDetailEntries } from '../placeDetail'

describe('placeDetail model helpers', () => {
  it('normalizes a nullable Place detail payload', () => {
    expect(
      normalizePlaceDetail({
        itemId: 880001,
        name: 'Seongsu Onsil',
        brand: null,
        branch: null,
        placeKind: 'NOT_SUPPORTED',
        thumbnailUrl: null,
        imageUrls: ['place.jpg', '', 42],
        region1: 'Seoul',
        region2: 'Seongsu',
        region3: null,
        addressRoad: null,
        addressDetail: null,
        latitude: null,
        longitude: null,
        hasForeignLang: null,
        hasParking: true,
        reservable: null,
        takeoutAvailable: null,
        cardPaymentAvailable: null,
        smokeFree: null,
        kidFacility: null,
        hasRestroom: null,
        isActive: null,
        viewCount: 0,
        favoriteCount: 0,
        saved: false,
        sourceUrl: null,
        postalCode: null,
        openingHours: { mon: '11:30–21:00' },
        closedDays: ['Seollal'],
        menuSummary: null,
        tel: null,
        activities: null,
      }),
    ).toMatchObject({
      placeId: 880001,
      placeKind: 'ETC',
      imageUrls: ['place.jpg'],
      isActive: false,
      activities: [],
    })
  })

  it('converts opening hours and closed days into display values', () => {
    expect(toDetailEntries({ mon: '11:30–21:00', tue: '11:30–21:00' })).toEqual([
      { label: 'mon', value: '11:30–21:00' },
      { label: 'tue', value: '11:30–21:00' },
    ])
    expect(toClosedDays(['Seollal', 'Chuseok'])).toBe('Seollal, Chuseok')
    expect(toClosedDays({ regular: 'Mondays' })).toBe('regular: Mondays')
  })

  // 수집한 영업시간의 3분의 1가량이 <br>을 그대로 달고 온다. 화면은 문자열을
  // 이스케이프하므로 두면 태그가 글자로 보인다.
  it('turns the crawled <br> tags into line breaks', () => {
    expect(toDetailEntries({ raw: '- 12:00~22:00<br>- 준비시간 15:00~18:00' })).toEqual([
      { label: 'raw', value: '- 12:00~22:00\n- 준비시간 15:00~18:00' },
    ])
    expect(toDetailEntries('12:00~22:00 <BR/>13:00~14:00')).toEqual([
      { label: 'hours', value: '12:00~22:00\n13:00~14:00' },
    ])
  })

  // 휴무일도 같은 크롤러에서 온다(2,211행 중 16행에 <br>이 있다). 여기는 한 줄로
  // 이어 적는 자리라 줄바꿈이 아니라 구분자로 바꾼다.
  it('strips the crawled <br> tags out of closed days too', () => {
    expect(toClosedDays(['매주 월요일<br>설·추석 당일'])).toBe('매주 월요일, 설·추석 당일')
  })

  /*
   * 행에 이미 "Closed"가 적혀 있어 크롤러가 붙인 키 이름을 덧붙일 이유가 없다.
   * 예전에는 객체로 오면 `raw: Every Monday`가 그대로 화면에 나갔고, 백엔드가 번역된
   * 휴무일을 배열로 감싸 피해 갔다(#531). 그 우회 때문에 프론트 렌더링 규칙이 SQL의
   * JSON 모양 선택을 붙잡고 있었다(#534).
   */
  it('does not print the crawler-made key when closed days arrive as an object', () => {
    expect(toClosedDays({ raw: 'Every Monday' })).toBe('Every Monday')
    expect(toClosedDays({ hours: 'Every Monday' })).toBe('Every Monday')
  })

  // 객체로 와도 배열과 같은 결과여야 백엔드가 어느 모양으로 보내든 화면이 같다.
  it('gives the same line for both JSON shapes the backend can send', () => {
    expect(toClosedDays({ raw: 'Every Monday' })).toBe(toClosedDays(['Every Monday']))
    expect(toClosedDays({ raw: '매주 월요일<br>설 당일' })).toBe(
      toClosedDays(['매주 월요일<br>설 당일']),
    )
  })
})
