<script setup lang="ts">
import { IconCalendar, IconCompass, IconRoute, IconUser, IconWallet } from '@tabler/icons-vue'
import type { Component } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute } from 'vue-router'

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
 * 라벨 텍스트 없이 아이콘만 노출하므로 접근 가능한 이름을 별도로 붙인다.
 * 화면이 준비되지 않은 탭은 담당 feature가 route를 추가할 때 `to`를 채운다.
 */
const items: NavItem[] = [
  { key: 'home', icon: IconCompass, labelKey: 'nav.home', to: '/explore' },
  { key: 'calendar', icon: IconCalendar, labelKey: 'nav.calendar', to: null },
  { key: 'profile', icon: IconUser, labelKey: 'nav.profile', to: null },
  { key: 'wallet', icon: IconWallet, labelKey: 'nav.wallet', to: '/wallet' },
  { key: 'journey', icon: IconRoute, labelKey: 'nav.journey', to: null },
]

function isActive(item: NavItem): boolean {
  return item.to !== null && route.path.startsWith(item.to)
}
</script>

<template>
  <nav
    class="fixed inset-x-0 bottom-0 z-10 mx-auto w-full max-w-[390px] px-4 pb-[env(safe-area-inset-bottom)]"
    :aria-label="t('nav.label')"
  >
    <ul
      class="mb-4 flex h-14 items-center justify-around rounded-pill bg-nav-surface shadow-raised"
    >
      <li
        v-for="item in items"
        :key="item.key"
      >
        <RouterLink
          v-if="item.to !== null"
          :to="item.to"
          class="flex size-11 items-center justify-center"
          :aria-label="t(item.labelKey)"
          :aria-current="isActive(item) ? 'page' : undefined"
        >
          <component
            :is="item.icon"
            :size="32"
            :stroke-width="1.75"
            :class="isActive(item) ? 'text-ink' : 'text-icon-muted'"
          />
        </RouterLink>
        <span
          v-else
          class="flex size-11 items-center justify-center opacity-40"
          :aria-label="`${t(item.labelKey)} — ${t('nav.comingSoon')}`"
          role="img"
        >
          <component
            :is="item.icon"
            :size="32"
            :stroke-width="1.75"
            class="text-icon-muted"
          />
        </span>
      </li>
    </ul>
  </nav>
</template>
