<template>
  <div class="message-list" ref="listRef">
    <div v-if="messages.length === 0" class="empty-state">
      <div class="empty-icon">&#x1F916;</div>
      <h2>Ask me anything...</h2>
      <p>I'm your local AI assistant</p>
    </div>

    <div v-for="msg in messages" :key="msg.id"
         :class="['message', msg.role]">
      <div class="message-role">{{ msg.role === 'user' ? 'You' : 'Claw' }}</div>
      <div class="message-content" v-html="renderContent(msg)"></div>
      <div v-if="msg.isStreaming" class="streaming-indicator">
        <span class="cursor">|</span>
      </div>
    </div>

    <div v-if="error" class="error-banner">
      {{ error }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import type { Message } from '../stores/chat'

const props = defineProps<{
    messages: Message[]
    error: string | null
}>()

const listRef = ref<HTMLElement>()

function renderContent(msg: Message): string {
    if (msg.role === 'user') {
        // Escape HTML in user messages
        return escapeHtml(msg.content)
    }
    // Render assistant messages as Markdown
    try {
        const raw = marked.parse(msg.content, { async: false }) as string
        return DOMPurify.sanitize(raw)
    } catch {
        return escapeHtml(msg.content)
    }
}

function escapeHtml(text: string): string {
    const div = document.createElement('div')
    div.textContent = text
    return div.innerHTML
}

// Auto-scroll to bottom when new messages arrive
watch(() => props.messages.length, () => {
    nextTick(() => {
        if (listRef.value) {
            listRef.value.scrollTop = listRef.value.scrollHeight
        }
    })
})

// Also scroll during streaming updates
watch(() => {
    const lastMsg = props.messages[props.messages.length - 1]
    return lastMsg?.content.length ?? 0
}, () => {
    nextTick(() => {
        if (listRef.value) {
            listRef.value.scrollTop = listRef.value.scrollHeight
        }
    })
})
</script>

<style scoped>
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: var(--text-muted);
  gap: 8px;
  user-select: none;
}

.empty-icon {
  font-size: 3rem;
  margin-bottom: 8px;
}

.empty-state h2 {
  font-size: 1.25rem;
  color: var(--text-primary);
  margin: 0;
}

.empty-state p {
  font-size: 0.875rem;
  margin: 0;
}

.message {
  max-width: 70%;
  padding: 10px 14px;
  border-radius: 12px;
  line-height: 1.5;
}

.message.user {
  align-self: flex-end;
  background-color: var(--bubble-user);
  color: var(--text-primary);
  border-bottom-right-radius: 4px;
}

.message.assistant {
  align-self: flex-start;
  background-color: var(--bubble-assistant);
  color: var(--text-primary);
  border-bottom-left-radius: 4px;
}

.message-role {
  font-size: 0.75rem;
  color: var(--text-muted);
  margin-bottom: 4px;
  font-weight: 600;
}

.message-content {
  white-space: pre-wrap;
  word-break: break-word;
}

/* Markdown rendered content overrides */
.message-content :deep(p) {
  margin: 0 0 0.5em 0;
}

.message-content :deep(p:last-child) {
  margin-bottom: 0;
}

.message-content :deep(code) {
  background-color: rgba(255, 255, 255, 0.1);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.875em;
}

.message-content :deep(pre) {
  background-color: rgba(0, 0, 0, 0.3);
  padding: 12px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 0.5em 0;
}

.message-content :deep(pre code) {
  background: none;
  padding: 0;
}

.streaming-indicator {
  display: inline;
}

.cursor {
  animation: blink 1s step-end infinite;
}

@keyframes blink {
  50% { opacity: 0; }
}

.error-banner {
  background: rgba(255, 68, 68, 0.13);
  color: #ff4444;
  padding: 8px 16px;
  margin: 8px 0;
  border-radius: 8px;
  font-size: 0.875rem;
}
</style>