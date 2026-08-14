<script setup lang="ts">
import { IconChevronRight } from '@tabler/icons-vue'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

import AppBadge from '@/shared/ui/AppBadge.vue'
import AppTicket from '@/shared/ui/AppTicket.vue'
import ImagePlaceholder from '@/shared/ui/ImagePlaceholder.vue'
import TicketStamp from '@/shared/ui/TicketStamp.vue'

import { formatReportDate, type ReportJourneyOption } from '../model/reportModel'

interface Props {
  option: ReportJourneyOption
  selected?: boolean
  pending?: boolean
}

const { option, selected = false, pending = false } = defineProps<Props>()

const emit = defineEmits<{
  viewReport: [reportId: number]
  chooseJourney: [tripId: number]
}>()

const { t } = useI18n()

/** 0인 종류는 숨기고, 둘 다 0이면 카운트 줄 전체를 숨긴다. */
const itemCounts = computed(() => {
  const counts: string[] = []

  if (option.eventCount > 0) {
    counts.push(t('report.list.eventCount', { count: option.eventCount }))
  }

  if (option.placeCount > 0) {
    counts.push(t('report.list.placeCount', { count: option.placeCount }))
  }

  return counts
})

function activateReport(): void {
  if (option.report !== null) {
    emit('viewReport', option.report.reportId)
    return
  }

  emit('chooseJourney', option.tripId)
}
</script>

<template>
  <li>
    <AppTicket
      :body-size="154"
      tone="paper"
      :selected="selected"
    >
      <template #body>
        <ImagePlaceholder />

        <div class="absolute left-3 top-3">
          <AppBadge
            tone="neutral"
            dot
          >
            {{ t('report.list.ended') }}
          </AppBadge>
        </div>
      </template>

      <template #stub>
        <div class="flex min-h-48 flex-col gap-2 p-4">
          <h2 class="min-w-0 truncate font-display text-trip-ticket-title uppercase text-on-paper">
            {{ option.title }}
          </h2>

          <p class="text-body-sm font-medium tabular-nums text-on-paper">
            <time :datetime="option.startDate">{{ formatReportDate(option.startDate) }}</time>
            <span aria-hidden="true">–</span>
            <time :datetime="option.endDate">{{ formatReportDate(option.endDate) }}</time>
          </p>

          <p
            v-if="itemCounts.length > 0"
            class="text-caption font-semibold tabular-nums text-on-paper/65"
          >
            {{ itemCounts.join(' · ') }}
          </p>

          <p class="text-body-sm text-on-paper/65">
            {{ option.report === null ? t('report.list.notCreated') : t('report.list.ready') }}
          </p>

          <div class="mt-auto flex min-h-16 items-center justify-between gap-3">
            <button
              type="button"
              class="inline-flex min-h-11 min-w-0 items-center gap-1 rounded-sm px-0.5 text-left text-title-sm text-on-paper transition-transform active:scale-[0.98] disabled:pointer-events-none disabled:opacity-40"
              :aria-pressed="option.report === null ? selected : undefined"
              :disabled="option.report === null && pending"
              @click="activateReport"
            >
              <span class="truncate">
                {{
                  option.report === null
                    ? selected
                      ? t('report.list.choosingExpenses')
                      : t('report.list.chooseExpenses')
                    : t('report.list.view')
                }}
              </span>
              <IconChevronRight
                :size="20"
                :stroke-width="1.75"
                aria-hidden="true"
              />
            </button>

            <TicketStamp :label="t('report.list.ended')" />
          </div>
        </div>
      </template>
    </AppTicket>
  </li>
</template>
