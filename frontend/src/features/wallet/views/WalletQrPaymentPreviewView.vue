<script setup lang="ts">
import { useMutation, useQuery } from '@tanstack/vue-query'
import { IconChevronLeft } from '@tabler/icons-vue'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { NormalizedApiError } from '@/shared/api/apiError'
import AmountInput from '@/shared/ui/AmountInput.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import SegmentedControl from '@/shared/ui/SegmentedControl.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'

import { executeQrPayment, previewQrPayment } from '../api/qrPaymentApi'
import { useWalletAppointmentIntegration } from '../model/appointmentIntegration'
import { parseServerDateTime } from '../model/walletHome'
import {
  formatKrw,
  isValidQrPaymentAmount,
  qrPaymentKeys,
  toQrPaymentSpendingScope,
  type SpendingScope,
} from '../model/qrPayment'
import { useQrPaymentSessionStore } from '../model/qrPaymentSession'

const i18n = useI18n()
const { t, locale } = i18n
const router = useRouter()
const qrPaymentSession = useQrPaymentSessionStore()
const { useMyOngoingAppointmentsQuery } = useWalletAppointmentIntegration()
const ongoingAppointmentsQuery = useMyOngoingAppointmentsQuery()

const session = computed(() => qrPaymentSession.session)

const spendingScope = ref<SpendingScope>('personal')
const selectedAppointmentId = ref<number | null>(null)
const enteredAmount = ref<number | null>(null)

const spendingOptions = computed(() => [
  { value: 'personal', label: t('wallet.qrPayment.personal') },
  { value: 'shared', label: t('wallet.qrPayment.shared') },
])

const isSharedExpense = computed(() => spendingScope.value === 'shared')
const selectedAppointment = computed(() =>
  ongoingAppointmentsQuery.data.value?.find(
    (appointment) => appointment.appointmentId === selectedAppointmentId.value,
  ),
)

const appointmentPeriodFormatter = computed(
  () =>
    new Intl.DateTimeFormat(locale.value, {
      month: 'short',
      day: 'numeric',
      timeZone: 'Asia/Seoul',
    }),
)

function formatAppointmentPeriod(activityStartAt: string, activityEndAt: string): string {
  const start = parseServerDateTime(activityStartAt)
  const end = parseServerDateTime(activityEndAt)

  if (start === null || end === null) return ''

  return `${appointmentPeriodFormatter.value.format(start)} – ${appointmentPeriodFormatter.value.format(end)}`
}

const finalAmount = computed<number | null>(() => {
  const resolved = session.value?.resolved

  if (resolved === undefined) return null

  return resolved.amountInputRequired ? enteredAmount.value : resolved.amount
})

const isPreviewReady = computed(
  () =>
    session.value !== null &&
    (spendingScope.value === 'personal' ||
      (spendingScope.value === 'shared' && selectedAppointment.value !== undefined)) &&
    isValidQrPaymentAmount(finalAmount.value),
)

const previewQuery = useQuery({
  queryKey: computed(() =>
    qrPaymentKeys.preview(
      session.value?.qrToken ?? '',
      finalAmount.value ?? 0,
      toQrPaymentSpendingScope(spendingScope.value),
      selectedAppointment.value?.appointmentId ?? null,
    ),
  ),
  queryFn: () =>
    previewQrPayment({
      qrToken: session.value?.qrToken ?? '',
      amount: finalAmount.value ?? 0,
      spendingScope: toQrPaymentSpendingScope(spendingScope.value),
      appointmentId: selectedAppointment.value?.appointmentId ?? null,
    }),
  enabled: isPreviewReady,
})

const previewErrorMessage = computed(() => {
  const error = previewQuery.error.value

  if (!(error instanceof NormalizedApiError) || !i18n.te(error.messageKey)) {
    return t('wallet.qrPayment.previewError')
  }

  return t(error.messageKey)
})

const executeMutation = useMutation({
  mutationFn: ({
    request,
    idempotencyKey,
  }: {
    request: Parameters<typeof executeQrPayment>[0]
    idempotencyKey: string
  }) => executeQrPayment(request, idempotencyKey),
})

const executeErrorMessage = computed(() => {
  const error = executeMutation.error.value

  if (!(error instanceof NormalizedApiError) || !i18n.te(error.messageKey)) {
    return t('wallet.qrPayment.executeError')
  }

  return t(error.messageKey)
})

const canPay = computed(
  () => previewQuery.data.value?.canPay === true && !executeMutation.isPending.value,
)

const goBack = (): void => {
  void router.push({ name: 'wallet-qr-scan' })
}

const idempotencyKey = ref<string | null>(null)

// 요청 내용이 바뀌면(다른 결제 시도) 새 키를 쓴다. 재시도(내용 동일)는 키를 재사용한다.
watch(
  () =>
    [
      session.value?.qrToken,
      finalAmount.value,
      spendingScope.value,
      selectedAppointmentId.value,
    ] as const,
  () => {
    idempotencyKey.value = null
  },
)

const completePayment = (): void => {
  if (!canPay.value || session.value === null || finalAmount.value === null) return

  idempotencyKey.value ??=
    globalThis.crypto?.randomUUID?.() ?? `qr-payment-${Date.now()}-${Math.random()}`

  executeMutation.mutate(
    {
      request: {
        qrToken: session.value.qrToken,
        amount: finalAmount.value,
        spendingScope: toQrPaymentSpendingScope(spendingScope.value),
        appointmentId: selectedAppointment.value?.appointmentId ?? null,
      },
      idempotencyKey: idempotencyKey.value,
    },
    {
      onSuccess: (response) => {
        idempotencyKey.value = null
        qrPaymentSession.clearSession()
        void router.push({
          name: 'wallet-qr-payment-complete',
          params: { transferId: String(response.transferId) },
        })
      },
    },
  )
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
      v-if="session === null"
      class="pt-6"
      aria-labelledby="wallet-qr-payment-preview-heading"
    >
      <h2
        id="wallet-qr-payment-preview-heading"
        class="sr-only"
      >
        {{ t('wallet.qrPayment.previewHeading') }}
      </h2>
      <StateEmpty
        :title="t('wallet.qrPayment.noSessionTitle')"
        :description="t('wallet.qrPayment.noSessionDescription')"
        :action-label="t('wallet.qrScan.rescan')"
        @action="goBack"
      />
    </section>

    <section
      v-else
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
          <div class="flex items-center justify-between gap-4 py-3 first:pt-0">
            <dt class="text-body-sm text-ink-2">{{ t('wallet.qrPayment.recipient') }}</dt>
            <dd class="text-right text-body-sm font-semibold">
              {{ session.resolved.payeeName }}
            </dd>
          </div>
        </dl>

        <div
          v-if="session.resolved.amountInputRequired"
          class="mt-1"
        >
          <AmountInput
            v-model="enteredAmount"
            :label="t('wallet.qrPayment.amount')"
            :helper="t('wallet.qrPayment.amountInputHelper')"
          />
        </div>
        <dl
          v-else
          class="divide-y divide-hairline"
        >
          <div class="flex items-center justify-between gap-4 py-3">
            <dt class="text-body-sm text-ink-2">{{ t('wallet.qrPayment.amount') }}</dt>
            <dd class="text-body-sm font-semibold">
              {{ formatKrw(session.resolved.amount ?? 0) }}
            </dd>
          </div>
        </dl>

        <p
          v-if="previewQuery.isPending.value"
          class="mt-3 text-body-sm text-ink-2"
        >
          {{ t('wallet.qrPayment.loadingPreview') }}
        </p>
        <p
          v-else-if="previewQuery.isError.value"
          role="alert"
          class="mt-3 rounded-sm bg-surface-3 px-3.5 py-3 text-body-sm text-ink-2"
        >
          {{ previewErrorMessage }}
        </p>
        <dl
          v-else-if="previewQuery.data.value"
          class="mt-1 divide-y divide-hairline"
        >
          <div class="flex items-center justify-between gap-4 py-3">
            <dt class="text-body-sm text-ink-2">{{ t('wallet.qrPayment.currentBalance') }}</dt>
            <dd class="text-body-sm font-semibold">
              {{ formatKrw(previewQuery.data.value.currentBalance) }}
            </dd>
          </div>
          <div class="flex items-center justify-between gap-4 py-3 last:pb-0">
            <dt class="text-body-sm text-ink-2">{{ t('wallet.qrPayment.balanceAfter') }}</dt>
            <dd class="text-body-sm font-semibold">
              {{ formatKrw(previewQuery.data.value.balanceAfter) }}
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

          <p
            v-if="ongoingAppointmentsQuery.isPending.value"
            class="mt-3 text-caption text-ink-3"
          >
            {{ t('wallet.qrPayment.appointmentsLoading') }}
          </p>
          <p
            v-else-if="ongoingAppointmentsQuery.isError.value"
            role="alert"
            class="mt-3 text-caption text-ink-3"
          >
            {{ t('wallet.qrPayment.appointmentsError') }}
          </p>
          <p
            v-else-if="(ongoingAppointmentsQuery.data.value ?? []).length === 0"
            class="mt-3 text-caption text-ink-3"
          >
            {{ t('wallet.qrPayment.appointmentsEmpty') }}
          </p>
          <template v-else>
            <div class="mt-3 space-y-2">
              <label
                v-for="appointment in ongoingAppointmentsQuery.data.value"
                :key="appointment.appointmentId"
                class="block cursor-pointer rounded-sm border p-3 transition-colors focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-ink"
                :class="
                  selectedAppointmentId === appointment.appointmentId
                    ? 'border-ink bg-surface-2'
                    : 'border-hairline bg-transparent'
                "
              >
                <input
                  v-model="selectedAppointmentId"
                  class="sr-only"
                  type="radio"
                  name="active-appointment"
                  :value="appointment.appointmentId"
                />
                <span class="flex items-center justify-between gap-3">
                  <span class="text-body-sm font-semibold">{{ appointment.appointmentName }}</span>
                  <span class="shrink-0 text-caption text-ink-3">
                    {{
                      formatAppointmentPeriod(
                        appointment.activityStartAt,
                        appointment.activityEndAt,
                      )
                    }}
                  </span>
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
          </template>
        </fieldset>
      </AppCard>

      <p
        v-if="executeMutation.isError.value"
        role="alert"
        class="rounded-sm bg-surface-3 px-3.5 py-3 text-body-sm text-ink-2"
      >
        {{ executeErrorMessage }}
      </p>

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
          {{
            executeMutation.isPending.value
              ? t('wallet.qrPayment.executing')
              : t('wallet.qrPayment.pay')
          }}
        </AppButton>
      </div>
    </section>
  </main>
</template>
