<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppButton from '@/shared/ui/AppButton.vue'
import AppPagination from '@/shared/ui/AppPagination.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'
import TextInput from '@/shared/ui/TextInput.vue'
import ScreenHeader from '@/shared/ui/ScreenHeader.vue'

import AppointmentDepositSheet from '../components/AppointmentDepositSheet.vue'
import AppointmentJourneySelectSheet from '../components/AppointmentJourneySelectSheet.vue'
import AppointmentListCard from '../components/AppointmentListCard.vue'
import {
  type AppointmentItemType,
  type AppointmentListFilters,
  type AppointmentSummary,
} from '../api/appointmentApi'
import { useAppointmentListQuery } from '../composables/useAppointmentListQuery'
import {
  useAppointmentJoinFlow,
  type AppointmentJoinTarget,
} from '../composables/useAppointmentJoinFlow'
import {
  defaultListLanguage,
  type AppointmentLanguageFilter,
} from '../model/appointmentListLanguage'

const route = useRoute()
const router = useRouter()
const { locale, t } = useI18n()

const languageOptions: AppointmentLanguageFilter[] = ['ALL', 'en', 'ja', 'zh-TW', 'vi']

function readLanguage(value: unknown): AppointmentLanguageFilter | undefined {
  const raw = Array.isArray(value) ? value[0] : value

  return languageOptions.find((option) => option === raw)
}

// 기본은 회원이 고른 언어다. 방한 외국인이 알아들을 수 있는 약속이 먼저 보여야
// 하는데, 목록 전체를 보여주면 대부분이 못 알아듣는 언어로 채워진다.
//
// 칩을 직접 누른 언어만 주소(`?language=`)에 적는다. 그래서 주소에 언어가 있으면 직접
// 고른 것이고, 없으면 회원 언어에서 시작한다 — Explore에서 처음 들어오는 길은 언제나
// 회원 언어다. "전체"도 `ALL`로 적어야 "아직 안 골랐다"와 구분된다.
const chosenLanguage = computed(() => readLanguage(route.query.language))
// 고르지 않았을 때의 언어. 0건이라 전체로 넓히는 것(아래 watch)은 사용자가 고른 것이
// 아니므로 주소에 적지 않고 여기서만 바꾼다. 다시 들어오면 처음부터 다시 판단한다.
const automaticLanguage = ref<AppointmentLanguageFilter>(defaultListLanguage(locale.value))
const selectedLanguage = computed(() => chosenLanguage.value ?? automaticLanguage.value)

function chooseLanguage(next: AppointmentLanguageFilter): void {
  const rest = { ...route.query }
  delete rest.page
  void router.replace({ path: route.path, query: { ...rest, language: next } })
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

// 0부터 센다(`?page=1`이 두 번째 페이지). Explore 목록과 같은 규칙이다.
function readPageIndex(value: unknown): number {
  const raw = Array.isArray(value) ? value[0] : value
  const parsed = Number(raw)

  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : 0
}

function readKeyword(value: unknown): string {
  const raw = Array.isArray(value) ? value[0] : value

  return typeof raw === 'string' ? raw.trim() : ''
}

const itemId = computed(() => readPositiveInteger(route.query.itemId))
const itemType = computed(() => readItemType(route.query.itemType))
// 페이지는 주소에 둔다. 참여하러 나갔다 돌아오거나 새로 고쳐도 보던 페이지가 남는다.
const page = computed(() => readPageIndex(route.query.page))
// 검색어도 주소에 둔다(페이지와 같은 이유). 검색칸에 치는 글자는 따로 두고, 타자가
// 멈춘 뒤에만 주소로 옮긴다 — 글자마다 주소를 고치면 그때마다 서버에 다시 묻는다.
const keyword = computed(() => readKeyword(route.query.keyword))
const keywordInput = ref(keyword.value)

const filters = computed<AppointmentListFilters>(() => ({
  itemId: itemId.value,
  itemType: itemType.value,
  keyword: keyword.value || undefined,
  language: selectedLanguage.value === 'ALL' ? undefined : selectedLanguage.value,
  page: page.value,
  size: 20,
}))

const appointmentQuery = useAppointmentListQuery(filters)
// 끝난 약속은 서버가 LIMIT 앞에서 뺀다(APPOINTMENT_API.md). 받은 쪽에서 거르면 끝난
// 약속이 차지한 자리만큼 페이지가 비고, 끝난 약속만으로 채워진 페이지는 다음 페이지에
// 모집 중 약속이 있어도 0건이 된다.
const appointments = computed(() => appointmentQuery.data.value?.content ?? [])
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

/** 첫 페이지는 주소에 남기지 않는다. Explore와 같다. */
function replacePage(next: number): void {
  const rest = { ...route.query }
  delete rest.page
  void router.replace({
    path: route.path,
    query: next > 0 ? { ...rest, page: String(next) } : rest,
  })
}

function changePage(next: number): void {
  // 다음 페이지는 맨 위 카드부터 읽어야 한다. 움직임을 줄이도록 설정한 사용자에게는 바로 옮긴다.
  window.scrollTo({
    top: 0,
    behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
  })
  replacePage(next)
}

// 조건이 바뀌면 결과가 통째로 달라지므로 첫 페이지부터 본다. 보던 페이지 번호가 새 결과에는
// 없을 수 있다. 검색어와 직접 고른 언어는 주소를 고칠 때 페이지를 함께 지우고, 이 watch는
// 전체로 자동으로 넓힌 경우를 맡는다.
watch(selectedLanguage, () => {
  if (page.value > 0) replacePage(0)
})

// Explore 검색과 같은 지연이다. replace라 뒤로 가기 기록도 쌓이지 않는다.
const KEYWORD_DEBOUNCE_MS = 300
let keywordTimer: ReturnType<typeof setTimeout> | undefined

watch(keywordInput, (value) => {
  clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => {
    const next = value.trim()
    if (next === keyword.value) return

    const rest = { ...route.query }
    delete rest.keyword
    delete rest.page
    void router.replace({
      path: route.path,
      query: next === '' ? rest : { ...rest, keyword: next },
    })
  }, KEYWORD_DEBOUNCE_MS)
})

// 기다리는 동안 화면을 떠나면, 떠난 화면이 다음 화면의 주소를 고치게 된다.
onBeforeUnmount(() => clearTimeout(keywordTimer))

// 보던 약속이 끝나 목록이 줄면 주소의 페이지가 범위를 벗어난다. 빈 화면 대신 마지막 페이지를
// 보여 준다 — 약속이 없는 것과 구분되지 않기 때문이다. 캐시가 남은 재방문은 값이 변하지
// 않아 첫 판정을 `immediate`가 맡는다.
watch(
  () => [page.value, appointmentQuery.data.value?.totalPages] as const,
  ([current, totalPages]) => {
    if (totalPages === undefined || current === 0 || current < totalPages) return
    replacePage(Math.max(0, totalPages - 1))
  },
  { immediate: true },
)

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
  // 고르던 카드가 있는 페이지로 돌아와야 한다. 첫 페이지로 오면 그 카드가 보이지 않는다.
  ...(page.value > 0 ? { page: String(page.value) } : {}),
  // 검색어·언어가 빠지면 같은 페이지 번호라도 다른 목록이라 그 카드가 없다.
  // 자동으로 넓힌 언어는 주소에 없으므로 싣지 않는다 — 돌아와서 다시 판단한다.
  ...(keyword.value === '' ? {} : { keyword: keyword.value }),
  ...(chosenLanguage.value === undefined ? {} : { language: chosenLanguage.value }),
}))

/**
 * 자동으로 채운 언어로 걸러 아무것도 없으면 전체로 되돌린다.
 *
 * 사용자가 고르지 않은 조건 때문에 빈 화면을 보여주면, 약속이 없는 것인지 걸러진
 * 것인지 구분되지 않는다.
 *
 * 직접 고른 언어에서는 되돌리지 않는다. 고른 조건을 화면이 임의로 풀면 방금 누른
 * 칩과 목록이 어긋난다. 검색어가 있을 때도 두는데, 그때 빈 결과의 이유는 검색어일
 * 수 있어서다.
 *
 * `immediate`는 지울 수 없다. watch는 값이 **변할 때** 도는데, 앱이 QueryClient 하나를
 * 공유하므로 회원 언어로 건 0건 결과가 캐시에 남은 재방문에서는 마운트 시점에 이미
 * `isSuccess=true`·`count=0`이라 값이 변하지 않는다 — watch가 한 번도 돌지 않는다.
 * 첫 실행에서 곧장 돌아 나오는 것은 **캐시가 빈 첫 방문에서만** 참이다.
 *
 * 받은 카드 수가 아니라 서버가 센 전체 개수를 본다. 범위를 벗어난 페이지도 카드가 0개라,
 * 카드 수를 보면 약속이 있는 언어까지 풀어 버린다.
 */
watch(
  () =>
    [appointmentQuery.isSuccess.value, appointmentQuery.data.value?.totalElements ?? 0] as const,
  ([isSuccess, count]) => {
    // 직접 고른 언어는 selectedLanguage에서 automaticLanguage보다 앞서므로 여기서 바꿔도
    // 화면에 닿지 않는다. 그래도 고른 언어가 있으면 판단 자체를 하지 않는다.
    if (chosenLanguage.value !== undefined || selectedLanguage.value === 'ALL') return
    if (!isSuccess || count > 0 || keyword.value !== '') return

    automaticLanguage.value = 'ALL'
  },
  { immediate: true },
)

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
  <main
    class="flex w-full flex-col gap-8 px-screen flex-1 pt-6 pb-[calc(7rem+env(safe-area-inset-bottom))]"
  >
    <ScreenHeader
      variant="back"
      :title="title"
      :back-label="t('action.back')"
      @back="goBack"
    />

    <TextInput
      v-model="keywordInput"
      type="search"
      :label="t('appointment.list.searchLabel')"
      :placeholder="t('appointment.list.searchPlaceholder')"
      label-hidden
    />

    <div
      class="scrollbar-hidden flex gap-2 overflow-x-auto pb-1"
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
          <!--
            서버가 센 값이다. 받은 페이지의 길이를 세면 size(20)에서 멈춰, 약속이 50개인
            항목도 "20 appointments"가 된다. 완료 제외가 countAppointments에도 같이
            걸려 있어(APPOINTMENT_API.md) 이 값은 보이는 카드와 같은 기준이다.
          -->
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

      <template v-else>
        <ul class="flex flex-col gap-3">
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
        <!--
          잠금은 isPending으로만 건다. 목록이 5초마다 다시 조회되므로 isFetching을 쓰면
          버튼이 그 주기마다 잠겼다 풀린다.
        -->
        <AppPagination
          :page="page"
          :total-pages="appointmentQuery.data.value?.totalPages ?? 0"
          :loading="appointmentQuery.isPending.value"
          :label="t('appointment.list.pagination.label')"
          :previous-label="t('appointment.list.pagination.previousPage')"
          :next-label="t('appointment.list.pagination.nextPage')"
          :page-label="(shown) => t('appointment.list.pagination.page', { page: shown })"
          @change="changePage"
        />
      </template>
    </section>

    <div
      class="fixed inset-x-0 bottom-0 z-20 mx-auto w-full max-w-shell bg-canvas/95 px-screen pt-3 pb-[calc(0.75rem+env(safe-area-inset-bottom))] backdrop-blur"
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
        class="w-full max-w-shell rounded-card bg-surface-1 p-5 shadow-sheet"
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
