<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'

import StateEmpty from '@/shared/ui/StateEmpty.vue'

const { t } = useI18n()
const router = useRouter()
</script>

<template>
  <section class="flex items-center justify-center px-screen flex-1 w-full">
    <StateEmpty
      :title="t('notFound.title')"
      :description="t('notFound.description')"
      :action-label="t('action.goHome')"
      @action="router.replace('/')"
    />
  </section>
</template>
