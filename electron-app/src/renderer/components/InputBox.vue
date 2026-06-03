<template>
  <div class="input-area">
    <textarea
      ref="inputRef"
      v-model="text"
      class="input-field"
      :disabled="disabled"
      placeholder="Type a message... (Enter to send, Shift+Enter for newline)"
      rows="1"
      @keydown="handleKeydown"
    ></textarea>
    <button class="send-button" @click="send" :disabled="disabled || !text.trim()">
      {{ disabled ? '...' : 'Send' }}
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{
  disabled: boolean
}>()

const emit = defineEmits<{
  send: [text: string]
}>()

const text = ref('')
const inputRef = ref<HTMLTextAreaElement | null>(null)

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    send()
  }
}

function send(): void {
  const trimmed = text.value.trim()
  if (!trimmed || props.disabled) return

  emit('send', trimmed)
  text.value = ''

  // Refocus the input after sending
  setTimeout(() => {
    inputRef.value?.focus()
  }, 0)
}
</script>

<style scoped>
.input-area {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--border-color);
  background-color: var(--surface-color);
}

.input-field {
  flex: 1;
  resize: none;
  padding: 10px 14px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background-color: var(--bg-color);
  color: var(--text-primary);
  font-family: inherit;
  font-size: 14px;
  line-height: 1.4;
  max-height: 120px;
  outline: none;
  transition: border-color 0.15s ease;
}

.input-area:focus-within {
  box-shadow: 0 0 0 2px rgba(74, 124, 247, 0.3);
}

.input-field:focus {
  border-color: var(--accent-color);
}

.input-field::placeholder {
  color: var(--text-muted);
}

.input-field:disabled {
  opacity: 0.6;
}

.send-button {
  padding: 10px 20px;
  border: none;
  border-radius: var(--radius-md);
  background-color: var(--accent-color);
  color: #fff;
  font-family: inherit;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.15s ease;
  white-space: nowrap;
  flex-shrink: 0;
}

.send-button:hover:not(:disabled) {
  background-color: var(--accent-hover);
}

.send-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>