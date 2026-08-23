import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import { i18n } from '@/app/i18n'

import ProfileEditForm from '../ProfileEditForm.vue'

function mountForm(props: Partial<InstanceType<typeof ProfileEditForm>['$props']> = {}) {
  return mount(ProfileEditForm, {
    props: {
      mode: 'edit',
      displayName: 'Mina',
      profileImageUrl: null,
      nationalityCode: 'JP',
      ...props,
    },
    global: { plugins: [i18n] },
  })
}

async function submitted(wrapper: ReturnType<typeof mountForm>) {
  await wrapper.get('form').trigger('submit')
  return wrapper.emitted('submit')
}

describe('ProfileEditForm', () => {
  it('starts from the profile it was given', () => {
    const wrapper = mountForm()

    expect((wrapper.get('input').element as HTMLInputElement).value).toBe('Mina')
    expect((wrapper.get('select').element as HTMLSelectElement).value).toBe('JP')
  })

  it('lists every country the server accepts', () => {
    // 첫 항목은 고르지 못하게 둔 안내용 자리다.
    expect(mountForm().findAll('option')).toHaveLength(250)
  })

  it('sends the trimmed values', async () => {
    const wrapper = mountForm()

    await wrapper.get('input').setValue('  Mina Park  ')

    expect((await submitted(wrapper))?.[0]?.[0]).toEqual({
      displayName: 'Mina Park',
      nationalityCode: 'JP',
    })
  })

  it('refuses an empty name', async () => {
    const wrapper = mountForm()

    await wrapper.get('input').setValue('   ')

    expect(await submitted(wrapper)).toBeUndefined()
    expect(wrapper.text()).toContain('Enter a name')
  })

  /* 길이는 code point로 센다. UTF-16으로 세면 이모지 이름이 서버에서만 막힌다. */
  it('counts a name in code points, not UTF-16 units', async () => {
    const wrapper = mountForm()

    await wrapper.get('input').setValue('🙂'.repeat(50))
    expect(await submitted(wrapper)).toHaveLength(1)

    await wrapper.get('input').setValue('🙂'.repeat(51))
    expect(await submitted(wrapper)).toHaveLength(1)
    expect(wrapper.text()).toContain('50 characters or fewer')
  })

  /*
   * 사진은 소셜 로그인이 가입 시점에 넣어 준 값을 그대로 쓴다. 첨부 경로가 영수증 전용이라
   * 주소를 붙여넣는 칸을 두지 않기로 했다(2026-08-22).
   */
  it('shows the photo but offers no way to change it', () => {
    const wrapper = mountForm({ profileImageUrl: 'https://example.test/me.png' })

    expect(wrapper.get('img').attributes('src')).toBe('https://example.test/me.png')
    // 이름 한 칸뿐이다. 국적은 select다.
    expect(wrapper.findAll('input')).toHaveLength(1)
  })

  /*
   * 서비스에서 처음 만나는 「Welcome」 화면이 아무것도 하지 않았는데 빨간 글씨부터 보여
   * 주지 않는다. 온보딩도 편집과 같은 시점에 오류를 띄운다.
   */
  it('stays quiet on arrival even when onboarding has nothing filled in', () => {
    const wrapper = mountForm({ mode: 'onboarding', nationalityCode: null, displayName: '' })

    expect(wrapper.text()).not.toContain('Choose a country.')
    expect(wrapper.text()).not.toContain('Enter a name.')
  })

  it('stays quiet in edit mode until a field is touched', () => {
    const wrapper = mountForm({ displayName: '', nationalityCode: null })

    expect(wrapper.text()).not.toContain('Enter a name.')
  })

  /*
   * 버튼을 잠가 두면 무엇이 빠졌는지 말해 줄 계기가 사라진다. 열어 두고 누르는 순간 짚어 준다.
   */
  it('names what is missing when the first attempt is made', async () => {
    const wrapper = mountForm({ mode: 'onboarding', nationalityCode: null, displayName: '' })

    expect(wrapper.get('button[type="submit"]').attributes('disabled')).toBeUndefined()
    expect(await submitted(wrapper)).toBeUndefined()
    expect(wrapper.text()).toContain('Choose a country.')
    expect(wrapper.text()).toContain('Enter a name.')
  })

  it('offers a way out only when editing', () => {
    expect(mountForm().text()).toContain('Cancel')
    expect(mountForm({ mode: 'onboarding' }).text()).not.toContain('Cancel')
  })

  /*
   * 버튼의 `:disabled` 하나에만 기대면 그 속성을 손대는 순간 이중 전송이 조용히 생긴다.
   * 지금 사용자가 닿는 경로는 없지만 가드는 함수 안에도 둔다.
   */
  it('does not send twice while a save is already in flight', async () => {
    const wrapper = mountForm({ submitting: true })

    await wrapper.get('form').trigger('submit')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('shows what the server said', () => {
    const wrapper = mountForm({ submitError: 'We do not support that country yet.' })

    expect(wrapper.get('[role="alert"]').text()).toContain('We do not support that country yet.')
  })
})
