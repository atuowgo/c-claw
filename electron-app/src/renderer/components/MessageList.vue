<template>
  <div class="message-list" ref="listRef">
    <div v-if="messages.length === 0" class="empty-state">
      <div class="empty-icon">&#x1F916;</div>
      <h2>Ask me anything...</h2>
      <p>I'm your local AI assistant</p>
    </div>

    <div v-for="msg in messages" :key="msg.id" :class="['message-row', msg.role]">
      <AssistantAvatar v-if="msg.role === 'assistant'" size="md" class="avatar-slot" />

      <div class="bubble" :class="msg.role">
        <ThinkingBlock
          v-if="msg.role === 'assistant' && msg.thinking"
          :content="msg.thinking"
          :isStreaming="msg.isStreaming"
        />
        <div class="message-content" v-html="renderContent(msg)"></div>
        <span v-if="msg.isStreaming && isLastMessage(msg)" class="cursor">|</span>

        <div v-if="msg.role === 'assistant' && msg.toolCalls?.length" class="tool-calls-pill">
          <span class="tool-calls-icon">&#x1F527;</span>
          <span class="tool-calls-names">
            {{ msg.toolCalls.map(t => t.toolName).join(', ') }}
          </span>
        </div>
      </div>

      <UserAvatar v-if="msg.role === 'user'" name="You" size="md" class="avatar-slot" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import type { Message } from '../stores/chat'
import ThinkingBlock from './ThinkingBlock.vue'
import UserAvatar from './UserAvatar.vue'
import AssistantAvatar from './AssistantAvatar.vue'

const props = defineProps<{
  messages: Message[]
}>()

const listRef = ref<HTMLElement>()

function renderContent(msg: Message): string {
  if (msg.role === 'user') {
    return escapeHtml(msg.content)
  }
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

function isLastMessage(msg: Message): boolean {
  return props.messages.length > 0 && props.messages[props.messages.length - 1].id === msg.id
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
  gap: 16px;
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

/* Message row */
.message-row {
  display: flex;
  gap: 10px;
  max-width: 80%;
}

.message-row.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message-row.assistant {
  align-self: flex-start;
}

.avatar-slot {
  flex-shrink: 0;
  align-self: flex-end;
}

/* Bubble */
.bubble {
  padding: 10px 14px;
  border-radius: var(--radius-md);
  line-height: 1.5;
  min-width: 0;
}

.bubble.user {
  background-color: var(--bubble-user);
  color: var(--text-primary);
  border-bottom-right-radius: 4px;
}

.bubble.assistant {
  background-color: var(--bubble-assistant);
  color: var(--text-primary);
  border-bottom-left-radius: 4px;
}

/* Content */
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

/* Streaming cursor */
.cursor {
  animation: blink 1s step-end infinite;
}

@keyframes blink {
  50% { opacity: 0; }
}

/* Tool calls pill */
.tool-calls-pill {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  padding: 4px 10px;
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.06);
  font-size: 0.75rem;
  color: var(--text-muted);
}

.tool-calls-icon {
  font-size: 0.75rem;
}

.tool-calls-names {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 200px;
}
</style>