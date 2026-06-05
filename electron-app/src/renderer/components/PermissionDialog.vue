<template>
  <Teleport to="body">
    <div class="permission-overlay" :class="{ visible }" @click.self="onDismiss">
      <div class="permission-dialog" :class="{ visible }">
        <div class="dialog-header">
          <span class="header-icon">&#x26A0;</span>
          <h2 class="header-title">权限确认</h2>
        </div>

        <div class="dialog-body">
          <div class="info-row">
            <span class="info-label">工具名称</span>
            <span class="info-value name">{{ request.toolName }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">权限级别</span>
            <span class="badge level-high">{{ request.level }}</span>
          </div>
          <div class="info-row">
            <span class="info-label">描述</span>
            <span class="info-value">{{ request.description }}</span>
          </div>
          <p class="notice">C-Claw 需要你的确认才能执行此操作</p>
        </div>

        <div class="dialog-footer">
          <div class="countdown">将在 {{ countdown }} 秒后自动拒绝</div>
          <div class="actions">
            <button class="btn btn-deny" :disabled="responding" @click="onDeny">拒绝 (Deny)</button>
            <button class="btn btn-always" :disabled="responding" @click="onAlwaysAllow">始终允许 (Always Allow)</button>
            <button class="btn btn-approve" :disabled="responding" @click="onApprove">允许 (Approve)</button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'

export interface PermissionRequest {
  toolUseId: string
  toolName: string
  level: string
  description: string
}

const props = defineProps<{
  request: PermissionRequest
}>()

const emit = defineEmits<{
  respond: [payload: { toolUseId: string; approved: boolean; scope: string }]
  dismiss: []
}>()

const visible = ref(false)
const countdown = ref(30)
const responding = ref(false)

let timer: ReturnType<typeof setInterval> | null = null

function startCountdown() {
  countdown.value = 30
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(timer!)
      timer = null
      emitDismiss()
    }
  }, 1000)
}

function emitDismiss() {
  visible.value = false
  setTimeout(() => emit('dismiss'), 200)
}

function onDismiss() {
  if (responding.value) return
  clearInterval(timer!)
  timer = null
  emitDismiss()
}

function onApprove() {
  if (responding.value) return
  responding.value = true
  clearInterval(timer!)
  timer = null
  visible.value = false
  setTimeout(() => {
    emit('respond', { toolUseId: props.request.toolUseId, approved: true, scope: 'once' })
  }, 200)
}

function onDeny() {
  if (responding.value) return
  responding.value = true
  clearInterval(timer!)
  timer = null
  visible.value = false
  setTimeout(() => {
    emit('respond', { toolUseId: props.request.toolUseId, approved: false, scope: 'once' })
  }, 200)
}

function onAlwaysAllow() {
  if (responding.value) return
  responding.value = true
  clearInterval(timer!)
  timer = null
  visible.value = false
  setTimeout(() => {
    emit('respond', { toolUseId: props.request.toolUseId, approved: true, scope: 'always' })
  }, 200)
}

onMounted(() => {
  nextTick(() => {
    visible.value = true
  })
  startCountdown()
})

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
})
</script>

<style scoped>
.permission-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
  opacity: 0;
  transition: opacity var(--transition-fast);
}

.permission-overlay.visible {
  opacity: 1;
}

.permission-dialog {
  background: var(--surface-color);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-md);
  width: 440px;
  max-width: 90vw;
  overflow: hidden;
  opacity: 0;
  transform: scale(0.92);
  transition: opacity var(--transition-normal), transform var(--transition-normal);
}

.permission-dialog.visible {
  opacity: 1;
  transform: scale(1);
}

.dialog-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color);
}

.header-icon {
  font-size: 20px;
  color: var(--warning-color);
}

.header-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
}

.dialog-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.info-value {
  font-size: 14px;
  color: var(--text-primary);
  word-break: break-all;
}

.info-value.name {
  font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', monospace;
  font-weight: 600;
}

.badge {
  display: inline-block;
  align-self: flex-start;
  padding: 3px 10px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  font-weight: 600;
}

.badge.level-high {
  color: var(--warning-color);
  background: color-mix(in srgb, var(--warning-color) 15%, transparent);
}

.notice {
  margin: 8px 0 0 0;
  font-size: 13px;
  color: var(--text-muted);
  line-height: 1.5;
}

.dialog-footer {
  padding: 12px 20px 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  border-top: 1px solid var(--border-color);
}

.countdown {
  font-size: 12px;
  color: var(--text-muted);
  text-align: center;
}

.actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

.btn {
  padding: 8px 16px;
  border: none;
  border-radius: var(--radius-md);
  font-family: inherit;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color var(--transition-fast), opacity var(--transition-fast);
  white-space: nowrap;
}

.btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.btn-deny {
  background: transparent;
  color: var(--danger-color);
  border: 1px solid var(--danger-color);
}

.btn-deny:hover:not(:disabled) {
  background: rgba(224, 85, 106, 0.12);
}

.btn-always {
  background: transparent;
  color: var(--accent-color);
  border: 1px solid var(--accent-color);
}

.btn-always:hover:not(:disabled) {
  background: rgba(74, 124, 247, 0.12);
}

.btn-approve {
  background: var(--success-color);
  color: #0a0a1a;
}

.btn-approve:hover:not(:disabled) {
  filter: brightness(1.1);
}
</style>