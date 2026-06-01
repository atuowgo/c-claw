import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface Message {
    id: string
    role: 'user' | 'assistant'
    content: string
    isStreaming: boolean
}

export const useChatStore = defineStore('chat', () => {
    const messages = ref<Message[]>([])
    const isSending = ref(false)
    const error = ref<string | null>(null)

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

    async function sendMessage(text: string) {
        if (isSending.value || !text.trim()) return

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
        const assistantMsg: Message = {
            id: generateId(),
            role: 'assistant',
            content: '',
            isStreaming: true
        }
        messages.value.push(assistantMsg)

        try {
            const port = await getPort()
            const response = await fetch(`http://127.0.0.1:${port}/api/chat`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ message: text.trim() })
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
                    // Flush decoder: final call without stream flag to emit remaining bytes
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
                                assistantMsg.content += parsed.delta || ''
                            } catch {}
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

            assistantMsg.isStreaming = false
        } catch (e: any) {
            error.value = e.message || 'Failed to send message'
            assistantMsg.content = assistantMsg.content || '(Error: failed to get response)'
            assistantMsg.isStreaming = false
        } finally {
            isSending.value = false
        }
    }

    function clearChat() {
        messages.value = []
        error.value = null
    }

    return { messages, isSending, error, sendMessage, clearChat }
})