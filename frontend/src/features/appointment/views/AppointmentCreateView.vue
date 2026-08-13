<script setup lang="ts">
import { computed } from 'vue'
import { useMutation, useQueryClient } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppButton from '@/shared/ui/AppButton.vue'

import {
  createAppointment,
  type AppointmentCreateRequest,
  type AppointmentItemType,
} from '../api/appointmentApi'
import AppointmentCreateForm from '../components/AppointmentCreateForm.vue'
import { appointmentKeys } from '../model/appointmentKeys'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const queryClient = useQueryClient()

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

const createMutation = useMutation({
  mutationFn: createAppointment,
  onSuccess: async (appointment) => {
    queryClient.setQueryData(appointmentKeys.detail(appointment.appointmentId), appointment)
    await queryClient.invalidateQueries({ queryKey: appointmentKeys.lists() })
    await router.push({ name: 'appointment-list' })
  },
})

const errorMessage = computed(() =>
  createMutation.error.value === null ? undefined : t('appointment.create.loadFailed'),
)

function submit(request: AppointmentCreateRequest): void {
  if (!createMutation.isPending.value) createMutation.mutate(request)
}

function goBack(): void {
  if (window.history.length > 1) {
    void router.back()
    return
  }
  void router.push({ name: 'appointment-list' })
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
      <h1 class="min-w-0 flex-1 truncate font-display text-screen-title text-ink-display">
        {{ t('appointment.create.title') }}
      </h1>
    </header>

    <AppointmentCreateForm
      :item-id="itemId"
      :item-type="itemType"
      :pending="createMutation.isPending.value"
      :error-message="errorMessage"
      @submit="submit"
    />
  </main>
</template>
