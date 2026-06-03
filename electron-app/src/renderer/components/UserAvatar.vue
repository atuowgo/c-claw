<template>
  <div class="avatar" :class="size" :style="{ backgroundColor: bgColor }">
    {{ name.charAt(0).toUpperCase() }}
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  name: string
  size?: 'sm' | 'md' | 'lg'
}>(), {
  size: 'md'
})

function hash(str: string): number {
  let h = 0
  for (let i = 0; i < str.length; i++) {
    h = (Math.imul(31, h) + str.charCodeAt(i)) | 0
  }
  return Math.abs(h)
}

const bgColor = computed(() => {
  const h = hash(props.name) % 360
  return `hsl(${h}, 50%, 45%)`
})
</script>

<style scoped>
.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-xl);
  color: var(--text-primary);
  font-weight: 600;
  font-size: 14px;
  flex-shrink: 0;
  transition: opacity var(--transition-fast);
}

.avatar:hover {
  opacity: 0.9;
}

.avatar.sm {
  width: 32px;
  height: 32px;
  font-size: 12px;
}

.avatar.md {
  width: 40px;
  height: 40px;
  font-size: 14px;
}

.avatar.lg {
  width: 48px;
  height: 48px;
  font-size: 18px;
}
</style>