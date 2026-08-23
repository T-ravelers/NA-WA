import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'
import AppTicket from '@/shared/ui/AppTicket.vue'

import ReportPersonaTicket from '../ReportPersonaTicket.vue'

const PROPS = {
  label: 'Travel spending type',
  title: '#FLAVORSEEKER',
  description: 'You followed your appetite — 42% of this journey went to food.',
  stampValue: '42%',
  stampLabel: 'Food',
}

describe('ReportPersonaTicket', () => {
  // i18n 플러그인 없이 마운트한다. props-only 계약이 실제로 상위와 끊겨 있는지 보는 것이다.
  it('renders the label, hashtag title, description and stamp from props alone', () => {
    const wrapper = mount(ReportPersonaTicket, { props: PROPS })

    expect(wrapper.text()).toContain('Travel spending type')
    expect(wrapper.get('h3').text()).toBe('#FLAVORSEEKER')
    expect(wrapper.text()).toContain('42% of this journey went to food.')
    expect(wrapper.text()).toContain('42%')
    expect(wrapper.text()).toContain('Food')
  })

  // 시안 R4는 티켓 위에 `Your spending type` 제목을 둔다. 티켓 안의 라벨과는 다른 문구다.
  it('draws the section heading above the ticket when it is given one', () => {
    const wrapper = mount(ReportPersonaTicket, {
      props: { ...PROPS, heading: 'Your spending type' },
    })

    expect(wrapper.get('h2').text()).toBe('Your spending type')
    expect(wrapper.get('h3').text()).toBe('#FLAVORSEEKER')
  })

  it('omits the section heading when the screen does not pass one', () => {
    const wrapper = mount(ReportPersonaTicket, { props: PROPS })

    expect(wrapper.find('h2').exists()).toBe(false)
  })

  it('paints the ticket in the tone it is given', () => {
    const wrapper = mount(ReportPersonaTicket, { props: { ...PROPS, tone: 'food' } })

    expect(wrapper.getComponent(AppTicket).props('tone')).toBe('food')
    expect(wrapper.getComponent(AppTicket).classes()).toContain('bg-food')
  })

  it('falls back to the paper tone when no tone is given', () => {
    const wrapper = mount(ReportPersonaTicket, { props: PROPS })

    expect(wrapper.getComponent(AppTicket).classes()).toContain('bg-paper')
  })

  it('uses the vertical ticket so the stamp sits under the perforation', () => {
    const wrapper = mount(ReportPersonaTicket, { props: PROPS })

    expect(wrapper.getComponent(AppTicket).props('orientation')).toBe('vertical')
    expect(wrapper.getComponent(AppTicket).props('bodySize')).toBeGreaterThan(0)
  })

  // 시안 R4의 stub 왼쪽 `Share ticket`. 무엇을 보낼지는 화면이 정하므로 여기서는 emit만 본다.
  // 버튼은 공용 `AppButton`이라 로딩 라벨 때문에 i18n 플러그인이 필요하다 — 티켓 자신은 여전히 props만 쓴다.
  it('draws the share button from its label and emits share when it is pressed', async () => {
    const wrapper = mount(ReportPersonaTicket, {
      props: { ...PROPS, shareLabel: 'Share ticket' },
      global: { plugins: [i18n] },
    })
    const button = wrapper.get('button')

    expect(button.text()).toBe('Share ticket')
    await button.trigger('click')
    expect(wrapper.emitted('share')).toHaveLength(1)
  })

  it('omits the share button when the screen passes no label', () => {
    const wrapper = mount(ReportPersonaTicket, { props: PROPS })

    expect(wrapper.find('button').exists()).toBe(false)
  })
})
