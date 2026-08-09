import { VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from '@/app/App.vue'
import { i18n } from '@/app/i18n'
import { bootstrapLocale } from '@/app/i18n/applyLocale'
import { queryClient } from '@/app/query/client'
import { router } from '@/app/router'
import { handleSessionExpired, handleSignedOut } from '@/app/session/sessionHandlers'
import '@/app/styles/index.css'
import { setSessionExpiredHandler } from '@/shared/api/sessionRecovery'
import { setSignedOutHandler } from '@/shared/api/sessionSignOut'
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

bootstrapLocale()

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(VueQueryPlugin, { queryClient })
app.use(i18n)

app.mount('#app')
