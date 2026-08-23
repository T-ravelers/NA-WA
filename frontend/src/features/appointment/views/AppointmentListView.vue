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

import AppointmentDepositSheet from '../components/AppointmentDepositSheet.vue'
import AppointmentJourneySelectSheet from '../components/AppointmentJourneySelectSheet.vue'
import AppointmentListCard from '../components/AppointmentListCard.vue'
import {
  type AppointmentItemType,
  type AppointmentLanguage,
  type AppointmentListFilters,
  type AppointmentSummary,
} from '../api/appointmentApi'
import { useAppointmentListQuery } from '../composables/useAppointmentListQuery'
import {
  useAppointmentJoinFlow,
  type AppointmentJoinTarget,
} from '../composables/useAppointmentJoinFlow'

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
// 끝난 약속은 목록에서 뺀다. 참여할 수도, 새로 할 일도 없는 카드가 목록을 채우면
// 지금 갈 수 있는 약속이 그만큼 밀린다. 서버 status 필터는 값 하나만 받아
// "완료만 빼기"를 표현하지 못하므로 받은 쪽에서 거른다.
const appointments = computed(() =>
  (appointmentQuery.data.value?.content ?? []).filter(
    (appointment) => appointment.appointmentStatus !== 'COMPLETED',
  ),
)
// 목록은 5초마다 다시 조회한다. 그 주기마다 실패할 기회도 함께 생기므로, 이미
// 받아 둔 목록이 있으면 지우지 않고 그대로 둔다. 오류 화면은 보여줄 카드가 아예
// 없을 때만 띄우고, 다음 조회가 성공하면 조용히 되돌아온다.
const listLoadFailed = computed(
  () => appointmentQuery.isError.value && appointmentQuery.data.value === undefined,
)
const title = computed(() =>
  itemType.value === 'PLACE'
    ? t('appointment.list.titlePlace')
    : itemType.value === 'EVENT'
      ? t('appointment.list.titleEvent')
      : t('appointment.list.title'),
)

const languageOptions: Array<'ALL' | AppointmentLanguage> = ['ALL', 'en', 'ja', 'zh-TW', 'vi']

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

/**
 * 카드의 Join. 상세 화면의 참여 버튼과 같은 흐름을 목록에서 그대로 연다.
 *
 * 목록은 "내가 이미 참여했는지"를 모른다 — 응답에 없고, 카드마다 따로 물어보면 폴링
 * 주기마다 카드 수만큼 요청이 나간다. 그래서 미리 막지 않고 서버 판정에 맡긴다.
 * 이미 참여한 약속이면 서버가 APPOINTMENT-003으로 돌려보내고 시트가 그 이유를 보여준다.
 */
const listQuery = computed(() => ({
  ...(itemId.value === undefined ? {} : { itemId: String(itemId.value) }),
  ...(itemType.value === undefined ? {} : { itemType: itemType.value }),
}))

const JOIN_TARGET_KEY = 'joinAppointmentId'

const joinFlow = useAppointmentJoinFlow({
  returnRoute: (target) => ({
    name: 'appointment-list',
    query: { ...listQuery.value, [JOIN_TARGET_KEY]: String(target.appointmentId) },
  }),
  resumeMarker: (target, tripId) => ({
    ...route.query,
    [JOIN_TARGET_KEY]: String(target.appointmentId),
    tripId: String(tripId),
  }),
})

watch(() => joinFlow.joinMutation.error.value, joinFlow.handleJoinError)

function toJoinTarget(appointment: AppointmentSummary): AppointmentJoinTarget {
  return {
    appointmentId: appointment.appointmentId,
    appointmentName: appointment.appointmentName,
    depositAmount: appointment.depositAmount,
    activityStartAt: appointment.activityStartAt,
  }
}

function startJoin(appointment: AppointmentSummary): void {
  joinFlow.open(toJoinTarget(appointment))
}

/**
 * 여정을 만들거나 충전하고 돌아온 경우(`?joinAppointmentId=&tripId=`). 어느 약속이었는지
 * 표시로 남겨 두므로 그 카드의 시트를 다시 연다.
 *
 * 이 표시는 사용자가 시트에서 행동하면 지운다. 남겨 두면 "이 화면을 열 때마다 시트를
 * 열라"는 상시 지시가 되어, 참여가 끝나 목록이 갱신되기만 해도 시트가 혼자 다시 뜬다.
 */
const resumeTripId = computed(() => readPositiveInteger(route.query.tripId))
const resumeAppointmentId = computed(() => readPositiveInteger(route.query[JOIN_TARGET_KEY]))

watch(
  () =>
    [
      resumeTripId.value,
      resumeAppointmentId.value,
      appointments.value,
      // 여정 목록도 소스다. 시트가 열려야 조회가 시작되므로(`useJourneyListQuery`는
      // `journeySelectOpen`을 enabled로 받는다) `resume`이 불리는 그 시점에는 아직
      // 목록이 없다. 이 소스가 빠지면 목록이 도착해도 watch를 다시 깨울 것이 없어
      // "골라 둔 채로 보여 준다"가 빈 선택으로 끝난다. 상세와 같은 구성이다.
      joinFlow.journeyListQuery.data.value,
    ] as const,
  ([tripId, appointmentId, list]) => {
    if (appointmentId === undefined) return
    // 여정을 만들지 않고 뒤로 온 경우. 표시만 남고 이어서 열 것이 없으니 주소를
    // 정리한다 — 남겨 두면 `consumeJoinMarker`가 영영 불리지 않아 계속 붙어 다닌다.
    if (tripId === undefined) {
      consumeJoinMarker()
      return
    }

    const found = list.find((appointment) => appointment.appointmentId === appointmentId)
    if (found === undefined) return

    joinFlow.resume(toJoinTarget(found), tripId)
  },
  { immediate: true },
)

function consumeJoinMarker(): void {
  if (resumeTripId.value === undefined && resumeAppointmentId.value === undefined) return

  const rest = { ...route.query }
  delete rest.tripId
  delete rest[JOIN_TARGET_KEY]
  void router.replace({ path: route.path, query: rest })
}

function closeJourneySelect(): void {
  joinFlow.closeJourneySelect()
  consumeJoinMarker()
}

function selectJourney(tripId: number): void {
  joinFlow.selectJourney(tripId)
  consumeJoinMarker()
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
      class="flex flex-1 flex-col gap-5"
      aria-labelledby="appointment-list-heading"
    >
      <div class="flex items-center justify-between gap-4">
        <h2
          id="appointment-list-heading"
          class="text-title text-ink"
        >
          {{ t('appointment.list.resultCount', { count: appointments.length }) }}
        </h2>
      </div>

      <StateLoading
        v-if="appointmentQuery.isPending.value"
        :label="t('state.loading')"
      />

      <StateError
        v-else-if="listLoadFailed"
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
          <AppointmentListCard
            :appointment="appointment"
            @join="startJoin"
          />
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

    <AppointmentJourneySelectSheet
      v-if="joinFlow.journeySelectOpen.value"
      :journeys="joinFlow.journeyListQuery.data.value ?? []"
      :selected-journey-id="joinFlow.selectedTripId.value"
      :loading="joinFlow.journeyListQuery.isPending.value"
      :error-message="joinFlow.journeyListErrorMessage.value"
      :selection-error="joinFlow.journeySelectionError.value"
      :empty-message="t('appointment.journeySelect.emptyForJoin')"
      @close="closeJourneySelect"
      @select="selectJourney"
      @create-journey="joinFlow.goToCreateJourney"
    />

    <AppointmentDepositSheet
      v-if="joinFlow.depositSheetOpen.value && joinFlow.target.value !== null"
      :appointment-name="joinFlow.target.value.appointmentName"
      :deposit-amount="joinFlow.target.value.depositAmount"
      :confirm-disabled="joinFlow.joinMutation.isPending.value"
      :error-message="joinFlow.joinErrorMessage.value"
      @close="joinFlow.closeDepositSheet"
      @confirm="joinFlow.confirmJoin"
    />

    <div
      v-if="joinFlow.topupPromptOpen.value"
      class="fixed inset-0 z-50 flex items-center justify-center bg-scrim/70 px-screen"
    >
      <section
        role="dialog"
        aria-modal="true"
        :aria-label="t('appointment.create.insufficientTitle')"
        class="w-full max-w-[390px] rounded-card bg-surface-1 p-5 shadow-sheet"
      >
        <h2 class="text-title text-ink-display">
          {{ t('appointment.create.insufficientTitle') }}
        </h2>
        <p class="mt-2 text-body-sm text-ink-3">
          {{
            t('appointment.create.insufficientDescription', {
              amount: joinFlow.formattedDepositAmount.value,
            })
          }}
        </p>
        <div class="mt-5 grid grid-cols-2 gap-3">
          <AppButton
            block
            variant="secondary"
            @click="joinFlow.closeTopupPrompt"
          >
            {{ t('appointment.create.insufficientLater') }}
          </AppButton>
          <AppButton
            block
            @click="joinFlow.goToTopup"
          >
            {{ t('appointment.create.insufficientTopup') }}
          </AppButton>
        </div>
      </section>
    </div>
  </main>
</template>
