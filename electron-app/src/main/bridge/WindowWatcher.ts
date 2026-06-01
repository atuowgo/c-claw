import activeWin from 'active-win'

export interface ActiveWindowResult {
  title: string
  processName: string
  pid?: number
}

export async function getActiveWindow(): Promise<ActiveWindowResult | null> {
  try {
    const result = await activeWin()
    if (!result) return null
    return {
      title: result.title,
      processName: result.owner.name,
      pid: result.owner.processId
    }
  } catch (err) {
    console.error('[c-claw] WindowWatcher error:', err)
    return null
  }
}