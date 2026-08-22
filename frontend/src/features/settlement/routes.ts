import type { RouteRecordRaw } from 'vue-router'

/**
 * 정산 화면 라우트
 *
 * 모든 화면이 `hideBottomNav`로 하단 탭을 감춘다.
 *
 * 하단 탭은 Explore·Report·Profile·Wallet·Journey 다섯 곳으로 가는 길이고, 정산은 그중
 * 어디도 아니다. 정산으로 들어오는 문은 지갑 홈 하나뿐이라 탭을 그려 두면 "여기가 탭 중
 * 한 곳"이라는 잘못된 신호를 준다. 실제로 누르면 하던 일이 사라지는데, Start Split처럼
 * 여러 단계를 거치는 화면에서는 적어 둔 참여자와 금액이 통째로 날아간다.
 *
 * 지갑도 같은 규칙이다 — 홈만 탭을 두고 충전·QR·거래 내역은 전부 감춘다.
 */

const routes: RouteRecordRaw[] = [
  /**
   * 정산 홈
   *
   * To Pay(내가 낼 것)과 To Collect(받을 것)를 한 화면에서 보여준다
   * 어느 쪽을 보고 있는지는 `query.side`에 남긴다
   * 상세·전체 내역·결제가 같은 쿼리를 주고받아 되돌아올 때 같은 쪽을 연다
   */
  {
    path: '/settlements',
    name: 'settlements',
    component: () => import('./views/SettlementListView.vue'),
    meta: { requiresAuth: true, hideBottomNav: true },
  },
  /**
   * 정산 요청 생성
   *
   * 결제 내역 고르기 -> 방식과 참여자 정하기 -> 확인 3단계로 주소 변경 없이 한 화면에서 진행한다
   * 1/n이면 금액을 입력하지 않는다
   * 품목별일 때만 항목과 단가를 적는다
   */
  {
    path: '/settlements/new',
    name: 'settlement-new',
    component: () => import('./views/SettlementRequestView.vue'),
    meta: { requiresAuth: true, hideBottomNav: true },
  },
  /**
   * 정산 전체 내역
   *
   * 목록 화면에서 넘어온 `query.side` 한쪽(To Pay 또는 To Collect)의 완료된 정산을 모두 보여준다
   * 목록 화면이 이미 받아 둔 응답을 걸러 쓴다
   * 따라서 서버를 다시 부르지 않는다
   */
  {
    path: '/settlements/history',
    name: 'settlement-history',
    component: () => import('./views/SettlementHistoryView.vue'),
    meta: { requiresAuth: true, hideBottomNav: true },
  },
  /**
   * 정산 한 건 상세
   *
   * `query.side`로 어느 목록에서 들어왔는지 받아 뒤로가기에 되돌려준다
   */
  {
    path: '/settlements/:settlementId',
    name: 'settlement-detail',
    component: () => import('./views/SettlementDetailView.vue'),
    meta: { requiresAuth: true, hideBottomNav: true },
  },
  /**
   * 정산 요청 완료 화면
   *
   * 정산 요청을 완료하면 이 화면으로 이동한다
   * 서버가 `viewer.role`을 `CREATOR`로 인정한 경우에만 완료를 표시한다
   */
  {
    path: '/settlements/:settlementId/requested',
    name: 'settlement-requested',
    component: () => import('./views/SettlementRequestedView.vue'),
    meta: { requiresAuth: true, hideBottomNav: true },
  },
  /**
   * 결제 진행 화면
   *
   * 새로고침해도 세션에 남은 멱등키로 중복 결제를 방지한다
   * 이체를 곧바로 실행하는 것은 상세의 Pay 버튼이 히스토리 상태로 진입 의사를 실어
   * 보낸 경우뿐이고, 주소로 직접 열면 확인을 한 번 받는다
   */
  {
    path: '/settlements/:settlementId/pay',
    name: 'settlement-pay',
    component: () => import('./views/SettlementPayView.vue'),
    meta: { requiresAuth: true, hideBottomNav: true },
  },
  /**
   * 결제 완료 화면
   */
  {
    path: '/settlements/:settlementId/pay/complete',
    name: 'settlement-pay-complete',
    component: () => import('./views/SettlementPayCompleteView.vue'),
    meta: { requiresAuth: true, hideBottomNav: true },
  },
]

export default routes
