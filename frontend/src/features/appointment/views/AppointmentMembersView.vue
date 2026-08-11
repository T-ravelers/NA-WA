<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppButton from '@/shared/ui/AppButton.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import AppointmentMemberList from '../components/AppointmentMemberList.vue'
import { appointmentMembersQueryOptions } from '../model/appointmentQueries'

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

const membersQuery = useQuery({
  ...appointmentMembersQueryOptions(appointmentId),
  enabled: computed(() => appointmentId.value !== null),
  retry: false,
})

const members = computed(() => membersQuery.data.value ?? [])

function goBack(): void {
  if (window.history.length > 1) {
    void router.back()
    return
  }
  void router.push({ name: 'appointment-detail', params: { appointmentId: appointmentId.value } })
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
        {{ t('appointment.members.title') }}
      </h1>
    </header>

    <StateEmpty
      v-if="appointmentId === null"
      :title="t('appointment.members.invalidTitle')"
      :description="t('appointment.members.invalidDescription')"
    />
    <StateLoading
      v-else-if="membersQuery.isPending.value"
      :label="t('appointment.members.loading')"
    />
    <StateError
      v-else-if="membersQuery.isError.value"
      :title="t('appointment.members.loadFailed')"
      :description="t('appointment.members.loadFailedDescription')"
      :action-label="t('action.retry')"
      @retry="membersQuery.refetch"
    />
    <StateEmpty
      v-else-if="members.length === 0"
      :title="t('appointment.members.emptyTitle')"
      :description="t('appointment.members.emptyDescription')"
    />
    <AppointmentMemberList
      v-else
      :members="members"
    />

    <div class="grid grid-cols-2 gap-3">
      <AppButton
        block
        variant="secondary"
        @click="
          router.push({ name: 'appointment-attendance', params: { appointmentId: appointmentId } })
        "
      >
        {{ t('appointment.members.attendance') }}
      </AppButton>
      <AppButton
        block
        variant="secondary"
        @click="
          router.push({ name: 'appointment-reviews', params: { appointmentId: appointmentId } })
        "
      >
        {{ t('appointment.members.reviews') }}
      </AppButton>
    </div>
  </main>
</template>
