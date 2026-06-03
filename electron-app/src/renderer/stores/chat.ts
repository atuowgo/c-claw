import { defineStore } from 'pinia'
import { ref } from 'vue'
import { useToolTracesStore } from './toolTraces'
import { useSessionsStore } from './sessions'

export interface ToolCallRecord {
    toolUseId: string
    toolName: string
    status: 'running' | 'success' | 'error'
    summary?: string
}

export interface Message {
    id: string
    role: 'user' | 'assistant'
    content: string
    isStreaming: boolean
    thinking?: string
    toolCalls?: ToolCallRecord[]
}

export const useChatStore = defineStore('chat', () => {
    const messages = ref<Message[]>([])
    const isSending = ref(false)
    const error = ref<string | null>(null)
    const currentSessionId = ref<string | null>(null)

    let backendPort: number | null = null

    async function getPort(): Promise<number> {
        if (backendPort) return backendPort
        if (window.electronAPI?.getBackendPort) {
            backendPort = await window.electronAPI.getBackendPort()
            if (backendPort === null) {
                throw new Error('Backend not started. Please restart the application.')
            }
            return backendPort
        }
        throw new Error('Backend port not available')
    }

    function generateId(): string {
        return Date.now().toString(36) + Math.random().toString(36).slice(2)
    }

    async function sendMessage(text: string, sessionId?: string | null) {
        if (isSending.value || !text.trim()) return

        // Auto-create session if none exists
        if (!currentSessionId.value && !sessionId) {
            await createSession()
        }

        isSending.value = true
        error.value = null

        // Add user message
        const userMsg: Message = {
            id: generateId(),
            role: 'user',
            content: text.trim(),
            isStreaming: false
        }
        messages.value.push(userMsg)

        // Create placeholder for assistant response
        const assistantId = generateId()
        messages.value.push({
            id: assistantId,
            role: 'assistant',
            content: '',
            isStreaming: true,
            toolCalls: []
        })
        const getAssistantMsg = (): Message => messages.value.find(m => m.id === assistantId)!

        try {
            const port = await getPort()
            const body: Record<string, string> = { message: text.trim() }
            const sid = sessionId ?? currentSessionId.value
            if (sid) body.sessionId = sid
            const response = await fetch(`http://127.0.0.1:${port}/api/chat`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            })

            if (!response.ok) {
                throw new Error(`Server error: ${response.status}`)
            }

            const reader = response.body!.getReader()
            const decoder = new TextDecoder()
            let buffer = ''
            let currentEvent = ''

            while (true) {
                const { done, value } = await reader.read()
                if (done) {
                    buffer += decoder.decode()
                    break
                }

                buffer += decoder.decode(value, { stream: true })

                // Parse SSE from buffer
                const lines = buffer.split('\n')
                buffer = lines.pop() || '' // Keep incomplete line in buffer
                for (const line of lines) {
                    // Blank line is SSE event delimiter -- reset currentEvent
                    if (line === '' || line === '\r') {
                        currentEvent = ''
                        continue
                    }
                    // Spring SseEmitter sends "event:text" (no space), but SSE spec allows both
                    if (line.startsWith('event:')) {
                        currentEvent = line.slice(6).trim()
                    } else if (line.startsWith('data:')) {
                        const data = line.slice(5).trim()
                        if (currentEvent === 'text') {
                            try {
                                const parsed = JSON.parse(data)
                                getAssistantMsg().content += parsed.delta || ''
                            } catch {
                                // skip malformed delta
                            }
                        } else if (currentEvent === 'tool_call') {
                            try {
                                const parsed = JSON.parse(data)
                                const record: ToolCallRecord = {
                                    toolUseId: parsed.toolUseId,
                                    toolName: parsed.toolName,
                                    status: 'running'
                                }
                                getAssistantMsg().toolCalls = [
                                    ...(getAssistantMsg().toolCalls || []),
                                    record
                                ]
                                const traces = useToolTracesStore()
                                traces.addTrace(record)
                                traces.openPanel()
                            } catch {
                                // skip malformed tool_call
                            }
                        } else if (currentEvent === 'tool_result') {
                            try {
                                const parsed = JSON.parse(data)
                                const msg = getAssistantMsg()
                                if (msg.toolCalls) {
                                    const tc = msg.toolCalls.find(t => t.toolUseId === parsed.toolUseId)
                                    if (tc) {
                                        tc.status = parsed.success ? 'success' : 'error'
                                        tc.summary = parsed.summary
                                    }
                                }
                                const traces = useToolTracesStore()
                                traces.updateTrace(parsed.toolUseId, {
                                    status: parsed.success ? 'success' : 'error',
                                    summary: parsed.summary
                                })
                            } catch {
                                // skip malformed tool_result
                            }
                        } else if (currentEvent === 'error') {
                            try {
                                const parsed = JSON.parse(data)
                                error.value = parsed.message || 'Unknown error'
                            } catch {
                                error.value = 'Unknown error'
                            }
                        }
                        // 'done' event doesn't need handling here
                    }
                }
            }

            getAssistantMsg().isStreaming = false
        } catch (e: any) {
            error.value = e.message || 'Failed to send message'
            const msg = getAssistantMsg()
            msg.content = msg.content || '(Error: failed to get response)'
            msg.isStreaming = false
        } finally {
            isSending.value = false

            // Trigger async title generation for new sessions
            const sid = sessionId ?? currentSessionId.value
            if (sid) {
                const sessionsStore = useSessionsStore()
                sessionsStore.fetchSessions().then(() => {
                    sessionsStore.generateTitle(sid)
                })
            }
        }
    }

    function clearChat() {
        messages.value = []
        error.value = null
    }

    async function loadSession(sessionId: string) {
        currentSessionId.value = sessionId
        clearChat()
        try {
            const port = await getPort()
            const response = await fetch(`http://127.0.0.1:${port}/api/sessions/${sessionId}/messages`)
            if (!response.ok) return
            const records = await response.json()
            for (const r of records) {
                messages.value.push({
                    id: generateId(),
                    role: r.role,
                    content: r.content,
                    isStreaming: false
                })
            }
        } catch {
            // silent — best-effort history loading
        }
    }

    function clearCurrentSession() {
        currentSessionId.value = null
        clearChat()
    }

    async function createSession(): Promise<string> {
        const sessionsStore = useSessionsStore()
        const session = await sessionsStore.createSession()
        currentSessionId.value = session.id
        clearChat()
        return session.id
    }

    return { messages, isSending, error, currentSessionId, sendMessage, clearChat, loadSession, clearCurrentSession, createSession }
})