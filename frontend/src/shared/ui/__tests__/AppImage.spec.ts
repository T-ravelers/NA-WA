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

  it('keeps raw image tags limited to generated QR codes and bundled payment logos', () => {
    const sourceRoot = resolve(process.cwd(), 'src')
    const rawImageFiles = globSync('**/*.vue', { cwd: sourceRoot })
      .filter((path) => readFileSync(`${sourceRoot}/${path}`, 'utf8').includes('<img'))
      .sort()

    expect(rawImageFiles).toEqual([
      'features/merchant/views/MerchantView.vue',
      'features/wallet/views/TopupView.vue',
      'features/wallet/views/WalletQrView.vue',
      'shared/ui/AppImage.vue',
    ])
  })
})
