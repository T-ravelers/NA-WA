/**
 * 여러 feature와 공통 계층이 함께 참조하는 경로 상수.
 *
 * feature 내부에서만 쓰는 경로는 여기가 아니라 각 feature의 `routes.ts`에 둔다.
 * 이 파일에는 인증 흐름처럼 도메인 경계를 넘는 소수의 고정 경로만 남긴다.
 */

/** 처음 오는 사용자가 만나는 화면. */
export const WELCOME_PATH = '/'

/** 미인증 사용자를 보낼 화면. */
export const SIGN_IN_PATH = '/sign-in'

/**
 * OAuth 콜백 수신 경로.
 *
 * 백엔드 `AUTH_FRONTEND_SUCCESS_URL`, `AUTH_FRONTEND_FAILURE_URL`과 일치해야 한다.
 * 백엔드 설정을 함께 바꾸지 않고 이 값을 변경하면 로그인이 끊긴다.
 */
export const AUTH_CALLBACK_PATH = '/auth/callback'

/** 인증된 사용자의 기본 진입 화면. */
export const AUTHENTICATED_HOME_PATH = '/explore'
