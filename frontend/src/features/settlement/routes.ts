import type { RouteRecordRaw } from 'vue-router'

/**
 * 정산 화면 라우트
 */

const routes: RouteRecordRaw[] = [
  /**
   * 정산 홈
   *
   * To Pay(내가 낼 것)과 To Collect(받을 것)를 한 화면에서 보여준다
   */
  {
    path: '/settlements',
    name: 'settlements',
    component: () => import('./views/SettlementListView.vue'),
    meta: { requiresAuth: true },
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
    meta: { requiresAuth: true },
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
    meta: { requiresAuth: true },
  },
  /**
   * 정산 한 건 상세
   */
  {
    path: '/settlements/:settlementId',
    name: 'settlement-detail',
    component: () => import('./views/SettlementDetailView.vue'),
    meta: { requiresAuth: true },
  },
  /**
   * 정산 요청 완료 화면
   *
   * 정산 요청을 완료하면 이 화면으로 이동한다
   */
  {
    path: '/settlements/:settlementId/requested',
    name: 'settlement-requested',
    component: () => import('./views/SettlementRequestedView.vue'),
    meta: { requiresAuth: true },
  },
  /**
   * 결제 진행 화면
   *
   * 새로고침해도 세션에 남은 멱등키로 중복 결제를 방지한다
   */
  {
    path: '/settlements/:settlementId/pay',
    name: 'settlement-pay',
    component: () => import('./views/SettlementPayView.vue'),
    meta: { requiresAuth: true },
  },
  /**
   * 결제 완료 화면
   */
  {
    path: '/settlements/:settlementId/pay/complete',
    name: 'settlement-pay-complete',
    component: () => import('./views/SettlementPayCompleteView.vue'),
    meta: { requiresAuth: true },
  },
]

export default routes
