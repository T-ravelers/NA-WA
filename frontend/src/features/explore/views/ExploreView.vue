<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import StateEmpty from '@/shared/ui/StateEmpty.vue'

const { t } = useI18n()
</script>

<template>
  <section class="px-screen py-8">
    <h1 class="font-display text-screen-title uppercase">Explore</h1>
    <!-- 목록·검색·필터는 후속 PR에서 GET /api/v1/explore/events에 연결한다. -->
    <StateEmpty :description="t('state.empty.description')" />
  </section>
</template>
