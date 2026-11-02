<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { IconHeart } from '@tabler/icons-vue'

import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import AppImage from '@/shared/ui/AppImage.vue'
import CategoryDot from '@/shared/ui/CategoryDot.vue'
import IconOrb from '@/shared/ui/IconOrb.vue'
import ImagePlaceholder from '@/shared/ui/ImagePlaceholder.vue'
import type { Category } from '@/shared/ui/category'

import { useExploreItemLikeMutation } from '../composables/useExploreItemLikeMutation'
import { formatFavoriteCount } from '../model/favoriteCount'
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
const favoriteCountLabel = computed(() => formatFavoriteCount(place.favoriteCount))

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
    <!--
      카드는 두 묶음이다. 왼쪽은 사진과 하트, 오른쪽은 글자와 두 버튼이다. 버튼이 글자 칸
      안에만 있어서 어느 언어로 길어지든 글자 시작점보다 왼쪽으로 나가지 않는다(#584).

      버튼과 하트를 뺀 카드 어디를 눌러도 상세로 간다. 카드 전체를 `role="link"`로 두면 링크
      안에 버튼이 놓이므로 클릭으로만 받고, 키보드와 스크린 리더에는 제목 버튼이 같은 일을 한다.
      버튼·하트(`data-card-action`)를 누를 때는 카드가 눌린 것처럼 줄어들지 않는다.
    -->
    <article
      data-testid="place-card-body"
      class="grid cursor-pointer grid-cols-[6rem_minmax(0,1fr)] grid-rows-[auto_auto] gap-y-2 transition-transform motion-reduce:transition-none motion-safe:[&:active:not(:has([data-card-action]:active))]:scale-[0.98]"
      @click="openPlace"
    >
      <!--
        두 묶음은 카드의 윗줄(사진·글자)과 아랫줄(하트·버튼)을 함께 쓴다(subgrid). 좁은 화면에서
        버튼 글자가 여러 줄로 꺾여 버튼이 커져도 하트가 그 줄의 가운데에 남는다(#585 리뷰).
      -->
      <div class="row-span-2 grid grid-rows-subgrid p-3">
        <!--
          `aspect-square`로 못박는다. `size-full`만 두면 높이를 글자 칸이 정해서 카드마다
          사진 비율이 달라지고, 자리표시 갈래(`aspect-square`)와도 모양이 어긋난다.
        -->
        <AppImage
          :src="place.thumbnailUrl"
          :alt="place.name"
          class="aspect-square w-full rounded-sm object-cover"
          loading="lazy"
        >
          <div
            class="relative flex aspect-square w-full items-center justify-center overflow-hidden rounded-sm border border-dashed border-hairline-strong bg-surface-2"
          >
            <ImagePlaceholder :label="t('explore.imageUnavailable')" />
            <!-- 카드가 시안 밀도로 낮아지면서 자리표시가 72px가 됐다. 캡션을 더 내리고 한 단계
                 줄여야 가운데 아이콘과 겹치지 않는다. -->
            <span class="absolute inset-x-0 bottom-1 text-center text-micro text-ink-2">{{
              t('explore.placePhoto')
            }}</span>
          </div>
        </AppImage>
        <!--
          누르는 칸(34px)은 그대로 두고 아이콘(20px)의 왼쪽 끝을 사진 왼쪽 끝에 맞춘다.
          칸 안 여백의 절반(7px)만큼 당기며, 당긴 자리는 카드 안쪽 여백(12px) 안이다.
        -->
        <div
          data-card-action
          class="-ml-[7px] flex items-center self-center"
          @click.stop
        >
          <IconOrb
            size="sm"
            :label="saved ? t('explore.unsavePlace') : t('explore.savePlace')"
            :pressed="saved"
            @click="toggleSaved"
          >
            <IconHeart
              :size="20"
              :stroke-width="1.8"
              :class="saved ? 'fill-danger text-danger' : 'text-ink-3'"
              aria-hidden="true"
            />
          </IconOrb>
          <!-- 짧게 줄인 숫자(1.2K)는 소리로 읽으면 뜻이 흐려서, 읽는 쪽에는 원래 수를 문장으로 준다. -->
          <template v-if="favoriteCountLabel">
            <span
              class="-ml-1 text-caption text-ink-3"
              aria-hidden="true"
              >{{ favoriteCountLabel }}</span
            >
            <span class="sr-only">{{
              t('explore.favoriteCount', { count: place.favoriteCount })
            }}</span>
          </template>
        </div>
      </div>

      <div class="row-span-2 grid min-w-0 grid-rows-subgrid py-3 pr-3">
        <div class="flex min-w-0 flex-col gap-1">
          <span
            class="flex min-w-0 items-center gap-1 truncate text-micro uppercase tracking-wide text-ink-2"
          >
            <CategoryDot :category="category" />
            {{ categoryLabel }} · {{ kindLabel }}
          </span>
          <h2 class="text-title-sm text-ink">
            <!-- 카드가 이미 클릭을 받으므로 여기서 멈추지 않으면 두 번 연다. -->
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

        <div
          data-card-action
          class="flex min-w-0 gap-2 self-center"
          @click.stop
        >
          <div class="flex min-w-0 flex-1">
            <AppButton
              block
              mini
              class="h-full whitespace-nowrap text-on-paper"
              :aria-label="t('explore.addItemToTrip', { name: place.name })"
              @click="emit('addToTrip', place.itemId)"
            >
              {{ t('explore.placeDetail.addToJourney') }}
            </AppButton>
          </div>
          <div class="flex min-w-0 flex-1">
            <AppButton
              block
              mini
              variant="secondary"
              class="h-full whitespace-nowrap border-success text-success"
              :aria-label="t('explore.findBuddyForItem', { name: place.name })"
              @click="emit('findBuddy', place.itemId)"
            >
              {{ t('explore.placeDetail.findCompanions') }}
            </AppButton>
          </div>
        </div>
      </div>
    </article>
  </AppCard>
</template>
