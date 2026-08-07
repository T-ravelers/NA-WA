<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'

import BottomNav from '@/shared/ui/BottomNav.vue'

const route = useRoute()

/**
 * 하단 탭은 서비스 화면에서만 보여준다.
 *
 * 로그인, 콜백, 404와 상세 화면에서는 이동할 곳이 없거나
 * 상세 콘텐츠에 집중해야 하므로 감춘다.
 */
const showBottomNav = computed(
  () => route.meta.requiresAuth === true && route.meta.hideBottomNav !== true,
)
</script>

<template>
  <div
    class="mx-auto min-h-dvh w-full max-w-[390px] bg-canvas text-ink"
    :class="showBottomNav ? 'pb-24' : ''"
  >
    <RouterView />
    <BottomNav v-if="showBottomNav" />
  </div>
</template>
