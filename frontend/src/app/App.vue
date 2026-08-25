<script setup lang="ts">
import { domAnimation, LazyMotion, MotionConfig } from 'motion-v'

import AppShell from '@/app/layouts/AppShell.vue'
</script>

<template>
  <MotionConfig reduced-motion="user">
    <LazyMotion
      :features="domAnimation"
      strict
    >
      <AppShell />
    </LazyMotion>
  </MotionConfig>
</template>
