import { VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from '@/app/App.vue'
import { i18n } from '@/app/i18n'
import { queryClient } from '@/app/query/client'
import { router } from '@/app/router'
import '@/app/styles/index.css'
import { clearAuthSession } from '@/features/auth/model/authQueries'
import { setSessionExpiredHandler } from '@/shared/api/sessionRecovery'
import { SIGN_IN_PATH } from '@/shared/config/routePaths'

/**
 * 세션이 완전히 끊겼을 때의 동작을 등록한다.
 *
 * shared 계층은 router와 feature를 import하지 않으므로 app에서 주입한다.
 */
setSessionExpiredHandler(() => {
  clearAuthSession()

  void router.replace({
    path: SIGN_IN_PATH,
    query: { returnPath: router.currentRoute.value.fullPath },
  })
})

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(VueQueryPlugin, { queryClient })
app.use(i18n)

app.mount('#app')
