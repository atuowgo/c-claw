<template>
  <aside class="sidebar">
    <button class="new-chat-btn" @click="$emit('new-conversation')">
      + New Conversation
    </button>

    <div class="session-list">
      <div class="list-label">Recent Conversations</div>

      <div
        v-for="s in sessionsStore.sessions"
        :key="s.id"
        class="session-item"
        :class="{ active: chatStore.currentSessionId === s.id }"
        @click="$emit('select-conversation', s.id)"
      >
        <span class="session-title">{{ s.title || 'New conversation' }}</span>
        <span class="session-count">{{ s.messageCount }}</span>
      </div>

      <button class="view-all-btn" @click="$emit('view-all')">
        View all
      </button>
    </div>

    <div class="sidebar-bottom">
      <div class="divider"></div>
      <div class="user-row">
        <UserAvatar :name="settingsStore.nickname || 'U'" size="sm" />
        <span class="user-name">{{ settingsStore.nickname || 'User' }}</span>
        <button
          class="settings-btn"
          @click="$emit('open-settings')"
          title="Settings"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="3"/>
            <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/>
          </svg>
        </button>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useSessionsStore } from '../stores/sessions'
import { useChatStore } from '../stores/chat'
import { useSettingsStore } from '../stores/settings'
import UserAvatar from './UserAvatar.vue'

defineEmits<{
  (e: 'new-conversation'): void
  (e: 'select-conversation', id: string): void
  (e: 'view-all'): void
  (e: 'open-settings'): void
}>()

const sessionsStore = useSessionsStore()
const chatStore = useChatStore()
const settingsStore = useSettingsStore()

onMounted(() => {
  sessionsStore.fetchSessions()
})
</script>

<style scoped>
.sidebar {
  width: var(--sidebar-width);
  height: 100%;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--border-color);
  background: var(--surface-color);
  user-select: none;
}

.new-chat-btn {
  width: calc(100% - 24px);
  margin: 12px;
  padding: 10px 16px;
  background: var(--accent-color);
  color: #fff;
  border: none;
  border-radius: var(--radius-md);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background var(--transition-fast);
  flex-shrink: 0;
}

.new-chat-btn:hover {
  background: var(--accent-hover);
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px;
}

.list-label {
  padding: 8px 8px 4px;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--text-muted);
}

.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: background var(--transition-fast);
  font-size: 13px;
  color: var(--text-primary);
}

.session-item:hover {
  background: var(--surface-hover);
}

.session-item.active {
  background: var(--surface-active);
}

.session-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.session-count {
  font-size: 11px;
  color: var(--text-muted);
  margin-left: 8px;
  flex-shrink: 0;
}

.view-all-btn {
  display: block;
  width: 100%;
  padding: 8px 12px;
  background: none;
  border: none;
  color: var(--accent-color);
  font-size: 12px;
  cursor: pointer;
  text-align: left;
  transition: color var(--transition-fast);
}

.view-all-btn:hover {
  color: var(--accent-hover);
}

.sidebar-bottom {
  flex-shrink: 0;
}

.divider {
  height: 1px;
  background: var(--border-color);
  margin: 0 12px;
}

.user-row {
  display: flex;
  align-items: center;
  padding: 12px;
  gap: 8px;
}

.user-name {
  flex: 1;
  font-size: 13px;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

.settings-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  padding: 0;
  background: none;
  border: none;
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  cursor: pointer;
  transition: background var(--transition-fast), color var(--transition-fast);
  flex-shrink: 0;
}

.settings-btn:hover {
  background: var(--surface-hover);
  color: var(--text-primary);
}
</style>