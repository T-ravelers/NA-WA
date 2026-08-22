<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { vFitText } from '@/shared/lib/fitText'
import AppButton from '@/shared/ui/AppButton.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'
import TextInput from '@/shared/ui/TextInput.vue'

import AppointmentListCard from '../components/AppointmentListCard.vue'
import { type AppointmentItemType, type AppointmentListFilters } from '../api/appointmentApi'
import { useAppointmentListQuery } from '../composables/useAppointmentListQuery'
import {
  defaultListLanguage,
  type AppointmentLanguageFilter,
} from '../model/appointmentListLanguage'

const route = useRoute()
const router = useRouter()
const { locale, t } = useI18n()

const keyword = ref('')
// 기본은 회원이 고른 언어다. 방한 외국인이 알아들을 수 있는 약속이 먼저 보여야
// 하는데, 목록 전체를 보여주면 대부분이 못 알아듣는 언어로 채워진다.
//
// 고른 칩은 저장하지 않는다. 들어올 때는 언제나 회원 언어에서 시작하고, 다른 언어를
// 보는 것은 이 화면에 머무는 동안의 일이다. 다만 그동안은 자동 되돌림을 멈춰야 해서
// (아래 watch) 직접 골랐는지만 화면 안에서 기억한다.
const userChoseLanguage = ref(false)
const selectedLanguage = ref<AppointmentLanguageFilter>(defaultListLanguage(locale.value))

function chooseLanguage(next: AppointmentLanguageFilter): void {
  selectedLanguage.value = next
  userChoseLanguage.value = true
}

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

const languageOptions: AppointmentLanguageFilter[] = ['ALL', 'en', 'ja', 'zh-TW', 'vi']

/**
 * 자동으로 채운 언어로 걸러 아무것도 없으면 전체로 되돌린다.
 *
 * 사용자가 고르지 않은 조건 때문에 빈 화면을 보여주면, 약속이 없는 것인지 걸러진
 * 것인지 구분되지 않는다.
 *
 * 직접 고른 언어에서는 되돌리지 않는다. 고른 조건을 화면이 임의로 풀면 방금 누른
 * 칩과 목록이 어긋난다. 검색어가 있을 때도 두는데, 그때 빈 결과의 이유는 검색어일
 * 수 있어서다.
 */
watch(
  () => [appointmentQuery.isSuccess.value, appointments.value.length] as const,
  ([isSuccess, count]) => {
    if (userChoseLanguage.value || selectedLanguage.value === 'ALL') return
    if (!isSuccess || count > 0 || keyword.value.trim() !== '') return

    selectedLanguage.value = 'ALL'
  },
  { immediate: true },
)

function goBack(): void {
  if (itemId.value !== undefined && itemType.value === 'EVENT') {
    void router.push({
      name: 'explore-event-detail',
      params: { eventId: itemId.value },
    })
    return
  }

  if (itemId.value !== undefined && itemType.value === 'PLACE') {
    void router.push({
      name: 'explore-place-detail',
      params: { placeId: itemId.value },
    })
    return
  }

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
  <main class="flex min-h-dvh w-full flex-col gap-8 px-screen pb-28 pt-6">
    <header class="flex items-center gap-3">
      <button
        type="button"
        class="flex size-11 shrink-0 items-center justify-center rounded-pill bg-surface-1 text-ink"
        :aria-label="t('action.back')"
        @click="goBack"
      >
        <span aria-hidden="true">‹</span>
      </button>
      <h1
        v-fit-text
        class="min-w-0 flex-1 truncate font-display text-section-header text-ink-display"
      >
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
        @click="chooseLanguage(language)"
      >
        {{
          language === 'ALL'
            ? t('appointment.languages.all')
            : t(`appointment.languages.${language}`)
        }}
      </button>
    </div>

    <section
      class="flex flex-1 flex-col gap-5"
      aria-labelledby="appointment-list-heading"
    >
      <div class="flex items-center justify-between gap-4">
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

    <div
      class="fixed inset-x-0 bottom-0 z-20 mx-auto w-full max-w-[390px] bg-canvas/95 px-screen py-3 backdrop-blur"
    >
      <AppButton
        block
        @click="goToCreate"
      >
        {{ t('appointment.list.create') }}
      </AppButton>
    </div>
  </main>
</template>
