import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface SessionInfo {
    id: string
    title: string | null
    createdAt: number
    active: boolean
    messageCount: number
}

export const useSessionsStore = defineStore('sessions', () => {
    const sessions = ref<SessionInfo[]>([])
    const isLoading = ref(false)
    const error = ref<string | null>(null)

    let backendPort: number | null = null

    async function getPort(): Promise<number> {
        if (backendPort) return backendPort
        if (window.electronAPI?.getBackendPort) {
            backendPort = await window.electronAPI.getBackendPort()
            if (backendPort === null) {
                throw new Error('Backend not started')
            }
            return backendPort
        }
        throw new Error('Backend port not available')
    }

    async function fetchSessions(limit = 10) {
        isLoading.value = true
        error.value = null
        try {
            const port = await getPort()
            const response = await fetch(`http://127.0.0.1:${port}/api/sessions?limit=${limit}`)
            if (!response.ok) throw new Error(`Server error: ${response.status}`)
            sessions.value = await response.json()
        } catch (e: any) {
            error.value = e.message || 'Failed to load sessions'
        } finally {
            isLoading.value = false
        }
    }

    async function deleteSession(id: string) {
        try {
            const port = await getPort()
            const response = await fetch(`http://127.0.0.1:${port}/api/sessions/${id}`, {
                method: 'DELETE'
            })
            if (!response.ok) throw new Error(`Server error: ${response.status}`)
            sessions.value = sessions.value.filter(s => s.id !== id)
        } catch (e: any) {
            error.value = e.message || 'Failed to delete session'
        }
    }

    async function renameSession(id: string, title: string) {
        try {
            const port = await getPort()
            const response = await fetch(`http://127.0.0.1:${port}/api/sessions/${id}`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ title })
            })
            if (!response.ok) throw new Error(`Server error: ${response.status}`)
            const session = sessions.value.find(s => s.id === id)
            if (session) session.title = title
        } catch (e: any) {
            error.value = e.message || 'Failed to rename session'
        }
    }

    async function generateTitle(id: string) {
        try {
            const port = await getPort()
            const response = await fetch(`http://127.0.0.1:${port}/api/sessions/${id}/generate-title`, {
                method: 'POST'
            })
            if (!response.ok) {
                console.error('[Sessions] generateTitle HTTP error:', response.status)
                return
            }
            const result = await response.json()
            if (result.success && result.title) {
                const session = sessions.value.find(s => s.id === id)
                if (session) session.title = result.title
            } else {
                console.warn('[Sessions] generateTitle returned success=false for session:', id)
            }
        } catch (e: any) {
            console.error('[Sessions] generateTitle failed:', e.message || e)
        }
    }

    async function createSession(): Promise<SessionInfo> {
        const port = await getPort()
        const response = await fetch(`http://127.0.0.1:${port}/api/sessions`, { method: 'POST' })
        if (!response.ok) throw new Error(`Server error: ${response.status}`)
        const session = await response.json()
        sessions.value.unshift(session)
        return session
    }

    return { sessions, isLoading, error, fetchSessions, deleteSession, renameSession, generateTitle, createSession }
})