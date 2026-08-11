<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppButton from '@/shared/ui/AppButton.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'
import TextInput from '@/shared/ui/TextInput.vue'

import AppointmentListCard from '../components/AppointmentListCard.vue'
import {
  type AppointmentItemType,
  type AppointmentLanguage,
  type AppointmentListFilters,
} from '../api/appointmentApi'
import { useAppointmentListQuery } from '../composables/useAppointmentListQuery'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const keyword = ref('')
const selectedLanguage = ref<'ALL' | AppointmentLanguage>('ALL')

function readPositiveInteger(value: unknown): number | undefined {
  const raw = Array.isArray(value) ? value[0] : value
  const parsed = Number(raw)

  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : undefined
}

function readItemType(value: unknown): AppointmentItemType | undefined {
  const raw = Array.isArray(value) ? value[0] : value

  return raw === 'EVENT' || raw === 'PLACE' ? raw : undefined
}

const itemId = computed(() => readPositiveInteger(route.query.itemId))
const itemType = computed(() => readItemType(route.query.itemType))

const filters = computed<AppointmentListFilters>(() => ({
  itemId: itemId.value,
  itemType: itemType.value,
  keyword: keyword.value.trim() || undefined,
  language: selectedLanguage.value === 'ALL' ? undefined : selectedLanguage.value,
  page: 0,
  size: 20,
}))

const appointmentQuery = useAppointmentListQuery(filters)
const appointments = computed(() => appointmentQuery.data.value?.content ?? [])
const title = computed(() =>
  itemType.value === 'PLACE'
    ? t('appointment.list.titlePlace')
    : itemType.value === 'EVENT'
      ? t('appointment.list.titleEvent')
      : t('appointment.list.title'),
)

const languageOptions: Array<'ALL' | AppointmentLanguage> = [
  'ALL',
  'en',
  'ja',
  'zh-CN',
  'zh-TW',
  'vi',
]

function goBack(): void {
  if (window.history.length > 1) {
    void router.back()
    return
  }

  void router.push({ name: 'explore' })
}

function goToCreate(): void {
  void router.push({
    name: 'appointment-create',
    query: {
      itemId: itemId.value,
      itemType: itemType.value,
    },
  })
}

function retry(): void {
  void appointmentQuery.refetch()
}
</script>

<template>
  <main class="flex min-h-dvh w-full flex-col gap-5 px-screen py-6">
    <header class="flex items-center gap-3">
      <button
        type="button"
        class="flex size-11 shrink-0 items-center justify-center rounded-pill bg-surface-1 text-ink"
        :aria-label="t('action.back')"
        @click="goBack"
      >
        <span aria-hidden="true">‹</span>
      </button>
      <h1 class="min-w-0 flex-1 truncate font-display text-section-header text-ink-display">
        {{ title }}
      </h1>
    </header>

    <TextInput
      v-model="keyword"
      type="search"
      :label="t('appointment.list.searchLabel')"
      :placeholder="t('appointment.list.searchPlaceholder')"
      label-hidden
    />

    <div
      class="flex gap-2 overflow-x-auto pb-1"
      :aria-label="t('appointment.list.languageLabel')"
      role="group"
    >
      <button
        v-for="language in languageOptions"
        :key="language"
        type="button"
        class="shrink-0 rounded-pill border px-4 py-2 text-caption transition-colors"
        :class="
          selectedLanguage === language
            ? 'border-paper-fill bg-paper-fill text-on-paper'
            : 'border-hairline-strong text-ink-2'
        "
        :aria-pressed="selectedLanguage === language"
        @click="selectedLanguage = language"
      >
        {{
          language === 'ALL'
            ? t('appointment.languages.all')
            : t(`appointment.languages.${language}`)
        }}
      </button>
    </div>

    <section
      class="flex flex-1 flex-col gap-3"
      aria-labelledby="appointment-list-heading"
    >
      <div class="flex items-center justify-between gap-3">
        <h2
          id="appointment-list-heading"
          class="text-title text-ink"
        >
          {{
            t('appointment.list.resultCount', {
              count: appointmentQuery.data.value?.totalElements ?? 0,
            })
          }}
        </h2>
        <AppButton
          compact
          @click="goToCreate"
        >
          {{ t('appointment.list.create') }}
        </AppButton>
      </div>

      <StateLoading
        v-if="appointmentQuery.isPending.value"
        :label="t('state.loading')"
      />

      <StateError
        v-else-if="appointmentQuery.isError.value"
        :title="t('appointment.list.loadFailed')"
        :description="t('appointment.list.loadFailedDescription')"
        :action-label="t('action.retry')"
        @retry="retry"
      />

      <StateEmpty
        v-else-if="appointments.length === 0"
        :title="t('appointment.list.emptyTitle')"
        :description="t('appointment.list.emptyDescription')"
        :action-label="t('appointment.list.create')"
        @action="goToCreate"
      />

      <ul
        v-else
        class="flex flex-col gap-3"
      >
        <li
          v-for="appointment in appointments"
          :key="appointment.appointmentId"
        >
          <AppointmentListCard :appointment="appointment" />
        </li>
      </ul>
    </section>
  </main>
</template>
