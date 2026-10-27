<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { IconHeart } from '@tabler/icons-vue'

import { vFitTextGroup } from '@/shared/lib/fitText'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import AppImage from '@/shared/ui/AppImage.vue'
import CategoryDot from '@/shared/ui/CategoryDot.vue'
import IconOrb from '@/shared/ui/IconOrb.vue'
import ImagePlaceholder from '@/shared/ui/ImagePlaceholder.vue'
import type { Category } from '@/shared/ui/category'

import { useExploreItemLikeMutation } from '../composables/useExploreItemLikeMutation'
import { normalizePlaceKind, type PlaceKind, type PlaceSummary } from '../model/placeExplore'
import { findExploreRegionLabelKey } from '../model/exploreRegions'

interface Props {
  place: PlaceSummary
}

const { place } = defineProps<Props>()
const emit = defineEmits<{
  open: [placeId: number]
  addToTrip: [placeId: number]
  findBuddy: [placeId: number]
}>()
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
    <article class="flex flex-col">
      <!-- EventCard와 같은 구조다. 버튼 줄을 뺀 위쪽이 상세로 가는 자리이고, 키보드와
           스크린 리더에는 제목 버튼이 같은 일을 한다. -->
      <div
        data-testid="place-card-body"
        class="flex cursor-pointer gap-0 transition-transform motion-reduce:transition-none active:scale-[0.98] motion-reduce:active:scale-100"
        @click="openPlace"
      >
        <div class="w-24 shrink-0 self-start p-3">
          <!-- EventCard와 같은 이유다. 비율을 못박지 않으면 카드마다 사진 모양이 달라지고
               자리표시 갈래(`aspect-square`)와도 어긋난다. -->
          <AppImage
            :src="place.thumbnailUrl"
            :alt="place.name"
            class="aspect-square w-full rounded-sm object-cover"
            loading="lazy"
          >
            <div
              class="relative flex aspect-square items-center justify-center overflow-hidden rounded-sm border border-dashed border-hairline-strong bg-surface-2"
            >
              <ImagePlaceholder :label="t('explore.imageUnavailable')" />
              <!-- 카드가 시안 밀도로 낮아지면서 자리표시가 72px가 됐다. 캡션을 더 내리고 한 단계
                   줄여야 가운데 아이콘과 겹치지 않는다. -->
              <span class="absolute inset-x-0 bottom-1 text-center text-micro text-ink-2">
                {{ t('explore.placePhoto') }}
              </span>
            </div>
          </AppImage>
        </div>

        <div class="flex min-w-0 flex-1 flex-col gap-1 py-3 pr-3">
          <span
            class="flex min-w-0 items-center gap-1 truncate text-micro uppercase tracking-wide text-ink-2"
          >
            <CategoryDot :category="category" />
            {{ categoryLabel }} · {{ kindLabel }}
          </span>

          <h2 class="text-title-sm text-ink">
            <button
              type="button"
              class="line-clamp-2 text-left"
              @click.stop="openPlace"
            >
              {{ place.name }}
            </button>
          </h2>

          <!-- 장소에는 운영 기간이 없어 이벤트 카드의 기간 자리에 지역을 둔다. -->
          <span
            v-if="regionLabel"
            class="text-caption text-ink-3"
            >{{ regionLabel }}</span
          >
        </div>
      </div>

      <div
        v-fit-text-group
        class="flex min-w-0 items-center gap-2 px-3 pb-3"
      >
        <IconOrb
          :label="saved ? t('explore.unsavePlace') : t('explore.savePlace')"
          :pressed="saved"
          class="rounded-sm border border-hairline-strong"
          @click="toggleSaved"
        >
          <IconHeart
            :size="21"
            :stroke-width="1.8"
            :class="saved ? 'fill-danger text-danger' : 'text-ink-3'"
            aria-hidden="true"
          />
        </IconOrb>
        <div class="min-w-0 flex-1">
          <AppButton
            block
            compact
            dense
            class="whitespace-nowrap text-on-paper"
            :aria-label="t('explore.addItemToTrip', { name: place.name })"
            @click="emit('addToTrip', place.itemId)"
          >
            {{ t('explore.placeDetail.addToJourney') }}
          </AppButton>
        </div>
        <div class="min-w-0 flex-1">
          <AppButton
            block
            compact
            dense
            variant="secondary"
            class="whitespace-nowrap border-success text-success"
            :aria-label="t('explore.findBuddyForItem', { name: place.name })"
            @click="emit('findBuddy', place.itemId)"
          >
            {{ t('explore.placeDetail.findCompanions') }}
          </AppButton>
        </div>
      </div>
    </article>
  </AppCard>
</template>
