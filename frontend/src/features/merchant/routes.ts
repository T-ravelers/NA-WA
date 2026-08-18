import type { RouteRecordRaw } from 'vue-router'

import { MERCHANT_HOME_PATH } from '@/shared/config/routePaths'

/**
 * 가맹점 화면.
 *
 * 화면이 하나뿐이라 route도 하나다. 등록·QR 생성·매출을 한 화면에서 처리한다.
 *
 * `hideBottomNav`로 손님용 하단 탭을 감춘다. 가맹점은 explore·journey로 갈 수 없으므로
 * 탭을 그려 봐야 전부 막힌 링크가 된다.
 */
const routes: RouteRecordRaw[] = [
  {
    path: MERCHANT_HOME_PATH,
    name: 'merchant',
    component: () => import('./views/MerchantView.vue'),
    meta: { requiresAuth: true, hideBottomNav: true },
  },
]

export default routes
