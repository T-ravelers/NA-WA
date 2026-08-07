/**
 * 회원 문구.
 *
 * `errorCode`는 백엔드 MemberErrorCode(MEMBER-001~MEMBER-004)와 1:1로 맞춘다.
 * 서버가 내려준 message를 화면에 그대로 노출하지 않고 여기의 문구를 사용한다.
 */
export default {
  member: {
    settings: {
      title: 'Settings',
      account: 'Account',
      preferences: 'Preferences',
      language: {
        label: 'Screen language',
        /** 아이콘만 있는 행이 아니라 값이 보이는 행이므로 동작을 이름에 담는다. */
        change: 'Change screen language',
        sheetTitle: 'Language',
        hint: 'Applies right away and is saved to your account.',
        /**
         * 저장 실패를 알리되 되돌리지 않는다. 화면은 이미 고른 언어로 그려져 있고,
         * 되돌리면 방금 누른 선택이 이유 없이 튕겨 보인다.
         */
        saveFailed:
          'Your language is set on this device, but we could not save it to your account.',
      },
    },
    errorCode: {
      'MEMBER-001': 'We could not find your account. Please sign in again.',
      'MEMBER-002': 'That language is not supported yet.',
      'MEMBER-003': 'That currency is not supported yet.',
      'MEMBER-004': 'There was nothing to change.',
    },
  },
}
