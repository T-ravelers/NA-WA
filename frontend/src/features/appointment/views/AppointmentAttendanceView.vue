<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { getAvatarInitial } from '@/shared/lib/avatarInitial'
import AppBadge from '@/shared/ui/AppBadge.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import AppImage from '@/shared/ui/AppImage.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'
import ScreenHeader from '@/shared/ui/ScreenHeader.vue'

import AppointmentAttendanceConfirmSheet from '../components/AppointmentAttendanceConfirmSheet.vue'
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
  appointmentParticipationQueryOptions,
} from '../model/appointmentQueries'

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

const participationQuery = useQuery({
  ...appointmentParticipationQueryOptions(appointmentId),
  enabled: computed(() => appointmentId.value !== null),
  retry: false,
})

const members = computed(() => membersQuery.data.value ?? [])
const myAppointmentMemberId = computed(
  () => participationQuery.data.value?.appointmentMemberId ?? null,
)
// 방장 여부는 상세 화면과 같은 근거(participation 응답)를 쓴다. 회원 목록에서
// 내 memberId를 찾아 추리면 목록 조회나 프로필 연동이 실패했을 때 방장 권한까지
// 함께 사라져, 정작 출석을 확정해야 할 사람이 막힌다.
const isHost = computed(() => participationQuery.data.value?.host === true)
// 상세 화면의 시트와 같은 조건이다. 활동이 끝나기 전에 확정하면 늦게 온 사람이
// 노쇼로 굳어 보증금을 잃는다. "끝났는가"는 클라이언트 시계로 계산하지 않고,
// 서버가 활동 종료 후 확정 전 약속에 내려주는 상태를 그대로 쓴다.
const attendanceOpen = computed(
  () => detailQuery.data.value?.appointmentStatus === 'AWAITING_ATTENDANCE',
)
// 방장이 화면에서 고른 값. 저장 전까지 서버에 반영되지 않는다.
const draft = reactive<Record<number, ConfirmedAttendance>>({})

// 아직 확정 전(PENDING)인 회원은 NO_SHOW에서 출발한다. 방장이 온 사람을 하나씩
// 눌러 ATTENDED로 바꾼다. 이미 확정된 값이 있으면 그 값을 그대로 이어받는다.
watch(
  members,
  (list) => {
    for (const member of list) {
      if (draft[member.memberId] === undefined) {
        draft[member.memberId] = member.attendanceStatus === 'ATTENDED' ? 'ATTENDED' : 'NO_SHOW'
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

const attendedCount = computed(
  () => members.value.filter((member) => attendanceStatus(member) === 'ATTENDED').length,
)
const noShowCount = computed(() => members.value.length - attendedCount.value)
// 서버는 참석자가 한 명도 없는 확정을 거부한다(APPOINTMENT-006). 전원 노쇼면
// 나눠 줄 상대가 없어 보증금 정산이 성립하지 않기 때문이다.
const hasAttendedMember = computed(() => attendedCount.value > 0)
// 방장도 기본값이 미참석이라, 자기를 올리지 않은 채 제출하면 자기 보증금을 잃는다.
// 화면에서는 "안 눌렀다"와 "안 왔다"가 같은 모양이라 확인 단계에서 따로 짚어 준다.
const selfNoShow = computed(() =>
  members.value.some(
    (member) =>
      member.appointmentMemberId === myAppointmentMemberId.value &&
      attendanceStatus(member) !== 'ATTENDED',
  ),
)

// 확정은 되돌리는 상태 전이가 없다. 누르자마자 보내지 않고 숫자로 한 번 되짚는다.
const saveConfirmOpen = ref(false)

const attendanceMutation = useMutation({
  mutationFn: () =>
    confirmAppointmentAttendance(appointmentId.value as number, {
      members: members.value.map((member) => ({
        memberId: member.memberId,
        attendanceStatus: attendanceStatus(member) as ConfirmedAttendance,
      })),
    }),
  onSuccess: async () => {
    saveConfirmOpen.value = false
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: appointmentKeys.detail(appointmentId.value) }),
      queryClient.invalidateQueries({ queryKey: appointmentKeys.members(appointmentId.value) }),
      queryClient.invalidateQueries({
        queryKey: appointmentKeys.participation(appointmentId.value),
      }),
      queryClient.invalidateQueries({ queryKey: appointmentKeys.lists() }),
      queryClient.invalidateQueries({ queryKey: appointmentKeys.mine() }),
    ])
    // 확정을 끝낸 출석 화면도 되돌아갈 화면이 아니다. ‹ 로 나가든 저장하고 나가든 같은 길이다.
    returnToDetail()
  },
})

const saveErrorMessage = computed(() =>
  attendanceMutation.error.value === null
    ? undefined
    : t(appointmentErrorMessageKey(attendanceMutation.error.value, hasMessage)),
)

function openSaveConfirm(): void {
  if (appointmentId.value === null || !hasAttendedMember.value) return

  attendanceMutation.reset()
  saveConfirmOpen.value = true
}

function closeSaveConfirm(): void {
  saveConfirmOpen.value = false
}

function confirmSave(): void {
  if (!attendanceMutation.isPending.value) attendanceMutation.mutate()
}

// 약속 상세에서 push로 열린 화면이다. 일을 마치든 그냥 나가든 자기 엔트리를 소비해
// 되감는다 — 바로 아래가 이미 상세다. replace로 자리를 바꿔치기하면 상세가 두 번 쌓여,
// 돌아온 뒤 뒤로 가기를 눌러도 같은 라우트에 머물러 아무 반응이 없는 것처럼 보인다.
// 되감을 히스토리가 없을 때(딥링크·PWA 재진입)만 상세로 보낸다.
function returnToDetail(): void {
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
}
</script>

<template>
  <main
    class="flex w-full flex-col gap-8 px-screen flex-1 pt-6 pb-[calc(7rem+env(safe-area-inset-bottom))]"
  >
    <ScreenHeader
      variant="back"
      :title="t('appointment.attendance.title')"
      :back-label="t('action.back')"
      @back="returnToDetail"
    />

    <StateEmpty
      v-if="appointmentId === null"
      :title="t('appointment.attendance.invalidTitle')"
      :description="t('appointment.attendance.invalidDescription')"
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
      :title="t('appointment.attendance.loadFailed')"
      :description="t('appointment.attendance.loadFailedDescription')"
      :action-label="t('action.retry')"
      @retry="retry"
    />
    <StateEmpty
      v-else-if="!attendanceOpen"
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
                  class="flex size-11 shrink-0 items-center justify-center overflow-hidden rounded-pill bg-surface-3 text-title text-ink"
                  aria-hidden="true"
                >
                  <AppImage
                    :src="member.profileImageUrl"
                    alt=""
                    class="size-full object-cover"
                  >
                    <span>{{ getAvatarInitial(member.displayName) }}</span>
                  </AppImage>
                </div>

                <div class="min-w-0 flex-1">
                  <div class="flex flex-wrap items-center gap-2">
                    <!-- 본인도 이름을 그대로 적고 배지로 가른다. 회원 목록이 쓰는
                         방식이고, 이름을 통째로 "You"로 바꾸면 토글의 aria-label이
                         쓰는 실명과 어긋나 보이는 이름과 읽히는 이름이 달라진다. -->
                    <h3 class="truncate text-title-sm text-ink">{{ member.displayName }}</h3>
                    <AppBadge
                      v-if="member.appointmentMemberId === myAppointmentMemberId"
                      tone="neutral"
                    >
                      {{ t('appointment.attendance.you') }}
                    </AppBadge>
                    <AppBadge :tone="member.isHost ? 'settlement' : 'neutral'">
                      {{
                        member.isHost
                          ? t('appointment.members.host')
                          : t('appointment.attendance.member')
                      }}
                    </AppBadge>
                  </div>
                </div>

                <div class="w-28 shrink-0">
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
        class="fixed inset-x-0 bottom-0 z-20 mx-auto w-full max-w-shell bg-canvas/95 px-screen pt-3 pb-[calc(0.75rem+env(safe-area-inset-bottom))] backdrop-blur"
      >
        <!-- 시트가 열려 있으면 같은 오류를 시트가 이미 말하고 있다. 여기까지
             띄우면 라이브 리전이 둘이라 스크린 리더가 두 번 읽고, 그중 하나는
             scrim 뒤라 눈으로는 보이지도 않는다. -->
        <p
          v-if="saveErrorMessage !== undefined && !saveConfirmOpen"
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
          @click="openSaveConfirm"
        >
          {{ t('appointment.attendance.save') }}
        </AppButton>
      </div>

      <AppointmentAttendanceConfirmSheet
        v-if="saveConfirmOpen"
        :attended-count="attendedCount"
        :no-show-count="noShowCount"
        :self-no-show="selfNoShow"
        :confirm-disabled="attendanceMutation.isPending.value"
        :error-message="saveErrorMessage"
        @close="closeSaveConfirm"
        @confirm="confirmSave"
      />
    </template>
  </main>
</template>
