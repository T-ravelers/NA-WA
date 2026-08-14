<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { IconHeart } from '@tabler/icons-vue'

import AppBadge from '@/shared/ui/AppBadge.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import CategoryDot from '@/shared/ui/CategoryDot.vue'
import ImagePlaceholder from '@/shared/ui/ImagePlaceholder.vue'
import type { Category } from '@/shared/ui/category'

import type { EventSummary } from '../model/eventExplore'
import { findExploreRegionLabelKey } from '../model/exploreRegions'
import { useSavedEventsStore } from '../model/savedEvents'

interface Props {
  event: EventSummary
}

const { event } = defineProps<Props>()
const emit = defineEmits<{ open: [eventId: number] }>()
const { t } = useI18n()
const savedEvents = useSavedEventsStore()
const saved = computed(() => savedEvents.isSaved(event.itemId))

const statusTone = computed(() => {
  if (event.status === 'ONGOING') return 'ongoing'
  if (event.status === 'SCHEDULED') return 'scheduled'
  return 'neutral'
})

const statusLabel = computed(() => t(`explore.statuses.${event.status}`))
const kindLabel = computed(() => t(`explore.eventKinds.${event.eventKind}`))

const categoryLabel = computed(() => {
  if (event.eventKind === 'POPUP') return t('explore.categories.shopping')
  if (event.eventKind === 'FESTIVAL') return t('explore.categories.show')
  if (event.eventKind === 'CONCERT' || event.eventKind === 'EXHIBITION')
    return t('explore.categories.show')
  return t('explore.categories.other')
})

const categoryKey = computed<Category>(() => {
  if (event.eventKind === 'POPUP') return 'shopping'
  if (event.eventKind === 'FESTIVAL') return 'food'
  return 'show'
})

const regionLabel = computed(() =>
  [event.region1, event.region2, event.region3]
    .filter((value): value is string => Boolean(value))
    .map((value) => {
      const labelKey = findExploreRegionLabelKey(value)
      return labelKey ? t(labelKey) : value
    })
    .join(' · '),
)

function formatDate(value: string | null): string {
  return value ? value.replace(/-/g, '.') : ''
}

// 한쪽 날짜만 있으면 구분자 없이 그 날짜만 보인다. `EventDetailView`와 같은 방식이다.
const periodLabel = computed(() =>
  [formatDate(event.startDate), formatDate(event.endDate)].filter(Boolean).join(' ~ '),
)

function openEvent(): void {
  emit('open', event.itemId)
}

function toggleSaved(): void {
  savedEvents.toggle(event.itemId)
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key !== 'Enter' && event.key !== ' ') return
  event.preventDefault()
  openEvent()
}
</script>

<template>
  <AppCard padding="none">
    <article
      class="flex min-h-36 cursor-pointer gap-0"
      role="link"
      tabindex="0"
      @click="openEvent"
      @keydown="handleKeydown"
    >
      <div class="w-28 shrink-0 p-3">
        <img
          v-if="event.thumbnailUrl"
          :src="event.thumbnailUrl"
          :alt="event.title"
          class="size-full rounded-sm object-cover"
          loading="lazy"
        />
        <div
          v-else
          class="relative flex aspect-square items-center justify-center overflow-hidden rounded-sm border border-dashed border-hairline-strong bg-surface-2"
        >
          <ImagePlaceholder :label="t('explore.imageUnavailable')" />
          <span class="absolute inset-x-0 bottom-3 text-center text-caption text-ink-2">{{
            t('explore.eventPhoto')
          }}</span>
        </div>
      </div>

      <div class="flex min-w-0 flex-1 flex-col gap-2 p-4">
        <div class="flex items-center justify-between gap-2">
          <span
            class="flex min-w-0 items-center gap-1 truncate text-micro uppercase tracking-wide text-ink-2"
          >
            <CategoryDot :category="categoryKey" />
            {{ categoryLabel }} · {{ kindLabel }}
          </span>
          <button
            type="button"
            class="flex size-11 shrink-0 items-center justify-center text-ink-3"
            :aria-label="saved ? 'Remove event from saved' : 'Save event'"
            :aria-pressed="saved"
            @click.stop="toggleSaved"
          >
            <IconHeart
              :size="21"
              :stroke-width="1.8"
              :class="saved ? 'fill-danger text-danger' : ''"
              aria-hidden="true"
            />
          </button>
        </div>

        <h2 class="line-clamp-2 text-title text-ink">
          {{ event.title }}
        </h2>

        <p
          v-if="event.subtitle"
          class="line-clamp-1 text-body-sm text-ink-2"
        >
          {{ event.subtitle }}
        </p>

        <div class="mt-auto flex flex-col gap-1 text-caption text-ink-3">
          <span v-if="regionLabel">{{ regionLabel }}</span>
          <span v-if="periodLabel">{{ periodLabel }}</span>
          <AppBadge
            :tone="statusTone"
            dot
            class="self-start"
            >{{ statusLabel }}</AppBadge
          >
        </div>
      </div>
    </article>
  </AppCard>
</template>
