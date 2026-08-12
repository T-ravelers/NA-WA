/**
 * 인증 문구.
 *
 * `errorCode`는 백엔드 AuthErrorCode(AUTH-001~AUTH-018)와 1:1로 맞춘다.
 * 서버가 내려준 message를 화면에 그대로 노출하지 않고 여기의 문구를 사용한다.
 */
export default {
  auth: {
    welcome: {
      /** 줄바꿈이 조판의 일부다. 로케일마다 끊는 위치가 달라질 수 있다. */
      headline: 'Your trip,\non record',
      body: 'Plan journeys in Korea, split costs with your crew, and see where every won went.',
      /** 티켓 위 표기는 워드마크와 같은 `NAWA`다. 문장 속 `app.name`(NA-WA)과 다르다. */
      passLabel: 'Boarding · NAWA',
      passTitle: 'Seoul & Beyond',
      passStamp: 'GO',
      start: 'Get started',
    },
    signIn: {
      title: 'Welcome to NA-WA',
      description: 'Sign in to plan trips and settle up with your travel mates.',
      google: 'Continue with Google',
      line: 'Continue with LINE',
      lineNotice: 'LINE sign-in is still being verified.',
      consent: 'By continuing you agree to the',
      terms: 'Terms of Service',
      privacy: 'Privacy Policy',
    },
    locale: {
      open: 'Change screen language',
      title: 'Language',
      /** 로그인 전에는 이 기기에만 남는다는 것을 알린다. */
      hint: 'Applies to this screen right away. We save it to your account after you sign in.',
      current: 'Screen language · {language}',
    },
    callback: {
      pending: 'Checking your sign-in',
      pendingBody:
        'One moment — we are confirming your account. No further input is needed on this screen.',
      failed: 'Sign-in did not finish',
      retry: 'Try signing in again',
    },
    signOut: 'Sign out',
    signOutBarrier: {
      title: 'We could not confirm that you signed out',
      description:
        'Your previous session is blocked on this device. Try signing out again or continue with a provider to sign in again.',
      retry: 'Try signing out again',
    },
    errorCode: {
      'AUTH-001': 'Your session has expired. Please sign in again.',
      'AUTH-002': 'Your session was ended for security reasons. Please sign in again.',
      'AUTH-003': 'Please sign in to continue.',
      'AUTH-004': 'You do not have access to this page.',
      'AUTH-005': 'Your request could not be verified. Please try again.',
      'AUTH-006': 'This request came from an unrecognised address.',
      'AUTH-007': 'That sign-in method is not supported.',
      'AUTH-008': 'We could not return you to the previous page. Please start again.',
      'AUTH-009': 'That sign-in method is temporarily unavailable.',
      'AUTH-010': 'Sign-in did not complete. Please try again.',
      'AUTH-011': 'The sign-in provider is not responding. Please try again shortly.',
      'AUTH-012': 'Sign-in did not complete. Please try again.',
      'AUTH-013': 'We could not verify your account. Please try again.',
      'AUTH-014': 'This sign-in link is no longer valid. Please start again.',
      'AUTH-015': 'Sign-in was cancelled or refused.',
      'AUTH-016': 'This account is suspended. Please contact support.',
      'AUTH-017': 'This account has been withdrawn.',
      'AUTH-018': 'We could not finish setting up your account. Please try again.',
    },
  },
}
