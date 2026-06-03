<template>
  <div class="assistant-avatar" :class="size">
    <svg width="60%" height="60%" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
      <rect x="3" y="3" width="18" height="12" rx="2" />
      <path d="M7 21h10" />
      <path d="M12 17v4" />
    </svg>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  size?: 'sm' | 'md' | 'lg'
}>(), {
  size: 'md'
})
</script>

<style scoped>
.assistant-avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-xl);
  background-color: var(--surface-active);
  color: var(--text-muted);
  flex-shrink: 0;
}

.assistant-avatar.sm {
  width: 32px;
  height: 32px;
}

.assistant-avatar.md {
  width: 40px;
  height: 40px;
}

.assistant-avatar.lg {
  width: 48px;
  height: 48px;
}
</style>