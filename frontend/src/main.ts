import { VueQueryPlugin } from '@tanstack/vue-query'
import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from '@/app/App.vue'
import { i18n } from '@/app/i18n'
import { bootstrapLocale } from '@/app/i18n/applyLocale'
import { queryClient } from '@/app/query/client'
import { router } from '@/app/router'
import '@/app/styles/index.css'
import { clearMemberProfile } from '@/features/member/model/memberQueries'
import { setSessionExpiredHandler } from '@/shared/api/sessionRecovery'
import { AUTH_CALLBACK_PATH, SIGN_IN_PATH } from '@/shared/config/routePaths'

/** 이미 인증 화면에 있으면 다시 보내지 않는다. */
const AUTH_FLOW_PATHS: string[] = [SIGN_IN_PATH, AUTH_CALLBACK_PATH]

/**
 * 세션이 완전히 끊겼을 때의 동작을 등록한다.
 *
 * shared 계층은 router와 feature를 import하지 않으므로 app에서 주입한다.
 */
setSessionExpiredHandler(() => {
  clearMemberProfile()

  const current = router.currentRoute.value

  // 로그인 화면에서 다시 로그인 화면으로 보내면 returnPath가 `/sign-in`이 된다.
  if (AUTH_FLOW_PATHS.includes(current.path)) {
    return
  }

  void router.replace({
    path: SIGN_IN_PATH,
    query: { returnPath: current.fullPath },
  })
})

bootstrapLocale()

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(VueQueryPlugin, { queryClient })
app.use(i18n)

app.mount('#app')
