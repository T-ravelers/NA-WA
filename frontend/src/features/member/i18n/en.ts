/**
 * 회원 문구.
 *
 * `errorCode`는 백엔드 MemberErrorCode와 1:1로 맞춘다. 서버가 내려준 message를
 * 화면에 그대로 노출하지 않고 여기의 문구를 사용한다.
 *
 * MEMBER-005~008(국적·이름·이미지·온보딩)의 문구는 그 오류에 도달하는 화면이
 * 아직 없어 비어 있다 — 프로필 편집·온보딩 폼(#232 ②)에서 함께 채운다.
 */
export default {
  member: {
    profile: {
      title: 'Profile',
      account: 'Account',
      preferences: 'Preferences',
      /** 이름 아래 한 줄. `{country}`는 국적 코드를 현재 언어의 나라 이름으로 옮긴 값이다. */
      from: 'From {country}',
      tabs: {
        saved: 'Saved',
        appointments: 'Appointments',
      },
      kinds: {
        events: 'Events',
        places: 'Places',
      },
      saved: {
        emptyEvents: 'Nothing saved yet. Tap the heart on an event to keep it here.',
        emptyPlaces: 'Nothing saved yet. Tap the heart on a place to keep it here.',
      },
      appointments: {
        emptyEvents: 'No event appointments yet.',
        emptyPlaces: 'No place appointments yet.',
      },
      currency: {
        label: 'Currency',
        /** 온보딩을 마치기 전에는 통화가 비어 있을 수 있다. */
        notSet: 'Not set',
      },
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
