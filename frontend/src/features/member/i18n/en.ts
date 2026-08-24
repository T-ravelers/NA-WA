/**
 * 회원 문구.
 *
 * `errorCode`는 백엔드 MemberErrorCode와 1:1로 맞춘다. 서버가 내려준 message를
 * 화면에 그대로 노출하지 않고 여기의 문구를 사용한다.
 *
 * MEMBER-005~008(국적·이름·이미지·온보딩)은 프로필 편집·온보딩 폼이 도달하는 오류다.
 * 폼이 같은 조건을 먼저 검사하지만 서버 판정이 정본이므로 문구를 둘 다 둔다.
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
        label: 'Type',
        events: 'Events',
        places: 'Places',
      },
      list: {
        showMore: 'Show more',
      },
      saved: {
        emptyEvents: 'Nothing saved yet. Tap the heart on an event to keep it here.',
        emptyPlaces: 'Nothing saved yet. Tap the heart on a place to keep it here.',
        limitNotice: 'Profile shows up to 30 saved items.',
        openDiscover: 'See the complete list in Explore',
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
    form: {
      editTitle: 'Edit profile',
      onboardingTitle: 'Welcome',
      onboardingLead: 'Tell us your name and where you are from. You can change both later.',
      name: 'Name',
      namePlaceholder: 'The name other travelers will see',
      photoHint: 'Your photo comes from the account you signed in with.',
      nationality: 'Nationality',
      nationalityPlaceholder: 'Choose a country',
      save: 'Save',
      start: 'Start',
      cancel: 'Cancel',
      error: {
        nameRequired: 'Enter a name.',
        nameTooLong: 'Use {max} characters or fewer.',
        countryRequired: 'Choose a country.',
        /** 서버가 알려 준 코드가 없거나 문구가 없는 코드일 때의 일반 안내. */
        saveFailed: 'We could not save your profile. Please try again.',
      },
    },
    errorCode: {
      'MEMBER-001': 'We could not find your account. Please sign in again.',
      'MEMBER-002': 'That language is not supported yet.',
      'MEMBER-003': 'That currency is not supported yet.',
      'MEMBER-004': 'There was nothing to change.',
      'MEMBER-005': 'We do not support that country yet.',
      'MEMBER-006': 'That name cannot be used. Try a shorter one.',
      'MEMBER-007': 'That photo address is not valid.',
      'MEMBER-008': 'Fill in every field to finish setting up.',
    },
  },
}
