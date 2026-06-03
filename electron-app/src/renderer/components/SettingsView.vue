<template>
  <div class="settings-view">
    <header class="view-header">
      <button class="back-btn" @click="$emit('back')">← Back</button>
      <h1 class="view-title">Settings</h1>
    </header>

    <div class="settings-content">
      <!-- Profile -->
      <section class="settings-group">
        <h2 class="group-label">Profile</h2>
        <FormField label="Nickname" :model-value="settings.nickname" @update:model-value="onNicknameChange" />
      </section>

      <!-- API -->
      <section class="settings-group">
        <h2 class="group-label">API</h2>
        <FormField label="API Key" type="password" :model-value="settings.apiKey" @update:model-value="onApiKeyChange" />
        <FormField label="Model Name" :model-value="settings.modelName" @update:model-value="onModelNameChange" />
        <FormField label="Proxy URL" :model-value="settings.proxyUrl" @update:model-value="onProxyUrlChange" />
      </section>

      <!-- Skills -->
      <section class="settings-group">
        <h2 class="group-label">Skills</h2>
        <div v-if="settings.skillsLoading" class="state-block">
          <p class="placeholder-text">Loading skills...</p>
        </div>
        <div v-else-if="settings.skills.length === 0" class="state-block">
          <p class="placeholder-text">No skills installed</p>
          <p class="hint-text">Add skills to ~/.c-claw/skills/</p>
        </div>
        <div v-else class="skill-list">
          <div v-for="skill in settings.skills" :key="skill.name" class="skill-item">
            <div class="skill-info">
              <span class="skill-name">{{ skill.name }}</span>
              <span class="skill-version">v{{ skill.version }}</span>
            </div>
            <p v-if="skill.description" class="skill-desc">{{ skill.description }}</p>
          </div>
        </div>
      </section>

      <!-- Shortcuts -->
      <section class="settings-group">
        <h2 class="group-label">Shortcuts</h2>
        <div class="shortcut-list">
          <div class="shortcut-row">
            <kbd>Ctrl + N</kbd>
            <span>New conversation</span>
          </div>
          <div class="shortcut-row">
            <kbd>Ctrl + Enter</kbd>
            <span>Send</span>
          </div>
          <div class="shortcut-row">
            <kbd>Alt + Space</kbd>
            <span>Toggle window</span>
          </div>
        </div>
      </section>

      <!-- Data -->
      <section class="settings-group">
        <h2 class="group-label">Data</h2>
        <FormField label="Storage Path" :model-value="settings.storagePath" readonly />
        <button class="action-btn" @click="onExportData">
          {{ exporting ? 'Exporting...' : 'Export Data' }}
        </button>
        <span v-if="exportMessage" class="export-msg">{{ exportMessage }}</span>
      </section>

      <!-- About -->
      <section class="settings-group">
        <h2 class="group-label">About</h2>
        <p class="version-text">Version 1.0.0</p>
        <button class="action-btn" @click="onCheckUpdates">
          {{ checkingUpdates ? 'Checking...' : 'Check for updates' }}
        </button>
        <span v-if="updateMessage" class="update-msg">{{ updateMessage }}</span>
      </section>

      <!-- Save -->
      <div class="save-area">
        <button class="save-btn" :disabled="!settings.isDirty" @click="onSave">
          Save
        </button>
        <span v-if="showSaved" class="saved-toast">Saved</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useSettingsStore } from '../stores/settings'
import FormField from './FormField.vue'

defineEmits<{
  (e: 'back'): void
}>()

const settings = useSettingsStore()
const showSaved = ref(false)
const exporting = ref(false)
const exportMessage = ref('')
const checkingUpdates = ref(false)
const updateMessage = ref('')

function onNicknameChange(v: string) { settings.nickname = v; settings.markDirty() }
function onApiKeyChange(v: string)    { settings.apiKey = v; settings.markDirty() }
function onModelNameChange(v: string) { settings.modelName = v; settings.markDirty() }
function onProxyUrlChange(v: string)  { settings.proxyUrl = v; settings.markDirty() }

function onSave() {
  settings.saveSettings()
  showSaved.value = true
  setTimeout(() => { showSaved.value = false }, 2000)
}

async function onExportData() {
  exporting.value = true
  exportMessage.value = ''
  try {
    const port = await window.electronAPI?.getBackendPort()
    if (!port) {
      exportMessage.value = 'Backend not available'
      return
    }
    const response = await fetch(`http://127.0.0.1:${port}/api/sessions?limit=1000`)
    if (!response.ok) throw new Error('Failed to fetch')
    const sessions = await response.json()
    const blob = new Blob([JSON.stringify(sessions, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `c-claw-sessions-${new Date().toISOString().slice(0, 10)}.json`
    a.click()
    URL.revokeObjectURL(url)
    exportMessage.value = `Exported ${sessions.length} sessions`
    setTimeout(() => { exportMessage.value = '' }, 3000)
  } catch (e: any) {
    exportMessage.value = 'Export failed: ' + (e.message || 'unknown error')
  } finally {
    exporting.value = false
  }
}

async function onCheckUpdates() {
  checkingUpdates.value = true
  updateMessage.value = ''
  try {
    const response = await fetch('https://api.github.com/repos/adamcchen/c-claw/releases/latest')
    if (!response.ok) {
      updateMessage.value = 'No updates available'
      return
    }
    const release = await response.json()
    const latest = release.tag_name?.replace(/^v/, '') || ''
    if (latest && latest !== '1.0.0') {
      updateMessage.value = `New version available: ${latest}`
    } else {
      updateMessage.value = 'You are up to date'
    }
    setTimeout(() => { updateMessage.value = '' }, 5000)
  } catch {
    updateMessage.value = 'Could not check for updates'
    setTimeout(() => { updateMessage.value = '' }, 3000)
  } finally {
    checkingUpdates.value = false
  }
}

onMounted(() => {
  settings.fetchSkills()
})
</script>

<style scoped>
.settings-view {
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

.settings-content {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.settings-group {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.group-label {
  font-size: 13px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  color: var(--text-muted);
  margin: 0 0 4px;
}

.placeholder-text {
  font-size: 14px;
  color: var(--text-muted);
  margin: 0;
}

.shortcut-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.shortcut-row {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: var(--text-primary);
}

.shortcut-row kbd {
  display: inline-block;
  padding: 2px 8px;
  font-size: 12px;
  font-family: inherit;
  background: var(--border-color);
  border-radius: 4px;
  color: var(--text-primary);
  min-width: 90px;
  text-align: center;
}

.version-text {
  font-size: 14px;
  color: var(--text-muted);
  margin: 0;
}

.action-btn {
  align-self: flex-start;
  padding: 6px 16px;
  font-size: 13px;
  background: none;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  color: var(--text-primary);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.action-btn:hover {
  background: var(--accent-color);
  color: #fff;
  border-color: var(--accent-color);
}

.save-area {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 8px;
  border-top: 1px solid var(--border-color);
}

.save-btn {
  padding: 8px 32px;
  font-size: 14px;
  font-weight: 600;
  background: var(--accent-color);
  color: #fff;
  border: none;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: background var(--transition-fast), opacity var(--transition-fast);
}

.save-btn:hover:not(:disabled) {
  background: var(--accent-hover);
}

.save-btn:disabled {
  opacity: 0.4;
  cursor: default;
}

.saved-toast {
  font-size: 13px;
  color: var(--success-color);
  transition: opacity var(--transition-fast);
}

.hint-text {
  font-size: 12px;
  color: var(--text-muted);
  margin: 4px 0 0;
  font-family: monospace;
}

.skill-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skill-item {
  padding: 10px 12px;
  background: var(--bg-color);
  border-radius: var(--radius-md);
  border: 1px solid var(--border-color);
}

.skill-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.skill-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.skill-version {
  font-size: 11px;
  color: var(--text-muted);
  background: var(--border-color);
  padding: 1px 6px;
  border-radius: 4px;
}

.skill-desc {
  font-size: 13px;
  color: var(--text-muted);
  margin: 4px 0 0;
}

.export-msg,
.update-msg {
  font-size: 13px;
  color: var(--success-color);
}

.state-block {
  padding: 8px 0;
}
</style>