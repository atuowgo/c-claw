<script setup lang="ts">
import { useToolTracesStore } from '../stores/toolTraces'

const tracesStore = useToolTracesStore()

function truncate(text: string, maxLen: number): string {
  if (text.length <= maxLen) return text
  return text.slice(0, maxLen) + '...'
}

const statusLabels: Record<string, string> = {
  running: 'Running',
  success: 'Success',
  error: 'Error',
}
</script>

<template>
  <aside class="tool-panel" :class="{ 'is-open': tracesStore.isPanelOpen }">
    <div class="panel-inner">
      <div class="panel-header">
        <h3 class="panel-title">Tool Traces</h3>
        <button class="close-btn" @click="tracesStore.closePanel()" aria-label="Close panel">
          &#x2715;
        </button>
      </div>

      <div v-if="tracesStore.traces.length > 0" class="trace-list">
        <div
          v-for="t in tracesStore.traces"
          :key="t.toolUseId"
          class="trace-item"
        >
          <div class="trace-header">
            <span class="trace-name">{{ t.toolName }}</span>
            <span class="trace-status" :class="'status-' + t.status">
              <span class="status-dot"></span>
              {{ statusLabels[t.status] ?? t.status }}
            </span>
          </div>
          <div v-if="t.summary" class="trace-summary">
            {{ truncate(t.summary, 200) }}
          </div>
        </div>
      </div>

      <div v-else class="empty-state">
        <span class="empty-icon" aria-hidden="true">&#x1F50D;</span>
        <span class="empty-text">No tool calls in this session</span>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.tool-panel {
  width: 0;
  overflow: hidden;
  border-left: none;
  background: var(--surface-color);
  transition: width var(--transition-normal);
  flex-shrink: 0;
}

.tool-panel.is-open {
  width: var(--right-panel-width, 320px);
  border-left: 1px solid var(--border-color);
  overflow-y: auto;
}

.panel-inner {
  min-width: var(--right-panel-width, 320px);
  height: 100%;
  display: flex;
  flex-direction: column;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.panel-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.close-btn {
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  padding: 4px;
  border-radius: var(--radius-sm);
}

.close-btn:hover {
  color: var(--text-primary);
  background: var(--border-color);
}

.trace-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.trace-item {
  padding: 10px 16px;
  border-bottom: 1px solid var(--border-color);
}

.trace-item:last-child {
  border-bottom: none;
}

.trace-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.trace-name {
  font-weight: 700;
  font-size: 13px;
  color: var(--text-primary);
  word-break: break-word;
}

.trace-status {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: var(--radius-sm);
  flex-shrink: 0;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  display: inline-block;
}

.status-running {
  color: var(--accent-color);
  background: color-mix(in srgb, var(--accent-color) 12%, transparent);
}

.status-running .status-dot {
  background: var(--accent-color);
  animation: pulse-dot 1.4s ease-in-out infinite;
}

.status-success {
  color: var(--success-color);
  background: color-mix(in srgb, var(--success-color) 12%, transparent);
}

.status-success .status-dot {
  background: var(--success-color);
}

.status-error {
  color: var(--danger-color);
  background: color-mix(in srgb, var(--danger-color) 12%, transparent);
}

.status-error .status-dot {
  background: var(--danger-color);
}

@keyframes pulse-dot {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.35; }
}

.trace-summary {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--text-muted);
  word-break: break-word;
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 32px 16px;
}

.empty-icon {
  font-size: 28px;
  opacity: 0.35;
}

.empty-text {
  font-size: 13px;
  color: var(--text-muted);
}
</style>