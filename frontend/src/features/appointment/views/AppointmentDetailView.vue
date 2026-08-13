<script setup lang="ts">
import { IconMenu2 } from '@tabler/icons-vue'
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppBadge from '@/shared/ui/AppBadge.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import AppointmentMemberList from '../components/AppointmentMemberList.vue'
import AppointmentDepositSheet from '../components/AppointmentDepositSheet.vue'
import {
  cancelAppointmentParticipation,
  joinAppointment,
  type AppointmentDateTimeValue,
  type AppointmentStatus,
} from '../api/appointmentApi'
import {
  appointmentDetailQueryOptions,
  appointmentMembersQueryOptions,
  appointmentParticipationQueryOptions,
} from '../model/appointmentQueries'
import { appointmentKeys } from '../model/appointmentKeys'
import { parseAppointmentDateTime } from '../model/appointmentDateTime'
import { useAppointmentMemberProfile } from '../model/memberIntegration'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const { locale, t } = useI18n()

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

const membersQuery = useQuery({
  ...appointmentMembersQueryOptions(appointmentId),
  enabled: computed(() => appointmentId.value !== null),
  retry: false,
})
const participationQuery = useQuery({
  ...appointmentParticipationQueryOptions(appointmentId),
  enabled: computed(() => appointmentId.value !== null),
  retry: false,
})

const appointment = computed(() => detailQuery.data.value)
const members = computed(() =>
  (membersQuery.data.value ?? appointment.value?.members ?? []).filter(
    (member) => member.membershipStatus === 'ACTIVE',
  ),
)
const profileQuery = useAppointmentMemberProfile()

const depositSheetOpen = ref(false)
const menuOpen = ref(false)

const statusTone = computed(() =>
  appointment.value?.appointmentStatus === 'RECRUITING' ? 'ongoing' : 'neutral',
)

const isJoinAvailable = computed(() => {
  if (appointment.value?.appointmentStatus !== 'RECRUITING') return false
  const deadline = parseAppointmentDateTime(appointment.value.joinDeadline)
  return deadline !== null && Date.now() < deadline.getTime()
})
const currentMemberId = computed(() => profileQuery.data.value?.memberId)
const isHost = computed(
  () =>
    currentMemberId.value !== undefined &&
    members.value.some((member) => member.memberId === currentMemberId.value && member.isHost),
)
const isActiveParticipant = computed(
  () =>
    currentMemberId.value !== undefined &&
    members.value.some(
      (member) =>
        member.memberId === currentMemberId.value &&
        member.membershipStatus === 'ACTIVE' &&
        member.attendanceStatus === 'ATTENDED',
    ),
)
const canCancelParticipation = computed(() => {
  const status = participationQuery.data.value?.membershipStatus
  return (
    participationQuery.data.value?.joined === true && (status === 'PENDING' || status === 'ACTIVE')
  )
})
const cancelMutation = useMutation({
  mutationFn: () => {
    if (appointmentId.value === null) throw new Error('Invalid appointment id')
    return cancelAppointmentParticipation(appointmentId.value)
  },
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: appointmentKeys.all })
  },
})
const joinMutation = useMutation({
  mutationFn: () => {
    if (appointmentId.value === null) throw new Error('Invalid appointment id')
    return joinAppointment(appointmentId.value)
  },
  onSuccess: async () => {
    depositSheetOpen.value = false
    await queryClient.invalidateQueries({ queryKey: appointmentKeys.all })
  },
})
const canOpenAttendance = computed(
  () => appointment.value?.appointmentStatus === 'IN_PROGRESS' && isHost.value,
)
const canOpenReviews = computed(
  () => appointment.value?.appointmentStatus === 'COMPLETED' && isActiveParticipant.value,
)
const canOpenPostEventMenu = computed(() => canOpenAttendance.value || canOpenReviews.value)

function formatDateTime(value: AppointmentDateTimeValue): string {
  if (!value) return t('appointment.detail.notProvided')
  const parsed = parseAppointmentDateTime(value)
  if (!parsed) return typeof value === 'string' ? value : t('appointment.detail.notProvided')
  return new Intl.DateTimeFormat(locale.value, {
    dateStyle: 'medium',
    timeStyle: 'short',
    timeZone: 'Asia/Seoul',
  }).format(parsed)
}

function formatDeposit(value: string): string {
  const amount = Number(value)
  return Number.isFinite(amount) ? new Intl.NumberFormat(locale.value).format(amount) : value
}

function statusLabel(status: AppointmentStatus): string {
  return t(`appointment.status.${status}`)
}

function goBack(): void {
  const current = appointment.value
  if (current?.itemType === 'EVENT' || current?.itemType === 'PLACE') {
    void router.push({
      name: 'explore',
      query: { tab: current.itemType === 'PLACE' ? 'places' : 'events' },
    })
    return
  }

  void router.push({ name: 'explore' })
}

function openMemberProfile(member: { memberId: number }): void {
  if (appointmentId.value !== null) {
    void router.push({
      name: 'appointment-member-profile',
      params: { appointmentId: appointmentId.value, memberId: member.memberId },
    })
  }
}

function openAttendance(): void {
  if (appointmentId.value === null || !canOpenAttendance.value) return

  menuOpen.value = false
  void router.push({
    name: 'appointment-attendance',
    params: { appointmentId: appointmentId.value },
  })
}

function openReviews(): void {
  if (appointmentId.value === null || !canOpenReviews.value) return

  menuOpen.value = false
  void router.push({
    name: 'appointment-reviews',
    params: { appointmentId: appointmentId.value },
  })
}

function retry(): void {
  void detailQuery.refetch()
  void membersQuery.refetch()
}

function openDepositSheet(): void {
  if (isJoinAvailable.value) depositSheetOpen.value = true
}

function closeDepositSheet(): void {
  depositSheetOpen.value = false
}

function confirmJoin(): void {
  if (!isJoinAvailable.value || joinMutation.isPending.value) return
  joinMutation.mutate()
}

function cancelParticipation(): void {
  if (!canCancelParticipation.value || cancelMutation.isPending.value) return
  cancelMutation.mutate()
}
</script>

<template>
  <main class="flex min-h-dvh w-full flex-col gap-8 px-screen pb-28 pt-6">
    <header class="relative flex items-center gap-3">
      <AppButton
        compact
        variant="secondary"
        :aria-label="t('action.back')"
        @click="goBack"
      >
        ‹
      </AppButton>
      <h1 class="min-w-0 flex-1 truncate font-display text-section-header text-ink-display">
        {{ t('appointment.detail.title') }}
      </h1>
      <AppButton
        v-if="canOpenPostEventMenu"
        compact
        variant="secondary"
        :aria-label="t('appointment.detail.openMenu')"
        :aria-expanded="menuOpen"
        aria-controls="appointment-detail-menu"
        @click="menuOpen = !menuOpen"
      >
        <IconMenu2
          :size="20"
          :stroke-width="2"
          aria-hidden="true"
        />
      </AppButton>
      <div
        v-if="menuOpen"
        id="appointment-detail-menu"
        role="menu"
        class="absolute right-0 top-14 z-30 flex w-48 flex-col gap-1 rounded-sm border border-hairline bg-surface-1 p-2 shadow-sheet"
      >
        <button
          v-if="canOpenAttendance"
          type="button"
          role="menuitem"
          class="rounded-xs px-3 py-3 text-left text-body-sm text-ink transition-colors hover:bg-surface-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
          @click="openAttendance"
        >
          {{ t('appointment.detail.menu.attendance') }}
        </button>
        <button
          v-if="canOpenReviews"
          type="button"
          role="menuitem"
          class="rounded-xs px-3 py-3 text-left text-body-sm text-ink transition-colors hover:bg-surface-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
          @click="openReviews"
        >
          {{ t('appointment.detail.menu.reviews') }}
        </button>
      </div>
    </header>

    <StateEmpty
      v-if="appointmentId === null"
      :title="t('appointment.detail.invalidTitle')"
      :description="t('appointment.detail.invalidDescription')"
    />

    <StateLoading
      v-else-if="detailQuery.isPending.value"
      :label="t('state.loading')"
    />

    <StateError
      v-else-if="detailQuery.isError.value"
      :title="t('appointment.detail.loadFailed')"
      :description="t('appointment.detail.loadFailedDescription')"
      :action-label="t('action.retry')"
      @retry="retry"
    />

    <template v-else-if="appointment !== undefined">
      <section class="flex flex-col gap-5">
        <div class="flex items-start justify-between gap-3">
          <div class="min-w-0">
            <p class="text-caption text-ink-3">
              {{
                appointment.itemType === 'EVENT'
                  ? t('appointment.detail.event')
                  : t('appointment.detail.place')
              }}
            </p>
            <h2 class="mt-1 break-words font-display text-screen-title text-ink-display">
              {{ appointment.appointmentName }}
            </h2>
          </div>
          <AppBadge
            :tone="statusTone"
            dot
          >
            {{ statusLabel(appointment.appointmentStatus) }}
          </AppBadge>
        </div>

        <AppCard padding="lg">
          <dl class="flex flex-col divide-y divide-hairline">
            <div class="grid grid-cols-[7rem_1fr] gap-3 py-3 first:pt-0 last:pb-0">
              <dt class="text-caption text-ink-3">{{ t('appointment.detail.schedule') }}</dt>
              <dd class="text-body-sm text-ink">
                {{ formatDateTime(appointment.activityStartAt) }}
                <span aria-hidden="true">–</span>
                {{ formatDateTime(appointment.activityEndAt) }}
              </dd>
            </div>
            <div class="grid grid-cols-[7rem_1fr] gap-3 py-3">
              <dt class="text-caption text-ink-3">{{ t('appointment.detail.meetingPlace') }}</dt>
              <dd class="text-body-sm text-ink">
                {{ appointment.meetingPlace ?? t('appointment.detail.notProvided') }}
                <span
                  v-if="appointment.meetingAddress"
                  class="mt-1 block text-caption text-ink-3"
                >
                  {{ appointment.meetingAddress }}
                </span>
              </dd>
            </div>
            <div class="grid grid-cols-[7rem_1fr] gap-3 py-3">
              <dt class="text-caption text-ink-3">{{ t('appointment.detail.language') }}</dt>
              <dd class="text-body-sm text-ink">
                {{ t(`appointment.languages.${appointment.languageCode}`) }}
              </dd>
            </div>
            <div class="grid grid-cols-[7rem_1fr] gap-3 py-3">
              <dt class="text-caption text-ink-3">{{ t('appointment.detail.deposit') }}</dt>
              <dd class="text-body-sm text-ink">₩{{ formatDeposit(appointment.depositAmount) }}</dd>
            </div>
            <div class="grid grid-cols-[7rem_1fr] gap-3 py-3 last:pb-0">
              <dt class="text-caption text-ink-3">{{ t('appointment.detail.joinDeadline') }}</dt>
              <dd class="text-body-sm text-ink">
                {{ formatDateTime(appointment.joinDeadline) }}
              </dd>
            </div>
          </dl>
        </AppCard>
      </section>

      <section
        class="flex flex-col gap-5"
        aria-labelledby="appointment-members-heading"
      >
        <h2
          id="appointment-members-heading"
          class="text-title text-ink"
        >
          {{ t('appointment.members.title') }}
        </h2>

        <StateLoading
          v-if="membersQuery.isPending.value"
          :label="t('appointment.members.loading')"
          :lines="2"
        />
        <StateError
          v-else-if="membersQuery.isError.value"
          :title="t('appointment.members.loadFailed')"
          :description="t('appointment.members.loadFailedDescription')"
          :action-label="t('action.retry')"
          @retry="retry"
        />
        <StateEmpty
          v-else-if="members.length === 0"
          :title="t('appointment.members.emptyTitle')"
          :description="t('appointment.members.emptyDescription')"
        />
        <AppointmentMemberList
          v-else
          :members="members"
          @select="openMemberProfile"
        />

        <AppButton
          v-if="canOpenAttendance"
          block
          variant="secondary"
          @click="openAttendance"
        >
          {{ t('appointment.detail.attendance') }}
        </AppButton>
      </section>

      <div
        class="fixed inset-x-0 bottom-0 z-20 mx-auto w-full max-w-[390px] bg-canvas/95 px-screen py-3 backdrop-blur"
      >
        <AppButton
          v-if="canCancelParticipation"
          block
          variant="secondary"
          :disabled="cancelMutation.isPending.value"
          @click="cancelParticipation"
        >
          {{ t('appointment.detail.cancelParticipation') }}
        </AppButton>
        <AppButton
          v-else
          block
          :disabled="!isJoinAvailable"
          :title="!isJoinAvailable ? t('appointment.detail.joinUnavailable') : undefined"
          @click="openDepositSheet"
        >
          {{ t('appointment.detail.join') }}
        </AppButton>
      </div>

      <AppointmentDepositSheet
        v-if="depositSheetOpen"
        :appointment-name="appointment.appointmentName"
        :deposit-amount="appointment.depositAmount"
        :confirm-disabled="joinMutation.isPending.value || !isJoinAvailable"
        @close="closeDepositSheet"
        @confirm="confirmJoin"
      />
    </template>
  </main>
</template>
