<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'

import { useEdgeSwipeHistory } from '@/shared/lib/edgeSwipeHistory'
import AppToastHost from '@/shared/ui/AppToastHost.vue'
import BottomNav from '@/shared/ui/BottomNav.vue'

const route = useRoute()

// 왼쪽 가장자리 스와이프로 뒤로 가기. 네이티브 제스처와 겹치지 않게 설치형 PWA에서만 켜진다(#381).
useEdgeSwipeHistory(useRouter())

/**
 * 하단 탭은 서비스 화면에서만 보여준다.
 *
 * 로그인, 콜백, 404에서는 이동할 곳이 없으므로 감춘다.
 */
const showBottomNav = computed(
  () => route.meta.requiresAuth === true && route.meta.hideBottomNav !== true,
)
</script>

<template>
  <div
    class="mx-auto min-h-dvh w-full max-w-shell bg-canvas text-ink"
    :class="showBottomNav ? 'pb-24' : ''"
  >
    <RouterView />
    <BottomNav v-if="showBottomNav" />
    <AppToastHost />
  </div>
</template>
