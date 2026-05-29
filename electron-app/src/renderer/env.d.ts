/// <reference types="vite/client" />

declare global {
    interface Window {
        electronAPI?: {
            getBackendPort: () => Promise<number>
            getBridgePort: () => Promise<number | null>
            platform: string
        }
    }
}

export {}