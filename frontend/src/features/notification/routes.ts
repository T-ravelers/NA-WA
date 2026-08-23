import type { RouteRecordRaw } from 'vue-router'

/**
 * 알림 화면 라우트
 */

const routes: RouteRecordRaw[] = [
  /**
   * 알림 목록
   *
   * 지갑 홈의 벨에서 들어온다
   * 화면에 들어온 것만으로는 읽음이 아니다. 항목을 누르거나 "모두 읽음"을 눌러야 읽음이
   * 되므로, 목록만 열어 본 사용자의 벨 숫자는 그대로 남는다
   * 항목을 누르면 그 알림이 가리키는 정산 상세로 이동한다
   */
  {
    path: '/notifications',
    name: 'notifications',
    component: () => import('./views/NotificationListView.vue'),
    meta: { requiresAuth: true },
  },
]

export default routes
