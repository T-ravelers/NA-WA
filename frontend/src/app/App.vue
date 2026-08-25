<script setup lang="ts">
import { MotionConfig } from 'motion-v'

import AppShell from '@/app/layouts/AppShell.vue'
</script>

<template>
  <MotionConfig reduced-motion="user">
    <AppShell />
  </MotionConfig>
</template>
