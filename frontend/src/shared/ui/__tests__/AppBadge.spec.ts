import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AppBadge from '../AppBadge.vue'

function mountBadge(props: Record<string, unknown> = {}) {
  return mount(AppBadge, { props, slots: { default: 'In progress' } })
}

describe('AppBadge', () => {
  it('always carries a text label', () => {
    expect(mountBadge({ tone: 'ongoing', dot: true }).text()).toBe('In progress')
  })

  it.each([
    ['ongoing', 'bg-status-ongoing'],
    ['scheduled', 'bg-on-paper/70'],
    ['pending', 'bg-status-scheduled'],
    ['completed', 'bg-status-ongoing'],
    ['info', 'bg-info'],
    ['danger', 'bg-danger'],
  ] as const)('codes the %s dot from tokens', (tone, expected) => {
    const dot = mountBadge({ tone, dot: true }).get('[aria-hidden="true"]')

    expect(dot.classes()).toContain(expected)
  })

  // 상태 점은 라벨을 보조하는 장식이다. 같은 정보가 두 번 읽히면 안 된다.
  it('hides the dot from assistive technology', () => {
    expect(mountBadge({ tone: 'ongoing', dot: true }).find('[aria-hidden="true"]').exists()).toBe(
      true,
    )
  })

  it('omits the dot by default', () => {
    expect(mountBadge({ tone: 'settlement' }).find('[aria-hidden="true"]').exists()).toBe(false)
  })

  it('distinguishes ongoing and scheduled with the badge surface, not only the dot', () => {
    const ongoing = mountBadge({ tone: 'ongoing', dot: true })
    const scheduled = mountBadge({ tone: 'scheduled', dot: true })

    expect(ongoing.classes()).toEqual(expect.arrayContaining(['bg-canvas/70', 'text-ink']))
    expect(scheduled.classes()).toEqual(
      expect.arrayContaining(['bg-status-scheduled', 'text-on-paper']),
    )
    expect(ongoing.classes()).not.toContain('bg-status-scheduled')
  })
})
