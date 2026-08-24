<script setup lang="ts">
import { IconCalendarEvent } from '@tabler/icons-vue'
import { nextTick, onBeforeUnmount, ref, useId, useTemplateRef } from 'vue'
import { useI18n } from 'vue-i18n'

import { formatCalendarDate } from '@/shared/lib/datetime'
import AppButton from '@/shared/ui/AppButton.vue'
import CalendarGrid from '@/shared/ui/CalendarGrid.vue'

interface Props {
  startDate: string
  endDate: string
  startLabel: string
  endLabel: string
  startError?: string
  endError?: string
}

const props = defineProps<Props>()

const emit = defineEmits<{
  'update:startDate': [value: string]
  'update:endDate': [value: string]
}>()

const { locale, t } = useI18n()

const pickerOpen = ref(false)
const target = ref<'start' | 'end'>('start')
const draftStart = ref('')
const draftEnd = ref('')
const dialog = useTemplateRef('dialog')
const endTargetButton = useTemplateRef('endTargetButton')
let opener: HTMLElement | null = null

const startId = useId()
const endId = useId()
const startErrorId = `${startId}-error`
const endErrorId = `${endId}-error`

function dateLabel(value: string): string {
  return value === ''
    ? t('journey.dateRange.chooseDate')
    : formatCalendarDate(value, locale.value, { dateStyle: 'short' }) || value
}

function dialogControls(): HTMLElement[] {
  return Array.from(
    dialog.value?.querySelectorAll<HTMLElement>('button:not([disabled]), [tabindex="0"]') ?? [],
  )
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape') {
    closePicker()
    return
  }

  if (event.key !== 'Tab') return

  const controls = dialogControls()
  const first = controls[0]
  const last = controls[controls.length - 1]
  if (first === undefined || last === undefined) return

  const activeElement = document.activeElement
  if (activeElement === null || !dialog.value?.contains(activeElement)) {
    event.preventDefault()
    const targetControl = event.shiftKey ? last : first
    targetControl.focus()
  } else if (event.shiftKey && activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

async function openPicker(nextTarget: 'start' | 'end'): Promise<void> {
  opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
  draftStart.value = props.startDate
  draftEnd.value = props.endDate
  target.value = nextTarget === 'end' && props.startDate === '' ? 'start' : nextTarget
  pickerOpen.value = true
  window.addEventListener('keydown', handleKeydown)

  await nextTick()
  dialogControls()[0]?.focus()
}

function closePicker(): void {
  const focusTarget = opener
  opener = null
  window.removeEventListener('keydown', handleKeydown)
  pickerOpen.value = false
  void nextTick(() => focusTarget?.focus())
}

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  if (pickerOpen.value) opener?.focus()
})

function chooseTarget(nextTarget: 'start' | 'end'): void {
  target.value = nextTarget === 'end' && draftStart.value === '' ? 'start' : nextTarget
}

function isDateAllowed(date: string): boolean {
  return target.value === 'start' || draftStart.value === '' || date >= draftStart.value
}

function selectDate(date: string): void {
  if (target.value === 'start') {
    draftStart.value = date
    if (draftEnd.value !== '' && draftEnd.value < date) draftEnd.value = ''
    target.value = 'end'
    void nextTick(() => endTargetButton.value?.focus())
    return
  }

  if (isDateAllowed(date)) draftEnd.value = date
}

function apply(): void {
  if (draftStart.value === '' || draftEnd.value === '') return

  emit('update:startDate', draftStart.value)
  emit('update:endDate', draftEnd.value)
  closePicker()
}
</script>

<template>
  <div>
    <div class="grid grid-cols-[minmax(0,1fr)_auto_minmax(0,1fr)] gap-x-2">
      <div class="flex min-w-0 flex-col gap-1.5">
        <label
          :for="startId"
          class="text-caption text-ink-2"
        >
          {{ startLabel }}
        </label>
        <button
          :id="startId"
          type="button"
          data-testid="journey-date-start"
          class="flex h-13 min-w-0 items-center gap-1.5 rounded-sm border-2 bg-surface-2 px-2 text-left outline-none focus-visible:border-ink"
          :class="startError ? 'border-danger' : 'border-transparent'"
          :aria-invalid="startError ? 'true' : undefined"
          :aria-describedby="startError ? startErrorId : undefined"
          aria-haspopup="dialog"
          :aria-expanded="pickerOpen"
          @click="openPicker('start')"
        >
          <IconCalendarEvent
            :size="16"
            class="shrink-0 text-icon-muted"
            aria-hidden="true"
          />
          <span class="min-w-0 truncate text-body-sm text-ink">{{ dateLabel(startDate) }}</span>
        </button>
      </div>

      <span
        aria-hidden="true"
        class="flex h-13 items-center self-end text-body text-ink-3"
      >
        –
      </span>

      <div class="flex min-w-0 flex-col gap-1.5">
        <label
          :for="endId"
          class="text-caption text-ink-2"
        >
          {{ endLabel }}
        </label>
        <button
          :id="endId"
          type="button"
          data-testid="journey-date-end"
          class="flex h-13 min-w-0 items-center gap-1.5 rounded-sm border-2 bg-surface-2 px-2 text-left outline-none focus-visible:border-ink"
          :class="endError ? 'border-danger' : 'border-transparent'"
          :aria-invalid="endError ? 'true' : undefined"
          :aria-describedby="endError ? endErrorId : undefined"
          aria-haspopup="dialog"
          :aria-expanded="pickerOpen"
          @click="openPicker('end')"
        >
          <IconCalendarEvent
            :size="16"
            class="shrink-0 text-icon-muted"
            aria-hidden="true"
          />
          <span class="min-w-0 truncate text-body-sm text-ink">{{ dateLabel(endDate) }}</span>
        </button>
      </div>
    </div>

    <div
      v-if="startError || endError"
      class="mt-1.5 grid grid-cols-2 gap-3"
    >
      <p
        v-if="startError"
        :id="startErrorId"
        class="text-caption text-danger"
      >
        {{ startError }}
      </p>
      <span v-else />
      <p
        v-if="endError"
        :id="endErrorId"
        class="text-caption text-danger"
      >
        {{ endError }}
      </p>
    </div>

    <div
      v-if="pickerOpen"
      class="fixed inset-0 z-40"
    >
      <button
        type="button"
        class="absolute inset-0 bg-scrim/70"
        :aria-label="t('action.close')"
        @click="closePicker"
      />

      <section
        ref="dialog"
        role="dialog"
        aria-modal="true"
        :aria-label="t('journey.dateRange.title')"
        class="absolute inset-x-0 bottom-0 z-10 mx-auto flex max-h-[88dvh] w-full max-w-shell flex-col rounded-t-lg bg-canvas px-screen pt-3 pb-6 shadow-sheet"
      >
        <span
          aria-hidden="true"
          class="mb-4 h-1 w-10 shrink-0 self-center rounded-pill bg-hairline-2"
        />

        <header>
          <h2 class="font-display text-section-header uppercase text-ink-display">
            {{ t('journey.dateRange.title') }}
          </h2>
          <p class="mt-1 text-body-sm text-ink-3">
            {{ t('journey.dateRange.description') }}
          </p>
        </header>

        <div class="mt-4 grid grid-cols-2 gap-2">
          <button
            type="button"
            data-testid="journey-date-target-start"
            class="min-w-0 rounded-sm border px-3 py-2 text-left"
            :class="
              target === 'start'
                ? 'border-paper-fill bg-paper-fill text-on-paper'
                : 'border-hairline bg-surface-1 text-ink'
            "
            :aria-pressed="target === 'start'"
            @click="chooseTarget('start')"
          >
            <span class="block text-micro">{{ startLabel }}</span>
            <span class="mt-0.5 block truncate text-body-sm">{{ dateLabel(draftStart) }}</span>
          </button>
          <button
            ref="endTargetButton"
            type="button"
            data-testid="journey-date-target-end"
            class="min-w-0 rounded-sm border px-3 py-2 text-left"
            :class="
              target === 'end'
                ? 'border-paper-fill bg-paper-fill text-on-paper'
                : 'border-hairline bg-surface-1 text-ink'
            "
            :aria-pressed="target === 'end'"
            :disabled="draftStart === ''"
            @click="chooseTarget('end')"
          >
            <span class="block text-micro">{{ endLabel }}</span>
            <span class="mt-0.5 block truncate text-body-sm">{{ dateLabel(draftEnd) }}</span>
          </button>
        </div>

        <p
          class="mt-3 text-caption text-ink-2"
          aria-live="polite"
        >
          {{
            t(target === 'start' ? 'journey.dateRange.selectStart' : 'journey.dateRange.selectEnd')
          }}
        </p>

        <CalendarGrid
          :key="target"
          class="mt-3"
          :range-start="draftStart || null"
          :range-end="draftEnd || null"
          :initial-month="
            target === 'end' ? draftEnd || draftStart || null : draftStart || draftEnd || null
          "
          :is-date-allowed="isDateAllowed"
          @select="selectDate"
        />

        <AppButton
          block
          class="mt-5"
          :disabled="draftStart === '' || draftEnd === ''"
          @click="apply"
        >
          {{ t('journey.dateRange.apply') }}
        </AppButton>
      </section>
    </div>
  </div>
</template>
