<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import AppBadge from '@/shared/ui/AppBadge.vue'
import { vFitText } from '@/shared/lib/fitText'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'

import type { AppointmentMember } from '../api/appointmentApi'
import { useAppointmentMemberStats } from '../model/memberIntegration'
import { appointmentMembersQueryOptions } from '../model/appointmentQueries'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

function routeNumber(name: 'appointmentId' | 'memberId'): number | null {
  const raw = Array.isArray(route.params[name]) ? route.params[name][0] : route.params[name]
  const parsed = Number(raw)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : null
}

const appointmentId = computed(() => routeNumber('appointmentId'))
const memberId = computed(() => routeNumber('memberId'))

const membersQuery = useQuery({
  ...appointmentMembersQueryOptions(appointmentId),
  enabled: computed(() => appointmentId.value !== null),
  retry: false,
})

const member = computed<AppointmentMember | undefined>(() =>
  membersQuery.data.value?.find((value) => value.memberId === memberId.value),
)

const profileStatsQuery = useAppointmentMemberStats(memberId)

const profileStats = computed(() => profileStatsQuery.data.value)

function indicatorValue(indicator: 'completionRate' | 'noShowCount' | 'averageRating'): string {
  const stats = profileStats.value
  if (!stats) return 'Unavailable'
  if (indicator === 'completionRate')
    return stats.completionRate === null ? 'No ratings yet' : `${stats.completionRate}%`
  if (indicator === 'averageRating')
    return stats.averageRating === null ? 'No ratings yet' : `${stats.averageRating.toFixed(1)} / 5`
  return String(stats.noShowCount)
}

function initials(displayName: string): string {
  return displayName.trim().charAt(0).toUpperCase() || '?'
}

function goBack(): void {
  if (window.history.length > 1) {
    void router.back()
    return
  }
  void router.push({ name: 'appointment-detail', params: { appointmentId: appointmentId.value } })
}
</script>

<template>
  <main class="flex min-h-dvh w-full flex-col gap-8 px-screen pb-8 pt-6">
    <header class="flex items-center gap-3">
      <AppButton
        compact
        variant="secondary"
        :aria-label="t('action.back')"
        @click="goBack"
      >
        ‹
      </AppButton>
      <h1
        v-fit-text
        class="min-w-0 flex-1 truncate font-display text-section-header text-ink-display"
      >
        {{ t('appointment.profile.title') }}
      </h1>
    </header>

    <StateEmpty
      v-if="appointmentId === null || memberId === null"
      :title="t('appointment.profile.invalidTitle')"
      :description="t('appointment.profile.invalidDescription')"
    />
    <StateLoading
      v-else-if="membersQuery.isPending.value"
      :label="t('appointment.profile.loading')"
    />
    <StateError
      v-else-if="membersQuery.isError.value"
      :title="t('appointment.profile.loadFailed')"
      :description="t('appointment.profile.loadFailedDescription')"
      :action-label="t('action.retry')"
      @retry="membersQuery.refetch"
    />
    <StateEmpty
      v-else-if="member === undefined"
      :title="t('appointment.profile.notFoundTitle')"
      :description="t('appointment.profile.notFoundDescription')"
    />
    <template v-else>
      <AppCard padding="lg">
        <section class="flex items-center gap-3">
          <div
            class="flex size-14 shrink-0 items-center justify-center overflow-hidden rounded-pill bg-surface-2 text-section-header text-ink"
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
          <div class="min-w-0">
            <div class="flex flex-wrap items-center gap-2">
              <h2 class="truncate text-title text-ink">{{ member.displayName }}</h2>
              <AppBadge
                v-if="member.isHost"
                tone="settlement"
              >
                {{ t('appointment.members.host') }}
              </AppBadge>
            </div>
            <p class="mt-1 text-body-sm text-ink-2">
              {{ t(`appointment.languages.${member.preferredLanguage}`) }}
            </p>
            <p class="mt-1 text-caption text-ink-3">
              {{
                profileStats?.reviewCount
                  ? `${profileStats.reviewCount} reviews`
                  : t('appointment.profile.ratingUnavailable')
              }}
            </p>
          </div>
        </section>
      </AppCard>

      <section class="flex flex-col gap-3">
        <h2 class="font-display text-title text-ink-display">
          {{ t('appointment.profile.trustIndicators') }}
        </h2>
        <AppCard
          v-for="indicator in ['completionRate', 'noShowCount', 'averageRating'] as const"
          :key="indicator"
          padding="base"
        >
          <div class="flex items-center justify-between gap-3">
            <span class="text-body-sm text-ink-2">
              {{ t(`appointment.profile.${indicator}`) }}
            </span>
            <span class="text-title-sm text-ink">{{ indicatorValue(indicator) }}</span>
          </div>
        </AppCard>
      </section>

      <div class="grid grid-cols-2 gap-3">
        <AppButton
          block
          variant="secondary"
          disabled
          :title="t('appointment.profile.actionsUnavailable')"
        >
          {{ t('appointment.profile.viewReviews') }}
        </AppButton>
        <AppButton
          block
          variant="secondary"
          disabled
          :title="t('appointment.profile.actionsUnavailable')"
        >
          {{ t('appointment.profile.report') }}
        </AppButton>
      </div>
    </template>
  </main>
</template>
