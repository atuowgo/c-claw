<template>
  <div class="app-layout">
    <!-- Left Sidebar -->
    <Sidebar
      @new-conversation="onNewConversation"
      @select-conversation="onSelectConversation"
      @view-all="onViewAll"
      @open-settings="onOpenSettings"
    />

    <!-- Center Area -->
    <div class="center">
      <Transition name="view-fade" mode="out-in">
        <ChatView v-if="activeView === 'chat'" key="chat" />
        <ConversationsView
          v-else-if="activeView === 'conversations'"
          key="conv"
          @back="activeView = 'chat'"
          @select="onSelectConversation"
        />
        <SettingsView
          v-else-if="activeView === 'settings'"
          key="settings"
          @back="activeView = 'chat'"
        />
      </Transition>
    </div>

    <!-- Right Panel -->
    <ToolTracePanel />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useChatStore } from './stores/chat'
import Sidebar from './components/Sidebar.vue'
import ChatView from './components/ChatView.vue'
import ConversationsView from './components/ConversationsView.vue'
import SettingsView from './components/SettingsView.vue'
import ToolTracePanel from './components/ToolTracePanel.vue'

type View = 'chat' | 'conversations' | 'settings'
const activeView = ref<View>('chat')

const chatStore = useChatStore()

async function onNewConversation() {
  await chatStore.createSession()
  activeView.value = 'chat'
}

function onSelectConversation(sessionId: string) {
  chatStore.loadSession(sessionId)
  activeView.value = 'chat'
}

function onViewAll() {
  activeView.value = 'conversations'
}

function onOpenSettings() {
  activeView.value = 'settings'
}
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
  background: var(--bg-color);
}

.center {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.view-fade-enter-active,
.view-fade-leave-active {
  transition: opacity 0.15s ease;
}

.view-fade-enter-from,
.view-fade-leave-to {
  opacity: 0;
}
</style>