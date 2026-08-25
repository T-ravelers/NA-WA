<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, type RouteLocationRaw } from 'vue-router'
import { IconMapPin, IconUser } from '@tabler/icons-vue'

import CategoryChip from '@/shared/ui/CategoryChip.vue'
import type { Category } from '@/shared/ui/category'

import type { JourneyTimelineItem } from '../api/journeyApi'
import { useJourneyAppointmentIntegration } from '../model/appointmentIntegration'
import { categoryLabelKey } from '../model/journeyCategory'
import { initialsOf } from '../model/journeyDetailPresentation'
import JourneyCategoryBloom from './JourneyCategoryBloom.vue'

interface Props {
  item: JourneyTimelineItem
  category: Category
  detailTo: RouteLocationRaw
  detailName: string
  location: string | null
  distanceKm?: number | null
  large?: boolean
}

const {
  item,
  category,
  detailTo,
  detailName,
  location,
  distanceKm = null,
  large = false,
} = defineProps<Props>()
const { locale, t } = useI18n()

const appointmentId = computed(() => item.appointment?.appointmentId ?? null)
const { useAppointmentMembersQuery } = useJourneyAppointmentIntegration()
const membersQuery = useAppointmentMembersQuery(appointmentId)
const activeMembers = computed(() =>
  (membersQuery.data.value ?? []).filter((member) => member.membershipStatus === 'ACTIVE'),
)
const visibleMembers = computed(() => activeMembers.value.slice(0, 3))

const CARD_TONE: Record<Category, string> = {
  beauty: 'bg-beauty-soft',
  shopping: 'bg-shopping-soft',
  show: 'bg-show-soft',
  food: 'bg-food-soft',
}

function formatDistance(value: number): string {
  return new Intl.NumberFormat(locale.value, {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  }).format(value)
}
</script>

<template>
  <div>
    <RouterLink
      :to="detailTo"
      :aria-label="detailName"
      class="relative block overflow-hidden rounded-md px-4 py-3.5 text-on-paper"
      :class="[CARD_TONE[category], large ? 'h-30' : 'h-19.5']"
    >
      <div class="relative z-10 min-w-0">
        <!-- 우상단 삭제 버튼(44px)이 얹히는 자리를 제목에서 비워 둔다. -->
        <h4 class="truncate pr-10 text-title-sm">{{ item.exploreItem.title }}</h4>
        <div class="mt-1.5 flex min-w-0 items-center gap-1.5">
          <CategoryChip
            :category="category"
            :label="t(categoryLabelKey(category))"
            size="sm"
          />
          <!--
            시안은 소비영역 칩만 두지만, 그러면 사용자가 이벤트와 장소를 구분할 수 없다.
            둘은 담는 곳도 상세 화면도 다르므로 유형은 남긴다(#537).
          -->
          <span class="shrink-0 text-caption text-on-paper-2">
            {{
              item.exploreItem.itemType === 'EVENT'
                ? t('journey.detail.event')
                : t('journey.detail.place')
            }}
          </span>
          <span
            v-if="location !== null"
            class="truncate text-caption text-on-paper-2"
          >
            <span
              aria-hidden="true"
              class="pr-1.5"
              >·</span
            >{{ location }}
          </span>
        </div>

        <div
          v-if="large && visibleMembers.length > 0"
          class="mt-4 flex items-center gap-1.5"
        >
          <span
            v-for="(member, index) in visibleMembers"
            :key="member.appointmentMemberId"
            class="inline-flex h-8 shrink-0 items-center gap-1.5 rounded-pill px-1.5 pr-3 text-micro"
            :class="index === 0 ? 'bg-paper-fill text-on-paper' : 'bg-on-paper text-paper'"
          >
            <span
              class="flex size-5.5 shrink-0 items-center justify-center rounded-pill text-micro"
              :class="index === 0 ? 'bg-on-paper text-paper' : 'bg-surface-3 text-ink'"
            >
              {{ initialsOf(member.displayName) }}
            </span>
            <span class="whitespace-nowrap">{{ member.displayName }}</span>
          </span>
        </div>
      </div>

      <JourneyCategoryBloom
        :category="category"
        :size="large ? 'lg' : 'sm'"
      />
    </RouterLink>

    <div
      v-if="distanceKm !== null || activeMembers.length > 0"
      class="mt-2 flex items-center gap-3 text-caption text-ink-2"
    >
      <span
        v-if="distanceKm !== null"
        class="inline-flex items-center gap-1"
      >
        <IconMapPin
          :size="14"
          aria-hidden="true"
        />
        {{ t('journey.detail.distanceKm', { distance: formatDistance(distanceKm) }) }}
      </span>
      <span
        v-if="activeMembers.length > 0"
        class="inline-flex items-center gap-1"
      >
        <IconUser
          :size="14"
          aria-hidden="true"
        />
        {{ t('journey.detail.personCount', { count: activeMembers.length }) }}
      </span>
    </div>
  </div>
</template>
