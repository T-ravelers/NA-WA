<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { IconArrowLeft, IconTrash } from '@tabler/icons-vue'
import { useI18n } from 'vue-i18n'
import { RouterLink, useRoute, useRouter } from 'vue-router'

import { NormalizedApiError } from '@/shared/api/apiError'
import AmountInput from '@/shared/ui/AmountInput.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'
import TextInput from '@/shared/ui/TextInput.vue'

import {
  deleteJourney,
  updateJourney,
  type CompanionPreference,
  type Journey,
  type JourneyRegion,
  type JourneyUpdateInput,
} from '../api/journeyApi'
import JourneyDialog from '../components/JourneyDialog.vue'
import { journeyErrorMessageKey, isJourneyForbidden } from '../model/journeyErrors'
import { journeyKeys as journeyListKeys } from '../model/journeyKeys'
import {
  validateJourneyForm,
  type JourneyFormDraft,
  type JourneyFormErrors,
} from '../model/journeyForm'
import {
  journeyDetailQueryOptions,
  journeyKeys,
  journeyTimelineQueryOptions,
} from '../model/journeyQueries'

const i18n = useI18n()
const { t } = i18n
const route = useRoute()
const router = useRouter()
const queryClient = useQueryClient()

const tripId = computed(() => {
  const raw = Array.isArray(route.params.tripId) ? route.params.tripId[0] : route.params.tripId
  const parsed = Number(raw)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
})

const detailQuery = useQuery({
  ...journeyDetailQueryOptions(tripId),
  enabled: computed(() => tripId.value !== null),
  retry: false,
})
const timelineQuery = useQuery({
  ...journeyTimelineQueryOptions(tripId),
  enabled: computed(() => tripId.value !== null),
  retry: false,
})

const draft = reactive<JourneyFormDraft>({
  title: '',
  startDate: '',
  endDate: '',
  budgetAmount: null,
  companionPreference: null,
})
const preservedRegions = ref<JourneyRegion[]>([])
const submittedErrors = ref<JourneyFormErrors>({})
const initializedTripId = ref<number | null>(null)
const deleteDialog = ref<'confirm' | 'blocked' | null>(null)
const saveError = ref<string | null>(null)
const deleteError = ref<string | null>(null)
const companionOptions: CompanionPreference[] = ['1', '2-4', '5+']

function normalizeCompanion(value: string | null): CompanionPreference | null {
  return value === '1' || value === '2-4' || value === '5+' ? value : null
}

function normalizedInput(): JourneyUpdateInput {
  return {
    title: draft.title.trim(),
    startDate: draft.startDate,
    endDate: draft.endDate,
    budgetAmount: draft.budgetAmount,
    companionPreference: draft.companionPreference,
    regions: preservedRegions.value.map((region) => ({ ...region })),
  }
}

function snapshot(journey: Journey): string {
  return JSON.stringify({
    title: journey.title,
    startDate: journey.startDate,
    endDate: journey.endDate,
    budgetAmount: journey.budgetAmount,
    companionPreference: normalizeCompanion(journey.companionPreference),
    regions: journey.regions,
  })
}

watch(
  () => detailQuery.data.value,
  (journey) => {
    if (journey === undefined || initializedTripId.value === journey.tripId) return
    draft.title = journey.title
    draft.startDate = journey.startDate
    draft.endDate = journey.endDate
    draft.budgetAmount = journey.budgetAmount
    draft.companionPreference = normalizeCompanion(journey.companionPreference)
    preservedRegions.value = journey.regions.map((region) => ({ ...region }))
    submittedErrors.value = {}
    saveError.value = null
    initializedTripId.value = journey.tripId
  },
  { immediate: true },
)

const timelineItems = computed(() =>
  (timelineQuery.data.value?.timeline ?? []).flatMap((day) =>
    day.items.map((item) => ({ ...item, visitDate: day.visitDate })),
  ),
)
const hasDateConflict = computed(() =>
  timelineItems.value.some(
    (item) => item.visitDate < draft.startDate || item.visitDate > draft.endDate,
  ),
)
const currentErrors = computed(() => validateJourneyForm(draft))
const dirty = computed(() => {
  const journey = detailQuery.data.value
  return journey !== undefined && JSON.stringify(normalizedInput()) !== snapshot(journey)
})

const updateMutation = useMutation({
  mutationFn: async (input: JourneyUpdateInput) => {
    if (tripId.value === null) throw new Error('A valid trip id is required.')
    return updateJourney(tripId.value, input)
  },
  onSuccess: async (journey) => {
    saveError.value = null
    initializedTripId.value = null
    queryClient.setQueryData(journeyKeys.detail(journey.tripId), journey)
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: journeyListKeys.list() }),
      queryClient.invalidateQueries({ queryKey: journeyKeys.timeline(journey.tripId) }),
    ])
    await router.replace({ name: 'journey-detail', params: { tripId: journey.tripId } })
  },
  onError: (error) => {
    saveError.value = t(journeyErrorMessageKey(error, (key) => i18n.te(key)))
  },
})

const saveDisabled = computed(
  () =>
    !dirty.value ||
    hasDateConflict.value ||
    Object.keys(currentErrors.value).length > 0 ||
    updateMutation.isPending.value,
)

const deleteMutation = useMutation({
  mutationFn: async () => {
    if (tripId.value === null) throw new Error('A valid trip id is required.')
    await deleteJourney(tripId.value)
  },
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: journeyKeys.all })
    await router.replace({ name: 'journey-list' })
  },
  onError: (error) => {
    if (error instanceof NormalizedApiError && error.code === 'JOURNEY-011') {
      deleteDialog.value = 'blocked'
      return
    }
    deleteError.value = t(journeyErrorMessageKey(error, (key) => i18n.te(key)))
    deleteDialog.value = null
  },
})

function submit(): void {
  submittedErrors.value = currentErrors.value
  if (saveDisabled.value) return
  updateMutation.mutate(normalizedInput())
}

function requestDelete(): void {
  deleteError.value = null
  deleteDialog.value = 'confirm'
}

function closeDeleteDialog(): void {
  if (!deleteMutation.isPending.value) deleteDialog.value = null
}

function retryAll(): void {
  void detailQuery.refetch()
  void timelineQuery.refetch()
}
</script>

<template>
  <main class="flex w-full flex-col gap-5 px-screen py-8">
    <section
      v-if="tripId === null"
      role="alert"
    >
      <StateEmpty
        :title="t('journey.detail.invalidTitle')"
        :description="t('journey.detail.invalidDescription')"
      />
    </section>
    <StateLoading
      v-else-if="detailQuery.isPending.value || timelineQuery.isPending.value"
      :label="t('state.loading')"
    />
    <section
      v-else-if="isJourneyForbidden(detailQuery.error.value ?? timelineQuery.error.value)"
      role="alert"
    >
      <StateEmpty
        :title="t('journey.detail.accessDeniedTitle')"
        :description="t('journey.detail.accessDeniedDescription')"
      />
    </section>
    <StateError
      v-else-if="detailQuery.isError.value || timelineQuery.isError.value"
      :title="t('journey.settings.loadFailed')"
      :description="
        t(
          journeyErrorMessageKey(detailQuery.error.value ?? timelineQuery.error.value, (key) =>
            i18n.te(key),
          ),
        )
      "
      :action-label="t('action.retry')"
      @retry="retryAll"
    />

    <template v-else-if="detailQuery.data.value !== undefined">
      <header class="flex items-center gap-0.5">
        <RouterLink
          :to="{ name: 'journey-detail', params: { tripId: detailQuery.data.value.tripId } }"
          :aria-label="t('action.back')"
          class="-ml-3 flex size-11 shrink-0 items-center justify-center text-ink"
        >
          <IconArrowLeft
            :size="24"
            :stroke-width="1.75"
            aria-hidden="true"
          />
        </RouterLink>
        <h1 class="font-display text-screen-title uppercase text-ink-display">
          {{ t('journey.settings.title') }}
        </h1>
      </header>

      <form
        class="flex min-w-0 flex-col gap-5"
        novalidate
        @submit.prevent="submit"
      >
        <fieldset
          class="flex min-w-0 flex-col gap-5"
          :disabled="updateMutation.isPending.value"
        >
          <legend class="mb-3 font-display text-section-header uppercase text-ink-display">
            {{ t('journey.settings.basics') }}
          </legend>
          <TextInput
            v-model="draft.title"
            class="min-w-0"
            :label="t('journey.settings.name')"
            :error="submittedErrors.title === undefined ? undefined : t(submittedErrors.title)"
          />
          <div class="grid min-w-0 grid-cols-[minmax(0,1fr)_minmax(0,1fr)] gap-3">
            <TextInput
              v-model="draft.startDate"
              class="min-w-0"
              type="date"
              :label="t('journey.create.startDate')"
              :error="
                submittedErrors.startDate === undefined ? undefined : t(submittedErrors.startDate)
              "
            />
            <TextInput
              v-model="draft.endDate"
              class="min-w-0"
              type="date"
              :min="draft.startDate || undefined"
              :label="t('journey.create.endDate')"
              :error="
                submittedErrors.endDate === undefined ? undefined : t(submittedErrors.endDate)
              "
            />
          </div>
          <p
            v-if="hasDateConflict"
            class="rounded-sm border border-warning bg-warning/10 px-3.5 py-3 text-body-sm text-warning"
            role="alert"
          >
            {{ t('journey.settings.dateConflict') }}
          </p>
          <AmountInput
            v-model="draft.budgetAmount"
            class="min-w-0"
            currency-symbol="P"
            symbol-position="suffix"
            :label="t('journey.settings.budget')"
            :helper="t('journey.settings.budgetHelper')"
            :error="
              submittedErrors.budgetAmount === undefined
                ? undefined
                : t(submittedErrors.budgetAmount)
            "
          />
        </fieldset>

        <fieldset
          class="flex min-w-0 flex-col gap-3"
          :disabled="updateMutation.isPending.value"
        >
          <legend class="mb-3 font-display text-section-header uppercase text-ink-display">
            {{ t('journey.settings.preferences') }}
          </legend>
          <p class="text-caption text-ink-2">{{ t('journey.settings.companions') }}</p>
          <div class="grid grid-cols-3 gap-2">
            <button
              v-for="option in companionOptions"
              :key="option"
              type="button"
              class="h-12 rounded-sm text-body font-semibold"
              :class="
                draft.companionPreference === option
                  ? 'bg-paper-fill text-on-paper'
                  : 'bg-surface-2 text-ink-3'
              "
              :aria-pressed="draft.companionPreference === option"
              @click="
                draft.companionPreference = draft.companionPreference === option ? null : option
              "
            >
              {{ option }}
            </button>
          </div>
        </fieldset>

        <p
          v-if="saveError !== null"
          class="text-body-sm text-danger"
          role="alert"
        >
          {{ saveError }}
        </p>
        <AppButton
          type="submit"
          block
          :disabled="saveDisabled"
          :loading="updateMutation.isPending.value"
        >
          {{ t('journey.settings.save') }}
        </AppButton>

        <button
          type="button"
          class="flex h-13 w-full items-center justify-center gap-2 rounded-sm border border-danger text-title-sm font-semibold text-danger transition-transform active:scale-[0.98]"
          @click="requestDelete"
        >
          <IconTrash
            :size="18"
            :stroke-width="1.75"
            aria-hidden="true"
          />
          {{ t('journey.settings.delete') }}
        </button>
        <p
          v-if="deleteError !== null"
          class="text-body-sm text-danger"
          role="alert"
        >
          {{ deleteError }}
        </p>
      </form>
    </template>

    <JourneyDialog
      v-if="deleteDialog === 'confirm' && detailQuery.data.value !== undefined"
      id="delete-journey-dialog"
      :title="t('journey.delete.title')"
      :description="t('journey.delete.description')"
      :confirm-label="t('journey.remove.delete')"
      :cancel-label="t('journey.remove.cancel')"
      :pending="deleteMutation.isPending.value"
      destructive
      @confirm="deleteMutation.mutate()"
      @cancel="closeDeleteDialog"
    >
      <div class="mt-1 rounded-sm bg-canvas px-3.5 py-3">
        <p class="text-body-sm font-semibold text-ink">{{ detailQuery.data.value.title }}</p>
        <p class="mt-1 text-caption text-ink-3">
          {{ t('journey.delete.itemCount', { count: timelineItems.length }) }}
        </p>
      </div>
    </JourneyDialog>

    <JourneyDialog
      v-else-if="deleteDialog === 'blocked'"
      id="blocked-journey-dialog"
      :title="t('journey.delete.blockedTitle')"
      :description="t('journey.delete.blockedDescription')"
      :confirm-label="t('action.close')"
      single-action
      @confirm="closeDeleteDialog"
      @cancel="closeDeleteDialog"
    />
  </main>
</template>
