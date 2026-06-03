<template>
  <div class="chat-view">
    <div v-if="chatStore.error" class="error-banner">
      <span>{{ chatStore.error }}</span>
      <button class="error-dismiss" @click="chatStore.error = null">&times;</button>
    </div>
    <MessageList :messages="chatStore.messages" />
    <InputBox :disabled="chatStore.isSending" @send="chatStore.sendMessage($event)" />
  </div>
</template>

<script setup lang="ts">
import { useChatStore } from '../stores/chat'
import MessageList from './MessageList.vue'
import InputBox from './InputBox.vue'

const chatStore = useChatStore()
</script>

<style scoped>
.chat-view {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.error-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(224, 85, 106, 0.15);
  color: var(--danger-color);
  padding: 8px 16px;
  font-size: 0.875rem;
  flex-shrink: 0;
}

.error-dismiss {
  background: none;
  border: none;
  color: var(--danger-color);
  font-size: 1.125rem;
  cursor: pointer;
  padding: 0 4px;
  line-height: 1;
}
</style>