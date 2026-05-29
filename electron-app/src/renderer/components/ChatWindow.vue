<template>
  <div class="chat-window">
    <MessageList
      :messages="store.messages"
      :error="store.error"
    />
    <InputBox
      :disabled="store.isSending"
      @send="store.sendMessage"
    />
  </div>
</template>

<script setup lang="ts">
import { useChatStore } from '../stores/chat'
import MessageList from './MessageList.vue'
import InputBox from './InputBox.vue'

const store = useChatStore()
</script>

<style scoped>
.chat-window {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
}
</style>