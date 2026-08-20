<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppButton from '@/shared/ui/AppButton.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import { submitAppointmentReview, type AppointmentReviewRequest } from '../api/appointmentApi'
import AppointmentReviewCard from '../components/AppointmentReviewCard.vue'
import { appointmentErrorMessageKey } from '../model/appointmentErrors'
import { appointmentKeys } from '../model/appointmentKeys'
import {
  appointmentDetailQueryOptions,
  appointmentMembersQueryOptions,
  appointmentParticipationQueryOptions,
  appointmentReviewStatusQueryOptions,
} from '../model/appointmentQueries'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const i18n = useI18n()
const { t } = i18n
const hasMessage = (key: string): boolean => i18n.te(key)

const appointmentId = computed(() => {
  const raw = Array.isArray(route.params.appointmentId)
    ? route.params.appointmentId[0]
    : route.params.appointmentId
  const parsed = Number(raw)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
})

const detailQuery = useQuery({
  ...appointmentDetailQueryOptions(appointmentId),
  enabled: computed(() => appointmentId.value !== null),
  retry: false,
})

// 내 참여 자격은 상세·출석 화면과 같은 근거(participation 응답)를 쓴다. 회원
// 목록에서 내 memberId를 찾아 추리면 프로필 연동과 목록 조회가 둘 다 성공해야
// 하고, 하나만 실패해도 참여자인데 화면이 막힌다.
const participationQuery = useQuery({
  ...appointmentParticipationQueryOptions(appointmentId),
  enabled: computed(() => appointmentId.value !== null),
  retry: false,
})

const membersQuery = useQuery({
  ...appointmentMembersQueryOptions(appointmentId),
  enabled: computed(() => appointmentId.value !== null),
  retry: false,
})

const myAppointmentMemberId = computed(
  () => participationQuery.data.value?.appointmentMemberId ?? null,
)
const reviewableMembers = computed(() =>
  (membersQuery.data.value ?? []).filter(
    (member) =>
      member.membershipStatus === 'ACTIVE' &&
      member.attendanceStatus === 'ATTENDED' &&
      myAppointmentMemberId.value !== null &&
      member.appointmentMemberId !== myAppointmentMemberId.value,
  ),
)
const appointmentCompleted = computed(
  () => detailQuery.data.value?.appointmentStatus === 'COMPLETED',
)
const isActiveParticipant = computed(() => {
  const participation = participationQuery.data.value
  return (
    participation?.joined === true &&
    participation.membershipStatus === 'ACTIVE' &&
    participation.attendanceStatus === 'ATTENDED'
  )
})
const canReview = computed(() => appointmentCompleted.value && isActiveParticipant.value)

// 이미 후기를 쓴 대상은 서버에서 받아온다. 화면 메모리에만 두면 새로고침하거나
// 나갔다 오는 순간 전원이 미작성으로 되돌아가고, 다시 제출하면 REVIEW-002가 난다.
//
// 자격이 확인된 뒤에만 조회한다. 서버는 약속이 COMPLETED가 아니거나 요청자가
// ACTIVE+ATTENDED가 아니면 이 조회를 REVIEW-001로 막는데(ReviewService), 자격과
// 무관하게 먼저 부르면 그 거절이 "불러오지 못했습니다"로 보인다. 재시도해도
// 달라질 게 없는 상태라 아래 전용 안내로 보내야 한다.
const reviewStatusQuery = useQuery({
  ...appointmentReviewStatusQueryOptions(appointmentId),
  enabled: computed(() => appointmentId.value !== null && canReview.value),
  retry: false,
})

const completedMemberIds = reactive(new Set<number>())

// 서버가 준 목록을 화면 상태에 합친다. 지우지 않고 더하기만 한다 — 방금 이 화면에서
// 저장한 건은 조회가 아직 갱신되기 전이라 목록에 없을 수 있다.
watch(
  () => reviewStatusQuery.data.value?.reviewedAppointmentMemberIds,
  (ids) => {
    for (const id of ids ?? []) completedMemberIds.add(id)
  },
  { immediate: true },
)
const pendingMemberId = ref<number | null>(null)
const failedMemberId = ref<number | null>(null)
const expandedMemberId = ref<number | null>(null)
const allReviewsComplete = computed(
  () =>
    reviewableMembers.value.length > 0 &&
    reviewableMembers.value.every((member) => completedMemberIds.has(member.appointmentMemberId)),
)

// 아직 안 쓴 첫 대상을 펼쳐 둔다. 이미 다 썼으면 아무것도 펼치지 않는다.
watch(
  [reviewableMembers, () => completedMemberIds.size],
  ([members]) => {
    if (expandedMemberId.value !== null) return
    expandedMemberId.value =
      members.find((member) => !completedMemberIds.has(member.appointmentMemberId))
        ?.appointmentMemberId ?? null
  },
  { immediate: true },
)

const reviewMutation = useMutation({
  mutationFn: ({
    appointmentId: id,
    request,
  }: {
    appointmentId: number
    request: AppointmentReviewRequest
  }) => submitAppointmentReview(id, request),
  onSuccess: (_data, variables) => {
    completedMemberIds.add(variables.request.reviewedAppointmentMemberId)
    failedMemberId.value = null
    expandedMemberId.value = null
    void queryClient.invalidateQueries({
      queryKey: appointmentKeys.reviewStatus(appointmentId.value),
    })
  },
  onError: (_error, variables) => {
    failedMemberId.value = variables.request.reviewedAppointmentMemberId
  },
  onSettled: () => {
    pendingMemberId.value = null
  },
})

function submit(request: AppointmentReviewRequest): void {
  if (appointmentId.value === null || !canReview.value || reviewMutation.isPending.value) return

  pendingMemberId.value = request.reviewedAppointmentMemberId
  failedMemberId.value = null
  reviewMutation.mutate({ appointmentId: appointmentId.value, request })
}

// 실패 원인을 서버 오류 코드로 가른다. "이미 작성한 후기"와 "작성 권한 없음"을
// 한 문구로 뭉개면 사용자가 무엇을 해야 할지 알 수 없다.
function errorMessage(memberId: number): string | undefined {
  const error = reviewMutation.error.value
  if (failedMemberId.value !== memberId || error === null) return undefined
  return t(appointmentErrorMessageKey(error, hasMessage))
}

function toggleMember(memberId: number): void {
  expandedMemberId.value = expandedMemberId.value === memberId ? null : memberId
}

function goBack(): void {
  if (window.history.length > 1) {
    void router.back()
    return
  }
  void router.push({ name: 'appointment-detail', params: { appointmentId: appointmentId.value } })
}

function retry(): void {
  void detailQuery.refetch()
  void membersQuery.refetch()
  void participationQuery.refetch()
  void reviewStatusQuery.refetch()
}

function finishReviews(): void {
  if (!canReview.value || !allReviewsComplete.value) return
  void router.push({ name: 'appointment-detail', params: { appointmentId: appointmentId.value } })
}
</script>

<template>
  <main class="flex min-h-dvh w-full flex-col gap-8 px-screen pb-28 pt-6">
    <header class="flex items-center gap-3">
      <AppButton
        compact
        variant="secondary"
        :aria-label="t('action.back')"
        @click="goBack"
      >
        ‹
      </AppButton>
      <h1 class="min-w-0 flex-1 truncate font-display text-section-header text-ink-display">
        {{ t('appointment.review.title') }}
      </h1>
    </header>

    <StateEmpty
      v-if="appointmentId === null"
      :title="t('appointment.review.invalidTitle')"
      :description="t('appointment.review.invalidDescription')"
    />
    <StateLoading
      v-else-if="
        detailQuery.isPending.value ||
        membersQuery.isPending.value ||
        participationQuery.isPending.value
      "
      :label="t('state.loading')"
    />
    <StateError
      v-else-if="
        detailQuery.isError.value || membersQuery.isError.value || participationQuery.isError.value
      "
      :title="t('appointment.review.loadFailed')"
      :description="t('appointment.review.loadFailedDescription')"
      :action-label="t('action.retry')"
      @retry="retry"
    />
    <!--
      쓸 자격이 없는 상태는 실패가 아니라 정상 결과다. 재시도로 달라지지 않으므로
      오류 화면보다 먼저 갈라 전용 안내를 보여준다. 후기 목록 조회는 자격이
      확인된 뒤에만 시작한다.
    -->
    <StateEmpty
      v-else-if="!appointmentCompleted"
      :title="t('appointment.review.notCompletedTitle')"
      :description="t('appointment.review.notCompletedDescription')"
    />
    <StateEmpty
      v-else-if="!isActiveParticipant"
      :title="t('appointment.review.accessDeniedTitle')"
      :description="t('appointment.review.accessDeniedDescription')"
    />
    <StateLoading
      v-else-if="reviewStatusQuery.isLoading.value"
      :label="t('state.loading')"
    />
    <StateError
      v-else-if="reviewStatusQuery.isError.value"
      :title="t('appointment.review.loadFailed')"
      :description="t('appointment.review.loadFailedDescription')"
      :action-label="t('action.retry')"
      @retry="retry"
    />
    <template v-else-if="detailQuery.data.value !== undefined">
      <section class="flex flex-col gap-2">
        <p class="text-caption text-ink-3">{{ t('appointment.review.subtitle') }}</p>
        <h2 class="font-display text-screen-title text-ink-display">
          {{ detailQuery.data.value.appointmentName }}
        </h2>
      </section>

      <StateEmpty
        v-if="reviewableMembers.length === 0"
        :title="t('appointment.review.emptyTitle')"
        :description="t('appointment.review.emptyDescription')"
      />
      <section
        v-else
        class="flex flex-col gap-5"
        aria-labelledby="appointment-review-heading"
      >
        <h2
          id="appointment-review-heading"
          class="text-title text-ink"
        >
          {{ t('appointment.review.members') }}
        </h2>
        <AppointmentReviewCard
          v-for="member in reviewableMembers"
          :key="member.appointmentMemberId"
          :member="member"
          :expanded="expandedMemberId === member.appointmentMemberId"
          :pending="pendingMemberId === member.appointmentMemberId"
          :completed="completedMemberIds.has(member.appointmentMemberId)"
          :error-message="errorMessage(member.appointmentMemberId)"
          @submit="submit"
          @toggle="toggleMember(member.appointmentMemberId)"
        />
      </section>

      <div class="sticky bottom-0 z-10 mt-auto bg-canvas/95 py-3 backdrop-blur">
        <AppButton
          block
          :disabled="!allReviewsComplete"
          @click="finishReviews"
        >
          {{ t('appointment.review.finish') }}
        </AppButton>
      </div>
    </template>
  </main>
</template>
