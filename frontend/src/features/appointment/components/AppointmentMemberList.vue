<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import AppBadge from '@/shared/ui/AppBadge.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'

import type { AppointmentMember } from '../api/appointmentApi'

interface Props {
  members: AppointmentMember[]
  /** 로그인 회원의 참여 id. 목록에서 자기 자신을 알아볼 수 있게 표시한다. */
  currentAppointmentMemberId?: number | null
}

const { members, currentAppointmentMemberId = null } = defineProps<Props>()
const emit = defineEmits<{ select: [member: AppointmentMember] }>()
const { t } = useI18n()

function initials(displayName: string): string {
  return displayName.trim().charAt(0).toUpperCase() || '?'
}
</script>

<template>
  <ul class="flex flex-col gap-3">
    <li
      v-for="member in members"
      :key="member.appointmentMemberId"
    >
      <AppCard padding="base">
        <article class="flex items-center gap-3">
          <div
            class="flex size-12 shrink-0 items-center justify-center overflow-hidden rounded-pill bg-surface-3 text-title text-ink"
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

          <div class="min-w-0 flex-1">
            <div class="flex flex-wrap items-center gap-2">
              <h3 class="truncate text-title-sm text-ink">{{ member.displayName }}</h3>
              <AppBadge
                v-if="member.appointmentMemberId === currentAppointmentMemberId"
                tone="neutral"
              >
                {{ t('appointment.members.you') }}
              </AppBadge>
              <AppBadge
                v-if="member.isHost"
                tone="settlement"
              >
                {{ t('appointment.members.host') }}
              </AppBadge>
            </div>
            <p class="mt-1 text-caption text-ink-3">
              {{ t(`appointment.languages.${member.preferredLanguage}`) }}
            </p>
          </div>

          <AppButton
            compact
            dense
            variant="primary"
            :aria-label="t('appointment.members.viewProfile', { name: member.displayName })"
            @click="emit('select', member)"
          >
            {{ t('appointment.members.visit') }}
          </AppButton>
        </article>
      </AppCard>
    </li>
  </ul>
</template>
