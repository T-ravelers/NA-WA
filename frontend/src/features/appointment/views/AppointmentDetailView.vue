<script setup lang="ts">
import { IconMenu2 } from '@tabler/icons-vue'
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { formatServerDateTime, parseServerDateTime } from '@/shared/lib/datetime'
import AppBadge from '@/shared/ui/AppBadge.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import AppointmentMemberList from '../components/AppointmentMemberList.vue'
import AppointmentDepositSheet from '../components/AppointmentDepositSheet.vue'
import {
  joinAppointment,
  type AppointmentDateTimeValue,
  type AppointmentStatus,
} from '../api/appointmentApi'
import { appointmentKeys } from '../model/appointmentKeys'
import { appointmentErrorMessageKey } from '../model/appointmentErrors'
import {
  appointmentDetailQueryOptions,
  appointmentMembersQueryOptions,
  appointmentParticipationQueryOptions,
} from '../model/appointmentQueries'
import { useAppointmentMemberProfile } from '../model/memberIntegration'

const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()
const i18n = useI18n()
const { locale, t } = i18n
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
const hasJoined = computed(() => participationQuery.data.value?.joined === true)
// 조회 실패 시 hasJoined는 false로 남는다. 서버가 최종적으로 중복 참여를
// 막아주니 데이터는 안전하지만, 그대로 두면 사용자가 결제 시트까지 갔다가
// 거기서야 오류를 보게 된다. 조회 자체가 실패했을 때는 참여 여부를 확신할 수
// 없다는 걸 버튼 단계에서 미리 알려준다.
const participationCheckFailed = computed(() => participationQuery.isError.value)

const statusTone = computed(() =>
  appointment.value?.appointmentStatus === 'RECRUITING' ? 'ongoing' : 'neutral',
)

const isJoinAvailable = computed(() => {
  if (appointment.value?.appointmentStatus !== 'RECRUITING') return false
  const deadline = parseServerDateTime(appointment.value.joinDeadline)
  return deadline !== null && Date.now() < deadline.getTime()
})
const isJoinButtonEnabled = computed(
  () => isJoinAvailable.value && !hasJoined.value && !participationCheckFailed.value,
)
// 버튼 title 툴팁은 비활성 버튼에 pointer-events-none이 걸려 뜨지 않고,
// 390px 모바일 PWA라 애초에 hover도 없다. 그래서 비활성 이유는 버튼 아래에
// 상시 텍스트로 보여준다. 모집 종료(CLOSED/COMPLETED/CANCELLED)는 사용자
// 잘못이 아닌 정상 상태라 text-danger(빨강)로 알릴 일이 아니어서, 세 경우
// 모두 중립색(text-ink-3)으로 통일한다.
const joinDisabledReason = computed(() => {
  if (participationCheckFailed.value) return t('appointment.detail.participationCheckFailed')
  if (hasJoined.value) return t('appointment.detail.alreadyJoined')
  if (!isJoinAvailable.value) return t('appointment.detail.joinUnavailable')
  return undefined
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
const canOpenAttendance = computed(
  () => appointment.value?.appointmentStatus === 'IN_PROGRESS' && isHost.value,
)
const canOpenReviews = computed(
  () => appointment.value?.appointmentStatus === 'COMPLETED' && isActiveParticipant.value,
)
const canOpenPostEventMenu = computed(() => canOpenAttendance.value || canOpenReviews.value)

function formatDateTime(value: AppointmentDateTimeValue): string {
  if (!value) return t('appointment.detail.notProvided')
  const parsed = parseServerDateTime(value)
  if (!parsed) return typeof value === 'string' ? value : t('appointment.detail.notProvided')
  return formatServerDateTime(parsed, locale.value, {
    dateStyle: 'medium',
    timeStyle: 'short',
  })
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
  void participationQuery.refetch()
}

const joinMutation = useMutation({
  mutationFn: () => joinAppointment(appointmentId.value as number),
  onSuccess: async () => {
    depositSheetOpen.value = false
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: appointmentKeys.detail(appointmentId.value) }),
      queryClient.invalidateQueries({ queryKey: appointmentKeys.members(appointmentId.value) }),
      queryClient.invalidateQueries({
        queryKey: appointmentKeys.participation(appointmentId.value),
      }),
    ])
  },
})

const joinErrorMessage = computed(() =>
  joinMutation.error.value === null
    ? undefined
    : t(appointmentErrorMessageKey(joinMutation.error.value, hasMessage)),
)

function openDepositSheet(): void {
  if (!isJoinButtonEnabled.value) return

  joinMutation.reset()
  depositSheetOpen.value = true
}

function closeDepositSheet(): void {
  depositSheetOpen.value = false
}

function confirmJoin(): void {
  if (!joinMutation.isPending.value) joinMutation.mutate()
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
        <p
          v-if="joinDisabledReason !== undefined"
          class="mb-2 text-center text-body-sm text-ink-3"
        >
          {{ joinDisabledReason }}
        </p>
        <AppButton
          block
          :disabled="!isJoinButtonEnabled"
          @click="openDepositSheet"
        >
          {{ t('appointment.detail.join') }}
        </AppButton>
      </div>

      <AppointmentDepositSheet
        v-if="depositSheetOpen"
        :appointment-name="appointment.appointmentName"
        :deposit-amount="appointment.depositAmount"
        :confirm-disabled="joinMutation.isPending.value"
        :error-message="joinErrorMessage"
        @close="closeDepositSheet"
        @confirm="confirmJoin"
      />
    </template>
  </main>
</template>
