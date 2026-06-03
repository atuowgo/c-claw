<template>
  <div class="conversations-view">
    <header class="view-header">
      <button class="back-btn" @click="$emit('back')">← Back</button>
      <h1 class="view-title">Conversations</h1>
    </header>

    <div class="search-bar">
      <input
        v-model="searchQuery"
        type="text"
        class="search-input"
        placeholder="Search conversations..."
      />
    </div>

    <div class="conversation-list">
      <!-- Loading state -->
      <div v-if="sessionsStore.isLoading" class="state-block">
        <div v-for="n in 5" :key="n" class="skeleton-row" />
      </div>

      <!-- Error state -->
      <div v-else-if="sessionsStore.error" class="state-block">
        <p class="state-message error">Failed to load</p>
        <button class="retry-btn" @click="sessionsStore.fetchSessions(100)">Retry</button>
      </div>

      <!-- Empty state -->
      <div v-else-if="filteredSessions.length === 0" class="state-block">
        <p class="state-message">No conversations yet. Start a new chat!</p>
      </div>

      <!-- Session list -->
      <div v-else class="session-items">
        <div
          v-for="s in filteredSessions"
          :key="s.id"
          class="session-row"
          @click="onSelectSession(s.id)"
        >
          <div class="session-main">
            <input
              v-if="editingId === s.id"
              ref="editInputRef"
              v-model="editTitle"
              class="inline-edit-input"
              @keydown.enter="confirmRename(s.id)"
              @blur="confirmRename(s.id)"
              @click.stop
            />
            <span
              v-else
              class="session-title"
              @click.stop="startRename(s.id, s.title)"
            >{{ s.title || 'Untitled' }}</span>
            <span class="session-meta">
              {{ formatDate(s.createdAt) }} · {{ s.messageCount }} messages
            </span>
          </div>
          <button
            class="delete-btn"
            @click.stop="onDelete(s.id)"
            title="Delete"
          >Delete</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useSessionsStore, type SessionInfo } from '../stores/sessions'

const emit = defineEmits<{
  (e: 'back'): void
  (e: 'select', id: string): void
}>()

const sessionsStore = useSessionsStore()

const searchQuery = ref('')
const editingId = ref<string | null>(null)
const editTitle = ref('')
const editInputRef = ref<HTMLInputElement | null>(null)

const filteredSessions = computed(() => {
  const q = searchQuery.value.toLowerCase().trim()
  if (!q) return sessionsStore.sessions
  return sessionsStore.sessions.filter(s =>
    (s.title || '').toLowerCase().includes(q)
  )
})

function formatDate(ts: number): string {
  const d = new Date(ts)
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

function onSelectSession(id: string) {
  if (editingId.value === id) return
  emit('select', id)
}

function startRename(id: string, title: string | null) {
  editingId.value = id
  editTitle.value = title || ''
  nextTick(() => {
    editInputRef.value?.focus()
    editInputRef.value?.select()
  })
}

function confirmRename(id: string) {
  if (editingId.value !== id) return
  const newTitle = editTitle.value.trim()
  if (newTitle) {
    sessionsStore.renameSession(id, newTitle)
  }
  editingId.value = null
  editTitle.value = ''
}

function onDelete(id: string) {
  if (confirm('Delete this conversation?')) {
    sessionsStore.deleteSession(id)
  }
}

onMounted(() => {
  sessionsStore.fetchSessions(100)
})
</script>

<style scoped>
.conversations-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--surface-color);
}

.view-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.back-btn {
  padding: 4px 10px;
  background: none;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  color: var(--text-primary);
  font-size: 13px;
  cursor: pointer;
  transition: background var(--transition-fast);
  flex-shrink: 0;
}

.back-btn:hover {
  background: var(--accent-color);
  color: #fff;
  border-color: var(--accent-color);
}

.view-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
}

.search-bar {
  padding: 12px 20px;
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.search-input {
  width: 100%;
  padding: 8px 12px;
  font-size: 14px;
  background: var(--surface-color);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  color: var(--text-primary);
  outline: none;
  transition: border-color var(--transition-fast);
}

.search-input:focus {
  border-color: var(--accent-color);
}

.search-input::placeholder {
  color: var(--text-muted);
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 20px;
}

.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 16px;
  gap: 12px;
}

.state-message {
  font-size: 14px;
  color: var(--text-muted);
  margin: 0;
}

.state-message.error {
  color: var(--danger-color);
}

.retry-btn {
  padding: 6px 20px;
  font-size: 13px;
  background: var(--accent-color);
  color: #fff;
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.retry-btn:hover {
  background: var(--accent-hover);
}

.skeleton-row {
  height: 48px;
  width: 100%;
  background: linear-gradient(90deg, var(--border-color) 25%, transparent 50%, var(--border-color) 75%);
  background-size: 200% 100%;
  border-radius: var(--radius-md);
  animation: shimmer 1.5s infinite;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.session-items {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.session-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.session-row:hover {
  background: var(--surface-hover);
}

.session-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  overflow: hidden;
  flex: 1;
  min-width: 0;
}

.session-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: text;
}

.inline-edit-input {
  font-size: 14px;
  padding: 2px 6px;
  background: var(--surface-color);
  border: 1px solid var(--accent-color);
  border-radius: var(--radius-md);
  color: var(--text-primary);
  outline: none;
  width: 100%;
}

.session-meta {
  font-size: 12px;
  color: var(--text-muted);
}

.delete-btn {
  padding: 4px 10px;
  font-size: 12px;
  background: none;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  color: var(--danger-color);
  cursor: pointer;
  transition: background var(--transition-fast), color var(--transition-fast);
  flex-shrink: 0;
}

.delete-btn:hover {
  background: var(--danger-color);
  color: #fff;
  border-color: var(--danger-color);
}
</style>