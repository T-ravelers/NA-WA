<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { IconHeart } from '@tabler/icons-vue'

import AppBadge from '@/shared/ui/AppBadge.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import CategoryDot from '@/shared/ui/CategoryDot.vue'
import ImagePlaceholder from '@/shared/ui/ImagePlaceholder.vue'
import type { Category } from '@/shared/ui/category'

import { useExploreItemLikeMutation } from '../composables/useExploreItemLikeMutation'
import { normalizePlaceKind, type PlaceKind, type PlaceSummary } from '../model/placeExplore'
import { findExploreRegionLabelKey } from '../model/exploreRegions'

interface Props {
  place: PlaceSummary
}

const { place } = defineProps<Props>()
const emit = defineEmits<{ open: [placeId: number] }>()
const { t } = useI18n()
const likeMutation = useExploreItemLikeMutation()
const saved = computed(() => place.saved)

const normalizedKind = computed<PlaceKind>(() => normalizePlaceKind(place.placeKind))

const kindLabel = computed(() => t(`explore.placeKinds.${normalizedKind.value}`))

const category = computed<Category>(() => {
  if (normalizedKind.value === 'BEAUTY') return 'beauty'
  if (normalizedKind.value === 'MARKET') return 'shopping'
  if (normalizedKind.value === 'RESTAURANT' || normalizedKind.value === 'CAFE') return 'food'
  return 'show'
})

const categoryLabel = computed(() => t(`explore.categories.${category.value}`))

const regionLabel = computed(() =>
  [place.region1, place.region2, place.region3]
    .filter((value): value is string => Boolean(value))
    .map((value) => {
      const labelKey = findExploreRegionLabelKey(value)
      return labelKey ? t(labelKey) : value
    })
    .join(' · '),
)

const subtitle = computed(() => [place.brand, place.branch].filter(Boolean).join(' · '))

const optionBadges = computed(() => {
  const options: Array<{ key: string; selected: boolean | null | undefined; label: string }> = [
    {
      key: 'reservation',
      selected: place.reservable,
      label: t('explore.placeOptions.reservation'),
    },
    { key: 'takeout', selected: place.takeoutAvailable, label: t('explore.placeOptions.takeout') },
    { key: 'restroom', selected: place.hasRestroom, label: t('explore.placeOptions.restroom') },
  ]

  return options.filter((option) => option.selected === true)
})

function openPlace(): void {
  emit('open', place.itemId)
}

function toggleSaved(): void {
  if (likeMutation.isPending.value) return
  likeMutation.mutate({ itemId: place.itemId, saved: !place.saved })
}
</script>

<template>
  <AppCard padding="none">
    <article
      class="flex gap-0"
      role="article"
    >
      <button
        type="button"
        class="w-24 shrink-0 p-3 text-left"
        :aria-label="t('explore.openPlace', { name: place.name })"
        @click="openPlace"
      >
        <img
          v-if="place.thumbnailUrl"
          :src="place.thumbnailUrl"
          :alt="place.name"
          class="size-full rounded-sm object-cover"
          loading="lazy"
        />
        <div
          v-else
          class="relative flex aspect-square items-center justify-center overflow-hidden rounded-sm border border-dashed border-hairline-strong bg-surface-2"
        >
          <ImagePlaceholder :label="t('explore.imageUnavailable')" />
          <!-- 카드가 시안 밀도로 낮아지면서 자리표시가 72px가 됐다. 캡션을 더 내리고 한 단계
               줄여야 가운데 아이콘과 겹치지 않는다. -->
          <span class="absolute inset-x-0 bottom-1 text-center text-micro text-ink-2">
            {{ t('explore.placePhoto') }}
          </span>
        </div>
      </button>

      <div class="flex min-w-0 flex-1 flex-col gap-1 py-3 pr-3">
        <div class="flex items-center justify-between gap-2">
          <span
            class="flex min-w-0 items-center gap-1 truncate text-micro uppercase tracking-wide text-ink-2"
          >
            <CategoryDot :category="category" />
            {{ categoryLabel }} · {{ kindLabel }}
          </span>
          <button
            type="button"
            class="flex size-11 shrink-0 items-center justify-center text-ink-3"
            :aria-label="saved ? t('explore.unsavePlace') : t('explore.savePlace')"
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

        <button
          type="button"
          class="line-clamp-2 text-left text-title-sm text-ink"
          @click="openPlace"
        >
          {{ place.name }}
        </button>

        <p
          v-if="subtitle"
          class="line-clamp-1 text-body-sm text-ink-2"
        >
          {{ subtitle }}
        </p>

        <div class="mt-1 flex flex-col gap-1 text-caption text-ink-3">
          <span v-if="regionLabel">{{ regionLabel }}</span>
          <div
            v-if="optionBadges.length > 0"
            class="flex flex-wrap gap-1"
          >
            <AppBadge
              v-for="badge in optionBadges"
              :key="badge.key"
              tone="neutral"
            >
              {{ badge.label }}
            </AppBadge>
          </div>
        </div>
      </div>
    </article>
  </AppCard>
</template>
