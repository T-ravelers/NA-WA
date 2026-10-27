<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { IconHeart } from '@tabler/icons-vue'

import { formatCalendarDateString } from '@/shared/lib/datetime'
import { vFitTextGroup } from '@/shared/lib/fitText'
import AppButton from '@/shared/ui/AppButton.vue'
import AppImage from '@/shared/ui/AppImage.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import CategoryDot from '@/shared/ui/CategoryDot.vue'
import IconOrb from '@/shared/ui/IconOrb.vue'
import ImagePlaceholder from '@/shared/ui/ImagePlaceholder.vue'
import type { Category } from '@/shared/ui/category'

import { useExploreItemLikeMutation } from '../composables/useExploreItemLikeMutation'
import type { EventStatus, EventSummary } from '../model/eventExplore'

interface Props {
  event: EventSummary
}

const { event } = defineProps<Props>()
const emit = defineEmits<{
  open: [eventId: number]
  addToTrip: [eventId: number]
  findBuddy: [eventId: number]
}>()
const { t } = useI18n()
const likeMutation = useExploreItemLikeMutation()
const saved = computed(() => event.saved)

/**
 * 상태 색.
 *
 * 시안은 상태를 칩이 아니라 점 하나와 글자로 말한다. 카드가 목록에 여러 장 쌓이는
 * 자리라 칩이 줄마다 서면 시선을 뺏는다. 색만으로 말하지 않도록 글자는 그대로 둔다.
 *
 * 글자색과 점색을 한 표에 둔다. 나눠 두면 상태를 하나 늘릴 때 한쪽만 고치게 된다.
 *
 * 종료는 `ink-3`가 아니라 `ink-2`다. 아래 기간 줄이 `ink-3` 12px인데 종료까지 같은
 * 값이면 점 하나 말고는 구분되는 것이 없어 상태가 캡션에 섞인다.
 * 카드 바탕(`surface-1` #262626) 위 대비는 진행 중 6.4:1 · 예정 8.6:1 · 종료 6.5:1이다.
 */
const STATUS_STYLE: Record<EventStatus, { text: string; dot: string }> = {
  ONGOING: { text: 'text-status-ongoing', dot: 'bg-status-ongoing' },
  SCHEDULED: { text: 'text-status-scheduled', dot: 'bg-status-scheduled' },
  ENDED: { text: 'text-ink-2', dot: 'bg-ink-2' },
}

const statusStyle = computed(() => STATUS_STYLE[event.status])

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

/**
 * 운영 기간.
 *
 * `isPermanent`는 "상시 운영"이 아니라 **종료일을 받지 못했다**는 뜻이다. DB 제약이
 * `end_date IS NULL`과 이 값을 묶어 둬서, 적재가 종료일을 못 채우면 축제·팝업·콘서트도
 * 상시가 된다(로컬 시드 74건 전부가 그런 종류다). 그래서 "상시"라고 적으면 거짓을
 * 단언한다. 끝을 모른다는 사실만 그대로 적는다.
 *
 * 시작일조차 없으면 적을 것이 없어 이 줄을 비운다. 기간이 있는 이벤트에서 한쪽 날짜만
 * 있으면 구분자 없이 그 날짜만 보인다.
 */
const periodLabel = computed(() => {
  const start = formatCalendarDateString(event.startDate)
  if (event.isPermanent) {
    return start ? t('explore.detail.openEndedPeriod', { date: start }) : ''
  }
  return [start, formatCalendarDateString(event.endDate)].filter(Boolean).join(' ~ ')
})

function openEvent(): void {
  emit('open', event.itemId)
}

function toggleSaved(): void {
  if (likeMutation.isPending.value) return
  likeMutation.mutate({ itemId: event.itemId, saved: !event.saved })
}
</script>

<template>
  <AppCard padding="none">
    <article class="flex flex-col">
      <!--
        버튼 줄을 뺀 위쪽 전체가 상세로 가는 자리다. 카드 전체를 `role="link"`로 두면 그 안에
        버튼이 들어가 링크 안에 버튼이 놓이므로, 누를 자리는 이 영역의 클릭으로 받고 키보드와
        스크린 리더에는 제목 버튼이 같은 일을 한다.
      -->
      <div
        data-testid="event-card-body"
        class="flex cursor-pointer gap-0 transition-transform motion-reduce:transition-none active:scale-[0.98] motion-reduce:active:scale-100"
        @click="openEvent"
      >
        <!--
          `self-start`가 없으면 이 칸이 오른쪽 글자 칸 높이만큼 늘어나 사진 아래가 빈다.
          최소 높이를 걷어낸 뒤로는 그 높이가 제목 줄 수에 따라 달라진다.
        -->
        <div class="w-24 shrink-0 self-start p-3">
          <!--
            `aspect-square`로 못박는다. `size-full`만 두면 높이를 글자 칸이 정해서 카드마다
            사진 비율이 달라지고, 자리표시 갈래(`aspect-square`)와도 모양이 어긋난다.
          -->
          <AppImage
            :src="event.thumbnailUrl"
            :alt="event.title"
            class="aspect-square w-full rounded-sm object-cover"
            loading="lazy"
          >
            <div
              class="relative flex aspect-square items-center justify-center overflow-hidden rounded-sm border border-dashed border-hairline-strong bg-surface-2"
            >
              <ImagePlaceholder :label="t('explore.imageUnavailable')" />
              <!-- 카드가 시안 밀도로 낮아지면서 자리표시가 72px가 됐다. 캡션을 더 내리고 한 단계
                   줄여야 가운데 아이콘과 겹치지 않는다. -->
              <span class="absolute inset-x-0 bottom-1 text-center text-micro text-ink-2">{{
                t('explore.eventPhoto')
              }}</span>
            </div>
          </AppImage>
        </div>

        <div class="flex min-w-0 flex-1 flex-col gap-1 py-3 pr-3">
          <div class="flex min-w-0 items-center gap-2 text-micro">
            <span
              class="flex min-w-0 items-center gap-1 truncate uppercase tracking-wide text-ink-2"
            >
              <CategoryDot :category="categoryKey" />
              {{ categoryLabel }} · {{ kindLabel }}
            </span>
            <span
              data-testid="event-status"
              class="flex shrink-0 items-center gap-1.5 font-medium"
              :class="statusStyle.text"
            >
              <span
                aria-hidden="true"
                class="size-1.5 shrink-0 rounded-pill"
                :class="statusStyle.dot"
              />
              {{ statusLabel }}
            </span>
          </div>

          <h2 class="text-title-sm text-ink">
            <button
              type="button"
              class="line-clamp-2 text-left"
              @click.stop="openEvent"
            >
              {{ event.title }}
            </button>
          </h2>

          <span
            v-if="periodLabel"
            class="text-caption text-ink-3"
            >{{ periodLabel }}</span
          >
        </div>
      </div>

      <div
        v-fit-text-group
        class="flex min-w-0 items-center gap-2 px-3 pb-3"
      >
        <IconOrb
          :label="saved ? t('explore.unsaveEvent') : t('explore.saveEvent')"
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
            :aria-label="t('explore.addItemToTrip', { name: event.title })"
            @click="emit('addToTrip', event.itemId)"
          >
            {{ t('explore.detail.addToJourney') }}
          </AppButton>
        </div>
        <div class="min-w-0 flex-1">
          <AppButton
            block
            compact
            dense
            variant="secondary"
            class="whitespace-nowrap border-success text-success"
            :aria-label="t('explore.findBuddyForItem', { name: event.title })"
            @click="emit('findBuddy', event.itemId)"
          >
            {{ t('explore.detail.findCompanions') }}
          </AppButton>
        </div>
      </div>
    </article>
  </AppCard>
</template>
