import { globSync, readFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import AppImage from '../AppImage.vue'
import ImagePlaceholder from '../ImagePlaceholder.vue'

describe('AppImage', () => {
  it('renders the image with native attributes while the source is available', () => {
    const wrapper = mount(AppImage, {
      props: { src: 'https://example.com/photo.jpg', alt: 'Seoul at night' },
      attrs: { class: 'size-full', loading: 'lazy' },
    })

    const image = wrapper.get('img')
    expect(image.attributes()).toMatchObject({
      src: 'https://example.com/photo.jpg',
      alt: 'Seoul at night',
      loading: 'lazy',
    })
    expect(image.classes()).toContain('size-full')
  })

  it('uses the placeholder when the source is absent or fails to load', async () => {
    const missing = mount(AppImage, { props: { src: null, alt: 'Event photo' } })
    expect(missing.find('img').exists()).toBe(false)
    expect(missing.getComponent(ImagePlaceholder).props('label')).toBe('Event photo')

    const failed = mount(AppImage, {
      props: { src: 'https://example.com/broken.jpg', placeholderLabel: 'Image unavailable' },
    })
    await failed.get('img').trigger('error')

    expect(failed.find('img').exists()).toBe(false)
    expect(failed.getComponent(ImagePlaceholder).props('label')).toBe('Image unavailable')
  })

  it('tries again when the source changes after a failure', async () => {
    const wrapper = mount(AppImage, {
      props: { src: 'https://example.com/broken.jpg', alt: '' },
    })
    await wrapper.get('img').trigger('error')
    await wrapper.setProps({ src: 'https://example.com/replacement.jpg' })

    expect(wrapper.get('img').attributes('src')).toBe('https://example.com/replacement.jpg')
  })

  it('lets the caller keep a domain-specific fallback', async () => {
    const wrapper = mount(AppImage, {
      props: { src: 'https://example.com/avatar.jpg', alt: '' },
      slots: { default: '<span data-testid="initials">AB</span>' },
    })
    await wrapper.get('img').trigger('error')

    expect(wrapper.get('[data-testid="initials"]').text()).toBe('AB')
  })

  /*
   * `AppImage`는 네트워크 이미지가 실패했을 때를 책임진다. 실패할 수 없는 이미지
   * — 화면에서 만든 QR, 번들에 들어 있는 결제 로고와 장식 — 만 날 `<img>`로 둔다.
   */
  it('keeps raw image tags limited to generated QR codes and bundled assets', () => {
    const sourceRoot = resolve(process.cwd(), 'src')
    const rawImageFiles = globSync('**/*.vue', { cwd: sourceRoot })
      .filter((path) => readFileSync(`${sourceRoot}/${path}`, 'utf8').includes('<img'))
      .sort()

    expect(rawImageFiles).toEqual([
      'features/journey/components/JourneyCategoryBloom.vue',
      'features/merchant/views/MerchantView.vue',
      'features/wallet/views/TopupView.vue',
      'features/wallet/views/WalletQrView.vue',
      'shared/ui/AppImage.vue',
    ])
  })
})
