<script setup lang="ts">
import { computed, reactive } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppBadge from '@/shared/ui/AppBadge.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import { type AppointmentAttendanceStatus, type AppointmentMember } from '../api/appointmentApi'
import {
  appointmentDetailQueryOptions,
  appointmentMembersQueryOptions,
} from '../model/appointmentQueries'

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

const membersQuery = useQuery({
  ...appointmentMembersQueryOptions(appointmentId),
  enabled: computed(() => appointmentId.value !== null),
  retry: false,
})

const members = computed(() => membersQuery.data.value ?? [])
const attendanceDraft = reactive<Record<number, AppointmentAttendanceStatus>>({})

function initials(displayName: string): string {
  return displayName.trim().charAt(0).toUpperCase() || '?'
}

function attendanceStatus(member: AppointmentMember): AppointmentAttendanceStatus {
  return attendanceDraft[member.appointmentMemberId] ?? member.attendanceStatus
}

function toggleAttendance(member: AppointmentMember): void {
  const current = attendanceStatus(member)
  attendanceDraft[member.appointmentMemberId] = current === 'ATTENDED' ? 'PENDING' : 'ATTENDED'
}

function statusLabel(status: AppointmentAttendanceStatus): string {
  return t(`appointment.attendance.status.${status}`)
}

function goBack(): void {
  if (window.history.length > 1) {
    void router.back()
    return
  }
  void router.push({ name: 'appointment-members', params: { appointmentId: appointmentId.value } })
}

function retry(): void {
  void detailQuery.refetch()
  void membersQuery.refetch()
}
</script>

<template>
  <main class="flex min-h-dvh w-full flex-col gap-6 px-screen py-6">
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
      v-else-if="detailQuery.isPending.value || membersQuery.isPending.value"
      :label="t('state.loading')"
    />
    <StateError
      v-else-if="detailQuery.isError.value || membersQuery.isError.value"
      :title="t('appointment.attendance.loadFailed')"
      :description="t('appointment.attendance.loadFailedDescription')"
      :action-label="t('action.retry')"
      @retry="retry"
    />
    <template v-else-if="detailQuery.data.value !== undefined">
      <section class="flex flex-col gap-2">
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
                  :tone="attendanceStatus(member) === 'ATTENDED' ? 'settlement' : 'onPaper'"
                >
                  {{ statusLabel(attendanceStatus(member)) }}
                </AppBadge>
                <AppButton
                  compact
                  :variant="attendanceStatus(member) === 'ATTENDED' ? 'settle' : 'primary'"
                  :aria-label="t('appointment.attendance.toggle', { name: member.displayName })"
                  @click="toggleAttendance(member)"
                >
                  {{ statusLabel(attendanceStatus(member)) }}
                </AppButton>
              </div>
            </AppCard>
          </li>
        </ul>
      </section>

      <AppButton
        block
        disabled
        :title="t('appointment.attendance.savePending')"
      >
        {{ t('appointment.attendance.save') }}
      </AppButton>
    </template>
  </main>
</template>
