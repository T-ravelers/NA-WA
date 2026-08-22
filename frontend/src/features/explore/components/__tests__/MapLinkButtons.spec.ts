import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { i18n } from '@/app/i18n'

const openMapAppUrl = vi.fn()
const openMapWebUrl = vi.fn()

// 진입 함수 두 개만 부분 모킹한다. 앱 스킴은 현재 문서를 이동시켜 jsdom의 navigation
// 경고가 다른 테스트로 새고, 웹 URL은 `window.open`의 인자 규칙(`noopener,noreferrer`)을
// `mapLink.spec.ts`가 소유하기 때문이다. 여기서는 **어떤 버튼이 어떤 URL을 여는가**만 본다.
vi.mock('@/shared/lib/mapLink', async (importOriginal) => ({
  ...(await importOriginal<typeof import('@/shared/lib/mapLink')>()),
  openMapAppUrl: (url: string | null) => openMapAppUrl(url),
  openMapWebUrl: (url: string | null) => openMapWebUrl(url),
}))

const MapLinkButtons = (await import('../MapLinkButtons.vue')).default

function mountButtons(props: Partial<InstanceType<typeof MapLinkButtons>['$props']> = {}) {
  return mount(MapLinkButtons, {
    props: { latitude: 37.48, longitude: 127.01, name: 'Seoul concert', ...props },
    global: { plugins: [i18n] },
  })
}

function clickButton(wrapper: ReturnType<typeof mountButtons>, label: string) {
  const button = wrapper.findAll('button').find((candidate) => candidate.text() === label)
  expect(button, `"${label}" 버튼이 없다`).toBeDefined()

  return button?.trigger('click')
}

describe('MapLinkButtons', () => {
  beforeEach(() => {
    openMapAppUrl.mockReset()
    openMapWebUrl.mockReset()
  })

  it('renders the four map buttons', () => {
    const labels = mountButtons()
      .findAll('button')
      .map((button) => button.text())

    expect(labels).toEqual(['Google Maps', 'Google transit', 'Naver Map', 'Naver transit'])
  })

  it('opens the Google Maps pin as a web URL', async () => {
    const wrapper = mountButtons()

    await clickButton(wrapper, 'Google Maps')

    expect(openMapWebUrl).toHaveBeenCalledWith(
      'https://www.google.com/maps/search/?api=1&query=37.48%2C127.01',
    )
  })

  it('opens the Google transit route as a web URL', async () => {
    const wrapper = mountButtons()

    await clickButton(wrapper, 'Google transit')

    expect(openMapWebUrl).toHaveBeenCalledWith(
      'https://www.google.com/maps/dir/?api=1&destination=37.48%2C127.01&travelmode=transit',
    )
  })

  it('opens the Naver Map place scheme with the place name', async () => {
    const wrapper = mountButtons()

    await clickButton(wrapper, 'Naver Map')

    expect(openMapAppUrl).toHaveBeenCalledWith(
      'nmap://place?lat=37.48&lng=127.01&name=Seoul%20concert&appname=NA-WA',
    )
  })

  it('opens the Naver Map transit scheme with the place name', async () => {
    const wrapper = mountButtons()

    await clickButton(wrapper, 'Naver transit')

    expect(openMapAppUrl).toHaveBeenCalledWith(
      'nmap://route/public?dlat=37.48&dlng=127.01&dname=Seoul%20concert&appname=NA-WA',
    )
  })

  // 장소명은 두 화면이 서로 다른 필드(`event.title` / `place.name`)를 넘긴다. 그 값이
  // 네이버 URL에 실려 나가는지 여기서 한 번만 확인한다.
  it('carries the given name into the Naver URLs', async () => {
    const wrapper = mountButtons({ latitude: 37.54, longitude: 127.05, name: 'Seongsu Onsil' })

    await clickButton(wrapper, 'Naver Map')

    expect(openMapAppUrl).toHaveBeenCalledWith(
      'nmap://place?lat=37.54&lng=127.05&name=Seongsu%20Onsil&appname=NA-WA',
    )
  })

  it('renders nothing when either coordinate is missing', () => {
    expect(mountButtons({ latitude: null }).findAll('button')).toHaveLength(0)
    expect(mountButtons({ longitude: undefined }).findAll('button')).toHaveLength(0)
  })
})
