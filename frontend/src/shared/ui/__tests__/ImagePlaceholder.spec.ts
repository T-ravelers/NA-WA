import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ImagePlaceholder from '../ImagePlaceholder.vue'

describe('ImagePlaceholder', () => {
  // 전용 일러스트가 오면 이 파일 하나만 바꾸면 되도록 자산을 쓰지 않는다.
  it('draws without loading an image asset', () => {
    const wrapper = mount(ImagePlaceholder)

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.get('div').classes()).toContain('bg-surface-2')
  })

  it('fills its container so the caller controls ratio and rounding', () => {
    expect(mount(ImagePlaceholder).get('div').classes()).toContain('size-full')
  })

  it('is decorative when no label is given', () => {
    const root = mount(ImagePlaceholder).get('div')

    expect(root.attributes('role')).toBe('presentation')
    expect(root.attributes('aria-label')).toBeUndefined()
  })

  it('becomes a named image when the caller supplies a label', () => {
    const root = mount(ImagePlaceholder, { props: { label: 'No photo for DDP tour' } }).get('div')

    expect(root.attributes('role')).toBe('img')
    expect(root.attributes('aria-label')).toBe('No photo for DDP tour')
  })
})
