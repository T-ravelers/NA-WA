<script setup lang="ts">
import { useMutation } from '@tanstack/vue-query'
import { IconChevronRight, IconLogout } from '@tabler/icons-vue'
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'

import { applyLocale } from '@/app/i18n/applyLocale'
import { NormalizedApiError } from '@/shared/api/apiError'
import { requestSignOut } from '@/shared/api/sessionSignOut'
import type { AppLocale } from '@/shared/i18n/locales'
import { formatServerDateTime } from '@/shared/lib/datetime'
import ImagePlaceholder from '@/shared/ui/ImagePlaceholder.vue'
import LocaleSheet from '@/shared/ui/LocaleSheet.vue'
import SegmentedControl from '@/shared/ui/SegmentedControl.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import { updateMemberProfile } from '../api/memberApi'
import { useMyAppointments } from '../model/appointmentIntegration'
import { useSavedExploreItems } from '../model/exploreIntegration'
import { setMemberProfile, useMemberProfile } from '../model/memberQueries'

const i18n = useI18n()
const { t, locale } = i18n

const profileQuery = useMemberProfile()
const { data: profile, isPending, isError } = profileQuery

const isLocaleSheetOpen = ref(false)

/** `Saved | Appointments`와 그 안의 `Events | Places`. */
const tab = ref<'saved' | 'appointments'>('saved')
const kind = ref<'EVENT' | 'PLACE'>('EVENT')

/**
 * 화면에 표시하는 언어는 서버 값이 아니라 실제로 적용된 로케일이다.
 *
 * `members.preferred_language`는 `NOT NULL DEFAULT 'en'`이라 서버 값만으로는 "en을 골랐다"와
 * "고른 적 없다"가 구분되지 않는다. 저장 실패로 둘이 어긋났을 때 서버 값을 보여주면
 * 눈앞의 화면과 다른 언어를 가리키게 된다.
 */
const currentLocale = computed(() => locale.value as AppLocale)

const NATIVE_LABEL: Record<AppLocale, string> = {
  en: 'English',
  ja: '日本語',
  'zh-TW': '繁體中文',
  vi: 'Tiếng Việt',
}

/**
 * 국적 코드를 현재 언어의 나라 이름으로 옮긴다.
 *
 * 나라 이름 목록을 따로 두지 않는다 — `Intl.DisplayNames`가 로케일 4종을 모두 알고 있다.
 * 모르는 코드는 `of()`가 코드를 그대로 돌려주고, 형식이 어긋나면 예외를 던지므로
 * 둘 다 국적 줄을 생략하는 쪽으로 모은다.
 */
const nationalityName = computed(() => {
  const code = profile.value?.nationalityCode

  if (code === null || code === undefined || code === '') {
    return null
  }

  try {
    const name = new Intl.DisplayNames([locale.value], { type: 'region' }).of(code)
    return name === undefined || name === code ? null : name
  } catch {
    return null
  }
})

const savedQuery = useSavedExploreItems(
  kind,
  computed(() => tab.value === 'saved'),
)
const appointmentsQuery = useMyAppointments(computed(() => tab.value === 'appointments'))

/** 약속은 서버가 예정 → 지난 순으로 세워 준다. 화면은 종류로 거르기만 한다. */
const visibleAppointments = computed(
  () => appointmentsQuery.data.value?.filter((item) => item.itemType === kind.value) ?? [],
)

const emptyMessage = computed(() => {
  const group = tab.value === 'saved' ? 'saved' : 'appointments'
  const suffix = kind.value === 'EVENT' ? 'emptyEvents' : 'emptyPlaces'

  return t(`member.profile.${group}.${suffix}`)
})

function appointmentDetail(item: { activityStartAt: string; meetingPlace: string | null }): string {
  const when = formatServerDateTime(item.activityStartAt, locale.value, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })

  return [when, item.meetingPlace].filter((value) => Boolean(value)).join(' · ')
}

const saveLanguage = useMutation({
  mutationFn: (next: AppLocale) => updateMemberProfile({ preferredLanguage: next }),
  onSuccess: setMemberProfile,
})

const signOut = useMutation({ mutationFn: requestSignOut })

/** 저장 실패의 구체적 사유. 번역된 코드가 있을 때만 덧붙인다. */
const saveFailureReason = computed(() => {
  const error = saveLanguage.error.value

  // 문구가 없는 코드는 key 문자열이 그대로 화면에 찍히므로 있을 때만 덧붙인다.
  if (!(error instanceof NormalizedApiError) || !i18n.te(error.messageKey)) {
    return null
  }

  return t(error.messageKey)
})

/**
 * 사용자가 직접 고른 선택이므로 저장 실패를 삼키지 않는다.
 *
 * 다만 화면 언어는 되돌리지 않는다. 이미 고른 언어로 그려진 화면을 되돌리면 방금 누른
 * 선택이 이유 없이 튕겨 보인다. 기기에는 남았고 계정에는 못 남았다는 사실을 알린다.
 */
function chooseLocale(next: AppLocale): void {
  isLocaleSheetOpen.value = false

  if (next === currentLocale.value) {
    return
  }

  applyLocale(next, { persist: true })
  saveLanguage.mutate(next)
}
</script>

<template>
  <section class="px-screen pt-14 pb-8">
    <h1 class="font-display text-screen-title font-bold text-ink-display uppercase">
      {{ t('member.profile.title') }}
    </h1>

    <StateLoading v-if="isPending" />

    <StateError
      v-else-if="isError || profile === undefined"
      @retry="profileQuery.refetch()"
    />

    <template v-else>
      <div class="mt-6 flex items-center gap-3.5 rounded-sm bg-surface-2 px-3.5 py-3.5">
        <span class="size-14 shrink-0 overflow-hidden rounded-pill">
          <img
            v-if="profile.profileImageUrl !== null"
            :src="profile.profileImageUrl"
            alt=""
            class="size-full object-cover"
          />
          <ImagePlaceholder v-else />
        </span>
        <span class="flex min-w-0 flex-col gap-0.5">
          <span class="truncate text-title text-ink-display">{{ profile.displayName }}</span>
          <span
            v-if="nationalityName !== null"
            class="text-body-sm text-ink-2"
            >{{ t('member.profile.from', { country: nationalityName }) }}</span
          >
        </span>
      </div>

      <SegmentedControl
        v-model="tab"
        class="mt-6"
        :label="t('member.profile.title')"
        :options="[
          { value: 'saved', label: t('member.profile.tabs.saved') },
          { value: 'appointments', label: t('member.profile.tabs.appointments') },
        ]"
      />

      <SegmentedControl
        v-model="kind"
        class="mt-2"
        :label="
          tab === 'saved' ? t('member.profile.tabs.saved') : t('member.profile.tabs.appointments')
        "
        :options="[
          { value: 'EVENT', label: t('member.profile.kinds.events') },
          { value: 'PLACE', label: t('member.profile.kinds.places') },
        ]"
      />

      <div
        class="mt-4"
        data-testid="profile-list"
      >
        <template v-if="tab === 'saved'">
          <StateLoading v-if="savedQuery.isPending.value" />
          <StateError
            v-else-if="savedQuery.isError.value"
            @retry="savedQuery.refetch()"
          />
          <StateEmpty
            v-else-if="(savedQuery.data.value?.length ?? 0) === 0"
            :description="emptyMessage"
          />
          <ul
            v-else
            class="flex flex-col gap-2"
          >
            <li
              v-for="item in savedQuery.data.value"
              :key="item.itemId"
            >
              <RouterLink
                :to="
                  kind === 'EVENT'
                    ? `/explore/events/${item.itemId}`
                    : `/explore/places/${item.itemId}`
                "
                class="flex items-center gap-3 rounded-sm bg-surface-2 px-3.5 py-3"
              >
                <span class="size-11 shrink-0 overflow-hidden rounded-xs">
                  <img
                    v-if="item.thumbnailUrl !== null"
                    :src="item.thumbnailUrl"
                    alt=""
                    class="size-full object-cover"
                  />
                  <ImagePlaceholder v-else />
                </span>
                <span class="flex min-w-0 flex-1 flex-col gap-0.5">
                  <span class="truncate text-title-sm text-ink-display">{{ item.title }}</span>
                  <span
                    v-if="item.subtitle !== null"
                    class="truncate text-body-sm text-ink-3"
                    >{{ item.subtitle }}</span
                  >
                </span>
                <IconChevronRight
                  :size="18"
                  :stroke-width="1.75"
                  class="shrink-0 text-icon-muted"
                  aria-hidden="true"
                />
              </RouterLink>
            </li>
          </ul>
        </template>

        <template v-else>
          <StateLoading v-if="appointmentsQuery.isPending.value" />
          <StateError
            v-else-if="appointmentsQuery.isError.value"
            @retry="appointmentsQuery.refetch()"
          />
          <StateEmpty
            v-else-if="visibleAppointments.length === 0"
            :description="emptyMessage"
          />
          <ul
            v-else
            class="flex flex-col gap-2"
          >
            <li
              v-for="item in visibleAppointments"
              :key="item.appointmentId"
            >
              <RouterLink
                :to="`/appointments/${item.appointmentId}`"
                class="flex items-center gap-3 rounded-sm bg-surface-2 px-3.5 py-3"
              >
                <span class="flex min-w-0 flex-1 flex-col gap-0.5">
                  <span class="truncate text-title-sm text-ink-display">{{
                    item.appointmentName
                  }}</span>
                  <span class="truncate text-body-sm text-ink-3">{{
                    appointmentDetail(item)
                  }}</span>
                </span>
                <IconChevronRight
                  :size="18"
                  :stroke-width="1.75"
                  class="shrink-0 text-icon-muted"
                  aria-hidden="true"
                />
              </RouterLink>
            </li>
          </ul>
        </template>
      </div>

      <h2 class="mt-8 font-display text-section-header text-ink-display uppercase">
        {{ t('member.profile.preferences') }}
      </h2>

      <button
        type="button"
        class="mt-2 flex min-h-14 w-full items-center gap-3 rounded-sm bg-surface-2 px-3.5 text-left"
        :aria-label="t('member.profile.language.change')"
        data-testid="profile-language"
        @click="isLocaleSheetOpen = true"
      >
        <span class="flex-1 text-body text-ink">{{ t('member.profile.language.label') }}</span>
        <span class="text-body text-ink-2">{{ NATIVE_LABEL[currentLocale] }}</span>
        <IconChevronRight
          :size="18"
          :stroke-width="1.75"
          class="text-icon-muted"
          aria-hidden="true"
        />
      </button>

      <div
        v-if="saveLanguage.isError.value"
        role="alert"
        class="mt-2 flex flex-col gap-1 rounded-sm bg-surface-3 px-3.5 py-3"
      >
        <p class="text-body-sm text-ink-2">{{ t('member.profile.language.saveFailed') }}</p>
        <p
          v-if="saveFailureReason !== null"
          class="text-caption text-ink-3"
        >
          {{ saveFailureReason }}
        </p>
      </div>

      <!--
        통화는 아직 고르는 시트가 없다(#232 ②). 눌리지 않는 버튼을 두지 않고 값만 보여 준다.
      -->
      <div class="mt-2 flex min-h-14 w-full items-center gap-3 rounded-sm bg-surface-2 px-3.5">
        <span class="flex-1 text-body text-ink">{{ t('member.profile.currency.label') }}</span>
        <span class="text-body text-ink-2">{{
          profile.preferredCurrencyCode ?? t('member.profile.currency.notSet')
        }}</span>
      </div>

      <h2 class="mt-8 font-display text-section-header text-ink-display uppercase">
        {{ t('member.profile.account') }}
      </h2>

      <button
        type="button"
        class="mt-2 flex min-h-14 w-full items-center gap-3 rounded-sm bg-surface-2 px-3.5 text-left disabled:opacity-60"
        :aria-label="t('auth.signOut')"
        :disabled="signOut.isPending.value"
        @click="signOut.mutate()"
      >
        <span class="flex-1 text-body text-ink">{{ t('auth.signOut') }}</span>
        <IconLogout
          :size="18"
          :stroke-width="1.75"
          class="text-icon-muted"
          aria-hidden="true"
        />
      </button>
    </template>

    <LocaleSheet
      v-if="isLocaleSheetOpen"
      :model-value="currentLocale"
      :title="t('member.profile.language.sheetTitle')"
      :hint="t('member.profile.language.hint')"
      @update:model-value="chooseLocale"
      @close="isLocaleSheetOpen = false"
    />
  </section>
</template>
