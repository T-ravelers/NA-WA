<script setup lang="ts">
import { IconChartPie, IconCompass, IconRoute, IconUserCircle, IconWallet } from '@tabler/icons-vue'
import type { Component } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'

import { vFitText } from '@/shared/lib/fitText'

interface NavItem {
  key: string
  icon: Component
  labelKey: string
  /** 화면이 아직 없는 탭은 `null`이고 비활성으로 그린다. */
  to: string | null
}

const { t } = useI18n()
const route = useRoute()

/**
 * 하단 탭.
 *
 * 순서·조형은 V2 시안(`OnFZL1uKGfyEuyyBn1Oawb`, `2360:4325`)이다. 아이콘 아래에 라벨을
 * 함께 그리므로 접근 가능한 이름은 라벨 글자가 맡는다 — `aria-label`을 따로 붙이면
 * 스크린 리더가 같은 말을 두 번 읽는다.
 *
 * 화면이 준비되지 않은 탭은 담당 feature가 route를 추가할 때 `to`를 채운다.
 */
const items: NavItem[] = [
  { key: 'journey', icon: IconRoute, labelKey: 'nav.journey', to: '/journeys' },
  { key: 'explore', icon: IconCompass, labelKey: 'nav.explore', to: '/explore' },
  { key: 'wallet', icon: IconWallet, labelKey: 'nav.wallet', to: '/wallet' },
  { key: 'report', icon: IconChartPie, labelKey: 'nav.report', to: '/reports' },
  { key: 'my', icon: IconUserCircle, labelKey: 'nav.my', to: '/profile' },
]

function isActive(item: NavItem): boolean {
  return item.to !== null && route.path.startsWith(item.to)
}
</script>

<template>
  <!--
    시안의 탭 바는 화면 폭을 꽉 채우고 아래로 붙는다. 안쪽 목록만 셸 폭으로 좁혀
    넓은 뷰포트에서도 아이콘이 본문과 같은 폭 안에 남게 한다.
  -->
  <nav
    class="fixed inset-x-0 bottom-0 z-10 bg-nav-surface pb-[calc(1rem+env(safe-area-inset-bottom))]"
    :aria-label="t('nav.label')"
  >
    <!--
      탭 하나는 시안대로 64px이되, 그보다 좁아지면 같이 줄어든다. 폭을 고정하면 폴더블
      커버(280)에서 다섯 칸이 화면을 넘어 마지막 탭이 잘린다.

      좁아진 칸에서 라벨은 자르지 않고 줄인다(#356). 280·ja의 `ウォレット`·`マイページ`가
      실제로 `…`에 걸렸고, 잘린 탭 이름은 어디로 가는 탭인지 알려 주지 못한다.
    -->
    <ul class="mx-auto flex w-full max-w-shell items-start justify-center px-2 pt-5">
      <li
        v-for="item in items"
        :key="item.key"
        class="max-w-16 min-w-0 flex-1"
      >
        <RouterLink
          v-if="item.to !== null"
          :to="item.to"
          class="flex w-full flex-col items-center gap-0.5"
          :aria-current="isActive(item) ? 'page' : undefined"
        >
          <component
            :is="item.icon"
            :size="24"
            :stroke-width="1.75"
            :class="isActive(item) ? 'text-on-paper' : 'text-on-paper-2'"
            aria-hidden="true"
          />
          <span
            v-fit-text="0.8"
            class="max-w-full truncate text-micro"
            :class="isActive(item) ? 'text-on-paper' : 'font-normal text-on-paper-2'"
            >{{ t(item.labelKey) }}</span
          >
        </RouterLink>
        <span
          v-else
          class="flex w-full flex-col items-center gap-0.5 opacity-40"
          :aria-label="`${t(item.labelKey)} — ${t('nav.comingSoon')}`"
          role="img"
        >
          <component
            :is="item.icon"
            :size="24"
            :stroke-width="1.75"
            class="text-on-paper-2"
            aria-hidden="true"
          />
          <span
            v-fit-text="0.8"
            class="max-w-full truncate text-micro font-normal text-on-paper-2"
            >{{ t(item.labelKey) }}</span
          >
        </span>
      </li>
    </ul>
  </nav>
</template>
