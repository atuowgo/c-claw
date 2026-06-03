import { defineStore } from 'pinia'
import { ref } from 'vue'

export interface SkillInfo {
    name: string
    version: string
    description: string
    directoryPath: string
}

export const useSettingsStore = defineStore('settings', () => {
    const nickname = ref('')
    const apiKey = ref('')
    const modelName = ref('')
    const proxyUrl = ref('')
    const storagePath = ref('')
    const isDirty = ref(false)

    const skills = ref<SkillInfo[]>([])
    const skillsLoading = ref(false)

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

    async function fetchSkills() {
        skillsLoading.value = true
        try {
            const port = await getPort()
            const response = await fetch(`http://127.0.0.1:${port}/api/skills`)
            if (!response.ok) return
            const data = await response.json()
            skills.value = data.skills || []
        } catch {
            // silent
        } finally {
            skillsLoading.value = false
        }
    }

    function loadSettings() {
        try {
            nickname.value = localStorage.getItem('c-claw:nickname') || ''
            apiKey.value = localStorage.getItem('c-claw:apiKey') || ''
            modelName.value = localStorage.getItem('c-claw:modelName') || ''
            proxyUrl.value = localStorage.getItem('c-claw:proxyUrl') || ''
            storagePath.value = localStorage.getItem('c-claw:storagePath') || ''
            isDirty.value = false
        } catch {
            // localStorage unavailable
        }
    }

    function saveSettings() {
        try {
            localStorage.setItem('c-claw:nickname', nickname.value)
            localStorage.setItem('c-claw:apiKey', apiKey.value)
            localStorage.setItem('c-claw:modelName', modelName.value)
            localStorage.setItem('c-claw:proxyUrl', proxyUrl.value)
            localStorage.setItem('c-claw:storagePath', storagePath.value)
            isDirty.value = false
        } catch {
            // localStorage unavailable
        }
    }

    function markDirty() {
        isDirty.value = true
    }

    loadSettings()

    return { nickname, apiKey, modelName, proxyUrl, storagePath, isDirty,
        skills, skillsLoading, fetchSkills, loadSettings, saveSettings, markDirty }
})