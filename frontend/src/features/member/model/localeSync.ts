import { applyLocale } from '@/app/i18n/applyLocale'
import { readStoredLocale } from '@/shared/i18n/localePreference'
import { isSupportedLocale } from '@/shared/i18n/locales'

import { updateMemberProfile, type MemberProfile } from '../api/memberApi'
import { setMemberProfile } from './memberQueries'

/**
 * 로그인 시점의 로케일 충돌을 해소한다.
 *
 * `members.preferred_language`는 `NOT NULL DEFAULT 'en'`이라 "사용자가 en을 골랐다"와
 * "아무도 고른 적 없다"가 서버에서 구분되지 않는다. 그래서 서버 값으로 덮지 않고
 * 브라우저의 명시 선택을 우선한다. 명시 선택이 없을 때만 서버 값을 따른다.
 *
 * 서버 저장에 실패해도 화면은 이미 사용자의 선택으로 그려져 있다. 되돌리면 방금 고른
 * 언어가 이유 없이 튕겨 보이므로, 실패를 삼키고 다음 로그인에서 다시 시도한다.
 */
export async function syncLocaleWithProfile(profile: MemberProfile): Promise<void> {
  const stored = readStoredLocale()

  if (stored === null) {
    if (!isSupportedLocale(profile.preferredLanguage)) {
      return
    }

    // 서버 값은 적용만 한다. 저장하면 「키의 존재 = 명시 선택」 규약이 깨진다 —
    // 가입 직후의 서버 값은 컬럼 기본값(en)일 수 있고, 그것을 명시 선택으로 굳히면
    // 브라우저 감지로 일본어를 보던 신규 회원이 영어에 영구히 갇힌다.
    applyLocale(profile.preferredLanguage)
    return
  }

  applyLocale(stored, { persist: true })

  if (stored === profile.preferredLanguage) {
    return
  }

  try {
    setMemberProfile(await updateMemberProfile({ preferredLanguage: stored }))
  } catch {
    // 로컬 선택이 정본이다. 다음 로그인에서 다시 시도한다.
  }
}
