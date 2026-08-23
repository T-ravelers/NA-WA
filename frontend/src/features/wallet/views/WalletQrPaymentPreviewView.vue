<script setup lang="ts">
import { useMutation, useQuery } from '@tanstack/vue-query'
import { IconChevronLeft } from '@tabler/icons-vue'
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

import { NormalizedApiError } from '@/shared/api/apiError'
import { formatServerDateTime } from '@/shared/lib/datetime'
import {
  DEFAULT_SPENDING_CATEGORY,
  SPENDING_CATEGORIES,
  spendingCategoryLabelKey,
  type SpendingCategory,
} from '@/shared/lib/spendingCategory'
import AmountInput from '@/shared/ui/AmountInput.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import SegmentedControl from '@/shared/ui/SegmentedControl.vue'
import SelectChip from '@/shared/ui/SelectChip.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'
import { useRovingRadioGroup } from '@/shared/ui/useRovingRadioGroup'

import { executeQrPayment, previewQrPayment } from '../api/qrPaymentApi'
import { useWalletAppointmentIntegration } from '../model/appointmentIntegration'
import {
  formatPoints,
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
const { useMyTodayAppointmentsQuery } = useWalletAppointmentIntegration()

const session = computed(() => qrPaymentSession.session)

const spendingScope = ref<SpendingScope>('personal')
const selectedAppointmentId = ref<number | null>(null)
const enteredAmount = ref<number | null>(null)

/**
 * 결제자가 고르는 소비 카테고리.
 *
 * 오프라인 결제 순간이라 선택을 강제하지 않는다. 기본값은 서버가 값 없는 결제를 접는
 * 값과 같은 `OTHER`다 — 고르지 않았을 때 임의의 카테고리를 넣으면 리포트가 거짓 근거로
 * 칭호를 만든다.
 */
const spendingCategory = ref<SpendingCategory>(DEFAULT_SPENDING_CATEGORY)

const spendingCategoryOptions = computed(() =>
  SPENDING_CATEGORIES.map((value) => ({ value, label: t(spendingCategoryLabelKey(value)) })),
)

/* 칩 일곱 개가 전부 탭 스톱이면 결제 버튼까지 가는 데 탭을 일곱 번 더 눌러야 한다(#305). */
const { onKeydown: onCategoryKeydown, tabindexFor: categoryTabindex } = useRovingRadioGroup(
  () => SPENDING_CATEGORIES,
  () => spendingCategory.value,
  (value) => {
    spendingCategory.value = value
  },
)

/** 조형과 접근성 트리가 같은 판정을 쓰게 묶는다. 둘이 어긋나면 아무도 못 잡는다. */
function isSelectedCategory(value: SpendingCategory): boolean {
  return value === spendingCategory.value
}

const isSharedExpense = computed(() => spendingScope.value === 'shared')
const todayAppointmentsQuery = useMyTodayAppointmentsQuery(isSharedExpense)

const spendingOptions = computed(() => [
  { value: 'personal', label: t('wallet.qrPayment.personal') },
  { value: 'shared', label: t('wallet.qrPayment.shared') },
])
const selectedAppointment = computed(() =>
  todayAppointmentsQuery.data.value?.find(
    (appointment) => appointment.appointmentId === selectedAppointmentId.value,
  ),
)

// Shared에서 고른 약속이 Personal로 돌아간 뒤에도 남아있으면, 백엔드가
// PERSONAL + appointmentId 조합을 거부한다(QR_PERSONAL_APPOINTMENT_NOT_ALLOWED).
watch(spendingScope, (scope) => {
  if (scope !== 'shared') {
    selectedAppointmentId.value = null
  }
})

function formatAppointmentPeriod(activityStartAt: string, activityEndAt: string): string {
  const options = { month: 'short' as const, day: 'numeric' as const }
  const start = formatServerDateTime(activityStartAt, locale.value, options)
  const end = formatServerDateTime(activityEndAt, locale.value, options)

  if (start === '' || end === '') return ''

  return `${start} – ${end}`
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
      spendingCategory.value,
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
        spendingCategory: spendingCategory.value,
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
            currency-symbol="P"
            symbol-position="suffix"
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
              {{ formatPoints(session.resolved.amount ?? 0) }}
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
              {{ formatPoints(previewQuery.data.value.currentBalance) }}
            </dd>
          </div>
          <div class="flex items-center justify-between gap-4 py-3 last:pb-0">
            <dt class="text-body-sm text-ink-2">{{ t('wallet.qrPayment.balanceAfter') }}</dt>
            <dd class="text-body-sm font-semibold">
              {{ formatPoints(previewQuery.data.value.balanceAfter) }}
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

          <StateLoading
            v-if="todayAppointmentsQuery.isPending.value"
            class="mt-3"
            :lines="2"
            :label="t('wallet.qrPayment.appointmentsLoading')"
          />
          <StateError
            v-else-if="todayAppointmentsQuery.isError.value"
            class="mt-3"
            :description="t('wallet.qrPayment.appointmentsError')"
            @retry="todayAppointmentsQuery.refetch"
          />
          <StateEmpty
            v-else-if="(todayAppointmentsQuery.data.value ?? []).length === 0"
            class="mt-3"
            :description="t('wallet.qrPayment.appointmentsEmpty')"
          />
          <template v-else>
            <div class="mt-3 space-y-2">
              <label
                v-for="appointment in todayAppointmentsQuery.data.value"
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

      <AppCard padding="lg">
        <h3 class="text-title-sm">{{ t('wallet.qrPayment.spendingCategory') }}</h3>
        <p class="mt-1 text-caption text-ink-3">
          {{ t('wallet.qrPayment.spendingCategoryHint') }}
        </p>

        <div
          role="radiogroup"
          :aria-label="t('wallet.qrPayment.spendingCategory')"
          class="mt-4 flex flex-wrap gap-2"
          @keydown="onCategoryKeydown"
        >
          <SelectChip
            v-for="option in spendingCategoryOptions"
            :key="option.value"
            interactive
            :label="option.label"
            :selected="isSelectedCategory(option.value)"
            role="radio"
            :data-testid="`payment-category-${option.value}`"
            :data-value="option.value"
            :aria-checked="isSelectedCategory(option.value)"
            :tabindex="categoryTabindex(option.value)"
            @toggle="spendingCategory = option.value"
          />
        </div>
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
