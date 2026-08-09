<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { IconHeart } from '@tabler/icons-vue'

import AppBadge from '@/shared/ui/AppBadge.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import CategoryDot from '@/shared/ui/CategoryDot.vue'
import ImagePlaceholder from '@/shared/ui/ImagePlaceholder.vue'
import type { Category } from '@/shared/ui/category'

import { PLACE_KINDS, type PlaceKind, type PlaceSummary } from '../model/placeExplore'

interface Props {
  place: PlaceSummary
}

const { place } = defineProps<Props>()
const emit = defineEmits<{ open: [placeId: number] }>()
const { t } = useI18n()

const normalizedKind = computed<PlaceKind>(() =>
  PLACE_KINDS.includes(place.placeKind as PlaceKind) ? (place.placeKind as PlaceKind) : 'ETC',
)

const kindLabel = computed(() => t(`explore.placeKinds.${normalizedKind.value}`))

const category = computed<Category>(() => {
  if (normalizedKind.value === 'BEAUTY') return 'beauty'
  if (normalizedKind.value === 'MARKET') return 'shopping'
  if (normalizedKind.value === 'RESTAURANT' || normalizedKind.value === 'CAFE') return 'food'
  return 'show'
})

const categoryLabel = computed(() => t(`explore.categories.${category.value}`))

const regionLabel = computed(() =>
  [place.region1, place.region2, place.region3].filter(Boolean).join(' · '),
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
</script>

<template>
  <AppCard padding="none">
    <article
      class="flex min-h-36 gap-0"
      role="article"
    >
      <button
        type="button"
        class="w-28 shrink-0 p-3 text-left"
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
          <span class="absolute inset-x-0 bottom-3 text-center text-caption text-ink-2">
            {{ t('explore.placePhoto') }}
          </span>
        </div>
      </button>

      <div class="flex min-w-0 flex-1 flex-col gap-2 p-4">
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
            :aria-label="t('explore.savePlaceUnavailable')"
            disabled
          >
            <IconHeart
              :size="21"
              :stroke-width="1.8"
              aria-hidden="true"
            />
          </button>
        </div>

        <button
          type="button"
          class="line-clamp-2 text-left text-title text-ink"
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

        <div class="mt-auto flex flex-col gap-1 text-caption text-ink-3">
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
