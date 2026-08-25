<script setup lang="ts">
import { computed } from 'vue'

import type { Category } from '@/shared/ui/category'

import beautyDecoration from '../assets/category-beauty.svg'
import foodDecoration from '../assets/category-food.svg'
import shoppingDecoration from '../assets/category-shopping.svg'
import showDecoration from '../assets/category-show.svg'

interface Props {
  category: Category
  size?: 'sm' | 'lg'
}

const { category, size = 'sm' } = defineProps<Props>()

const ASSET: Record<Category, string> = {
  beauty: beautyDecoration,
  shopping: shoppingDecoration,
  show: showDecoration,
  food: foodDecoration,
}

const SMALL_GEOMETRY: Record<Category, { right: number; size: number }> = {
  beauty: { right: -12, size: 56 },
  shopping: { right: -6, size: 51 },
  show: { right: -31, size: 62 },
  food: { right: -9, size: 50 },
}

const geometry = computed(() =>
  size === 'lg' ? { right: -18, size: 74 } : SMALL_GEOMETRY[category],
)
</script>

<template>
  <img
    aria-hidden="true"
    class="pointer-events-none absolute top-1/2 max-w-none -translate-y-1/2"
    :src="ASSET[category]"
    :style="{
      right: `${geometry.right}px`,
      width: `${geometry.size}px`,
      height: `${geometry.size}px`,
    }"
  />
</template>
