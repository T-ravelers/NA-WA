<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import AppBadge from '@/shared/ui/AppBadge.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'

import type { AppointmentMember } from '../api/appointmentApi'

/**
 * 약속 회원 목록.
 *
 * 나가기는 이 목록의 내 행에서 시작한다. 한때는 헤더 버거 메뉴에 있었는데,
 * "누가 나가는가"가 화면에 없는 자리라 자기 참여를 취소하는 것인지 약속을
 * 없애는 것인지 구분되지 않았다. 내 행 옆에 두면 대상이 곧 표시된다.
 *
 * 나가기 버튼은 지금 나갈 수 있는지와 무관하게 언제나 눌린다. 막히는 이유는 누른
 * 뒤 모달이 말한다 — 이유를 이름 옆에 상시로 적어 두면 목록이 안내문으로 찬다.
 * 예외는 방장이다: 방장은 어떤 상태에서도 자기 참여를 취소할 수 없어
 * (APPOINTMENT-007) 버튼을 줘도 영영 열리지 않으므로 다른 회원과 같은 Visit으로
 * 남긴다.
 */
interface Props {
  members: AppointmentMember[]
  /** 로그인 회원의 참여 id. 목록에서 자기 자신을 알아볼 수 있게 표시한다. */
  currentAppointmentMemberId?: number | null
  /** 내 행의 Visit을 나가기 버튼으로 바꾼다. 방장에게는 주지 않는다. */
  showLeave?: boolean
}

const { members, currentAppointmentMemberId = null, showLeave = false } = defineProps<Props>()

const emit = defineEmits<{
  select: [member: AppointmentMember]
  leave: []
}>()

const { t } = useI18n()

function initials(displayName: string): string {
  return displayName.trim().charAt(0).toUpperCase() || '?'
}

function isCurrentMember(member: AppointmentMember): boolean {
  // 참여 조회가 실패하면 currentAppointmentMemberId가 null이라 어느 행도 내 것이
  // 아니게 된다. 그때는 목록이 아니라 목록 위 안내가 이유를 말한다.
  return (
    currentAppointmentMemberId !== null && member.appointmentMemberId === currentAppointmentMemberId
  )
}

function showsLeave(member: AppointmentMember): boolean {
  return showLeave && isCurrentMember(member)
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
                v-if="isCurrentMember(member)"
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

          <!-- 두 버튼은 라벨 길이와 무관하게 같은 크기다. 폭을 고정하지 않으면
               로케일마다('Visit' / 'プロフィール') 행마다 크기가 갈린다. -->
          <AppButton
            v-if="showsLeave(member)"
            compact
            dense
            variant="destructive"
            class="w-24"
            @click="emit('leave')"
          >
            {{ t('appointment.members.leave') }}
          </AppButton>
          <AppButton
            v-else
            compact
            dense
            variant="primary"
            class="w-24"
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
