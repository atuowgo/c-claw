/// <reference types="vite/client" />

declare global {
    interface Window {
        electronAPI?: {
            getBackendPort: () => Promise<number>
            platform: string
        }
    }
}

export {}