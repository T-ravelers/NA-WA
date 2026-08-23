/**
 * 약속 화면이 서버에 다시 물어보는 주기(ms).
 *
 * 서버는 이미 실시간이다. 목록·상세 응답의 `appointmentStatus`는 저장된 컬럼이
 * 아니라 조회하는 그 순간에 정원과 시각으로 다시 계산한 값이다(백엔드
 * `AppointmentService.resolveDisplayStatus`). 60초 주기 lifecycle 배치는 DB 컬럼을
 * 뒤늦게 따라잡을 뿐이라 화면 표시와는 무관하다. 그래서 실시간으로 만드는 데
 * 필요한 것은 푸시 경로가 아니라 "다시 물어보는 것"뿐이다.
 *
 * 5초인 이유는 이 화면들이 바뀌는 원인이 둘뿐이기 때문이다. 다른 사람의 참여·탈퇴는
 * 사람이 버튼을 누르는 속도라 몇 초 차이를 알아채기 어렵고, 시각 경과에 따른 상태
 * 전이는 활동 시작·종료 시각이 기준이라 애초에 분 단위 경계다. 더 짧게 잡으면 요청만
 * 배로 늘고 얻는 것이 없다 — 백엔드에도 nginx에도 rate limit이 없어서 그 요청은
 * 그대로 서버에 도달한다.
 *
 * 탭이 백그라운드로 가면 폴링은 멈추고(Vue Query `refetchIntervalInBackground`
 * 기본값 `false`), 탭으로 돌아오면 즉시 한 번 갱신된다(`refetchOnWindowFocus`
 * 기본값 `true`). 두 기본값에 기대고 있으니 끄지 않는다.
 */
export const APPOINTMENT_LIVE_REFETCH_INTERVAL_MS = 5_000
