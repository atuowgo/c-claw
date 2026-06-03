import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface ToolTrace {
    toolUseId: string
    toolName: string
    status: 'running' | 'success' | 'error'
    summary?: string
}

export const useToolTracesStore = defineStore('toolTraces', () => {
    const traces = ref<ToolTrace[]>([])
    const isPanelOpen = ref(false)

    function openPanel() {
        isPanelOpen.value = true
    }

    function closePanel() {
        isPanelOpen.value = false
    }

    function addTrace(trace: ToolTrace) {
        // deduplicate by toolUseId
        if (!traces.value.find(t => t.toolUseId === trace.toolUseId)) {
            traces.value.push(trace)
        }
    }

    function updateTrace(toolUseId: string, update: { status: 'success' | 'error'; summary?: string }) {
        const trace = traces.value.find(t => t.toolUseId === toolUseId)
        if (trace) {
            trace.status = update.status
            trace.summary = update.summary
        }
    }

    function clearTraces() {
        traces.value = []
        isPanelOpen.value = false
    }

    return { traces, isPanelOpen, openPanel, closePanel, addTrace, updateTrace, clearTraces }
})