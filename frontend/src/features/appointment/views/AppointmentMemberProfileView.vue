<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'

import { getAvatarInitial } from '@/shared/lib/avatarInitial'
import AppBadge from '@/shared/ui/AppBadge.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import AppImage from '@/shared/ui/AppImage.vue'
import StateEmpty from '@/shared/ui/StateEmpty.vue'
import StateError from '@/shared/ui/StateError.vue'
import StateLoading from '@/shared/ui/StateLoading.vue'
import ScreenHeader from '@/shared/ui/ScreenHeader.vue'

import type { AppointmentMember } from '../api/appointmentApi'
import { useAppointmentMemberStats } from '../model/memberIntegration'
import { appointmentMembersQueryOptions } from '../model/appointmentQueries'

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()

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

/**
 * 신뢰 점수 한 줄(#173 「신뢰도 점수」). 평점과 후기 수만 보여 준다.
 * 출석률·노쇼 수는 공개 범위를 정하지 않아 본인 조회에서도 그리지 않는다.
 * 조회에 실패하면(열람 범위 밖 403 포함) 줄을 그리지 않는다.
 */
const trustLine = computed<string | null>(() => {
  const stats = profileStatsQuery.data.value
  if (!stats) return null
  if (stats.reviewCount === 0 || stats.averageRating === null)
    return t('appointment.profile.noReviews')
  const rating = new Intl.NumberFormat(locale.value, {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  }).format(stats.averageRating)
  return t('appointment.profile.trustLine', {
    rating,
    count: stats.reviewCount,
  })
})

function goBack(): void {
  if (window.history.length > 1) {
    void router.back()
    return
  }
  void router.push({ name: 'appointment-detail', params: { appointmentId: appointmentId.value } })
}
</script>

<template>
  <main class="flex w-full flex-col gap-8 px-screen flex-1 pt-6 pb-8">
    <ScreenHeader
      variant="back"
      :title="t('appointment.profile.title')"
      :back-label="t('action.back')"
      @back="goBack"
    />

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
            class="flex size-14 shrink-0 items-center justify-center overflow-hidden rounded-pill bg-surface-3 text-section-header text-ink"
            aria-hidden="true"
          >
            <AppImage
              :src="member.profileImageUrl"
              alt=""
              class="size-full object-cover"
            >
              <span>{{ getAvatarInitial(member.displayName) }}</span>
            </AppImage>
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
            <p
              v-if="trustLine !== null"
              class="mt-1 text-caption text-ink-3"
              data-testid="member-trust-line"
            >
              {{ trustLine }}
            </p>
          </div>
        </section>
      </AppCard>
    </template>
  </main>
</template>
