<script setup lang="ts">
import { useI18n } from 'vue-i18n'

import AppBadge from '@/shared/ui/AppBadge.vue'
import AppButton from '@/shared/ui/AppButton.vue'
import AppCard from '@/shared/ui/AppCard.vue'
import AppImage from '@/shared/ui/AppImage.vue'

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
 *
 * 방장 본인 행만 버튼 칸이 빈다. 방장은 어떤 상태에서도 자기 참여를 취소할 수 없고
 * (APPOINTMENT-007) 자기 프로필을 방문할 일도 없어, 둘 다 놓을 것이 없다.
 */
interface Props {
  members: AppointmentMember[]
  /** 로그인 회원의 참여 id. 목록에서 자기 자신을 알아볼 수 있게 표시한다. */
  currentAppointmentMemberId?: number | null
}

const { members, currentAppointmentMemberId = null } = defineProps<Props>()

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
  // 아니게 된다. 그때는 목록이 아니라 하단 CTA 위 문구가 이유를 말한다.
  return (
    currentAppointmentMemberId !== null && member.appointmentMemberId === currentAppointmentMemberId
  )
}

function showsLeave(member: AppointmentMember): boolean {
  return isCurrentMember(member) && !member.isHost
}

/** 방장 본인 행. 나가기도 프로필 방문도 놓을 것이 없어 버튼 칸을 비운다. */
function showsNoAction(member: AppointmentMember): boolean {
  return isCurrentMember(member) && member.isHost
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
            <AppImage
              :src="member.profileImageUrl"
              alt=""
              class="size-full object-cover"
            >
              <span>{{ initials(member.displayName) }}</span>
            </AppImage>
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
          <!-- 빨강(destructive)은 손실이 확정되는 순간에만 쓴다(#371) — 이 버튼은
               확인 모달을 한 번 더 거치고, 환급되는 탈퇴에서도 눌린다. 그래서
               보증금이 오가는 동작에 써 온 노랑을 쓴다. -->
          <AppButton
            v-if="showsLeave(member)"
            compact
            dense
            variant="settle"
            class="w-24"
            @click="emit('leave')"
          >
            {{ t('appointment.members.leave') }}
          </AppButton>
          <AppButton
            v-else-if="!showsNoAction(member)"
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
