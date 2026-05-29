// SSE stream parser utilities
// Core logic is in stores/chat.ts for now

export interface SseEvent {
    event: string
    data: string
}

export function parseSseLine(line: string): Partial<SseEvent> | null {
    if (line.startsWith('event: ')) {
        return { event: line.slice(7).trim() }
    }
    if (line.startsWith('data: ')) {
        return { data: line.slice(6) }
    }
    return null
}