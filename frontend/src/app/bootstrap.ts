import { createPinia } from 'pinia'
import { createApp } from 'vue'

import App from './App.vue'
import { router } from './router'

export function createNaWaApp() {
  const app = createApp(App)

  app.use(createPinia())
  app.use(router)

  return app
}

export function bootstrapApp(selector = '#app') {
  return createNaWaApp().mount(selector)
}
