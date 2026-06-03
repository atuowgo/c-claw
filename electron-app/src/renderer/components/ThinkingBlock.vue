<template>
  <div class="thinking-block" :class="{ collapsed: !expanded }">
    <div class="thinking-header" @click="toggle">
      <span class="thinking-chevron">{{ expanded ? '▼' : '▶' }}</span>
      <span class="thinking-label">Thinking</span>
    </div>
    <div class="thinking-body" ref="bodyRef">
      <div class="thinking-content" v-html="renderedContent"></div>
      <span v-if="isStreaming" class="thinking-dots">...</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = defineProps<{
  content: string
  isStreaming: boolean
}>()

const expanded = ref(true)
const bodyRef = ref<HTMLElement>()

let collapseTimer: ReturnType<typeof setTimeout> | null = null

const renderedContent = computed(() => {
  if (!props.content) return ''
  try {
    const raw = marked.parse(props.content, { async: false }) as string
    return DOMPurify.sanitize(raw)
  } catch {
    return ''
  }
})

function toggle() {
  expanded.value = !expanded.value
}

watch(() => props.isStreaming, (streaming) => {
  if (collapsedTimer) {
    clearTimeout(collapsedTimer)
    collapsedTimer = null
  }
  if (streaming) {
    expanded.value = true
  } else {
    collapsedTimer = setTimeout(() => {
      expanded.value = false
    }, 1500)
  }
})

// Auto-scroll to bottom while streaming
watch(() => props.content.length, () => {
  if (expanded.value && bodyRef.value) {
    nextTick(() => {
      bodyRef.value!.scrollTop = bodyRef.value!.scrollHeight
    })
  }
})
</script>

<style scoped>
.thinking-block {
  margin-bottom: 8px;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: rgba(0, 0, 0, 0.12);
  font-size: 0.85rem;
}

.thinking-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  cursor: pointer;
  color: var(--text-muted);
  user-select: none;
  font-size: 0.8rem;
}

.thinking-header:hover {
  color: var(--text-primary);
}

.thinking-chevron {
  font-size: 0.65rem;
  width: 12px;
  text-align: center;
}

.thinking-label {
  font-weight: 500;
}

.thinking-body {
  max-height: 300px;
  overflow-y: auto;
  padding: 0 10px 8px 10px;
  transition: max-height var(--transition-normal), padding var(--transition-normal);
  color: var(--text-muted);
  line-height: 1.5;
}

.collapsed .thinking-body {
  max-height: 0;
  padding-top: 0;
  padding-bottom: 0;
  overflow: hidden;
}

/* Markdown overrides within thinking block */
.thinking-content :deep(p) {
  margin: 0 0 0.4em 0;
}

.thinking-content :deep(p:last-child) {
  margin-bottom: 0;
}

.thinking-content :deep(code) {
  background-color: rgba(255, 255, 255, 0.06);
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 0.9em;
}

.thinking-content :deep(pre) {
  background-color: rgba(0, 0, 0, 0.2);
  padding: 8px;
  border-radius: var(--radius-sm);
  overflow-x: auto;
  margin: 0.3em 0;
}

.thinking-content :deep(pre code) {
  background: none;
  padding: 0;
}

.thinking-dots {
  display: inline-block;
  animation: blink 1s step-end infinite;
}

@keyframes blink {
  50% { opacity: 0; }
}
</style>