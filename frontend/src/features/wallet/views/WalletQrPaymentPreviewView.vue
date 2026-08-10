<script setup lang="ts">
import { IconChevronLeft } from '@tabler/icons-vue'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import SegmentedControl from '@/shared/ui/SegmentedControl.vue'

import {
  ACTIVE_APPOINTMENTS,
  formatKrw,
  QR_PAYMENT_PREVIEW,
  type SpendingScope,
} from '../model/qrPayment'

const { t } = useI18n()
const router = useRouter()

const spendingScope = ref<SpendingScope>('personal')
const selectedAppointmentId = ref('')

const spendingOptions = computed(() => [
  { value: 'personal', label: t('wallet.qrPayment.personal') },
  { value: 'shared', label: t('wallet.qrPayment.shared') },
])

const isSharedExpense = computed(() => spendingScope.value === 'shared')
const selectedAppointment = computed(() =>
  ACTIVE_APPOINTMENTS.find((appointment) => appointment.id === selectedAppointmentId.value),
)
const canPay = computed(
  () => spendingScope.value === 'personal' || selectedAppointment.value !== undefined,
)

const goBack = (): void => {
  void router.push({ name: 'wallet-qr-scan' })
}

const completePayment = (): void => {
  if (!canPay.value) return

  void router.push({
    name: 'wallet-qr-payment-complete',
    query: {
      scope: spendingScope.value,
      ...(selectedAppointment.value ? { appointment: selectedAppointment.value.id } : {}),
    },
  })
}
</script>

<template>
  <main class="min-h-dvh bg-canvas px-screen pb-8 text-ink">
    <header class="flex items-center border-b border-hairline py-4">
      <button
        type="button"
        class="grid size-11 place-items-center rounded-sm text-ink transition-colors hover:bg-surface-2 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink"
        :aria-label="t('wallet.qrPayment.back')"
        @click="goBack"
      >
        <IconChevronLeft
          :size="22"
          :stroke-width="2"
          aria-hidden="true"
        />
      </button>
      <h1 class="flex-1 text-center text-title font-bold tracking-[-0.03em]">
        {{ t('wallet.qrPayment.previewTitle') }}
      </h1>
      <span
        class="size-11"
        aria-hidden="true"
      />
    </header>

    <section
      class="space-y-4 pt-6"
      aria-labelledby="wallet-qr-payment-preview-heading"
    >
      <h2
        id="wallet-qr-payment-preview-heading"
        class="sr-only"
      >
        {{ t('wallet.qrPayment.previewHeading') }}
      </h2>

      <AppCard padding="lg">
        <h3 class="text-title-sm">{{ t('wallet.qrPayment.previewHeading') }}</h3>

        <dl class="mt-4 divide-y divide-hairline">
          <div class="flex items-center justify-between gap-4 py-3 first:pt-0 last:pb-0">
            <dt class="text-body-sm text-ink-2">{{ t('wallet.qrPayment.recipient') }}</dt>
            <dd class="text-right text-body-sm font-semibold">
              {{ QR_PAYMENT_PREVIEW.recipient }}
            </dd>
          </div>
          <div class="flex items-center justify-between gap-4 py-3">
            <dt class="text-body-sm text-ink-2">{{ t('wallet.qrPayment.amount') }}</dt>
            <dd class="text-body-sm font-semibold">
              {{ formatKrw(QR_PAYMENT_PREVIEW.amount) }}
            </dd>
          </div>
          <div class="flex items-center justify-between gap-4 py-3">
            <dt class="text-body-sm text-ink-2">{{ t('wallet.qrPayment.currentBalance') }}</dt>
            <dd class="text-body-sm font-semibold">
              {{ formatKrw(QR_PAYMENT_PREVIEW.currentBalance) }}
            </dd>
          </div>
          <div class="flex items-center justify-between gap-4 py-3 last:pb-0">
            <dt class="text-body-sm text-ink-2">{{ t('wallet.qrPayment.balanceAfter') }}</dt>
            <dd class="text-body-sm font-semibold">
              {{ formatKrw(QR_PAYMENT_PREVIEW.balanceAfter) }}
            </dd>
          </div>
        </dl>
      </AppCard>

      <AppCard padding="lg">
        <h3 class="text-title-sm">{{ t('wallet.qrPayment.expenseType') }}</h3>
        <p class="mt-1 text-caption text-ink-3">
          {{ t('wallet.qrPayment.expenseTypeHint') }}
        </p>

        <SegmentedControl
          v-model="spendingScope"
          class="mt-4"
          :options="spendingOptions"
          :label="t('wallet.qrPayment.expenseType')"
        />

        <fieldset
          v-if="isSharedExpense"
          class="mt-5"
        >
          <legend class="text-body-sm font-semibold">
            {{ t('wallet.qrPayment.activeAppointments') }}
          </legend>
          <p class="mt-1 text-caption text-ink-3">
            {{ t('wallet.qrPayment.activeAppointmentsHint') }}
          </p>

          <div class="mt-3 space-y-2">
            <label
              v-for="appointment in ACTIVE_APPOINTMENTS"
              :key="appointment.id"
              class="block cursor-pointer rounded-sm border p-3 transition-colors focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-ink"
              :class="
                selectedAppointmentId === appointment.id
                  ? 'border-ink bg-surface-2'
                  : 'border-hairline bg-transparent'
              "
            >
              <input
                v-model="selectedAppointmentId"
                class="sr-only"
                type="radio"
                name="active-appointment"
                :value="appointment.id"
              />
              <span class="flex items-center justify-between gap-3">
                <span class="text-body-sm font-semibold">{{ appointment.name }}</span>
                <span class="shrink-0 text-caption text-ink-3">{{ appointment.period }}</span>
              </span>
            </label>
          </div>

          <p
            v-if="selectedAppointment === undefined"
            class="mt-3 text-caption text-warning"
            role="status"
          >
            {{ t('wallet.qrPayment.selectAppointment') }}
          </p>
        </fieldset>
      </AppCard>

      <div class="grid grid-cols-2 gap-3 pt-1">
        <AppButton
          variant="secondary"
          block
          @click="goBack"
        >
          {{ t('wallet.qrPayment.cancel') }}
        </AppButton>
        <AppButton
          variant="primary"
          block
          :disabled="!canPay"
          @click="completePayment"
        >
          {{ t('wallet.qrPayment.pay') }}
        </AppButton>
      </div>
    </section>
  </main>
</template>
