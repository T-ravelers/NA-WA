<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppButton from '@/shared/ui/AppButton.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import { submitAppointmentReview, type AppointmentReviewRequest } from '../api/appointmentApi'
import AppointmentReviewCard from '../components/AppointmentReviewCard.vue'
import {
  appointmentDetailQueryOptions,
  appointmentMembersQueryOptions,
} from '../model/appointmentQueries'
import { useAppointmentMemberProfile } from '../model/memberIntegration'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

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

const profileQuery = useAppointmentMemberProfile()

const membersQuery = useQuery({
  ...appointmentMembersQueryOptions(appointmentId),
  enabled: computed(() => appointmentId.value !== null),
  retry: false,
})

const reviewableMembers = computed(() =>
  (membersQuery.data.value ?? []).filter(
    (member) =>
      member.membershipStatus === 'ACTIVE' &&
      profileQuery.data.value?.memberId !== undefined &&
      member.memberId !== profileQuery.data.value.memberId,
  ),
)
const completedMemberIds = reactive(new Set<number>())
const pendingMemberId = ref<number | null>(null)
const failedMemberId = ref<number | null>(null)
const expandedMemberId = ref<number | null>(null)
const allReviewsComplete = computed(
  () =>
    reviewableMembers.value.length > 0 &&
    reviewableMembers.value.every((member) => completedMemberIds.has(member.appointmentMemberId)),
)

watch(
  reviewableMembers,
  (members) => {
    if (expandedMemberId.value === null && members.length > 0 && completedMemberIds.size === 0) {
      expandedMemberId.value = members[0]?.appointmentMemberId ?? null
    }
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
  },
  onError: (_error, variables) => {
    failedMemberId.value = variables.request.reviewedAppointmentMemberId
  },
  onSettled: () => {
    pendingMemberId.value = null
  },
})

function submit(request: AppointmentReviewRequest): void {
  if (appointmentId.value === null || reviewMutation.isPending.value) return

  pendingMemberId.value = request.reviewedAppointmentMemberId
  failedMemberId.value = null
  reviewMutation.mutate({ appointmentId: appointmentId.value, request })
}

function errorMessage(memberId: number): string | undefined {
  return failedMemberId.value === memberId && reviewMutation.error.value !== null
    ? t('appointment.review.saveFailed')
    : undefined
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
  void profileQuery.refetch()
}

function finishReviews(): void {
  if (!allReviewsComplete.value) return
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
        detailQuery.isPending.value || membersQuery.isPending.value || profileQuery.isPending.value
      "
      :label="t('state.loading')"
    />
    <StateError
      v-else-if="
        detailQuery.isError.value || membersQuery.isError.value || profileQuery.isError.value
      "
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
