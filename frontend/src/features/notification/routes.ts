import type { RouteRecordRaw } from 'vue-router'

/**
 * 알림 화면 라우트
 */

const routes: RouteRecordRaw[] = [
  /**
   * 알림 목록
   *
   * 지갑 홈의 벨에서 들어온다
   * 화면에 들어가는 순간 전부 읽음으로 바꾸므로 배지는 비워진 채로 되돌아간다
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
