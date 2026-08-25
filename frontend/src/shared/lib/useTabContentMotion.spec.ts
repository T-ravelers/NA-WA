import { mount } from '@vue/test-utils'
import { defineComponent, h, nextTick, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

const reducedMotion = ref(false)

vi.mock('motion-v', async (importOriginal) => ({
  ...(await importOriginal<typeof import('motion-v')>()),
  useReducedMotion: () => reducedMotion,
}))

const { useTabContentMotion } = await import('./useTabContentMotion')

function mountHarness() {
  let setupValue: ReturnType<typeof useTabContentMotion>['value'] | undefined

  const Harness = defineComponent({
    setup() {
      const motion = useTabContentMotion()
      setupValue = motion.value

      return () =>
        h('div', {
          'data-initial': JSON.stringify(motion.value.initial),
          'data-duration': String(motion.value.transition.duration),
        })
    },
  })

  return { wrapper: mount(Harness), setupValue }
}

describe('useTabContentMotion', () => {
  it('skips the first render and prepares a 180ms incoming fade after mount', async () => {
    reducedMotion.value = false

    const { wrapper, setupValue } = mountHarness()

    expect(setupValue?.initial).toBe(false)
    expect(setupValue?.transition.duration).toBe(0)

    await nextTick()

    expect(wrapper.attributes('data-initial')).toBe('{"opacity":0}')
    expect(wrapper.attributes('data-duration')).toBe('0.18')
  })

  it('keeps tab content transitions instant when reduced motion is requested', async () => {
    reducedMotion.value = true

    const { wrapper } = mountHarness()
    await nextTick()

    expect(wrapper.attributes('data-initial')).toBe('false')
    expect(wrapper.attributes('data-duration')).toBe('0')
  })
})
