<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import StateEmpty from '@/shared/ui/StateEmpty.vue'

const { t } = useI18n()
const router = useRouter()
</script>

<template>
  <section class="flex min-h-dvh items-center justify-center px-screen">
    <StateEmpty
      :title="t('notFound.title')"
      :description="t('notFound.description')"
      :action-label="t('action.goHome')"
      @action="router.replace('/')"
    />
  </section>
</template>
