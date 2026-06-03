<template>
  <div class="form-field">
    <label class="field-label">{{ label }}</label>
    <select
      v-if="type === 'select'"
      class="field-input"
      :value="modelValue"
      :disabled="readonly"
      @change="onChange(($event.target as HTMLSelectElement).value)"
    >
      <option v-for="opt in options" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
    </select>
    <input
      v-else
      class="field-input"
      :class="{ readonly }"
      :type="type"
      :value="modelValue"
      :readonly="readonly"
      @input="onChange(($event.target as HTMLInputElement).value)"
    />
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  label: string
  type?: 'text' | 'password' | 'select'
  modelValue: string
  options?: { label: string; value: string }[]
  readonly?: boolean
}>(), {
  type: 'text',
  readonly: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

function onChange(value: string) {
  emit('update:modelValue', value)
}
</script>

<style scoped>
.form-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.field-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.field-input {
  padding: 8px 12px;
  font-size: 14px;
  background: var(--surface-color);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  color: var(--text-primary);
  outline: none;
  transition: border-color var(--transition-fast), opacity var(--transition-fast);
}

.field-input:focus {
  border-color: var(--accent-color);
}

.field-input::placeholder {
  color: var(--text-muted);
}

.field-input.readonly {
  opacity: 0.5;
  cursor: default;
}

.field-input:disabled {
  opacity: 0.5;
  cursor: default;
}
</style>