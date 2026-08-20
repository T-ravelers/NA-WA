<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppBadge from '@/shared/ui/AppBadge.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import {
  confirmAppointmentAttendance,
  type AppointmentAttendanceStatus,
  type AppointmentMember,
} from '../api/appointmentApi'
import { appointmentErrorMessageKey } from '../model/appointmentErrors'
import { appointmentKeys } from '../model/appointmentKeys'
import {
  appointmentDetailQueryOptions,
  appointmentMembersQueryOptions,
} from '../model/appointmentQueries'
import { useAppointmentMemberProfile } from '../model/memberIntegration'

/** 서버가 받는 값. `PENDING`은 확정 요청에 실을 수 없다. */
type ConfirmedAttendance = 'ATTENDED' | 'NO_SHOW'

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

const membersQuery = useQuery({
  ...appointmentMembersQueryOptions(appointmentId),
  enabled: computed(() => appointmentId.value !== null),
  retry: false,
})

const profileQuery = useAppointmentMemberProfile()
const members = computed(() => membersQuery.data.value ?? [])
const isHost = computed(() => {
  const currentMemberId = profileQuery.data.value?.memberId
  return (
    currentMemberId !== undefined &&
    members.value.some((member) => member.memberId === currentMemberId && member.isHost)
  )
})
const appointmentInProgress = computed(
  () => detailQuery.data.value?.appointmentStatus === 'IN_PROGRESS',
)
function initials(displayName: string): string {
  return displayName.trim().charAt(0).toUpperCase() || '?'
}

// 방장이 화면에서 고른 값. 저장 전까지 서버에 반영되지 않는다.
const draft = reactive<Record<number, ConfirmedAttendance>>({})

// 아직 확정 전(PENDING)인 회원은 ATTENDED에서 출발한다. NO_SHOW는 보증금을
// 몰수해 참석자에게 나누는 처리라(16절), 방장이 명시적으로 고르지 않은 회원에게
// 기본값으로 적용할 값이 아니다.
watch(
  members,
  (list) => {
    for (const member of list) {
      if (draft[member.memberId] === undefined) {
        draft[member.memberId] = member.attendanceStatus === 'NO_SHOW' ? 'NO_SHOW' : 'ATTENDED'
      }
    }
  },
  { immediate: true },
)

function attendanceStatus(member: AppointmentMember): AppointmentAttendanceStatus {
  return draft[member.memberId] ?? member.attendanceStatus
}

function statusLabel(status: AppointmentAttendanceStatus): string {
  return t(`appointment.attendance.status.${status}`)
}

function toggleAttendance(member: AppointmentMember): void {
  draft[member.memberId] = attendanceStatus(member) === 'ATTENDED' ? 'NO_SHOW' : 'ATTENDED'
}

// 서버는 참석자가 한 명도 없는 확정을 거부한다(APPOINTMENT-006). 전원 노쇼면
// 나눠 줄 상대가 없어 보증금 정산이 성립하지 않기 때문이다.
const hasAttendedMember = computed(() =>
  members.value.some((member) => attendanceStatus(member) === 'ATTENDED'),
)

const attendanceMutation = useMutation({
  mutationFn: () =>
    confirmAppointmentAttendance(appointmentId.value as number, {
      members: members.value.map((member) => ({
        memberId: member.memberId,
        attendanceStatus: attendanceStatus(member) as ConfirmedAttendance,
      })),
    }),
  onSuccess: async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: appointmentKeys.detail(appointmentId.value) }),
      queryClient.invalidateQueries({ queryKey: appointmentKeys.members(appointmentId.value) }),
      queryClient.invalidateQueries({
        queryKey: appointmentKeys.participation(appointmentId.value),
      }),
      queryClient.invalidateQueries({ queryKey: appointmentKeys.lists() }),
      queryClient.invalidateQueries({ queryKey: appointmentKeys.mine() }),
    ])
    void router.push({
      name: 'appointment-detail',
      params: { appointmentId: appointmentId.value },
    })
  },
})

const saveErrorMessage = computed(() =>
  attendanceMutation.error.value === null
    ? undefined
    : t(appointmentErrorMessageKey(attendanceMutation.error.value, hasMessage)),
)

function save(): void {
  if (appointmentId.value === null || !hasAttendedMember.value) return
  if (!attendanceMutation.isPending.value) attendanceMutation.mutate()
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
        {{ t('appointment.attendance.title') }}
      </h1>
    </header>

    <StateEmpty
      v-if="appointmentId === null"
      :title="t('appointment.attendance.invalidTitle')"
      :description="t('appointment.attendance.invalidDescription')"
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
      :title="t('appointment.attendance.loadFailed')"
      :description="t('appointment.attendance.loadFailedDescription')"
      :action-label="t('action.retry')"
      @retry="retry"
    />
    <StateEmpty
      v-else-if="!appointmentInProgress"
      :title="t('appointment.attendance.notCompletedTitle')"
      :description="t('appointment.attendance.notCompletedDescription')"
    />
    <StateEmpty
      v-else-if="!isHost"
      :title="t('appointment.attendance.accessDeniedTitle')"
      :description="t('appointment.attendance.accessDeniedDescription')"
    />
    <template v-else-if="detailQuery.data.value !== undefined">
      <section class="flex flex-col gap-4">
        <p class="text-caption text-ink-3">{{ t('appointment.attendance.subtitle') }}</p>
        <h2 class="font-display text-screen-title text-ink-display">
          {{ detailQuery.data.value.appointmentName }}
        </h2>
      </section>

      <section
        class="flex flex-col gap-3"
        aria-labelledby="attendance-members-heading"
      >
        <h2
          id="attendance-members-heading"
          class="text-title text-ink"
        >
          {{ t('appointment.attendance.members') }}
        </h2>
        <p class="text-caption text-ink-2">{{ t('appointment.attendance.hint') }}</p>

        <ul class="flex flex-col gap-3">
          <li
            v-for="member in members"
            :key="member.appointmentMemberId"
          >
            <AppCard padding="base">
              <div class="flex items-center gap-3">
                <div
                  class="flex size-11 shrink-0 items-center justify-center overflow-hidden rounded-pill bg-surface-2 text-title text-ink"
                  aria-hidden="true"
                >
                  <img
                    v-if="member.profileImageUrl"
                    :src="member.profileImageUrl"
                    alt=""
                    class="size-full object-cover"
                  />
                  <span v-else>{{ initials(member.displayName) }}</span>
                </div>

                <div class="min-w-0 flex-1">
                  <h3 class="truncate text-title-sm text-ink">{{ member.displayName }}</h3>
                  <p class="mt-1 text-caption text-ink-3">
                    {{ statusLabel(attendanceStatus(member)) }}
                  </p>
                </div>

                <AppBadge
                  :tone="
                    attendanceStatus(member) === 'ATTENDED'
                      ? 'settlement'
                      : attendanceStatus(member) === 'PENDING'
                        ? 'pending'
                        : 'onPaper'
                  "
                >
                  {{ statusLabel(attendanceStatus(member)) }}
                </AppBadge>
                <div class="w-24 shrink-0">
                  <AppButton
                    block
                    compact
                    dense
                    :variant="attendanceStatus(member) === 'ATTENDED' ? 'settle' : 'primary'"
                    :aria-label="t('appointment.attendance.toggle', { name: member.displayName })"
                    :aria-pressed="attendanceStatus(member) === 'ATTENDED'"
                    :disabled="attendanceMutation.isPending.value"
                    @click="toggleAttendance(member)"
                  >
                    {{ statusLabel(attendanceStatus(member)) }}
                  </AppButton>
                </div>
              </div>
            </AppCard>
          </li>
        </ul>
      </section>

      <div
        class="fixed inset-x-0 bottom-0 z-20 mx-auto w-full max-w-[390px] bg-canvas/95 px-screen py-3 backdrop-blur"
      >
        <p
          v-if="saveErrorMessage !== undefined"
          class="mb-2 text-center text-body-sm text-danger"
          role="alert"
        >
          {{ saveErrorMessage }}
        </p>
        <p
          v-else-if="!hasAttendedMember"
          class="mb-2 text-center text-body-sm text-ink-3"
        >
          {{ t('appointment.attendance.requireAttended') }}
        </p>
        <AppButton
          block
          :loading="attendanceMutation.isPending.value"
          :disabled="!hasAttendedMember || attendanceMutation.isPending.value"
          @click="save"
        >
          {{ t('appointment.attendance.save') }}
        </AppButton>
      </div>
    </template>
  </main>
</template>
