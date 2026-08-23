import { VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from '@/app/App.vue'
import { i18n } from '@/app/i18n'
import { bootstrapLocale } from '@/app/i18n/applyLocale'
import { queryClient } from '@/app/query/client'
import { router } from '@/app/router'
import {
  handleSessionExpired,
  handleSignedOut,
  handleSignOutBarrier,
} from '@/app/session/sessionHandlers'
import {
  useMyOngoingAppointmentsQuery,
  useMyTodayAppointmentsQuery,
} from '@/features/appointment/composables/useMyOngoingAppointmentsQuery'
import { appointmentJourneyIntegrationKey } from '@/features/appointment/model/journeyIntegration'
import { appointmentExploreIntegrationKey } from '@/features/appointment/model/exploreIntegration'
import { appointmentMemberIntegrationKey } from '@/features/appointment/model/memberIntegration'
import { addJourneyItem, checkJourneyItemExists } from '@/features/journey/api/journeyApi'
import { useJourneyListQuery } from '@/features/journey/composables/useJourneyListQuery'
import { parseJourneyRouteQuery } from '@/features/journey/model/journeyRouteQuery'
import { memberAppointmentIntegrationKey } from '@/features/member/model/appointmentIntegration'
import { memberExploreIntegrationKey } from '@/features/member/model/exploreIntegration'
import { useMemberAppointmentProfile } from '@/features/member/model/memberQueries'
import { useExploreItemLocationQuery } from '@/features/explore/model/appointmentIntegration'
import { useSavedExploreItemsQuery } from '@/features/explore/model/memberIntegration'
import { exploreJourneyIntegrationKey } from '@/features/explore/model/journeyIntegration'
import { journeyReportIntegrationKey } from '@/features/journey/model/reportIntegration'
import { useReportSummariesQuery } from '@/features/report/composables/useReportQueries'
import { walletAppointmentIntegrationKey } from '@/features/wallet/model/appointmentIntegration'
import '@/app/styles/index.css'
import { setSessionExpiredHandler } from '@/shared/api/sessionRecovery'
import { setSignedOutHandler } from '@/shared/api/sessionSignOut'
import { subscribeSignOutBarrier } from '@/shared/api/signOutBarrier'
import { assertApiBaseUrlConfigured } from '@/shared/config/apiBaseUrl'

// 설정이 빠진 채로 뜨면 모든 요청이 조용히 앱 셸 HTML을 받는다. 화면을 그리기 전에 끊는다.
assertApiBaseUrlConfigured(import.meta.env.VITE_API_BASE_URL)

/**
 * 세션이 끊겼을 때의 앱 동작을 등록한다.
 *
 * shared 계층은 router와 feature를 import하지 않으므로 app에서 주입한다.
 */
setSessionExpiredHandler(handleSessionExpired)
setSignedOutHandler(handleSignedOut)
subscribeSignOutBarrier((active) => {
  if (active) {
    handleSignOutBarrier()
  }
})

bootstrapLocale()

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(VueQueryPlugin, { queryClient })
app.use(i18n)
app.provide(exploreJourneyIntegrationKey, {
  useJourneyListQuery,
  addJourneyItem,
  parseJourneyRouteQuery,
})
app.provide(appointmentMemberIntegrationKey, {
  useMemberStats: useMemberAppointmentProfile,
})
app.provide(appointmentJourneyIntegrationKey, {
  useJourneyListQuery,
  checkJourneyItemExists,
})
app.provide(journeyReportIntegrationKey, { useReportSummariesQuery })
app.provide(walletAppointmentIntegrationKey, { useMyTodayAppointmentsQuery })
app.provide(appointmentExploreIntegrationKey, { useItemLocation: useExploreItemLocationQuery })
app.provide(memberExploreIntegrationKey, { useSavedItems: useSavedExploreItemsQuery })
app.provide(memberAppointmentIntegrationKey, {
  // 프로필의 약속 탭은 지난 약속까지 본다. 지갑 QR 결제가 쓰는 기본 범위와 다르다.
  useMyAppointments: (enabled) => useMyOngoingAppointmentsQuery(enabled, 'ALL'),
})

app.mount('#app')
